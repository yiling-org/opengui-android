package top.yling.ozx.guiagent.speech.impl.android

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import top.yling.ozx.guiagent.speech.TextToSpeechEngine
import java.util.Locale
import java.util.UUID

/**
 * Android 系统 TTS 引擎实现
 * 使用 Android 内置的 TextToSpeech API
 * 
 * 特点：
 * - 无需额外配置，开箱即用
 * - 支持多种语言（取决于设备）
 * - 离线可用（取决于设备语音包）
 * 
 * @author shanwb
 */
class AndroidTTSEngine(private val context: Context) : TextToSpeechEngine {

    companion object {
        private const val TAG = "AndroidTTS"
    }

    private var tts: TextToSpeech? = null
    private var currentState = TextToSpeechEngine.State.UNINITIALIZED
    private var initCallback: TextToSpeechEngine.Callback? = null
    private val utteranceCallbacks = mutableMapOf<String, TextToSpeechEngine.Callback>()
    private var speechRate = 1.0f
    private var pitch = 1.0f

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "onStart: $utteranceId")
            utteranceId?.let { id ->
                updateState(TextToSpeechEngine.State.SPEAKING)
                utteranceCallbacks[id]?.onStart(id)
            }
        }

        override fun onDone(utteranceId: String?) {
            Log.d(TAG, "onDone: $utteranceId")
            utteranceId?.let { id ->
                updateState(TextToSpeechEngine.State.READY)
                utteranceCallbacks[id]?.onDone(id)
                utteranceCallbacks.remove(id)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            Log.e(TAG, "onError: $utteranceId")
            utteranceId?.let { id ->
                updateState(TextToSpeechEngine.State.ERROR)
                utteranceCallbacks[id]?.onError(id, -1, "TTS 播放错误")
                utteranceCallbacks.remove(id)
            }
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.e(TAG, "onError: $utteranceId, errorCode=$errorCode")
            utteranceId?.let { id ->
                updateState(TextToSpeechEngine.State.ERROR)
                utteranceCallbacks[id]?.onError(id, errorCode, "TTS 播放错误: $errorCode")
                utteranceCallbacks.remove(id)
            }
        }
    }

    override fun initialize(callback: TextToSpeechEngine.Callback?) {
        Log.d(TAG, "initialize")
        initCallback = callback
        updateState(TextToSpeechEngine.State.INITIALIZING)

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS 不支持中文，尝试使用默认语言")
                    // 尝试使用默认语言
                    tts?.setLanguage(Locale.getDefault())
                }

                tts?.setOnUtteranceProgressListener(utteranceListener)
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)

                updateState(TextToSpeechEngine.State.READY)
                Log.d(TAG, "TTS 初始化成功")
                initCallback?.onStateChanged(TextToSpeechEngine.State.READY)
            } else {
                Log.e(TAG, "TTS 初始化失败: $status")
                updateState(TextToSpeechEngine.State.ERROR)
                initCallback?.onError("init", status, "TTS 初始化失败")
            }
        }
    }

    override fun speak(text: String, callback: TextToSpeechEngine.Callback?): String {
        val utteranceId = UUID.randomUUID().toString()

        if (text.isBlank()) {
            Log.w(TAG, "播放文本为空，跳过")
            callback?.onDone(utteranceId)
            return utteranceId
        }

        if (!isReady()) {
            Log.w(TAG, "TTS 未就绪，无法播放")
            callback?.onError(utteranceId, -1, "TTS 未就绪")
            return utteranceId
        }

        callback?.let { utteranceCallbacks[utteranceId] = it }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "speak 失败: $result")
            callback?.onError(utteranceId, result ?: -1, "播放失败")
            utteranceCallbacks.remove(utteranceId)
        } else {
            Log.d(TAG, "播放 TTS: $text (id=$utteranceId)")
        }

        return utteranceId
    }

    override fun speakQueued(text: String, callback: TextToSpeechEngine.Callback?): String {
        val utteranceId = UUID.randomUUID().toString()

        if (text.isBlank()) {
            Log.w(TAG, "播放文本为空，跳过")
            callback?.onDone(utteranceId)
            return utteranceId
        }

        if (!isReady()) {
            Log.w(TAG, "TTS 未就绪，无法播放")
            callback?.onError(utteranceId, -1, "TTS 未就绪")
            return utteranceId
        }

        callback?.let { utteranceCallbacks[utteranceId] = it }

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        val result = tts?.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "speakQueued 失败: $result")
            callback?.onError(utteranceId, result ?: -1, "播放失败")
            utteranceCallbacks.remove(utteranceId)
        } else {
            Log.d(TAG, "添加到播放队列: $text (id=$utteranceId)")
        }

        return utteranceId
    }

    override fun stop() {
        Log.d(TAG, "stop")
        tts?.stop()
        utteranceCallbacks.clear()
        if (currentState == TextToSpeechEngine.State.SPEAKING) {
            updateState(TextToSpeechEngine.State.READY)
        }
    }

    override fun release() {
        Log.d(TAG, "release")
        tts?.stop()
        tts?.shutdown()
        tts = null
        utteranceCallbacks.clear()
        updateState(TextToSpeechEngine.State.UNINITIALIZED)
        Log.d(TAG, "TTS 已销毁")
    }

    override fun isReady(): Boolean {
        return currentState == TextToSpeechEngine.State.READY ||
                currentState == TextToSpeechEngine.State.SPEAKING
    }

    override fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    override fun getState(): TextToSpeechEngine.State {
        return currentState
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.1f, 4.0f)
        tts?.setSpeechRate(speechRate)
        Log.d(TAG, "设置语速: $speechRate")
    }

    override fun setPitch(pitch: Float) {
        this.pitch = pitch.coerceIn(0.1f, 4.0f)
        tts?.setPitch(this.pitch)
        Log.d(TAG, "设置音调: ${this.pitch}")
    }

    override fun getName(): String = "系统 TTS"

    override fun getProviderId(): String = "android"

    private fun updateState(state: TextToSpeechEngine.State) {
        currentState = state
    }
}

