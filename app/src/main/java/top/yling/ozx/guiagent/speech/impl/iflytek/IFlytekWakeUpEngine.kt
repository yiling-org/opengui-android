package top.yling.ozx.guiagent.speech.impl.iflytek

import android.app.Application
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.iflytek.aikit.core.AiAudio
import com.iflytek.aikit.core.AiHandle
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.AiListener
import com.iflytek.aikit.core.AiRequest
import com.iflytek.aikit.core.AiResponse
import com.iflytek.aikit.core.AiStatus
import top.yling.ozx.guiagent.MyApplication
import top.yling.ozx.guiagent.R
import top.yling.ozx.guiagent.speech.WakeUpEngine
import top.yling.ozx.guiagent.util.IFlytekConfigProvider
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 讯飞语音唤醒引擎实现
 * 基于讯飞 AIKit SDK 的离线语音唤醒
 * 
 * 特点：
 * - 离线唤醒，无需网络
 * - 支持自定义唤醒词
 * - 低功耗持续监听
 * 
 * 配置要求：
 * - 需要在讯飞控制台开通"离线语音唤醒（新版）"服务
 * - 需要配置 App ID、API Key、API Secret
 * - 需要唤醒资源文件（IVW_FILLER_1, IVW_GRAM_1, IVW_KEYWORD_1, IVW_MLP_1）
 * 
 * @author shanwb
 */
class IFlytekWakeUpEngine(private val context: Context) : WakeUpEngine {

    companion object {
        private const val TAG = "IFlytekWakeUp"
        private const val ABILITY_ID = "e867a88f2"  // IVW70 离线唤醒能力ID
        private const val BUFFER_SIZE = 1280  // 录音缓冲区大小（40ms音频数据）
        private const val SAMPLE_RATE = 16000  // 采样率 16kHz

        // 消息类型
        private const val MSG_START = 0x0001
        private const val MSG_WRITE = 0x0002
        private const val MSG_END = 0x0003
    }

    private var aiHandle: AiHandle? = null
    private var audioRecord: AudioRecord? = null
    private var isEnd = AtomicBoolean(true)
    private var isRecording = AtomicBoolean(false)
    private var currentCallback: WakeUpEngine.Callback? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isListeningFlag = false
    private var workHandler: Handler? = null
    private var workThread: Thread? = null
    private var resDir: String = ""
    private var currentKeywords: List<String> = emptyList()
    private var currentState = WakeUpEngine.State.UNINITIALIZED

    init {
        initializeWorkThread()
        // 使用动态获取的工作目录
        val workDir = MyApplication.getAIKitWorkDir(context.applicationContext as Application)
        resDir = "$workDir/ivw"
    }

    /**
     * 初始化工作线程
     */
    private fun initializeWorkThread() {
        workThread = Thread {
            Looper.prepare()
            workHandler = Handler(Looper.myLooper()!!) { msg ->
                when (msg.what) {
                    MSG_START -> {
                        Log.d(TAG, "MSG_START")
                        val ret = start()
                        if (ret == 0) {
                            createAudioRecord()
                            isRecording.set(true)
                            audioRecord?.startRecording()

                            workHandler?.removeCallbacksAndMessages(null)
                            val writeMsg = Message.obtain(workHandler, MSG_WRITE)
                            writeMsg.obj = AiStatus.BEGIN
                            workHandler?.sendMessage(writeMsg)
                        } else {
                            Log.e(TAG, "启动唤醒失败: $ret")
                            isListeningFlag = false
                            updateState(WakeUpEngine.State.ERROR)
                        }
                        true
                    }
                    MSG_WRITE -> {
                        val status = msg.obj as? AiStatus ?: AiStatus.CONTINUE
                        val data = ByteArray(BUFFER_SIZE)
                        val read = audioRecord?.read(data, 0, BUFFER_SIZE) ?: 0

                        if (read != AudioRecord.ERROR_INVALID_OPERATION && read > 0) {
                            val audioData = data.copyOf(read)
                            write(audioData, status)
                        }

                        if (status == AiStatus.END) {
                            audioRecord?.stop()
                            isRecording.set(false)
                            workHandler?.sendEmptyMessage(MSG_END)
                        } else if (isRecording.get()) {
                            val writeMsg = Message.obtain(workHandler, MSG_WRITE)
                            writeMsg.obj = AiStatus.CONTINUE
                            workHandler?.sendMessage(writeMsg)
                        }
                        true
                    }
                    MSG_END -> {
                        Log.d(TAG, "MSG_END")
                        end()
                        true
                    }
                    else -> false
                }
            }
            Looper.loop()
        }
        workThread?.start()
    }

    /**
     * 创建录音器
     */
    private fun createAudioRecord() {
        if (isRecording.get()) {
            return
        }
        if (audioRecord == null) {
            Log.d(TAG, "createAudioRecord")
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE
            )
        }
    }

    /**
     * 开始会话
     */
    private fun start(): Int {
        // 检查 SDK 授权状态
        if (MyApplication.aikitAuthResult != 0) {
            Log.e(TAG, "AIKit SDK 未授权，授权码: ${MyApplication.aikitAuthResult}")
            return -1
        }

        // 检查资源文件是否存在
        if (!checkIVWResources()) {
            Log.e(TAG, "唤醒资源文件缺失，请检查工作目录")
            return -1
        }

        // 写入唤醒词文件
        if (!keyword2File()) {
            Log.e(TAG, "唤醒词文件写入失败，请检查是否有读写权限")
            return -1
        }

        // 注册监听器
        AiHelper.getInst().registerListener(ABILITY_ID, aiListener)

        // 加载唤醒词资源
        val customBuilder = AiRequest.builder()
        customBuilder.customText("key_word", "$resDir/keyword.txt", 0)
        var ret = AiHelper.getInst().loadData(ABILITY_ID, customBuilder.build())
        if (ret != 0) {
            Log.e(TAG, "loadData 失败: $ret")
            Log.e(TAG, "错误码 18608 表示缺少资源文件，请检查:")
            Log.e(TAG, "  1. 工作目录: $resDir")
            Log.e(TAG, "  2. 资源文件是否存在: IVW_FILLER_1, IVW_GRAM_1, IVW_KEYWORD_1, IVW_MLP_1")
            return ret
        }
        Log.d(TAG, "loadData 成功: $ret")

        // 指定数据集
        val indexs = intArrayOf(0)
        ret = AiHelper.getInst().specifyDataSet(ABILITY_ID, "key_word", indexs)
        if (ret != 0) {
            Log.e(TAG, "specifyDataSet 失败: $ret")
            return ret
        }
        Log.d(TAG, "specifyDataSet 成功: $ret")

        // 设置参数并启动
        val paramBuilder = AiRequest.builder()
        paramBuilder.param("wdec_param_nCmThreshold", "0 0:800")
        paramBuilder.param("gramLoad", true)

        isEnd.set(false)
        aiHandle = AiHelper.getInst().start(ABILITY_ID, paramBuilder.build(), null)
        if (aiHandle?.code != 0) {
            Log.e(TAG, "start 失败: ${aiHandle?.code}")
            return aiHandle?.code ?: -1
        }

        Log.d(TAG, "唤醒启动成功")
        return 0
    }

    /**
     * 写入音频数据
     */
    private fun write(part: ByteArray, status: AiStatus) {
        if (isEnd.get()) {
            return
        }

        val dataBuilder = AiRequest.builder()
        val aiAudio = AiAudio.get("wav").data(part).status(status).valid()
        dataBuilder.payload(aiAudio)

        val ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle)
        if (ret != 0) {
            Log.w(TAG, "write 失败: $ret")
        }
    }

    /**
     * 结束会话
     */
    private fun end() {
        if (!isEnd.get()) {
            val ret = AiHelper.getInst().end(aiHandle)
            if (ret == 0) {
                isEnd.set(true)
                aiHandle = null
                Log.d(TAG, "唤醒结束成功: $ret")
            } else {
                Log.e(TAG, "唤醒结束失败: $ret")
            }
        }
    }

    /**
     * 检查唤醒资源文件是否存在
     */
    private fun checkIVWResources(): Boolean {
        val resourceFiles = listOf(
            "IVW_FILLER_1",
            "IVW_GRAM_1",
            "IVW_KEYWORD_1",
            "IVW_MLP_1"
        )

        val workDir = File(resDir).parentFile
        val ivwDir = File(workDir, "ivw")

        var allExists = true
        for (fileName in resourceFiles) {
            val file = File(ivwDir, fileName)
            if (!file.exists()) {
                allExists = false
                Log.e(TAG, "缺少资源文件: ${file.absolutePath}")
            }
        }

        if (!allExists) {
            Log.e(TAG, "=== 资源文件缺失 ===")
            Log.e(TAG, "请从 SDK 包的 resource/ivw/ 目录复制以下文件到:")
            Log.e(TAG, "  $ivwDir")
            resourceFiles.forEach {
                Log.e(TAG, "  - $it")
            }
        }

        return allExists
    }

    /**
     * 将唤醒词写入文件
     */
    private fun keyword2File(): Boolean {
        return try {
            val keywordFile = File("$resDir/keyword.txt")
            val binFile = File("$resDir/keyword.bin")

            // 删除旧文件
            if (keywordFile.exists()) {
                keywordFile.delete()
            }
            if (binFile.exists()) {
                binFile.delete()
            }

            // 创建工作目录
            val dir = File(resDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }

            // 写入唤醒词
            val keyword = currentKeywords.firstOrNull()
                ?: context.getString(R.string.app_name_wake_word)

            if (!keywordFile.exists()) {
                keywordFile.createNewFile()
            }

            val writer = OutputStreamWriter(FileOutputStream(keywordFile), "UTF-8")
            val bufferedWriter = BufferedWriter(writer)
            bufferedWriter.write("$keyword;")
            bufferedWriter.newLine()
            bufferedWriter.close()

            Log.d(TAG, "唤醒词文件写入成功: $keyword")
            true
        } catch (e: IOException) {
            Log.e(TAG, "唤醒词文件写入失败: ${e.message}", e)
            false
        }
    }

    /**
     * 能力监听回调
     */
    private val aiListener = object : AiListener {
        override fun onResult(handleID: Int, outputData: List<AiResponse>, usrContext: Any?) {
            if (outputData.isNotEmpty()) {
                Log.i(TAG, "onResult: handleID=$handleID, size=${outputData.size}")
                for (response in outputData) {
                    val key = response.key
                    val bytes = response.value
                    val result = String(bytes)
                    val status = response.status

                    Log.d(TAG, "key=$key, value=$result, status=$status")

                    // 检测到唤醒词
                    if (key == "func_wake_up" || key == "func_pre_wakeup") {
                        Log.d(TAG, "检测到唤醒词: $result")
                        handleWakeUpDetected(result)
                    }
                }
            }
        }

        override fun onEvent(handleID: Int, eventType: Int, outputData: List<AiResponse>?, usrContext: Any?) {
            Log.i(TAG, "onEvent: handleID=$handleID, eventType=$eventType")
        }

        override fun onError(handleID: Int, errorCode: Int, errorMsg: String, usrContext: Any?) {
            Log.e(TAG, "onError: handleID=$handleID, errorCode=$errorCode, errorMsg=$errorMsg")
            isListeningFlag = false
            updateState(WakeUpEngine.State.ERROR)
            mainHandler.post {
                currentCallback?.onError(errorCode, errorMsg)
            }
        }
    }

    /**
     * 处理唤醒词检测
     */
    private fun handleWakeUpDetected(keyword: String) {
        Log.d(TAG, "检测到唤醒词: $keyword")

        // 停止唤醒监听
        stopListening()

        // 通知回调
        mainHandler.post {
            currentCallback?.onWakeUp(
                WakeUpEngine.WakeUpResult(
                    keyword = keyword,
                    confidence = 1f
                )
            )
        }
    }

    private fun updateState(state: WakeUpEngine.State) {
        currentState = state
        mainHandler.post {
            currentCallback?.onStateChanged(state)
        }
    }

    override suspend fun initialize(keywords: List<String>): Boolean {
        Log.d(TAG, "initialize: keywords=$keywords")
        currentKeywords = keywords.ifEmpty {
            listOf(context.getString(R.string.app_name_wake_word))
        }
        updateState(WakeUpEngine.State.IDLE)
        return true
    }

    override fun startListening(callback: WakeUpEngine.Callback) {
        Log.d(TAG, "startListening")

        if (isListeningFlag) {
            Log.w(TAG, "已经在监听中")
            return
        }

        if (!isConfigured()) {
            Log.e(TAG, "讯飞配置不完整，无法开始唤醒监听")
            callback.onError(-1, "讯飞配置不完整，请在设置中配置")
            return
        }

        // 检查 SDK 授权状态
        if (MyApplication.aikitAuthResult != 0) {
            if (MyApplication.aikitAuthResult == -1) {
                Log.d(TAG, "AIKit SDK 正在初始化，等待授权完成...")
                // 可以在这里添加等待逻辑
            } else {
                Log.e(TAG, "AIKit SDK 未授权，授权码: ${MyApplication.aikitAuthResult}")
                callback.onError(MyApplication.aikitAuthResult, "AIKit SDK 未授权")
                return
            }
        }

        currentCallback = callback
        isListeningFlag = true
        updateState(WakeUpEngine.State.LISTENING)

        // 发送启动消息
        workHandler?.sendEmptyMessage(MSG_START)
        Log.d(TAG, "唤醒服务启动请求已发送")
    }

    override fun stopListening() {
        Log.d(TAG, "stopListening")
        isListeningFlag = false

        if (isRecording.get()) {
            val msg = Message.obtain(workHandler, MSG_WRITE)
            msg.obj = AiStatus.END
            workHandler?.sendMessage(msg)
        } else {
            workHandler?.sendEmptyMessage(MSG_END)
        }

        updateState(WakeUpEngine.State.IDLE)
    }

    override fun release() {
        Log.d(TAG, "release")
        stopListening()

        // 停止录音
        if (isRecording.get()) {
            audioRecord?.stop()
            isRecording.set(false)
        }
        audioRecord?.release()
        audioRecord = null

        // 结束会话
        end()

        // 停止工作线程
        workHandler?.looper?.quitSafely()
        workThread = null
        workHandler = null

        currentCallback = null
        updateState(WakeUpEngine.State.UNINITIALIZED)
        Log.d(TAG, "已销毁")
    }

    override fun isConfigured(): Boolean {
        return IFlytekConfigProvider.isConfigured(context)
    }

    override fun isListening(): Boolean {
        return isListeningFlag
    }

    override fun getState(): WakeUpEngine.State {
        return currentState
    }

    override fun getName(): String = "讯飞唤醒"

    override fun getProviderId(): String = "iflytek"

    override fun getKeywords(): List<String> {
        return currentKeywords.ifEmpty {
            listOf(context.getString(R.string.app_name_wake_word))
        }
    }
}

