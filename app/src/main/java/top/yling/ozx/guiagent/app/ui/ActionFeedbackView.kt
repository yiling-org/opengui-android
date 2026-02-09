package top.yling.ozx.guiagent.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator

/**
 * 操作反馈视图
 * 在AI执行操作时显示轻量级的视觉反馈效果
 *
 * 支持的反馈类型：
 * - 点击：涟漪扩散动画
 * - 长按：脉冲光晕
 * - 滑动：方向箭头 + 轨迹
 * - 拖拽：起点→终点轨迹线
 * - 输入：键盘图标闪烁
 *
 * @author shanwb
 */
class ActionFeedbackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ActionFeedbackView"

        // 主题颜色
        private val COLOR_PRIMARY = Color.parseColor("#007AFF")
        private val COLOR_PRIMARY_LIGHT = Color.parseColor("#4DA6FF")

        // 动画时长
        private const val CLICK_DURATION = 250L      // 点击涟漪，快速消失
        private const val LONG_PRESS_PULSE_DURATION = 800L
        private const val SCROLL_DURATION = 400L     // 滑动轨迹
        private const val DRAG_DURATION = 400L       // 拖拽轨迹
        private const val TYPE_DURATION = 250L       // 输入提示

        // 点击光环配置
        private const val CLICK_RADIUS_DP = 24f        // 光环半径
        private const val CLICK_STROKE_WIDTH_DP = 2.5f // 光环线宽

        // 轨迹配置
        private const val TRAIL_STROKE_WIDTH_DP = 3f
        private const val TRAIL_ARROW_SIZE_DP = 12f
        private const val TRAIL_DOT_RADIUS_DP = 6f
    }

    // 画笔
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rippleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val typePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 尺寸配置（dp转px）
    private val density = resources.displayMetrics.density
    private val clickRadius = CLICK_RADIUS_DP * density
    private val clickStrokeWidth = CLICK_STROKE_WIDTH_DP * density
    private val trailStrokeWidth = TRAIL_STROKE_WIDTH_DP * density
    private val trailArrowSize = TRAIL_ARROW_SIZE_DP * density
    private val trailDotRadius = TRAIL_DOT_RADIUS_DP * density

    // 当前动画状态
    private var currentAnimationType: AnimationType = AnimationType.NONE
    private var animatorSet: AnimatorSet? = null
    private var longPressAnimator: ValueAnimator? = null

    // 点击动画参数
    private var clickX = 0f
    private var clickY = 0f
    private var clickScale = 0f    // 0->1 缩放
    private var clickAlpha = 1f

    // 长按动画参数
    private var longPressX = 0f
    private var longPressY = 0f
    private var longPressScale = 1f
    private var longPressAlpha = 0.6f

    // 滑动/拖拽动画参数
    private var trailStartX = 0f
    private var trailStartY = 0f
    private var trailEndX = 0f
    private var trailEndY = 0f
    private var trailProgress = 0f
    private var trailAlpha = 1f

    // 输入动画参数
    private var typeAlpha = 0f

    enum class AnimationType {
        NONE,
        CLICK,
        LONG_PRESS,
        SCROLL,
        DRAG,
        TYPE
    }

    init {
        // 设置为不可点击，透传触摸事件
        isClickable = false
        isFocusable = false

        // 默认隐藏
        visibility = GONE

        // 初始化画笔
        setupPaints()
    }

    private fun setupPaints() {
        // 点击光环画笔
        ripplePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = clickStrokeWidth
            color = COLOR_PRIMARY
        }

        // 点击中心点画笔
        rippleFillPaint.apply {
            style = Paint.Style.FILL
            color = COLOR_PRIMARY
        }

        // 轨迹画笔
        trailPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = trailStrokeWidth
            color = COLOR_PRIMARY
            strokeCap = Paint.Cap.ROUND
            pathEffect = DashPathEffect(floatArrayOf(10f * density, 5f * density), 0f)
        }

        // 圆点画笔
        dotPaint.apply {
            style = Paint.Style.FILL
            color = COLOR_PRIMARY
        }

        // 箭头画笔
        arrowPaint.apply {
            style = Paint.Style.FILL
            color = COLOR_PRIMARY
        }

        // 输入图标画笔
        typePaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = COLOR_PRIMARY
            strokeCap = Paint.Cap.ROUND
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        when (currentAnimationType) {
            AnimationType.CLICK -> drawClickRipple(canvas)
            AnimationType.LONG_PRESS -> drawLongPressGlow(canvas)
            AnimationType.SCROLL -> drawScrollTrail(canvas)
            AnimationType.DRAG -> drawDragTrail(canvas)
            AnimationType.TYPE -> drawTypeIndicator(canvas)
            AnimationType.NONE -> { /* 不绘制 */ }
        }
    }

    /**
     * 绘制点击光环效果
     * 简洁的单圈光环，快速闪现消失
     */
    private fun drawClickRipple(canvas: Canvas) {
        val radius = clickRadius * clickScale
        val alpha = (clickAlpha * 220).toInt()

        // 单圈光环
        ripplePaint.alpha = alpha
        canvas.drawCircle(clickX, clickY, radius, ripplePaint)

        // 中心小点（可选，增加定位感）
        if (clickScale < 0.5f) {
            val dotRadius = 3f * density * (1f - clickScale * 2)
            rippleFillPaint.alpha = alpha
            canvas.drawCircle(clickX, clickY, dotRadius, rippleFillPaint)
        }
    }

    /**
     * 绘制长按脉冲光晕
     */
    private fun drawLongPressGlow(canvas: Canvas) {
        val baseRadius = clickRadius * 1.5f
        val currentRadius = baseRadius * longPressScale

        // 外圈光晕
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                longPressX, longPressY, currentRadius * 1.5f,
                intArrayOf(
                    Color.argb((longPressAlpha * 100).toInt(), 0, 122, 255),
                    Color.argb((longPressAlpha * 50).toInt(), 0, 122, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(longPressX, longPressY, currentRadius * 1.5f, glowPaint)

        // 中心实心圆
        rippleFillPaint.alpha = (longPressAlpha * 200).toInt()
        canvas.drawCircle(longPressX, longPressY, currentRadius * 0.3f, rippleFillPaint)

        // 边框环
        ripplePaint.alpha = (longPressAlpha * 180).toInt()
        canvas.drawCircle(longPressX, longPressY, currentRadius, ripplePaint)
    }

    /**
     * 绘制滑动轨迹
     */
    private fun drawScrollTrail(canvas: Canvas) {
        // 计算当前终点
        val currentEndX = trailStartX + (trailEndX - trailStartX) * trailProgress
        val currentEndY = trailStartY + (trailEndY - trailStartY) * trailProgress

        // 起点圆点
        dotPaint.alpha = (trailAlpha * 200).toInt()
        canvas.drawCircle(trailStartX, trailStartY, trailDotRadius, dotPaint)

        // 轨迹线
        trailPaint.alpha = (trailAlpha * 180).toInt()
        canvas.drawLine(trailStartX, trailStartY, currentEndX, currentEndY, trailPaint)

        // 箭头
        if (trailProgress > 0.3f) {
            drawArrow(canvas, currentEndX, currentEndY,
                Math.atan2((trailEndY - trailStartY).toDouble(), (trailEndX - trailStartX).toDouble()).toFloat())
        }
    }

    /**
     * 绘制拖拽轨迹
     */
    private fun drawDragTrail(canvas: Canvas) {
        // 计算当前终点
        val currentEndX = trailStartX + (trailEndX - trailStartX) * trailProgress
        val currentEndY = trailStartY + (trailEndY - trailStartY) * trailProgress

        // 起点圆点（带光晕）
        val startGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                trailStartX, trailStartY, trailDotRadius * 3,
                intArrayOf(
                    Color.argb((trailAlpha * 150).toInt(), 0, 122, 255),
                    Color.TRANSPARENT
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(trailStartX, trailStartY, trailDotRadius * 3, startGlowPaint)
        dotPaint.alpha = (trailAlpha * 220).toInt()
        canvas.drawCircle(trailStartX, trailStartY, trailDotRadius, dotPaint)

        // 轨迹线（实线）
        val solidTrailPaint = Paint(trailPaint).apply {
            pathEffect = null
            alpha = (trailAlpha * 150).toInt()
        }
        canvas.drawLine(trailStartX, trailStartY, currentEndX, currentEndY, solidTrailPaint)

        // 当前位置圆点（移动中的点）
        if (trailProgress > 0.1f) {
            dotPaint.alpha = (trailAlpha * 255).toInt()
            canvas.drawCircle(currentEndX, currentEndY, trailDotRadius * 1.2f, dotPaint)
        }

        // 终点标记（完成时）
        if (trailProgress > 0.9f) {
            val endGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = RadialGradient(
                    trailEndX, trailEndY, trailDotRadius * 3,
                    intArrayOf(
                        Color.argb((trailAlpha * 100).toInt(), 76, 175, 80),
                        Color.TRANSPARENT
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(trailEndX, trailEndY, trailDotRadius * 3, endGlowPaint)
        }
    }

    /**
     * 绘制输入指示器
     */
    private fun drawTypeIndicator(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height * 0.4f
        val iconSize = 24f * density

        typePaint.alpha = (typeAlpha * 200).toInt()

        // 绘制简化的键盘图标
        val left = centerX - iconSize
        val top = centerY - iconSize * 0.6f
        val right = centerX + iconSize
        val bottom = centerY + iconSize * 0.6f

        // 键盘外框（圆角矩形）
        val rect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(rect, 4f * density, 4f * density, typePaint)

        // 键盘按键（简化的三行点）
        val dotRadius = 2f * density
        val spacing = iconSize * 0.4f

        // 第一行
        for (i in -2..2) {
            canvas.drawCircle(centerX + i * spacing * 0.5f, top + iconSize * 0.3f, dotRadius, typePaint)
        }
        // 第二行
        for (i in -1..1) {
            canvas.drawCircle(centerX + i * spacing * 0.6f, centerY, dotRadius, typePaint)
        }
        // 空格条
        canvas.drawLine(centerX - spacing * 0.8f, bottom - iconSize * 0.25f,
            centerX + spacing * 0.8f, bottom - iconSize * 0.25f, typePaint)
    }

    /**
     * 绘制箭头
     */
    private fun drawArrow(canvas: Canvas, x: Float, y: Float, angle: Float) {
        arrowPaint.alpha = (trailAlpha * 200).toInt()

        val path = Path()
        val arrowLength = trailArrowSize
        val arrowWidth = trailArrowSize * 0.6f

        // 箭头顶点在 (x, y)
        path.moveTo(x, y)
        path.lineTo(
            x - arrowLength * Math.cos(angle - Math.PI / 6).toFloat(),
            y - arrowLength * Math.sin(angle - Math.PI / 6).toFloat()
        )
        path.lineTo(
            x - arrowLength * 0.6f * Math.cos(angle.toDouble()).toFloat(),
            y - arrowLength * 0.6f * Math.sin(angle.toDouble()).toFloat()
        )
        path.lineTo(
            x - arrowLength * Math.cos(angle + Math.PI / 6).toFloat(),
            y - arrowLength * Math.sin(angle + Math.PI / 6).toFloat()
        )
        path.close()

        canvas.drawPath(path, arrowPaint)
    }

    // ========== 公开 API ==========

    /**
     * 显示点击反馈
     * @param x 点击位置X坐标（屏幕绝对坐标）
     * @param y 点击位置Y坐标（屏幕绝对坐标）
     */
    fun showClickFeedback(x: Float, y: Float) {
        Log.d(TAG, "showClickFeedback: ($x, $y)")
        stopCurrentAnimation()

        clickX = x
        clickY = y
        clickScale = 0f
        clickAlpha = 1f
        currentAnimationType = AnimationType.CLICK
        visibility = VISIBLE

        // 单一动画：快速扩展 + 同步淡出
        animatorSet = AnimatorSet().apply {
            val scaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = CLICK_DURATION
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    clickScale = animator.animatedValue as Float
                    // 后半程开始淡出
                    clickAlpha = if (clickScale > 0.4f) {
                        1f - (clickScale - 0.4f) / 0.6f
                    } else {
                        1f
                    }
                    invalidate()
                }
            }

            play(scaleAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hideAndReset()
                }
            })
            start()
        }
    }

    /**
     * 显示长按反馈（开始）
     * @param x 长按位置X坐标
     * @param y 长按位置Y坐标
     */
    fun showLongPressFeedback(x: Float, y: Float) {
        Log.d(TAG, "showLongPressFeedback: ($x, $y)")
        stopCurrentAnimation()

        longPressX = x
        longPressY = y
        longPressScale = 1f
        longPressAlpha = 0.6f
        currentAnimationType = AnimationType.LONG_PRESS
        visibility = VISIBLE

        // 脉冲动画（循环）
        longPressAnimator = ValueAnimator.ofFloat(0.9f, 1.1f, 0.9f).apply {
            duration = LONG_PRESS_PULSE_DURATION
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                longPressScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 隐藏长按反馈（结束）
     */
    fun hideLongPressFeedback() {
        if (currentAnimationType == AnimationType.LONG_PRESS) {
            Log.d(TAG, "hideLongPressFeedback")
            // 淡出动画
            ValueAnimator.ofFloat(longPressAlpha, 0f).apply {
                duration = 200
                addUpdateListener { animator ->
                    longPressAlpha = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        hideAndReset()
                    }
                })
                start()
            }
            longPressAnimator?.cancel()
            longPressAnimator = null
        }
    }

    /**
     * 显示滑动反馈
     * @param startX 起始位置X
     * @param startY 起始位置Y
     * @param direction 方向（up/down/left/right）
     * @param distance 滑动距离
     */
    fun showScrollFeedback(startX: Float, startY: Float, direction: String, distance: Float) {
        Log.d(TAG, "showScrollFeedback: ($startX, $startY) $direction $distance")
        stopCurrentAnimation()

        trailStartX = startX
        trailStartY = startY

        // 根据方向计算终点
        val displayDistance = minOf(distance, 300f * density) // 限制显示距离
        when (direction.lowercase()) {
            "up" -> {
                trailEndX = startX
                trailEndY = startY - displayDistance
            }
            "down" -> {
                trailEndX = startX
                trailEndY = startY + displayDistance
            }
            "left" -> {
                trailEndX = startX - displayDistance
                trailEndY = startY
            }
            "right" -> {
                trailEndX = startX + displayDistance
                trailEndY = startY
            }
            else -> {
                trailEndX = startX
                trailEndY = startY - displayDistance // 默认向上
            }
        }

        trailProgress = 0f
        trailAlpha = 1f
        currentAnimationType = AnimationType.SCROLL
        visibility = VISIBLE

        val progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SCROLL_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                trailProgress = animator.animatedValue as Float
                invalidate()
            }
        }

        val alphaAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            startDelay = SCROLL_DURATION - 100
            addUpdateListener { animator ->
                trailAlpha = animator.animatedValue as Float
            }
        }

        animatorSet = AnimatorSet().apply {
            playTogether(progressAnimator, alphaAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hideAndReset()
                }
            })
            start()
        }
    }

    /**
     * 显示拖拽反馈
     * @param startX 起始位置X
     * @param startY 起始位置Y
     * @param endX 结束位置X
     * @param endY 结束位置Y
     */
    fun showDragFeedback(startX: Float, startY: Float, endX: Float, endY: Float) {
        Log.d(TAG, "showDragFeedback: ($startX, $startY) -> ($endX, $endY)")
        stopCurrentAnimation()

        trailStartX = startX
        trailStartY = startY
        trailEndX = endX
        trailEndY = endY
        trailProgress = 0f
        trailAlpha = 1f
        currentAnimationType = AnimationType.DRAG
        visibility = VISIBLE

        val progressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DRAG_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                trailProgress = animator.animatedValue as Float
                invalidate()
            }
        }

        val alphaAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            startDelay = DRAG_DURATION
            addUpdateListener { animator ->
                trailAlpha = animator.animatedValue as Float
            }
        }

        animatorSet = AnimatorSet().apply {
            playTogether(progressAnimator, alphaAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hideAndReset()
                }
            })
            start()
        }
    }

    /**
     * 显示输入反馈
     */
    fun showTypeFeedback() {
        Log.d(TAG, "showTypeFeedback")
        stopCurrentAnimation()

        typeAlpha = 0f
        currentAnimationType = AnimationType.TYPE
        visibility = VISIBLE

        // 闪烁动画
        animatorSet = AnimatorSet().apply {
            val fadeIn = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TYPE_DURATION / 2
                addUpdateListener { animator ->
                    typeAlpha = animator.animatedValue as Float
                    invalidate()
                }
            }
            val fadeOut = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = TYPE_DURATION / 2
                addUpdateListener { animator ->
                    typeAlpha = animator.animatedValue as Float
                    invalidate()
                }
            }
            playSequentially(fadeIn, fadeOut)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hideAndReset()
                }
            })
            start()
        }
    }

    /**
     * 快速隐藏（截图前调用）
     */
    fun hideForScreenshot() {
        visibility = INVISIBLE
    }

    /**
     * 恢复显示（截图后调用）
     */
    fun showAfterScreenshot() {
        if (currentAnimationType != AnimationType.NONE) {
            visibility = VISIBLE
        }
    }

    private fun stopCurrentAnimation() {
        animatorSet?.cancel()
        animatorSet = null
        longPressAnimator?.cancel()
        longPressAnimator = null
    }

    private fun hideAndReset() {
        visibility = GONE
        currentAnimationType = AnimationType.NONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopCurrentAnimation()
    }
}
