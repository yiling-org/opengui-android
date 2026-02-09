package top.yling.ozx.guiagent.transport.api

/**
 * 连接状态枚举
 *
 * 定义了 Agent 连接的所有可能状态，支持不同传输协议实现。
 *
 * 状态转换图：
 * ```
 * DISCONNECTED → CONNECTING → CONNECTED
 *       ↑              ↓          ↓
 *       ↑         FAILED     RECONNECTING
 *       ↑           ↓              ↓
 *       ←←←←←←←←←←←←←←←←←←←←←←←←
 * ```
 *
 * @author shanwb
 */
enum class ConnectionState {
    /** 未连接 */
    DISCONNECTED,

    /** 正在连接 */
    CONNECTING,

    /** 已连接 */
    CONNECTED,

    /** 正在重连（连接断开后自动重连中） */
    RECONNECTING,

    /** 连接失败 */
    FAILED;

    /** 是否处于已连接状态 */
    val isConnected: Boolean get() = this == CONNECTED

    /** 是否处于活跃状态（连接中或已连接） */
    val isActive: Boolean get() = this == CONNECTING || this == CONNECTED || this == RECONNECTING
}

