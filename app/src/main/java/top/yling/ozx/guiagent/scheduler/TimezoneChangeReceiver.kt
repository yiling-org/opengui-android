package top.yling.ozx.guiagent.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.data.room.repository.ScheduledTaskRepository

/**
 * 时区变化接收器
 * 时区变化时重新计算任务触发时间
 *
 * @author shanwb
 * @date 2026/01/29
 */
class TimezoneChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TimezoneChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return

        Log.i(TAG, "Timezone changed, recalculating task trigger times...")

        CoroutineScope(Dispatchers.IO).launch {
            recalculateAllTasks(context)
        }
    }

    private suspend fun recalculateAllTasks(context: Context) {
        val taskRepository = ScheduledTaskRepository.getInstance(context)
        val automationHandler = AutomationHandler(context, taskRepository)

        // 取消所有现有的 AlarmManager 任务
        val activeTasks = taskRepository.getAllActiveAutomationTasks()
        for (task in activeTasks) {
            cancelAlarm(context, task.id)
        }

        // 重新计算并设置
        for (task in activeTasks) {
            val newTriggerTime = RepeatScheduleCalculator.calculateNextTriggerTime(task)
            if (newTriggerTime > 0) {
                taskRepository.updateNextTriggerTime(task.id, newTriggerTime)
                automationHandler.scheduleAutomation(task.copy(nextTriggerTimeMillis = newTriggerTime))
            }
        }

        Log.i(TAG, "Recalculated ${activeTasks.size} tasks for new timezone")
    }

    private fun cancelAlarm(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutomationTaskReceiver::class.java).apply {
            action = AutomationHandler.ACTION_EXECUTE_TASK
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
    }
}
