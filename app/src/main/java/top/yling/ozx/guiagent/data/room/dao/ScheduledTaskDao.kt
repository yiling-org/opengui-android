package top.yling.ozx.guiagent.data.room.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask

/**
 * 定时任务 DAO
 *
 * @author shanwb
 * @date 2026/01/29
 */
@Dao
interface ScheduledTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTask): Long

    @Update
    suspend fun update(task: ScheduledTask)

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun getById(id: Long): ScheduledTask?

    @Query("SELECT * FROM scheduled_tasks WHERE userId = :userId AND status IN ('ACTIVE', 'PAUSED') ORDER BY createdAt DESC")
    suspend fun getActiveTasksByUser(userId: String): List<ScheduledTask>

    /**
     * 获取用户任务列表（Flow，支持实时更新）
     */
    @Query("SELECT * FROM scheduled_tasks WHERE userId = :userId ORDER BY nextTriggerTimeMillis ASC")
    fun getTasksByUserFlow(userId: String): Flow<List<ScheduledTask>>

    /**
     * 获取所有任务（Flow）
     */
    @Query("SELECT * FROM scheduled_tasks ORDER BY nextTriggerTimeMillis ASC")
    fun getAllTasksFlow(): Flow<List<ScheduledTask>>

    @Query("SELECT * FROM scheduled_tasks WHERE intentType = 'AUTOMATION' AND status = 'ACTIVE'")
    suspend fun getAllActiveAutomationTasks(): List<ScheduledTask>

    @Query("SELECT * FROM scheduled_tasks WHERE status = 'ACTIVE' AND nextTriggerTimeMillis <= :now")
    suspend fun getDueTasks(now: Long): List<ScheduledTask>

    @Query("UPDATE scheduled_tasks SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE scheduled_tasks SET nextTriggerTimeMillis = :time, updatedAt = :now WHERE id = :id")
    suspend fun updateNextTriggerTime(id: Long, time: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE scheduled_tasks SET executeCount = executeCount + 1, lastExecuteTime = :time, updatedAt = :time WHERE id = :id")
    suspend fun incrementExecuteCount(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE scheduled_tasks SET retryCount = retryCount + 1, updatedAt = :now WHERE id = :id")
    suspend fun incrementRetryCount(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE scheduled_tasks SET lastExecuteResult = :result, updatedAt = :now WHERE id = :id")
    suspend fun updateLastExecuteResult(id: Long, result: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM scheduled_tasks WHERE status IN ('SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED') AND createdAt < :before")
    suspend fun deleteOldTasks(before: Long)
}
