package top.yling.ozx.guiagent.intervention

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import top.yling.ozx.guiagent.R
import top.yling.ozx.guiagent.websocket.WebSocketService

/**
 * 用户介入悬浮窗管理器
 * 负责显示不干扰用户操作的悬浮提示，包括倒计时和确认按钮
 *
 * 设计要点：
 * 1. 悬浮窗显示在屏幕顶部，不遮挡主要操作区域
 * 2. 半透明背景，用户可以看到下方内容
 * 3. 提供"我已完成"按钮，用户可以提前确认
 * 4. 显示倒计时，让用户知道剩余时间
 * 5. 悬浮窗可拖动（可选）
 */
class InterventionOverlayManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "InterventionOverlay"

        @Volatile
        private var instance: InterventionOverlayManager? = null

        fun getInstance(context: Context): InterventionOverlayManager {
            return instance ?: synchronized(this) {
                instance ?: InterventionOverlayManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val mainHandler = Handler(Looper.getMainLooper())

    // 当前显示的悬浮窗
    private var currentOverlayView: View? = null
    private var currentInterventionId: String? = null
    private var countDownTimer: CountDownTimer? = null

    // 确认回调
    private var confirmCallback: ((String) -> Unit)? = null

    /**
     * 显示用户介入提示悬浮窗
     *
     * @param interventionId 介入请求 ID
     * @param sceneType 场景类型
     * @param description 描述信息
     * @param timeoutSeconds 超时时间（秒）
     * @param onConfirm 用户点击确认时的回调
     */
    fun showIntervention(
        interventionId: String,
        sceneType: String,
        description: String,
        timeoutSeconds: Int,
        onConfirm: (String) -> Unit
    ) {
        mainHandler.post {
            // 如果已有悬浮窗，先同步移除（不能调用 hideIntervention()，因为它也用 mainHandler.post 会导致异步执行）
            removeCurrentOverlaySync()

            currentInterventionId = interventionId
            confirmCallback = onConfirm

            try {
                // 创建悬浮窗视图
                val overlayView = createOverlayView(sceneType, description, timeoutSeconds)
                currentOverlayView = overlayView

                // 配置悬浮窗参数
                val layoutParams = createLayoutParams()

                // 添加到窗口
                windowManager.addView(overlayView, layoutParams)

                // 启动倒计时
                startCountDown(timeoutSeconds)

                Log.i(TAG, "显示用户介入提示: id=$interventionId, scene=$sceneType, timeout=${timeoutSeconds}s")

            } catch (e: Exception) {
                Log.e(TAG, "显示悬浮窗失败", e)
                currentOverlayView = null
                currentInterventionId = null
            }
        }
    }

    /**
     * 同步移除当前悬浮窗（仅在主线程内部调用）
     * 用于 showIntervention 内部清理旧悬浮窗，避免异步 post 导致的时序问题
     */
    private fun removeCurrentOverlaySync() {
        // 停止倒计时
        countDownTimer?.cancel()
        countDownTimer = null

        // 移除悬浮窗
        currentOverlayView?.let { view ->
            try {
                windowManager.removeView(view)
                Log.i(TAG, "同步移除旧悬浮窗: id=$currentInterventionId")
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮窗失败", e)
            }
        }

        currentOverlayView = null
        // 注意：不清空 currentInterventionId 和 confirmCallback，因为马上会设置新值
    }

    /**
     * 隐藏用户介入提示
     */
    fun hideIntervention(interventionId: String? = null) {
        mainHandler.post {
            // 如果指定了 ID，检查是否匹配
            if (interventionId != null && currentInterventionId != interventionId) {
                Log.w(TAG, "interventionId 不匹配，忽略隐藏请求: current=$currentInterventionId, request=$interventionId")
                return@post
            }

            // 停止倒计时
            countDownTimer?.cancel()
            countDownTimer = null

            // 移除悬浮窗
            currentOverlayView?.let { view ->
                try {
                    windowManager.removeView(view)
                    Log.i(TAG, "隐藏用户介入提示: id=$currentInterventionId")
                } catch (e: Exception) {
                    Log.e(TAG, "移除悬浮窗失败", e)
                }
            }

            currentOverlayView = null
            currentInterventionId = null
            confirmCallback = null
        }
    }

    /**
     * 用户点击确认按钮
     */
    private fun onUserConfirm() {
        val interventionId = currentInterventionId ?: return

        Log.i(TAG, "用户点击确认: id=$interventionId")

        // 发送确认消息到服务端
        sendConfirmationToServer(interventionId)

        // 回调通知（本地回调，可选）
        confirmCallback?.invoke(interventionId)

        // 隐藏悬浮窗
        hideIntervention()
    }

    /**
     * 发送用户确认消息到服务端
     */
    private fun sendConfirmationToServer(interventionId: String) {
        val message = """
            {
                "type": "intervention_confirmed",
                "interventionId": "$interventionId",
                "timestamp": ${System.currentTimeMillis()}
            }
        """.trimIndent()

        val sent = WebSocketService.instance?.webSocketClient?.sendMessage(message) ?: false
        Log.i(TAG, "发送确认消息到服务端: id=$interventionId, sent=$sent")
    }

    /**
     * 创建悬浮窗视图
     */
    private fun createOverlayView(
        sceneType: String,
        description: String,
        timeoutSeconds: Int
    ): View {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.overlay_intervention, null)

        // 设置场景图标和标题
        val titleTextView = view.findViewById<TextView>(R.id.tv_intervention_title)
        titleTextView.text = getSceneTitle(sceneType)

        // 设置描述信息
        val descTextView = view.findViewById<TextView>(R.id.tv_intervention_desc)
        descTextView.text = description

        // 设置倒计时
        val countdownTextView = view.findViewById<TextView>(R.id.tv_countdown)
        countdownTextView.text = formatCountdown(timeoutSeconds)

        // 设置确认按钮
        val confirmButton = view.findViewById<Button>(R.id.btn_confirm)
        confirmButton.setOnClickListener {
            onUserConfirm()
        }

        return view
    }

    /**
     * 创建悬浮窗布局参数
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // 关键：不拦截触摸事件（除了悬浮窗本身的区域）
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // 顶部留出状态栏的空间
            y = getStatusBarHeight()
        }
    }

    /**
     * 启动倒计时
     */
    private fun startCountDown(timeoutSeconds: Int) {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(timeoutSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = (millisUntilFinished / 1000).toInt()
                updateCountdown(secondsRemaining)
            }

            override fun onFinish() {
                updateCountdown(0)
                // 超时后不自动隐藏，等待服务端发送 hide_intervention
            }
        }.start()
    }

    /**
     * 更新倒计时显示
     */
    private fun updateCountdown(secondsRemaining: Int) {
        mainHandler.post {
            currentOverlayView?.let { view ->
                val countdownTextView = view.findViewById<TextView>(R.id.tv_countdown)
                countdownTextView?.text = formatCountdown(secondsRemaining)
            }
        }
    }

    /**
     * 格式化倒计时显示
     */
    private fun formatCountdown(seconds: Int): String {
        return if (seconds > 0) {
            "${seconds}s"
        } else {
            "等待中..."
        }
    }

    /**
     * 获取场景标题
     */
    private fun getSceneTitle(sceneType: String): String {
        return when (sceneType) {
            "login_input" -> "请完成登录"
            "captcha_slide" -> "请完成滑动验证"
            "captcha_sms" -> "请输入验证码"
            "payment_confirm" -> "请确认支付"
            else -> "请完成操作"
        }
    }

    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = context.resources.getDimensionPixelSize(resourceId)
        }
        return result
    }

    /**
     * 检查是否有悬浮窗权限
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
}
