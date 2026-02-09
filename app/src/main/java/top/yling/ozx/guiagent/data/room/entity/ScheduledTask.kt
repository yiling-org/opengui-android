package top.yling.ozx.guiagent.data.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 定时任务实体
 * 用于持久化定时任务信息
 *
 * @author shanwb
 * @date 2026/01/29
 */
@Entity(
    tableName = "scheduled_tasks",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["status"]),
        Index(value = ["nextTriggerTimeMillis"])
    ]
)
data class ScheduledTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 用户信息
    val userId: String,
    val clientId: String,

    // 任务内容
    val intentType: String,               // REMINDER / AUTOMATION
    val taskContent: String,              // 原始用户指令
    val message: String? = null,          // 提醒消息（仅提醒类）

    // 时间配置
    val repeatType: String,               // ONCE / DAILY / WEEKLY / WEEKDAYS / MONTHLY / CUSTOM
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String? = null,       // JSON: [1,2,3,4,5] 表示周一到周五
    val dayOfMonth: Int? = null,
    val cronExpression: String? = null,
    val nextTriggerTimeMillis: Long,

    // 状态管理
    val status: String = TaskStatus.ACTIVE.name,
    val lastExecuteTime: Long? = null,
    val lastExecuteResult: String? = null,
    val executeCount: Int = 0,
    val retryCount: Int = 0,
    val maxRetry: Int = 3,

    // 元数据
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isRepeating: Boolean
        get() = repeatType != RepeatType.ONCE.name
}

/**
 * 意图类型
 */
enum class IntentType {
    REMINDER,    // 提醒类
    AUTOMATION   // 自动化类
}

/**
 * 重复类型
 */
enum class RepeatType {
    ONCE,        // 单次
    DAILY,       // 每天
    WEEKLY,       // 每周
    WEEKDAYS,     // 工作日
    MONTHLY,      // 每月
    CUSTOM        // 自定义 cron
}

/**
 * 任务状态
 */
enum class TaskStatus {
    ACTIVE,          // 活跃
    PAUSED,          // 暂停
    RUNNING,         // 执行中
    RETRY_WAITING,   // 等待重试
    SUCCESS,         // 成功
    FAILED,          // 失败
    EXPIRED,         // 已过期
    CANCELLED        // 已取消
}
