package top.yling.ozx.guiagent.scheduler

import java.util.Calendar
import top.yling.ozx.guiagent.data.room.entity.RepeatType
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask

/**
 * 重复任务时间计算器
 *
 * @author shanwb
 * @date 2026/01/29
 */
object RepeatScheduleCalculator {

    /**
     * 计算下次触发时间
     */
    fun calculateNextTriggerTime(task: ScheduledTask): Long {
        return when (task.repeatType) {
            RepeatType.ONCE.name -> {
                // 单次任务不需要计算下次
                -1L
            }
            RepeatType.DAILY.name -> {
                getNextDailyTrigger(task.hour, task.minute)
            }
            RepeatType.WEEKLY.name -> {
                getNextWeeklyTrigger(task.daysOfWeek, task.hour, task.minute)
            }
            RepeatType.WEEKDAYS.name -> {
                getNextWeekdayTrigger(task.hour, task.minute)
            }
            RepeatType.MONTHLY.name -> {
                getNextMonthlyTrigger(task.dayOfMonth, task.hour, task.minute)
            }
            RepeatType.CUSTOM.name -> {
                // 自定义 cron 表达式解析
                parseCronExpression(task.cronExpression)
            }
            else -> {
                // 默认当作每日任务处理
                getNextDailyTrigger(task.hour, task.minute)
            }
        }
    }

    /**
     * 每天触发：如果今天时间已过，设置为明天
     */
    private fun getNextDailyTrigger(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        return calendar.timeInMillis
    }

    /**
     * 每周触发：找到下一个匹配的星期
     */
    private fun getNextWeeklyTrigger(daysOfWeek: String?, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val today = calendar.get(Calendar.DAY_OF_WEEK)
        val now = System.currentTimeMillis()

        // 解析星期列表
        val targetDays = try {
            daysOfWeek?.replace("[", "")?.replace("]", "")
                ?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (targetDays.isEmpty()) {
            // 如果没有指定星期，默认明天
            return getNextDailyTrigger(hour, minute)
        }

        // 查找下一个匹配的星期
        for (i in 0..7) {
            val targetDay = (today + i - 1) % 7 + 1
            if (targetDays.contains(targetDay)) {
                val targetCalendar = calendar.clone() as Calendar
                targetCalendar.add(Calendar.DAY_OF_MONTH, i)
                if (targetCalendar.timeInMillis > now) {
                    return targetCalendar.timeInMillis
                }
            }
        }

        // 回到下周第一个匹配的日子
        val firstMatchDay = targetDays.minOrNull() ?: Calendar.MONDAY
        val daysUntilFirst = (firstMatchDay - today + 7) % 7 + 7
        calendar.add(Calendar.DAY_OF_MONTH, daysUntilFirst)

        return calendar.timeInMillis
    }

    /**
     * 每周触发（使用 List 作为参数）
     */
    private fun getNextWeeklyTrigger(daysOfWeek: List<Int>, hour: Int, minute: Int): Long {
        return getNextWeeklyTrigger(daysOfWeek.joinToString(","), hour, minute)
    }

    /**
     * 工作日触发（周一到周五）
     */
    private fun getNextWeekdayTrigger(hour: Int, minute: Int): Long {
        return getNextWeeklyTrigger(
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                   Calendar.THURSDAY, Calendar.FRIDAY),
            hour, minute
        )
    }

    /**
     * 每月触发
     */
    private fun getNextMonthlyTrigger(dayOfMonth: Int?, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, dayOfMonth ?: 1)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.MONTH, 1)
        }

        return calendar.timeInMillis
    }

    /**
     * 解析 cron 表达式（简化版）
     * TODO: 实现更完整的 cron 表达式解析
     */
    private fun parseCronExpression(cronExpression: String?): Long {
        // 简化实现：默认返回明天同一时间
        return System.currentTimeMillis() + 24 * 60 * 60 * 1000
    }
}
