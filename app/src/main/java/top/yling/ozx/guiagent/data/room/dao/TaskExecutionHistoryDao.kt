package top.yling.ozx.guiagent.data.room.dao

import androidx.room.*
import top.yling.ozx.guiagent.data.room.entity.TaskExecutionHistory

/**
 * 任务执行历史 DAO
 *
 * @author shanwb
 * @date 2026/01/29
 */
@Dao
interface TaskExecutionHistoryDao {

    @Insert
    suspend fun insert(history: TaskExecutionHistory): Long

    @Query("SELECT * FROM task_execution_history WHERE taskId = :taskId ORDER BY executedAt DESC LIMIT :limit")
    suspend fun getRecentByTaskId(taskId: Long, limit: Int = 10): List<TaskExecutionHistory>

    @Query("DELETE FROM task_execution_history WHERE executedAt < :before")
    suspend fun deleteOldHistory(before: Long)

    @Query("DELETE FROM task_execution_history WHERE taskId = :taskId AND id NOT IN (SELECT id FROM task_execution_history WHERE taskId = :taskId ORDER BY executedAt DESC LIMIT :keepCount)")
    suspend fun keepRecentHistory(taskId: Long, keepCount: Int = 10)
}