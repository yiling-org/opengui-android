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
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.TaskStatus
import top.yling.ozx.guiagent.data.room.repository.ScheduledTaskRepository
import top.yling.ozx.guiagent.websocket.WebSocketService
import top.yling.ozx.guiagent.util.DeviceUtils

/**
 * 自动化任务执行接收器
 * 在定时任务触发时执行对应的 Agent 操作
 *
 * @author shanwb
 * @date 2026/01/29
 */
class AutomationTaskReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AutomationTaskReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationHandler.ACTION_EXECUTE_TASK) return

        val taskId = intent.getLongExtra(AutomationHandler.EXTRA_TASK_ID, -1)
        if (taskId == -1L) return

        Log.i(TAG, "Automation task triggered - taskId: $taskId")

        // 使用 Coroutine 在后台执行
        CoroutineScope(Dispatchers.IO).launch {
            executeTask(context, taskId)
        }
    }

    private suspend fun executeTask(context: Context, taskId: Long) {
        val taskRepository = ScheduledTaskRepository.getInstance(context)
        val task = taskRepository.getById(taskId) ?: run {
            Log.e(TAG, "Task not found: $taskId")
            return
        }

        try {
            // 1. 更新状态为执行中
            taskRepository.updateStatus(taskId, TaskStatus.RUNNING)

            // 2. 检查网络和服务端连接
            val isConnected = checkConnection(context)
            if (!isConnected) {
                handleNetworkError(context, task)
                return
            }

            // 3. 通过 WebSocket 发送任务到服务端执行
            val success = sendTaskToServer(context, task)

            // 4. 更新执行结果
            if (success) {
                taskRepository.updateStatus(taskId, TaskStatus.SUCCESS)
                taskRepository.incrementExecuteCount(taskId)

                // 5. 如果是重复任务，设置下次触发
                if (task.isRepeating) {
                    scheduleNextExecution(context, task)
                }

                Log.i(TAG, "Automation task executed successfully: $taskId")
            } else {
                handleExecutionError(context, task, null)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute automation task: $taskId", e)
            handleExecutionError(context, task, e)
        }
    }

    /**
     * 检查 WebSocket 连接
     */
    private fun checkConnection(context: Context): Boolean {
        val service = WebSocketService.instance
        return service?.isConnected() ?: false
    }

    /**
     * 发送任务到服务端执行
     */
    private fun sendTaskToServer(context: Context, task: ScheduledTask): Boolean {
        val service = WebSocketService.instance ?: return false
        val deviceId = DeviceUtils.getDeviceId(context)

        var success = false
        val latch = java.util.concurrent.CountDownLatch(1)

        // 通过 WebSocket 发送自动化任务
        service.sendToAgent(
            text = task.taskContent,
            androidId = deviceId,
            onSuccess = {
                success = true
                latch.countDown()
            },
            onError = { error ->
                Log.e(TAG, "Failed to send task to server: $error")
                success = false
                latch.countDown()
            }
        )

        // 等待结果（最多30秒）
        try {
            latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for task result", e)
        }

        return success
    }

    /**
     * 网络异常处理：延迟重试
     */
    private suspend fun handleNetworkError(context: Context, task: ScheduledTask) {
        val taskRepository = ScheduledTaskRepository.getInstance(context)

        if (task.retryCount < task.maxRetry) {
            // 更新状态为等待重试
            taskRepository.updateStatus(task.id, TaskStatus.RETRY_WAITING)
            taskRepository.incrementRetryCount(task.id)

            // 5分钟后重试
            val retryTime = System.currentTimeMillis() + 5 * 60 * 1000
            scheduleRetry(context, task.id, retryTime)

            NotificationHelper.showNotification(
                context,
                "任务等待执行",
                "任务将在网络恢复后执行: ${task.taskContent}"
            )
            Log.i(TAG, "Task ${task.id} scheduled for retry at $retryTime")
        } else {
            // 超过最大重试次数
            taskRepository.updateStatus(task.id, TaskStatus.FAILED)
            taskRepository.updateLastExecuteResult(
                task.id,
                "网络不可用，已达最大重试次数"
            )

            NotificationHelper.showNotification(
                context,
                "任务执行失败",
                "网络不可用: ${task.taskContent}"
            )
            Log.w(TAG, "Task ${task.id} failed after max retries")
        }
    }

    /**
     * 执行错误处理
     */
    private suspend fun handleExecutionError(context: Context, task: ScheduledTask, error: Throwable?) {
        val taskRepository = ScheduledTaskRepository.getInstance(context)

        if (task.retryCount < task.maxRetry) {
            taskRepository.updateStatus(task.id, TaskStatus.RETRY_WAITING)
            taskRepository.incrementRetryCount(task.id)

            // 10分钟后重试
            val retryTime = System.currentTimeMillis() + 10 * 60 * 1000
            scheduleRetry(context, task.id, retryTime)

            Log.i(TAG, "Task ${task.id} scheduled for retry at $retryTime due to error: ${error?.message}")
        } else {
            taskRepository.updateStatus(task.id, TaskStatus.FAILED)
            taskRepository.updateLastExecuteResult(task.id, error?.message ?: "执行失败")

            NotificationHelper.showNotification(context, "任务执行失败", task.taskContent)
            Log.w(TAG, "Task ${task.id} failed: ${error?.message}")
        }
    }

    /**
     * 计算并设置下次执行时间（重复任务）
     */
    private suspend fun scheduleNextExecution(context: Context, task: ScheduledTask) {
        val nextTriggerTime = RepeatScheduleCalculator.calculateNextTriggerTime(task)
        if (nextTriggerTime <= 0) {
            Log.w(TAG, "Invalid next trigger time for task: ${task.id}")
            return
        }

        val taskRepository = ScheduledTaskRepository.getInstance(context)
        taskRepository.updateNextTriggerTime(task.id, nextTriggerTime)

        // 重新设置 AlarmManager
        val automationHandler = AutomationHandler(context, taskRepository)
        automationHandler.scheduleAutomation(task.copy(nextTriggerTimeMillis = nextTriggerTime))

        Log.i(TAG, "Scheduled next execution for task ${task.id} at $nextTriggerTime")
    }

    /**
     * 安排重试
     */
    private fun scheduleRetry(context: Context, taskId: Long, retryTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutomationTaskReceiver::class.java).apply {
            action = AutomationHandler.ACTION_EXECUTE_TASK
            putExtra(AutomationHandler.EXTRA_TASK_ID, taskId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            retryTime,
            pendingIntent
        )
    }
}
