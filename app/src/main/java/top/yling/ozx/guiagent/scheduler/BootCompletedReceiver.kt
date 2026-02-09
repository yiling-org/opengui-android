package top.yling.ozx.guiagent.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.data.room.entity.TaskStatus
import top.yling.ozx.guiagent.data.room.repository.ScheduledTaskRepository

/**
 * 开机自启动接收器
 * 设备重启后恢复定时任务
 *
 * @author shanwb
 * @date 2026/01/29
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Device boot completed, restoring scheduled tasks...")

        CoroutineScope(Dispatchers.IO).launch {
            restoreAllTasks(context)
        }
    }

    private suspend fun restoreAllTasks(context: Context) {
        val taskRepository = ScheduledTaskRepository.getInstance(context)
        val automationHandler = AutomationHandler(context, taskRepository)

        // 获取所有活跃的自动化任务
        val activeTasks = taskRepository.getAllActiveAutomationTasks()

        var restoredCount = 0
        for (task in activeTasks) {
            // 检查任务是否已过期
            if (task.nextTriggerTimeMillis < System.currentTimeMillis()) {
                if (task.isRepeating) {
                    // 重复任务：计算下次触发时间
                    val nextTime = RepeatScheduleCalculator.calculateNextTriggerTime(task)
                    if (nextTime > 0) {
                        taskRepository.updateNextTriggerTime(task.id, nextTime)
                        automationHandler.scheduleAutomation(task.copy(nextTriggerTimeMillis = nextTime))
                        restoredCount++
                    }
                } else {
                    // 单次任务已过期：标记为过期
                    taskRepository.updateStatus(task.id, TaskStatus.EXPIRED)
                }
            } else {
                // 未过期：重新设置 AlarmManager
                automationHandler.scheduleAutomation(task)
                restoredCount++
            }
        }

        Log.i(TAG, "Restored $restoredCount scheduled tasks")
    }
}
