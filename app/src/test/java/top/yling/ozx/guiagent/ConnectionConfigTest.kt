package top.yling.ozx.guiagent

import org.junit.Assert.*
import org.junit.Test
import top.yling.ozx.guiagent.transport.api.ConnectionConfig
import top.yling.ozx.guiagent.transport.api.ConnectionState

/**
 * Transport 层配置和状态测试
 *
 * 测试 ConnectionConfig 的参数计算和默认值，
 * 以及 ConnectionState 枚举的状态属性。
 */
class ConnectionConfigTest {

    // ========== ConnectionConfig 测试 ==========

    @Test
    fun `无后缀时actualClientId应等于clientId`() {
        val config = ConnectionConfig(
            serverUrl = "ws://localhost:8080/ws",
            clientId = "device_abc123"
        )

        assertEquals("无后缀时 actualClientId 应等于 clientId",
            "device_abc123", config.actualClientId)
    }

    @Test
    fun `有后缀时actualClientId应包含后缀`() {
        val config = ConnectionConfig(
            serverUrl = "ws://localhost:8080/ws",
            clientId = "device_abc123",
            clientIdSuffix = "chat"
        )

        assertEquals("有后缀时 actualClientId 应为 clientId_suffix",
            "device_abc123_chat", config.actualClientId)
    }

    @Test
    fun `空后缀时actualClientId应等于clientId`() {
        val config = ConnectionConfig(
            serverUrl = "ws://localhost:8080/ws",
            clientId = "device_abc123",
            clientIdSuffix = ""
        )

        assertEquals("空后缀时 actualClientId 应等于 clientId",
            "device_abc123", config.actualClientId)
    }

    @Test
    fun `null后缀时actualClientId应等于clientId`() {
        val config = ConnectionConfig(
            serverUrl = "ws://localhost:8080/ws",
            clientId = "device_abc123",
            clientIdSuffix = null
        )

        assertEquals("null 后缀时 actualClientId 应等于 clientId",
            "device_abc123", config.actualClientId)
    }

    @Test
    fun `默认配置值应正确`() {
        val config = ConnectionConfig(
            serverUrl = "ws://localhost:8080/ws",
            clientId = "device_abc123"
        )

        assertNull("默认 token 应为 null", config.token)
        assertTrue("默认应启用重连", config.reconnectEnabled)
        assertEquals("默认重连延迟应为 3000ms", 3000L, config.reconnectDelayMs)
        assertEquals("默认最大重连延迟应为 30000ms", 30000L, config.maxReconnectDelayMs)
        assertEquals("默认心跳间隔应为 30000ms", 30000L, config.heartbeatIntervalMs)
        assertEquals("默认连接超时应为 15000ms", 15000L, config.connectTimeoutMs)
        assertTrue("默认 extras 应为空 Map", config.extras.isEmpty())
    }

    @Test
    fun `自定义配置值应正确`() {
        val config = ConnectionConfig(
            serverUrl = "ws://example.com/ws",
            clientId = "my_device",
            token = "auth_token_123",
            reconnectEnabled = false,
            reconnectDelayMs = 5000L,
            maxReconnectDelayMs = 60000L,
            heartbeatIntervalMs = 15000L,
            connectTimeoutMs = 10000L,
            extras = mapOf("key" to "value")
        )

        assertEquals("serverUrl 应正确", "ws://example.com/ws", config.serverUrl)
        assertEquals("clientId 应正确", "my_device", config.clientId)
        assertEquals("token 应正确", "auth_token_123", config.token)
        assertFalse("重连应禁用", config.reconnectEnabled)
        assertEquals("重连延迟应正确", 5000L, config.reconnectDelayMs)
        assertEquals("最大重连延迟应正确", 60000L, config.maxReconnectDelayMs)
        assertEquals("心跳间隔应正确", 15000L, config.heartbeatIntervalMs)
        assertEquals("连接超时应正确", 10000L, config.connectTimeoutMs)
        assertEquals("extras 应正确", "value", config.extras["key"])
    }

    @Test
    fun `ConnectionConfig的copy应正确工作`() {
        val original = ConnectionConfig(
            serverUrl = "ws://localhost:8080/ws",
            clientId = "device_123",
            token = "token_abc"
        )

        val copied = original.copy(token = "new_token")

        assertEquals("serverUrl 应不变", "ws://localhost:8080/ws", copied.serverUrl)
        assertEquals("clientId 应不变", "device_123", copied.clientId)
        assertEquals("token 应更新", "new_token", copied.token)
    }

    // ========== ConnectionState 枚举测试 ==========

    @Test
    fun `ConnectionState应包含所有预期状态`() {
        val states = ConnectionState.values()

        assertEquals("应有5个状态", 5, states.size)
        assertTrue("应包含 DISCONNECTED", states.contains(ConnectionState.DISCONNECTED))
        assertTrue("应包含 CONNECTING", states.contains(ConnectionState.CONNECTING))
        assertTrue("应包含 CONNECTED", states.contains(ConnectionState.CONNECTED))
        assertTrue("应包含 RECONNECTING", states.contains(ConnectionState.RECONNECTING))
        assertTrue("应包含 FAILED", states.contains(ConnectionState.FAILED))
    }

    @Test
    fun `CONNECTED状态isConnected应为true`() {
        assertTrue("CONNECTED.isConnected 应为 true", ConnectionState.CONNECTED.isConnected)
    }

    @Test
    fun `非CONNECTED状态isConnected应为false`() {
        assertFalse("DISCONNECTED.isConnected 应为 false", ConnectionState.DISCONNECTED.isConnected)
        assertFalse("CONNECTING.isConnected 应为 false", ConnectionState.CONNECTING.isConnected)
        assertFalse("RECONNECTING.isConnected 应为 false", ConnectionState.RECONNECTING.isConnected)
        assertFalse("FAILED.isConnected 应为 false", ConnectionState.FAILED.isConnected)
    }

    @Test
    fun `活跃状态isActive应为true`() {
        assertTrue("CONNECTING.isActive 应为 true", ConnectionState.CONNECTING.isActive)
        assertTrue("CONNECTED.isActive 应为 true", ConnectionState.CONNECTED.isActive)
        assertTrue("RECONNECTING.isActive 应为 true", ConnectionState.RECONNECTING.isActive)
    }

    @Test
    fun `非活跃状态isActive应为false`() {
        assertFalse("DISCONNECTED.isActive 应为 false", ConnectionState.DISCONNECTED.isActive)
        assertFalse("FAILED.isActive 应为 false", ConnectionState.FAILED.isActive)
    }

    @Test
    fun `状态名称应正确`() {
        assertEquals("DISCONNECTED", ConnectionState.DISCONNECTED.name)
        assertEquals("CONNECTING", ConnectionState.CONNECTING.name)
        assertEquals("CONNECTED", ConnectionState.CONNECTED.name)
        assertEquals("RECONNECTING", ConnectionState.RECONNECTING.name)
        assertEquals("FAILED", ConnectionState.FAILED.name)
    }
}

