package top.yling.ozx.guiagent.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * TTS 语音播放工具类
 * 用于播放文本转语音
 */
class TtsHelper(private val context: Context) {
    private val TAG = "TtsHelper"
    
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    
    init {
        initializeTTS()
    }
    
    /**
     * 初始化TTS
     */
    private fun initializeTTS() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS不支持中文")
                } else {
                    isTtsReady = true
                    Log.d(TAG, "TTS初始化成功")
                }
            } else {
                Log.e(TAG, "TTS初始化失败")
            }
        }
    }
    
    /**
     * 播放TTS语音
     * @param text 要播放的文本
     */
    fun speak(text: String) {
        if (text.isBlank()) {
            Log.w(TAG, "播放文本为空，跳过")
            return
        }
        
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d(TAG, "播放TTS: $text")
        } else {
            Log.w(TAG, "TTS未就绪，无法播放")
        }
    }
    
    /**
     * 停止播放
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * 销毁资源
     */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
        Log.d(TAG, "TTS已销毁")
    }
    
    /**
     * 检查TTS是否就绪
     */
    fun isReady(): Boolean = isTtsReady
}

