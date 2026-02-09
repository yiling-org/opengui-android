package top.yling.ozx.guiagent.transport.api

/**
 * 连接配置
 *
 * 包含建立 Agent 连接所需的所有参数，与具体传输协议无关。
 * 不同的 [AgentConnection] 实现可以根据需要使用其中的参数。
 *
 * @property serverUrl 服务端地址（WebSocket URL、gRPC endpoint 等）
 * @property clientId 客户端标识符（设备 ID）
 * @property token 认证令牌（可选）
 * @property clientIdSuffix 客户端 ID 后缀（可选，用于区分同一设备的多个连接）
 * @property reconnectEnabled 是否启用自动重连
 * @property reconnectDelayMs 重连初始延迟（毫秒）
 * @property maxReconnectDelayMs 重连最大延迟（毫秒）
 * @property heartbeatIntervalMs 心跳间隔（毫秒），0 表示不发送心跳
 * @property connectTimeoutMs 连接超时（毫秒）
 * @property extras 扩展参数（供特定实现使用）
 *
 * @author shanwb
 */
data class ConnectionConfig(
    val serverUrl: String,
    val clientId: String,
    val token: String? = null,
    val clientIdSuffix: String? = null,
    val reconnectEnabled: Boolean = true,
    val reconnectDelayMs: Long = 3000L,
    val maxReconnectDelayMs: Long = 30000L,
    val heartbeatIntervalMs: Long = 30000L,
    val connectTimeoutMs: Long = 15000L,
    val extras: Map<String, Any?> = emptyMap()
) {
    /**
     * 实际使用的 clientId（包含后缀）
     */
    val actualClientId: String
        get() = if (!clientIdSuffix.isNullOrEmpty()) {
            "${clientId}_${clientIdSuffix}"
        } else {
            clientId
        }
}

