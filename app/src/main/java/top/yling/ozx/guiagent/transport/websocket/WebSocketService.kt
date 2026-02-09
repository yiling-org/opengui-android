package top.yling.ozx.guiagent.websocket

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import top.yling.ozx.guiagent.BuildConfig
import top.yling.ozx.guiagent.MainActivity
import top.yling.ozx.guiagent.R
import com.google.gson.Gson
import kotlinx.coroutines.*

/**
 * 前台服务，用于保持 WebSocket 连接在后台运行
 */
class WebSocketService : Service() {

    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_ID_FOLLOWUP = 1002
        private const val CHANNEL_ID = "websocket_service_channel"
        private const val CHANNEL_ID_FOLLOWUP = "followup_question_channel"

        private const val PREF_NAME = "websocket_prefs"
        private const val PREF_SERVER_URL = "server_url"
        // 使用 BuildConfig 中的配置（从 local.properties 读取）
        private val DEFAULT_SERVER_URL = BuildConfig.WEBSOCKET_URL

        // 服务是否正在运行
        var isRunning = false
            private set

        // 服务实例（用于 AppChangeReceiver 调用）
        var instance: WebSocketService? = null
            private set

        /**
         * 检查是否已忽略电池优化
         */
        fun isIgnoringBatteryOptimizations(context: Context): Boolean {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        /**
         * 请求忽略电池优化
         */
        fun requestIgnoreBatteryOptimizations(context: Context) {
            if (!isIgnoringBatteryOptimizations(context)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "请求电池优化豁免失败: ${e.message}")
                }
            }
        }
    }

    var webSocketClient: WebSocketClient? = null
        private set
    // 传输层连接实例
    private var agentConnection: top.yling.ozx.guiagent.transport.api.AgentConnection? = null
    private val binder = LocalBinder()
    
    // 协程作用域用于处理异步操作
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // WakeLock 和 WiFi Lock
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    // WakeLock 续约任务
    private var wakeLockRenewalJob: Job? = null

    // 应用变化广播接收器
    private var appChangeReceiver: top.yling.ozx.guiagent.sync.AppChangeReceiver? = null

    // 应用收集器
    private val appCollector by lazy { top.yling.ozx.guiagent.sync.InstalledAppCollector(applicationContext) }
    
    // 应用前后台状态监听
    private var appStateMonitor: AppStateMonitor? = null
    
    // WebSocket连接状态监控任务
    private var connectionMonitorJob: Job? = null

    // 状态回调
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onMessageReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onFollowUpQuestion: ((top.yling.ozx.guiagent.task.TaskManager.FollowUpQuestion) -> Unit)? = null
    var onLifeAssistant: ((top.yling.ozx.guiagent.task.TaskManager.LifeAssistantInfo) -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): WebSocketService = this@WebSocketService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "WebSocket 服务创建")
        isRunning = true
        instance = this
        createNotificationChannel()
        acquireLocks()
        registerAppChangeReceiver()
        startAppStateMonitor()
        // 检查关键权限（仅记录日志，不阻塞服务启动）
        checkCriticalPermissions()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "WebSocket 服务启动")

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification("正在连接..."))

        // 确保锁已获取
        acquireLocks()

        // 检查关键权限（每次启动时都检查）
        checkCriticalPermissions()

        // 获取服务器地址并连接
        val serverUrl = getServerUrl()
        connectWebSocket(serverUrl)
        
        // 启动连接状态监控，确保连接失败后能自动重连
        startConnectionMonitor()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "WebSocket 服务销毁")
        isRunning = false
        instance = null
        serviceScope.cancel() // 取消所有协程
        webSocketClient?.disconnect()
        webSocketClient = null
        agentConnection?.release()
        agentConnection = null
        releaseLocks()
        unregisterAppChangeReceiver()
        stopAppStateMonitor()
        stopConnectionMonitor()
    }

    // ==================== WakeLock 和 WiFi Lock ====================

    /**
     * 获取 WakeLock 和 WiFi Lock（异步执行，避免阻塞主线程）
     */
    private fun acquireLocks() {
        serviceScope.launch(Dispatchers.Default) {
            // WakeLock - 保持 CPU 运行
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "xiaoling:WebSocketWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    // 不设置超时，手动管理释放（在onDestroy中释放）
                    // 避免长时间任务执行过程中WakeLock超时导致app被杀
                    acquire()
                }
                Log.i(TAG, "WakeLock 已获取")

                // 启动定期续约任务，每2分钟检查一次WakeLock状态（更频繁，提高保活率）
                startWakeLockRenewal()
            }

            // WiFi Lock - 保持 WiFi 连接
            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                wifiLock = wifiManager.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "xiaoling:WebSocketWifiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.i(TAG, "WiFi Lock 已获取")
            }
        }
    }

    /**
     * 启动 WakeLock 定期续约任务
     * 防止系统在某些情况下意外释放 WakeLock
     */
    private fun startWakeLockRenewal() {
        // 取消之前的任务（如果存在）
        wakeLockRenewalJob?.cancel()

        // 每2分钟检查并续约一次（更频繁的检查，提高保活率）
        wakeLockRenewalJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(2 * 60 * 1000L) // 2分钟

                wakeLock?.let { wl ->
                    if (!wl.isHeld) {
                        Log.w(TAG, "检测到 WakeLock 已释放，重新获取")
                        try {
                            wl.acquire()
                            Log.i(TAG, "WakeLock 重新获取成功")
                        } catch (e: Exception) {
                            Log.e(TAG, "WakeLock 重新获取失败: ${e.message}", e)
                        }
                    } else {
                        // 即使持有，也尝试重新获取一次（防止系统强制释放）
                        // 注意：PARTIAL_WAKE_LOCK 不支持超时参数，使用无参 acquire
                        try {
                            // 先释放再获取，确保续约
                            if (wl.isHeld) {
                                wl.release()
                            }
                            wl.acquire()
                        } catch (e: Exception) {
                            Log.e(TAG, "WakeLock 续约失败: ${e.message}", e)
                        }
                    }
                }
            }
        }
        Log.i(TAG, "WakeLock 续约任务已启动")
    }

    /**
     * 释放 WakeLock 和 WiFi Lock
     */
    private fun releaseLocks() {
        // 取消续约任务
        wakeLockRenewalJob?.cancel()
        wakeLockRenewalJob = null
        Log.i(TAG, "WakeLock 续约任务已取消")

        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "WakeLock 已释放")
            }
        }
        wakeLock = null

        wifiLock?.let {
            if (it.isHeld) {
                it.release()
                Log.i(TAG, "WiFi Lock 已释放")
            }
        }
        wifiLock = null
    }

    /**
     * 连接 WebSocket
     */
    private fun connectWebSocket(serverUrl: String) {
        webSocketClient?.disconnect()
        agentConnection?.release()

        // 通过工厂创建传输层连接
        val connection = top.yling.ozx.guiagent.transport.api.ConnectionFactory.create(this)
        agentConnection = connection

        webSocketClient = WebSocketClient(this, serverUrl, connection).apply {
            onConnectionStateChanged = { connected ->
                Log.i(TAG, "连接状态变化: $connected")
                updateNotification(if (connected) "已连接" else "已断开")
                this@WebSocketService.onConnectionStateChanged?.invoke(connected)
            }

            onMessageReceived = { message ->
                this@WebSocketService.onMessageReceived?.invoke(message)
            }

            onError = { error ->
                Log.e(TAG, "WebSocket 错误: $error")
                updateNotification("连接错误: $error")
                this@WebSocketService.onError?.invoke(error)
            }
            
            onFollowUpQuestion = { followUp ->
                Log.i(TAG, "WebSocketClient 触发 onFollowUpQuestion 回调")
                Log.i(TAG, "问题: ${followUp.question}, TaskId: ${followUp.taskId}")
                
                // 检查是否开启了 background 模式
                val isBackgroundMode = top.yling.ozx.guiagent.util.AppSettings.isBackgroundRunEnabled(applicationContext)
                
                if (isBackgroundMode) {
                    // background 模式：尝试调用外部回调（MainActivity），如果失败则使用Activity
                    val callback = this@WebSocketService.onFollowUpQuestion
                    if (callback != null) {
                        try {
                            Log.i(TAG, "background 模式：调用MainActivity回调显示问题")
                            callback.invoke(followUp)
                        } catch (e: Exception) {
                            Log.e(TAG, "MainActivity回调失败，使用FollowUpAnswerActivity: ${e.message}", e)
                            // 回调失败（可能Activity已destroyed），使用独立的Activity
                            startFollowUpActivity(followUp.question, followUp.taskId)
                        }
                    } else {
                        Log.w(TAG, "background 模式：MainActivity回调未设置，使用FollowUpAnswerActivity")
                        startFollowUpActivity(followUp.question, followUp.taskId)
                    }
                } else {
                    // 非 background 模式：直接启动 Activity 显示弹框（更可靠）
                    Log.i(TAG, "非 background 模式，启动 FollowUpAnswerActivity 显示弹框")
                    startFollowUpActivity(followUp.question, followUp.taskId)
                }
            }
            
            onChatMessage = { messages ->
                Log.i(TAG, "WebSocketClient 触发 onChatMessage 回调")
                Log.i(TAG, "消息内容: $messages")
                
                // 启动 ChatActivity 显示消息
                Log.i(TAG, "启动 ChatActivity 显示消息")
                startChatActivity(messages)
            }
            
            onLifeAssistant = { lifeAssistant ->
                Log.i(TAG, "WebSocketClient 触发 onLifeAssistant 回调")
                Log.i(TAG, "JSON内容长度: ${lifeAssistant.jsonContent.length}, TaskId: ${lifeAssistant.taskId}")
                Log.i(TAG, "life_assistant 完整JSON内容:\n${lifeAssistant.jsonContent}")
                
                // 先调用外部回调（如果有）
                this@WebSocketService.onLifeAssistant?.invoke(lifeAssistant)
                
                // 直接启动 LifeAssistantActivity 显示内容（无论应用在前台还是后台）
                Log.i(TAG, "直接启动 LifeAssistantActivity 显示内容")
                startLifeAssistantActivity(lifeAssistant.jsonContent, lifeAssistant.taskId)
            }
        }

        webSocketClient?.connect()
    }

    /**
     * 重新连接到新地址
     */
    fun reconnect(serverUrl: String) {
        saveServerUrl(serverUrl)
        connectWebSocket(serverUrl)
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        webSocketClient?.disconnect()
    }

    /**
     * 检查是否已连接
     */
    fun isConnected(): Boolean = webSocketClient?.isConnected() ?: false

    /**
     * 发送消息到 Agent（使用新的Task管理机制）
     * @param text 消息内容
     * @param androidId 设备ID（可选）
     * @param taskId 任务ID（如果提供，则使用现有任务，否则创建新任务）
     * @param onSuccess 成功回调
     * @param onError 失败回调
     * @return 创建的taskId，如果发送失败返回null
     */
    fun sendToAgent(
        text: String,
        androidId: String? = null,
        taskId: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // 在IO线程执行，避免阻塞主线程
        serviceScope.launch {
            try {
                if (!isConnected()) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("WebSocket 未连接")
                    }
                    return@launch
                }

                val deviceId = androidId ?: top.yling.ozx.guiagent.util.DeviceUtils.getDeviceId(applicationContext)

                // 如果提供了taskId，使用现有任务发送消息
                if (taskId != null) {
                    val sent = webSocketClient?.sendAgentMessageWithExistingTask(text, deviceId, taskId) ?: false
                    withContext(Dispatchers.Main) {
                        if (sent) {
                            Log.i(TAG, "Agent 消息已发送到现有任务, taskId: $taskId")
                            onSuccess?.invoke()
                        } else {
                            Log.e(TAG, "发送 Agent 消息到现有任务失败")
                            onError?.invoke("发送失败")
                        }
                    }
                } else {
                    // 使用新的Task管理机制发送消息
                    val newTaskId = webSocketClient?.sendAgentMessageWithTask(text, deviceId)

                    withContext(Dispatchers.Main) {
                        if (newTaskId != null) {
                            Log.i(TAG, "Agent 消息已发送, taskId: $newTaskId")
                            onSuccess?.invoke()
                        } else {
                            Log.e(TAG, "发送 Agent 消息失败")
                            onError?.invoke("发送失败")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送 Agent 消息异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "未知错误")
                }
            }
        }
    }

    /**
     * 取消当前任务
     * @param reason 取消原因
     * @return 是否成功发送取消请求
     */
    fun cancelCurrentTask(reason: String = "user_requested"): Boolean {
        return webSocketClient?.cancelCurrentTask(reason) ?: false
    }

    /**
     * 发送回放请求
     * @param taskId 要回放的任务ID
     * @param androidId 设备ID（可选）
     * @param onSuccess 成功回调
     * @param onError 失败回调
     */
    fun sendReplayRequest(
        taskId: String,
        androidId: String? = null,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        serviceScope.launch {
            try {
                if (!isConnected()) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("WebSocket 未连接")
                    }
                    return@launch
                }

                val deviceId = androidId ?: top.yling.ozx.guiagent.util.DeviceUtils.getDeviceId(applicationContext)
                val sent = webSocketClient?.sendReplayRequest(taskId, deviceId) ?: false

                withContext(Dispatchers.Main) {
                    if (sent) {
                        Log.i(TAG, "回放请求已发送: taskId=$taskId")
                        onSuccess?.invoke()
                    } else {
                        Log.e(TAG, "发送回放请求失败")
                        onError?.invoke("发送失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送回放请求异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "未知错误")
                }
            }
        }
    }

    /**
     * 发送回放取消请求
     * @param taskId 要取消的原始任务ID
     * @param onSuccess 成功回调
     * @param onError 失败回调
     */
    fun sendReplayCancelRequest(
        taskId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        serviceScope.launch {
            try {
                if (!isConnected()) {
                    withContext(Dispatchers.Main) {
                        onError?.invoke("WebSocket 未连接")
                    }
                    return@launch
                }

                val sent = webSocketClient?.sendReplayCancelRequest(taskId) ?: false

                withContext(Dispatchers.Main) {
                    if (sent) {
                        Log.i(TAG, "回放取消请求已发送: taskId=$taskId")
                        onSuccess?.invoke()
                    } else {
                        Log.e(TAG, "发送回放取消请求失败")
                        onError?.invoke("发送失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "发送回放取消请求异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke(e.message ?: "未知错误")
                }
            }
        }
    }

    /**
     * 获取保存的服务器地址
     */
    fun getServerUrl(): String {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    /**
     * 保存服务器地址
     */
    fun saveServerUrl(url: String) {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_SERVER_URL, url).apply()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            // WebSocket 服务渠道 - 使用 IMPORTANCE_LOW 但设置为不可移除，确保保活
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebSocket 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 WebSocket 连接在后台运行"
                setShowBadge(false)
                // 关键：设置为不可移除，防止用户误操作导致服务被杀
                setShowBadge(true)
                // Android 8.0+ 设置声音和振动，提高重要性
                enableVibration(false)
                enableLights(false)
            }
            manager.createNotificationChannel(channel)
            
            // FollowUp 问题渠道
            val followUpChannel = NotificationChannel(
                CHANNEL_ID_FOLLOWUP,
                "问题回答",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "需要您回答的问题通知"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(followUpChannel)
        }
    }

    /**
     * 创建前台通知
     */
    private fun createNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name_assistant))
            .setContentText("WebSocket: $status")
            .setSmallIcon(R.drawable.ic_microphone)
            .setContentIntent(pendingIntent)
            .setOngoing(true)  // 关键：设置为持续通知，不可滑动移除
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)  // 标记为服务通知
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)  // 公开可见
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)  // Android 14+ 立即显示
            .build()
    }

    /**
     * 更新通知状态
     */
    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 检查应用是否在前台
     */
    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val packageName = applicationContext.packageName
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val processes = activityManager.runningAppProcesses ?: return false
            for (process in processes) {
                if (process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    process.processName == packageName) {
                    return true
                }
            }
            return false
        }
        
        @Suppress("DEPRECATION")
        val tasks = activityManager.getRunningTasks(1)
        if (tasks.isNotEmpty()) {
            val topActivity = tasks[0].topActivity
            return topActivity?.packageName == packageName
        }
        
        return false
    }
    
    /**
     * 启动 Chat Activity 显示消息
     * 无论应用在前台还是后台，都直接显示弹框
     * 使用多种策略确保能够从后台启动
     */
    private fun startChatActivity(messages: String) {
        val intent = top.yling.ozx.guiagent.FollowUpAnswerActivity.createChatIntent(
            applicationContext,
            messages
        )
        
        // 添加必要的标志，确保可以从后台启动
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        
        Log.i(TAG, "========== 开始启动 ChatActivity ==========")
        Log.i(TAG, "消息内容: $messages")
        
        // 策略1: 使用 PendingIntent 启动（最可靠，可绕过后台限制）
        try {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Log.i(TAG, "PendingIntent 启动 ChatActivity 成功")
            return
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent 启动 ChatActivity 失败: ${e.message}")
        }
        
        // 策略2: 使用前台服务上下文启动（前台服务有启动Activity权限）
        try {
            startActivity(intent)
            Log.i(TAG, "前台服务上下文启动 ChatActivity 成功")
            return
        } catch (e: Exception) {
            Log.w(TAG, "前台服务上下文启动 ChatActivity 失败: ${e.message}")
        }
        
        // 策略3: 使用无障碍服务启动（如果有）
        try {
            val accessibilityService = top.yling.ozx.guiagent.MyAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.startActivity(intent)
                Log.i(TAG, "无障碍服务启动 ChatActivity 成功")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "无障碍服务启动 ChatActivity 失败: ${e.message}")
        }
        
        // 策略4: 使用透明跳板Activity（如果有悬浮窗权限）
        try {
            val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(this)
            } else {
                true
            }
            
            if (hasOverlayPermission) {
                val bridgeIntent = Intent(this, top.yling.ozx.guiagent.LaunchBridgeActivity::class.java).apply {
                    putExtra(top.yling.ozx.guiagent.LaunchBridgeActivity.EXTRA_TARGET_INTENT, intent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(bridgeIntent)
                Log.i(TAG, "跳板Activity启动 ChatActivity 成功")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "跳板Activity启动 ChatActivity 失败: ${e.message}")
        }
        
        Log.e(TAG, "所有策略都失败，无法启动 ChatActivity")
    }
    
    /**
     * 直接启动 FollowUp Answer Activity
     * 无论应用在前台还是后台，都直接显示弹框
     * 使用多种策略确保能够从后台启动
     */
    private fun startLifeAssistantActivity(jsonContent: String, taskId: String?) {
        val intent = top.yling.ozx.guiagent.LifeAssistantActivity.createIntent(
            applicationContext,
            jsonContent,
            taskId
        )
        
        // 添加必要的标志，确保可以从后台启动
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        
        Log.i(TAG, "========== 开始启动 LifeAssistant Activity ==========")
        Log.i(TAG, "JSON内容长度: ${jsonContent.length}")
        Log.i(TAG, "TaskId: $taskId")
        Log.i(TAG, "life_assistant 完整JSON内容:\n$jsonContent")
        
        // 策略1: 使用 PendingIntent 启动（最可靠，可绕过后台限制）
        try {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Log.i(TAG, "PendingIntent 启动成功")
            return
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent 启动失败: ${e.message}")
        }
        
        // 策略2: 使用前台服务上下文启动（前台服务有启动Activity权限）
        try {
            startActivity(intent)
            Log.i(TAG, "前台服务上下文启动成功")
            return
        } catch (e: Exception) {
            Log.w(TAG, "前台服务上下文启动失败: ${e.message}")
        }
        
        // 策略3: 使用无障碍服务启动（如果有）
        try {
            val accessibilityService = top.yling.ozx.guiagent.MyAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.startActivity(intent)
                Log.i(TAG, "无障碍服务启动成功")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "无障碍服务启动失败: ${e.message}")
        }
        
        // 策略4: 使用透明跳板Activity（如果有悬浮窗权限）
        try {
            val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(this)
            } else {
                true
            }
            
            if (hasOverlayPermission) {
                val bridgeIntent = Intent(this, top.yling.ozx.guiagent.LaunchBridgeActivity::class.java).apply {
                    putExtra(top.yling.ozx.guiagent.LaunchBridgeActivity.EXTRA_TARGET_INTENT, intent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(bridgeIntent)
                Log.i(TAG, "跳板Activity启动成功")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "跳板Activity启动失败: ${e.message}")
        }
        
        // 所有策略都失败，记录错误
        Log.e(TAG, "所有启动策略均失败")
    }
    
    private fun startFollowUpActivity(question: String, taskId: String?) {
        val intent = top.yling.ozx.guiagent.FollowUpAnswerActivity.createIntent(
            applicationContext,
            question,
            taskId
        )
        
        // 添加必要的标志，确保可以从后台启动
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
            Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        
        Log.i(TAG, "========== 开始启动 FollowUpAnswerActivity ==========")
        Log.i(TAG, "问题: $question")
        Log.i(TAG, "TaskId: $taskId")
        
        // 策略1: 使用 PendingIntent 启动（最可靠，可绕过后台限制）
        try {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                System.currentTimeMillis().toInt(),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
            Log.i(TAG, "PendingIntent 启动成功")
            return
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent 启动失败: ${e.message}")
        }
        
        // 策略2: 使用前台服务上下文启动（前台服务有启动Activity权限）
        try {
            startActivity(intent)
            Log.i(TAG, "前台服务上下文启动成功")
            return
        } catch (e: Exception) {
            Log.w(TAG, "前台服务上下文启动失败: ${e.message}")
        }
        
        // 策略3: 使用无障碍服务启动（如果有）
        try {
            val accessibilityService = top.yling.ozx.guiagent.MyAccessibilityService.instance
            if (accessibilityService != null) {
                accessibilityService.startActivity(intent)
                Log.i(TAG, "无障碍服务启动成功")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "无障碍服务启动失败: ${e.message}")
        }
        
        // 策略4: 使用透明跳板Activity（如果有悬浮窗权限）
        try {
            val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.provider.Settings.canDrawOverlays(this)
            } else {
                true
            }
            
            if (hasOverlayPermission) {
                val bridgeIntent = Intent(this, top.yling.ozx.guiagent.LaunchBridgeActivity::class.java).apply {
                    putExtra(top.yling.ozx.guiagent.LaunchBridgeActivity.EXTRA_TARGET_INTENT, intent)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(bridgeIntent)
                Log.i(TAG, "跳板Activity启动成功")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "跳板Activity启动失败: ${e.message}")
        }
        
        // 所有策略都失败，记录错误
        Log.e(TAG, "所有启动策略均失败，回退到显示通知")
        showFollowUpNotification(question, taskId)
    }
    
    /**
     * 显示 FollowUp 问题通知
     * 支持应用在后台时显示，包括锁屏状态
     */
    private fun showFollowUpNotification(question: String, taskId: String?) {
        Log.i(TAG, "========== 开始显示 FollowUp 通知 ==========")
        Log.i(TAG, "问题: $question")
        Log.i(TAG, "TaskId: $taskId")
        
        // 创建打开输入 Activity 的 Intent
        val intent = top.yling.ozx.guiagent.FollowUpAnswerActivity.createIntent(
            applicationContext,
            question,
            taskId
        )
        
        // 使用唯一的请求码，避免通知被覆盖
        val requestCode = System.currentTimeMillis().toInt()
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Android 10+ 使用全屏 Intent，确保可以在锁屏上显示
        var fullScreenIntent: PendingIntent? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            fullScreenIntent = PendingIntent.getActivity(
                this,
                requestCode + 1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            Log.d(TAG, "创建全屏 Intent (Android 10+)")
        }
        
        // 创建通知
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_FOLLOWUP)
            .setSmallIcon(R.drawable.ic_microphone)
            .setContentTitle("需要您的回答")
            .setContentText(question.take(100) + if (question.length > 100) "..." else "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(question))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setOnlyAlertOnce(false)  // 每次显示都提醒
        
        // 设置全屏 Intent（Android 10+）
        if (fullScreenIntent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            notificationBuilder.setFullScreenIntent(fullScreenIntent, true)
        }
        
        val notification = notificationBuilder.build()
        
        val manager = getSystemService(NotificationManager::class.java)
        
        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            if (!hasPermission) {
                Log.e(TAG, "缺少通知权限 (Android 13+)，无法显示通知，直接启动 Activity")
                // 即使没有权限，也尝试直接启动 Activity
                startFollowUpActivity(question, taskId)
                return
            }
        }
        
        // 检查通知渠道是否已创建
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = manager.getNotificationChannel(CHANNEL_ID_FOLLOWUP)
            if (channel == null) {
                Log.w(TAG, "通知渠道不存在，重新创建...")
                createNotificationChannel()
            }
        }
        
        try {
            manager.notify(NOTIFICATION_ID_FOLLOWUP, notification)
            Log.i(TAG, "FollowUp 问题通知已显示成功")
            Log.i(TAG, "==========================================")
        } catch (e: SecurityException) {
            Log.e(TAG, "显示通知失败 (SecurityException): ${e.message}", e)
            // 如果通知失败，尝试直接启动 Activity
            startFollowUpActivity(question, taskId)
        } catch (e: Exception) {
            Log.e(TAG, "显示通知失败 (其他异常): ${e.message}", e)
            // 如果通知失败，尝试直接启动 Activity
            startFollowUpActivity(question, taskId)
        }
    }

    // ==================== 已安装应用同步 ====================

    /**
     * 注册应用变化广播接收器
     */
    private fun registerAppChangeReceiver() {
        if (appChangeReceiver == null) {
            appChangeReceiver = top.yling.ozx.guiagent.sync.AppChangeReceiver()
            val filter = top.yling.ozx.guiagent.sync.AppChangeReceiver.createIntentFilter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(appChangeReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(appChangeReceiver, filter)
            }
            Log.i(TAG, "应用变化广播接收器已注册")
        }
    }

    /**
     * 取消注册应用变化广播接收器
     */
    private fun unregisterAppChangeReceiver() {
        appChangeReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.i(TAG, "应用变化广播接收器已取消注册")
            } catch (e: Exception) {
                Log.w(TAG, "取消注册广播接收器失败: ${e.message}")
            }
        }
        appChangeReceiver = null
    }

    /**
     * 发送全量已安装应用列表
     * @param syncType 同步类型: full-全量, incremental-增量
     */
    fun sendInstalledApps(syncType: String = "full") {
        serviceScope.launch {
            try {
                if (!isConnected()) {
                    Log.w(TAG, "WebSocket 未连接，跳过应用同步")
                    return@launch
                }

                val apps = appCollector.getInstalledApps()
                val deviceId = top.yling.ozx.guiagent.util.DeviceUtils.getDeviceId(applicationContext)

                Log.i(TAG, "准备同步已安装应用: syncType=$syncType, appCount=${apps.size}")

                val message = mapOf(
                    "type" to "installed_apps",
                    "androidId" to deviceId,
                    "data" to mapOf(
                        "apps" to apps.map { it.toMap() },
                        "syncType" to syncType
                    )
                )

                val json = Gson().toJson(message)
                val sent = webSocketClient?.sendRaw(json) ?: false

                if (sent) {
                    Log.i(TAG, "已安装应用列表已发送: appCount=${apps.size}")
                } else {
                    Log.e(TAG, "发送已安装应用列表失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步已安装应用异常: ${e.message}", e)
            }
        }
    }

    /**
     * 发送新安装的应用信息
     * @param packageName 应用包名
     */
    fun sendAppInstalled(packageName: String) {
        serviceScope.launch {
            try {
                if (!isConnected()) {
                    Log.w(TAG, "WebSocket 未连接，跳过应用安装同步: $packageName")
                    return@launch
                }

                val appInfo = appCollector.getAppInfo(packageName)
                if (appInfo == null) {
                    Log.w(TAG, "无法获取应用信息: $packageName")
                    return@launch
                }

                val deviceId = top.yling.ozx.guiagent.util.DeviceUtils.getDeviceId(applicationContext)

                Log.i(TAG, "准备同步新安装应用: $packageName (${appInfo.appName})")

                val message = mapOf(
                    "type" to "installed_apps",
                    "androidId" to deviceId,
                    "data" to mapOf(
                        "apps" to listOf(appInfo.toMap()),
                        "syncType" to "incremental"
                    )
                )

                val json = Gson().toJson(message)
                val sent = webSocketClient?.sendRaw(json) ?: false

                if (sent) {
                    Log.i(TAG, "新安装应用已同步: $packageName")
                } else {
                    Log.e(TAG, "发送新安装应用失败: $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步新安装应用异常: ${e.message}", e)
            }
        }
    }

    /**
     * 发送应用更新信息
     * @param packageName 应用包名
     */
    fun sendAppUpdated(packageName: String) {
        // 更新和安装使用相同的逻辑（incremental 同步）
        sendAppInstalled(packageName)
    }

    /**
     * 发送应用卸载通知
     * @param packageName 应用包名
     */
    fun sendAppUninstalled(packageName: String) {
        serviceScope.launch {
            try {
                if (!isConnected()) {
                    Log.w(TAG, "WebSocket 未连接，跳过应用卸载同步: $packageName")
                    return@launch
                }

                val deviceId = top.yling.ozx.guiagent.util.DeviceUtils.getDeviceId(applicationContext)

                Log.i(TAG, "准备同步应用卸载: $packageName")

                val message = mapOf(
                    "type" to "installed_apps",
                    "androidId" to deviceId,
                    "data" to mapOf(
                        "apps" to listOf(mapOf("packageName" to packageName)),
                        "syncType" to "uninstall"
                    )
                )

                val json = Gson().toJson(message)
                val sent = webSocketClient?.sendRaw(json) ?: false

                if (sent) {
                    Log.i(TAG, "应用卸载已同步: $packageName")
                } else {
                    Log.e(TAG, "发送应用卸载失败: $packageName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步应用卸载异常: ${e.message}", e)
            }
        }
    }

    // ==================== 应用状态监听 ====================

    /**
     * 启动应用状态监听（检测应用前后台状态，后台时采取保活措施）
     */
    private fun startAppStateMonitor() {
        appStateMonitor = AppStateMonitor(applicationContext) { isForeground ->
            if (!isForeground) {
                // 应用进入后台，采取保活措施
                Log.w(TAG, "应用进入后台，采取保活措施")
                keepAliveWhenBackground()
            } else {
            }
        }
        appStateMonitor?.start()
        Log.i(TAG, "应用状态监听已启动")
    }

    /**
     * 停止应用状态监听
     */
    private fun stopAppStateMonitor() {
        appStateMonitor?.stop()
        appStateMonitor = null
        Log.i(TAG, "应用状态监听已停止")
    }

    /**
     * 应用进入后台时的保活措施
     */
    private fun keepAliveWhenBackground() {
        serviceScope.launch(Dispatchers.Default) {
            try {
                // 1. 确保 WakeLock 持有
                wakeLock?.let { wl ->
                    if (!wl.isHeld) {
                        Log.w(TAG, "后台保活：重新获取 WakeLock")
                        wl.acquire()
                    }
                }

                // 2. 更新通知，提醒用户服务正在运行
                updateNotification("后台运行中...")

                // 3. 确保 WebSocket 连接正常
                if (!isConnected()) {
                    Log.w(TAG, "后台保活：检测到 WebSocket 断开，尝试重连")
                    val serverUrl = getServerUrl()
                    connectWebSocket(serverUrl)
                }

                Log.i(TAG, "后台保活措施执行完成")
            } catch (e: Exception) {
                Log.e(TAG, "后台保活措施执行失败: ${e.message}", e)
            }
        }
    }

    /**
     * 应用状态监听器
     */
    inner class AppStateMonitor(
        private val context: Context,
        private val onStateChanged: (Boolean) -> Unit
    ) {
        private var isMonitoring = false
        private val handler = Handler(Looper.getMainLooper())
        private val checkInterval = 2000L // 每2秒检查一次

        private val checkRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return

                val isForeground = isAppInForeground(context)
                onStateChanged(isForeground)

                handler.postDelayed(this, checkInterval)
            }
        }

        fun start() {
            if (isMonitoring) return
            isMonitoring = true
            handler.post(checkRunnable)
        }

        fun stop() {
            isMonitoring = false
            handler.removeCallbacks(checkRunnable)
        }

        private fun isAppInForeground(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val packageName = context.packageName

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val processes = activityManager.runningAppProcesses ?: return false
                for (process in processes) {
                    if (process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                        process.processName == packageName) {
                        return true
                    }
                }
                return false
            }

            @Suppress("DEPRECATION")
            val tasks = activityManager.getRunningTasks(1)
            if (tasks.isNotEmpty()) {
                val topActivity = tasks[0].topActivity
                return topActivity?.packageName == packageName
            }

            return false
        }
    }

    // ==================== 权限检查 ====================

    /**
     * 检查关键权限（用于保活）
     * 仅记录日志，不阻塞服务启动
     */
    private fun checkCriticalPermissions() {
        serviceScope.launch(Dispatchers.Default) {
            try {
                val context = applicationContext
                val packageName = context.packageName
                
                // 1. 检查电池优化白名单
                val batteryOptimized = isIgnoringBatteryOptimizations(context)
                if (!batteryOptimized) {
                    Log.w(TAG, "⚠️ 电池优化未加入白名单，可能导致进程被杀")
                    Log.w(TAG, "   请前往：设置 → 省电与电池 → 电池优化 → 选择'小零' → '不优化'")
                } else {
                    Log.i(TAG, "✅ 电池优化已加入白名单")
                }

                // 2. 检查MIUI自启动权限（仅小米设备）
                if (top.yling.ozx.guiagent.util.PermissionHelper.isXiaomiDevice()) {
                    val autoStartEnabled = top.yling.ozx.guiagent.util.PermissionHelper.isAutoStartEnabled(context)
                    if (!autoStartEnabled) {
                        Log.w(TAG, "⚠️ MIUI自启动权限未开启，可能导致进程被杀")
                        Log.w(TAG, "   请前往：设置 → 应用设置 → 授权管理 → 自启动管理 → 开启'小零'")
                    } else {
                        Log.i(TAG, "✅ MIUI自启动权限已开启")
                    }
                }

                // 3. 检查通知权限（Android 13+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val notificationGranted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!notificationGranted) {
                        Log.w(TAG, "⚠️ 通知权限未授予，前台服务通知可能无法显示")
                        Log.w(TAG, "   请前往：设置 → 应用设置 → 小零 → 通知管理 → 开启通知权限")
                    } else {
                        Log.i(TAG, "✅ 通知权限已授予")
                    }
                }

                // 4. 检查悬浮窗权限（用于后台启动Activity）
                val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.provider.Settings.canDrawOverlays(context)
                } else {
                    true
                }
                if (!overlayGranted) {
                    Log.w(TAG, "⚠️ 悬浮窗权限未授予，后台启动Activity可能失败")
                    Log.w(TAG, "   请前往：设置 → 应用设置 → 授权管理 → 后台弹出界面 → 开启'小零'")
                } else {
                    Log.i(TAG, "✅ 悬浮窗权限已授予")
                }

                // 5. 汇总权限状态
                val allPermissionsOk = batteryOptimized &&
                        (!top.yling.ozx.guiagent.util.PermissionHelper.isXiaomiDevice() ||
                                top.yling.ozx.guiagent.util.PermissionHelper.isAutoStartEnabled(context)) &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                                        android.content.pm.PackageManager.PERMISSION_GRANTED) &&
                        overlayGranted

                if (allPermissionsOk) {
                    Log.i(TAG, "✅ 所有关键权限检查通过，保活机制应该正常工作")
                } else {
                    Log.e(TAG, "❌ 部分关键权限未设置，进程可能被系统杀死")
                    Log.e(TAG, "   建议按照上述提示设置所有权限，以确保服务稳定运行")
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查关键权限失败: ${e.message}", e)
            }
        }
    }

    // ==================== WebSocket连接监控 ====================

    /**
     * 启动连接状态监控（定期检查连接状态，断开时自动重连）
     */
    private fun startConnectionMonitor() {
        // 取消之前的监控任务
        connectionMonitorJob?.cancel()
        
        // 每30秒检查一次连接状态
        connectionMonitorJob = serviceScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(30 * 1000L) // 30秒
                
                try {
                    val connected = isConnected()
                    if (!connected) {
                        Log.w(TAG, "连接监控：检测到WebSocket断开，尝试重连")
                        val serverUrl = getServerUrl()
                        connectWebSocket(serverUrl)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "连接监控检查失败: ${e.message}", e)
                }
            }
        }
        Log.i(TAG, "WebSocket连接监控已启动")
    }

    /**
     * 停止连接状态监控
     */
    private fun stopConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = null
        Log.i(TAG, "WebSocket连接监控已停止")
    }
}
