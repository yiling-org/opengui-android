package top.yling.ozx.guiagent.scheduler

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import top.yling.ozx.guiagent.R

/**
 * 通知辅助类
 * 统一管理通知渠道和创建逻辑
 *
 * @author shanwb
 * @date 2026/01/29
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val CHANNEL_REMINDER = "reminder_channel"
    private const val CHANNEL_AUTOMATION = "automation_channel"

    /**
     * 创建通知渠道
     */
    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 提醒通知渠道（高优先级）
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDER,
                "提醒通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "定时提醒通知"
                enableVibration(true)
                setShowBadge(true)
            }

            // 自动化任务通知渠道
            val automationChannel = NotificationChannel(
                CHANNEL_AUTOMATION,
                "自动化任务",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "自动化任务执行状态通知"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(reminderChannel, automationChannel)
            )
            
            Log.i(TAG, "Notification channels created")
        }
    }

    /**
     * 显示通知（简化版）
     */
    fun showNotification(context: Context, title: String, message: String) {
        showNotification(context, CHANNEL_AUTOMATION, title, message, NotificationCompat.PRIORITY_DEFAULT)
    }

    /**
     * 显示通知
     */
    fun showNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        importance: Int = NotificationCompat.PRIORITY_DEFAULT
    ) {
        try {
            // 确保通知渠道已创建
            createChannels(context)

            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(importance)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            
            Log.i(TAG, "Notification shown: $title - $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show notification", e)
        }
    }

    /**
     * 显示提醒通知
     */
    fun showReminderNotification(context: Context, title: String, message: String) {
        showNotification(context, CHANNEL_REMINDER, title, message, NotificationCompat.PRIORITY_HIGH)
    }

    /**
     * 显示自动化任务通知
     */
    fun showAutomationNotification(context: Context, title: String, message: String) {
        showNotification(context, CHANNEL_AUTOMATION, title, message, NotificationCompat.PRIORITY_DEFAULT)
    }
}
