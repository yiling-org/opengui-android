package top.yling.ozx.guiagent.speech

/**
 * 语音唤醒引擎接口
 * 支持多种语音唤醒方案的统一抽象
 * 
 * 实现类需要处理：
 * - 持续监听音频
 * - 唤醒词检测
 * - 唤醒回调
 * 
 * @author shanwb
 */
interface WakeUpEngine {

    /**
     * 唤醒引擎状态
     */
    enum class State {
        /** 未初始化 */
        UNINITIALIZED,
        /** 已初始化，空闲 */
        IDLE,
        /** 正在监听唤醒词 */
        LISTENING,
        /** 发生错误 */
        ERROR
    }

    /**
     * 唤醒结果
     * @property keyword 检测到的唤醒词
     * @property confidence 置信度 (0-1)
     */
    data class WakeUpResult(
        val keyword: String,
        val confidence: Float = 1f
    )

    /**
     * 唤醒回调接口
     */
    interface Callback {
        /**
         * 检测到唤醒词
         * @param result 唤醒结果
         */
        fun onWakeUp(result: WakeUpResult)

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
     * 初始化唤醒引擎
     * @param keywords 唤醒词列表
     * @return true 如果初始化成功
     */
    suspend fun initialize(keywords: List<String>): Boolean

    /**
     * 开始监听唤醒词
     * @param callback 唤醒回调
     */
    fun startListening(callback: Callback)

    /**
     * 停止监听
     */
    fun stopListening()

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
     * 检查是否正在监听
     * @return true 如果正在监听唤醒词
     */
    fun isListening(): Boolean

    /**
     * 获取当前状态
     * @return 当前状态
     */
    fun getState(): State

    /**
     * 获取引擎名称（用于 UI 显示）
     * @return 引擎名称，如 "讯飞唤醒"、"Porcupine" 等
     */
    fun getName(): String

    /**
     * 获取引擎提供商标识
     * @return 提供商标识，如 "iflytek"、"picovoice" 等
     */
    fun getProviderId(): String

    /**
     * 获取支持的唤醒词列表
     * @return 当前配置的唤醒词列表
     */
    fun getKeywords(): List<String>
}

