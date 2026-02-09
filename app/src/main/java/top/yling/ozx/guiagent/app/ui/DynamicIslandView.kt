package top.yling.ozx.guiagent.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.media.MediaPlayer
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.os.Handler
import android.os.Looper
import top.yling.ozx.guiagent.R
import top.yling.ozx.guiagent.StepInfo
import top.yling.ozx.guiagent.task.TaskInfo
import top.yling.ozx.guiagent.task.TaskManager
import top.yling.ozx.guiagent.task.TaskStatus

/**
 * 灵动岛View
 * 
 * 功能特点:
 * - 动态展开/收起动画（类似iOS Dynamic Island）
 * - 显示任务状态、进度、步骤信息
 * - 支持点击交互展开/收起
 * - 流畅的动画过渡效果
 * - 紧凑设计，适合状态栏区域显示
 */
class DynamicIslandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "DynamicIslandView"

        // 尺寸配置
        private const val COLLAPSED_WIDTH = 120f     // 收起状态宽度(dp)
        private const val COLLAPSED_HEIGHT = 36f     // 收起状态高度(dp)
        private const val EXPANDED_WIDTH = 360f      // 展开状态宽度(dp)
        private const val EXPANDED_HEIGHT = 200f     // 展开状态高度(dp) - 增加以容纳步骤列表
        private const val CORNER_RADIUS = 18f       // 圆角半径(dp)
        
        // 摄像头区域配置
        private const val CAMERA_WIDTH = 36f        // 摄像头宽度(dp)
        private const val CAMERA_HEIGHT = 16f       // 摄像头高度(dp) - 减少高度
        
        // 文字大小
        private const val TITLE_TEXT_SIZE = 13f     // 标题文字大小(sp)
        private const val SUBTITLE_TEXT_SIZE = 11f  // 副标题文字大小(sp)
        private const val PROGRESS_TEXT_SIZE = 10f  // 进度文字大小(sp)
        
        // 内边距
        private const val PADDING_HORIZONTAL = 16f   // 水平内边距(dp)
        private const val PADDING_VERTICAL = 12f     // 垂直内边距(dp)
        private const val ITEM_SPACING = 8f         // 项目间距(dp)
        
        // 动画时长
        private const val ANIMATION_DURATION = 300L  // 展开/收起动画时长(ms)
    }

    // 绘制相关
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val bgRectF = RectF()
    private val progressRectF = RectF()
    private val progressFillRectF = RectF()
    private val bgPath = Path()  // 用于绘制带缺口的背景
    
    // 配置参数
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    
    // 尺寸
    private val collapsedWidth = COLLAPSED_WIDTH * density
    private val collapsedHeight = COLLAPSED_HEIGHT * density
    private val expandedWidth = EXPANDED_WIDTH * density
    private val expandedHeight = EXPANDED_HEIGHT * density
    private val cornerRadius = CORNER_RADIUS * density
    
    // 当前尺寸（动画过程中会变化）
    private var currentWidth = collapsedWidth
    private var currentHeight = collapsedHeight
    
    // 状态
    private var isExpanded = false
    private var currentTask: TaskInfo? = null
    
    // 动画
    private var expandAnimator: ValueAnimator? = null
    
    // 状态颜色
    private val colorIdle = Color.parseColor("#007AFF")      // 空闲 - 蓝色
    private val colorRecording = Color.parseColor("#E91E63") // 录音中 - 红色
    private val colorProcessing = Color.parseColor("#FFA726")// 处理中 - 金色
    private val colorCompleted = Color.parseColor("#4CAF50") // 完成 - 绿色
    private val colorError = Color.parseColor("#F44336")     // 错误 - 红色
    
    // 脉冲动画
    private var pulseAnimator: ValueAnimator? = null
    private var pulseScale = 1f
    private var isPulsing = false

    // 完成提示音
    private var successSoundPlayer: MediaPlayer? = null
    private var lastTaskStatus: TaskStatus? = null

    // Handler 用于延迟操作
    private val handler = Handler(Looper.getMainLooper())

    // 完成后自动隐藏的延迟时间（毫秒）
    private val AUTO_HIDE_DELAY = 500L

    // ========== 律动波形动画 ==========
    // 波形配置
    private val waveBarCount = 5                    // 波形条数量
    private val waveBarWidth = 3f                   // 波形条宽度(dp)
    private val waveBarSpacing = 2.5f               // 波形条间距(dp)
    private val waveBarMinHeight = 3f               // 最小高度(dp)
    private val waveBarMaxHeight = 14f              // 最大高度(dp)

    // 波形动画状态
    private var waveAnimator: ValueAnimator? = null
    private var wavePhase = 0f                      // 波形相位 (0-2π)
    private val waveBarHeights = FloatArray(waveBarCount) { 0.5f }  // 每根波形条的高度比例 (0-1)
    private val waveBarPhaseOffsets = floatArrayOf(0f, 0.8f, 0.3f, 1.1f, 0.6f)  // 每根条的相位偏移，产生自然波动

    // 波形画笔
    private val waveBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    init {
        setupPaints()
        isClickable = true
        isFocusable = true
        // 默认隐藏，只有在有任务时才显示
        visibility = GONE
    }

    private fun setupPaints() {
        // 背景画笔 - 使用更深的背景，更融入系统UI
        bgPaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F0000000")  // 更不透明的黑色背景，更接近iOS
        }
        
        // 标题文字画笔
        titlePaint.apply {
            color = Color.WHITE
            textSize = TITLE_TEXT_SIZE * scaledDensity
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }
        
        // 副标题文字画笔
        subtitlePaint.apply {
            color = Color.WHITE  // 改为纯白色，确保可见
            textSize = SUBTITLE_TEXT_SIZE * scaledDensity
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        
        // 进度文字画笔
        progressPaint.apply {
            color = Color.WHITE
            textSize = PROGRESS_TEXT_SIZE * scaledDensity
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        
        // 状态点画笔
        dotPaint.apply {
            style = Paint.Style.FILL
            color = colorIdle
        }
        
        // 进度条背景
        progressBgPaint.apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#33FFFFFF")
        }
        
        // 进度条填充
        progressFillPaint.apply {
            style = Paint.Style.FILL
            color = colorIdle
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 如果是收起状态，使用固定宽度（左侧状态灯 + 摄像头 + 右侧波形）
        if (!isExpanded) {
            val dotRadius = 6f * density
            val dotWidth = dotRadius * 2
            val cameraWidth = CAMERA_WIDTH * density

            // 计算波形区域宽度
            val waveWidth = (waveBarWidth * waveBarCount + waveBarSpacing * (waveBarCount - 1)) * density

            // 计算总宽度：左侧内边距 + 圆点 + 间距 + 摄像头 + 间距 + 波形 + 右侧内边距
            val leftPadding = 14f * density
            val rightPadding = 12f * density
            val spacing = 8f * density
            val contentWidth = leftPadding + dotWidth + spacing + cameraWidth + spacing + waveWidth + rightPadding

            currentWidth = contentWidth.coerceAtLeast(COLLAPSED_WIDTH * density)
        }

        setMeasuredDimension(
            currentWidth.toInt(),
            currentHeight.toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制带摄像头缺口的背景
        drawBackgroundWithCameraCutout(canvas)
        
        if (isExpanded && currentTask != null) {
            drawExpanded(canvas)
        } else {
            drawCollapsed(canvas)
        }
    }
    
    /**
     * 绘制带摄像头缺口的背景（类似iPhone Dynamic Island）
     * 中间摄像头位置也有背景色，和两边一样
     * 左侧右边直角，右侧左边直角
     */
    private fun drawBackgroundWithCameraCutout(canvas: Canvas) {
        val cameraWidth = CAMERA_WIDTH * density
        val centerX = currentWidth / 2f
        
        // 计算左右两部分的宽度
        val leftWidth = centerX - cameraWidth / 2f
        val rightStartX = centerX + cameraWidth / 2f
        val rightWidth = currentWidth - rightStartX
        
        bgPath.reset()
        
        // 左侧部分（只保留左侧圆角，右边直角）
        if (leftWidth > 0) {
            bgRectF.set(0f, 0f, leftWidth, currentHeight)
            // 只绘制左侧圆角，右侧直角
            bgPath.addRoundRect(
                bgRectF,
                floatArrayOf(
                    cornerRadius, cornerRadius,  // 左上角
                    0f, 0f,                      // 右上角（直角）
                    0f, 0f,                      // 右下角（直角）
                    cornerRadius, cornerRadius   // 左下角
                ),
                Path.Direction.CW
            )
        }
        
        // 中间部分（摄像头位置，也有背景色，无圆角）
        val middleStartX = leftWidth
        val middleWidth = cameraWidth
        if (middleWidth > 0) {
            bgRectF.set(middleStartX, 0f, middleStartX + middleWidth, currentHeight)
            bgPath.addRect(bgRectF, Path.Direction.CW)
        }
        
        // 右侧部分（只保留右侧圆角，左边直角）
        if (rightWidth > 0) {
            bgRectF.set(rightStartX, 0f, currentWidth, currentHeight)
            // 只绘制右侧圆角，左侧直角
            bgPath.addRoundRect(
                bgRectF,
                floatArrayOf(
                    0f, 0f,                      // 左上角（直角）
                    cornerRadius, cornerRadius,  // 右上角
                    cornerRadius, cornerRadius,  // 右下角
                    0f, 0f                       // 左下角（直角）
                ),
                Path.Direction.CW
            )
        }
        
        // 绘制背景（整个区域都有背景色）
        canvas.drawPath(bgPath, bgPaint)
    }
    
    /**
     * 绘制收起状态（左侧状态灯 + 右侧律动波形）
     */
    private fun drawCollapsed(canvas: Canvas) {
        val paddingH = 14f * density
        val centerY = currentHeight / 2f
        val centerX = currentWidth / 2f
        val cameraWidth = CAMERA_WIDTH * density

        // 绘制状态点（带脉冲效果）- 放在左侧区域
        val dotRadius = 6f * density * pulseScale
        val dotX = paddingH + dotRadius
        val dotY = centerY

        // 确保圆点不超出左侧区域
        if (dotX + dotRadius < centerX - cameraWidth / 2f) {
            canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
        }

        // 绘制右侧律动波形
        drawWaveBars(canvas)
    }

    /**
     * 绘制律动波形条
     * 设计灵感来自 Apple Music 的音频可视化
     */
    private fun drawWaveBars(canvas: Canvas) {
        val centerX = currentWidth / 2f
        val centerY = currentHeight / 2f
        val cameraWidth = CAMERA_WIDTH * density
        val rightStartX = centerX + cameraWidth / 2f

        // 波形条参数
        val barWidthPx = waveBarWidth * density
        val barSpacingPx = waveBarSpacing * density
        val minHeightPx = waveBarMinHeight * density
        val maxHeightPx = waveBarMaxHeight * density

        // 计算波形区域总宽度
        val totalWaveWidth = barWidthPx * waveBarCount + barSpacingPx * (waveBarCount - 1)

        // 波形区域起始X位置（居中在右侧区域）
        val rightPadding = 12f * density
        val availableWidth = currentWidth - rightStartX - rightPadding
        val waveStartX = rightStartX + (availableWidth - totalWaveWidth) / 2f

        // 设置波形颜色（与状态灯同色，但稍微透明以增加层次感）
        waveBarPaint.color = dotPaint.color

        // 绘制每根波形条
        for (i in 0 until waveBarCount) {
            // 获取当前条的高度比例
            val heightRatio = waveBarHeights[i]
            val barHeight = minHeightPx + (maxHeightPx - minHeightPx) * heightRatio

            // 计算条的位置
            val barX = waveStartX + i * (barWidthPx + barSpacingPx)
            val barTop = centerY - barHeight / 2f
            val barBottom = centerY + barHeight / 2f

            // 绘制圆角矩形（圆角半径为宽度的一半，形成胶囊形状）
            val cornerRadius = barWidthPx / 2f
            canvas.drawRoundRect(
                barX, barTop, barX + barWidthPx, barBottom,
                cornerRadius, cornerRadius,
                waveBarPaint
            )
        }
    }
    
    /**
     * 绘制展开状态（显示完整任务信息）
     */
    private fun drawExpanded(canvas: Canvas) {
        val paddingH = PADDING_HORIZONTAL * density
        val paddingV = PADDING_VERTICAL * density
        val itemSpacing = ITEM_SPACING * density
        val centerX = currentWidth / 2f
        val cameraWidth = CAMERA_WIDTH * density
        
        // 计算左右可用区域
        val leftAvailableWidth = centerX - cameraWidth / 2f - paddingH
        val rightStartX = centerX + cameraWidth / 2f
        
        var y = paddingV
        
        // 标题行：状态点 + 任务描述 - 放在左侧区域
        val dotRadius = 6f * density * pulseScale
        val dotX = paddingH + dotRadius
        val dotY = paddingV + titlePaint.textSize / 2
        
        // 确保圆点不超出左侧区域
        if (dotX + dotRadius < centerX - cameraWidth / 2f) {
            // 绘制状态点
            canvas.drawCircle(dotX, dotY, dotRadius, dotPaint)
            
            // 绘制任务描述
            val titleX = dotX + dotRadius + 8f * density
            val titleY = y + titlePaint.textSize / 3
            val maxTitleWidth = centerX - cameraWidth / 2f - titleX - paddingH
            val description = currentTask?.description?.take(20) ?: "AI就绪"
            
            // 如果文字太长，截断
            val titleText = if (titlePaint.measureText(description) > maxTitleWidth) {
                var charCount = 0
                var textWidth = 0f
                val ellipsis = "..."
                val ellipsisWidth = titlePaint.measureText(ellipsis)
                val availableWidth = maxTitleWidth - ellipsisWidth
                
                while (charCount < description.length && textWidth < availableWidth) {
                    val char = description[charCount]
                    val charWidth = titlePaint.measureText(char.toString())
                    if (textWidth + charWidth <= availableWidth) {
                        textWidth += charWidth
                        charCount++
                    } else {
                        break
                    }
                }
                if (charCount < description.length) {
                    description.substring(0, charCount) + ellipsis
                } else {
                    description
                }
            } else {
                description
            }
            
            canvas.drawText(titleText, titleX, titleY, titlePaint)
        }
        
        y += titlePaint.textSize + itemSpacing
        
        // 状态文字和其他内容 - 放在左侧区域
        currentTask?.let { task ->
            val statusText = getStatusText(task.status)
            val statusY = y + subtitlePaint.textSize / 3
            val maxStatusWidth = centerX - cameraWidth / 2f - paddingH
            val statusTextWidth = subtitlePaint.measureText(statusText)
            
            if (statusTextWidth <= maxStatusWidth) {
                canvas.drawText(statusText, paddingH, statusY, subtitlePaint)
            } else {
                // 文字太长，截断
                val ellipsis = "..."
                val ellipsisWidth = subtitlePaint.measureText(ellipsis)
                val availableWidth = maxStatusWidth - ellipsisWidth
                var charCount = 0
                var textWidth = 0f
                
                while (charCount < statusText.length && textWidth < availableWidth) {
                    val char = statusText[charCount]
                    val charWidth = subtitlePaint.measureText(char.toString())
                    if (textWidth + charWidth <= availableWidth) {
                        textWidth += charWidth
                        charCount++
                    } else {
                        break
                    }
                }
                val displayText = if (charCount < statusText.length) {
                    statusText.substring(0, charCount) + ellipsis
                } else {
                    statusText
                }
                canvas.drawText(displayText, paddingH, statusY, subtitlePaint)
            }
            
            y += subtitlePaint.textSize + itemSpacing
            
            // 如果有步骤列表，显示步骤信息
            val steps = task.steps
            if (!steps.isNullOrEmpty()) {
                // 计算已完成步骤数
                val completedCount = steps.count { 
                    val stepStatus = it.status?.uppercase() ?: ""
                    stepStatus == "COMPLETED" || stepStatus == "SUCCESS"
                }
                
                // 显示步骤进度
                val progressText = "$completedCount/${steps.size} 完成"
                val progressTextY = y + subtitlePaint.textSize / 3
                canvas.drawText(progressText, paddingH, progressTextY, subtitlePaint)
                
                y += subtitlePaint.textSize + itemSpacing * 0.5f
                
                // 显示步骤列表（最多显示3个步骤）
                val sortedSteps = steps.sortedBy { it.stepIndex ?: 0 }
                val maxStepsToShow = 3
                val stepsToShow = sortedSteps.take(maxStepsToShow)
                
                stepsToShow.forEachIndexed { index, step ->
                    val stepIndex = step.stepIndex ?: (index + 1)
                    val stepName = step.stepName ?: "未知步骤"
                    val stepStatus = step.status?.uppercase() ?: "UNKNOWN"
                    
                    // 步骤图标（使用简单的文本符号）
                    val iconText = when (stepStatus) {
                        "COMPLETED", "SUCCESS" -> "✓"
                        "RUNNING", "PROCESSING", "EXECUTING", "IN_PROGRESS" -> "○"
                        "FAILED", "ERROR" -> "✗"
                        else -> "○"
                    }
                    val iconColor = when (stepStatus) {
                        "COMPLETED", "SUCCESS" -> Color.parseColor("#4CAF50")
                        "RUNNING", "PROCESSING", "EXECUTING", "IN_PROGRESS" -> Color.parseColor("#FFA726")
                        "FAILED", "ERROR" -> Color.parseColor("#F44336")
                        else -> Color.parseColor("#9E9E9E")
                    }
                    
                    // 绘制步骤图标
                    val iconX = paddingH
                    val iconY = y + subtitlePaint.textSize / 3
                    val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = iconColor
                        textSize = SUBTITLE_TEXT_SIZE * scaledDensity
                        textAlign = Paint.Align.LEFT
                    }
                    canvas.drawText(iconText, iconX, iconY, iconPaint)
                    
                    // 绘制步骤序号和名称
                    val stepText = "$stepIndex. $stepName"
                    val stepTextX = iconX + 16f * density
                    val maxStepTextWidth = currentWidth - stepTextX - paddingH
                    val stepTextWidth = subtitlePaint.measureText(stepText)
                    
                    val displayStepText = if (stepTextWidth <= maxStepTextWidth) {
                        stepText
                    } else {
                        // 截断步骤名称
                        val ellipsis = "..."
                        val ellipsisWidth = subtitlePaint.measureText(ellipsis)
                        val availableWidth = maxStepTextWidth - ellipsisWidth
                        var charCount = 0
                        var textWidth = 0f
                        
                        while (charCount < stepText.length && textWidth < availableWidth) {
                            val char = stepText[charCount]
                            val charWidth = subtitlePaint.measureText(char.toString())
                            if (textWidth + charWidth <= availableWidth) {
                                textWidth += charWidth
                                charCount++
                            } else {
                                break
                            }
                        }
                        if (charCount < stepText.length) {
                            stepText.substring(0, charCount) + ellipsis
                        } else {
                            stepText
                        }
                    }
                    
                    // 设置步骤文字颜色
                    subtitlePaint.color = when (stepStatus) {
                        "COMPLETED", "SUCCESS" -> Color.WHITE
                        "RUNNING", "PROCESSING", "EXECUTING", "IN_PROGRESS" -> Color.parseColor("#FFA726")
                        "FAILED", "ERROR" -> Color.parseColor("#F44336")
                        else -> Color.parseColor("#9E9E9E")
                    }
                    
                    canvas.drawText(displayStepText, stepTextX, iconY, subtitlePaint)
                    
                    // 恢复文字颜色
                    subtitlePaint.color = Color.WHITE
                    
                    y += subtitlePaint.textSize + itemSpacing * 0.5f
                }
                
                // 如果还有更多步骤，显示提示
                if (sortedSteps.size > maxStepsToShow) {
                    val moreText = "...还有 ${sortedSteps.size - maxStepsToShow} 个步骤"
                    val moreTextY = y + subtitlePaint.textSize / 3
                    subtitlePaint.color = Color.parseColor("#9E9E9E")
                    canvas.drawText(moreText, paddingH, moreTextY, subtitlePaint)
                    subtitlePaint.color = Color.WHITE
                }
            } else if (task.totalSteps > 0 || task.progress > 0) {
                // 进度信息（没有步骤列表时显示）
                val progressText = if (task.totalSteps > 0) {
                    "步骤 ${task.currentStep}/${task.totalSteps}"
                } else {
                    "进度 ${task.progress}%"
                }
                val progressTextY = y + subtitlePaint.textSize / 3
                canvas.drawText(progressText, paddingH, progressTextY, subtitlePaint)
                
                y += subtitlePaint.textSize + itemSpacing
                
                // 绘制进度条 - 连续显示，包括摄像头位置
                val progressBarHeight = 4f * density
                val progressBarY = y
                val totalProgressBarWidth = currentWidth - paddingH * 2
                
                // 绘制完整的进度条背景（包括摄像头位置）
                progressRectF.set(
                    paddingH,
                    progressBarY,
                    paddingH + totalProgressBarWidth,
                    progressBarY + progressBarHeight
                )
                canvas.drawRoundRect(progressRectF, progressBarHeight / 2, progressBarHeight / 2, progressBgPaint)
                
                // 绘制进度填充
                val progress = if (task.totalSteps > 0) {
                    task.currentStep.toFloat() / task.totalSteps
                } else {
                    task.progress / 100f
                }
                val fillWidth = totalProgressBarWidth * progress.coerceIn(0f, 1f)
                
                // 绘制进度填充（连续显示，包括摄像头位置）
                if (fillWidth > 0) {
                    progressFillRectF.set(
                        paddingH,
                        progressBarY,
                        paddingH + fillWidth,
                        progressBarY + progressBarHeight
                    )
                    canvas.drawRoundRect(progressFillRectF, progressBarHeight / 2, progressBarHeight / 2, progressFillPaint)
                }
            } else if (!task.lastMessage.isNullOrEmpty()) {
                // 显示最后的消息 - 放在左侧区域
                val message = task.lastMessage.take(30)
                val messageY = y + subtitlePaint.textSize / 3
                val maxMessageWidth = centerX - cameraWidth / 2f - paddingH
                val messageWidth = subtitlePaint.measureText(message)
                
                if (messageWidth <= maxMessageWidth) {
                    canvas.drawText(message, paddingH, messageY, subtitlePaint)
                } else {
                    // 截断消息
                    val ellipsis = "..."
                    val ellipsisWidth = subtitlePaint.measureText(ellipsis)
                    val availableWidth = maxMessageWidth - ellipsisWidth
                    var charCount = 0
                    var textWidth = 0f
                    
                    while (charCount < message.length && textWidth < availableWidth) {
                        val char = message[charCount]
                        val charWidth = subtitlePaint.measureText(char.toString())
                        if (textWidth + charWidth <= availableWidth) {
                            textWidth += charWidth
                            charCount++
                        } else {
                            break
                        }
                    }
                    val displayText = if (charCount < message.length) {
                        message.substring(0, charCount) + ellipsis
                    } else {
                        message
                    }
                    canvas.drawText(displayText, paddingH, messageY, subtitlePaint)
                }
            }
        }
    }
    
    /**
     * 获取状态文字
     */
    private fun getStatusText(status: TaskStatus): String {
        return when (status) {
            TaskStatus.PENDING -> "等待中"
            TaskStatus.RUNNING -> "运行中"
            TaskStatus.PROCESSING -> "AI处理中"
            TaskStatus.EXECUTING -> "执行中"
            TaskStatus.COMPLETED -> "已完成"
            TaskStatus.FAILED -> "失败"
            TaskStatus.CANCELLED -> "已取消"
            TaskStatus.CANCELLING -> "取消中"
        }
    }
    
    /**
     * 获取状态颜色
     */
    private fun getStatusColor(status: TaskStatus): Int {
        return when (status) {
            TaskStatus.PENDING -> colorIdle
            TaskStatus.RUNNING -> colorProcessing
            TaskStatus.PROCESSING -> colorProcessing
            TaskStatus.EXECUTING -> colorProcessing
            TaskStatus.COMPLETED -> colorCompleted
            TaskStatus.FAILED -> colorError
            TaskStatus.CANCELLED -> colorError
            TaskStatus.CANCELLING -> colorError
        }
    }
    
    /**
     * 更新任务信息
     */
    fun updateTask(task: TaskInfo?) {
        Log.d(TAG, "updateTask: task=${task?.taskId}, status=${task?.status}, lastStatus=$lastTaskStatus")
        currentTask = task

        // 没有任务时隐藏，有任务时显示
        if (task == null) {
            Log.d(TAG, "updateTask: task is null, hiding")
            visibility = GONE
            // 如果正在展开，先收起
            if (isExpanded) {
                collapse()
            }
            // 停止所有动画
            stopWaveAnimation()
            stopPulseAnimation()
            lastTaskStatus = null
            return
        } else {
            visibility = VISIBLE
        }

        // 检测任务完成状态变化，播放提示音
        if (task.status == TaskStatus.COMPLETED && lastTaskStatus != TaskStatus.COMPLETED) {
            Log.i(TAG, "updateTask: 任务完成，播放成功音效")
            playSuccessSound()
        }
        lastTaskStatus = task.status

        // 更新颜色
        val color = getStatusColor(task.status)
        dotPaint.color = color
        progressFillPaint.color = color

        // 控制脉冲动画（左侧状态灯）
        if (task.status in listOf(
            TaskStatus.RUNNING,
            TaskStatus.PROCESSING,
            TaskStatus.EXECUTING
        )) {
            startPulseAnimation()
        } else {
            stopPulseAnimation()
        }

        // 控制波形动画（右侧律动条）
        updateWaveAnimation(task.status)

        invalidate()
    }

    /**
     * 播放完成提示音
     * 播放完成后自动隐藏灵动岛
     * 
     * 注意：只隐藏 UI，不清除任务状态
     * 任务状态保留在 TaskManager 中，用户可以通过其他方式查看历史任务
     */
    private fun playSuccessSound() {
        try {
            // 释放之前的播放器
            successSoundPlayer?.release()

            // 创建新的播放器并播放
            successSoundPlayer = MediaPlayer.create(context, R.raw.success)?.apply {
                setOnCompletionListener { mp ->
                    mp.release()
                    // 音效播放完成后，延迟一小段时间再隐藏灵动岛
                    handler.postDelayed({
                        // 只隐藏 UI，不清除任务状态
                        hideWithAnimation()
                    }, AUTO_HIDE_DELAY)
                }
                start()
            }

            // 如果音效创建失败（无音效文件），也要在延迟后隐藏
            if (successSoundPlayer == null) {
                handler.postDelayed({
                    hideWithAnimation()
                }, AUTO_HIDE_DELAY)
            }
        } catch (e: Exception) {
            // 播放失败时也要隐藏灵动岛
            e.printStackTrace()
            handler.postDelayed({
                hideWithAnimation()
            }, AUTO_HIDE_DELAY)
        }
    }
    
    /**
     * 带动画隐藏灵动岛
     * 只隐藏 UI，不清除任务状态
     */
    private fun hideWithAnimation() {
        // 如果正在展开，先收起再隐藏
        if (isExpanded) {
            collapse()
            handler.postDelayed({
                visibility = GONE
            }, ANIMATION_DURATION + 50)
        } else {
            visibility = GONE
        }
    }
    
    /**
     * 切换展开/收起状态
     */
    fun toggle() {
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }
    
    /**
     * 展开
     */
    fun expand() {
        if (isExpanded) return
        
        isExpanded = true
        startExpandAnimation(true)
    }
    
    /**
     * 收起
     */
    fun collapse() {
        if (!isExpanded) return
        
        isExpanded = false
        startExpandAnimation(false)
    }
    
    /**
     * 展开/收起动画
     */
    private fun startExpandAnimation(expand: Boolean) {
        expandAnimator?.cancel()
        
        val startWidth = currentWidth
        val startHeight = currentHeight
        val endWidth = if (expand) expandedWidth else collapsedWidth
        val endHeight = if (expand) expandedHeight else collapsedHeight
        
        expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = if (expand) {
                OvershootInterpolator(1.2f)
            } else {
                AccelerateDecelerateInterpolator()
            }
            
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                currentWidth = startWidth + (endWidth - startWidth) * progress
                currentHeight = startHeight + (endHeight - startHeight) * progress
                
                requestLayout()
                invalidate()
            }
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentWidth = endWidth
                    currentHeight = endHeight
                    requestLayout()
                    invalidate()
                }
            })
            
            start()
        }
    }
    
    /**
     * 开始脉冲动画
     */
    private fun startPulseAnimation() {
        if (isPulsing) return
        
        isPulsing = true
        pulseAnimator = ValueAnimator.ofFloat(1f, 1.3f, 1f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener {
                pulseScale = it.animatedValue as Float
                invalidate()
            }
            
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
        pulseScale = 1f
        invalidate()
    }

    // ========== 波形动画控制 ==========

    /**
     * 根据任务状态启动相应的波形动画
     * - 运行中：活跃的律动效果，波形条交替跳动
     * - 等待中：缓慢的呼吸效果
     * - 完成/失败/取消：静态波形
     */
    private fun updateWaveAnimation(status: TaskStatus) {
        when (status) {
            TaskStatus.RUNNING, TaskStatus.PROCESSING, TaskStatus.EXECUTING -> {
                startActiveWaveAnimation()
            }
            TaskStatus.PENDING -> {
                startBreathingWaveAnimation()
            }
            else -> {
                // 完成、失败、取消等状态：设置静态波形
                stopWaveAnimation()
                setStaticWavePattern(status)
            }
        }
    }

    /**
     * 启动活跃的波形动画（运行中状态）
     * 模拟音频频谱的跳动效果
     */
    private fun startActiveWaveAnimation() {
        waveAnimator?.cancel()

        waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 600  // 较快的动画周期，显得更活跃
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()

            addUpdateListener { animation ->
                wavePhase = animation.animatedValue as Float

                // 更新每根波形条的高度，使用不同相位产生波浪效果
                for (i in 0 until waveBarCount) {
                    val phaseOffset = waveBarPhaseOffsets[i]
                    // 使用正弦函数产生平滑的波动
                    // 添加额外的频率变化让动画更有层次感
                    val primaryWave = kotlin.math.sin((wavePhase + phaseOffset * Math.PI).toDouble())
                    val secondaryWave = kotlin.math.sin((wavePhase * 1.5 + phaseOffset * Math.PI * 0.7).toDouble()) * 0.3

                    // 高度范围：0.2 - 1.0（避免完全消失）
                    waveBarHeights[i] = (0.6f + (primaryWave + secondaryWave).toFloat() * 0.4f).coerceIn(0.2f, 1f)
                }

                invalidate()
            }

            start()
        }
    }

    /**
     * 启动呼吸式波形动画（等待状态）
     * 缓慢、平静的呼吸效果
     */
    private fun startBreathingWaveAnimation() {
        waveAnimator?.cancel()

        waveAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 2000  // 缓慢的呼吸周期
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()

            addUpdateListener { animation ->
                wavePhase = animation.animatedValue as Float

                // 所有波形条以相近的节奏呼吸，但有轻微的相位差
                for (i in 0 until waveBarCount) {
                    val phaseOffset = i * 0.15f  // 轻微的相位差产生波浪感
                    val breathValue = kotlin.math.sin((wavePhase + phaseOffset).toDouble())

                    // 高度范围：0.3 - 0.7（较小的波动幅度，显得平静）
                    waveBarHeights[i] = (0.5f + breathValue.toFloat() * 0.2f).coerceIn(0.3f, 0.7f)
                }

                invalidate()
            }

            start()
        }
    }

    /**
     * 停止波形动画
     */
    private fun stopWaveAnimation() {
        waveAnimator?.cancel()
        waveAnimator = null
    }

    /**
     * 设置静态波形图案（完成/失败/取消状态）
     */
    private fun setStaticWavePattern(status: TaskStatus) {
        when (status) {
            TaskStatus.COMPLETED -> {
                // 完成状态：中间高两边低的山形，象征成功
                waveBarHeights[0] = 0.3f
                waveBarHeights[1] = 0.6f
                waveBarHeights[2] = 0.9f  // 中间最高
                waveBarHeights[3] = 0.6f
                waveBarHeights[4] = 0.3f
            }
            TaskStatus.FAILED, TaskStatus.CANCELLED, TaskStatus.CANCELLING -> {
                // 失败/取消状态：扁平的低波形
                for (i in 0 until waveBarCount) {
                    waveBarHeights[i] = 0.25f
                }
            }
            else -> {
                // 默认：均匀的中等高度
                for (i in 0 until waveBarCount) {
                    waveBarHeights[i] = 0.5f
                }
            }
        }
        invalidate()
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
    
    override fun performClick(): Boolean {
        toggle()
        return super.performClick()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        expandAnimator?.cancel()
        stopPulseAnimation()
        stopWaveAnimation()
        // 清除所有待执行的延迟任务
        handler.removeCallbacksAndMessages(null)
        // 释放音频播放器
        successSoundPlayer?.release()
        successSoundPlayer = null
    }
}

