package top.yling.ozx.guiagent.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import top.yling.ozx.guiagent.R
import kotlin.math.min
import kotlin.math.sin

/**
 * AI 状态指示器视图
 *
 * 设计理念：遵循乔布斯审美标准
 * - 极简主义：简洁的双环设计
 * - 流畅动画：自然的呼吸和旋转效果
 * - 精致细节：渐变色彩和柔和阴影
 * - 状态感知：通过动画传达AI的工作状态
 *
 * 状态：
 * - IDLE: 闲置状态，展示轻柔的呼吸动画（缩放+透明度）
 * - PROCESSING: 任务执行中，展示优雅的旋转动画
 * - LISTENING: 正在聆听，展示脉冲动画
 */
class AIStatusIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Status {
        IDLE,       // 闲置 - 呼吸动画
        PROCESSING, // 处理中 - 旋转动画
        LISTENING   // 聆听中 - 脉冲动画
    }

    // 当前状态
    private var currentStatus = Status.IDLE

    // 画笔
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)  // 外环发光层
    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 颜色
    private val primaryColor = ContextCompat.getColor(context, R.color.accent_purple)
    private val secondaryColor = ContextCompat.getColor(context, R.color.accent_indigo)
    private val glowColor = ContextCompat.getColor(context, R.color.accent_gold)
    private val whiteColor = Color.WHITE

    // 动画值
    private var breatheScale = 1f
    private var breatheAlpha = 1f
    private var rotationAngle = 0f
    private var pulseScale = 1f
    private var innerRotation = 0f
    private var glowIntensity = 0.3f

    // 动画器
    private var breatheAnimatorSet: AnimatorSet? = null
    private var rotationAnimator: ObjectAnimator? = null
    private var innerRotationAnimator: ObjectAnimator? = null
    private var pulseAnimatorSet: AnimatorSet? = null
    private var glowAnimator: ObjectAnimator? = null

    // 尺寸
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f

    init {
        // 设置图层类型以支持阴影
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        // 初始化画笔
        setupPaints()

        // 启动默认的呼吸动画
        post { startBreathingAnimation() }
    }

    private fun setupPaints() {
        // 外环画笔 - 白色描边（增强亮度）
        outerRingPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f  // 增粗描边
            color = whiteColor
            alpha = 255  // 满透明度
        }

        // 外环发光层 - 营造光晕效果
        outerGlowPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 12f  // 较宽的发光带
            color = whiteColor
            alpha = 60  // 半透明
        }

        // 内环画笔 - 渐变描边（增粗）
        innerRingPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f  // 增粗
        }

        // 中心光晕画笔
        centerGlowPaint.apply {
            style = Paint.Style.FILL
        }

        // 装饰点画笔（增强亮度）
        dotPaint.apply {
            style = Paint.Style.FILL
            color = whiteColor
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) / 2f * 0.85f

        // 更新渐变
        updateGradients()
    }

    private fun updateGradients() {
        // 内环渐变
        innerRingPaint.shader = LinearGradient(
            centerX - baseRadius, centerY - baseRadius,
            centerX + baseRadius, centerY + baseRadius,
            primaryColor, secondaryColor,
            Shader.TileMode.CLAMP
        )

        // 中心光晕径向渐变
        centerGlowPaint.shader = RadialGradient(
            centerX, centerY, baseRadius * 0.6f,
            intArrayOf(
                Color.argb((glowIntensity * 80).toInt(), 251, 191, 36),  // 金色
                Color.argb((glowIntensity * 40).toInt(), 139, 92, 246),  // 紫色
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 保存画布状态
        canvas.save()

        // 应用呼吸缩放
        canvas.scale(breatheScale, breatheScale, centerX, centerY)

        // 绘制中心光晕
        drawCenterGlow(canvas)

        // 绘制外环
        drawOuterRing(canvas)

        // 绘制内环（带旋转）
        drawInnerRing(canvas)

        // 绘制装饰点
        drawDecorativeDots(canvas)

        // 恢复画布
        canvas.restore()
    }

    private fun drawCenterGlow(canvas: Canvas) {
        // 更新光晕渐变（增强亮度和饱和度）
        centerGlowPaint.shader = RadialGradient(
            centerX, centerY, baseRadius * 0.75f,
            intArrayOf(
                Color.argb((glowIntensity * 180).toInt().coerceAtMost(255), 251, 191, 36),  // 金色核心更亮
                Color.argb((glowIntensity * 120).toInt().coerceAtMost(255), 251, 191, 36),  // 金色扩散
                Color.argb((glowIntensity * 80).toInt().coerceAtMost(255), 139, 92, 246),   // 紫色过渡
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        canvas.drawCircle(centerX, centerY, baseRadius * 0.75f * pulseScale, centerGlowPaint)
    }

    private fun drawOuterRing(canvas: Canvas) {
        val radius = baseRadius * 0.9f

        // 先绘制外层发光（柔和的光晕效果）
        outerGlowPaint.alpha = ((40 + glowIntensity * 40) * breatheAlpha).toInt().coerceAtMost(255)
        canvas.drawCircle(centerX, centerY, radius, outerGlowPaint)

        // 再绘制主环（更亮的白色）
        outerRingPaint.alpha = (breatheAlpha * 255).toInt()
        outerRingPaint.setShadowLayer(6f, 0f, 0f, Color.argb(100, 255, 255, 255))  // 白色外发光
        canvas.drawCircle(centerX, centerY, radius, outerRingPaint)
    }

    private fun drawInnerRing(canvas: Canvas) {
        canvas.save()

        // 应用旋转（处理状态时）
        canvas.rotate(rotationAngle + innerRotation, centerX, centerY)

        innerRingPaint.alpha = (breatheAlpha * 220).toInt()

        val radius = baseRadius * 0.7f

        // 绘制弧形而非完整圆环，增加动感
        if (currentStatus == Status.PROCESSING) {
            // 处理中：绘制多段弧形
            val rectF = RectF(
                centerX - radius, centerY - radius,
                centerX + radius, centerY + radius
            )

            // 主弧
            innerRingPaint.strokeWidth = 4f
            canvas.drawArc(rectF, 0f, 270f, false, innerRingPaint)

            // 次弧（较细）
            innerRingPaint.strokeWidth = 2f
            innerRingPaint.alpha = (breatheAlpha * 150).toInt()
            canvas.drawArc(rectF, 300f, 45f, false, innerRingPaint)
        } else {
            // 闲置/聆听：绘制完整圆环
            innerRingPaint.strokeWidth = 3f
            canvas.drawCircle(centerX, centerY, radius, innerRingPaint)
        }

        canvas.restore()
    }

    private fun drawDecorativeDots(canvas: Canvas) {
        // 在环周围绘制3个装饰点，随动画旋转
        val dotRadius = baseRadius * 0.065f  // 增大点的尺寸
        val orbitRadius = baseRadius * 0.92f  // 稍微向外移动

        for (i in 0..2) {
            val angle = Math.toRadians((rotationAngle + i * 120.0 + innerRotation * 0.5f).toDouble())
            val x = centerX + (orbitRadius * kotlin.math.cos(angle)).toFloat()
            val y = centerY + (orbitRadius * kotlin.math.sin(angle)).toFloat()

            // 根据位置调整透明度，产生闪烁效果（提高基础亮度）
            val alpha = (0.6f + 0.4f * sin(rotationAngle * 0.02f + i * 2f).toFloat().coerceIn(0f, 1f)) * breatheAlpha
            dotPaint.alpha = (alpha * 255).toInt()

            // 添加点的发光效果
            dotPaint.setShadowLayer(4f, 0f, 0f, Color.argb(150, 255, 255, 255))

            canvas.drawCircle(x, y, dotRadius * pulseScale, dotPaint)
        }
    }

    /**
     * 设置状态
     */
    fun setStatus(status: Status) {
        if (currentStatus == status) return

        currentStatus = status

        // 停止所有动画
        stopAllAnimations()

        // 根据状态启动相应动画
        when (status) {
            Status.IDLE -> startBreathingAnimation()
            Status.PROCESSING -> startProcessingAnimation()
            Status.LISTENING -> startListeningAnimation()
        }
    }

    /**
     * 获取当前状态
     */
    fun getStatus(): Status = currentStatus

    /**
     * 呼吸动画 - 闲置状态
     * 轻柔的缩放和透明度变化，模拟平静的呼吸
     */
    private fun startBreathingAnimation() {
        // 重置动画值
        breatheScale = 1f
        breatheAlpha = 1f
        rotationAngle = 0f
        pulseScale = 1f

        // 缩放动画：1.0 -> 0.96 -> 1.0
        val scaleAnimator = ObjectAnimator.ofFloat(this, "breatheScale", 1f, 0.96f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 透明度动画：1.0 -> 0.85 -> 1.0
        val alphaAnimator = ObjectAnimator.ofFloat(this, "breatheAlpha", 1f, 0.85f, 1f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 光晕强度动画（增强范围）
        val glowAnimator = ObjectAnimator.ofFloat(this, "glowIntensity", 0.4f, 0.8f, 0.4f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 缓慢的内环旋转（几乎察觉不到）
        innerRotationAnimator = ObjectAnimator.ofFloat(this, "innerRotation", 0f, 360f).apply {
            duration = 60000  // 60秒一圈，非常缓慢
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        breatheAnimatorSet = AnimatorSet().apply {
            playTogether(scaleAnimator, alphaAnimator, glowAnimator)
            start()
        }
    }

    /**
     * 处理动画 - 任务执行状态
     * 优雅的旋转，传达AI正在工作
     */
    private fun startProcessingAnimation() {
        // 重置部分动画值
        breatheScale = 1f
        breatheAlpha = 1f
        pulseScale = 1f

        // 主旋转动画
        rotationAnimator = ObjectAnimator.ofFloat(this, "rotationAngle", 0f, 360f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // 内环反向旋转（产生视觉层次）
        innerRotationAnimator = ObjectAnimator.ofFloat(this, "innerRotation", 0f, -360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // 轻微的脉冲
        val pulseAnimator = ObjectAnimator.ofFloat(this, "pulseScale", 1f, 1.08f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 光晕增强（更强的脉冲效果）
        val glowAnim = ObjectAnimator.ofFloat(this, "glowIntensity", 0.6f, 1.2f, 0.6f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        breatheAnimatorSet = AnimatorSet().apply {
            playTogether(pulseAnimator, glowAnim)
            start()
        }
    }

    /**
     * 聆听动画 - 语音输入状态
     * 活跃的脉冲，传达AI正在聆听
     */
    private fun startListeningAnimation() {
        // 重置动画值
        breatheScale = 1f
        breatheAlpha = 1f
        rotationAngle = 0f

        // 脉冲缩放
        val pulseScaleAnimator = ObjectAnimator.ofFloat(this, "pulseScale", 1f, 1.15f, 1f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 呼吸缩放
        val scaleAnimator = ObjectAnimator.ofFloat(this, "breatheScale", 1f, 1.03f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 光晕脉冲（更强烈）
        val glowAnim = ObjectAnimator.ofFloat(this, "glowIntensity", 0.7f, 1.4f, 0.7f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        // 缓慢旋转
        innerRotationAnimator = ObjectAnimator.ofFloat(this, "innerRotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        pulseAnimatorSet = AnimatorSet().apply {
            playTogether(pulseScaleAnimator, scaleAnimator, glowAnim)
            start()
        }
    }

    /**
     * 停止所有动画
     */
    private fun stopAllAnimations() {
        breatheAnimatorSet?.cancel()
        breatheAnimatorSet = null

        rotationAnimator?.cancel()
        rotationAnimator = null

        innerRotationAnimator?.cancel()
        innerRotationAnimator = null

        pulseAnimatorSet?.cancel()
        pulseAnimatorSet = null

        glowAnimator?.cancel()
        glowAnimator = null
    }

    // 动画属性的setter方法
    fun setBreatheScale(scale: Float) {
        breatheScale = scale
        invalidate()
    }

    fun setBreatheAlpha(alpha: Float) {
        breatheAlpha = alpha
        invalidate()
    }

    fun setRotationAngle(angle: Float) {
        rotationAngle = angle
        invalidate()
    }

    fun setPulseScale(scale: Float) {
        pulseScale = scale
        invalidate()
    }

    fun setInnerRotation(rotation: Float) {
        innerRotation = rotation
        invalidate()
    }

    fun setGlowIntensity(intensity: Float) {
        glowIntensity = intensity
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimations()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // 重新启动当前状态的动画
        when (currentStatus) {
            Status.IDLE -> startBreathingAnimation()
            Status.PROCESSING -> startProcessingAnimation()
            Status.LISTENING -> startListeningAnimation()
        }
    }
}
