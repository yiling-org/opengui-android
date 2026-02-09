package top.yling.ozx.guiagent.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 通知样式类型
 */
enum class NotificationStyle {
    DEFAULT,    // 默认样式
    BIG_TEXT,   // 大文本样式（对应 -S bigtext）
    INBOX,      // 收件箱样式（多行文本）
    BIG_PICTURE // 大图片样式
}

/**
 * 通知优先级
 */
enum class NotificationPriority {
    MIN,        // 最低优先级
    LOW,        // 低优先级
    DEFAULT,    // 默认优先级
    HIGH,       // 高优先级
    MAX         // 最高优先级
}

/**
 * 通知结果
 */
data class NotificationResult(
    val success: Boolean,       // 是否成功
    val notificationId: Int,    // 通知ID
    val message: String         // 结果信息
)

/**
 * 通知服务类
 * 
 * 提供通过 API 发送通知的功能，替代 adb shell cmd notification post 命令
 * 对应命令: adb shell cmd notification post -S bigtext tag "标题" "内容"
 */
class NotificationService(private val context: Context) {

    companion object {
        private const val TAG = "NotificationService"
        
        // 默认通知渠道
        const val CHANNEL_ID_DEFAULT = "default_channel"
        const val CHANNEL_NAME_DEFAULT = "默认通知"
        
        // 高优先级通知渠道
        const val CHANNEL_ID_HIGH = "high_priority_channel"
        const val CHANNEL_NAME_HIGH = "重要通知"
        
        // 低优先级通知渠道
        const val CHANNEL_ID_LOW = "low_priority_channel"
        const val CHANNEL_NAME_LOW = "普通通知"
        
        // 通知ID计数器
        private var notificationIdCounter = 1000
    }

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    init {
        // 创建通知渠道（Android 8.0+）
        createNotificationChannels()
    }

    /**
     * 发送大文本通知（对应 -S bigtext 样式）
     * 
     * @param tag 通知标签（用于标识和取消通知）
     * @param title 通知标题
     * @param content 通知内容
     * @param notificationId 通知ID（可选，默认自动生成）
     * @return 通知结果
     */
    fun postBigTextNotification(
        tag: String,
        title: String,
        content: String,
        notificationId: Int? = null
    ): NotificationResult {
        return postNotification(
            tag = tag,
            title = title,
            content = content,
            style = NotificationStyle.BIG_TEXT,
            notificationId = notificationId
        )
    }

    /**
     * 发送通知
     * 
     * @param tag 通知标签
     * @param title 通知标题
     * @param content 通知内容
     * @param style 通知样式
     * @param priority 通知优先级
     * @param notificationId 通知ID
     * @param autoCancel 点击后自动取消
     * @param ongoing 是否为持续通知
     * @return 通知结果
     */
    fun postNotification(
        tag: String,
        title: String,
        content: String,
        style: NotificationStyle = NotificationStyle.DEFAULT,
        priority: NotificationPriority = NotificationPriority.DEFAULT,
        notificationId: Int? = null,
        autoCancel: Boolean = true,
        ongoing: Boolean = false
    ): NotificationResult {
        
        if (title.isBlank() && content.isBlank()) {
            Log.e(TAG, "标题和内容不能同时为空")
            return NotificationResult(false, -1, "标题和内容不能同时为空")
        }

        val id = notificationId ?: generateNotificationId()
        val channelId = getChannelIdByPriority(priority)

        try {
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(mapPriority(priority))
                .setAutoCancel(autoCancel)
                .setOngoing(ongoing)

            // 设置通知样式
            when (style) {
                NotificationStyle.BIG_TEXT -> {
                    builder.setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(content)
                            .setBigContentTitle(title)
                    )
                }
                NotificationStyle.INBOX -> {
                    val inboxStyle = NotificationCompat.InboxStyle()
                        .setBigContentTitle(title)
                    content.lines().forEach { line ->
                        inboxStyle.addLine(line)
                    }
                    builder.setStyle(inboxStyle)
                }
                else -> {
                    // 默认样式，不做特殊处理
                }
            }

            val notification = builder.build()
            notificationManager.notify(tag, id, notification)

            Log.d(TAG, "发送通知成功: tag=$tag, id=$id, title=$title")
            return NotificationResult(true, id, "通知发送成功")
        } catch (e: Exception) {
            Log.e(TAG, "发送通知失败", e)
            return NotificationResult(false, id, "发送通知失败: ${e.message}")
        }
    }

    /**
     * 发送简单通知
     * 
     * @param title 通知标题
     * @param content 通知内容
     * @return 通知结果
     */
    fun postSimpleNotification(title: String, content: String): NotificationResult {
        return postNotification(
            tag = "simple_${System.currentTimeMillis()}",
            title = title,
            content = content
        )
    }

    /**
     * 发送高优先级通知（会弹出横幅）
     * 
     * @param tag 通知标签
     * @param title 通知标题
     * @param content 通知内容
     * @return 通知结果
     */
    fun postHighPriorityNotification(
        tag: String,
        title: String,
        content: String
    ): NotificationResult {
        return postNotification(
            tag = tag,
            title = title,
            content = content,
            style = NotificationStyle.BIG_TEXT,
            priority = NotificationPriority.HIGH
        )
    }

    /**
     * 发送多行文本通知（收件箱样式）
     * 
     * @param tag 通知标签
     * @param title 通知标题
     * @param lines 多行内容
     * @return 通知结果
     */
    fun postInboxNotification(
        tag: String,
        title: String,
        lines: List<String>
    ): NotificationResult {
        return postNotification(
            tag = tag,
            title = title,
            content = lines.joinToString("\n"),
            style = NotificationStyle.INBOX
        )
    }

    /**
     * 发送带进度的通知
     * 
     * @param tag 通知标签
     * @param title 通知标题
     * @param content 通知内容
     * @param progress 进度值 (0-100)
     * @param indeterminate 是否为不确定进度
     * @param notificationId 通知ID
     * @return 通知结果
     */
    fun postProgressNotification(
        tag: String,
        title: String,
        content: String,
        progress: Int,
        indeterminate: Boolean = false,
        notificationId: Int? = null
    ): NotificationResult {
        val id = notificationId ?: generateNotificationId()

        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setAutoCancel(false)

            if (indeterminate) {
                builder.setProgress(0, 0, true)
            } else {
                builder.setProgress(100, progress.coerceIn(0, 100), false)
            }

            val notification = builder.build()
            notificationManager.notify(tag, id, notification)

            Log.d(TAG, "发送进度通知: tag=$tag, progress=$progress")
            return NotificationResult(true, id, "进度通知发送成功")
        } catch (e: Exception) {
            Log.e(TAG, "发送进度通知失败", e)
            return NotificationResult(false, id, "发送进度通知失败: ${e.message}")
        }
    }

    /**
     * 更新进度通知
     * 
     * @param tag 通知标签
     * @param notificationId 通知ID
     * @param title 通知标题
     * @param content 通知内容
     * @param progress 新的进度值
     */
    fun updateProgressNotification(
        tag: String,
        notificationId: Int,
        title: String,
        content: String,
        progress: Int
    ): NotificationResult {
        return postProgressNotification(tag, title, content, progress, false, notificationId)
    }

    /**
     * 完成进度通知（移除进度条，改为普通通知）
     * 
     * @param tag 通知标签
     * @param notificationId 通知ID
     * @param title 完成标题
     * @param content 完成内容
     */
    fun completeProgressNotification(
        tag: String,
        notificationId: Int,
        title: String,
        content: String
    ): NotificationResult {
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID_DEFAULT)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOngoing(false)
                .setProgress(0, 0, false) // 移除进度条

            val notification = builder.build()
            notificationManager.notify(tag, notificationId, notification)

            Log.d(TAG, "完成进度通知: tag=$tag, id=$notificationId")
            return NotificationResult(true, notificationId, "进度通知已完成")
        } catch (e: Exception) {
            Log.e(TAG, "完成进度通知失败", e)
            return NotificationResult(false, notificationId, "完成进度通知失败: ${e.message}")
        }
    }

    /**
     * 取消通知
     * 
     * @param tag 通知标签
     * @param notificationId 通知ID
     */
    fun cancelNotification(tag: String, notificationId: Int) {
        try {
            notificationManager.cancel(tag, notificationId)
            Log.d(TAG, "取消通知: tag=$tag, id=$notificationId")
        } catch (e: Exception) {
            Log.e(TAG, "取消通知失败", e)
        }
    }

    /**
     * 取消所有通知
     */
    fun cancelAllNotifications() {
        try {
            notificationManager.cancelAll()
            Log.d(TAG, "取消所有通知")
        } catch (e: Exception) {
            Log.e(TAG, "取消所有通知失败", e)
        }
    }

    /**
     * 检查是否有通知权限
     * 
     * @return 是否有权限
     */
    fun hasNotificationPermission(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 获取活动的通知数量
     */
    fun getActiveNotificationCount(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications.size
        } else {
            -1 // 低版本不支持
        }
    }

    /**
     * 获取活动通知的标签列表
     */
    fun getActiveNotificationTags(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications.mapNotNull { it.tag }
        } else {
            emptyList()
        }
    }

    // ===== 私有方法 =====

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 默认渠道
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                CHANNEL_NAME_DEFAULT,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "应用默认通知渠道"
            }

            // 高优先级渠道
            val highChannel = NotificationChannel(
                CHANNEL_ID_HIGH,
                CHANNEL_NAME_HIGH,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "重要通知渠道"
                enableVibration(true)
            }

            // 低优先级渠道
            val lowChannel = NotificationChannel(
                CHANNEL_ID_LOW,
                CHANNEL_NAME_LOW,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "普通通知渠道"
            }

            notificationManager.createNotificationChannels(
                listOf(defaultChannel, highChannel, lowChannel)
            )
            
            Log.d(TAG, "通知渠道创建完成")
        }
    }

    /**
     * 生成通知ID
     */
    private fun generateNotificationId(): Int {
        return notificationIdCounter++
    }

    /**
     * 根据优先级获取渠道ID
     */
    private fun getChannelIdByPriority(priority: NotificationPriority): String {
        return when (priority) {
            NotificationPriority.HIGH, NotificationPriority.MAX -> CHANNEL_ID_HIGH
            NotificationPriority.LOW, NotificationPriority.MIN -> CHANNEL_ID_LOW
            else -> CHANNEL_ID_DEFAULT
        }
    }

    /**
     * 映射优先级到 NotificationCompat 常量
     */
    private fun mapPriority(priority: NotificationPriority): Int {
        return when (priority) {
            NotificationPriority.MIN -> NotificationCompat.PRIORITY_MIN
            NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
            NotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
            NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationPriority.MAX -> NotificationCompat.PRIORITY_MAX
        }
    }
}

