package top.yling.ozx.guiagent.websocket.handler

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.websocket.CommandResult
import top.yling.ozx.guiagent.data.room.entity.IntentType
import top.yling.ozx.guiagent.data.room.entity.RepeatType
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.TaskStatus
import top.yling.ozx.guiagent.data.room.repository.ScheduledTaskRepository
import top.yling.ozx.guiagent.scheduler.AutomationHandler
import top.yling.ozx.guiagent.scheduler.ReminderHandler
import top.yling.ozx.guiagent.util.DeviceUtils
import top.yling.ozx.guiagent.util.TokenManager

/**
 * 定时任务处理器
 * 根据任务类型分发到 ReminderHandler 或 AutomationHandler
 *
 * @author shanwb
 * @date 2026/01/29
 */
class ScheduleTaskHandler : ActionHandler {
    
    companion object {
        private const val TAG = "ScheduleTaskHandler"
    }
    
    override val actionName = "schedule_task"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val intentType = context.params?.intent_type
        val taskContent = context.params?.task_content
        val scheduleExpression = context.params?.schedule_expression
        val message = context.params?.message ?: taskContent

        // 解析时间信息
        val timeInfo = context.params?.time_info

        Log.i(TAG, "ScheduleTaskHandler received - intentType: $intentType, taskContent: $taskContent, scheduleExpression: $scheduleExpression")

        // 验证必需参数
        if (intentType == null || taskContent == null || scheduleExpression == null) {
            Log.e(TAG, "Missing required parameters")
            callback(CommandResult(
                success = false,
                message = "缺少必需参数: intent_type, task_content, schedule_expression"
            ))
            return
        }

        val timeInfoMap = timeInfo as? Map<*, *> ?: emptyMap<String, Any>()
        val repeatType = (timeInfoMap["repeat_type"] as? String) ?: RepeatType.ONCE.name
        val hour = (timeInfoMap["hour"] as? Number)?.toInt() ?: 9
        val minute = (timeInfoMap["minute"] as? Number)?.toInt() ?: 0
        val daysOfWeek = timeInfoMap["days_of_week"] as? String
        val nextTrigger = (timeInfoMap["next_trigger"] as? String) ?: ""

        // 获取用户信息
        val userId = TokenManager.getUsername(context.applicationContext) ?: "unknown"
        val clientId = DeviceUtils.getDeviceId(context.applicationContext)

        // 计算下次触发时间（毫秒）
        val nextTriggerTimeMillis = parseNextTriggerTime(nextTrigger)

        Log.i(TAG, "Parsed time info - repeatType: $repeatType, hour: $hour, minute: $minute, nextTrigger: $nextTrigger")

        // 构建任务实体
        val task = ScheduledTask(
            userId = userId,
            clientId = clientId,
            intentType = intentType.uppercase(),
            taskContent = taskContent,
            message = if (intentType.uppercase() == IntentType.REMINDER.name) message else null,
            repeatType = repeatType.uppercase(),
            hour = hour,
            minute = minute,
            daysOfWeek = daysOfWeek,
            nextTriggerTimeMillis = nextTriggerTimeMillis,
            status = TaskStatus.ACTIVE.name
        )

        // 异步处理
        context.coroutineScope.launch(Dispatchers.IO) {
            try {
                val repository = ScheduledTaskRepository.getInstance(context.applicationContext)

                when (intentType.uppercase()) {
                    IntentType.REMINDER.name -> {
                        // 提醒类任务：调用 ReminderHandler
                        val reminderHandler = ReminderHandler(context.applicationContext)
                        val result = reminderHandler.setReminder(task)

                        if (result.isSuccess) {
                            // 保存到数据库
                            repository.insert(task)
                            Log.i(TAG, "Reminder task created successfully: $taskContent")
                            callback(CommandResult(
                                success = true,
                                message = "已设置提醒: $taskContent, 触发时间: $nextTrigger"
                            ))
                        } else {
                            Log.e(TAG, "Failed to set reminder: ${result.exceptionOrNull()?.message}")
                            callback(CommandResult(
                                success = false,
                                message = "设置提醒失败: ${result.exceptionOrNull()?.message}"
                            ))
                        }
                    }
                    IntentType.AUTOMATION.name -> {
                        // 自动化类任务：调用 AutomationHandler
                        val automationHandler = AutomationHandler(context.applicationContext, repository)
                        val result = automationHandler.scheduleAutomation(task)

                        if (result.isSuccess) {
                            Log.i(TAG, "Automation task created successfully: $taskContent, taskId: ${result.getOrNull()}")
                            callback(CommandResult(
                                success = true,
                                message = "已设置自动化任务: $taskContent, 触发时间: $nextTrigger"
                            ))
                        } else {
                            Log.e(TAG, "Failed to schedule automation: ${result.exceptionOrNull()?.message}")
                            callback(CommandResult(
                                success = false,
                                message = "设置自动化任务失败: ${result.exceptionOrNull()?.message}"
                            ))
                        }
                    }
                    else -> {
                        Log.e(TAG, "Unsupported intent type: $intentType")
                        callback(CommandResult(
                            success = false,
                            message = "不支持的意图类型: $intentType"
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create scheduled task", e)
                callback(CommandResult(
                    success = false,
                    message = "创建定时任务失败: ${e.message}"
                ))
            }
        }
    }

    /**
     * 解析下次触发时间字符串为毫秒时间戳
     */
    private fun parseNextTriggerTime(timeString: String): Long {
        return try {
            // 格式: "yyyy-MM-dd HH:mm:ss"
            val parts = timeString.split(" ")
            if (parts.size == 2) {
                val dateParts = parts[0].split("-")
                val timeParts = parts[1].split(":")
                if (dateParts.size == 3 && timeParts.size >= 2) {
                    val calendar = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.YEAR, dateParts[0].toInt())
                        set(java.util.Calendar.MONTH, dateParts[1].toInt() - 1)
                        set(java.util.Calendar.DAY_OF_MONTH, dateParts[2].toInt())
                        set(java.util.Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                        set(java.util.Calendar.MINUTE, timeParts[1].toInt())
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    calendar.timeInMillis
                } else {
                    System.currentTimeMillis() + 60 * 60 * 1000 // 默认1小时后
                }
            } else {
                System.currentTimeMillis() + 60 * 60 * 1000 // 默认1小时后
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse time: $timeString", e)
            System.currentTimeMillis() + 60 * 60 * 1000 // 默认1小时后
        }
    }
}
