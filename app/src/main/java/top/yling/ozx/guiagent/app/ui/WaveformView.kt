package top.yling.ozx.guiagent

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlin.math.sin
import kotlin.random.Random

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wave_primary)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val secondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wave_secondary)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wave_accent)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.wave_primary)
        style = Paint.Style.STROKE
        strokeWidth = 12f
        alpha = 20
        strokeCap = Paint.Cap.ROUND
    }
    
    // 复用Paint对象，避免每帧创建导致GC
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()
    private var isAnimating = false
    private var animator: ValueAnimator? = null
    private var fadeAnimator: ValueAnimator? = null
    private var phase = 0f
    private val waveCount = 3
    private val amplitudes = FloatArray(waveCount) { 0f }
    private val frequencies = FloatArray(waveCount) { (it + 1) * 0.02f }

    init {
        // 初始化随机振幅
        for (i in 0 until waveCount) {
            amplitudes[i] = Random.nextFloat() * 0.3f + 0.1f
        }
        
        // 设置无障碍支持
        setupAccessibility()
    }

    fun startAnimation() {
        if (isAnimating) return
        isAnimating = true
        
        // 通知无障碍服务状态变化
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        contentDescription = context.getString(R.string.accessibility_waveform) + ", 正在动画"

        animator = ValueAnimator.ofFloat(0f, Float.MAX_VALUE).apply {
            duration = Long.MAX_VALUE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                phase = (animation.animatedValue as Float) % 360f
                
                // 动态改变振幅以模拟音频变化
                for (i in 0 until waveCount) {
                    val target = Random.nextFloat() * 0.5f + 0.3f
                    amplitudes[i] += (target - amplitudes[i]) * 0.1f
                }
                
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        isAnimating = false
        animator?.cancel()
        animator = null
        
        // 通知无障碍服务状态变化
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        contentDescription = context.getString(R.string.accessibility_waveform) + ", 静止"
        
        // 取消之前的过渡动画
        fadeAnimator?.cancel()
        
        // 平滑过渡到静止状态
        fadeAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                for (i in 0 until waveCount) {
                    amplitudes[i] *= progress
                }
                invalidate()
            }
            start()
        }
    }
    
    fun cleanup() {
        animator?.cancel()
        animator = null
        fadeAnimator?.cancel()
        fadeAnimator = null
        isAnimating = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val centerY = height / 2
        
        if (!isAnimating && amplitudes.all { it < 0.01f }) {
            return
        }

        // 绘制中心粒子效果
        if (isAnimating) {
            drawParticles(canvas, width, centerY)
        }
    }

    private fun drawParticles(canvas: Canvas, width: Float, centerY: Float) {
        for (i in 0..12) {
            val x = (width / 12) * i + (sin((phase + i * 30) * Math.PI / 180) * 25).toFloat()
            val y = centerY + (sin((phase * 1.5 + i * 30) * Math.PI / 180) * 18).toFloat()
            val radius = 2f + (sin((phase * 2.5 + i * 30) * Math.PI / 180) * 1.5f).toFloat()
            
            particlePaint.color = when (i % 3) {
                0 -> ContextCompat.getColor(context, R.color.accent_purple)
                1 -> ContextCompat.getColor(context, R.color.accent_indigo)
                else -> ContextCompat.getColor(context, R.color.accent_gold)
            }
            
            particlePaint.alpha = (80 + sin((phase + i * 30) * Math.PI / 180) * 175).toInt()
            canvas.drawCircle(x, y, radius, particlePaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }
    
    /**
     * 设置无障碍支持
     */
    private fun setupAccessibility() {
        // 设置无障碍代理
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                
                // 添加状态描述
                val stateDescription = if (isAnimating) {
                    "正在显示语音波形"
                } else {
                    "静止"
                }
                info.stateDescription = stateDescription
                
                // 设置为装饰性视图（不能交互）
                info.className = "android.view.View"
            }
        })
        
        // 设置初始内容描述
        contentDescription = context.getString(R.string.accessibility_waveform) + ", 静止"
    }
}
