package top.yling.ozx.guiagent.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 屏幕边框光晕效果View
 *
 * 功能特点:
 * - 屏幕四周2-3px宽度的渐变光晕
 * - 支持顺时针流动动画效果
 * - 不干扰触摸事件（透传给下层）
 * - 截图时可快速隐藏
 */
class ScreenBorderGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ScreenBorderGlowView"

        // 优化后的配置 - 协调一致的光晕效果
        private const val DEFAULT_BORDER_WIDTH = 4f   // 边框宽度(dp) - 精致的主边框线
        private const val DEFAULT_GLOW_WIDTH = 10f     // 光晕扩散宽度(dp) - 柔和的外发光（2.5倍边框）
        private const val DEFAULT_ANIMATION_DURATION = 2000L  // 动画周期(ms)
        private const val DEFAULT_ALPHA = 0.75f        // 透明度 - 平衡的可见度
        private const val DEFAULT_INNER_GLOW_WIDTH = 24f  // 内发光渐变宽度(dp) - 协调的内渐变（6倍边框）

        // 状态颜色
        val COLOR_IDLE = Color.parseColor("#007AFF")      // 空闲 - 蓝色
        val COLOR_PROCESSING = Color.parseColor("#007AFF") // 处理中 - 蓝色
        val COLOR_RECORDING = Color.parseColor("#007AFF")  // 录音中 - 蓝色（与空闲状态相同）
        val COLOR_COMPLETED = Color.parseColor("#4CAF50")  // 完成 - 绿色
        val COLOR_ERROR = Color.parseColor("#F44336")      // 错误 - 红色
    }

    // 绘制相关
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val innerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var borderPath = Path()

    // 配置参数
    private var borderWidth = DEFAULT_BORDER_WIDTH * resources.displayMetrics.density
    private var glowWidth = DEFAULT_GLOW_WIDTH * resources.displayMetrics.density
    private var currentColor = COLOR_IDLE
    private var currentAlpha = DEFAULT_ALPHA

    // 内发光渐变宽度（从边缘向内）- 与边框宽度协调
    private var innerGlowWidth = DEFAULT_INNER_GLOW_WIDTH * resources.displayMetrics.density

    // 动画相关
    private var flowAnimator: ValueAnimator? = null
    private var flowProgress = 0f  // 0-1的流动进度
    private var isAnimating = false

    // 脉冲动画
    private var pulseAnimator: ValueAnimator? = null
    private var pulseScale = 1f

    // 流动光点的渐变着色器
    private var flowShader: LinearGradient? = null
    private var shaderMatrix = Matrix()

    // 四边渐变着色器
    private var topGradient: LinearGradient? = null
    private var bottomGradient: LinearGradient? = null
    private var leftGradient: LinearGradient? = null
    private var rightGradient: LinearGradient? = null

    init {
        // 设置为不可点击，透传触摸事件
        isClickable = false
        isFocusable = false

        // Android 15+ 尝试使用硬件加速，如果 BlurMaskFilter 不支持再回退到软件渲染
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            // Android 15 上先尝试硬件加速
            setLayerType(LAYER_TYPE_HARDWARE, null)
        } else {
            // Android 14 及以下使用软件渲染以支持 BlurMaskFilter
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        // 默认隐藏边框光晕
        visibility = GONE

        // 初始化画笔
        setupPaints()
    }

    private fun setupPaints() {
        borderPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            color = currentColor
            alpha = (currentAlpha * 255).toInt()
            // Android 15+ 使用抗锯齿和更好的渲染质量
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                isAntiAlias = true
                isDither = true
            }
        }

        glowPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = glowWidth
            color = currentColor
            // 外发光透明度调整为0.5倍，使光晕更柔和协调
            alpha = (currentAlpha * 0.5f * 255).toInt()
            // Android 15+ 如果硬件加速不支持 BlurMaskFilter，会在绘制时回退
            // 先尝试设置，如果失败会在 onDraw 中处理
            try {
                // 使用更柔和的模糊半径，与边框宽度协调
                maskFilter = BlurMaskFilter(glowWidth * 0.8f, BlurMaskFilter.Blur.OUTER)
            } catch (e: Exception) {
                Log.w(TAG, "BlurMaskFilter 设置失败，将使用替代方案: ${e.message}")
                // 如果 BlurMaskFilter 不支持，回退到软件渲染
                if (android.os.Build.VERSION.SDK_INT >= 35) {
                    setLayerType(LAYER_TYPE_SOFTWARE, null)
                    maskFilter = BlurMaskFilter(glowWidth * 0.8f, BlurMaskFilter.Blur.OUTER)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                isAntiAlias = true
                isDither = true
            }
        }

        innerGlowPaint.apply {
            style = Paint.Style.FILL
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                isAntiAlias = true
                isDither = true
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d(TAG, "onSizeChanged: ${w}x${h}")
        updateBorderPath()
        updateFlowShader()
        updateEdgeGradients()
    }

    private fun updateBorderPath() {
        borderPath.reset()
        val halfBorder = borderWidth / 2
        borderPath.addRect(
            halfBorder,
            halfBorder,
            width - halfBorder,
            height - halfBorder,
            Path.Direction.CW
        )
    }

    private fun updateFlowShader() {
        if (width <= 0 || height <= 0) return

        // 创建渐变着色器，用于流动效果
        val perimeter = 2f * (width + height)
        val gradientColors = intArrayOf(
            Color.TRANSPARENT,
            currentColor,
            currentColor,
            Color.TRANSPARENT
        )
        val gradientPositions = floatArrayOf(0f, 0.1f, 0.2f, 0.3f)

        flowShader = LinearGradient(
            0f, 0f, perimeter, 0f,
            gradientColors, gradientPositions,
            Shader.TileMode.REPEAT
        )
    }

    /**
     * 更新四边渐变着色器
     */
    private fun updateEdgeGradients() {
        if (width <= 0 || height <= 0) return

        val colorWithAlpha = Color.argb(
            (currentAlpha * 255).toInt(),
            Color.red(currentColor),
            Color.green(currentColor),
            Color.blue(currentColor)
        )
        val transparent = Color.TRANSPARENT

        // 顶部渐变（从上往下，颜色到透明）
        topGradient = LinearGradient(
            0f, 0f, 0f, innerGlowWidth,
            colorWithAlpha, transparent,
            Shader.TileMode.CLAMP
        )

        // 底部渐变（从下往上，颜色到透明）
        bottomGradient = LinearGradient(
            0f, height.toFloat(), 0f, height - innerGlowWidth,
            colorWithAlpha, transparent,
            Shader.TileMode.CLAMP
        )

        // 左侧渐变（从左往右，颜色到透明）
        leftGradient = LinearGradient(
            0f, 0f, innerGlowWidth, 0f,
            colorWithAlpha, transparent,
            Shader.TileMode.CLAMP
        )

        // 右侧渐变（从右往左，颜色到透明）
        rightGradient = LinearGradient(
            width.toFloat(), 0f, width - innerGlowWidth, 0f,
            colorWithAlpha, transparent,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        // 绘制四边的内发光渐变效果
        drawEdgeGradients(canvas)

        // 绘制边框线
        if (isAnimating && flowShader != null) {
            // 有流动动画时，绘制流动边框
            drawFlowingBorder(canvas)
        } else {
            // 静态边框
            canvas.drawPath(borderPath, borderPaint)
        }

        // 绘制外发光
        canvas.drawPath(borderPath, glowPaint)
    }

    /**
     * 绘制四边的内发光渐变效果
     */
    private fun drawEdgeGradients(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val glowW = innerGlowWidth * pulseScale

        // 绘制顶部渐变
        topGradient?.let {
            innerGlowPaint.shader = it
            canvas.drawRect(0f, 0f, w, glowW, innerGlowPaint)
        }

        // 绘制底部渐变
        bottomGradient?.let {
            innerGlowPaint.shader = it
            canvas.drawRect(0f, h - glowW, w, h, innerGlowPaint)
        }

        // 绘制左侧渐变
        leftGradient?.let {
            innerGlowPaint.shader = it
            canvas.drawRect(0f, 0f, glowW, h, innerGlowPaint)
        }

        // 绘制右侧渐变
        rightGradient?.let {
            innerGlowPaint.shader = it
            canvas.drawRect(w - glowW, 0f, w, h, innerGlowPaint)
        }
    }

    private fun drawFlowingBorder(canvas: Canvas) {
        val perimeter = 2f * (width + height)
        val offset = flowProgress * perimeter

        // 更新着色器矩阵实现流动效果
        shaderMatrix.reset()
        shaderMatrix.setTranslate(offset, 0f)
        flowShader?.setLocalMatrix(shaderMatrix)

        // 使用渐变着色器绘制
        val flowPaint = Paint(borderPaint).apply {
            shader = flowShader
            alpha = 255
            // 流动时边框稍微加粗，但保持协调（1.3倍而不是1.5倍）
            strokeWidth = borderWidth * 1.3f
        }

        canvas.drawPath(borderPath, flowPaint)
    }

    /**
     * 设置状态颜色
     */
    fun setStatusColor(color: Int) {
        if (currentColor != color) {
            currentColor = color
            setupPaints()
            updateFlowShader()
            updateEdgeGradients()  // 更新渐变颜色
            invalidate()
        }
    }

    /**
     * 设置透明度
     */
    fun setGlowAlpha(alpha: Float) {
        currentAlpha = alpha.coerceIn(0f, 1f)
        setupPaints()
        updateEdgeGradients()  // 更新渐变透明度
        invalidate()
    }

    /**
     * 开始流动动画
     */
    fun startFlowAnimation(duration: Long = DEFAULT_ANIMATION_DURATION) {
        if (isAnimating) return

        isAnimating = true
        flowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                flowProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 停止流动动画
     */
    fun stopFlowAnimation() {
        isAnimating = false
        flowAnimator?.cancel()
        flowAnimator = null
        flowProgress = 0f
        invalidate()
    }

    /**
     * 设置为空闲状态
     */
    fun setIdle() {
        setStatusColor(COLOR_IDLE)
        stopFlowAnimation()
        stopPulseAnimation()
        setGlowAlpha(DEFAULT_ALPHA)
        // 空闲状态隐藏边框光晕
        visibility = GONE
    }

    /**
     * 设置为处理中状态（带流动动画）- AI执行任务时最明显
     * 注意：此方法不会自动显示边框光晕，需要调用 showGlow() 来显示
     */
    fun setProcessing() {
        setStatusColor(COLOR_PROCESSING)
        startFlowAnimation()
        startPulseAnimation()  // 添加脉冲效果
        setGlowAlpha(0.8f)  // 适中的亮度，保持协调
    }

    /**
     * 显示边框光晕（用于路线开始和任务开始）
     */
    fun showGlow() {
        Log.d(TAG, "showGlow() 被调用，当前可见性: $visibility")
        visibility = VISIBLE
        // Android 15+ 需要强制刷新
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            invalidate()
            requestLayout()
            // 确保父 View 也刷新
            parent?.requestLayout()
            Log.d(TAG, "已强制刷新 View (Android 15+)")
        }
    }

    /**
     * 隐藏边框光晕
     */
    fun hideGlow() {
        visibility = GONE
    }

    /**
     * 开始脉冲动画（内发光呼吸效果）
     */
    private fun startPulseAnimation() {
        pulseAnimator?.cancel()
        // 优化脉冲幅度，从1.3倍调整为1.2倍，使动画更柔和协调
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.2f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                pulseScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 停止脉冲动画
     */
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        pulseScale = 1f
    }

    /**
     * 设置为录音中状态（带快速脉冲）
     */
    fun setRecording() {
        setStatusColor(COLOR_RECORDING)
        startFlowAnimation(1000L)  // 更快的流动
        startPulseAnimation()
        setGlowAlpha(0.8f)
        // 录音状态显示边框光晕
        visibility = VISIBLE
    }

    /**
     * 设置为完成状态
     */
    fun setCompleted() {
        setStatusColor(COLOR_COMPLETED)
        stopFlowAnimation()
        stopPulseAnimation()
        setGlowAlpha(0.7f)
        // 完成状态隐藏边框光晕
        visibility = GONE
    }

    /**
     * 设置为错误状态
     */
    fun setError() {
        setStatusColor(COLOR_ERROR)
        stopFlowAnimation()
        stopPulseAnimation()
        setGlowAlpha(0.8f)
        // 错误状态隐藏边框光晕
        visibility = GONE
    }

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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopFlowAnimation()
        stopPulseAnimation()
    }
}
