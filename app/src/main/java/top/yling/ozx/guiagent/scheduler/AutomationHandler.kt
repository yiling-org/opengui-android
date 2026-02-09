package top.yling.ozx.guiagent.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.repository.ScheduledTaskRepository

/**
 * 自动化类处理器
 * 使用 AlarmManager 实现自动化任务调度
 *
 * @author shanwb
 * @date 2026/01/29
 */
class AutomationHandler(
    private val context: Context,
    private val taskRepository: ScheduledTaskRepository
) {

    companion object {
        const val ACTION_EXECUTE_TASK = "top.yling.ozx.guiagent.ACTION_EXECUTE_TASK"
        const val EXTRA_TASK_ID = "task_id"
        private const val TAG = "AutomationHandler"
    }

    /**
     * 设置自动化任务
     */
    fun scheduleAutomation(task: ScheduledTask): Result<Long> {
        return try {
            // 1. 保存到数据库
            val taskId = taskRepository.insertBlocking(task)

            // 2. 设置 AlarmManager
            setAlarm(task.copy(id = taskId))

            Result.success(taskId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule automation task", e)
            Result.failure(e)
        }
    }

    /**
     * 设置精确闹钟
     */
    private fun setAlarm(task: ScheduledTask) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AutomationTaskReceiver::class.java).apply {
            action = ACTION_EXECUTE_TASK
            putExtra(EXTRA_TASK_ID, task.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用精确闹钟，穿透 Doze 模式
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            task.nextTriggerTimeMillis,
            pendingIntent
        )

        Log.i(TAG, "Scheduled automation task: ${task.id}, trigger at: ${task.nextTriggerTimeMillis}")
    }

    /**
     * 取消任务
     */
    fun cancelTask(taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AutomationTaskReceiver::class.java).apply {
            action = ACTION_EXECUTE_TASK
            putExtra(EXTRA_TASK_ID, taskId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Log.i(TAG, "Cancelled automation task: $taskId")
    }
}
