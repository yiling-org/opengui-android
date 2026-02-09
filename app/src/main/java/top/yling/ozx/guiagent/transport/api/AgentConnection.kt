package top.yling.ozx.guiagent.transport.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Agent 连接抽象接口
 *
 * 定义了客户端与服务端之间的通信协议抽象，与具体传输层实现无关。
 * 支持多种传输协议实现（WebSocket、gRPC、HTTP Long Polling 等）。
 *
 * ## 使用示例
 *
 * ```kotlin
 * // 创建连接
 * val connection = ConnectionFactory.create(context, ConnectionFactory.Type.WEBSOCKET)
 *
 * // 设置监听器
 * connection.setListener(object : ConnectionListener {
 *     override fun onStateChanged(state: ConnectionState) { ... }
 *     override fun onMessage(message: String) { ... }
 *     override fun onError(error: String, throwable: Throwable?) { ... }
 * })
 *
 * // 连接
 * val config = ConnectionConfig(
 *     serverUrl = "ws://example.com/ws",
 *     clientId = "device_123"
 * )
 * connection.connect(config)
 *
 * // 发送消息
 * connection.send("{\"type\":\"agent\",\"data\":{...}}")
 *
 * // 断开连接
 * connection.disconnect()
 * ```
 *
 * ## 实现要求
 *
 * - 线程安全：所有方法必须支持从任意线程调用
 * - 自动重连：当 [ConnectionConfig.reconnectEnabled] 为 true 时，应自动处理重连
 * - 心跳维护：当 [ConnectionConfig.heartbeatIntervalMs] > 0 时，应自动发送心跳
 * - 状态管理：正确维护 [connectionState] 的状态转换
 *
 * @author shanwb
 */
interface AgentConnection {

    /**
     * 当前连接状态（响应式）
     *
     * 通过 StateFlow 提供实时的连接状态，支持 UI 层直接观察。
     */
    val connectionState: StateFlow<ConnectionState>

    /**
     * 当前连接配置（连接后可用）
     */
    val currentConfig: ConnectionConfig?

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean

    /**
     * 建立连接
     *
     * 使用指定配置建立与服务端的连接。如果当前已连接，会先断开再重新连接。
     *
     * @param config 连接配置
     */
    fun connect(config: ConnectionConfig)

    /**
     * 断开连接
     *
     * 主动断开与服务端的连接，不会触发自动重连。
     */
    fun disconnect()

    /**
     * 发送消息到服务端
     *
     * @param message 要发送的消息文本（通常为 JSON 字符串）
     * @return 是否成功发送（仅表示写入传输层缓冲区，不保证服务端收到）
     */
    fun send(message: String): Boolean

    /**
     * 设置连接事件监听器
     *
     * @param listener 监听器实例，传 null 可清除监听器
     */
    fun setListener(listener: ConnectionListener?)

    /**
     * 释放连接资源
     *
     * 断开连接并释放所有资源（线程、内存等），调用后不应再使用此实例。
     */
    fun release()

    /**
     * 获取连接实现名称
     *
     * @return 实现名称，如 "WebSocket"、"gRPC"
     */
    fun getName(): String

    /**
     * 获取连接实现标识
     *
     * @return 实现标识，如 "websocket"、"grpc"
     */
    fun getProviderId(): String
}

