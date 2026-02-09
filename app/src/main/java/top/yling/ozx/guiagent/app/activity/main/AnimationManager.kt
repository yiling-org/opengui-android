package top.yling.ozx.guiagent.app.activity.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import top.yling.ozx.guiagent.R
import top.yling.ozx.guiagent.databinding.ActivityMainBinding

/**
 * MainActivity 动画管理器
 * 负责管理所有 UI 动画效果
 * @author shanwb
 */
class AnimationManager(
    private val context: Context,
    private val binding: ActivityMainBinding
) {
    // 动画状态标志
    private var pulseAnimationStarted = false
    private var actionButtonPulseStarted = false

    // 动画器引用
    private var pulseAnimatorSet: AnimatorSet? = null
    private var actionButtonPulseAnimatorSet: AnimatorSet? = null
    private var recordingAnimator: ObjectAnimator? = null
    private val rippleAnimators = mutableListOf<ObjectAnimator>()
    private var textInputPulseAnimator: ObjectAnimator? = null
    private var voiceGlowPulseAnimator: ObjectAnimator? = null
    private var swipeHintAnimation: Animation? = null

    /**
     * 启动按钮脉冲动画（actionButtonGlow）
     */
    fun startButtonPulseAnimation() {
        // 防止重复启动
        if (pulseAnimationStarted) {
            return
        }

        // 确保 actionButtonGlow 可见
        if (binding.actionButtonGlow.visibility != View.VISIBLE) {
            binding.actionButtonGlow.visibility = View.VISIBLE
        }

        // 取消之前的动画集
        pulseAnimatorSet?.cancel()

        // 重置初始状态
        binding.actionButtonGlow.scaleX = 1f
        binding.actionButtonGlow.scaleY = 1f
        binding.actionButtonGlow.alpha = 0.4f

        // 创建所有动画
        val scaleXAnimator = ObjectAnimator.ofFloat(binding.actionButtonGlow, "scaleX", 1f, 1.1f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(binding.actionButtonGlow, "scaleY", 1f, 1.1f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val alphaAnimator = ObjectAnimator.ofFloat(binding.actionButtonGlow, "alpha", 0.4f, 0.6f, 0.4f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 使用 AnimatorSet 统一管理
        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, alphaAnimator)
            start()
        }

        pulseAnimationStarted = true
    }

    /**
     * 启动主操作按钮的脉冲动画
     */
    fun startRecordButtonPulseAnimation() {
        // 防止重复启动
        if (actionButtonPulseStarted) {
            return
        }

        // 确保按钮可见
        if (binding.actionButton.visibility != View.VISIBLE) {
            return
        }

        // 取消之前的动画集
        actionButtonPulseAnimatorSet?.cancel()

        // 重置初始状态
        binding.actionButton.scaleX = 1f
        binding.actionButton.scaleY = 1f

        // 创建按钮的轻微脉冲动画
        val scaleXAnimator = ObjectAnimator.ofFloat(binding.actionButton, "scaleX", 1f, 0.98f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(binding.actionButton, "scaleY", 1f, 0.98f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 使用 AnimatorSet 统一管理
        actionButtonPulseAnimatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator)
            start()
        }

        actionButtonPulseStarted = true
    }

    /**
     * 启动录音动画
     */
    fun startRecordingAnimation() {
        // 取消之前的动画
        recordingAnimator?.cancel()
        rippleAnimators.forEach { it.cancel() }
        rippleAnimators.clear()

        // 暂停默认的脉冲动画，避免冲突
        actionButtonPulseAnimatorSet?.pause()

        // 创建脉冲缩放动画
        recordingAnimator = ObjectAnimator.ofFloat(binding.actionButton, "scaleX", 0.92f, 0.88f, 0.92f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.actionButton, "scaleY", 0.92f, 0.88f, 0.92f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // 发光层动画
        ObjectAnimator.ofFloat(binding.actionButtonGlow, "scaleX", 1.1f, 1.25f, 1.1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.actionButtonGlow, "scaleY", 1.1f, 1.25f, 1.1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.actionButtonGlow, "alpha", 0.7f, 0.9f, 0.7f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 停止录音动画
     */
    fun stopRecordingAnimation() {
        recordingAnimator?.cancel()
        recordingAnimator = null

        // 停止并重置水波纹
        rippleAnimators.forEach { it.cancel() }
        rippleAnimators.clear()

        // 恢复默认的脉冲动画
        actionButtonPulseAnimatorSet?.resume()

        // 重置actionButton的缩放和透明度
        binding.actionButton.scaleX = 1f
        binding.actionButton.scaleY = 1f

        // 重置发光层
        binding.actionButtonGlow.scaleX = 1f
        binding.actionButtonGlow.scaleY = 1f
        binding.actionButtonGlow.alpha = 0.4f
    }

    /**
     * 启动水波纹动画
     */
    fun startRippleAnimation() {
        val ripples = listOf(binding.rippleRing1, binding.rippleRing2, binding.rippleRing3)
        val delays = listOf(0L, 600L, 1200L)

        ripples.forEachIndexed { index, ripple ->
            // 确保 rippleRing 可见并重置状态
            ripple.visibility = View.VISIBLE
            ripple.alpha = 0.6f
            ripple.scaleX = 1f
            ripple.scaleY = 1f

            // 缩放动画
            val scaleXAnimator = ObjectAnimator.ofFloat(ripple, "scaleX", 1f, 1.8f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                startDelay = delays[index]
                interpolator = AccelerateDecelerateInterpolator()
            }

            val scaleYAnimator = ObjectAnimator.ofFloat(ripple, "scaleY", 1f, 1.8f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                startDelay = delays[index]
                interpolator = AccelerateDecelerateInterpolator()
            }

            // 透明度动画
            val alphaAnimator = ObjectAnimator.ofFloat(ripple, "alpha", 0.6f, 0f).apply {
                duration = 1800
                repeatCount = ValueAnimator.INFINITE
                startDelay = delays[index]
                interpolator = AccelerateDecelerateInterpolator()
            }

            rippleAnimators.add(scaleXAnimator)
            rippleAnimators.add(scaleYAnimator)
            rippleAnimators.add(alphaAnimator)

            scaleXAnimator.start()
            scaleYAnimator.start()
            alphaAnimator.start()
        }
    }

    /**
     * 语音卡片按下/松开动画
     */
    fun animateVoiceCardPressed(pressed: Boolean) {
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
    fun startVoiceGlowPulseAnimation() {
        voiceGlowPulseAnimator?.cancel()
        voiceGlowPulseAnimator = ObjectAnimator.ofFloat(binding.inputGlowLayer, "alpha", 0.5f, 0.9f, 0.5f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 停止语音发光脉冲动画
     */
    fun stopVoiceGlowPulseAnimation() {
        voiceGlowPulseAnimator?.cancel()
        voiceGlowPulseAnimator = null
        binding.inputGlowLayer.alpha = 0f
    }

    /**
     * 操作按钮动画
     */
    fun animateActionButton(pressed: Boolean) {
        val scale = if (pressed) 0.92f else 1f
        val alpha = if (pressed) 0.85f else 1f
        val glowAlpha = if (pressed) 0.7f else 0.4f

        ObjectAnimator.ofFloat(binding.actionButton, "scaleX", scale).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.actionButton, "scaleY", scale).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        ObjectAnimator.ofFloat(binding.actionButton, "alpha", alpha).apply {
            duration = 200
            start()
        }

        ObjectAnimator.ofFloat(binding.actionButtonGlow, "alpha", glowAlpha).apply {
            duration = 200
            start()
        }
    }

    /**
     * 统一的Header按钮点击动画
     * 提供优雅的缩放反馈效果
     */
    fun animateHeaderButtonClick(button: View, action: () -> Unit) {
        // 缩放动画 - 更流畅的交互反馈
        button.animate()
            .scaleX(0.88f)
            .scaleY(0.88f)
            .setDuration(120)
            .withEndAction {
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        action()
                    }
                    .start()
            }
            .start()
    }

    /**
     * 模式切换按钮点击动画
     */
    fun animateModeToggleClick(action: () -> Unit) {
        binding.modeToggleButton.animate()
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(100)
            .withEndAction {
                binding.modeToggleButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        action()
                    }
                    .start()
            }
            .start()
    }

    /**
     * 启动文字输入区域脉动动画
     */
    fun startTextInputPulseAnimation() {
        // 取消之前的动画
        textInputPulseAnimator?.cancel()

        // 输入框发光层脉动动画
        textInputPulseAnimator = ObjectAnimator.ofFloat(binding.inputGlowLayer, "alpha", 0.2f, 0.35f, 0.2f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // 主操作按钮发光层脉动
        ObjectAnimator.ofFloat(binding.actionButtonGlow, "alpha", 0.35f, 0.55f, 0.35f).apply {
            duration = 2500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 停止文字输入区域脉动动画
     */
    fun stopTextInputPulseAnimation() {
        textInputPulseAnimator?.cancel()
        textInputPulseAnimator = null
    }

    /**
     * 启动上滑切换提示的呼吸动画
     */
    fun startSwipeHintAnimation() {
        try {
            swipeHintAnimation = android.view.animation.AnimationUtils.loadAnimation(context, R.anim.bounce_hint)
            binding.voiceSwipeArrow.startAnimation(swipeHintAnimation)
        } catch (e: Exception) {
            android.util.Log.e("AnimationManager", "启动上滑提示动画失败: ${e.message}")
        }
    }

    /**
     * 停止上滑切换提示的呼吸动画
     */
    fun stopSwipeHintAnimation() {
        swipeHintAnimation?.cancel()
        swipeHintAnimation = null
        binding.voiceSwipeArrow.clearAnimation()
    }

    /**
     * 停止所有录音相关的动画（用于切换到文字模式时）
     */
    fun stopAllRecordingAnimations() {
        // 停止波形动画
        binding.waveformView.stopAnimation()
        binding.waveformView.visibility = View.GONE

        // 停止录音动画
        stopRecordingAnimation()

        // 停止语音发光脉冲动画
        stopVoiceGlowPulseAnimation()

        // 停止并取消脉冲动画
        actionButtonPulseAnimatorSet?.cancel()
        actionButtonPulseAnimatorSet = null
        actionButtonPulseStarted = false

        // 重置语音卡片状态
        binding.voiceInputCard.scaleX = 1f
        binding.voiceInputCard.scaleY = 1f
        binding.inputGlowLayer.alpha = 0f

        android.util.Log.d("AnimationManager", "已停止所有录音相关动画")
    }

    /**
     * 暂停所有动画
     */
    fun pauseAll() {
        actionButtonPulseAnimatorSet?.pause()
        textInputPulseAnimator?.pause()
    }

    /**
     * 恢复所有动画
     */
    fun resumeAll() {
        actionButtonPulseAnimatorSet?.resume()
    }

    /**
     * 清理所有动画资源
     */
    fun cleanup() {
        pulseAnimatorSet?.cancel()
        pulseAnimatorSet = null
        actionButtonPulseAnimatorSet?.cancel()
        actionButtonPulseAnimatorSet = null
        recordingAnimator?.cancel()
        recordingAnimator = null
        rippleAnimators.forEach { it.cancel() }
        rippleAnimators.clear()
        textInputPulseAnimator?.cancel()
        textInputPulseAnimator = null
        voiceGlowPulseAnimator?.cancel()
        voiceGlowPulseAnimator = null
        swipeHintAnimation?.cancel()
        swipeHintAnimation = null

        // 重置动画标志
        pulseAnimationStarted = false
        actionButtonPulseStarted = false

        // 清理波形视图
        binding.waveformView.cleanup()
    }
}

