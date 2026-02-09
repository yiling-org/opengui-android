package top.yling.ozx.guiagent.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.RepeatType

/**
 * 提醒类处理器
 * 使用系统闹钟功能实现提醒
 *
 * @author shanwb
 * @date 2026/01/29
 */
class ReminderHandler(private val context: Context) {

    companion object {
        private const val TAG = "ReminderHandler"
    }

    /**
     * 设置提醒
     */
    fun setReminder(task: ScheduledTask): Result<Unit> {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_MESSAGE, task.message ?: task.taskContent)
                putExtra(AlarmClock.EXTRA_HOUR, task.hour)
                putExtra(AlarmClock.EXTRA_MINUTES, task.minute)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true) // 不跳转到闹钟界面

                // 重复设置
                if (task.repeatType == RepeatType.WEEKLY.name) {
                    val days = parseDaysOfWeek(task.daysOfWeek)
                    if (days.isNotEmpty()) {
                        putExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
                    }
                }
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Log.i(TAG, "Set reminder via system alarm clock: ${task.message ?: task.taskContent}")
                Result.success(Unit)
            } else {
                // 降级方案：使用 AlarmManager + 自定义通知
                Log.w(TAG, "System alarm clock not available, using fallback")
                fallbackReminder(task)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set reminder", e)
            Result.failure(e)
        }
    }

    /**
     * 解析星期列表
     * 输入格式: "1,2,3,4,5" 或 "[1,2,3,4,5]"
     * 返回: List<Int> 表示周几 (Calendar.SUNDAY=1 到 Calendar.SATURDAY=7)
     */
    private fun parseDaysOfWeek(daysOfWeek: String?): List<Int> {
        return try {
            if (daysOfWeek.isNullOrBlank()) return emptyList()
            
            // 移除方括号
            val cleaned = daysOfWeek.replace("[", "").replace("]", "")
            cleaned.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 1..7 }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse days of week: $daysOfWeek", e)
            emptyList()
        }
    }

    /**
     * 降级方案：当系统闹钟不可用时
     */
    private fun fallbackReminder(task: ScheduledTask): Result<Unit> {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("task_id", task.id)
                putExtra("message", task.message ?: task.taskContent)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                task.nextTriggerTimeMillis,
                pendingIntent
            )

            Log.i(TAG, "Set fallback reminder: ${task.message ?: task.taskContent}, trigger at: ${task.nextTriggerTimeMillis}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set fallback reminder", e)
            Result.failure(e)
        }
    }
}
