package top.yling.ozx.guiagent.app.activity.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import top.yling.ozx.guiagent.AdvancedSettingsActivity
import top.yling.ozx.guiagent.app.activity.InputMode
import top.yling.ozx.guiagent.app.activity.RecordingState
import top.yling.ozx.guiagent.app.activity.TaskStatus
import top.yling.ozx.guiagent.databinding.ActivityMainBinding
import top.yling.ozx.guiagent.util.IFlytekConfigProvider

/**
 * 输入控制器
 * 负责管理语音输入和文字输入的切换、状态管理
 * @author shanwb
 */
class InputController(
    private val activity: Activity,
    private val binding: ActivityMainBinding,
    private val animationManager: AnimationManager,
    private val getCurrentTaskStatus: () -> TaskStatus,
    private val hasRecordPermission: () -> Boolean,
    private val requestRecordPermission: () -> Unit,
    private val startRecording: () -> Unit,
    private val cancelRecording: () -> Unit,
    private val stopRecording: () -> Unit,
    private val sendToBackend: (String) -> Unit,
    private val updateUIState: (RecordingState) -> Unit,
    private val announceForAccessibility: (String) -> Unit,
    private val performHapticFeedback: () -> Unit,
    private val getIsRecording: () -> Boolean
) {
    // 输入模式
    var currentInputMode = InputMode.VOICE
        private set

    // 语音输入触摸状态
    private var voiceTouchStartY = 0f
    private var voiceTouchStartX = 0f
    var isOutsideVoiceArea = false
        private set

    // 常量
    private val SWIPE_UP_THRESHOLD_DP = 80f
    private val PREFS_NAME = "MainActivity_prefs"
    private val KEY_INPUT_MODE = "input_mode"

    /**
     * 初始化输入控制器
     */
    fun initialize() {
        // 恢复输入模式
        restoreInputMode()

        // 设置语音输入触摸监听
        setupVoiceInputTouchListener()

        // 设置文字模式按钮点击事件
        setupTextModeButtons()

        // 更新 UI
        updateInputModeUI()
    }

    /**
     * 设置语音输入卡片的触摸监听器
     */
    private fun setupVoiceInputTouchListener() {
        val swipeUpThresholdPx = SWIPE_UP_THRESHOLD_DP * activity.resources.displayMetrics.density

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

                    // 检查任务状态
                    if (getCurrentTaskStatus() == TaskStatus.RUNNING) {
                        Toast.makeText(activity, "任务执行中，请稍候", Toast.LENGTH_SHORT).show()
                        return@setOnTouchListener true
                    }

                    // 检查讯飞配置
                    if (!IFlytekConfigProvider.isConfigured(activity)) {
                        showVoiceConfigGuideDialog()
                        return@setOnTouchListener true
                    }

                    // 触觉反馈
                    performHapticFeedback()

                    // 开始录音
                    if (hasRecordPermission()) {
                        startRecording()
                        // 按下动画
                        animateVoiceCardPressed(true)
                    } else {
                        requestRecordPermission()
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!getIsRecording()) {
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

                    // 扩大判定区域（上下左右各扩大 50dp）
                    val expandPx = (50 * activity.resources.displayMetrics.density).toInt()
                    val isInside = event.rawX >= viewLeft - expandPx &&
                            event.rawX <= viewRight + expandPx &&
                            event.rawY >= viewTop - expandPx &&
                            event.rawY <= viewBottom + expandPx

                    if (!isInside && !isOutsideVoiceArea) {
                        // 刚滑出区域
                        isOutsideVoiceArea = true
                        showCancelHint(true)
                    } else if (isInside && isOutsideVoiceArea) {
                        // 滑回区域
                        isOutsideVoiceArea = false
                        showCancelHint(false)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢复按下动画
                    animateVoiceCardPressed(false)

                    if (getIsRecording()) {
                        if (isOutsideVoiceArea) {
                            // 滑出区域，取消录音
                            cancelRecording()
                            showCancelHint(false)
                        } else {
                            // 正常松手，停止录音并发送
                            stopRecording()
                        }
                    }
                    isOutsideVoiceArea = false
                    true
                }

                else -> false
            }
        }
    }

    /**
     * 设置文字模式按钮点击事件
     */
    private fun setupTextModeButtons() {
        // 语音模式：键盘切换按钮点击事件（切换到文字模式）
        binding.voiceModeKeyboardButton.setOnClickListener {
            performHapticFeedback()
            toggleInputMode()
        }

        // 文字模式：麦克风切换按钮点击事件（切换到语音模式）
        binding.textModeVoiceButton.setOnClickListener {
            performHapticFeedback()
            toggleInputMode()
        }

        // 文字模式：发送按钮点击事件
        binding.textSendButton.setOnClickListener {
            performHapticFeedback()
            sendTextMessage()
        }
    }

    /**
     * 切换输入模式（语音/文字）
     */
    fun toggleInputMode() {
        // 如果任务正在执行，不允许切换
        if (getCurrentTaskStatus() == TaskStatus.RUNNING) {
            Toast.makeText(activity, "任务执行中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        // 切换模式
        currentInputMode = if (currentInputMode == InputMode.VOICE) {
            InputMode.TEXT
        } else {
            InputMode.VOICE
        }

        // 保存输入模式到 SharedPreferences
        saveInputMode()

        // 更新 UI
        updateInputModeUI()

        android.util.Log.d("InputController", "切换到${if (currentInputMode == InputMode.VOICE) "语音" else "文字"}输入模式")
    }

    /**
     * 更新输入模式 UI
     */
    fun updateInputModeUI() {
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
                animationManager.startSwipeHintAnimation()

                android.util.Log.d("InputController", "已切换到语音模式")
            }
            InputMode.TEXT -> {
                // 停止上滑提示动画
                animationManager.stopSwipeHintAnimation()
                // 切换到文字模式

                // 隐藏语音输入卡片，显示文字输入卡片
                binding.voiceInputCard.visibility = View.GONE
                binding.textInputCard.visibility = View.VISIBLE

                // 停止录音相关动画
                animationManager.stopAllRecordingAnimations()
                animationManager.stopVoiceGlowPulseAnimation()

                // 自动聚焦到输入框
                binding.textInputField.postDelayed({
                    binding.textInputField.requestFocus()
                    showKeyboard(binding.textInputField)
                }, 200)

                android.util.Log.d("InputController", "已切换到文字模式")
            }
        }
    }

    /**
     * 发送文字消息
     */
    fun sendTextMessage() {
        val text = binding.textInputField.text.toString().trim()

        if (text.isEmpty()) {
            Toast.makeText(activity, "请输入消息内容", Toast.LENGTH_SHORT).show()
            // 添加抖动动画提示
            binding.textInputCard.animate()
                .translationX(-10f)
                .setDuration(50)
                .withEndAction {
                    binding.textInputCard.animate()
                        .translationX(10f)
                        .setDuration(50)
                        .withEndAction {
                            binding.textInputCard.animate()
                                .translationX(0f)
                                .setDuration(50)
                                .start()
                        }
                        .start()
                }
                .start()
            return
        }

        // 检查任务状态
        if (getCurrentTaskStatus() == TaskStatus.RUNNING) {
            Toast.makeText(activity, "任务执行中，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        android.util.Log.d("InputController", "发送文字消息: $text")

        // 显示发送动画
        binding.textSendButton.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(120)
            .withEndAction {
                binding.textSendButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .start()
            }
            .start()

        // 隐藏键盘
        hideKeyboard()

        // 清空输入框
        binding.textInputField.text.clear()

        // 更新状态为处理中
        updateUIState(RecordingState.PROCESSING)

        // 无障碍公告
        announceForAccessibility("消息已发送：$text")

        // 发送到后端
        sendToBackend(text)
    }

    /**
     * 显示/隐藏取消提示
     */
    fun showCancelHint(show: Boolean) {
        if (show) {
            binding.cancelHintText.visibility = View.VISIBLE
            binding.cancelHintText.animate()
                .alpha(1f)
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
        }
    }

    /**
     * 语音卡片按下动画
     */
    private fun animateVoiceCardPressed(pressed: Boolean) {
        val scale = if (pressed) 0.95f else 1f
        binding.voiceInputCard.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(100)
            .start()
    }

    /**
     * 保存输入模式到 SharedPreferences
     */
    private fun saveInputMode() {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeValue = if (currentInputMode == InputMode.VOICE) "VOICE" else "TEXT"
        prefs.edit().putString(KEY_INPUT_MODE, modeValue).apply()
        android.util.Log.d("InputController", "保存输入模式: $modeValue")
    }

    /**
     * 从 SharedPreferences 恢复输入模式
     * 默认使用文字输入模式，让用户可以直接体验
     */
    private fun restoreInputMode() {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 默认使用文字输入模式（TEXT），让用户可以直接体验
        val savedMode = prefs.getString(KEY_INPUT_MODE, "TEXT")
        currentInputMode = when (savedMode) {
            "VOICE" -> InputMode.VOICE
            else -> InputMode.TEXT
        }
        android.util.Log.d("InputController", "恢复输入模式: $currentInputMode")
    }

    /**
     * 根据任务状态更新输入区域可见性
     */
    fun updateVisibilityForTaskStatus(taskStatus: TaskStatus) {
        when (currentInputMode) {
            InputMode.VOICE -> {
                binding.voiceInputCard.visibility = if (taskStatus == TaskStatus.RUNNING) View.GONE else View.VISIBLE
                binding.textInputCard.visibility = View.GONE
            }
            InputMode.TEXT -> {
                binding.voiceInputCard.visibility = View.GONE
                binding.textInputCard.visibility = if (taskStatus == TaskStatus.RUNNING) View.GONE else View.VISIBLE
            }
        }
        
        // 任务运行时隐藏所有输入
        if (taskStatus == TaskStatus.RUNNING) {
            binding.voiceInputCard.visibility = View.GONE
            binding.textInputCard.visibility = View.GONE
        }
    }

    /**
     * 显示键盘
     */
    private fun showKeyboard(view: View) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * 隐藏键盘
     */
    private fun hideKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = activity.currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }

    /**
     * 设置滑出区域状态
     */
    fun setOutsideVoiceArea(outside: Boolean) {
        isOutsideVoiceArea = outside
    }

    /**
     * 显示语音配置指引对话框
     */
    private fun showVoiceConfigGuideDialog() {
        AlertDialog.Builder(activity)
            .setTitle("语音功能未配置")
            .setMessage("使用语音输入功能需要配置讯飞语音识别。\n\n您可以：\n1. 前往设置页面配置讯飞参数\n2. 或者使用文字输入功能\n\n讯飞提供免费额度，足够个人使用。")
            .setPositiveButton("去配置") { _, _ ->
                // 跳转到设置页面
                val intent = Intent(activity, AdvancedSettingsActivity::class.java)
                activity.startActivity(intent)
            }
            .setNeutralButton("使用文字输入") { _, _ ->
                // 切换到文字输入模式
                currentInputMode = InputMode.TEXT
                saveInputMode()
                updateInputModeUI()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 检查语音功能是否可用
     * @return true 如果讯飞配置完整
     */
    fun isVoiceInputAvailable(): Boolean {
        return IFlytekConfigProvider.isConfigured(activity)
    }
}

