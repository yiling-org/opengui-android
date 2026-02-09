package top.yling.ozx.guiagent.app.activity.main

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import top.yling.ozx.guiagent.R
import top.yling.ozx.guiagent.WaveformView
import top.yling.ozx.guiagent.app.activity.InputMode
import top.yling.ozx.guiagent.speech.SpeechRecognizer
import top.yling.ozx.guiagent.websocket.WebSocketService

/**
 * FollowUp 对话框管理器
 * 负责管理 FollowUp 问题对话框的显示和交互
 * @author shanwb
 */
class FollowUpDialogManager(
    private val activity: Activity,
    private val speechRecognizer: SpeechRecognizer,
    private val getWebSocketService: () -> WebSocketService?,
    private val isServiceBound: () -> Boolean,
    private val hasRecordPermission: () -> Boolean,
    private val isNetworkAvailable: () -> Boolean,
    private val requestRecordPermission: () -> Unit
) {
    // Followup 对话框相关状态
    private var followupDialog: AlertDialog? = null
    private var followupDialogView: View? = null
    private var followupDialogInputMode = InputMode.VOICE
    private var followupDialogIsRecording = false
    private var followupDialogVoiceTouchStartY = 0f
    private var followupDialogVoiceTouchStartX = 0f
    private var followupDialogIsOutsideVoiceArea = false
    private val FOLLOWUP_SWIPE_UP_THRESHOLD_DP = 80f
    
    // 当前识别结果（用于在 stopListening 时获取）
    private var currentRecognitionResult = ""
    private var pendingStopCallback: ((String) -> Unit)? = null

    /**
     * 显示 followup 问题对话框
     * @param question 问题内容
     * @param taskId 任务ID
     */
    fun showDialog(question: String, taskId: String?) {
        activity.runOnUiThread {
            // 检查Activity状态，避免在Activity已销毁或detached时显示对话框
            if (activity.isFinishing || activity.isDestroyed) {
                android.util.Log.w("FollowUpDialogManager", "Activity已销毁，跳过显示followup对话框")
                return@runOnUiThread
            }

            // 检查Window是否已attached（通过检查decorView）
            val decorView = activity.window?.decorView
            if (decorView == null || decorView.parent == null) {
                android.util.Log.w("FollowUpDialogManager", "Activity Window未attached，跳过显示followup对话框")
                return@runOnUiThread
            }

            // 如果已有对话框，先关闭（需要检查Activity状态）
            followupDialog?.let { dialog ->
                try {
                    // 检查Activity和Window状态
                    if (!activity.isFinishing && !activity.isDestroyed && activity.window?.decorView?.parent != null) {
                        dialog.dismiss()
                    } else {
                        android.util.Log.w("FollowUpDialogManager", "Activity状态无效，跳过dismiss旧对话框")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FollowUpDialogManager", "dismiss旧对话框失败: ${e.message}", e)
                }
            }

            // 创建对话框布局
            val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_followup_question, null)
            followupDialogView = dialogView

            // 获取视图引用
            val questionText = dialogView.findViewById<TextView>(R.id.questionText)
            val titleText = dialogView.findViewById<TextView>(R.id.titleText)
            val subtitleText = dialogView.findViewById<TextView>(R.id.subtitleText)
            val answerInput = dialogView.findViewById<EditText>(R.id.answerInput)
            val textSendButton = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.textSendButton)
            val textModeVoiceButton = dialogView.findViewById<ImageButton>(R.id.textModeVoiceButton)
            val voiceInputCard = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.voiceInputCard)
            val textInputCard = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.textInputCard)
            val inputHintText = dialogView.findViewById<TextView>(R.id.inputHintText)
            val waveformView = dialogView.findViewById<WaveformView>(R.id.waveformView)
            val inputGlowLayer = dialogView.findViewById<View>(R.id.inputGlowLayer)
            val cancelHintText = dialogView.findViewById<TextView>(R.id.cancelHintText)

            // 设置问题文本
            questionText.text = question
            titleText.text = activity.getString(R.string.app_name_question)
            subtitleText.text = "回复后继续执行任务"

            // 创建对话框
            val dialog = AlertDialog.Builder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            followupDialog = dialog

            // 设置对话框窗口样式
            dialog.window?.apply {
                setBackgroundDrawableResource(R.drawable.gradient_background)
                setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
                // 设置对话框位置和大小
                val displayMetrics = activity.resources.displayMetrics
                val width = (displayMetrics.widthPixels * 0.95).toInt()
                val height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                attributes = attributes?.apply {
                    this.width = width
                    this.height = height
                    gravity = android.view.Gravity.BOTTOM
                }
            }

            // 初始化输入模式为语音模式
            followupDialogInputMode = InputMode.VOICE
            updateInputModeUI(dialogView)

            // 文字模式：麦克风切换按钮点击事件
            textModeVoiceButton.setOnClickListener {
                performHapticFeedback(dialogView)
                toggleInputMode(dialogView)
            }

            // 文字模式：发送按钮点击事件
            textSendButton.setOnClickListener {
                performHapticFeedback(dialogView)
                val answer = answerInput.text.toString().trim()
                if (answer.isEmpty()) {
                    Toast.makeText(activity, "答案不能为空", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                sendAnswer(answer, taskId, dialog)
            }

            // 输入框回车键监听
            answerInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                    val answer = answerInput.text.toString().trim()
                    if (answer.isNotEmpty()) {
                        sendAnswer(answer, taskId, dialog)
                    }
                    true
                } else {
                    false
                }
            }

            // 设置语音输入触摸监听
            setupVoiceInputTouchListener(dialogView, taskId, dialog)

            // 再次检查Activity状态，确保在显示对话框前Activity仍然有效
            val decorViewCheck = activity.window?.decorView
            if (activity.isFinishing || activity.isDestroyed || decorViewCheck == null || decorViewCheck.parent == null) {
                android.util.Log.w("FollowUpDialogManager", "Activity状态已改变，取消显示followup对话框")
                dialog.dismiss()
                return@runOnUiThread
            }

            // 显示对话框
            try {
                dialog.show()
            } catch (e: Exception) {
                android.util.Log.e("FollowUpDialogManager", "显示followup对话框失败: ${e.message}", e)
                // 如果显示失败，尝试使用FollowUpAnswerActivity作为fallback
                top.yling.ozx.guiagent.FollowUpAnswerActivity.start(activity, question, taskId)
            }

            // 对话框关闭时清理资源
            dialog.setOnDismissListener {
                if (followupDialogIsRecording) {
                    cancelRecording(dialogView)
                }
                waveformView?.stopAnimation()
                followupDialogView = null
                followupDialog = null
            }
        }
    }

    /**
     * 关闭对话框
     */
    fun dismissDialog() {
        followupDialog?.dismiss()
        followupDialog = null
        followupDialogView = null
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        dismissDialog()
    }

    /**
     * 切换输入模式
     */
    private fun toggleInputMode(dialogView: View) {
        followupDialogInputMode = if (followupDialogInputMode == InputMode.VOICE) {
            InputMode.TEXT
        } else {
            InputMode.VOICE
        }
        updateInputModeUI(dialogView)
    }

    /**
     * 更新输入模式UI
     */
    private fun updateInputModeUI(dialogView: View) {
        val voiceInputCard = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.voiceInputCard)
        val textInputCard = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.textInputCard)
        val answerInput = dialogView.findViewById<EditText>(R.id.answerInput)
        val inputHintText = dialogView.findViewById<TextView>(R.id.inputHintText)
        val waveformView = dialogView.findViewById<WaveformView>(R.id.waveformView)
        val inputGlowLayer = dialogView.findViewById<View>(R.id.inputGlowLayer)

        when (followupDialogInputMode) {
            InputMode.VOICE -> {
                voiceInputCard.visibility = View.VISIBLE
                textInputCard.visibility = View.GONE
                inputHintText.visibility = View.VISIBLE
                inputHintText.text = "按住说话"
                waveformView.visibility = View.GONE
                inputGlowLayer.alpha = 0f
                hideKeyboard()
            }
            InputMode.TEXT -> {
                voiceInputCard.visibility = View.GONE
                textInputCard.visibility = View.VISIBLE
                answerInput.postDelayed({
                    answerInput.requestFocus()
                    showKeyboard(answerInput)
                }, 200)
            }
            else -> {
                // 默认使用语音模式
                voiceInputCard.visibility = View.VISIBLE
                textInputCard.visibility = View.GONE
            }
        }
    }

    /**
     * 设置语音输入触摸监听
     */
    private fun setupVoiceInputTouchListener(dialogView: View, taskId: String?, dialog: AlertDialog) {
        val swipeUpThresholdPx = FOLLOWUP_SWIPE_UP_THRESHOLD_DP * activity.resources.displayMetrics.density
        val voiceInputCard = dialogView.findViewById<androidx.cardview.widget.CardView>(R.id.voiceInputCard)

        voiceInputCard.setOnTouchListener { view, event ->
            if (followupDialogInputMode != InputMode.VOICE) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    followupDialogVoiceTouchStartY = event.rawY
                    followupDialogVoiceTouchStartX = event.rawX
                    followupDialogIsOutsideVoiceArea = false

                    performHapticFeedback(dialogView)

                    if (hasRecordPermission()) {
                        startRecording(dialogView)
                    } else {
                        requestRecordPermission()
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!followupDialogIsRecording) {
                        return@setOnTouchListener true
                    }

                    val deltaY = followupDialogVoiceTouchStartY - event.rawY
                    val deltaX = kotlin.math.abs(event.rawX - followupDialogVoiceTouchStartX)

                    if (deltaY > swipeUpThresholdPx) {
                        cancelRecording(dialogView)
                        toggleInputMode(dialogView)
                        return@setOnTouchListener true
                    }

                    if (deltaX > 100 * activity.resources.displayMetrics.density) {
                        followupDialogIsOutsideVoiceArea = true
                        showCancelHint(dialogView, true)
                    } else {
                        followupDialogIsOutsideVoiceArea = false
                        showCancelHint(dialogView, false)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (followupDialogIsRecording) {
                        if (followupDialogIsOutsideVoiceArea) {
                            cancelRecording(dialogView)
                        } else {
                            stopRecording(dialogView, taskId, dialog)
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    /**
     * 开始录音
     */
    private fun startRecording(dialogView: View) {
        if (!hasRecordPermission()) {
            Toast.makeText(activity, "没有录音权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isNetworkAvailable()) {
            Toast.makeText(activity, "请先连接网络！\n语音识别需要网络连接", Toast.LENGTH_LONG).show()
            return
        }

        followupDialogIsRecording = true

        val inputHintText = dialogView.findViewById<TextView>(R.id.inputHintText)
        val waveformView = dialogView.findViewById<WaveformView>(R.id.waveformView)
        val inputGlowLayer = dialogView.findViewById<View>(R.id.inputGlowLayer)

        inputHintText.visibility = View.GONE
        waveformView.visibility = View.VISIBLE
        waveformView.startAnimation()

        // 启动发光动画
        inputGlowLayer.animate()
            .alpha(0.7f)
            .setDuration(200)
            .start()

        // 清空之前的识别结果
        currentRecognitionResult = ""
        
        try {
            speechRecognizer.startListening(object : SpeechRecognizer.Callback {
                override fun onResult(result: SpeechRecognizer.Result) {
                    android.util.Log.d("FollowUpDialogManager", "Followup对话框实时识别: ${result.text}, isFinal=${result.isFinal}")
                    currentRecognitionResult = result.text
                    
                    // 如果是最终结果且有等待的回调，执行回调
                    if (result.isFinal) {
                        pendingStopCallback?.let { callback ->
                            pendingStopCallback = null
                            activity.runOnUiThread {
                                callback(result.text)
                            }
                        }
                    }
                }

                override fun onError(code: Int, message: String) {
                    android.util.Log.e("FollowUpDialogManager", "Followup对话框识别错误: $message")
                    activity.runOnUiThread {
                        Toast.makeText(activity, "语音识别错误: $message", Toast.LENGTH_LONG).show()
                        followupDialogIsRecording = false
                        inputHintText.visibility = View.VISIBLE
                        waveformView.visibility = View.GONE
                        waveformView.stopAnimation()
                        inputGlowLayer.alpha = 0f
                    }
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("FollowUpDialogManager", "Followup对话框识别启动异常: ${e.message}", e)
            Toast.makeText(activity, "语音识别启动失败: ${e.message}", Toast.LENGTH_LONG).show()
            followupDialogIsRecording = false
            inputHintText.visibility = View.VISIBLE
            waveformView.visibility = View.GONE
            waveformView.stopAnimation()
            inputGlowLayer.alpha = 0f
        }
    }

    /**
     * 停止录音
     */
    private fun stopRecording(dialogView: View, taskId: String?, dialog: AlertDialog) {
        followupDialogIsRecording = false

        val inputHintText = dialogView.findViewById<TextView>(R.id.inputHintText)
        val waveformView = dialogView.findViewById<WaveformView>(R.id.waveformView)
        val inputGlowLayer = dialogView.findViewById<View>(R.id.inputGlowLayer)

        waveformView.stopAnimation()
        waveformView.visibility = View.GONE
        inputHintText.visibility = View.VISIBLE

        inputGlowLayer.animate()
            .alpha(0f)
            .setDuration(200)
            .start()

        // 设置停止后的回调
        pendingStopCallback = { finalText ->
            if (finalText.isNotEmpty()) {
                sendAnswer(finalText, taskId, dialog)
            } else {
                Toast.makeText(activity, "未识别到语音，请说话时间长一点或靠近麦克风", Toast.LENGTH_LONG).show()
            }
        }
        
        // 停止识别，结果会通过 onResult(isFinal=true) 回调
        speechRecognizer.stopListening()
    }

    /**
     * 取消录音
     */
    private fun cancelRecording(dialogView: View) {
        if (!followupDialogIsRecording) return

        followupDialogIsRecording = false

        val inputHintText = dialogView.findViewById<TextView>(R.id.inputHintText)
        val waveformView = dialogView.findViewById<WaveformView>(R.id.waveformView)
        val inputGlowLayer = dialogView.findViewById<View>(R.id.inputGlowLayer)

        waveformView.stopAnimation()
        waveformView.visibility = View.GONE
        inputHintText.visibility = View.VISIBLE

        inputGlowLayer.animate()
            .alpha(0f)
            .setDuration(200)
            .start()

        showCancelHint(dialogView, false)

        // 取消识别，不需要结果
        pendingStopCallback = null
        speechRecognizer.cancel()
    }

    /**
     * 显示/隐藏取消提示
     */
    private fun showCancelHint(dialogView: View, show: Boolean) {
        val cancelHintText = dialogView.findViewById<TextView>(R.id.cancelHintText)
        if (show) {
            cancelHintText.visibility = View.VISIBLE
            cancelHintText.animate()
                .alpha(1f)
                .setDuration(150)
                .start()
        } else {
            cancelHintText.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction {
                    cancelHintText.visibility = View.GONE
                }
                .start()
        }
    }

    /**
     * 发送答案
     */
    private fun sendAnswer(answer: String, taskId: String?, dialog: AlertDialog) {
        val service = getWebSocketService()
        if (service == null || !isServiceBound()) {
            Toast.makeText(activity, "WebSocket 服务未绑定", Toast.LENGTH_SHORT).show()
            return
        }

        if (!service.isConnected()) {
            Toast.makeText(activity, "WebSocket 未连接", Toast.LENGTH_SHORT).show()
            return
        }

        android.util.Log.i("FollowUpDialogManager", "发送 followup 答案: $answer, taskId: $taskId")

        // 发送答案
        service.sendToAgent(
            text = answer,
            taskId = taskId,
            onSuccess = {
                activity.runOnUiThread {
                    android.util.Log.d("FollowUpDialogManager", "followup 答案已发送")
                    Toast.makeText(activity, "答案已发送", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            },
            onError = { error ->
                activity.runOnUiThread {
                    android.util.Log.e("FollowUpDialogManager", "发送 followup 答案失败: $error")
                    Toast.makeText(activity, "发送失败: $error", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    /**
     * 执行触觉反馈
     */
    private fun performHapticFeedback(view: View) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        } else {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
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
}

