package top.yling.ozx.guiagent.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import top.yling.ozx.guiagent.data.room.dao.ScheduledTaskDao
import top.yling.ozx.guiagent.data.room.dao.TaskExecutionHistoryDao
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.TaskExecutionHistory

/**
 * 应用数据库
 *
 * @author shanwb
 * @date 2026/01/29
 */
@Database(
    entities = [ScheduledTask::class, TaskExecutionHistory::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduledTaskDao(): ScheduledTaskDao
    abstract fun taskExecutionHistoryDao(): TaskExecutionHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mysmarter_scheduler.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
