package top.yling.ozx.guiagent.speech.impl.iflytek

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.iflytek.sparkchain.core.asr.ASR
import com.iflytek.sparkchain.core.asr.AsrCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.speech.SpeechRecognizer
import top.yling.ozx.guiagent.util.IFlytekConfigProvider

/**
 * 讯飞语音识别实现
 * 基于讯飞 SparkChain SDK 的大模型语音识别
 * 
 * 特点：
 * - 支持实时流式识别
 * - 支持中文普通话
 * - 需要网络连接
 * 
 * 配置要求：
 * - 需要在讯飞控制台开通"大模型识别"服务
 * - 需要配置 App ID、API Key、API Secret
 * 
 * @author shanwb
 */
class IFlytekSpeechRecognizer(private val context: Context) : SpeechRecognizer {

    companion object {
        private const val TAG = "IFlytekASR"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE = 1280  // 40ms audio data
    }

    private var asr: ASR? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var recordingScope: CoroutineScope? = null
    private var isListeningFlag = false
    private var currentCallback: SpeechRecognizer.Callback? = null
    private var accumulatedText = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var finalResultTimeout: Runnable? = null
    private var isInitialized = false

    init {
        prepareResources()
    }

    private fun prepareResources() {
        if (!isConfigured()) {
            Log.w(TAG, "讯飞配置不完整，跳过资源预创建")
            return
        }

        try {
            // 创建 ASR 实例
            if (asr == null) {
                asr = ASR("zh_cn", "slm", "mandarin")
                setRecognizerParams()
                Log.d(TAG, "ASR 实例已创建")
            }

            // 预创建 AudioRecord
            if (audioRecord == null) {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBufferSize, BUFFER_SIZE * 4)
                )

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "AudioRecord 预创建成功")
                    isInitialized = true
                } else {
                    Log.e(TAG, "AudioRecord 预创建失败")
                    audioRecord?.release()
                    audioRecord = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "预创建资源失败: ${e.message}", e)
        }
    }

    private fun setRecognizerParams() {
        asr?.apply {
            vadEos(1500)  // VAD 静音检测时长
            ptt(true)     // 启用标点
            nunum(true)   // 数字转换
            vinfo(false)  // 不返回音频信息
            Log.d(TAG, "参数设置完成")
        }
    }

    private val asrCallbacks = object : AsrCallbacks {
        override fun onResult(asrResult: ASR.ASRResult?, usrContext: Any?) {
            if (asrResult != null) {
                try {
                    val text = asrResult.getBestMatchText() ?: ""
                    val status = asrResult.getStatus()

                    Log.d(TAG, "识别结果: '$text', status: $status")

                    if (text.isNotEmpty()) {
                        when (status) {
                            0 -> accumulatedText.clear()  // 开始新句
                            1, 2 -> {}  // 中间/结束
                        }
                        accumulatedText.append(text)

                        val currentText = accumulatedText.toString()
                        val isFinal = status == 2

                        // 回调结果
                        mainHandler.post {
                            currentCallback?.onResult(
                                SpeechRecognizer.Result(
                                    text = currentText,
                                    isFinal = isFinal
                                )
                            )
                        }

                        if (isFinal) {
                            Log.d(TAG, "最终结果: $currentText")
                            // 清除超时任务
                            finalResultTimeout?.let {
                                mainHandler.removeCallbacks(it)
                                Log.d(TAG, "已取消超时回调")
                            }
                            finalResultTimeout = null
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析结果失败: ${e.message}", e)
                }
            }
        }

        override fun onError(asrError: ASR.ASRError?, usrContext: Any?) {
            isListeningFlag = false

            val code: Int
            val message: String

            if (asrError != null) {
                code = asrError.code
                val msg = asrError.errMsg

                message = when (code) {
                    11201 -> "授权失败 - 请在讯飞控制台开通'大模型识别'服务"
                    18504 -> "APIKey 为空 - 请配置讯飞参数"
                    18505 -> "APISecret 为空 - 请配置讯飞参数"
                    18700 -> "网络错误 - 请检查网络连接"
                    else -> msg
                }

                Log.e(TAG, "识别错误 [code=$code]: $message")
            } else {
                code = -1
                message = "未知错误"
                Log.e(TAG, "识别错误: 未知错误")
            }

            stopRecording()

            // 回调错误
            mainHandler.post {
                currentCallback?.onError(code, message)
                currentCallback?.onStateChanged(SpeechRecognizer.State.ERROR)
            }
        }
    }

    override fun startListening(callback: SpeechRecognizer.Callback) {
        Log.d(TAG, "startListening 被调用")

        if (isListeningFlag) {
            Log.w(TAG, "已经在监听中")
            return
        }

        if (!isConfigured()) {
            Log.e(TAG, "讯飞配置不完整，无法开始识别")
            callback.onError(-1, "讯飞配置不完整，请在设置中配置")
            return
        }

        // 创建新的 CoroutineScope
        recordingScope?.cancel()
        recordingScope = CoroutineScope(Dispatchers.IO)

        // 如果资源未初始化，尝试预创建
        if (!isInitialized || asr == null) {
            prepareResources()
            if (!isInitialized || asr == null) {
                Log.e(TAG, "资源未初始化，无法开始录音")
                callback.onError(-1, "语音识别初始化失败")
                return
            }
        }

        currentCallback = callback
        accumulatedText.clear()

        // 确保 ASR 处于停止状态
        try {
            asr?.stop(false)
            Log.d(TAG, "确保 ASR 已停止")
        } catch (e: Exception) {
            Log.w(TAG, "停止 ASR 异常: ${e.message}")
        }

        // 重新设置参数
        setRecognizerParams()
        asr?.registerCallbacks(asrCallbacks)

        // 启动识别
        val ret = asr?.start(null)
        if (ret != 0) {
            Log.e(TAG, "启动识别失败: $ret")
            callback.onError(ret ?: -1, "启动识别失败")
            return
        }

        isListeningFlag = true
        callback.onStateChanged(SpeechRecognizer.State.LISTENING)
        Log.d(TAG, "识别启动成功")

        // 开始录音
        startRecordingAndSending()
    }

    private fun startRecordingAndSending() {
        try {
            // 检查 AudioRecord 状态
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 未初始化，尝试重新创建")

                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBufferSize, BUFFER_SIZE * 4)
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord 初始化失败")
                    return
                }
            }

            audioRecord?.startRecording()
            Log.d(TAG, "开始录音")

            val scope = recordingScope
            if (scope != null) {
                recordingJob = scope.launch {
                    val buffer = ByteArray(BUFFER_SIZE)

                    while (isActive && isListeningFlag) {
                        val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0

                        if (readSize > 0) {
                            val ret = asr?.write(buffer.copyOf(readSize))
                            if (ret != 0) {
                                Log.w(TAG, "发送音频失败: $ret")
                            }
                        }

                        Thread.sleep(40)
                    }

                    Log.d(TAG, "录音线程结束")
                }
            } else {
                Log.e(TAG, "recordingScope 为 null，无法启动录音")
            }

        } catch (e: Exception) {
            Log.e(TAG, "启动录音失败: ${e.message}", e)
        }
    }

    private fun stopRecording() {
        try {
            recordingJob?.cancel()
            audioRecord?.stop()
            Log.d(TAG, "录音已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止录音异常: ${e.message}", e)
        }
    }

    override fun stopListening() {
        Log.d(TAG, "stopListening 被调用")

        if (isListeningFlag) {
            Log.d(TAG, "停止监听")
            stopRecording()
            asr?.stop(false)
            isListeningFlag = false

            currentCallback?.onStateChanged(SpeechRecognizer.State.PROCESSING)

            // 设置超时回调
            finalResultTimeout?.let { mainHandler.removeCallbacks(it) }
            finalResultTimeout = Runnable {
                Log.d(TAG, "超时未收到最终结果，返回当前文本: ${accumulatedText}")
                val currentText = accumulatedText.toString()
                mainHandler.post {
                    currentCallback?.onResult(
                        SpeechRecognizer.Result(
                            text = currentText,
                            isFinal = true
                        )
                    )
                    currentCallback?.onStateChanged(SpeechRecognizer.State.IDLE)
                }
            }
            mainHandler.postDelayed(finalResultTimeout!!, 2000)
        } else {
            val finalText = accumulatedText.toString()
            Log.d(TAG, "已停止，返回: $finalText")
            currentCallback?.onResult(
                SpeechRecognizer.Result(
                    text = finalText,
                    isFinal = true
                )
            )
            currentCallback?.onStateChanged(SpeechRecognizer.State.IDLE)
        }
    }

    override fun cancel() {
        Log.d(TAG, "cancel 被调用")
        finalResultTimeout?.let { mainHandler.removeCallbacks(it) }
        finalResultTimeout = null
        stopRecording()
        try {
            asr?.stop(false)
        } catch (e: Exception) {
            Log.w(TAG, "停止 ASR 异常: ${e.message}")
        }
        isListeningFlag = false
        accumulatedText.clear()
        currentCallback?.onStateChanged(SpeechRecognizer.State.IDLE)
        currentCallback = null
    }

    override fun release() {
        Log.d(TAG, "release 被调用")
        finalResultTimeout?.let { mainHandler.removeCallbacks(it) }
        finalResultTimeout = null
        stopRecording()
        recordingScope?.cancel()
        recordingScope = null
        audioRecord?.release()
        audioRecord = null
        try {
            asr?.stop(false)
        } catch (e: Exception) {
            Log.w(TAG, "停止 ASR 异常: ${e.message}")
        }
        asr = null
        isInitialized = false
        currentCallback = null
        Log.d(TAG, "已销毁")
    }

    override fun isConfigured(): Boolean {
        return IFlytekConfigProvider.isConfigured(context)
    }

    override fun isListening(): Boolean {
        return isListeningFlag
    }

    override fun getName(): String = "讯飞语音"

    override fun getProviderId(): String = "iflytek"
}

