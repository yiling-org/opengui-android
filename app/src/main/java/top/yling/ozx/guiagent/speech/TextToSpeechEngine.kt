package top.yling.ozx.guiagent.speech

/**
 * 语音合成（TTS）引擎接口
 * 支持多种语音合成方案的统一抽象
 * 
 * 实现类需要处理：
 * - 文本转语音
 * - 音频播放
 * - 播放状态回调
 * 
 * @author shanwb
 */
interface TextToSpeechEngine {

    /**
     * TTS 引擎状态
     */
    enum class State {
        /** 未初始化 */
        UNINITIALIZED,
        /** 初始化中 */
        INITIALIZING,
        /** 已就绪 */
        READY,
        /** 正在播放 */
        SPEAKING,
        /** 发生错误 */
        ERROR
    }

    /**
     * TTS 回调接口
     */
    interface Callback {
        /**
         * 开始播放
         * @param utteranceId 播放标识
         */
        fun onStart(utteranceId: String) {}

        /**
         * 播放完成
         * @param utteranceId 播放标识
         */
        fun onDone(utteranceId: String) {}

        /**
         * 播放错误
         * @param utteranceId 播放标识
         * @param errorCode 错误码
         * @param errorMessage 错误信息
         */
        fun onError(utteranceId: String, errorCode: Int, errorMessage: String) {}

        /**
         * 状态变化
         * @param state 新状态
         */
        fun onStateChanged(state: State) {}
    }

    /**
     * 初始化 TTS 引擎
     * @param callback 初始化完成回调（可选）
     */
    fun initialize(callback: Callback? = null)

    /**
     * 播放文本
     * @param text 要播放的文本
     * @param callback 播放回调（可选）
     * @return 播放标识（utteranceId），可用于跟踪播放状态
     */
    fun speak(text: String, callback: Callback? = null): String

    /**
     * 将文本添加到播放队列
     * @param text 要播放的文本
     * @param callback 播放回调（可选）
     * @return 播放标识（utteranceId）
     */
    fun speakQueued(text: String, callback: Callback? = null): String

    /**
     * 停止当前播放
     */
    fun stop()

    /**
     * 释放资源
     * 调用后该实例不可再使用
     */
    fun release()

    /**
     * 检查是否已就绪
     * @return true 如果 TTS 引擎已初始化完成可以使用
     */
    fun isReady(): Boolean

    /**
     * 检查是否正在播放
     * @return true 如果正在播放语音
     */
    fun isSpeaking(): Boolean

    /**
     * 获取当前状态
     * @return 当前状态
     */
    fun getState(): State

    /**
     * 设置语速
     * @param rate 语速，1.0 为正常速度，范围通常为 0.5-2.0
     */
    fun setSpeechRate(rate: Float)

    /**
     * 设置音调
     * @param pitch 音调，1.0 为正常音调，范围通常为 0.5-2.0
     */
    fun setPitch(pitch: Float)

    /**
     * 获取引擎名称（用于 UI 显示）
     * @return 引擎名称，如 "系统 TTS"、"讯飞语音" 等
     */
    fun getName(): String

    /**
     * 获取引擎提供商标识
     * @return 提供商标识，如 "android"、"iflytek" 等
     */
    fun getProviderId(): String
}

