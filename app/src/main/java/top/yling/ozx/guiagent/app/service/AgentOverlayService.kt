package top.yling.ozx.guiagent

import android.app.Notification
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import top.yling.ozx.guiagent.ui.ScreenBorderGlowView
import top.yling.ozx.guiagent.ui.DynamicIslandView
import top.yling.ozx.guiagent.ui.ActionFeedbackView
import top.yling.ozx.guiagent.task.TaskManager
import top.yling.ozx.guiagent.task.TaskInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Agent覆盖层服务
 *
 * 功能特点:
 * - 统一管理AI控制相关的UI覆盖层
 * - 状态指示灯显示在状态栏左侧
 * - 屏幕边框光晕效果
 * - 截图时自动隐藏所有UI
 * - 支持音量键紧急中断
 */
class AgentOverlayService : Service() {

    companion object {
        private const val TAG = "AgentOverlayService"
        private const val NOTIFICATION_ID = 1002

        // 服务状态
        @Volatile
        private var isRunning = false

        // 单例实例，供外部调用
        @Volatile
        var instance: AgentOverlayService? = null
            private set

        fun isServiceRunning(): Boolean = isRunning

        fun startService(context: Context) {
            val intent = Intent(context, AgentOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, AgentOverlayService::class.java)
            context.stopService(intent)
        }

        // AI控制状态
        const val STATUS_IDLE = 0
        const val STATUS_RECORDING = 1
        const val STATUS_PROCESSING = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_ERROR = 4
    }

    private lateinit var windowManager: WindowManager
    private var dynamicIslandView: DynamicIslandView? = null
    private var borderGlowView: ScreenBorderGlowView? = null
    private var actionFeedbackView: ActionFeedbackView? = null
    private var dynamicIslandParams: WindowManager.LayoutParams? = null
    private var borderGlowParams: WindowManager.LayoutParams? = null
    private var actionFeedbackParams: WindowManager.LayoutParams? = null

    // 当前状态
    private var currentStatus = STATUS_IDLE

    // 处理器
    private val handler = Handler(Looper.getMainLooper())
    
    // 协程作用域
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // 中断回调
    var onInterruptRequested: (() -> Unit)? = null

    // 音量键监听（用于紧急中断）
    private var volumeKeyReceiver: BroadcastReceiver? = null
    private var lastVolumeDownTime = 0L
    private val DOUBLE_PRESS_INTERVAL = 500L  // 双击间隔

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 初始化UI组件
        // 先添加边框光晕（在底层）
        setupBorderGlow()
        // 添加操作反馈层（中层）
        setupActionFeedback()
        // 后添加灵动岛（在上层，覆盖光晕）
        setupDynamicIsland()

        // 注册音量键监听
        registerVolumeKeyReceiver()
        
        // 监听任务状态变化
        observeTaskUpdates()

        Log.i(TAG, "AgentOverlayService 已创建")
    }

    /**
     * 设置灵动岛
     * 位置：屏幕顶部中央（类似iOS Dynamic Island）
     */
    private fun setupDynamicIsland() {
        dynamicIslandView = DynamicIslandView(this)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 获取状态栏高度
        val statusBarHeight = getStatusBarHeight()
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // 灵动岛下移偏移量（8dp转为像素）
        val islandOffsetY = (8 * displayMetrics.density).toInt()

        dynamicIslandParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            // 关键flags：允许点击交互，覆盖状态栏区域
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // 位置：屏幕顶部中央，向下偏移10dp
            x = 0
            y = islandOffsetY
        }

        try {
            windowManager.addView(dynamicIslandView, dynamicIslandParams)
            Log.d(TAG, "灵动岛已添加，状态栏高度: $statusBarHeight")
        } catch (e: Exception) {
            Log.e(TAG, "添加灵动岛失败: ${e.message}", e)
        }
    }
    
    /**
     * 监听任务状态变化
     */
    private fun observeTaskUpdates() {
        serviceScope.launch {
            TaskManager.currentTask.collect { task ->
                handler.post {
                    // 更新灵动岛（没有任务时会自动隐藏）
                    dynamicIslandView?.updateTask(task)
                    
                    // 同步更新边框光晕状态
                    if (task != null) {
                        val status = when (task.status) {
                            top.yling.ozx.guiagent.task.TaskStatus.PENDING -> STATUS_IDLE
                            top.yling.ozx.guiagent.task.TaskStatus.RUNNING -> STATUS_PROCESSING
                            top.yling.ozx.guiagent.task.TaskStatus.PROCESSING -> STATUS_PROCESSING
                            top.yling.ozx.guiagent.task.TaskStatus.EXECUTING -> STATUS_PROCESSING
                            top.yling.ozx.guiagent.task.TaskStatus.COMPLETED -> STATUS_COMPLETED
                            top.yling.ozx.guiagent.task.TaskStatus.FAILED -> STATUS_ERROR
                            top.yling.ozx.guiagent.task.TaskStatus.CANCELLED -> STATUS_ERROR
                            top.yling.ozx.guiagent.task.TaskStatus.CANCELLING -> STATUS_ERROR
                        }
                        setStatus(status)
                    } else {
                        // 没有任务时，隐藏边框光晕
                        setStatus(STATUS_IDLE)
                        hideBorderGlow()
                    }
                }
            }
        }
    }

    /**
     * 获取系统状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        // 如果获取失败，使用默认值24dp
        if (result == 0) {
            result = (24 * resources.displayMetrics.density).toInt()
        }
        return result
    }

    /**
     * 设置屏幕边框光晕
     * 全屏覆盖，确保在所有应用之上显示
     */
    private fun setupBorderGlow() {
        borderGlowView = ScreenBorderGlowView(this)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 获取屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // Android 15 (API 35) 及以上版本，移除 FLAG_FULLSCREEN，使用更兼容的标志组合
        val flags = if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ 使用更兼容的标志组合
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
        } else {
            // Android 14 及以下使用原有标志
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        }

        borderGlowParams = WindowManager.LayoutParams(
            screenWidth,
            screenHeight + getStatusBarHeight() + getNavigationBarHeight(),  // 覆盖状态栏和导航栏
            layoutFlag,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(borderGlowView, borderGlowParams)
            Log.d(TAG, "边框光晕已添加，尺寸: ${screenWidth}x${screenHeight}, Android版本: ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.e(TAG, "添加边框光晕失败: ${e.message}", e)
        }
    }

    /**
     * 获取导航栏高度
     */
    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }
    }

    /**
     * 设置操作反馈层
     * 全屏覆盖，用于显示点击、滑动等操作的视觉反馈
     */
    private fun setupActionFeedback() {
        actionFeedbackView = ActionFeedbackView(this)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 获取屏幕尺寸
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        actionFeedbackParams = WindowManager.LayoutParams(
            screenWidth,
            screenHeight + getStatusBarHeight() + getNavigationBarHeight(),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(actionFeedbackView, actionFeedbackParams)
            Log.d(TAG, "操作反馈层已添加，尺寸: ${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "添加操作反馈层失败: ${e.message}", e)
        }
    }

    /**
     * 注册音量键监听（用于紧急中断）
     */
    private fun registerVolumeKeyReceiver() {
        volumeKeyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    // 检测音量变化，但这不是最佳方式
                    // 真正的音量键拦截需要在Activity或AccessibilityService中处理
                }
            }
        }

        try {
            val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            registerReceiver(volumeKeyReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "注册音量键监听失败: ${e.message}")
        }
    }

    /**
     * 处理音量键按下（需要从AccessibilityService调用）
     */
    fun handleVolumeKeyDown(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastVolumeDownTime < DOUBLE_PRESS_INTERVAL) {
            // 双击音量下键 - 触发紧急中断
            if (currentStatus == STATUS_PROCESSING) {
                Log.i(TAG, "检测到双击音量下键，触发紧急中断")
                requestInterrupt()
                return true
            }
        }
        lastVolumeDownTime = now
        return false
    }

    /**
     * 请求中断当前任务
     */
    fun requestInterrupt() {
        Log.i(TAG, "请求中断当前任务")
        onInterruptRequested?.invoke()
        setStatus(STATUS_IDLE)
    }

    /**
     * 设置状态
     */
    fun setStatus(status: Int) {
        if (currentStatus == status) return

        currentStatus = status
        Log.d(TAG, "状态更新: $status")

        handler.post {
            // 更新边框光晕（但不自动显示，需要调用 showBorderGlow() 来显示）
            when (status) {
                STATUS_IDLE -> borderGlowView?.setIdle()
                STATUS_RECORDING -> borderGlowView?.setRecording()
                STATUS_PROCESSING -> borderGlowView?.setProcessing()
                STATUS_COMPLETED -> borderGlowView?.setCompleted()
                STATUS_ERROR -> borderGlowView?.setError()
            }
        }
    }

    /**
     * 显示边框光晕（用于路线开始和任务开始）
     */
    fun showBorderGlow() {
        Log.d(TAG, "显示边框光晕，Android版本: ${Build.VERSION.SDK_INT}")
        handler.post {
            borderGlowView?.showGlow()
            // Android 15+ 需要强制刷新 View
            if (Build.VERSION.SDK_INT >= 35) {
                borderGlowView?.invalidate()
                borderGlowView?.requestLayout()
                // 确保 View 可见性正确设置
                borderGlowView?.visibility = View.VISIBLE
                // 强制更新窗口布局
                borderGlowParams?.let { params ->
                    try {
                        windowManager.updateViewLayout(borderGlowView, params)
                        Log.d(TAG, "已强制更新边框光晕窗口布局")
                    } catch (e: Exception) {
                        Log.e(TAG, "更新边框光晕窗口布局失败: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * 隐藏边框光晕
     */
    fun hideBorderGlow() {
        Log.d(TAG, "隐藏边框光晕")
        handler.post {
            borderGlowView?.hideGlow()
        }
    }

    /**
     * 隐藏所有UI（截图前调用）
     */
    fun hideAllForScreenshot() {
        Log.d(TAG, "隐藏所有UI用于截图")
        handler.post {
            dynamicIslandView?.hideForScreenshot()
            borderGlowView?.hideForScreenshot()
            actionFeedbackView?.hideForScreenshot()
        }
    }

    /**
     * 恢复显示所有UI（截图后调用）
     */
    fun showAllAfterScreenshot() {
        Log.d(TAG, "恢复显示所有UI")
        handler.post {
            dynamicIslandView?.showAfterScreenshot()
            borderGlowView?.showAfterScreenshot()
            actionFeedbackView?.showAfterScreenshot()
        }
    }

    // ========== 操作反馈 API ==========

    /**
     * 显示点击反馈效果
     * @param x 点击位置X坐标（屏幕绝对坐标）
     * @param y 点击位置Y坐标（屏幕绝对坐标）
     */
    fun showClickFeedback(x: Float, y: Float) {
        handler.post {
            actionFeedbackView?.showClickFeedback(x, y)
        }
    }

    /**
     * 显示长按反馈效果（开始）
     * @param x 长按位置X坐标
     * @param y 长按位置Y坐标
     */
    fun showLongPressFeedback(x: Float, y: Float) {
        handler.post {
            actionFeedbackView?.showLongPressFeedback(x, y)
        }
    }

    /**
     * 隐藏长按反馈效果（结束）
     */
    fun hideLongPressFeedback() {
        handler.post {
            actionFeedbackView?.hideLongPressFeedback()
        }
    }

    /**
     * 显示滑动反馈效果
     * @param startX 起始位置X
     * @param startY 起始位置Y
     * @param direction 方向（up/down/left/right）
     * @param distance 滑动距离
     */
    fun showScrollFeedback(startX: Float, startY: Float, direction: String, distance: Float) {
        handler.post {
            actionFeedbackView?.showScrollFeedback(startX, startY, direction, distance)
        }
    }

    /**
     * 显示拖拽反馈效果
     * @param startX 起始位置X
     * @param startY 起始位置Y
     * @param endX 结束位置X
     * @param endY 结束位置Y
     */
    fun showDragFeedback(startX: Float, startY: Float, endX: Float, endY: Float) {
        handler.post {
            actionFeedbackView?.showDragFeedback(startX, startY, endX, endY)
        }
    }

    /**
     * 显示输入反馈效果
     */
    fun showTypeFeedback() {
        handler.post {
            actionFeedbackView?.showTypeFeedback()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android 8.0+ 需要前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val emptyIntent = Intent()
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, emptyIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notification = Notification.Builder(this, MyApplication.CHANNEL_ID_DEFAULT)
                .setContentTitle(getString(R.string.app_name_assistant))
                .setContentText(getString(R.string.app_name_assistant_ready, getString(R.string.app_name_assistant)))
                .setSmallIcon(R.drawable.ic_microphone)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRunning = false

        // 取消协程
        serviceScope.cancel()
        
        // 移除UI组件
        try {
            dynamicIslandView?.let { windowManager.removeView(it) }
            borderGlowView?.let { windowManager.removeView(it) }
            actionFeedbackView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.e(TAG, "移除UI组件失败: ${e.message}")
        }

        // 注销音量键监听
        try {
            volumeKeyReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            Log.e(TAG, "注销音量键监听失败: ${e.message}")
        }

        // 清理Handler
        handler.removeCallbacksAndMessages(null)

        // 停止前台服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }

        Log.i(TAG, "AgentOverlayService 已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
