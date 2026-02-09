package top.yling.ozx.guiagent.data.room.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import top.yling.ozx.guiagent.data.room.AppDatabase
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.TaskStatus
import top.yling.ozx.guiagent.data.room.entity.TaskExecutionHistory

/**
 * 定时任务 Repository
 * 提供任务数据访问的统一接口
 *
 * @author shanwb
 * @date 2026/01/29
 */
class ScheduledTaskRepository private constructor(context: Context) {

    private val dao = AppDatabase.getInstance(context).scheduledTaskDao()
    private val historyDao = AppDatabase.getInstance(context).taskExecutionHistoryDao()

    suspend fun insert(task: ScheduledTask): Long = dao.insert(task)

    fun insertBlocking(task: ScheduledTask): Long = runBlocking { dao.insert(task) }

    suspend fun update(task: ScheduledTask) = dao.update(task)

    suspend fun getById(id: Long): ScheduledTask? = dao.getById(id)

    suspend fun getActiveTasksByUser(userId: String): List<ScheduledTask> =
        dao.getActiveTasksByUser(userId)

    /**
     * 获取用户任务列表（Flow，支持实时更新）
     */
    fun getTasksFlow(userId: String): Flow<List<ScheduledTask>> {
        return dao.getTasksByUserFlow(userId)
    }

    /**
     * 获取所有任务（Flow）
     */
    fun getAllTasksFlow(): Flow<List<ScheduledTask>> {
        return dao.getAllTasksFlow()
    }

    suspend fun getAllActiveAutomationTasks(): List<ScheduledTask> =
        dao.getAllActiveAutomationTasks()

    suspend fun getDueTasks(): List<ScheduledTask> =
        dao.getDueTasks(System.currentTimeMillis())

    suspend fun updateStatus(id: Long, status: TaskStatus) =
        dao.updateStatus(id, status.name)

    fun updateStatusBlocking(id: Long, status: TaskStatus) = runBlocking {
        dao.updateStatus(id, status.name)
    }

    suspend fun updateNextTriggerTime(id: Long, time: Long) =
        dao.updateNextTriggerTime(id, time)

    fun updateNextTriggerTimeBlocking(id: Long, time: Long) = runBlocking {
        dao.updateNextTriggerTime(id, time)
    }

    suspend fun incrementExecuteCount(id: Long) =
        dao.incrementExecuteCount(id)

    suspend fun incrementRetryCount(id: Long) =
        dao.incrementRetryCount(id)

    suspend fun updateLastExecuteResult(id: Long, result: String) =
        dao.updateLastExecuteResult(id, result)

    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * 获取任务执行历史
     */
    suspend fun getExecutionHistory(taskId: Long, limit: Int = 10): List<TaskExecutionHistory> {
        return historyDao.getRecentByTaskId(taskId, limit)
    }

    /**
     * 清理30天前的历史任务
     */
    suspend fun cleanupOldTasks() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.deleteOldTasks(thirtyDaysAgo)
    }

    companion object {
        @Volatile
        private var INSTANCE: ScheduledTaskRepository? = null

        fun getInstance(context: Context): ScheduledTaskRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ScheduledTaskRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
