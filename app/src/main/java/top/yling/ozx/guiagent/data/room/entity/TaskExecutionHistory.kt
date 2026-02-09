package top.yling.ozx.guiagent.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务执行历史记录
 * 保存最近 N 次执行记录，便于用户查看
 *
 * @author shanwb
 * @date 2026/01/29
 */
@Entity(
    tableName = "task_execution_history",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["executedAt"])
    ]
)
data class TaskExecutionHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val taskId: Long,

    // 执行结果
    val success: Boolean,
    val result: String? = null,
    val errorMessage: String? = null,

    // 执行时间
    val executedAt: Long = System.currentTimeMillis(),
    val durationMs: Long? = null
)
