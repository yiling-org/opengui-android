package top.yling.ozx.guiagent.speech

/**
 * 语音识别器接口
 * 支持多种语音识别方案的统一抽象
 * 
 * 实现类需要处理：
 * - 音频录制
 * - 语音识别
 * - 结果回调
 * 
 * @author shanwb
 */
interface SpeechRecognizer {

    /**
     * 识别状态
     */
    enum class State {
        /** 空闲状态 */
        IDLE,
        /** 正在监听录音 */
        LISTENING,
        /** 处理识别中 */
        PROCESSING,
        /** 发生错误 */
        ERROR
    }

    /**
     * 识别结果
     * @property text 识别出的文本
     * @property isFinal 是否为最终结果（false 表示中间结果）
     * @property confidence 置信度 (0-1)，部分实现可能不支持
     */
    data class Result(
        val text: String,
        val isFinal: Boolean,
        val confidence: Float = 1f
    )

    /**
     * 识别回调接口
     */
    interface Callback {
        /**
         * 收到识别结果
         * @param result 识别结果
         */
        fun onResult(result: Result)

        /**
         * 发生错误
         * @param code 错误码
         * @param message 错误信息
         */
        fun onError(code: Int, message: String)

        /**
         * 状态变化
         * @param state 新状态
         */
        fun onStateChanged(state: State) {}
    }

    /**
     * 开始语音识别
     * @param callback 识别回调
     */
    fun startListening(callback: Callback)

    /**
     * 停止语音识别
     * 停止后会返回最终结果
     */
    fun stopListening()

    /**
     * 取消语音识别
     * 取消后不会返回结果
     */
    fun cancel()

    /**
     * 释放资源
     * 调用后该实例不可再使用
     */
    fun release()

    /**
     * 检查是否已正确配置
     * @return true 如果配置完整可以使用
     */
    fun isConfigured(): Boolean

    /**
     * 检查是否正在识别
     * @return true 如果正在监听或处理中
     */
    fun isListening(): Boolean

    /**
     * 获取识别器名称（用于 UI 显示）
     * @return 识别器名称，如 "讯飞语音"、"Google Speech" 等
     */
    fun getName(): String

    /**
     * 获取识别器提供商标识
     * @return 提供商标识，如 "iflytek"、"google" 等
     */
    fun getProviderId(): String
}

