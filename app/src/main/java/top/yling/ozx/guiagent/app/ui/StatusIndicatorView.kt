package top.yling.ozx.guiagent.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * 状态指示灯View
 *
 * 功能特点:
 * - 圆点 + 文字标签显示当前AI状态
 * - 不同状态对应不同颜色
 * - 支持脉冲动画效果
 * - 紧凑设计，适合状态栏区域显示
 */
class StatusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "StatusIndicatorView"

        // 状态定义
        const val STATUS_IDLE = 0
        const val STATUS_RECORDING = 1
        const val STATUS_PROCESSING = 2
        const val STATUS_COMPLETED = 3
        const val STATUS_ERROR = 4

        // 默认配置
        private const val DEFAULT_DOT_RADIUS = 4f    // 圆点半径(dp)
        private const val DEFAULT_TEXT_SIZE = 11f    // 文字大小(sp)
        private const val DEFAULT_PADDING = 6f       // 内边距(dp)
        private const val DEFAULT_DOT_TEXT_GAP = 4f  // 圆点和文字间距(dp)

        // 状态颜色
        private val COLOR_IDLE = Color.parseColor("#007AFF")      // 空闲 - 蓝色
        private val COLOR_RECORDING = Color.parseColor("#E91E63") // 录音中 - 红色
        private val COLOR_PROCESSING = Color.parseColor("#FFA726")// 处理中 - 金色
        private val COLOR_COMPLETED = Color.parseColor("#4CAF50") // 完成 - 绿色
        private val COLOR_ERROR = Color.parseColor("#F44336")     // 错误 - 红色

        // 状态文字
        private val STATUS_LABELS = mapOf(
            STATUS_IDLE to "AI就绪",
            STATUS_RECORDING to "录音中",
            STATUS_PROCESSING to "AI控制中",
            STATUS_COMPLETED to "已完成",
            STATUS_ERROR to "出错了"
        )
    }

    // 绘制相关
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bgRectF = RectF()

    // 配置参数
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private var dotRadius = DEFAULT_DOT_RADIUS * density
    private var textSize = DEFAULT_TEXT_SIZE * scaledDensity
    private var padding = DEFAULT_PADDING * density
    private var dotTextGap = DEFAULT_DOT_TEXT_GAP * density

    // 当前状态
    private var currentStatus = STATUS_IDLE
    private var currentColor = COLOR_IDLE
    private var currentLabel = STATUS_LABELS[STATUS_IDLE] ?: ""

    // 动画相关
    private var pulseAnimator: ObjectAnimator? = null
    private var dotScale = 1f
    private var isPulsing = false

    init {
        setupPaints()
    }

    private fun setupPaints() {
        dotPaint.apply {
            style = Paint.Style.FILL
            color = currentColor
        }

        textPaint.apply {
            color = Color.WHITE
            textSize = this@StatusIndicatorView.textSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        bgPaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#CC000000")  // 半透明黑色背景
        }

        glowPaint.apply {
            style = Paint.Style.FILL
            color = currentColor
            alpha = 80
            maskFilter = BlurMaskFilter(dotRadius, BlurMaskFilter.Blur.NORMAL)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val textWidth = textPaint.measureText(currentLabel)
        val textHeight = textPaint.textSize

        val width = (padding * 2 + dotRadius * 2 + dotTextGap + textWidth).toInt()
        val height = (padding * 2 + maxOf(dotRadius * 2, textHeight)).toInt()

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 更新背景矩形
        bgRectF.set(0f, 0f, width.toFloat(), height.toFloat())

        // 绘制圆角背景
        val cornerRadius = height / 2f
        canvas.drawRoundRect(bgRectF, cornerRadius, cornerRadius, bgPaint)

        // 计算圆点位置
        val dotCenterX = padding + dotRadius
        val dotCenterY = height / 2f

        // 绘制圆点光晕
        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius * dotScale * 1.5f, glowPaint)

        // 绘制圆点
        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius * dotScale, dotPaint)

        // 绘制文字
        val textX = dotCenterX + dotRadius + dotTextGap
        val textY = height / 2f + textPaint.textSize / 3  // 垂直居中调整
        canvas.drawText(currentLabel, textX, textY, textPaint)
    }

    /**
     * 设置状态
     */
    fun setStatus(status: Int) {
        if (currentStatus == status) return

        currentStatus = status
        currentColor = when (status) {
            STATUS_IDLE -> COLOR_IDLE
            STATUS_RECORDING -> COLOR_RECORDING
            STATUS_PROCESSING -> COLOR_PROCESSING
            STATUS_COMPLETED -> COLOR_COMPLETED
            STATUS_ERROR -> COLOR_ERROR
            else -> COLOR_IDLE
        }
        currentLabel = STATUS_LABELS[status] ?: "未知"

        // 更新画笔颜色
        dotPaint.color = currentColor
        glowPaint.color = currentColor
        glowPaint.alpha = 80

        // 根据状态控制动画
        when (status) {
            STATUS_RECORDING, STATUS_PROCESSING -> startPulseAnimation()
            else -> stopPulseAnimation()
        }

        requestLayout()
        invalidate()
    }

    /**
     * 开始脉冲动画
     */
    private fun startPulseAnimation() {
        if (isPulsing) return

        isPulsing = true
        pulseAnimator = ObjectAnimator.ofFloat(this, "dotScaleValue", 1f, 1.3f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    /**
     * 停止脉冲动画
     */
    private fun stopPulseAnimation() {
        isPulsing = false
        pulseAnimator?.cancel()
        pulseAnimator = null
        dotScale = 1f
        invalidate()
    }

    /**
     * 动画属性setter（供ObjectAnimator使用）
     */
    @Suppress("unused")
    fun setDotScaleValue(scale: Float) {
        dotScale = scale
        invalidate()
    }

    /**
     * 动画属性getter
     */
    @Suppress("unused")
    fun getDotScaleValue(): Float = dotScale

    /**
     * 便捷方法：设置为空闲状态
     */
    fun setIdle() = setStatus(STATUS_IDLE)

    /**
     * 便捷方法：设置为录音中状态
     */
    fun setRecording() = setStatus(STATUS_RECORDING)

    /**
     * 便捷方法：设置为处理中状态
     */
    fun setProcessing() = setStatus(STATUS_PROCESSING)

    /**
     * 便捷方法：设置为完成状态
     */
    fun setCompleted() = setStatus(STATUS_COMPLETED)

    /**
     * 便捷方法：设置为错误状态
     */
    fun setError() = setStatus(STATUS_ERROR)

    /**
     * 快速隐藏（用于截图前）
     */
    fun hideForScreenshot() {
        visibility = INVISIBLE
    }

    /**
     * 恢复显示（截图后）
     */
    fun showAfterScreenshot() {
        visibility = VISIBLE
    }

    /**
     * 设置自定义文字
     */
    fun setCustomLabel(label: String) {
        currentLabel = label
        requestLayout()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulseAnimation()
    }
}
