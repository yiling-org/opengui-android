package top.yling.ozx.guiagent.transport.websocket

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import top.yling.ozx.guiagent.transport.api.AgentConnection
import top.yling.ozx.guiagent.transport.api.ConnectionConfig
import top.yling.ozx.guiagent.transport.api.ConnectionListener
import top.yling.ozx.guiagent.transport.api.ConnectionState
import java.util.concurrent.TimeUnit

/**
 * 基于 WebSocket 的 AgentConnection 实现
 *
 * 使用 OkHttp WebSocket 与服务端建立长连接，提供：
 * - 自动重连（指数退避策略）
 * - 心跳维护（防止连接超时断开）
 * - 连接状态管理（通过 StateFlow 实时通知）
 *
 * ## 线程安全
 *
 * 所有公开方法均可从任意线程调用：
 * - 状态修改通过 MutableStateFlow 保证原子性
 * - 心跳和重连通过主线程 Handler 调度
 * - OkHttp WebSocket 自身是线程安全的
 *
 * @author shanwb
 */
class WebSocketAgentConnection : AgentConnection {

    companion object {
        private const val TAG = "WsAgentConnection"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val PING_INTERVAL_SECONDS = 15L
    }

    // ==================== 连接状态 ====================

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override var currentConfig: ConnectionConfig? = null
        private set

    private var listener: ConnectionListener? = null

    // ==================== WebSocket 相关 ====================

    private var webSocket: WebSocket? = null
    private var shouldReconnect = false
    private var reconnectAttempts = 0
    private var lastPongTime = System.currentTimeMillis()

    // ==================== 心跳 ====================

    private val mainHandler = Handler(Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null

    // ==================== OkHttp 客户端 ====================

    private var httpClient: OkHttpClient? = null

    /**
     * 根据配置创建或复用 OkHttpClient
     */
    private fun getOrCreateClient(config: ConnectionConfig): OkHttpClient {
        return httpClient ?: OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build().also {
                httpClient = it
            }
    }

    // ==================== WebSocket 监听器 ====================

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.i(TAG, "WebSocket 连接成功: ${currentConfig?.serverUrl}")
            reconnectAttempts = 0
            lastPongTime = System.currentTimeMillis()
            updateState(ConnectionState.CONNECTED)
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "收到消息: ${text.take(200)}${if (text.length > 200) "..." else ""}")
            lastPongTime = System.currentTimeMillis()
            listener?.onMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket 正在关闭: $code - $reason")
            webSocket.close(NORMAL_CLOSURE_STATUS, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.i(TAG, "WebSocket 已关闭: $code - $reason")
            stopHeartbeat()
            updateState(ConnectionState.DISCONNECTED)
            if (shouldReconnect) {
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket 连接失败: ${t.message}", t)
            stopHeartbeat()
            updateState(ConnectionState.FAILED)
            listener?.onError(t.message ?: "Unknown error", t)
            if (shouldReconnect) {
                scheduleReconnect()
            }
        }
    }

    // ==================== AgentConnection 接口实现 ====================

    override fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    override fun connect(config: ConnectionConfig) {
        if (isConnected()) {
            Log.w(TAG, "已经连接，先断开再重连")
            disconnectInternal(triggerReconnect = false)
        }

        currentConfig = config
        shouldReconnect = config.reconnectEnabled
        reconnectAttempts = 0

        updateState(ConnectionState.CONNECTING)

        val url = buildUrl(config)
        Log.i(TAG, "正在连接 WebSocket: ${maskSensitiveInfo(url)} (clientId: ${config.actualClientId})")

        val client = getOrCreateClient(config)
        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, webSocketListener)
    }

    override fun disconnect() {
        disconnectInternal(triggerReconnect = false)
    }

    override fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    override fun setListener(listener: ConnectionListener?) {
        this.listener = listener
    }

    override fun release() {
        disconnect()
        httpClient?.dispatcher?.executorService?.shutdown()
        httpClient = null
        listener = null
    }

    override fun getName(): String = "WebSocket"

    override fun getProviderId(): String = "websocket"

    // ==================== 内部方法 ====================

    /**
     * 更新连接状态并通知监听器
     */
    private fun updateState(newState: ConnectionState) {
        val oldState = _connectionState.value
        if (oldState != newState) {
            Log.d(TAG, "连接状态变化: $oldState -> $newState")
            _connectionState.value = newState
            listener?.onStateChanged(newState)
        }
    }

    /**
     * 内部断开连接
     */
    private fun disconnectInternal(triggerReconnect: Boolean) {
        shouldReconnect = triggerReconnect
        stopHeartbeat()
        mainHandler.removeCallbacksAndMessages(null) // 清除所有待执行的重连任务
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Client disconnect")
        webSocket = null
        if (!triggerReconnect) {
            updateState(ConnectionState.DISCONNECTED)
        }
    }

    /**
     * 构建带参数的 URL
     */
    private fun buildUrl(config: ConnectionConfig): String {
        val baseUrl = config.serverUrl
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val url = StringBuilder(baseUrl)
            .append(separator)
            .append("clientId=").append(config.actualClientId)

        if (!config.token.isNullOrEmpty()) {
            url.append("&token=").append(config.token)
        }

        return url.toString()
    }

    /**
     * 隐藏敏感信息用于日志
     */
    private fun maskSensitiveInfo(url: String): String {
        val tokenIndex = url.indexOf("token=")
        if (tokenIndex == -1) return url
        val tokenEnd = url.indexOf("&", tokenIndex).takeIf { it != -1 } ?: url.length
        val tokenValue = url.substring(tokenIndex + 6, tokenEnd)
        if (tokenValue.length <= 10) return url
        val maskedToken = tokenValue.take(5) + "***" + tokenValue.takeLast(5)
        return url.replaceRange(tokenIndex + 6, tokenEnd, maskedToken)
    }

    // ==================== 心跳管理 ====================

    /**
     * 启动心跳
     */
    private fun startHeartbeat() {
        val config = currentConfig ?: return
        if (config.heartbeatIntervalMs <= 0) return

        stopHeartbeat()

        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected()) {
                    val timeSinceLastPong = System.currentTimeMillis() - lastPongTime
                    if (timeSinceLastPong > config.heartbeatIntervalMs * 2) {
                        Log.w(TAG, "心跳超时 (${timeSinceLastPong}ms)，连接可能已断开")
                        webSocket?.close(NORMAL_CLOSURE_STATUS, "Heartbeat timeout")
                        return
                    }

                    // 发送心跳（由调用方通过 send 方法自定义心跳内容，
                    // 此处仅做超时检测，实际心跳消息由 WebSocketClient 发送）
                    mainHandler.postDelayed(this, config.heartbeatIntervalMs)
                }
            }
        }

        mainHandler.postDelayed(heartbeatRunnable!!, config.heartbeatIntervalMs)
        Log.i(TAG, "心跳监控已启动，间隔: ${config.heartbeatIntervalMs}ms")
    }

    /**
     * 停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        heartbeatRunnable = null
    }

    /**
     * 更新最后响应时间（外部调用，用于心跳超时检测）
     */
    fun updateLastPongTime() {
        lastPongTime = System.currentTimeMillis()
    }

    // ==================== 重连管理 ====================

    /**
     * 调度重连
     */
    private fun scheduleReconnect() {
        val config = currentConfig ?: return
        if (!config.reconnectEnabled) return

        val delay = minOf(
            config.reconnectDelayMs * (1L shl minOf(reconnectAttempts, 5)),
            config.maxReconnectDelayMs
        )
        reconnectAttempts++

        Log.i(TAG, "${delay}ms 后尝试第 $reconnectAttempts 次重连...")
        updateState(ConnectionState.RECONNECTING)

        mainHandler.postDelayed({
            if (shouldReconnect && !isConnected()) {
                val currentCfg = currentConfig
                if (currentCfg != null) {
                    updateState(ConnectionState.CONNECTING)
                    val url = buildUrl(currentCfg)
                    val client = getOrCreateClient(currentCfg)
                    val request = Request.Builder()
                        .url(url)
                        .build()
                    webSocket = client.newWebSocket(request, webSocketListener)
                }
            }
        }, delay)
    }
}

