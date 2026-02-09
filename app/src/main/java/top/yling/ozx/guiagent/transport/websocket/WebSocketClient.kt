package top.yling.ozx.guiagent.websocket

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.services.BrowserService
import top.yling.ozx.guiagent.services.CalendarService
import top.yling.ozx.guiagent.services.ContactsService
import top.yling.ozx.guiagent.services.NotificationService
import top.yling.ozx.guiagent.services.PhoneCallService
import top.yling.ozx.guiagent.services.SmsService
import top.yling.ozx.guiagent.task.TaskManager
import top.yling.ozx.guiagent.transport.api.AgentConnection
import top.yling.ozx.guiagent.transport.api.ConnectionConfig
import top.yling.ozx.guiagent.transport.api.ConnectionListener
import top.yling.ozx.guiagent.transport.api.ConnectionState
import top.yling.ozx.guiagent.util.AppSettings
import top.yling.ozx.guiagent.util.DeviceUtils
import top.yling.ozx.guiagent.util.TokenManager
import top.yling.ozx.guiagent.util.TtsHelper

/**
 * WebSocket 客户端，用于接收服务器下发的无障碍指令
 *
 * 职责：连接管理、消息收发、心跳维护、指令执行。
 * 传输层通过 [AgentConnection] 抽象，支持替换底层传输协议。
 * 指令执行委托给 [CommandExecutor]。
 *
 * @param context Android 上下文
 * @param serverUrl 服务端 WebSocket 地址
 * @param agentConnection 传输层连接实例
 */
class WebSocketClient(
    private val context: Context,
    private val serverUrl: String,
    private val agentConnection: AgentConnection
) {
    companion object {
        private const val TAG = "WebSocketClient"
        private const val HEARTBEAT_INTERVAL_MS = 30000L
    }

    private val gson = Gson()
    private var shouldReconnect = true

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var heartbeatRunnable: Runnable? = null

    // 实际使用的 clientId（可能包含后缀）
    private var actualClientId: String = ""

    // 上次全量同步时间（用于避免频繁全量同步）
    private var lastFullSyncTime: Long = 0L
    // 全量同步最小间隔（30分钟）
    private val FULL_SYNC_MIN_INTERVAL_MS = 30 * 60 * 1000L

    // 存储 clientId 后缀，用于重连时保持一致
    private var clientIdSuffix: String? = null

    // 使用单线程调度器保证消息顺序处理，避免并发导致的消息乱序
    @OptIn(ExperimentalCoroutinesApi::class)
    private val messageScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1) + SupervisorJob())

    // 委托组件
    private val appLauncher = AppLauncher(context)
    private val browserService = BrowserService(context)
    private val smsService = SmsService(context)
    private val contactsService = ContactsService(context)
    private val notificationService = NotificationService(context)
    private val calendarService = CalendarService(context)
    private val phoneCallService = PhoneCallService(context)
    private val commandExecutor = CommandExecutor(context, appLauncher, browserService, smsService, contactsService, notificationService, calendarService, phoneCallService, messageScope)
    
    // TTS 工具类
    private val ttsHelper = TtsHelper(context)

    // 状态监听器
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onMessageReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onFollowUpQuestion: ((TaskManager.FollowUpQuestion) -> Unit)? = null
    var onChatMessage: ((String) -> Unit)? = null
    var onLifeAssistant: ((TaskManager.LifeAssistantInfo) -> Unit)? = null

    init {
        // 设置传输层监听器
        agentConnection.setListener(object : ConnectionListener {
            override fun onStateChanged(state: ConnectionState) {
                val connected = state.isConnected
                Log.i(TAG, "连接状态变化: $state (connected=$connected)")
                onConnectionStateChanged?.invoke(connected)

                when (state) {
                    ConnectionState.CONNECTED -> {
                        sendDeviceInfo()
                        startHeartbeat()
                        triggerInstalledAppsSync()
                    }
                    ConnectionState.DISCONNECTED, ConnectionState.FAILED -> {
                        stopHeartbeat()
                    }
                    else -> { /* CONNECTING, RECONNECTING - no action needed */ }
                }
            }

            override fun onMessage(message: String) {
                Log.d(TAG, "收到消息: $message")

                messageScope.launch {
                    onMessageReceived?.invoke(message)
                    handleCommand(message)
                }
            }

            override fun onError(error: String, throwable: Throwable?) {
                Log.e(TAG, "连接错误: $error", throwable)
                onError?.invoke(error)
            }
        })
    }

    fun connect(suffix: String? = null) {
        if (isConnected()) {
            Log.w(TAG, "已经连接，无需重复连接")
            return
        }

        shouldReconnect = true

        // 保存后缀，用于重连
        if (suffix != null) {
            clientIdSuffix = suffix
        }

        val baseClientId = DeviceUtils.getDeviceId(context)
        actualClientId = if (!clientIdSuffix.isNullOrEmpty()) {
            "${baseClientId}_${clientIdSuffix}"
        } else {
            baseClientId
        }

        val token = TokenManager.getToken(context)
        Log.i(TAG, "正在连接 WebSocket (clientId: $actualClientId, hasToken: ${token != null})")

        val config = ConnectionConfig(
            serverUrl = serverUrl,
            clientId = baseClientId,
            clientIdSuffix = clientIdSuffix,
            token = token,
            reconnectEnabled = shouldReconnect,
            heartbeatIntervalMs = HEARTBEAT_INTERVAL_MS
        )

        agentConnection.connect(config)
    }

    fun disconnect() {
        shouldReconnect = false
        stopHeartbeat()
        messageScope.cancel()
        agentConnection.disconnect()
        // 销毁 TTS 资源
        ttsHelper.destroy()
    }

    fun sendMessage(message: String): Boolean {
        return agentConnection.send(message)
    }

    /**
     * 发送原始 JSON 消息（sendMessage 的别名）
     */
    fun sendRaw(json: String): Boolean {
        return sendMessage(json)
    }

    private fun sendResponse(response: CommandResponse) {
        val json = gson.toJson(response)
        Log.d(TAG, "发送响应: $json")
        sendMessage(json)
    }

    private fun sendDeviceInfo() {
        val clientId = DeviceUtils.getDeviceId(context)
        val (screenWidth, screenHeight) = DeviceUtils.getScreenSize(context)
        val deviceInfo = mapOf(
            "type" to "device_info",
            "clientId" to clientId,
            "device" to android.os.Build.MODEL,
            "brand" to android.os.Build.BRAND,
            "sdk" to android.os.Build.VERSION.SDK_INT,
            "screenWidth" to screenWidth,
            "screenHeight" to screenHeight,
            "accessibility_enabled" to MyAccessibilityService.isServiceEnabled(),
            "marketingName" to getMarketingName()
        )
        sendMessage(gson.toJson(deviceInfo))
    }

    /**
     * 获取设备营销名称（用户友好的设备名称）
     * 尝试从系统属性获取，不同厂商使用不同的属性名
     */
    private fun getMarketingName(): String {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            // 尝试不同厂商的属性名
            val marketName = (get.invoke(c, "ro.product.marketname") as? String)?.takeIf { it.isNotBlank() }
                ?: (get.invoke(c, "ro.product.vendor.marketname") as? String)?.takeIf { it.isNotBlank() }
                ?: (get.invoke(c, "ro.config.marketing_name") as? String)?.takeIf { it.isNotBlank() }
                ?: (get.invoke(c, "ro.product.nickname") as? String)?.takeIf { it.isNotBlank() }
            marketName ?: "${android.os.Build.BRAND} ${android.os.Build.MODEL}"
        } catch (e: Exception) {
            "${android.os.Build.BRAND} ${android.os.Build.MODEL}"
        }
    }

    /**
     * 触发已安装应用全量同步
     * 在 WebSocket 连接成功后延迟执行，避免与其他初始化消息冲突
     *
     * 优化策略：
     * - 首次连接：进行全量同步
     * - 重连：如果距离上次同步时间小于 FULL_SYNC_MIN_INTERVAL_MS，跳过全量同步
     *   （期间的变化由 BroadcastReceiver 增量同步处理）
     */
    private fun triggerInstalledAppsSync() {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastSync = currentTime - lastFullSyncTime

        // 如果距离上次全量同步时间小于最小间隔，跳过本次同步
        if (lastFullSyncTime > 0 && timeSinceLastSync < FULL_SYNC_MIN_INTERVAL_MS) {
            Log.i(TAG, "距离上次全量同步仅 ${timeSinceLastSync / 1000}秒，跳过本次全量同步（最小间隔: ${FULL_SYNC_MIN_INTERVAL_MS / 1000 / 60}分钟）")
            return
        }

        // 延迟 2 秒执行，确保连接稳定
        mainHandler.postDelayed({
            Log.i(TAG, "触发已安装应用全量同步")
            WebSocketService.instance?.sendInstalledApps("full")
            // 记录本次全量同步时间
            lastFullSyncTime = System.currentTimeMillis()
        }, 2000L)
    }

    private fun startHeartbeat() {
        stopHeartbeat()

        heartbeatRunnable = object : Runnable {
            override fun run() {
                if (isConnected()) {
                    val heartbeat = gson.toJson(mapOf(
                        "type" to "heartbeat",
                        "clientId" to DeviceUtils.getDeviceId(context),
                        "timestamp" to System.currentTimeMillis()
                    ))
                    val sent = sendMessage(heartbeat)
                    Log.d(TAG, "发送心跳: $sent")
                    mainHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
                }
            }
        }

        mainHandler.postDelayed(heartbeatRunnable!!, HEARTBEAT_INTERVAL_MS)
        Log.i(TAG, "心跳已启动，间隔: ${HEARTBEAT_INTERVAL_MS}ms")
    }

    private fun stopHeartbeat() {
        heartbeatRunnable?.let {
            mainHandler.removeCallbacks(it)
        }
        heartbeatRunnable = null
    }

    /**
     * 处理服务器下发的指令
     */
    @Suppress("UNCHECKED_CAST")
    private fun handleCommand(json: String) {
        try {
            val jsonObject = gson.fromJson(json, Map::class.java) as? Map<String, Any?>
            val type = jsonObject?.get("type") as? String
            val reqId = jsonObject?.get("reqId") as? String

            // 处理任务相关消息（用于状态推断）
            when (type) {
                "agent_response" -> {
                    val content = jsonObject?.get("data") as? String ?: ""
                    val serverTaskId = jsonObject?.get("taskId") as? String
                    if (serverTaskId != null && serverTaskId.isNotEmpty()) {
                        Log.d(TAG, "收到 agent_response, taskId=$serverTaskId, content长度=${content.length}")
                    }
                    
                    // 检测 chat 标签
                    val chatMessages = TaskManager.extractChatMessages(content)
                    if (chatMessages != null) {
                        Log.i(TAG, "========== 检测到 chat 标签 ==========")
                        Log.i(TAG, "消息内容: $chatMessages")
                        Log.i(TAG, "TaskId: $serverTaskId")
                        
                        // 通知外部处理 chat 消息
                        val callback = onChatMessage
                        if (callback != null) {
                            callback.invoke(chatMessages)
                            Log.i(TAG, "已调用 onChatMessage 回调")
                        } else {
                            Log.e(TAG, "onChatMessage 回调为空，无法显示 chat！")
                        }
                        Log.i(TAG, "==========================================")
                    }
                    
                    val responseResult = TaskManager.onAgentResponse(content, serverTaskId)
                    
                    // 如果检测到 resultText（attempt_completion 内的 result 标签），播放语音
                    if (responseResult.resultText != null) {
                        val resultText = responseResult.resultText
                        Log.i(TAG, "========== 检测到 result 文本，准备播放 ==========")
                        Log.i(TAG, "内容: $resultText")
                        Log.i(TAG, "==========================================")
                        
                        // 播放 TTS
                        ttsHelper.speak(resultText)
                    }
                    
                    // 如果检测到followup问题，通知外部处理
                    if (responseResult.followUpQuestion != null) {
                        val followUpQuestion = responseResult.followUpQuestion
                        Log.i(TAG, "========== 检测到 followup 问题 ==========")
                        Log.i(TAG, "问题: ${followUpQuestion.question}")
                        Log.i(TAG, "TaskId: ${followUpQuestion.taskId}")
                        
                        val callback = onFollowUpQuestion
                        Log.i(TAG, "onFollowUpQuestion 回调是否为空: ${callback == null}")
                        
                        if (callback != null) {
                            callback.invoke(followUpQuestion)
                            Log.i(TAG, "已调用 onFollowUpQuestion 回调")
                        } else {
                            Log.e(TAG, "onFollowUpQuestion 回调为空，无法显示通知！")
                        }
                        Log.i(TAG, "==========================================")
                    } else {
                        Log.d(TAG, "未检测到 followup 问题")
                    }
                    
                    // 如果检测到life_assistant标签，通知外部处理
                    if (responseResult.lifeAssistant != null) {
                        val lifeAssistant = responseResult.lifeAssistant
                        Log.i(TAG, "========== 检测到 life_assistant 标签 ==========")
                        Log.i(TAG, "JSON内容长度: ${lifeAssistant.jsonContent.length}")
                        Log.i(TAG, "TaskId: ${lifeAssistant.taskId}")
                        Log.i(TAG, "life_assistant 完整JSON内容:\n${lifeAssistant.jsonContent}")
                        
                        val callback = onLifeAssistant
                        Log.i(TAG, "onLifeAssistant 回调是否为空: ${callback == null}")
                        
                        if (callback != null) {
                            callback.invoke(lifeAssistant)
                            Log.i(TAG, "已调用 onLifeAssistant 回调")
                        } else {
                            Log.e(TAG, "onLifeAssistant 回调为空，无法显示 life_assistant！")
                        }
                        Log.i(TAG, "==========================================")
                    } else {
                        Log.d(TAG, "未检测到 life_assistant 标签")
                    }
                    return
                }
                "agent_complete" -> {
                    val content = jsonObject?.get("data") as? String ?: ""
                    val serverTaskId = jsonObject?.get("taskId") as? String
                    if (serverTaskId != null && serverTaskId.isNotEmpty()) {
                        Log.d(TAG, "收到 agent_complete, taskId=$serverTaskId")
                    }
                    TaskManager.onAgentComplete(content)
                    return
                }
                "task_completed" -> {
                    val serverTaskId = jsonObject?.get("taskId") as? String
                    val message = jsonObject?.get("message") as? String ?: "任务已完成"
                    Log.i(TAG, "收到任务完成通知: taskId=$serverTaskId, message=$message")
                    val currentTask = TaskManager.getCurrentTask()
                    if (currentTask != null && currentTask.isActive()) {
                        Log.i(TAG, "任务处于活跃状态，标记为完成")
                        TaskManager.onAgentComplete(message)
                    } else {
                        Log.d(TAG, "任务已是终态或不存在，忽略 task_completed 消息")
                    }
                    return
                }
                "task_cancelled" -> {
                    val serverTaskId = jsonObject?.get("taskId") as? String
                    val reason = jsonObject?.get("reason") as? String ?: ""
                    Log.i(TAG, "任务取消已确认: taskId=$serverTaskId, reason=$reason")
                    TaskManager.onTaskCancelled()
                    return
                }
                "task_cancel_error" -> {
                    val error = jsonObject?.get("error") as? String ?: "取消失败"
                    val serverTaskId = jsonObject?.get("taskId") as? String
                    Log.w(TAG, "任务取消失败: taskId=$serverTaskId, error=$error")
                    TaskManager.forceCancel("取消请求失败: $error")
                    return
                }
                "agent_error" -> {
                    val error = jsonObject?.get("error") as? String ?: "未知错误"
                    val serverTaskId = jsonObject?.get("taskId") as? String
                    Log.e(TAG, "Agent错误: taskId=$serverTaskId, error=$error")
                    TaskManager.onTaskError(error)
                    return
                }
                "error" -> {
                    val error = jsonObject?.get("data") as? String ?: "未知错误"
                    TaskManager.onTaskError(error)
                    return
                }
                "heartbeat", "connected", "installed_apps_response" -> {
                    return
                }
                // ========== 回放相关消息处理 ==========
                "replay_start" -> {
                    val replayTaskId = jsonObject?.get("taskId") as? String ?: ""
                    val message = jsonObject?.get("message") as? String ?: "开始回放..."
                    Log.i(TAG, "收到回放开始消息: taskId=$replayTaskId, message=$message")
                    return
                }
                "replay_progress" -> {
                    val replayTaskId = jsonObject?.get("taskId") as? String ?: ""
                    val step = (jsonObject?.get("step") as? Number)?.toInt() ?: 0
                    val total = (jsonObject?.get("total") as? Number)?.toInt() ?: 0
                    val action = jsonObject?.get("action") as? String ?: ""
                    val success = jsonObject?.get("success") as? Boolean ?: true
                    Log.i(TAG, "收到回放进度: taskId=$replayTaskId, step=$step/$total, action=$action")
                    TaskManager.onReplayProgress(replayTaskId, step, total, action, success)
                    return
                }
                "replay_complete" -> {
                    val replayTaskId = jsonObject?.get("taskId") as? String ?: ""
                    val success = jsonObject?.get("success") as? Boolean ?: true
                    val successCount = (jsonObject?.get("successCount") as? Number)?.toInt() ?: 0
                    val failCount = (jsonObject?.get("failCount") as? Number)?.toInt() ?: 0
                    val message = jsonObject?.get("message") as? String
                    Log.i(TAG, "收到回放完成: taskId=$replayTaskId, success=$success, 成功=$successCount, 失败=$failCount")
                    TaskManager.onReplayComplete(replayTaskId, success, successCount, failCount, message)
                    return
                }
                "replay_error" -> {
                    val replayTaskId = jsonObject?.get("taskId") as? String ?: ""
                    val error = jsonObject?.get("error") as? String ?: "未知错误"
                    Log.e(TAG, "收到回放错误: taskId=$replayTaskId, error=$error")
                    TaskManager.onReplayError(replayTaskId, error)
                    return
                }
                "replay_cancelled" -> {
                    val replayTaskId = jsonObject?.get("taskId") as? String ?: ""
                    val cancelledAtStep = (jsonObject?.get("cancelledAtStep") as? Number)?.toInt() ?: 0
                    val total = (jsonObject?.get("total") as? Number)?.toInt() ?: 0
                    val reason = jsonObject?.get("reason") as? String ?: "用户取消"
                    Log.i(TAG, "收到回放取消: taskId=$replayTaskId, step=$cancelledAtStep/$total, reason=$reason")
                    TaskManager.onReplayCancelled(replayTaskId, cancelledAtStep, total, reason)
                    return
                }
                "replay_cancel_ack" -> {
                    val replayTaskId = jsonObject?.get("taskId") as? String ?: ""
                    Log.i(TAG, "回放取消请求已确认: taskId=$replayTaskId")
                    return
                }
                "replay_cancel_error" -> {
                    val replayTaskId = jsonObject?.get("taskId") as? String ?: ""
                    val error = jsonObject?.get("error") as? String ?: "取消失败"
                    Log.w(TAG, "回放取消失败: taskId=$replayTaskId, error=$error")
                    return
                }
            }

            // 解析指令（提前解析，以便判断是否需要无障碍服务）
            val command = parseCommand(json, jsonObject, reqId)
            
            if (command == null) {
                if (reqId != null) {
                    sendClientResponse(reqId, false, "消息缺少 action 字段", null)
                }
                return
            }
            
            // 不需要无障碍服务的命令：直接处理
            if (command.action == "schedule_task") {
                TaskManager.onCommandReceived(command.action, reqId)
                handleScheduleTask(command, reqId)
                return
            }
            
            val service = MyAccessibilityService.instance
            if (service == null) {
                if (reqId != null) {
                    sendClientResponse(reqId, false, "无障碍服务未启用", null)
                } else {
                    sendResponse(CommandResponse("unknown", false, "无障碍服务未启用"))
                }
                return
            }

            TaskManager.onCommandReceived(command.action, reqId)

            val isLast = command.params?.isLast == true
            commandExecutor.execute(service, command, takeScreenshotAfter = isLast && reqId != null) { result ->
                TaskManager.onCommandCompleted(command.action, result.success, result.message)

                if (reqId != null) {
                    sendClientResponse(reqId, result.success, result.message, result.data)
                } else {
                    sendResponse(CommandResponse(command.action, result.success, result.message))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "解析指令失败: ${e.message}", e)
            sendResponse(CommandResponse("unknown", false, "指令解析失败: ${e.message}"))
        }
    }

    /**
     * 解析指令，支持两种格式
     */
    private fun parseCommand(
        json: String,
        jsonObject: Map<String, Any?>?,
        reqId: String?
    ): AccessibilityCommand? {
        return if (reqId != null && jsonObject != null) {
            val action = jsonObject["action"] as? String ?: return null
            val paramsObj = jsonObject["params"]
            val params = if (paramsObj != null) {
                val paramsJson = gson.toJson(paramsObj)
                gson.fromJson(paramsJson, CommandParams::class.java)
            } else {
                null
            }
            AccessibilityCommand(action = action, params = params)
        } else {
            gson.fromJson(json, AccessibilityCommand::class.java)
        }
    }

    /**
     * 发送客户端响应（带有 resId）
     */
    private fun sendClientResponse(resId: String, success: Boolean, message: String, data: Map<String, Any?>?) {
        val response = mutableMapOf<String, Any?>(
            "type" to "client_response",
            "resId" to resId,
            "data" to mapOf(
                "success" to success,
                "message" to message,
                "data" to data,
                "timestamp" to System.currentTimeMillis()
            )
        )
        val json = gson.toJson(response)
        Log.d(TAG, "发送客户端响应: $json")
        sendMessage(json)
    }
    
    /**
     * 处理定时任务命令（不需要无障碍服务）
     */
    private fun handleScheduleTask(command: AccessibilityCommand, reqId: String?) {
        commandExecutor.execute(null, command, takeScreenshotAfter = false) { result ->
            TaskManager.onCommandCompleted(command.action, result.success, result.message)
            
            if (reqId != null) {
                sendClientResponse(reqId, result.success, result.message, result.data)
            } else {
                sendResponse(CommandResponse(command.action, result.success, result.message))
            }
        }
    }

    fun isConnected(): Boolean = agentConnection.isConnected()

    /**
     * 取消当前任务
     * @param reason 取消原因
     * @return 是否成功发送取消请求
     */
    fun cancelCurrentTask(reason: String = "user_requested"): Boolean {
        val taskId = TaskManager.requestCancel(reason)
        if (taskId == null) {
            Log.w(TAG, "没有可取消的任务")
            return false
        }

        val clientId = DeviceUtils.getDeviceId(context)
        val cancelMessage = gson.toJson(mapOf(
            "type" to "task_cancel",
            "taskId" to taskId,
            "clientId" to clientId,
            "reason" to reason
        ))

        val sent = sendMessage(cancelMessage)
        Log.i(TAG, "发送取消请求: taskId=$taskId, sent=$sent")

        if (!sent) {
            TaskManager.forceCancel("发送取消请求失败")
        }

        return sent
    }

    /**
     * 发送Agent消息并创建Task
     * @param text 消息内容
     * @param androidId 设备ID
     * @return 创建的taskId
     */
    fun sendAgentMessageWithTask(text: String, androidId: String): String? {
        val taskId = TaskManager.createTask(text)
        val userId = TokenManager.getUsername(context) ?: androidId

        val dataMap = mutableMapOf<String, Any?>(
            "content" to text,
            "userId" to userId,
            "agentId" to "coder",
            "androidId" to androidId
        )

        val llmConfig = AppSettings.getLlmConfigMap(context)
        if (llmConfig != null) {
            dataMap["llmConfig"] = llmConfig
            Log.i(TAG, "使用自定义模型配置: ${llmConfig["llmName"]}")
        }

        val message = gson.toJson(mapOf(
            "type" to "agent",
            "taskId" to taskId,
            "data" to dataMap
        ))

        val sent = sendMessage(message)
        if (sent) {
            TaskManager.onTaskSent()
            Log.i(TAG, "Agent消息已发送: taskId=$taskId")
            return taskId
        } else {
            TaskManager.onTaskError("消息发送失败")
            Log.e(TAG, "Agent消息发送失败: taskId=$taskId")
            return null
        }
    }

    /**
     * 发送Agent消息到现有Task
     * @param text 消息内容
     * @param androidId 设备ID
     * @param taskId 现有任务ID
     * @return 是否发送成功
     */
    fun sendAgentMessageWithExistingTask(text: String, androidId: String, taskId: String): Boolean {
        val userId = TokenManager.getUsername(context) ?: androidId

        val dataMap = mutableMapOf<String, Any?>(
            "content" to text,
            "userId" to userId,
            "agentId" to "coder",
            "androidId" to androidId
        )

        val message = gson.toJson(mapOf(
            "type" to "agent",
            "taskId" to taskId,
            "data" to dataMap
        ))

        val sent = sendMessage(message)
        if (sent) {
            Log.i(TAG, "Agent消息已发送到现有任务: taskId=$taskId")
        } else {
            Log.e(TAG, "Agent消息发送失败: taskId=$taskId")
        }
        return sent
    }

    /**
     * 发送回放请求
     * @param taskId 要回放的任务ID
     * @param androidId 设备ID
     * @return 是否发送成功
     */
    fun sendReplayRequest(taskId: String, androidId: String): Boolean {
        val message = gson.toJson(mapOf(
            "type" to "replay",
            "taskId" to taskId,
            "data" to mapOf(
                "androidId" to androidId
            )
        ))

        val sent = sendMessage(message)
        if (sent) {
            Log.i(TAG, "回放请求已发送: taskId=$taskId")
        } else {
            Log.e(TAG, "回放请求发送失败: taskId=$taskId")
        }
        return sent
    }

    /**
     * 发送回放取消请求
     * @param taskId 要取消的原始任务ID（不是 replay_xxx 的临时ID）
     * @return 是否发送成功
     */
    fun sendReplayCancelRequest(taskId: String): Boolean {
        val message = gson.toJson(mapOf(
            "type" to "replay_cancel",
            "taskId" to taskId
        ))

        val sent = sendMessage(message)
        if (sent) {
            Log.i(TAG, "回放取消请求已发送: taskId=$taskId")
        } else {
            Log.e(TAG, "回放取消请求发送失败: taskId=$taskId")
        }
        return sent
    }
}
