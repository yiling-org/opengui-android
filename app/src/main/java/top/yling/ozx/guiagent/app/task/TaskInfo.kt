package top.yling.ozx.guiagent.task

import top.yling.ozx.guiagent.StepInfo

/**
 * 任务状态枚举
 */
enum class TaskStatus {
    PENDING,     // 待处理（已创建，等待发送）
    RUNNING,     // 运行中（已发送，等待响应）
    PROCESSING,  // 处理中（收到agent_response）
    EXECUTING,   // 执行中（收到command指令）
    COMPLETED,   // 已完成
    FAILED,      // 失败
    CANCELLED,   // 已取消
    CANCELLING   // 取消中（已发送取消请求）
}

/**
 * 任务信息数据类
 * 用于跟踪整个任务生命周期
 * 
 * 线程安全说明：
 * - accumulatedData 使用 StringBuffer 保证线程安全
 * - WebSocket 回调可能在不同线程中执行，需要保证并发安全
 */
data class TaskInfo(
    val taskId: String,                      // 任务唯一ID
    val description: String,                 // 任务描述（用户语音内容）
    var status: TaskStatus = TaskStatus.PENDING,  // 当前状态
    val startTime: Long = System.currentTimeMillis(),  // 开始时间
    var endTime: Long? = null,               // 结束时间
    var currentStep: Int = 0,                // 当前步骤
    var totalSteps: Int = 0,                 // 总步骤数（预估）
    var progress: Int = 0,                   // 进度百分比 0-100
    var lastMessage: String = "",            // 最后的消息/动作
    var lastAction: String? = null,          // 最后执行的动作类型
    var result: String? = null,              // 任务结果
    var errorMessage: String? = null,        // 错误信息
    var cancelReason: String? = null,        // 取消原因
    var accumulatedData: StringBuffer = StringBuffer(),  // 拼接的agent_response data内容（线程安全）
    var taskProgress: String? = null,        // 任务进度（从 <task_progress> 标签提取）
    var steps: List<StepInfo>? = null        // 步骤列表
) {
    /**
     * 是否处于活跃状态（可被取消）
     * 注意：CANCELLING 也属于活跃状态，因为取消请求可能失败需要重试
     */
    fun isActive(): Boolean {
        return status in listOf(
            TaskStatus.PENDING,
            TaskStatus.RUNNING,
            TaskStatus.PROCESSING,
            TaskStatus.EXECUTING,
            TaskStatus.CANCELLING
        )
    }

    /**
     * 是否已终结
     */
    fun isTerminal(): Boolean {
        return status in listOf(
            TaskStatus.COMPLETED,
            TaskStatus.FAILED,
            TaskStatus.CANCELLED
        )
    }

    /**
     * 获取任务持续时间（毫秒）
     */
    fun getDuration(): Long {
        val end = endTime ?: System.currentTimeMillis()
        return end - startTime
    }

    /**
     * 复制并更新状态
     */
    fun withStatus(newStatus: TaskStatus): TaskInfo {
        return copy(status = newStatus).also {
            if (newStatus.isTerminal()) {
                it.endTime = System.currentTimeMillis()
            }
        }
    }

    private fun TaskStatus.isTerminal(): Boolean {
        return this in listOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED)
    }

    companion object {
        /**
         * 创建新任务
         */
        fun create(description: String): TaskInfo {
            return TaskInfo(
                taskId = java.util.UUID.randomUUID().toString(),
                description = description
            )
        }
    }
}
