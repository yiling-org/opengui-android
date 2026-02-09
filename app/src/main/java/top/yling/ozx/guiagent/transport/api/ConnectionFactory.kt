package top.yling.ozx.guiagent.transport.api

import android.content.Context
import android.util.Log
import top.yling.ozx.guiagent.transport.websocket.WebSocketAgentConnection

/**
 * Agent 连接工厂
 *
 * 创建不同传输协议的 [AgentConnection] 实例。
 * 支持通过配置或代码选择不同的传输协议实现。
 *
 * ## 扩展新协议
 *
 * 1. 创建新的 [AgentConnection] 实现（如 `GrpcAgentConnection`）
 * 2. 在 [Type] 枚举中添加新类型
 * 3. 在 [create] 方法中添加创建逻辑
 *
 * @author shanwb
 */
object ConnectionFactory {

    private const val TAG = "ConnectionFactory"

    /**
     * 传输协议类型
     */
    enum class Type {
        /** WebSocket 协议（默认） */
        WEBSOCKET,

        /** gRPC 协议（扩展预留） */
        GRPC,

        /** HTTP Long Polling（扩展预留） */
        HTTP_POLLING
    }

    /**
     * 创建 AgentConnection 实例
     *
     * @param context Android 上下文
     * @param type 传输协议类型
     * @return AgentConnection 实例
     * @throws IllegalArgumentException 如果指定的协议类型未实现
     */
    fun create(context: Context, type: Type = Type.WEBSOCKET): AgentConnection {
        Log.i(TAG, "创建 AgentConnection: type=$type")

        return when (type) {
            Type.WEBSOCKET -> WebSocketAgentConnection()

            Type.GRPC -> {
                // 扩展点：gRPC 实现
                throw IllegalArgumentException(
                    "gRPC 传输协议尚未实现。" +
                    "请添加 GrpcAgentConnection 实现并在此处注册。"
                )
            }

            Type.HTTP_POLLING -> {
                // 扩展点：HTTP Long Polling 实现
                throw IllegalArgumentException(
                    "HTTP Long Polling 传输协议尚未实现。" +
                    "请添加 HttpPollingAgentConnection 实现并在此处注册。"
                )
            }
        }
    }

    /**
     * 获取所有已注册的传输协议类型
     *
     * @return 可用的协议类型列表
     */
    fun getAvailableTypes(): List<Type> {
        return listOf(Type.WEBSOCKET) // 目前仅 WebSocket 可用
    }
}

