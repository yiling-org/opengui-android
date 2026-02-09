package top.yling.ozx.guiagent

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import top.yling.ozx.guiagent.databinding.ActivityFollowUpAnswerBinding
import top.yling.ozx.guiagent.websocket.WebSocketService
import top.yling.ozx.guiagent.speech.SpeechRecognizer
import top.yling.ozx.guiagent.speech.SpeechServiceFactory

/**
 * FollowUp 问题回答 Activity
 * 用于显示问题并让用户输入答案
 */
class FollowUpAnswerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "FollowUpAnswerActivity"
        const val EXTRA_QUESTION = "question"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_MESSAGES = "messages"
        
        /**
         * 创建启动 Intent（用于 followup 问题）
         * 确保可以从后台启动，包括应用已退出的情况
         */
        fun createIntent(context: android.content.Context, question: String, taskId: String?): Intent {
            return Intent(context, FollowUpAnswerActivity::class.java).apply {
                putExtra(EXTRA_QUESTION, question)
                putExtra(EXTRA_TASK_ID, taskId)
                // 确保可以从后台启动
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
        }
        
        /**
         * 创建启动 Intent（用于 chat 消息）
         * 确保可以从后台启动，包括应用已退出的情况
         */
        fun createChatIntent(context: android.content.Context, messages: String): Intent {
            return Intent(context, FollowUpAnswerActivity::class.java).apply {
                putExtra(EXTRA_MESSAGES, messages)
                // 确保可以从后台启动
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
        }
        
        /**
         * 启动 FollowUpAnswerActivity（用于 followup 问题）
         * 便捷方法，内部调用 createIntent 并启动 Activity
         */
        fun start(context: android.content.Context, question: String, taskId: String?) {
            val intent = createIntent(context, question, taskId)
            context.startActivity(intent)
        }
        
    }
    
    private lateinit var binding: ActivityFollowUpAnswerBinding
    private var taskId: String? = null
    
    // 语音识别服务（使用抽象接口）
    private lateinit var speechRecognizer: SpeechRecognizer
    
    // 当前识别结果（用于 stopListening 后获取）
    private var currentRecognitionResult = ""
    private var pendingStopCallback: ((String) -> Unit)? = null
    
    // 输入模式管理
    private var currentInputMode = InputMode.VOICE
    
    // 录音状态
    private var isRecording = false
    
    // 语音输入手势检测
    private var voiceTouchStartY = 0f
    private var voiceTouchStartX = 0f
    private var isOutsideVoiceArea = false
    private val SWIPE_UP_THRESHOLD_DP = 80f  // 上滑切换到文字模式的阈值
    
    // 上滑切换提示箭头动画
    private var swipeHintAnimation: android.view.animation.Animation? = null
    
    // 语音发光脉冲动画
    private var voiceGlowPulseAnimator: ObjectAnimator? = null
    
    // 网络状态缓存
    private var lastNetworkCheckTime = 0L
    private var lastNetworkState = false
    
    /**
     * 设置底部弹窗窗口样式
     * 窗口高度为屏幕的1/2，从底部向上弹出
     */
    private fun setupBottomSheetWindow() {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val windowHeight = (screenHeight * 0.5).toInt() // 屏幕高度的1/2
        
        val params = window.attributes
        params.height = windowHeight
        params.width = WindowManager.LayoutParams.MATCH_PARENT
        params.gravity = android.view.Gravity.BOTTOM
        window.attributes = params
        
        // 设置窗口背景为半透明黑色，用于遮罩效果
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }
    
    /**
     * 应用从底部向上滑入的动画
     */
    private fun applySlideUpAnimation() {
        // 确保在 View 完全布局后再执行动画
        binding.root.post {
            // 由于窗口已经定位在底部，内容需要从窗口底部外（屏幕底部外）滑入
            // 窗口高度就是需要滑动的距离
            val windowHeight = window.attributes.height
            binding.root.translationY = windowHeight.toFloat()
            
            // 应用动画：从底部滑入到最终位置
            binding.root.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }
    
    /**
     * 设置窗口插入处理，确保按钮不被系统导航栏遮挡
     */
    private fun setupWindowInsets() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            binding.root.setOnApplyWindowInsetsListener { view, insets ->
                val systemBars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                val navigationBarHeight = systemBars.bottom
                
                // 为输入容器添加底部边距，避免被系统导航栏遮挡
                val inputContainer = binding.unifiedInputContainer
                val layoutParams = inputContainer.layoutParams as android.view.ViewGroup.MarginLayoutParams
                layoutParams.bottomMargin = navigationBarHeight + resources.getDimensionPixelSize(
                    top.yling.ozx.guiagent.R.dimen.spacing_large
                )
                inputContainer.layoutParams = layoutParams
                
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            val navigationBarHeight = getNavigationBarHeight()
            val inputContainer = binding.unifiedInputContainer
            val layoutParams = inputContainer.layoutParams as android.view.ViewGroup.MarginLayoutParams
            layoutParams.bottomMargin = navigationBarHeight + resources.getDimensionPixelSize(
                top.yling.ozx.guiagent.R.dimen.spacing_large
            )
            inputContainer.layoutParams = layoutParams
        }
    }
    
    /**
     * 获取系统导航栏高度（兼容旧版本）
     */
    @Suppress("DEPRECATION")
    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            // 默认值，大多数设备约为 48dp
            (48 * resources.displayMetrics.density).toInt()
        }
    }
    
    /**
     * 设置键盘监听，确保整个弹框向上移动，不被键盘遮挡
     */
    private fun setupKeyboardListener() {
        var lastKeyboardHeight = 0
        var originalY = 0
        var isAnimating = false
        
        binding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            binding.root.getWindowVisibleDisplayFrame(rect)
            val screenHeight = binding.root.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            
            // 如果键盘高度大于屏幕的15%，认为键盘已显示
            val isKeyboardVisible = keypadHeight > screenHeight * 0.15
            
            if (isKeyboardVisible && keypadHeight != lastKeyboardHeight && !isAnimating) {
                isAnimating = true
                // 键盘显示时，将整个窗口向上移动
                val params = window.attributes
                
                // 记录原始 Y 位置（如果还没记录）
                if (originalY == 0) {
                    originalY = params.y
                }
                
                // 计算需要向上移动的距离：键盘高度 + 一些边距
                val moveUpDistance = keypadHeight + resources.getDimensionPixelSize(
                    top.yling.ozx.guiagent.R.dimen.spacing_medium
                )
                
                // 计算新的 Y 位置：原始位置 - 移动距离
                // 由于窗口 gravity 是 BOTTOM，y 是相对于底部的偏移
                // 负值表示向上移动
                val newY = -moveUpDistance
                
                // 确保不会移动得太高（至少保留屏幕的1/3可见）
                val maxMoveUp = (screenHeight * 0.33).toInt()
                val finalY = maxOf(newY, -maxMoveUp)
                
                // 使用动画平滑移动窗口
                val startY = params.y.toFloat()
                val endY = finalY.toFloat()
                
                if (kotlin.math.abs(startY - endY) > 1) {
                    val animator = android.animation.ValueAnimator.ofFloat(startY, endY).apply {
                        duration = 200
                        interpolator = AccelerateDecelerateInterpolator()
                        addUpdateListener { animation ->
                            val currentY = (animation.animatedValue as Float).toInt()
                            val currentParams = window.attributes
                            currentParams.y = currentY
                            window.attributes = currentParams
                        }
                        addListener(object : android.animation.Animator.AnimatorListener {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                isAnimating = false
                            }
                            override fun onAnimationStart(animation: android.animation.Animator) {}
                            override fun onAnimationCancel(animation: android.animation.Animator) {
                                isAnimating = false
                            }
                            override fun onAnimationRepeat(animation: android.animation.Animator) {}
                        })
                    }
                    animator.start()
                } else {
                    isAnimating = false
                }
                
                lastKeyboardHeight = keypadHeight
            } else if (!isKeyboardVisible && lastKeyboardHeight > 0 && !isAnimating) {
                isAnimating = true
                // 键盘隐藏时，恢复原始位置
                val params = window.attributes
                val startY = params.y.toFloat()
                val endY = originalY.toFloat()
                
                if (kotlin.math.abs(startY - endY) > 1) {
                    val animator = android.animation.ValueAnimator.ofFloat(startY, endY).apply {
                        duration = 200
                        interpolator = AccelerateDecelerateInterpolator()
                        addUpdateListener { animation ->
                            val currentY = (animation.animatedValue as Float).toInt()
                            val currentParams = window.attributes
                            currentParams.y = currentY
                            window.attributes = currentParams
                        }
                        addListener(object : android.animation.Animator.AnimatorListener {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                isAnimating = false
                                lastKeyboardHeight = 0
                                originalY = 0
                            }
                            override fun onAnimationStart(animation: android.animation.Animator) {}
                            override fun onAnimationCancel(animation: android.animation.Animator) {
                                isAnimating = false
                            }
                            override fun onAnimationRepeat(animation: android.animation.Animator) {}
                        })
                    }
                    animator.start()
                } else {
                    isAnimating = false
                    lastKeyboardHeight = 0
                    originalY = 0
                }
            }
        }
    }
    
    // 权限请求回调
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d(TAG, "用户授予了录音权限")
            Toast.makeText(this, "录音权限已授予，可以开始使用", Toast.LENGTH_SHORT).show()
        } else {
            android.util.Log.e(TAG, "用户拒绝了录音权限")
            Toast.makeText(this, "需要录音权限才能使用语音输入功能", Toast.LENGTH_LONG).show()
        }
    }
    
    // 输入模式枚举
    enum class InputMode {
        VOICE,  // 语音输入模式
        TEXT    // 文字输入模式
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 启用边到边显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // 设置窗口为底部弹窗样式：高度为屏幕的2/3
        setupBottomSheetWindow()
        
        binding = ActivityFollowUpAnswerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 处理系统窗口插入，确保按钮不被系统组件遮挡
        setupWindowInsets()
        
        // 监听键盘显示/隐藏，确保按钮不被键盘遮挡
        setupKeyboardListener()
        
        // 设置窗口标志，确保可以在锁屏上显示，即使应用在后台
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 确保 Activity 可以显示在其他应用之上（从通知启动时）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        // 隐藏标题栏
        supportActionBar?.hide()
        
        // 初始化语音识别服务（使用抽象接口）
        speechRecognizer = SpeechServiceFactory.createSpeechRecognizer(this)
        
        // 获取传递的数据
        val question = intent.getStringExtra(EXTRA_QUESTION)
        val messages = intent.getStringExtra(EXTRA_MESSAGES)
        taskId = intent.getStringExtra(EXTRA_TASK_ID)
        
        // 判断是哪种模式
        val isChatMode = messages != null && messages.isNotEmpty()
        
        if (isChatMode) {
            // Chat 消息模式：只显示消息，隐藏输入框和发送按钮
            setupChatMode(messages!!)
        } else {
            // Followup 问题模式：显示问题，需要输入答案
            if (question == null || question.isEmpty()) {
                android.util.Log.e(TAG, "问题为空，关闭 Activity")
                finish()
                return
            }
            setupFollowUpMode(question)
        }
        
        // 初始化输入模式 UI
        updateInputModeUI()
    }
    
    /**
     * 设置 Followup 问题模式
     */
    private fun setupFollowUpMode(question: String) {
        // 显示问题
        binding.questionText.text = question
        
        // 更新标题和副标题
        binding.titleText.text = getString(R.string.app_name_question)
        binding.subtitleText.text = "回复后继续执行任务"
        
        // 显示输入框和发送按钮（已注释，使用文字输入模式中的发送按钮）
        binding.answerInputCard.visibility = android.view.View.VISIBLE
        // binding.sendButton.visibility = android.view.View.VISIBLE
        
        // 显示取消按钮（已注释，不再显示）
        // binding.cancelButton.visibility = android.view.View.VISIBLE
        // binding.cancelButtonText.text = "取消"
        // binding.cancelButton.setCardBackgroundColor(getColor(R.color.card_background))
        // binding.cancelButtonText.setTextColor(getColor(R.color.text_secondary))
        // binding.cancelButtonText.setTypeface(null, android.graphics.Typeface.NORMAL)
        
        // 立即尝试绑定服务（不等待 onStart）
        bindWebSocketService()
        
        // 设置语音输入触摸监听
        setupVoiceInputTouchListener()
        
        // 文字模式：麦克风切换按钮点击事件
        binding.textModeVoiceButton.setOnClickListener {
            performHapticFeedback()
            toggleInputMode()
        }
        
        // 文字模式：发送按钮点击事件
        binding.textSendButton.setOnClickListener {
            performHapticFeedback()
            sendAnswer()
        }
        
        // 发送按钮点击事件（已注释，使用文字输入模式中的发送按钮）
        // binding.sendButton.setOnClickListener {
        //     sendAnswer()
        // }
        
        // 取消按钮点击事件（已注释，不再使用）
        // binding.cancelButton.setOnClickListener {
        //     finish()
        // }
        
        // 输入框回车键监听
        binding.answerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendAnswer()
                true
            } else {
                false
            }
        }
        
        // 根据输入模式决定是否自动聚焦
        if (currentInputMode == InputMode.TEXT) {
            binding.answerInput.post {
                binding.answerInput.requestFocus()
                showKeyboard(binding.answerInput)
            }
        }
    }
    
    /**
     * 设置 Chat 消息模式
     */
    private fun setupChatMode(messages: String) {
        // 显示消息
        binding.questionText.text = messages
        
        // 更新标题和副标题
        binding.titleText.text = getString(R.string.app_name_message)
        binding.subtitleText.text = "回复后继续对话"
        
        // 显示输入框和发送按钮（已注释，使用文字输入模式中的发送按钮）
        binding.answerInputCard.visibility = android.view.View.VISIBLE
        // binding.sendButton.visibility = android.view.View.VISIBLE
        
        // 显示取消按钮（已注释，不再显示）
        // binding.cancelButton.visibility = android.view.View.VISIBLE
        // binding.cancelButtonText.text = "取消"
        // binding.cancelButton.setCardBackgroundColor(getColor(R.color.card_background))
        // binding.cancelButtonText.setTextColor(getColor(R.color.text_secondary))
        // binding.cancelButtonText.setTypeface(null, android.graphics.Typeface.NORMAL)
        
        // 更新输入框提示
        binding.answerInput.hint = "请输入您的回复..."
        
        // 立即尝试绑定服务（不等待 onStart）
        bindWebSocketService()
        
        // 设置语音输入触摸监听
        setupVoiceInputTouchListener()
        
        // 文字模式：麦克风切换按钮点击事件
        binding.textModeVoiceButton.setOnClickListener {
            performHapticFeedback()
            toggleInputMode()
        }
        
        // 文字模式：发送按钮点击事件
        binding.textSendButton.setOnClickListener {
            performHapticFeedback()
            sendAnswer()
        }
        
        // 发送按钮点击事件（已注释，使用文字输入模式中的发送按钮）
        // binding.sendButton.setOnClickListener {
        //     sendAnswer()
        // }
        
        // 取消按钮点击事件（已注释，不再使用）
        // binding.cancelButton.setOnClickListener {
        //     finish()
        // }
        
        // 输入框回车键监听
        binding.answerInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendAnswer()
                true
            } else {
                false
            }
        }
        
        // 根据输入模式决定是否自动聚焦
        if (currentInputMode == InputMode.TEXT) {
            binding.answerInput.post {
                binding.answerInput.requestFocus()
                showKeyboard(binding.answerInput)
            }
        }
    }
    
    
    private var webSocketService: WebSocketService? = null
    private var serviceBound = false
    
    override fun onStart() {
        super.onStart()
        // 绑定 WebSocket 服务（无论哪种模式都需要）
        bindWebSocketService()
        // 在 Activity 可见时应用从底部向上滑入的动画
        applySlideUpAnimation()
    }
    
    /**
     * 绑定 WebSocket 服务
     */
    private fun bindWebSocketService() {
        if (WebSocketService.isRunning && !serviceBound) {
            val intent = Intent(this, WebSocketService::class.java)
            val bound = bindService(intent, serviceConnection, android.content.Context.BIND_AUTO_CREATE)
            if (!bound) {
                android.util.Log.w(TAG, "绑定 WebSocket 服务失败")
                // 如果绑定失败，提示用户
                Toast.makeText(this, "无法连接到服务，请确保应用正在运行", Toast.LENGTH_LONG).show()
            }
        } else if (!WebSocketService.isRunning) {
            android.util.Log.w(TAG, "WebSocket 服务未运行")
            Toast.makeText(this, "WebSocket 服务未运行，请重新启动应用", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onStop() {
        super.onStop()
        // 解绑服务
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            webSocketService = null
        }
    }
    
    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
            val localBinder = binder as? WebSocketService.LocalBinder
            webSocketService = localBinder?.getService()
            serviceBound = true
            android.util.Log.d(TAG, "WebSocket 服务已绑定")
        }
        
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            serviceBound = false
            webSocketService = null
            android.util.Log.d(TAG, "WebSocket 服务已断开")
        }
    }
    
    /**
     * 发送答案
     * @param text 可选的文本内容，如果为 null 则从输入框获取
     */
    private fun sendAnswer(text: String? = null) {
        val answer = text?.trim() ?: binding.answerInput.text.toString().trim()
        
        if (answer.isEmpty()) {
            Toast.makeText(this, "答案不能为空", Toast.LENGTH_SHORT).show()
            // 添加抖动动画提示
            val targetView = if (currentInputMode == InputMode.TEXT) {
                binding.textInputCard
            } else {
                binding.voiceInputCard
            }
            targetView.animate()
                .translationX(-10f)
                .setDuration(50)
                .withEndAction {
                    targetView.animate()
                        .translationX(10f)
                        .setDuration(50)
                        .withEndAction {
                            targetView.animate()
                                .translationX(0f)
                                .setDuration(50)
                                .start()
                        }
                        .start()
                }
                .start()
            return
        }
        
        val isChatMode = intent.getStringExtra(EXTRA_MESSAGES) != null
        android.util.Log.d(TAG, "发送${if (isChatMode) "chat" else "followup"}消息: $answer, taskId: $taskId")
        
        // 检查 WebSocket 服务是否可用
        val service = webSocketService
        if (service == null || !service.isConnected()) {
            Toast.makeText(this, "WebSocket 未连接", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 禁用发送按钮，防止重复发送（已注释，使用文字输入模式中的发送按钮）
        // binding.sendButton.isEnabled = false
        // binding.sendButtonText.text = "发送中..."
        
        // 对于 chat 模式，如果没有 taskId，尝试获取当前任务的 taskId
        val finalTaskId = if (isChatMode && taskId == null) {
            top.yling.ozx.guiagent.task.TaskManager.getCurrentTaskId()
        } else {
            taskId
        }
        
        // 发送答案
        service.sendToAgent(
            text = answer,
            taskId = finalTaskId,
            onSuccess = {
                android.util.Log.d(TAG, "${if (isChatMode) "chat" else "followup"} 消息已发送")
                Toast.makeText(this, "消息已发送", Toast.LENGTH_SHORT).show()
                // 延迟关闭，让用户看到成功提示
                binding.root.postDelayed({
                    finish()
                }, 500)
            },
            onError = { error ->
                android.util.Log.e(TAG, "发送${if (isChatMode) "chat" else "followup"} 消息失败: $error")
                Toast.makeText(this, "发送失败: $error", Toast.LENGTH_SHORT).show()
                // 恢复发送按钮（已注释，使用文字输入模式中的发送按钮）
                // binding.sendButton.isEnabled = true
                // binding.sendButtonText.text = "发送"
            }
        )
    }
    
    /**
     * 显示软键盘
     */
    private fun showKeyboard(view: android.view.View) {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
    
    /**
     * 隐藏软键盘
     */
    private fun hideKeyboard() {
        val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }
    
    override fun onBackPressed() {
        // 允许返回键关闭，使用向下滑动退出动画
        finish()
    }
    
    override fun finish() {
        // 应用向下滑动退出动画
        val windowHeight = window.attributes.height
        binding.root.animate()
            .translationY(windowHeight.toFloat())
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                super.finish()
                overridePendingTransition(0, 0) // 禁用系统动画，因为我们已经用了自定义动画
            }
            .start()
    }
    
    override fun onPause() {
        super.onPause()
        // 停止波形动画
        binding.waveformView.stopAnimation()
        // 如果正在录音，停止录音
        if (isRecording) {
            cancelRecording()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            speechRecognizer.cancel()
        }
        speechRecognizer.release()
        // 停止动画
        voiceGlowPulseAnimator?.cancel()
        swipeHintAnimation?.cancel()
    }
    
    /**
     * 设置语音输入卡片的触摸监听器
     */
    private fun setupVoiceInputTouchListener() {
        val swipeUpThresholdPx = SWIPE_UP_THRESHOLD_DP * resources.displayMetrics.density

        binding.voiceInputCard.setOnTouchListener { view, event ->
            if (currentInputMode != InputMode.VOICE) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录触摸起始位置
                    voiceTouchStartY = event.rawY
                    voiceTouchStartX = event.rawX
                    isOutsideVoiceArea = false

                    // 触觉反馈
                    performHapticFeedback()

                    // 开始录音
                    if (hasRecordPermission()) {
                        startRecording()
                        // 按下动画
                        animateVoiceCardPressed(true)
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isRecording) {
                        return@setOnTouchListener true
                    }

                    val deltaY = voiceTouchStartY - event.rawY  // 正值表示上滑

                    // 检查是否上滑超过阈值（切换到文字模式）
                    if (deltaY > swipeUpThresholdPx) {
                        // 取消当前录音
                        cancelRecording()
                        // 切换到文字模式
                        toggleInputMode()
                        return@setOnTouchListener true
                    }

                    // 检查是否滑出区域（取消录音）
                    val location = IntArray(2)
                    view.getLocationOnScreen(location)
                    val viewLeft = location[0]
                    val viewTop = location[1]
                    val viewRight = viewLeft + view.width
                    val viewBottom = viewTop + view.height

                    val isInside = event.rawX >= viewLeft - 50 &&
                            event.rawX <= viewRight + 50 &&
                            event.rawY >= viewTop - 100 &&
                            event.rawY <= viewBottom + 50

                    if (!isInside && !isOutsideVoiceArea) {
                        // 刚刚滑出区域
                        isOutsideVoiceArea = true
                        showCancelHint(true)
                        performHapticFeedback()
                    } else if (isInside && isOutsideVoiceArea) {
                        // 滑回区域内
                        isOutsideVoiceArea = false
                        showCancelHint(false)
                        performHapticFeedback()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    // 恢复按钮状态
                    animateVoiceCardPressed(false)
                    showCancelHint(false)

                    if (!isRecording) {
                        return@setOnTouchListener true
                    }

                    if (isOutsideVoiceArea) {
                        // 滑出区域松手 = 取消录音
                        cancelRecording()
                        Toast.makeText(this, "已取消", Toast.LENGTH_SHORT).show()
                    } else {
                        // 正常松手 = 停止录音并识别
                        stopRecording()
                    }
                    isOutsideVoiceArea = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    // 被系统取消
                    animateVoiceCardPressed(false)
                    showCancelHint(false)
                    if (isRecording) {
                        cancelRecording()
                    }
                    isOutsideVoiceArea = false
                    true
                }

                else -> false
            }
        }
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
     * 显示/隐藏取消录音提示
     */
    private fun showCancelHint(show: Boolean) {
        if (show) {
            binding.cancelHintText.visibility = View.VISIBLE
            binding.cancelHintText.animate()
                .alpha(1f)
                .setDuration(150)
                .start()

            // 语音卡片变红色调
            binding.voiceInputContent.animate()
                .alpha(0.7f)
                .setDuration(150)
                .start()
        } else {
            binding.cancelHintText.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    binding.cancelHintText.visibility = View.GONE
                }
                .start()

            // 恢复正常
            binding.voiceInputContent.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        }
    }
    
    /**
     * 语音卡片按下/松开动画
     */
    private fun animateVoiceCardPressed(pressed: Boolean) {
        val scale = if (pressed) 0.96f else 1f
        val glowAlpha = if (pressed) 0.8f else 0f

        binding.voiceInputCard.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 发光层动画
        binding.inputGlowLayer.animate()
            .alpha(glowAlpha)
            .setDuration(200)
            .start()

        // 录音时启动发光层脉冲动画
        if (pressed) {
            startVoiceGlowPulseAnimation()
        } else {
            stopVoiceGlowPulseAnimation()
        }
    }
    
    /**
     * 语音录音时的发光脉冲动画
     */
    private fun startVoiceGlowPulseAnimation() {
        voiceGlowPulseAnimator?.cancel()
        voiceGlowPulseAnimator = ObjectAnimator.ofFloat(binding.inputGlowLayer, "alpha", 0.5f, 0.9f, 0.5f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopVoiceGlowPulseAnimation() {
        voiceGlowPulseAnimator?.cancel()
        voiceGlowPulseAnimator = null
        binding.inputGlowLayer.alpha = 0f
    }
    
    /**
     * 开始录音
     */
    private fun startRecording() {
        android.util.Log.d(TAG, "开始录音")
        
        // 再次确认权限
        if (!hasRecordPermission()) {
            android.util.Log.e(TAG, "尝试录音但没有权限")
            Toast.makeText(this, "没有录音权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查网络连接（讯飞 SDK 需要网络）
        if (!isNetworkAvailable()) {
            android.util.Log.e(TAG, "网络不可用，无法使用语音识别")
            Toast.makeText(this, "请先连接网络！\n语音识别需要网络连接", Toast.LENGTH_LONG).show()
            return
        }
        
        isRecording = true

        // 隐藏提示文本，显示波形
        binding.inputHintText.visibility = View.GONE
        binding.waveformView.visibility = View.VISIBLE

        // 启动波形动画
        binding.waveformView.startAnimation()
        
        // 清空之前的识别结果
        currentRecognitionResult = ""
        
        // 开始语音识别（使用抽象接口）
        try {
            android.util.Log.d(TAG, "启动语音识别 (${speechRecognizer.getName()})...")
            speechRecognizer.startListening(object : SpeechRecognizer.Callback {
                override fun onResult(result: SpeechRecognizer.Result) {
                    android.util.Log.d(TAG, "实时识别: ${result.text}, isFinal=${result.isFinal}")
                    currentRecognitionResult = result.text
                    
                    // 如果是最终结果且有等待的回调，执行回调
                    if (result.isFinal) {
                        pendingStopCallback?.let { callback ->
                            pendingStopCallback = null
                            runOnUiThread {
                                callback(result.text)
                            }
                        }
                    }
                }

                override fun onError(code: Int, message: String) {
                    android.util.Log.e(TAG, "识别错误: $message")
                    runOnUiThread {
                        Toast.makeText(this@FollowUpAnswerActivity, "语音识别错误: $message", Toast.LENGTH_LONG).show()
                        isRecording = false
                        binding.inputHintText.visibility = View.VISIBLE
                        binding.waveformView.visibility = View.GONE
                        binding.waveformView.stopAnimation()
                    }
                }
            })
            android.util.Log.d(TAG, "语音识别已启动")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "识别启动异常: ${e.message}", e)
            Toast.makeText(this, "语音识别启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            isRecording = false
            binding.inputHintText.visibility = View.VISIBLE
            binding.waveformView.visibility = View.GONE
            binding.waveformView.stopAnimation()
            return
        }
    }
    
    /**
     * 停止录音
     */
    private fun stopRecording() {
        isRecording = false

        // 停止波形动画
        binding.waveformView.stopAnimation()
        binding.waveformView.visibility = View.GONE
        binding.inputHintText.visibility = View.VISIBLE

        // 停止语音卡片动画
        animateVoiceCardPressed(false)
        stopVoiceGlowPulseAnimation()
        
        // 设置停止后的回调
        pendingStopCallback = { finalText ->
            android.util.Log.d(TAG, "识别结果回调被调用: '$finalText', 长度: ${finalText.length}")
            
            if (finalText.isNotEmpty()) {
                // 直接发送识别结果，不填入输入框
                sendAnswer(finalText)
            } else {
                // 识别失败
                Toast.makeText(this, "未识别到语音，请说话时间长一点或靠近麦克风", Toast.LENGTH_LONG).show()
            }
        }
        
        // 停止语音识别，结果会通过 onResult(isFinal=true) 回调
        speechRecognizer.stopListening()
    }
    
    /**
     * 取消录音（不保存）
     */
    private fun cancelRecording() {
        if (!isRecording) return

        isRecording = false

        // 停止波形动画
        binding.waveformView.stopAnimation()
        binding.waveformView.visibility = View.GONE
        binding.inputHintText.visibility = View.VISIBLE

        // 停止动画
        stopVoiceGlowPulseAnimation()
        animateVoiceCardPressed(false)

        // 取消语音识别（不需要结果）
        pendingStopCallback = null
        speechRecognizer.cancel()
    }
    
    /**
     * 切换输入模式（语音/文字）
     */
    private fun toggleInputMode() {
        // 切换模式
        currentInputMode = if (currentInputMode == InputMode.VOICE) {
            InputMode.TEXT
        } else {
            InputMode.VOICE
        }
        
        // 更新 UI
        updateInputModeUI()

        android.util.Log.d(TAG, "切换到${if (currentInputMode == InputMode.VOICE) "语音" else "文字"}输入模式")
    }
    
    /**
     * 更新输入模式 UI
     */
    private fun updateInputModeUI() {
        // 重置取消提示状态
        binding.cancelHintText.visibility = View.GONE
        binding.cancelHintText.alpha = 0f
        isOutsideVoiceArea = false

        when (currentInputMode) {
            InputMode.VOICE -> {
                // 切换到语音模式

                // 显示语音输入卡片，隐藏文字输入卡片
                binding.voiceInputCard.visibility = View.VISIBLE
                binding.textInputCard.visibility = View.GONE

                // 确保语音模式UI状态正确
                binding.inputHintText.visibility = View.VISIBLE
                binding.inputHintText.text = "按住说话"
                binding.waveformView.visibility = View.GONE

                // 隐藏软键盘
                hideKeyboard()

                // 重置发光层
                binding.inputGlowLayer.alpha = 0f

                // 启动上滑切换提示的呼吸动画
                startSwipeHintAnimation()

                android.util.Log.d(TAG, "已切换到语音模式")
            }
            InputMode.TEXT -> {
                // 停止上滑提示动画
                stopSwipeHintAnimation()
                // 切换到文字模式

                // 隐藏语音输入卡片，显示文字输入卡片
                binding.voiceInputCard.visibility = View.GONE
                binding.textInputCard.visibility = View.VISIBLE

                // 停止录音相关动画
                stopVoiceGlowPulseAnimation()

                // 自动聚焦到输入框
                binding.answerInput.postDelayed({
                    binding.answerInput.requestFocus()
                    showKeyboard(binding.answerInput)
                }, 200)

                android.util.Log.d(TAG, "已切换到文字模式")
            }
        }
    }
    
    /**
     * 启动上滑切换提示的呼吸动画
     */
    private fun startSwipeHintAnimation() {
        try {
            swipeHintAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.bounce_hint)
            binding.voiceSwipeArrow.startAnimation(swipeHintAnimation)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "启动上滑提示动画失败: ${e.message}")
        }
    }

    /**
     * 停止上滑切换提示的呼吸动画
     */
    private fun stopSwipeHintAnimation() {
        swipeHintAnimation?.cancel()
        swipeHintAnimation = null
        binding.voiceSwipeArrow.clearAnimation()
    }
    
    /**
     * 检查是否有录音权限
     */
    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查网络是否可用
     */
    private fun isNetworkAvailable(): Boolean {
        // 缓存网络状态，1秒内不重复检查
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
}

