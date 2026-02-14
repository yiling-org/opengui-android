package top.yling.ozx.guiagent

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yling.ozx.guiagent.databinding.ActivityMainBinding
import top.yling.ozx.guiagent.websocket.WebSocketService
import top.yling.ozx.guiagent.task.TaskManager
import top.yling.ozx.guiagent.ui.AIStatusIndicatorView
import top.yling.ozx.guiagent.util.PermissionHelper
import top.yling.ozx.guiagent.util.AppSettings
import top.yling.ozx.guiagent.util.VirtualDisplayManager
import top.yling.ozx.guiagent.shizuku.ShizukuApi
import top.yling.ozx.guiagent.app.activity.RecordingState
import top.yling.ozx.guiagent.app.activity.TaskStatus
import top.yling.ozx.guiagent.app.activity.InputMode
import top.yling.ozx.guiagent.app.activity.main.AnimationManager
import top.yling.ozx.guiagent.app.activity.main.FollowUpDialogManager
import top.yling.ozx.guiagent.app.activity.main.InputController
import top.yling.ozx.guiagent.app.activity.main.VirtualDisplayController
import top.yling.ozx.guiagent.speech.SpeechRecognizer
import top.yling.ozx.guiagent.speech.WakeUpEngine
import top.yling.ozx.guiagent.speech.SpeechServiceFactory
import top.yling.ozx.guiagent.data.model.TaskWithStepsDTO
import top.yling.ozx.guiagent.data.model.TaskApiResultResponse
import top.yling.ozx.guiagent.data.api.TaskApiClient
import okhttp3.*
import com.google.gson.Gson
import androidx.appcompat.app.AlertDialog
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var wakeUpEngine: WakeUpEngine
    private var webSocketService: WebSocketService? = null
    private var serviceBound = false

    // 动画管理器
    private lateinit var animationManager: AnimationManager
    
    // 虚拟屏幕控制器
    private lateinit var virtualDisplayController: VirtualDisplayController
    
    // FollowUp 对话框管理器
    private lateinit var followUpDialogManager: FollowUpDialogManager
    
    // 输入控制器
    private lateinit var inputController: InputController

    private var isRecording = false
    private val pendingRunnables = mutableListOf<Runnable>()
    private var lastNetworkCheckTime = 0L
    private var lastNetworkState = false
    
    // 语音识别结果回调（用于 stopListening 后获取结果）
    private var pendingRecognitionCallback: ((String) -> Unit)? = null

    // 任务状态管理
    private var currentTaskStatus = TaskStatus.IDLE
    private var currentTaskText = ""

    // AgentOverlayService是否由MainActivity启动（用于管理生命周期）
    private var overlayServiceStartedByMe = false

    // 权限检查标志，避免重复显示对话框
    private var permissionCheckShown = false



    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WebSocketService.LocalBinder
            webSocketService = binder.getService()
            serviceBound = true
            
            // 设置 followup 问题回调（用于 background 模式）
            // 注意：回调可能在Activity已destroyed时被触发，需要检查状态
            webSocketService?.onFollowUpQuestion = { followUp ->
                run {
                    android.util.Log.i("MainActivity", "收到 followup 问题: ${followUp.question}, TaskId: ${followUp.taskId}")
                    
                    // 检查Activity状态，避免在destroyed时显示对话框
                    if (this@MainActivity.isFinishing || this@MainActivity.isDestroyed) {
                        android.util.Log.w("MainActivity", "Activity已销毁，使用FollowUpAnswerActivity显示问题")
                        // 使用独立的Activity显示，避免崩溃
                        top.yling.ozx.guiagent.FollowUpAnswerActivity.start(this@MainActivity, followUp.question, followUp.taskId)
                        return@run
                    }
                    
                    // 检查Window状态
                    val decorView = this@MainActivity.window?.decorView
                    if (decorView == null || decorView.parent == null) {
                        android.util.Log.w("MainActivity", "Activity Window未attached，使用FollowUpAnswerActivity显示问题")
                        top.yling.ozx.guiagent.FollowUpAnswerActivity.start(this@MainActivity, followUp.question, followUp.taskId)
                        return@run
                    }
                    
                    // Activity状态正常，在应用内显示对话框
                    try {
                        this@MainActivity.showFollowUpQuestionDialog(followUp.question, followUp.taskId)
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "显示followup对话框失败，使用FollowUpAnswerActivity: ${e.message}", e)
                        // 失败时使用独立的Activity作为fallback
                        top.yling.ozx.guiagent.FollowUpAnswerActivity.start(this@MainActivity, followUp.question, followUp.taskId)
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            // 清除回调，避免在Activity destroyed后仍然被调用
            webSocketService?.onFollowUpQuestion = null
            webSocketService = null
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "用户授予了录音权限")
            Toast.makeText(this, "录音权限已授予，可以开始使用", Toast.LENGTH_SHORT).show()
            binding.statusText.text = "Ready, hold to speak"
        } else {
            android.util.Log.e("MainActivity", "用户拒绝了录音权限")
            Toast.makeText(this, "需要录音权限才能使用此功能", Toast.LENGTH_LONG).show()
            binding.statusText.text = "Microphone permission required"
        }
    }
    
    // 通知权限请求结果回调（Android 13+）
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "用户授予了通知权限")
        } else {
            android.util.Log.w("MainActivity", "用户拒绝了通知权限")
        }
    }

    // 无障碍服务权限请求结果回调
    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (isAccessibilityServiceEnabled()) {
            android.util.Log.d("MainActivity", "无障碍服务已启用")
            Toast.makeText(this, "无障碍服务已启用", Toast.LENGTH_SHORT).show()
        } else {
            android.util.Log.w("MainActivity", "无障碍服务未启用")
            Toast.makeText(this, "需要启用无障碍服务以实现自动化操作功能", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用边到边显示（Edge-to-Edge）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 隐藏标题栏
        supportActionBar?.hide()
        
        // 处理系统栏的内边距（避免与状态栏、导航栏重叠）
        setupWindowInsets()
        
        // 初始化语音服务（使用抽象接口，支持多种实现）
        speechRecognizer = SpeechServiceFactory.createSpeechRecognizer(this)
        wakeUpEngine = SpeechServiceFactory.createWakeUpEngine(this)
        
        // 初始化动画管理器
        animationManager = AnimationManager(this, binding)
        
        // 初始化虚拟屏幕控制器
        virtualDisplayController = VirtualDisplayController(this, binding, lifecycleScope)
        
        // 初始化 FollowUp 对话框管理器
        followUpDialogManager = FollowUpDialogManager(
            activity = this,
            speechRecognizer = speechRecognizer,
            getWebSocketService = { webSocketService },
            isServiceBound = { serviceBound },
            hasRecordPermission = { hasRecordPermission() },
            isNetworkAvailable = { isNetworkAvailable() },
            requestRecordPermission = { requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        )

        // 初始化唤醒服务（已禁用 - 目前不需要语音唤起功能）
        // initializeWakeUpService()
        
        // 尝试绑定 WebSocket 服务
        bindWebSocketServiceIfRunning()
        
        // 设置 WebSocket 消息监听
        // setupWebSocketMessageListener()
        
        setupUI()
        checkPermissions()
        
        // 检查所有必要权限（延迟执行，避免阻塞UI初始化）
        // 只在首次启动时检查，避免重复提示
        // 延迟 3 秒，给 Shizuku 自动授权时间（首次安装可能需要更长时间）
        binding.root.postDelayed({
            if (!permissionCheckShown) {
                checkAllRequiredPermissions()
            }
        }, 3000)
        
        // 输入模式已在 InputController.initialize() 中恢复和初始化

        // 初始化虚拟屏幕（如果允许后台运行）
        virtualDisplayController.initIfEnabled()

        // 检查是否有正在运行的任务
        checkCurrentTask()
        
        // 订阅 TaskManager 的任务状态变化（与灵动岛保持一致的机制）
        observeTaskManagerState()
    }
    
    /**
     * 观察 TaskManager 的任务状态变化
     * 当 TaskManager 中的任务状态更新时（如通过 WebSocket 消息检测到 attempt_completion），
     * 自动同步更新 MainActivity 的 UI 状态
     */
    private fun observeTaskManagerState() {
        lifecycleScope.launch {
            TaskManager.currentTask.collectLatest { task ->
                // 在主线程更新 UI
                if (task == null) {
                    // 没有任务，重置为空闲状态
                    if (currentTaskStatus == TaskStatus.RUNNING) {
                        android.util.Log.d("MainActivity", "[TaskManager] 任务已清除，重置为 IDLE")
                        updateTaskStatus(TaskStatus.IDLE, "")
                    }
                } else {
                    // 根据 TaskManager 的状态更新 UI
                    val newStatus = when (task.status) {
                        top.yling.ozx.guiagent.task.TaskStatus.PENDING -> TaskStatus.IDLE
                        top.yling.ozx.guiagent.task.TaskStatus.RUNNING -> TaskStatus.RUNNING
                        top.yling.ozx.guiagent.task.TaskStatus.PROCESSING -> TaskStatus.RUNNING
                        top.yling.ozx.guiagent.task.TaskStatus.EXECUTING -> TaskStatus.RUNNING
                        top.yling.ozx.guiagent.task.TaskStatus.COMPLETED -> TaskStatus.IDLE
                        top.yling.ozx.guiagent.task.TaskStatus.FAILED -> TaskStatus.IDLE
                        top.yling.ozx.guiagent.task.TaskStatus.CANCELLED -> TaskStatus.IDLE
                        top.yling.ozx.guiagent.task.TaskStatus.CANCELLING -> TaskStatus.RUNNING
                    }
                    
                    // 只有状态真正变化时才更新状态 UI
                    if (currentTaskStatus != newStatus) {
                        android.util.Log.d("MainActivity", "[TaskManager] 任务状态变化: ${task.status} -> UI状态: $newStatus")
                        val message = when (task.status) {
                            top.yling.ozx.guiagent.task.TaskStatus.COMPLETED -> task.result ?: "任务已完成"
                            top.yling.ozx.guiagent.task.TaskStatus.FAILED -> task.errorMessage ?: "任务失败"
                            top.yling.ozx.guiagent.task.TaskStatus.CANCELLED -> task.cancelReason ?: "任务已取消"
                            else -> task.lastMessage
                        }
                        updateTaskStatus(newStatus, message)
                    }
                    
                    // 更新步骤列表（无论状态是否变化，步骤可能有更新）
                    if (newStatus == TaskStatus.RUNNING && task.steps != null) {
                        updateTaskStepsFromTaskManager(task.steps!!)
                    }
                }
            }
        }
    }
    
    /**
     * 从 TaskManager 的步骤数据更新 UI
     * 将 StepInfo 转换为 StepDTO 格式后调用现有的更新方法
     */
    private fun updateTaskStepsFromTaskManager(steps: List<StepInfo>) {
        // 将 StepInfo 转换为 StepDTO
        val stepDTOs = steps.map { stepInfo ->
            TaskWithStepsDTO.StepDTO(
                stepIndex = stepInfo.stepIndex,
                stepName = stepInfo.stepName,
                status = stepInfo.status,
                startTime = stepInfo.startTime,
                endTime = stepInfo.endTime,
                errorMsg = stepInfo.errorMsg
            )
        }
        
        android.util.Log.d("MainActivity", "[TaskManager] 更新步骤列表，步骤数: ${stepDTOs.size}")
        
        // 更新步骤显示
        updateTaskStepsDisplay(stepDTOs)
        
        // 确保响应卡片可见
        if (stepDTOs.isNotEmpty() && binding.responseCard.visibility != View.VISIBLE) {
            binding.responseCard.alpha = 0f
            binding.responseCard.visibility = View.VISIBLE
            binding.responseCard.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }
    
    override fun onStart() {
        super.onStart()

        // 延迟初始化，等待布局完成
        binding.root.post {
            // 确保统一输入区域可见
            if (currentTaskStatus != TaskStatus.RUNNING) {
                binding.unifiedInputContainer.visibility = View.VISIBLE
                binding.unifiedInputContainer.alpha = 1f
            }
        }
    }
    
    // 保存原始底部边距（28dp转换为px）
    private var originalBottomMargin = 0
    
    /**
     * 设置窗口内边距，处理系统栏重叠问题
     * 同时处理键盘弹出时的布局调整
     */
    private fun setupWindowInsets() {
        // 保存原始底部边距（28dp，从布局文件中获取）
        originalBottomMargin = (28 * resources.displayMetrics.density).toInt()
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or 
                WindowInsetsCompat.Type.displayCutout()
            )
            
            // 获取键盘高度（如果键盘已弹出）
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardHeight = if (imeInsets.bottom > 0) imeInsets.bottom else 0
            
            // 为根布局设置内边距，避免内容被系统栏遮挡
            view.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                systemBarsInsets.bottom
            )
            
            // 如果键盘弹出，调整统一输入容器的底部边距，确保输入框在键盘上方可见
            val inputContainerParams = binding.unifiedInputContainer.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (inputContainerParams != null) {
                if (keyboardHeight > 0) {
                    // 键盘弹出时，设置底部边距为键盘高度，确保输入框在键盘上方
                    // 减去系统栏的底部高度，因为键盘已经考虑了系统栏
                    val adjustedKeyboardHeight = keyboardHeight - systemBarsInsets.bottom
                    inputContainerParams.bottomMargin = if (adjustedKeyboardHeight > 0) adjustedKeyboardHeight else keyboardHeight
                    binding.unifiedInputContainer.layoutParams = inputContainerParams
                    binding.unifiedInputContainer.requestLayout()
                } else {
                    // 键盘收起时，恢复原始底部边距
                    inputContainerParams.bottomMargin = originalBottomMargin
                    binding.unifiedInputContainer.layoutParams = inputContainerParams
                    binding.unifiedInputContainer.requestLayout()
                }
            }
            
            WindowInsetsCompat.CONSUMED
        }
    }
    
    /**
     * 管理延迟任务，确保在 Activity 销毁时可以取消
     */
    private fun postDelayedManaged(delayMillis: Long, action: () -> Unit) {
        val runnable = Runnable { action() }
        pendingRunnables.add(runnable)
        binding.root.postDelayed(runnable, delayMillis)
    }
    
    override fun onResume() {
        super.onResume()

        // 延迟绑定服务，避免阻塞主线程
        binding.root.post {
            bindWebSocketServiceIfRunning()
            // 重新设置 WebSocket 消息监听器
            // setupWebSocketMessageListener()
            
            // onResume时重新设置followup回调（如果服务已绑定）
            // 注意：只在Activity可见时设置回调，避免在后台时触发
            if (serviceBound && webSocketService != null) {
                webSocketService?.onFollowUpQuestion = { followUp ->
                    run {
                        android.util.Log.i("MainActivity", "收到 followup 问题: ${followUp.question}, TaskId: ${followUp.taskId}")
                        
                        // 检查Activity状态，避免在destroyed时显示对话框
                        if (this@MainActivity.isFinishing || this@MainActivity.isDestroyed) {
                            android.util.Log.w("MainActivity", "Activity已销毁，使用FollowUpAnswerActivity显示问题")
                            top.yling.ozx.guiagent.FollowUpAnswerActivity.start(this@MainActivity, followUp.question, followUp.taskId)
                            return@run
                        }
                        
                        // 检查Window状态
                        val decorView = this@MainActivity.window?.decorView
                        if (decorView == null || decorView.parent == null) {
                            android.util.Log.w("MainActivity", "Activity Window未attached，使用FollowUpAnswerActivity显示问题")
                            top.yling.ozx.guiagent.FollowUpAnswerActivity.start(this@MainActivity, followUp.question, followUp.taskId)
                            return@run
                        }
                        
                        // Activity状态正常，在应用内显示对话框
                        try {
                            this@MainActivity.showFollowUpQuestionDialog(followUp.question, followUp.taskId)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "显示followup对话框失败，使用FollowUpAnswerActivity: ${e.message}", e)
                            top.yling.ozx.guiagent.FollowUpAnswerActivity.start(this@MainActivity, followUp.question, followUp.taskId)
                        }
                    }
                }
            }
        }

        // 如果任务正在执行，确保AgentOverlayService正在运行
        if (currentTaskStatus == TaskStatus.RUNNING && !AgentOverlayService.isServiceRunning()) {
            startAgentOverlayForTask()
        }
        
        // 检查当前任务状态（只查询一次，不再轮询）
        checkCurrentTask()

        // 恢复脉冲动画（如果已启动）
        animationManager.resumeAll()

        // 检查所有必要权限
        if (!permissionCheckShown) {
            checkAllRequiredPermissions()
        }

        // 确保输入模式 UI 状态一致（InputController 已在初始化时恢复输入模式）
        ensureInputModeUIConsistent()
        
        // @author shanwb - 修复任务完成后返回首页，AI状态指示器仍显示运行中的问题
        // 强制同步 AI 状态指示器状态，确保与当前任务状态一致
        syncAIStatusIndicatorOnResume()

        // 启动唤醒服务（如果任务未在执行）
        if (currentTaskStatus != TaskStatus.RUNNING && hasRecordPermission()) {
            startWakeUpService()
        }

        // 如果后台模式已开启，更新按钮可见性（可能在设置页面改变了 debug 模式）
        if (AppSettings.isBackgroundRunEnabled(this)) {
            virtualDisplayController.updateButtonVisibility()
        }
    }
    
    override fun onStop() {
        super.onStop()
        // Activity 不可见时暂停动画，节省资源
        animationManager.pauseAll()
    }

    override fun onPause() {
        super.onPause()
        // 暂停动画，避免后台运行浪费资源
        animationManager.pauseAll()
        // 停止波形动画
        binding.waveformView.stopAnimation()
        // 停止唤醒服务（节省资源）
        stopWakeUpService()
        
        // Activity进入后台时，清除followup回调，避免在后台时显示对话框导致崩溃
        // 后台时应该使用FollowUpAnswerActivity（由WebSocketService直接启动）
        webSocketService?.onFollowUpQuestion = null
    }
    
    private fun bindWebSocketServiceIfRunning() {
        // 检查用户是否已登录，未登录则不自动连接
        if (!top.yling.ozx.guiagent.util.TokenManager.isLoggedIn(this)) {
            android.util.Log.d("MainActivity", "用户未登录，跳过 WebSocket 自动连接")
            return
        }

        // 如果服务正在运行且未绑定，则绑定它
        if (WebSocketService.isRunning && !serviceBound) {
            val intent = Intent(this, WebSocketService::class.java)
            val bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            android.util.Log.d("MainActivity", "尝试绑定 WebSocket 服务: $bound")
        } else if (!WebSocketService.isRunning) {
            // 服务未运行，启动服务
            android.util.Log.d("MainActivity", "WebSocket 服务未运行，正在启动...")
            val intent = Intent(this, WebSocketService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            // 延迟后再绑定
            binding.root.postDelayed({
                if (!serviceBound && WebSocketService.isRunning) {
                    val bindIntent = Intent(this, WebSocketService::class.java)
                    val bound = bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                    android.util.Log.d("MainActivity", "延迟绑定 WebSocket 服务: $bound")
                }
            }, 1000)
        } else {
            android.util.Log.d("MainActivity", "WebSocket 服务状态: isRunning=${WebSocketService.isRunning}, serviceBound=$serviceBound")
        }
    }
    
    /**
     * 设置 WebSocket 消息监听器
     */
    private fun setupWebSocketMessageListener() {
        // 在 onResume 中重新设置，确保绑定后能监听到消息
        binding.root.post {
            webSocketService?.onMessageReceived = { message ->
                handleWebSocketMessage(message)
            }
        }
    }
    
    /**
     * 处理 WebSocket 消息
     * 现在只处理错误消息，任务状态和日志完全通过轮询 API 来管理
     */
    private fun handleWebSocketMessage(message: String) {
        android.util.Log.d("MainActivity", "收到 WebSocket 消息: $message")
        
        try {
            // 解析 JSON 消息
            val gson = com.google.gson.Gson()
            val jsonObject = gson.fromJson(message, Map::class.java) as? Map<String, Any?>
            val type = jsonObject?.get("type") as? String
            
            when (type) {
                // agent_response 和 agent_complete 消息不再处理
                // 任务状态和日志现在完全通过轮询 API 来管理，不显示 WebSocket 消息
                "agent_response", "agent_complete" -> {
                    // 这些消息由 WebSocketClient 中的 TaskManager 处理
                    // MainActivity 不再需要处理这些消息，也不显示日志
                    android.util.Log.d("MainActivity", "收到 $type 消息，已由 TaskManager 处理，不显示日志")
                }
                "error" -> {
                    // 错误消息 - data 可能是字符串或对象
                    val errorMsg = when (val data = jsonObject["data"]) {
                        is String -> data
                        is Map<*, *> -> (data as? Map<String, Any?>)?.get("message") as? String ?: "未知错误"
                        else -> "未知错误"
                    }
                    
                    android.util.Log.e("MainActivity", "收到错误消息: $errorMsg")
                    
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "任务出错: $errorMsg", Toast.LENGTH_LONG).show()
                        
                        // 隐藏日志区域并重置
                        resetTaskStatus()
                    }
                }
                else -> {
                    // 其他类型的消息不处理，也不显示日志
                    android.util.Log.d("MainActivity", "收到 $type 消息，不处理")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "解析 WebSocket 消息失败: ${e.message}", e)
        }
    }
    
    private fun startButtonPulseAnimation() {
        animationManager.startButtonPulseAnimation()
    }
    
    /**
     * 启动主操作按钮的脉冲动画
     */
    private fun startRecordButtonPulseAnimation() {
        animationManager.startRecordButtonPulseAnimation()
    }
    
    private fun setupUI() {
        // 设置触摸目标优化
        ensureMinimumTouchTarget(binding.settingsButton, 48)
        ensureMinimumTouchTarget(binding.moreButton, 48)

        // 更多按钮点击事件
        binding.moreButton.setOnClickListener {
            animateHeaderButtonClick(binding.moreButton) {
                showHeaderMenu(binding.moreButton)
            }
        }

        // 设置按钮点击监听和动画
        binding.settingsButton.setOnClickListener {
            // 设置按钮有特殊的旋转动画
            ObjectAnimator.ofFloat(binding.settingsButton, "rotation", 0f, 360f).apply {
                duration = 600
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }

            animateHeaderButtonClick(binding.settingsButton) {
                announceForAccessibility("打开设置")
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
        }

        // 打断任务按钮点击监听
        binding.cancelTaskButton.setOnClickListener {
            cancelCurrentTask()
        }

        // 文字输入框回车键监听（委托给 InputController）
        binding.textInputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                inputController.sendTextMessage()
                true
            } else {
                false
            }
        }

        // 设置文本选择高亮颜色
        binding.textInputField.setHighlightColor(0x40FFFFFF.toInt())

        // ============================================
        // 初始化输入控制器（处理语音和文字输入）
        // ============================================
        inputController = InputController(
            activity = this,
            binding = binding,
            animationManager = animationManager,
            getCurrentTaskStatus = { currentTaskStatus },
            hasRecordPermission = { hasRecordPermission() },
            requestRecordPermission = { requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            startRecording = { startRecording() },
            cancelRecording = { cancelRecording() },
            stopRecording = { stopRecording() },
            sendToBackend = { text -> sendToBackend(text) },
            updateUIState = { state -> updateUIState(state) },
            announceForAccessibility = { msg -> announceForAccessibility(msg) },
            performHapticFeedback = { performHapticFeedback() },
            getIsRecording = { isRecording }
        )
        inputController.initialize()

        // 设置初始状态
        updateUIState(RecordingState.IDLE)
    }

    /**
     * 执行触觉反馈
     */
    private fun performHapticFeedback() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            binding.voiceInputCard.performHapticFeedback(
                android.view.HapticFeedbackConstants.KEYBOARD_TAP
            )
        } else {
            binding.voiceInputCard.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY
            )
        }
    }

    /**
     * 显示/隐藏取消录音提示（委托给 InputController）
     */
    private fun showCancelHint(show: Boolean) {
        inputController.showCancelHint(show)
    }

    /**
     * 语音卡片按下/松开动画
     */
    private fun animateVoiceCardPressed(pressed: Boolean) {
        animationManager.animateVoiceCardPressed(pressed)
    }

    private fun startVoiceGlowPulseAnimation() {
        animationManager.startVoiceGlowPulseAnimation()
    }

    private fun stopVoiceGlowPulseAnimation() {
        animationManager.stopVoiceGlowPulseAnimation()
    }

    /**
     * 取消录音（不保存）
     */
    private fun cancelRecording() {
        if (!isRecording) return

        isRecording = false

        // 恢复AI状态指示器
        setAIStatusListening(false)

        // 无障碍公告
        announceForAccessibility("录音已取消")

        // 停止波形动画
        binding.waveformView.stopAnimation()
        binding.waveformView.visibility = View.GONE
        binding.inputHintText.visibility = View.VISIBLE

        // 停止动画
        stopVoiceGlowPulseAnimation()
        animateVoiceCardPressed(false)
        stopRecordingAnimation()

        // 取消语音识别（不需要结果）
        speechRecognizer.cancel()

        updateUIState(RecordingState.IDLE)
    }

    /**
     * 模式切换按钮点击动画
     */
    private fun animateModeToggleClick(action: () -> Unit) {
        animationManager.animateModeToggleClick(action)
    }
    
    private fun startRecording() {
        android.util.Log.d("MainActivity", "开始录音")
        
        // 检查是否有任务正在执行
        if (currentTaskStatus == TaskStatus.RUNNING) {
            android.util.Log.w("MainActivity", "任务正在执行中，无法开始新的录音")
            Toast.makeText(this, "任务正在执行中，请等待完成或打断当前任务", Toast.LENGTH_LONG).show()
            return
        }
        
        // 再次确认权限
        if (!hasRecordPermission()) {
            android.util.Log.e("MainActivity", "尝试录音但没有权限")
            Toast.makeText(this, "没有录音权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查网络连接（讯飞 SDK 需要网络）
        if (!isNetworkAvailable()) {
            android.util.Log.e("MainActivity", "网络不可用，无法使用语音识别")
            Toast.makeText(this, "请先连接网络！\n语音识别需要网络连接", Toast.LENGTH_LONG).show()
            return
        }
        
        isRecording = true
        updateUIState(RecordingState.RECORDING)

        // 设置AI状态指示器为聆听状态
        setAIStatusListening(true)

        // 无障碍公告
        announceForAccessibility(getString(R.string.accessibility_recording_started))
        
        // 更新按钮内容描述
        binding.actionButton.contentDescription = getString(R.string.accessibility_record_button_pressed)
        
        // 开始语音识别（使用抽象接口，支持多种实现）
        try {
            android.util.Log.d("MainActivity", "启动语音识别 (${speechRecognizer.getName()})...")
            speechRecognizer.startListening(object : SpeechRecognizer.Callback {
                override fun onResult(result: SpeechRecognizer.Result) {
                    android.util.Log.d("MainActivity", "实时识别: ${result.text}, isFinal=${result.isFinal}")
                    runOnUiThread {
                        // 注释掉转录文本展示
                        // binding.transcriptionText.text = result.text
                    }
                    
                    // 如果是最终结果，处理它
                    if (result.isFinal && pendingRecognitionCallback != null) {
                        val callback = pendingRecognitionCallback
                        pendingRecognitionCallback = null
                        runOnUiThread {
                            callback?.invoke(result.text)
                        }
                    }
                }

                override fun onError(code: Int, message: String) {
                    android.util.Log.e("MainActivity", "识别错误: $message")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "语音识别错误: $message", Toast.LENGTH_LONG).show()
                        isRecording = false
                        updateUIState(RecordingState.ERROR)
                    }
                }

                override fun onStateChanged(state: SpeechRecognizer.State) {
                    android.util.Log.d("MainActivity", "识别状态变化: $state")
                }
            })
            android.util.Log.d("MainActivity", "语音识别已启动")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "识别启动异常: ${e.message}", e)
            Toast.makeText(this, "语音识别启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            isRecording = false
            updateUIState(RecordingState.IDLE)
            return
        }
        
        // 只在语音模式下启动录音相关动画
        if (inputController.currentInputMode == InputMode.VOICE) {
            // 隐藏提示文本，显示波形
            binding.inputHintText.visibility = View.GONE
            binding.waveformView.visibility = View.VISIBLE

            // 启动波形动画
            binding.waveformView.startAnimation()

            // 添加持续的脉冲动画
            startRecordingAnimation()
        }
    }
    
    private fun stopRecording() {
        isRecording = false

        // 恢复AI状态指示器
        setAIStatusListening(false)

        // 无障碍公告
        announceForAccessibility(getString(R.string.accessibility_recording_stopped))

        // 停止波形动画
        binding.waveformView.stopAnimation()
        binding.waveformView.visibility = View.GONE
        binding.inputHintText.visibility = View.VISIBLE

        // 停止语音卡片动画
        animateVoiceCardPressed(false)
        stopVoiceGlowPulseAnimation()

        // 取消持续动画
        stopRecordingAnimation()
        
        updateUIState(RecordingState.PROCESSING)
        
        // 设置停止后的回调
        pendingRecognitionCallback = { finalText ->
            android.util.Log.d("MainActivity", "识别结果回调被调用: '$finalText', 长度: ${finalText.length}")
            
            if (finalText.isNotEmpty()) {
                // 注释掉转录文本展示
                // binding.transcriptionText.text = finalText
                sendToBackend(finalText)
            } else {
                // 识别失败，显示错误状态
                updateUIState(RecordingState.ERROR)
                // 注释掉转录文本展示
                // binding.transcriptionText.text = "未识别到语音"
                binding.responseText.text = "请说话时间长一点或靠近麦克风"
                showCenteredToast("未识别到语音，请说话时间长一点", Toast.LENGTH_LONG)

                // 2秒后恢复到 IDLE 状态
                postDelayedManaged(2000) {
                    updateUIState(RecordingState.IDLE)
                }
            }
        }
        
        // 停止语音识别，结果会通过 onResult(isFinal=true) 回调
        speechRecognizer.stopListening()
    }
    
    /**
     * 显示 followup 问题对话框（用于 background 模式）
     * @param question 问题内容
     * @param taskId 任务ID
     */
    private fun showFollowUpQuestionDialog(question: String, taskId: String?) {
        followUpDialogManager.showDialog(question, taskId)
    }
    
    private fun sendToBackend(text: String) {
        android.util.Log.d("MainActivity", "Sending to agent via WebSocket: $text")

        val service = webSocketService
        if (service == null || !serviceBound) {
            android.util.Log.w("MainActivity", "WebSocket 服务未绑定，显示识别结果但不发送")
            // 显示成功状态（识别成功了，只是没有发送）
            updateUIState(RecordingState.SUCCESS)
            Toast.makeText(this, "识别成功！如需发送到服务器，请在设置中连接 WebSocket", Toast.LENGTH_LONG).show()
            
            // 2秒后重置UI
            postDelayedManaged(2000) {
                updateUIState(RecordingState.IDLE)
            }
            return
        }

        if (!service.isConnected()) {
            android.util.Log.w("MainActivity", "WebSocket 未连接，显示识别结果但不发送")
            updateUIState(RecordingState.SUCCESS)
            Toast.makeText(this, "识别成功！WebSocket 未连接，请先连接服务器", Toast.LENGTH_LONG).show()
            
            // 2秒后重置UI
            postDelayedManaged(2000) {
                updateUIState(RecordingState.IDLE)
            }
            return
        }

        // WebSocket 已连接，发送消息
        val androidId = top.yling.ozx.guiagent.util.DeviceUtils.getDeviceId(this)
        
        // 保存当前任务信息
        currentTaskText = text
        
        service.sendToAgent(
            text = text,
            androidId = androidId,
            onSuccess = {
                runOnUiThread {
                    // 启动AgentOverlayService显示任务状态（全局可见）
                    startAgentOverlayForTask()

                    // 清空响应文本（已移除日志展示区域）

                    // 注释掉转录文本展示
                    // 清空转录文本
                    // binding.transcriptionText.text = ""

                    // 标记任务开始（会自动设置状态文本、隐藏录音按钮和转录卡片）
                    updateTaskStatus(TaskStatus.RUNNING, "任务进行中...")

                    // 响应卡片将在收到步骤数据后自动显示，避免显示空卡片

                    // 延迟一小段时间后开始轮询任务状态（已注释，不再使用轮询）
                }
            },
            onError = { error ->
                runOnUiThread {
                    android.util.Log.e("MainActivity", "Failed to send: $error")
                    updateUIState(RecordingState.ERROR)
                    Toast.makeText(this@MainActivity, "发送失败: $error", Toast.LENGTH_LONG).show()

                    postDelayedManaged(2000) {
                        updateUIState(RecordingState.IDLE)
                    }
                }
            }
        )
    }
    
    private fun updateUIState(state: RecordingState) {
        // 如果任务正在运行，不修改 statusText（由 updateTaskStatus 控制）
        val isTaskRunning = currentTaskStatus == TaskStatus.RUNNING
        
        when (state) {
            RecordingState.IDLE -> {
                if (!isTaskRunning) {
                    binding.statusText.visibility = View.GONE
                }
                // 注释掉转录文本展示
                // binding.transcriptionText.text = ""
                binding.actionButton.contentDescription = getString(R.string.accessibility_record_button)
            }
            RecordingState.RECORDING -> {
                if (!isTaskRunning) {
                    binding.statusText.text = "Listening..."
                    binding.statusText.setTextColor(getColor(R.color.recording))
                    binding.statusText.visibility = View.VISIBLE
                }
                binding.actionButton.contentDescription = getString(R.string.accessibility_record_button_pressed)
            }
            RecordingState.PROCESSING -> {
                if (!isTaskRunning) {
                    binding.statusText.text = "Processing..."
                    binding.statusText.setTextColor(getColor(R.color.processing))
                    binding.statusText.visibility = View.VISIBLE
                }
                binding.actionButton.contentDescription = getString(R.string.accessibility_record_button_processing)
                announceForAccessibility(getString(R.string.accessibility_processing))
            }
            RecordingState.SUCCESS -> {
                if (!isTaskRunning) {
                    binding.statusText.text = "Success"
                    binding.statusText.setTextColor(getColor(R.color.success))
                }
                announceForAccessibility(getString(R.string.accessibility_success))
            }
            RecordingState.ERROR -> {
                if (!isTaskRunning) {
                    binding.statusText.text = "Error"
                    binding.statusText.setTextColor(getColor(R.color.error))
                }
                announceForAccessibility(getString(R.string.accessibility_error))
            }
        }
    }
    
    private fun animateButton(pressed: Boolean) {
        animateActionButton(pressed)
    }

    private fun animateActionButton(pressed: Boolean) {
        animationManager.animateActionButton(pressed)
    }
    
    private fun startRecordingAnimation() {
        animationManager.startRecordingAnimation()
    }
    
    private fun startRippleAnimation() {
        animationManager.startRippleAnimation()
    }
    
    private fun stopRecordingAnimation() {
        animationManager.stopRecordingAnimation()
    }
    
    /**
     * 停止所有录音相关的动画（用于切换到文字模式时）
     */
    private fun stopAllRecordingAnimations() {
        animationManager.stopAllRecordingAnimations()
    }
    
    private fun checkPermissions() {
        if (!hasRecordPermission()) {
            android.util.Log.d("MainActivity", "录音权限未授予，正在请求")
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            android.util.Log.d("MainActivity", "录音权限已授予")
            binding.statusText.text = "Ready, hold to speak"
        }
    }
    
    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun isNetworkAvailable(): Boolean {
        // 缓存网络状态，1秒内不重复检查，避免频繁系统调用
        val now = System.currentTimeMillis()
        if (now - lastNetworkCheckTime < 1000) {
            return lastNetworkState
        }
        
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        lastNetworkState = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                   capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo?.isConnected == true
        }
        
        lastNetworkCheckTime = now
        return lastNetworkState
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 取消所有待处理的任务
        pendingRunnables.forEach { binding.root.removeCallbacks(it) }
        pendingRunnables.clear()
        
        if (isRecording) {
            speechRecognizer.cancel()
        }
        speechRecognizer.release()
        wakeUpEngine.release()
        
        if (serviceBound) {
            // 清除回调，避免在Activity destroyed后仍然被调用
            webSocketService?.onFollowUpQuestion = null
            unbindService(serviceConnection)
            serviceBound = false
        }
        
        // 清理动画管理器
        animationManager.cleanup()

        // 清理虚拟屏幕控制器
        virtualDisplayController.cleanup()
        
        // 清理 FollowUp 对话框管理器
        followUpDialogManager.cleanup()
    }
    
    // 枚举类已提取到 MainActivityEnums.kt
    
    /**
     * 发送无障碍公告
     */
    private fun announceForAccessibility(message: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            @Suppress("DEPRECATION")
            binding.root.announceForAccessibility(message)
        }
    }
    
    /**
     * 确保视图有最小触摸目标尺寸（通过padding扩展）
     * @param view 视图
     * @param minSizeDp 最小尺寸（dp）
     */
    private fun ensureMinimumTouchTarget(view: View, minSizeDp: Int) {
        val minSizePx = (minSizeDp * resources.displayMetrics.density).toInt()
        view.post {
            val width = view.width
            val height = view.height
            if (width < minSizePx || height < minSizePx) {
                val extraWidth = maxOf(0, minSizePx - width) / 2
                val extraHeight = maxOf(0, minSizePx - height) / 2
                view.setPadding(
                    view.paddingLeft + extraWidth,
                    view.paddingTop + extraHeight,
                    view.paddingRight + extraWidth,
                    view.paddingBottom + extraHeight
                )
            }
        }
    }
    
    /**
     * 启动AgentOverlayService用于任务状态显示
     */
    private fun startAgentOverlayForTask() {
        if (!AgentOverlayService.isServiceRunning()) {
            AgentOverlayService.startService(this)
            overlayServiceStartedByMe = true
            android.util.Log.d("MainActivity", "启动AgentOverlayService用于任务状态显示")
        }
    }

    /**
     * 停止由MainActivity启动的AgentOverlayService
     */
    private fun stopAgentOverlayIfNeeded() {
        if (overlayServiceStartedByMe && AgentOverlayService.isServiceRunning()) {
            AgentOverlayService.stopService(this)
            overlayServiceStartedByMe = false
            android.util.Log.d("MainActivity", "停止AgentOverlayService")
        }
    }

    /**
     * 同步状态到AgentOverlayService
     */
    private fun syncStatusToOverlay(status: TaskStatus) {
        val overlayStatus = when (status) {
            TaskStatus.IDLE -> AgentOverlayService.STATUS_IDLE
            TaskStatus.RUNNING -> AgentOverlayService.STATUS_PROCESSING
            TaskStatus.COMPLETED -> AgentOverlayService.STATUS_COMPLETED
            TaskStatus.CANCELLED -> AgentOverlayService.STATUS_ERROR
        }
        AgentOverlayService.instance?.setStatus(overlayStatus)
    }

    /**
     * 更新AI状态指示器
     * 根据任务状态切换动画效果：
     * - IDLE: 呼吸动画
     * - RUNNING: 旋转动画
     * - COMPLETED/CANCELLED: 先显示完成/取消效果，再恢复呼吸
     */
    private fun updateAIStatusIndicator(status: TaskStatus) {
        val indicatorStatus = when (status) {
            TaskStatus.IDLE -> AIStatusIndicatorView.Status.IDLE
            TaskStatus.RUNNING -> AIStatusIndicatorView.Status.PROCESSING
            TaskStatus.COMPLETED, TaskStatus.CANCELLED -> AIStatusIndicatorView.Status.IDLE
        }
        binding.aiStatusIndicator.setStatus(indicatorStatus)
    }
    
    /**
     * 在 onResume 时同步 AI 状态指示器
     * 
     * @author shanwb - 修复任务完成后返回首页，AI状态指示器仍显示运行中的问题
     * 
     * 问题原因：
     * 1. 任务完成后，TaskManager.currentTask 仍然保持着 COMPLETED 状态的任务（没有被清除）
     * 2. observeTaskManagerState 中的逻辑只在状态变化时才更新 UI
     * 3. 当用户回到 MainActivity 时，如果 currentTaskStatus 已经是 IDLE，
     *    但 AIStatusIndicatorView 可能仍然处于 PROCESSING 状态（因为 View 重新 attach 时
     *    会根据其内部 currentStatus 重新启动动画）
     * 
     * 解决方案：
     * 在 onResume 时，根据 TaskManager 的实际任务状态，强制同步 AI 状态指示器
     */
    private fun syncAIStatusIndicatorOnResume() {
        val task = TaskManager.getCurrentTask()
        val expectedIndicatorStatus = if (task == null || task.isTerminal()) {
            // 没有任务或任务已终结，应该显示 IDLE 状态
            AIStatusIndicatorView.Status.IDLE
        } else if (task.isActive()) {
            // 任务正在执行，显示 PROCESSING 状态
            AIStatusIndicatorView.Status.PROCESSING
        } else {
            AIStatusIndicatorView.Status.IDLE
        }
        
        // 获取当前指示器状态
        val currentIndicatorStatus = binding.aiStatusIndicator.getStatus()
        
        // 如果状态不一致，强制更新
        if (currentIndicatorStatus != expectedIndicatorStatus) {
            android.util.Log.d("MainActivity", 
                "[syncAIStatusIndicator] 状态不一致，强制更新: $currentIndicatorStatus -> $expectedIndicatorStatus" +
                " (task=${task?.taskId}, taskStatus=${task?.status})")
            binding.aiStatusIndicator.setStatus(expectedIndicatorStatus)
            
            // 同时更新 currentTaskStatus 以保持一致
            if (expectedIndicatorStatus == AIStatusIndicatorView.Status.IDLE && currentTaskStatus == TaskStatus.RUNNING) {
                android.util.Log.d("MainActivity", "[syncAIStatusIndicator] 同步更新 currentTaskStatus: RUNNING -> IDLE")
                currentTaskStatus = TaskStatus.IDLE
            }
        }
    }

    /**
     * 设置AI状态指示器为聆听状态
     * 用于录音时的脉冲动画效果
     */
    private fun setAIStatusListening(listening: Boolean) {
        if (listening) {
            binding.aiStatusIndicator.setStatus(AIStatusIndicatorView.Status.LISTENING)
        } else {
            // 根据当前任务状态恢复
            updateAIStatusIndicator(currentTaskStatus)
        }
    }

    /**
     * 统一的Header按钮点击动画
     * 提供优雅的缩放反馈效果
     */
    private fun animateHeaderButtonClick(button: View, action: () -> Unit) {
        animationManager.animateHeaderButtonClick(button, action)
    }
    
    /**
     * 显示Header菜单
     * 使用自定义PopupWindow，完全控制深色主题样式
     */
    private fun showHeaderMenu(anchor: View) {
        val menuView = layoutInflater.inflate(R.layout.custom_header_menu, null)

        // 创建PopupWindow
        val popupWindow = android.widget.PopupWindow(
            menuView,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            elevation = 8f
            isOutsideTouchable = true
            isFocusable = true
        }
        
        // 虚拟屏开关
        val virtualDisplaySwitch = menuView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.virtualDisplaySwitch)
        val menuItemVirtualDisplay = menuView.findViewById<View>(R.id.menuItemVirtualDisplay)

        // 初始化开关状态
        virtualDisplaySwitch.isChecked = AppSettings.isBackgroundRunEnabled(this)

        // 点击整个菜单项切换开关
        menuItemVirtualDisplay.setOnClickListener {
            val newState = !virtualDisplaySwitch.isChecked
            virtualDisplaySwitch.isChecked = newState
            toggleDisplayMode(newState)
        }

        // 开关本身的点击事件
        virtualDisplaySwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleDisplayMode(isChecked)
        }

        // 设置菜单项点击事件
        menuView.findViewById<View>(R.id.menuItemMinimize).setOnClickListener {
            popupWindow.dismiss()
            moveTaskToBack(true)
        }

        menuView.findViewById<View>(R.id.menuItemHistory).setOnClickListener {
            popupWindow.dismiss()
            announceForAccessibility("打开任务历史")
            val intent = Intent(this, TaskHistoryActivity::class.java)
            startActivity(intent)
        }

        menuView.findViewById<View>(R.id.menuItemSkills).setOnClickListener {
            popupWindow.dismiss()
            announceForAccessibility("打开技能管理")
            val intent = Intent(this, SkillListActivity::class.java)
            startActivity(intent)
        }

        menuView.findViewById<View>(R.id.menuItemScheduledTasks)?.setOnClickListener {
            android.util.Log.d("MainActivity", "定时任务菜单项被点击")
            popupWindow.dismiss()
            announceForAccessibility("打开定时任务管理")
            val intent = Intent(this, top.yling.ozx.guiagent.scheduler.ui.ScheduledTaskActivity::class.java)
            startActivity(intent)
        } ?: android.util.Log.e("MainActivity", "找不到 menuItemScheduledTasks")

        // AI Agents 入口已注释
        // menuView.findViewById<View>(R.id.menuItemAgents).setOnClickListener {
        //     popupWindow.dismiss()
        //     announceForAccessibility("打开AI Agents")
        //     val intent = Intent(this, AgentListActivity::class.java)
        //     startActivity(intent)
        // }

        // 测量菜单视图尺寸
        menuView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        )
        
        // 计算菜单位置（右对齐，按钮下方，更靠右）
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]
        val menuWidth = menuView.measuredWidth
        
        // 右对齐：按钮右边缘对齐菜单右边缘，再向右偏移更多
        val rightOffset = (32 * resources.displayMetrics.density).toInt() // 向右偏移32dp
        val x = anchorX + anchor.width - menuWidth + rightOffset
        val y = anchorY + anchor.height + (6 * resources.displayMetrics.density).toInt()
        
        // 显示菜单
        popupWindow.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
    }

    /**
     * 更新任务状态
     */
    private fun updateTaskStatus(status: TaskStatus, message: String) {
        currentTaskStatus = status

        // 同步状态到AgentOverlayService
        syncStatusToOverlay(status)

        // 更新AI状态指示器
        updateAIStatusIndicator(status)

        when (status) {
            TaskStatus.IDLE -> {
                // 恢复到空闲状态
                binding.statusText.visibility = View.GONE
                binding.taskStatusText.visibility = View.GONE

                // 隐藏取消按钮（带动画）
                if (binding.cancelTaskButton.visibility == View.VISIBLE) {
                    binding.cancelTaskButton.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setDuration(200)
                        .withEndAction {
                            binding.cancelTaskButton.visibility = View.GONE
                            binding.cancelTaskButton.alpha = 1f
                            binding.cancelTaskButton.scaleX = 1f
                            binding.cancelTaskButton.scaleY = 1f
                        }
                        .start()
                } else {
                    binding.cancelTaskButton.visibility = View.GONE
                }

                // 隐藏响应卡片（带动画）
                if (binding.responseCard.visibility == View.VISIBLE) {
                    binding.responseCard.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            binding.responseCard.visibility = View.GONE
                            binding.responseCard.alpha = 1f
                        }
                        .start()
                } else {
                    binding.responseCard.visibility = View.GONE
                }

                // 注释掉转录卡片展示
                // binding.transcriptionCard.visibility = View.GONE

                // 显示统一输入区域（带动画）
                if (binding.unifiedInputContainer.visibility != View.VISIBLE) {
                    binding.unifiedInputContainer.alpha = 0f
                    binding.unifiedInputContainer.visibility = View.VISIBLE
                    binding.unifiedInputContainer.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start()
                } else {
                    binding.unifiedInputContainer.alpha = 1f
                }

                // 根据当前输入模式更新UI
                updateInputModeUI()
            }
            TaskStatus.RUNNING -> {
                // 任务执行中
                binding.statusText.text = if (message.isNotEmpty()) message else "任务进行中..."
                binding.statusText.setTextColor(getColor(R.color.processing))
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.alpha = 1f
                binding.taskStatusText.visibility = View.GONE

                // 先隐藏统一输入区域（带动画）
                if (binding.unifiedInputContainer.visibility == View.VISIBLE) {
                    binding.unifiedInputContainer.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            binding.unifiedInputContainer.visibility = View.GONE
                            binding.unifiedInputContainer.alpha = 1f
                        }
                        .start()
                } else {
                    binding.unifiedInputContainer.visibility = View.GONE
                }

                // 显示取消按钮（带动画）
                if (binding.cancelTaskButton.visibility != View.VISIBLE) {
                    binding.cancelTaskButton.alpha = 0f
                    binding.cancelTaskButton.scaleX = 0.8f
                    binding.cancelTaskButton.scaleY = 0.8f
                    binding.cancelTaskButton.visibility = View.VISIBLE
                    binding.cancelTaskButton.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(300)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }

                // 响应卡片将在收到步骤数据后自动显示，避免显示空卡片
                // 步骤数据到达时会通过 handleTaskStepsUpdate 触发显示

                if (message.isNotEmpty()) {
                    announceForAccessibility(message)
                }
            }
            TaskStatus.COMPLETED -> {
                // 任务完成
                binding.statusText.text = "✓ $message"
                binding.statusText.setTextColor(getColor(R.color.success))
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.alpha = 1f
                binding.statusText.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction {
                        binding.statusText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()

                binding.taskStatusText.visibility = View.GONE

                // 隐藏取消按钮（带动画）
                if (binding.cancelTaskButton.visibility == View.VISIBLE) {
                    binding.cancelTaskButton.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setDuration(200)
                        .withEndAction {
                            binding.cancelTaskButton.visibility = View.GONE
                            binding.cancelTaskButton.alpha = 1f
                            binding.cancelTaskButton.scaleX = 1f
                            binding.cancelTaskButton.scaleY = 1f
                        }
                        .start()
                } else {
                    binding.cancelTaskButton.visibility = View.GONE
                }

                // responseCard 保持当前状态，只有有步骤数据时才显示
                announceForAccessibility("任务完成")
            }
            TaskStatus.CANCELLED -> {
                // 任务取消
                binding.statusText.text = "✗ $message"
                binding.statusText.setTextColor(getColor(R.color.error))
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.alpha = 1f
                binding.statusText.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(150)
                    .withEndAction {
                        binding.statusText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()

                binding.taskStatusText.visibility = View.GONE

                // 隐藏取消按钮（带动画）
                if (binding.cancelTaskButton.visibility == View.VISIBLE) {
                    binding.cancelTaskButton.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setDuration(200)
                        .withEndAction {
                            binding.cancelTaskButton.visibility = View.GONE
                            binding.cancelTaskButton.alpha = 1f
                            binding.cancelTaskButton.scaleX = 1f
                            binding.cancelTaskButton.scaleY = 1f
                        }
                        .start()
                } else {
                    binding.cancelTaskButton.visibility = View.GONE
                }

                // responseCard 保持当前状态，只有有步骤数据时才显示
                announceForAccessibility("任务已取消")
            }
        }
    }
    
    /**
     * 隐藏录音按钮及其相关装饰（保留向后兼容）
     */
    private fun hideRecordButton() {
        // 新布局中不需要此操作，统一输入区域在updateTaskStatus中控制
        binding.waveformView.visibility = View.GONE
        // 注释掉转录卡片展示
        // binding.transcriptionCard.visibility = View.GONE
    }

    /**
     * 显示录音按钮及其相关装饰（保留向后兼容）
     */
    private fun showRecordButton(animated: Boolean = false) {
        // 新布局中，只需要显示统一输入区域
        binding.unifiedInputContainer.visibility = View.VISIBLE
        binding.unifiedInputContainer.alpha = 1f
        updateInputModeUI()
    }
    
    /**
     * 重置任务状态
     */
    private fun resetTaskStatus() {
        updateTaskStatus(TaskStatus.IDLE, "")
        currentTaskText = ""

        // 注释掉转录文本展示
        // 重置所有文本显示
        // binding.transcriptionText.text = ""

        // 隐藏步骤列表
        hideTaskStepsDisplay()

        // 隐藏响应卡片和状态文本
        binding.responseCard.visibility = View.GONE
        binding.statusText.visibility = View.GONE

        // 显示统一输入区域
        binding.unifiedInputContainer.visibility = View.VISIBLE
        binding.unifiedInputContainer.alpha = 1f

        // 停止AgentOverlayService（任务已完成）
        stopAgentOverlayIfNeeded()
    }
    
    /**
     * 重置任务状态并显示录音UI（带淡入动画）
     */
    private fun resetTaskStatusAndShowRecording() {
        currentTaskText = ""

        // 注释掉转录文本展示
        // 重置所有文本显示
        // binding.transcriptionText.text = ""

        // 隐藏步骤列表
        hideTaskStepsDisplay()

        // 更新状态为 IDLE
        currentTaskStatus = TaskStatus.IDLE
        syncStatusToOverlay(TaskStatus.IDLE)

        binding.taskStatusText.visibility = View.GONE
        binding.statusText.visibility = View.GONE
        // 注释掉转录卡片展示
        // binding.transcriptionCard.visibility = View.GONE

        // 隐藏取消按钮（带动画）
        if (binding.cancelTaskButton.visibility == View.VISIBLE) {
            binding.cancelTaskButton.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(200)
                .withEndAction {
                    binding.cancelTaskButton.visibility = View.GONE
                    binding.cancelTaskButton.alpha = 1f
                    binding.cancelTaskButton.scaleX = 1f
                    binding.cancelTaskButton.scaleY = 1f
                }
                .start()
        } else {
            binding.cancelTaskButton.visibility = View.GONE
        }

        // 隐藏响应卡片（带动画）
        if (binding.responseCard.visibility == View.VISIBLE) {
            binding.responseCard.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    binding.responseCard.visibility = View.GONE
                    binding.responseCard.alpha = 1f
                }
                .start()
        } else {
            binding.responseCard.visibility = View.GONE
        }

        // 带淡入动画显示统一输入区域
        if (binding.unifiedInputContainer.visibility != View.VISIBLE) {
            binding.unifiedInputContainer.alpha = 0f
            binding.unifiedInputContainer.visibility = View.VISIBLE
            binding.unifiedInputContainer.animate()
                .alpha(1f)
                .setDuration(300)
                .withEndAction {
                    updateInputModeUI()
                }
                .start()
        } else {
            binding.unifiedInputContainer.alpha = 1f
            updateInputModeUI()
        }

        // 停止AgentOverlayService（任务已完成）
        stopAgentOverlayIfNeeded()

        // 重新启动唤醒服务（任务完成后）
        if (hasRecordPermission() && isNetworkAvailable()) {
            startWakeUpService()
        }
    }

    /**
     * 取消当前任务
     */
    private fun cancelCurrentTask() {
        if (currentTaskStatus != TaskStatus.RUNNING) {
            return
        }
        
        android.util.Log.i("MainActivity", "用户请求取消任务")
        
        val service = webSocketService
        if (service == null || !serviceBound || !service.isConnected()) {
            Toast.makeText(this, "无法取消任务：WebSocket 未连接", Toast.LENGTH_SHORT).show()
            updateTaskStatus(TaskStatus.CANCELLED, "任务已取消")
            postDelayedManaged(2500) {
                binding.statusText.animate()
                    .alpha(0.5f)
                    .setDuration(300)
                    .start()
                
                // 确保响应卡片已隐藏
                if (binding.responseCard.visibility == View.VISIBLE) {
                    binding.responseCard.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            binding.responseCard.visibility = View.GONE
                            binding.responseCard.alpha = 1f
                            postDelayedManaged(200) {
                                resetTaskStatusAndShowRecording()
                            }
                        }
                        .start()
                } else {
                    postDelayedManaged(200) {
                        resetTaskStatusAndShowRecording()
                    }
                }
            }
            return
        }
        
        // 直接调用 cancelCurrentTask 方法
        val cancelled = service.cancelCurrentTask("user_requested")
        
        if (cancelled) {
            android.util.Log.i("MainActivity", "取消请求已发送")
            
            updateTaskStatus(TaskStatus.CANCELLED, "任务已取消")

            // 延迟后重置状态（与任务完成效果一致，等待服务器确认）
            postDelayedManaged(2500) {
                binding.statusText.animate()
                    .alpha(0.5f)
                    .setDuration(300)
                    .start()
                
                // 确保响应卡片已隐藏
                if (binding.responseCard.visibility == View.VISIBLE) {
                    binding.responseCard.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            binding.responseCard.visibility = View.GONE
                            binding.responseCard.alpha = 1f
                            postDelayedManaged(200) {
                                resetTaskStatusAndShowRecording()
                            }
                        }
                        .start()
                } else {
                    postDelayedManaged(200) {
                        resetTaskStatusAndShowRecording()
                    }
                }
            }
        } else {
            android.util.Log.w("MainActivity", "没有可取消的任务或取消请求发送失败")
            Toast.makeText(this, "没有可取消的任务", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 切换输入模式（委托给 InputController）
     */
    private fun toggleInputMode() {
        inputController.toggleInputMode()
    }
    
    /**
     * 更新输入模式 UI（委托给 InputController）
     */
    private fun updateInputModeUI() {
        inputController.updateInputModeUI()
    }
    

    /**
     * 显示居中的 Toast 消息
     */
    private fun showCenteredToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        val toast = Toast.makeText(this, message, duration)
        toast.setGravity(android.view.Gravity.CENTER, 0, 0)
        toast.show()
    }

    /**
     * 检查当前任务（只查询一次，不再轮询）
     * 如果任务存在且是运行中状态，则更新任务状态；否则重置为 IDLE
     */
    private fun checkCurrentTask() {
        val androidId = top.yling.ozx.guiagent.util.DeviceUtils.getDeviceId(this)

        TaskApiClient.getCurrentTask(
            context = this,
            androidId = androidId,
            onSuccess = { taskDto ->
                runOnUiThread {
                    if (taskDto != null && TaskApiClient.isTaskRunning(taskDto.status)) {
                        android.util.Log.d("MainActivity", "任务存在且运行中，更新任务状态: ${taskDto.status}")
                        handleTaskStatusUpdate(taskDto)
                    } else {
                        if (taskDto != null) {
                            android.util.Log.d("MainActivity", "任务存在但不是运行中状态(${taskDto.status})，重置为 IDLE")
                        } else {
                            android.util.Log.d("MainActivity", "没有当前任务，重置为 IDLE")
                        }
                        resetTaskStatus()
                    }
                }
            },
            onError = { error ->
                android.util.Log.e("MainActivity", "检查当前任务失败: $error")
                runOnUiThread {
                    resetTaskStatus()
                }
            }
        )
    }
    
    /**
     * 处理任务状态更新
     */
    private fun handleTaskStatusUpdate(taskDto: TaskWithStepsDTO) {
        val status = taskDto.status ?: "UNKNOWN"
        val taskId = taskDto.taskId ?: "unknown"
        val description = taskDto.description ?: "未知任务"
        val currentStep = taskDto.currentStep ?: 0
        val totalSteps = taskDto.totalSteps ?: 0
        val progress = taskDto.progress ?: 0
        val lastMessage = taskDto.lastMessage
        val lastAction = taskDto.lastAction
        
        // 记录任务状态到日志
        android.util.Log.i("MainActivity", "========== 任务状态更新 ==========")
        android.util.Log.i("MainActivity", "任务ID: $taskId")
        android.util.Log.i("MainActivity", "任务描述: $description")
        android.util.Log.i("MainActivity", "任务状态: $status")
        android.util.Log.i("MainActivity", "当前步骤: $currentStep/$totalSteps")
        android.util.Log.i("MainActivity", "进度: $progress%")
        if (!lastMessage.isNullOrEmpty()) {
            android.util.Log.i("MainActivity", "最后消息: $lastMessage")
        }
        if (!lastAction.isNullOrEmpty()) {
            android.util.Log.i("MainActivity", "最后操作: $lastAction")
        }
        
        // 记录步骤信息
        if (!taskDto.steps.isNullOrEmpty()) {
            android.util.Log.i("MainActivity", "步骤详情:")
            taskDto.steps.forEachIndexed { index, step ->
                val stepName = step.stepName ?: "未知步骤"
                val stepStatus = step.status ?: "UNKNOWN"
                val stepIndex = step.stepIndex ?: index
                android.util.Log.i("MainActivity", "  步骤 $stepIndex: $stepName [状态: $stepStatus]")
                if (!step.errorMsg.isNullOrEmpty()) {
                    android.util.Log.w("MainActivity", "    错误: ${step.errorMsg}")
                }
            }
        }
        android.util.Log.i("MainActivity", "====================================")
        
        // 转换步骤信息
        val stepInfoList = taskDto.steps?.map { stepDto ->
            StepInfo(
                stepIndex = stepDto.stepIndex,
                stepName = stepDto.stepName,
                status = stepDto.status,
                startTime = stepDto.startTime,
                endTime = stepDto.endTime,
                errorMsg = stepDto.errorMsg
            )
        }
        
        // 同步任务信息到 TaskManager
        TaskManager.syncTaskFromApi(
            taskId = taskId,
            description = description,
            status = status,
            currentStep = currentStep,
            totalSteps = totalSteps,
            progress = progress,
            lastMessage = lastMessage,
            lastAction = lastAction,
            result = taskDto.result,
            errorMessage = taskDto.errorMessage,
            cancelReason = taskDto.cancelReason,
            createdAt = taskDto.createdAt,
            updatedAt = taskDto.updatedAt,
            endedAt = taskDto.endedAt,
            steps = stepInfoList
        )
        
        // 更新 UI 状态
        val taskStatus = when (status) {
            "PENDING", "RUNNING", "PROCESSING", "EXECUTING" -> TaskStatus.RUNNING
            "COMPLETED" -> TaskStatus.COMPLETED
            "FAILED", "CANCELLED" -> TaskStatus.CANCELLED
            else -> TaskStatus.IDLE
        }
        
        // 计算已完成步骤数（与步骤卡片保持一致）
        val completedStepsCount = if (!taskDto.steps.isNullOrEmpty()) {
            taskDto.steps.count {
                val stepStatus = it.status?.uppercase() ?: ""
                stepStatus == "COMPLETED" || stepStatus == "SUCCESS"
            }
        } else {
            0
        }
        val actualTotalSteps = taskDto.steps?.size ?: totalSteps

        // 构建状态消息（使用已完成数/总步数，与步骤卡片保持一致）
        val statusMessage = when {
            taskStatus == TaskStatus.RUNNING && actualTotalSteps > 0 -> {
                "任务进行中... ($completedStepsCount/$actualTotalSteps)"
            }
            taskStatus == TaskStatus.COMPLETED -> {
                "任务已完成"
            }
            taskStatus == TaskStatus.CANCELLED -> {
                "任务已取消"
            }
            else -> {
                "任务进行中..."
            }
        }
        
        // 更新任务状态
        updateTaskStatus(taskStatus, statusMessage)
        
        // 只在任务运行中时才显示步骤
        val isTaskRunning = taskStatus == TaskStatus.RUNNING
        
        if (isTaskRunning) {
            // 更新步骤列表显示（如果步骤为空会自动隐藏）
            updateTaskStepsDisplay(taskDto.steps)

            // 只有当步骤不为空时才确保响应卡片可见
            val hasSteps = !taskDto.steps.isNullOrEmpty()
            if (hasSteps && binding.responseCard.visibility != View.VISIBLE) {
                binding.responseCard.alpha = 0f
                binding.responseCard.visibility = View.VISIBLE
                binding.responseCard.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            } else if (!hasSteps) {
                // 步骤为空，隐藏步骤容器
                hideTaskStepsDisplay()
            }
        } else {
            // 任务已完成/取消/失败，隐藏步骤列表
            hideTaskStepsDisplay()
            if (binding.responseCard.visibility == View.VISIBLE) {
                binding.responseCard.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        binding.responseCard.visibility = View.GONE
                        binding.responseCard.alpha = 1f
                    }
                    .start()
            }
        }
        
        // 如果任务完成或取消，延迟后重置 UI（效果一致）
        if (taskStatus == TaskStatus.COMPLETED || taskStatus == TaskStatus.CANCELLED) {
            postDelayedManaged(2500) {
                binding.statusText.animate()
                    .alpha(0.5f)
                    .setDuration(300)
                    .start()
                
                // 确保响应卡片已隐藏
                if (binding.responseCard.visibility == View.VISIBLE) {
                    binding.responseCard.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction {
                            binding.responseCard.visibility = View.GONE
                            binding.responseCard.alpha = 1f
                            postDelayedManaged(200) {
                                resetTaskStatusAndShowRecording()
                            }
                        }
                        .start()
                } else {
                    postDelayedManaged(200) {
                        resetTaskStatusAndShowRecording()
                    }
                }
            }
        }
    }
    
    /**
     * 更新任务步骤显示（使用与任务历史页面相同的样式）
     * 如果步骤为空，则隐藏步骤容器
     */
    private fun updateTaskStepsDisplay(steps: List<TaskWithStepsDTO.StepDTO>?) {
        val stepsContainer = binding.taskStepsContainer
        val stepsList = binding.taskStepsList

        // 如果步骤为空或null，隐藏步骤容器并清空列表
        if (steps.isNullOrEmpty()) {
            stepsContainer.visibility = View.GONE
            stepsList.removeAllViews()
            return
        }

        // 过滤掉无效的步骤（stepName 为空或 null 的步骤）
        val validSteps = steps.filter { !it.stepName.isNullOrBlank() }
        
        // 如果过滤后没有有效步骤，也隐藏容器
        if (validSteps.isEmpty()) {
            stepsContainer.visibility = View.GONE
            stepsList.removeAllViews()
            return
        }

        // 显示步骤容器
        stepsContainer.visibility = View.VISIBLE

        // 清空 responseText，避免同时显示原始 Markdown 和解析后的步骤列表
        binding.responseText.text = ""

        // 显示步骤进度（与任务历史页面保持一致）
        val completedCount = validSteps.count {
            val status = it.status?.uppercase() ?: ""
            status == "COMPLETED" || status == "SUCCESS"
        }
        binding.stepsProgress.text = "$completedCount/${validSteps.size} 完成"

        // 清空现有步骤视图
        stepsList.removeAllViews()

        // 按步骤索引排序
        val sortedSteps = validSteps.sortedBy { it.stepIndex ?: 0 }

        // 为每个步骤创建视图
        sortedSteps.forEachIndexed { index, step ->
            val stepView = createStepView(step, index)
            stepsList.addView(stepView)
        }
    }

    /**
     * 创建步骤视图（使用与任务历史页面相同的布局）
     */
    private fun createStepView(step: TaskWithStepsDTO.StepDTO, index: Int): View {
        val stepIndex = step.stepIndex ?: (index + 1)
        val stepName = step.stepName ?: "未知步骤"
        val stepStatus = step.status ?: "UNKNOWN"

        // 使用与任务历史页面相同的布局
        val stepView = LayoutInflater.from(this)
            .inflate(R.layout.item_task_step, binding.taskStepsList, false)

        val stepStatusIcon = stepView.findViewById<ImageView>(R.id.stepStatusIcon)
        val stepNumber = stepView.findViewById<TextView>(R.id.stepNumber)
        val stepNameView = stepView.findViewById<TextView>(R.id.stepName)

        // 设置步骤序号
        stepNumber.text = "$stepIndex."

        // 设置步骤名称
        stepNameView.text = stepName

        // 根据状态设置图标和颜色（与 TaskHistoryActivity 保持一致）
        when (stepStatus.uppercase()) {
            "COMPLETED", "SUCCESS" -> {
                stepStatusIcon.setImageResource(R.drawable.ic_check_circle)
                stepNameView.setTextColor(getColor(R.color.white))
            }
            "RUNNING", "PROCESSING", "EXECUTING", "IN_PROGRESS" -> {
                stepStatusIcon.setImageResource(R.drawable.ic_running_circle)
                stepNameView.setTextColor(getColor(R.color.processing))
                // 为运行中的步骤添加脉冲动画
                val pulseAnimator = ObjectAnimator.ofFloat(stepStatusIcon, "alpha", 0.5f, 1f, 0.5f).apply {
                    duration = 1200
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                }
                pulseAnimator.start()
            }
            "FAILED", "ERROR" -> {
                stepStatusIcon.setImageResource(R.drawable.ic_error_circle)
                stepNameView.setTextColor(getColor(R.color.error))
            }
            "SKIPPED" -> {
                stepStatusIcon.setImageResource(R.drawable.ic_pending_circle)
                stepNameView.setTextColor(getColor(R.color.text_secondary))
                stepNameView.alpha = 0.6f
            }
            else -> { // PENDING 或其他状态
                stepStatusIcon.setImageResource(R.drawable.ic_pending_circle)
                stepNameView.setTextColor(getColor(R.color.text_secondary))
            }
        }

        // 如果有错误信息，显示在步骤名称中
        if (!step.errorMsg.isNullOrEmpty() && stepStatus.uppercase() in listOf("FAILED", "ERROR")) {
            stepNameView.text = "$stepName (错误: ${step.errorMsg})"
        }

        return stepView
    }

    /**
     * 隐藏任务步骤显示
     */
    private fun hideTaskStepsDisplay() {
        binding.taskStepsContainer.visibility = View.GONE
        binding.taskStepsList.removeAllViews()
    }

    
    /**
     * 确保输入模式 UI 状态一致
     * 在 onResume 时调用，防止切换应用后 UI 状态不一致
     */
    private fun ensureInputModeUIConsistent() {
        // 如果任务正在执行，不修改 UI
        if (currentTaskStatus == TaskStatus.RUNNING) {
            return
        }

        // 确保统一输入区域可见
        if (binding.unifiedInputContainer.visibility != View.VISIBLE) {
            binding.unifiedInputContainer.visibility = View.VISIBLE
            binding.unifiedInputContainer.alpha = 1f
        }

        // 委托给 InputController 更新 UI
        inputController.updateInputModeUI()
    }
    
    /**
     * 初始化唤醒服务
     */
    private fun initializeWakeUpService() {
        // 检查权限
        if (!hasRecordPermission()) {
            android.util.Log.d("MainActivity", "录音权限未授予，唤醒服务暂不启动")
            return
        }
        
        // 检查网络
        if (!isNetworkAvailable()) {
            android.util.Log.d("MainActivity", "网络不可用，唤醒服务暂不启动")
            return
        }
        
        // 启动唤醒服务
        startWakeUpService()
    }
    
    /**
     * 启动唤醒服务
     */
    private fun startWakeUpService() {
        if (currentTaskStatus == TaskStatus.RUNNING) {
            android.util.Log.d("MainActivity", "任务执行中，不启动唤醒服务")
            return
        }
        
        if (!hasRecordPermission()) {
            android.util.Log.d("MainActivity", "录音权限未授予，无法启动唤醒服务")
            return
        }
        
        if (!isNetworkAvailable()) {
            android.util.Log.d("MainActivity", "网络不可用，无法启动唤醒服务")
            return
        }
        
        android.util.Log.d("MainActivity", "启动唤醒服务")
        wakeUpEngine.startListening(object : WakeUpEngine.Callback {
            override fun onWakeUp(result: WakeUpEngine.WakeUpResult) {
                android.util.Log.d("MainActivity", "检测到唤醒词: ${result.keyword}")
                runOnUiThread {
                    // 显示唤醒提示
                    binding.statusText.text = "在，请说"
                    binding.statusText.setTextColor(getColor(R.color.recording))
                    Toast.makeText(this@MainActivity, "已唤醒，请说话", Toast.LENGTH_SHORT).show()
                    
                    // 唤醒后自动开始语音识别
                    startRecording()
                }
            }

            override fun onError(code: Int, message: String) {
                android.util.Log.e("MainActivity", "唤醒服务错误: $message")
            }

            override fun onStateChanged(state: WakeUpEngine.State) {
                android.util.Log.d("MainActivity", "唤醒服务状态变化: $state")
            }
        })
    }
    
    /**
     * 停止唤醒服务
     */
    private fun stopWakeUpService() {
        android.util.Log.d("MainActivity", "停止唤醒服务")
        wakeUpEngine.stopListening()
    }
    
    /**
     * 检查所有必要权限
     * 如果权限未授予，启动权限引导页面
     */
    private fun checkAllRequiredPermissions() {
        // 如果 Shizuku 可用且已授权，但无障碍服务未启用，使用轮询等待授权完成
        val shizukuAvailable = ShizukuApi.isAvailable() && ShizukuApi.shizukuGrantedFlow.value

        // 强制刷新 Settings 缓存后再检查
        try {
            contentResolver.notifyChange(
                android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                null
            )
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "刷新 Settings 缓存失败: ${e.message}")
        }

        // 延迟一小段时间让 Settings 更新
        binding.root.postDelayed({
            val accessibilityNotEnabled = !isAccessibilityServiceEnabled()
            android.util.Log.d("MainActivity", "检查无障碍服务: Shizuku可用=$shizukuAvailable, 无障碍未启用=$accessibilityNotEnabled")

            if (shizukuAvailable && accessibilityNotEnabled) {
                // Shizuku 可用，使用轮询方式等待自动授权完成
                android.util.Log.d("MainActivity", "Shizuku 可用，等待自动授权完成...")
                waitForAccessibilityServiceWithPolling(maxWaitTime = 5000, checkInterval = 500) { enabled ->
                    if (enabled) {
                        android.util.Log.d("MainActivity", "Shizuku 自动授权成功，无障碍服务已启用")
                    } else {
                        android.util.Log.d("MainActivity", "等待超时，无障碍服务仍未启用")
                    }
                    // 无论是否启用，都执行完整的权限检查
                    checkAllRequiredPermissionsInternal()
                }
            } else {
                // 正常执行权限检查
                checkAllRequiredPermissionsInternal()
            }
        }, 500)  // 延迟 500ms 让 Settings 更新
    }
    
    /**
     * 轮询等待无障碍服务启用
     * @param maxWaitTime 最大等待时间（毫秒）
     * @param checkInterval 检查间隔（毫秒）
     * @param onComplete 完成回调，参数为是否已启用
     */
    private fun waitForAccessibilityServiceWithPolling(
        maxWaitTime: Long,
        checkInterval: Long,
        onComplete: (Boolean) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val checkRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                // 强制刷新 Settings 缓存
                try {
                    // 通过重新获取来刷新缓存
                    contentResolver.notifyChange(
                        android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                        null
                    )
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "刷新 Settings 缓存失败: ${e.message}")
                }
                
                val isEnabled = isAccessibilityServiceEnabled()
                android.util.Log.d("MainActivity", "轮询检查无障碍服务: enabled=$isEnabled, elapsed=${elapsed}ms")
                
                if (isEnabled || elapsed >= maxWaitTime) {
                    // 已启用或超时，执行回调
                    android.util.Log.d("MainActivity", "轮询结束: enabled=$isEnabled, elapsed=${elapsed}ms")
                    onComplete(isEnabled)
                } else {
                    // 继续轮询
                    binding.root.postDelayed(this, checkInterval)
                }
            }
        }
        // 先等待一小段时间，让系统设置更新
        binding.root.postDelayed(checkRunnable, 1000)
    }
    
    /**
     * 内部方法：执行实际的权限检查
     * 使用 PermissionGuideActivity 提供统一的权限引导体验
     */
    private fun checkAllRequiredPermissionsInternal() {
        // 检查是否有缺失的权限
        if (PermissionGuideActivity.hasMissingPermissions(this)) {
            // 标记已显示权限检查，避免重复
            permissionCheckShown = true
            // 启动权限引导页面
            PermissionGuideActivity.start(this, showSkipButton = true)
        } else {
            android.util.Log.d("MainActivity", "所有必要权限已授予")
        }
    }
    
    /**
     * 检查无障碍服务是否已启用
     * 同时检查系统设置和服务实例
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        // 首先检查服务实例是否存在（更快速）
        if (MyAccessibilityService.instance != null) {
            return true
        }
        
        // 然后检查系统设置
        val expectedComponentName = ComponentName(this, MyAccessibilityService::class.java)
        val packageName = packageName
        
        try {
            val enabledServicesSetting = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            // 检查多种可能的格式
            val fullName = "$packageName/${MyAccessibilityService::class.java.name}"
            val shortName = "$packageName/.${MyAccessibilityService::class.java.simpleName}"
            
            // 直接字符串匹配（更快）
            if (enabledServicesSetting.contains(fullName) || 
                enabledServicesSetting.contains(shortName)) {
                return true
            }

            // 精确匹配（更可靠）
            val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledService = ComponentName.unflattenFromString(componentNameString)
                if (enabledService != null && enabledService == expectedComponentName) {
                    return true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "检查无障碍服务状态失败: ${e.message}", e)
        }
        
        return false
    }

    /**
     * 切换虚拟屏/物理屏模式（委托给 VirtualDisplayController）
     */
    private fun toggleDisplayMode(enableVirtualDisplay: Boolean) {
        virtualDisplayController.toggleDisplayMode(enableVirtualDisplay)
    }
}
