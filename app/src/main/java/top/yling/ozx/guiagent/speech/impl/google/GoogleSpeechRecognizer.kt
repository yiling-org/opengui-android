package top.yling.ozx.guiagent.speech.impl.google

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.util.Log
import top.yling.ozx.guiagent.speech.SpeechRecognizer as AppSpeechRecognizer

/**
 * Google 语音识别实现
 * 基于 Android 原生 SpeechRecognizer API
 * 
 * 特点：
 * - 支持中文普通话识别
 * - 无需额外 API Key（使用设备绑定的 Google 服务）
 * - 支持离线识别（需下载语言包）
 * - 自动处理权限请求
 * 
 * @author shanwb
 */
class GoogleSpeechRecognizer(private val context: Context) : AppSpeechRecognizer {

    companion object {
        private const val TAG = "GoogleASR"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var currentCallback: AppSpeechRecognizer.Callback? = null
    private var isListeningFlag = false
    private var accumulatedText = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "准备就绪，可以开始说话")
            isListeningFlag = true
            mainHandler.post {
                currentCallback?.onStateChanged(AppSpeechRecognizer.State.LISTENING)
            }
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "开始说话")
        }

        override fun onRmsChanged(rmsdB: Float) {
            // 音量变化回调，可用于 UI 动画
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // 音频缓冲区接收
        }

        override fun onEndOfSpeech() {
            Log.d(TAG, "说话结束")
            isListeningFlag = false
            mainHandler.post {
                currentCallback?.onStateChanged(AppSpeechRecognizer.State.PROCESSING)
            }
        }

        override fun onError(error: Int) {
            isListeningFlag = false
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "未能识别语音"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器正忙"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "说话超时"
                else -> "未知错误: $error"
            }
            Log.e(TAG, "识别错误: $message")
            mainHandler.post {
                currentCallback?.onError(error, message)
                currentCallback?.onStateChanged(AppSpeechRecognizer.State.ERROR)
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "识别结果: $text")
                accumulatedText.append(text)
                val finalText = accumulatedText.toString()
                mainHandler.post {
                    currentCallback?.onResult(
                        AppSpeechRecognizer.Result(
                            text = finalText,
                            isFinal = true,
                            confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.get(0) ?: 1f
                        )
                    )
                    currentCallback?.onStateChanged(AppSpeechRecognizer.State.IDLE)
                }
            } else {
                mainHandler.post {
                    currentCallback?.onError(-1, "无识别结果")
                    currentCallback?.onStateChanged(AppSpeechRecognizer.State.ERROR)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "中间结果: $text")
                mainHandler.post {
                    currentCallback?.onResult(
                        AppSpeechRecognizer.Result(
                            text = text,
                            isFinal = false
                        )
                    )
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            Log.d(TAG, "事件: $eventType")
        }
    }

    override fun startListening(callback: AppSpeechRecognizer.Callback) {
        Log.d(TAG, "startListening 被调用")

        if (isListeningFlag) {
            Log.w(TAG, "已经在监听中")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "设备不支持语音识别")
            callback.onError(-1, "设备不支持 Google 语音识别")
            return
        }

        currentCallback = callback
        accumulatedText.clear()

        // 创建 SpeechRecognizer 实例
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
        }

        // 配置识别意图
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
        Log.d(TAG, "语音识别启动")
    }

    override fun stopListening() {
        Log.d(TAG, "stopListening 被调用")
        if (isListeningFlag) {
            isListeningFlag = false
            speechRecognizer?.stopListening()
        }
    }

    override fun cancel() {
        Log.d(TAG, "cancel 被调用")
        isListeningFlag = false
        accumulatedText.clear()
        speechRecognizer?.cancel()
        currentCallback?.onStateChanged(AppSpeechRecognizer.State.IDLE)
        currentCallback = null
    }

    override fun release() {
        Log.d(TAG, "release 被调用")
        isListeningFlag = false
        accumulatedText.clear()
        speechRecognizer?.destroy()
        speechRecognizer = null
        currentCallback = null
    }

    override fun isConfigured(): Boolean {
        // Google 语音识别不需要额外配置，只要设备支持即可
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun isListening(): Boolean {
        return isListeningFlag
    }

    override fun getName(): String = "Google 语音"

    override fun getProviderId(): String = "google"
}
