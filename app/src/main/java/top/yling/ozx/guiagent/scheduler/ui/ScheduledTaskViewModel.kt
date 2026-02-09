package top.yling.ozx.guiagent.scheduler.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.data.room.AppDatabase
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.TaskStatus
import top.yling.ozx.guiagent.data.room.entity.IntentType
import top.yling.ozx.guiagent.data.room.entity.TaskExecutionHistory
import top.yling.ozx.guiagent.data.room.repository.ScheduledTaskRepository
import top.yling.ozx.guiagent.scheduler.AutomationHandler
import top.yling.ozx.guiagent.scheduler.RepeatScheduleCalculator

/**
 * 定时任务管理 ViewModel
 *
 * @author shanwb
 * @date 2026/01/30
 */
class ScheduledTaskViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScheduledTaskRepository.getInstance(application)
    private val automationHandler = AutomationHandler(application, repository)
    private val historyDao = AppDatabase.getInstance(application).taskExecutionHistoryDao()
    private val taskDao = AppDatabase.getInstance(application).scheduledTaskDao()

    // 当前用户ID（从登录状态获取）
    private var currentUserId: String = ""

    // 当前选中的 Tab
    private val _selectedTab = MutableStateFlow(TaskTab.ALL)
    val selectedTab: StateFlow<TaskTab> = _selectedTab.asStateFlow()

    // 筛选状态
    private val _filterStatus = MutableStateFlow<TaskStatus?>(null)
    val filterStatus: StateFlow<TaskStatus?> = _filterStatus.asStateFlow()

    // 任务列表 - 使用 Flow 实时更新
    private val _allTasks = MutableStateFlow<List<ScheduledTask>>(emptyList())
    
    val tasks: StateFlow<List<ScheduledTask>> = combine(
        _allTasks,
        selectedTab,
        filterStatus
    ) { allTasks, tab, status ->
        allTasks
            .filter { task ->
                // Tab 筛选
                when (tab) {
                    TaskTab.ALL -> true
                    TaskTab.REMINDER -> task.intentType == IntentType.REMINDER.name
                    TaskTab.AUTOMATION -> task.intentType == IntentType.AUTOMATION.name
                }
            }
            .filter { task ->
                // 状态筛选
                status == null || task.status == status.name
            }
            .sortedBy { it.nextTriggerTimeMillis }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 提醒任务数量
    val reminderCount: StateFlow<Int> = _allTasks
        .map { tasks -> tasks.count { it.intentType == IntentType.REMINDER.name && it.status == TaskStatus.ACTIVE.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 自动化任务数量
    val automationCount: StateFlow<Int> = _allTasks
        .map { tasks -> tasks.count { it.intentType == IntentType.AUTOMATION.name && it.status == TaskStatus.ACTIVE.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 操作结果
    private val _operationResult = MutableSharedFlow<OperationResult>()
    val operationResult: SharedFlow<OperationResult> = _operationResult.asSharedFlow()

    /**
     * 初始化用户ID并加载数据
     */
    fun init(userId: String) {
        currentUserId = userId
        loadTasks()
    }

    /**
     * 加载任务列表
     */
    private fun loadTasks() {
        viewModelScope.launch {
            // 使用 Flow 监听数据库变化
            if (currentUserId.isNotEmpty()) {
                taskDao.getTasksByUserFlow(currentUserId).collect { tasks ->
                    _allTasks.value = tasks
                }
            } else {
                // 如果没有用户ID，加载所有任务
                taskDao.getAllTasksFlow().collect { tasks ->
                    _allTasks.value = tasks
                }
            }
        }
    }

    /**
     * 刷新任务列表
     */
    fun refresh() {
        loadTasks()
    }

    /**
     * 切换 Tab
     */
    fun selectTab(tab: TaskTab) {
        _selectedTab.value = tab
    }

    /**
     * 设置状态筛选
     */
    fun setStatusFilter(status: TaskStatus?) {
        _filterStatus.value = status
    }

    /**
     * 暂停任务
     */
    fun pauseTask(taskId: Long) {
        viewModelScope.launch {
            try {
                val task = repository.getById(taskId) ?: return@launch

                // 1. 取消 AlarmManager
                automationHandler.cancelTask(taskId)

                // 2. 更新状态
                repository.updateStatus(taskId, TaskStatus.PAUSED)

                _operationResult.emit(OperationResult.Success("任务已暂停"))
            } catch (e: Exception) {
                _operationResult.emit(OperationResult.Error("暂停失败: ${e.message}"))
            }
        }
    }

    /**
     * 恢复任务
     */
    fun resumeTask(taskId: Long) {
        viewModelScope.launch {
            try {
                val task = repository.getById(taskId) ?: return@launch

                // 1. 计算下次触发时间
                val nextTriggerTime = if (task.nextTriggerTimeMillis < System.currentTimeMillis()) {
                    // 如果已过期，重新计算
                    RepeatScheduleCalculator.calculateNextTriggerTime(task)
                } else {
                    task.nextTriggerTimeMillis
                }

                // 2. 更新数据库
                repository.updateNextTriggerTime(taskId, nextTriggerTime)
                repository.updateStatus(taskId, TaskStatus.ACTIVE)

                // 3. 重新设置 AlarmManager
                automationHandler.scheduleAutomation(task.copy(
                    nextTriggerTimeMillis = nextTriggerTime,
                    status = TaskStatus.ACTIVE.name
                ))

                _operationResult.emit(OperationResult.Success("任务已恢复"))
            } catch (e: Exception) {
                _operationResult.emit(OperationResult.Error("恢复失败: ${e.message}"))
            }
        }
    }

    /**
     * 删除任务
     */
    fun deleteTask(taskId: Long) {
        viewModelScope.launch {
            try {
                // 1. 取消 AlarmManager
                automationHandler.cancelTask(taskId)

                // 2. 删除数据库记录
                repository.delete(taskId)

                _operationResult.emit(OperationResult.Success("任务已删除"))
            } catch (e: Exception) {
                _operationResult.emit(OperationResult.Error("删除失败: ${e.message}"))
            }
        }
    }

    /**
     * 获取任务执行历史
     */
    suspend fun getTaskHistory(taskId: Long): List<TaskExecutionHistory> {
        return historyDao.getRecentByTaskId(taskId, limit = 10)
    }
}

/**
 * Tab 类型
 */
enum class TaskTab {
    ALL,        // 全部
    REMINDER,   // 提醒任务
    AUTOMATION  // 自动化任务
}

/**
 * 操作结果
 */
sealed class OperationResult {
    data class Success(val message: String) : OperationResult()
    data class Error(val message: String) : OperationResult()
}

