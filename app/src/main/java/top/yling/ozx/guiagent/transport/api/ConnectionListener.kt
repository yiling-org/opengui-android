package top.yling.ozx.guiagent.transport.api

/**
 * 连接事件监听器
 *
 * 用于接收 [AgentConnection] 的连接状态变化、消息接收和错误事件。
 * 调用方通过实现此接口来处理连接层的事件通知。
 *
 * @author shanwb
 */
interface ConnectionListener {
    /**
     * 连接状态发生变化
     *
     * @param state 新的连接状态
     */
    fun onStateChanged(state: ConnectionState)

    /**
     * 收到服务端消息
     *
     * @param message 原始消息文本（JSON 字符串）
     */
    fun onMessage(message: String)

    /**
     * 连接发生错误
     *
     * @param error 错误描述信息
     * @param throwable 异常对象（可选）
     */
    fun onError(error: String, throwable: Throwable? = null)
}

