package top.yling.ozx.guiagent.task

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 任务管理器
 * 单例模式，管理整个应用的任务生命周期
 *
 * 核心职责：
 * 1. 创建和管理任务
 * 2. 根据WebSocket消息推断任务状态
 * 3. 提供任务取消能力
 * 4. 维护任务历史
 */
object TaskManager {
    private const val TAG = "TaskManager"

    // 当前活跃任务
    private val _currentTask = MutableStateFlow<TaskInfo?>(null)
    val currentTask: StateFlow<TaskInfo?> = _currentTask.asStateFlow()

    // 任务历史（最近10条）
    private val _taskHistory = MutableStateFlow<List<TaskInfo>>(emptyList())
    val taskHistory: StateFlow<List<TaskInfo>> = _taskHistory.asStateFlow()

    // 步骤计数器
    private var stepCount = 0

    // 任务状态变化监听器
    var onTaskStatusChanged: ((TaskInfo) -> Unit)? = null
    
    // 已处理的 life_assistant 标签位置记录（用于防止重复触发）
    private val processedLifeAssistantPositions = mutableSetOf<Pair<Int, Int>>()

    /**
     * 创建新任务
     * @param description 任务描述（用户输入的语音内容）
     * @return 新创建的taskId
     */
    fun createTask(description: String): String {
        // 如果有活跃任务，先结束它
        _currentTask.value?.let { existingTask ->
            if (existingTask.isActive()) {
                Log.w(TAG, "存在活跃任务 ${existingTask.taskId}，标记为取消")
                updateTaskStatus(TaskStatus.CANCELLED, "被新任务覆盖")
            }
        }

        // 重置步骤计数
        stepCount = 0
        
        // 清空已处理的 life_assistant 标签位置记录
        processedLifeAssistantPositions.clear()

        // 创建新任务
        val task = TaskInfo.create(description)
        _currentTask.value = task

        Log.i(TAG, "任务已创建: ${task.taskId}, 描述: $description")
        notifyStatusChanged(task)

        return task.taskId
    }

    /**
     * 获取当前任务ID
     */
    fun getCurrentTaskId(): String? = _currentTask.value?.taskId

    /**
     * 获取当前任务
     */
    fun getCurrentTask(): TaskInfo? = _currentTask.value

    /**
     * 任务已发送（更新为RUNNING状态）
     */
    fun onTaskSent() {
        updateCurrentTask { task ->
            task.copy(
                status = TaskStatus.RUNNING,
                accumulatedData = task.accumulatedData
            )
        }
        Log.i(TAG, "任务已发送: ${getCurrentTaskId()}")
    }

    /**
     * Agent响应结果
     * 包含可能提取到的 followup 问题、life_assistant 信息和 result 文本
     */
    data class AgentResponseResult(
        val followUpQuestion: FollowUpQuestion? = null,
        val lifeAssistant: LifeAssistantInfo? = null,
        val resultText: String? = null  // attempt_completion 标签内的 result 标签内容
    )

    /**
     * 收到 agent_response 消息
     * 推断：AI正在处理中
     * 
     * 注意：此方法只负责提取信息和更新中间状态，不会将任务标记为 COMPLETED
     * 真正的完成判断只在收到 task_completed 消息时（onAgentComplete）进行
     * 这避免了客户端提前判断完成导致 UI 提前隐藏的问题
     * 
     * @author shanwb - 修复灵动岛状态闪烁问题：黄→绿→黄→绿
     * 
     * @param content 本次收到的data内容
     * @param taskId 任务ID（用于后续发送followup问题时使用）
     * @return AgentResponseResult 包含可能提取到的 followup 问题和 life_assistant 信息
     */
    fun onAgentResponse(content: String, taskId: String? = null): AgentResponseResult {
        var followUpQuestion: FollowUpQuestion? = null
        var lifeAssistant: LifeAssistantInfo? = null
        var resultText: String? = null
        
        // 如果任务已经是终态，不再处理 agent_response（防止 task_completed 后的消息覆盖状态）
        // 这是修复灵动岛状态闪烁问题的关键：当 task_completed 和后续的 agent_response 消息
        // 几乎同时到达时，可能导致状态在 COMPLETED(绿色) 和 PROCESSING(黄色) 之间快速切换
        val currentTask = _currentTask.value
        if (currentTask != null && currentTask.isTerminal()) {
            Log.d(TAG, "任务已是终态(${currentTask.status})，忽略 agent_response 消息，防止状态闪烁")
            return AgentResponseResult(null, null, null)
        }
        
        updateCurrentTask { task ->
            // 双重检查：在 updateCurrentTask 内部再次检查终态
            // 因为在多线程环境下，状态可能在外部检查和内部更新之间发生变化
            if (task.isTerminal()) {
                Log.d(TAG, "updateCurrentTask 内部检测到任务已是终态(${task.status})，跳过状态更新")
                return@updateCurrentTask task  // 返回原任务，不做任何修改
            }
            // 拼接data内容
            task.accumulatedData.append(content)
            val accumulatedContent = task.accumulatedData.toString()
            
            // 检查是否包含attempt_completion标签（仅用于提取 result 内容，不触发状态变更）
            val attemptCompletion = extractAttemptCompletion(accumulatedContent)
            if (attemptCompletion != null) {
                // 仅记录日志和提取 result，不改变任务状态
                Log.i(TAG, "检测到 attempt_completion 标签（等待 task_completed 消息确认完成）")
                
                // 解析 attempt_completion 标签内的 result 标签
                resultText = extractResultFromAttemptCompletion(attemptCompletion)
                resultText?.let {
                    Log.i(TAG, "提取到 result 标签内容，长度: ${it.length}")
                }
            }
            
            // 检查是否包含ask_followup_question标签
            followUpQuestion = extractFollowUpQuestion(accumulatedContent, taskId)
            
            // 如果提取到 followup 问题，从 accumulatedData 中移除已处理的标签，避免重复触发
            if (followUpQuestion != null) {
                removeProcessedFollowUpQuestion(task.accumulatedData)
                Log.i(TAG, "已移除已处理的 followup 问题标签，避免重复触发")
            }
            
            // 检查是否包含life_assistant标签（只处理完整且未处理过的标签）
            lifeAssistant = extractLifeAssistant(accumulatedContent, taskId, task.accumulatedData)
            
            // 如果提取到 life_assistant，标签已在 extractLifeAssistant 中移除
            if (lifeAssistant != null) {
                Log.i(TAG, "已提取并移除 life_assistant 标签，避免重复触发")
            }

            // 提取 task_progress 标签内容（如果存在）
            val newTaskProgress = extractTaskProgress(accumulatedContent)
            val taskProgress = newTaskProgress ?: task.taskProgress
            
            // 如果 taskProgress 不为空，解析成步骤列表
            val parsedSteps = if (taskProgress != null) {
                parseTaskProgressToSteps(taskProgress)
            } else {
                task.steps
            }
            
            // 始终保持 PROCESSING 状态，等待 task_completed 消息确认完成
            task.copy(
                status = TaskStatus.PROCESSING,
                lastMessage = taskProgress ?: "",
                accumulatedData = task.accumulatedData,
                taskProgress = taskProgress,
                steps = parsedSteps,
                totalSteps = parsedSteps?.size ?: task.totalSteps
            )
        }
        
        return AgentResponseResult(followUpQuestion, lifeAssistant, resultText)
    }
    
    /**
     * 提取followup问题
     * 匹配格式：
     * <ask_followup_question>
     *     <question>问题内容</question>
     *     <task_progress>...</task_progress> (可选)
     * </ask_followup_question>
     */
    private fun extractFollowUpQuestion(content: String, taskId: String?): FollowUpQuestion? {
        Log.d(TAG, "提取followup问题，内容长度: ${content.length}")
        
        // 匹配 <ask_followup_question>...</ask_followup_question> 标签
        // 使用 DOT_MATCHES_ALL 选项以匹配换行符和空白字符
        val outerRegex = "<ask_followup_question>(.*?)</ask_followup_question>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val outerMatch = outerRegex.find(content)
        
        if (outerMatch == null) {
            Log.d(TAG, "未找到 ask_followup_question 标签")
            return null
        }
        
        // 提取标签内部内容
        val innerContent = outerMatch.groupValues[1]
        Log.d(TAG, "找到 ask_followup_question 标签，内部内容长度: ${innerContent.length}")
        
        // 从内部内容中提取 <question>...</question> 标签
        // 使用 DOT_MATCHES_ALL 选项以匹配换行符
        val questionRegex = "<question>(.*?)</question>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val questionMatch = questionRegex.find(innerContent)
        
        if (questionMatch == null) {
            Log.w(TAG, "在 ask_followup_question 标签中未找到 question 标签")
            return null
        }
        
        // 提取问题内容并去除首尾空白字符
        val question = questionMatch.groupValues[1].trim()
        
        if (question.isEmpty()) {
            Log.w(TAG, "提取到的问题为空")
            return null
        }
        
        Log.i(TAG, "提取到问题: $question")
        
        return FollowUpQuestion(question, taskId)
    }
    
    /**
     * 从 accumulatedData 中移除已处理的 followup 问题标签
     * 避免重复触发同一个问题
     */
    private fun removeProcessedFollowUpQuestion(accumulatedData: StringBuffer) {
        val content = accumulatedData.toString()
        
        // 匹配第一个 <ask_followup_question>...</ask_followup_question> 标签
        val regex = "<ask_followup_question>(.*?)</ask_followup_question>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(content)
        
        if (match != null) {
            // 找到匹配的标签，从 StringBuffer 中移除
            val startIndex = match.range.first
            val endIndex = match.range.last + 1
            
            accumulatedData.delete(startIndex, endIndex)
            Log.d(TAG, "已从 accumulatedData 中移除 followup 问题标签 (位置: $startIndex-$endIndex)")
        } else {
            Log.w(TAG, "未找到要移除的 followup 问题标签")
        }
    }
    
    /**
     * 提取life_assistant标签内容
     * 匹配格式：
     * <life_assistant>
     *     {"success":true,"action":"book_train",...}
     * </life_assistant>
     * 
     * 要求：
     * 1. 标签必须完整（有开始和结束标签）
     * 2. JSON 内容必须有效
     * 3. 同一个标签位置只能处理一次
     * 
     * @param content 累计内容
     * @param taskId 任务ID
     * @param accumulatedData StringBuffer，用于在提取后立即移除标签（线程安全）
     * @return 如果找到完整且有效的 life_assistant 标签，返回解析后的信息；否则返回 null
     */
    private fun extractLifeAssistant(content: String, taskId: String?, accumulatedData: StringBuffer): LifeAssistantInfo? {
        Log.d(TAG, "提取life_assistant标签，内容长度: ${content.length}")
        
        // 匹配所有 <life_assistant>...</life_assistant> 标签
        // 使用 DOT_MATCHES_ALL 选项以匹配换行符和空白字符
        val regex = "<life_assistant>(.*?)</life_assistant>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(content)
        
        // 查找第一个未处理过的完整标签
        for (match in matches) {
            val startIndex = match.range.first
            val endIndex = match.range.last + 1
            val position = startIndex to endIndex
            
            // 检查这个位置是否已经处理过
            if (processedLifeAssistantPositions.contains(position)) {
                Log.d(TAG, "life_assistant 标签位置 ($startIndex-$endIndex) 已处理过，跳过")
                continue
            }
            
            // 提取标签内部内容（JSON字符串）
            val jsonContent = match.groupValues[1].trim()
            
            if (jsonContent.isEmpty()) {
                Log.w(TAG, "life_assistant 标签内容为空，跳过")
                continue
            }
            
            // 验证 JSON 是否有效
            if (!isValidJson(jsonContent)) {
                Log.w(TAG, "life_assistant 标签内容不是有效的 JSON，跳过")
                continue
            }
            
            // 标记为已处理
            processedLifeAssistantPositions.add(position)
            
            // 立即从 accumulatedData 中移除标签，避免重复触发
            accumulatedData.delete(startIndex, endIndex)
            Log.i(TAG, "提取到完整的 life_assistant 标签，JSON长度: ${jsonContent.length}，已移除标签 (位置: $startIndex-$endIndex)")
            Log.i(TAG, "life_assistant 提取的完整内容:\n$jsonContent")
            
            return LifeAssistantInfo(jsonContent, taskId)
        }
        
        Log.d(TAG, "未找到未处理的完整 life_assistant 标签")
        return null
    }
    
    /**
     * 验证字符串是否为有效的 JSON
     */
    private fun isValidJson(jsonString: String): Boolean {
        return try {
            org.json.JSONObject(jsonString)
            true
        } catch (e: org.json.JSONException) {
            // 如果不是对象，尝试解析为数组
            try {
                org.json.JSONArray(jsonString)
                true
            } catch (e2: org.json.JSONException) {
                false
            }
        }
    }
    
    /**
     * FollowUp问题数据类
     */
    data class FollowUpQuestion(
        val question: String,
        val taskId: String?
    )
    
    /**
     * LifeAssistant信息数据类
     * 包含解析后的JSON内容
     */
    data class LifeAssistantInfo(
        val jsonContent: String,
        val taskId: String?
    )

    /**
     * 收到 command 消息（准备执行指令）
     * 推断：正在执行具体操作
     * 
     * @author shanwb - 添加终态检查，防止任务完成后的状态闪烁
     */
    fun onCommandReceived(action: String, reqId: String?) {
        // 如果任务已经是终态，不再处理（防止 task_completed 后的消息覆盖状态）
        val currentTask = _currentTask.value
        if (currentTask != null && currentTask.isTerminal()) {
            Log.d(TAG, "收到command但任务已是终态(${currentTask.status})，忽略状态更新")
            return
        }
        
        stepCount++
        updateCurrentTask { task ->
            // 双重检查：在 updateCurrentTask 内部再次检查终态
            if (task.isTerminal()) {
                Log.d(TAG, "updateCurrentTask 内部检测到任务已是终态(${task.status})，跳过状态更新")
                return@updateCurrentTask task
            }
            task.copy(
                status = TaskStatus.EXECUTING,
                currentStep = stepCount,
                lastAction = action,
                lastMessage = "执行: $action",
                accumulatedData = task.accumulatedData
            )
        }
        Log.d(TAG, "收到command: $action, 步骤: $stepCount")
    }

    /**
     * 指令执行完成（client_response已发送）
     */
    fun onCommandCompleted(action: String, success: Boolean, message: String?) {
        // 如果任务已经是终态，不再处理（防止 task_completed 后的消息覆盖状态）
        // @author shanwb - 修复灵动岛状态闪烁问题：黄→绿→黄→绿
        val currentTask = _currentTask.value
        if (currentTask != null && currentTask.isTerminal()) {
            Log.d(TAG, "指令完成但任务已是终态(${currentTask.status})，忽略状态更新")
            return
        }
        
        updateCurrentTask { task ->
            // 双重检查：在 updateCurrentTask 内部再次检查终态
            // 这是为了避免异步回调导致的竞态条件
            val newStatus = if (task.isTerminal()) {
                Log.d(TAG, "指令完成但任务已是终态(${task.status})，保留原状态")
                task.status
            } else {
                // 执行完成后回到PROCESSING状态，等待下一个指令或完成
                TaskStatus.PROCESSING
            }
            task.copy(
                lastMessage = if (success) "$action 完成" else "$action 失败: $message",
                status = newStatus,
                accumulatedData = task.accumulatedData
            )
        }
        Log.d(TAG, "指令完成: $action, 成功: $success")
    }

    /**
     * 收到 task_completed 消息（后端确认任务完成）
     * 这是任务完成的唯一可靠信号源
     * 
     * 设计说明：
     * - onAgentResponse 只负责提取信息，不会将任务标记为 COMPLETED
     * - 只有收到后端发送的 task_completed 消息时，才真正标记任务完成
     * - 这确保了客户端和后端状态的一致性
     */
    fun onAgentComplete(content: String) {
        val currentTask = _currentTask.value
        // 如果任务已经是终态，不再处理（防止重复处理）
        if (currentTask != null && currentTask.isTerminal()) {
            Log.i(TAG, "任务已是终态(${currentTask.status})，忽略 task_completed 消息")
            return
        }

        // 优先检查 accumulatedData 中是否包含完成标签
        val accumulatedContent = currentTask?.accumulatedData?.toString() ?: ""
        val isSuccessFromAccumulated = isCompletionContent(accumulatedContent)
        val isSuccess = isSuccessFromAccumulated || isCompletionContent(content)
        val isFailed = isFailureContent(content)

        when {
            isSuccess -> {
                updateCurrentTask { task ->
                    task.copy(
                        status = TaskStatus.COMPLETED,
                        result = extractResult(accumulatedContent.ifEmpty { content }),
                        endTime = System.currentTimeMillis(),
                        progress = 100,
                        accumulatedData = task.accumulatedData
                    )
                }
                moveCurrentTaskToHistory()
                Log.i(TAG, "任务完成: ${getCurrentTaskId()}")
            }
            isFailed -> {
                updateCurrentTask { task ->
                    task.copy(
                        status = TaskStatus.FAILED,
                        errorMessage = extractError(content),
                        endTime = System.currentTimeMillis(),
                        accumulatedData = task.accumulatedData
                    )
                }
                moveCurrentTaskToHistory()
                Log.w(TAG, "任务失败: ${getCurrentTaskId()}")
            }
            else -> {
                // 可能是中间状态，继续处理
                updateCurrentTask { task ->
                    task.copy(
                        status = TaskStatus.PROCESSING,
                        lastMessage = extractDisplayContent(content),
                        accumulatedData = task.accumulatedData
                    )
                }
            }
        }
    }

    /**
     * 请求取消当前任务
     * @param reason 取消原因
     * @return 如果有活跃任务返回taskId，否则返回null
     */
    fun requestCancel(reason: String): String? {
        val task = _currentTask.value
        if (task == null || !task.isActive()) {
            Log.w(TAG, "没有可取消的活跃任务")
            return null
        }

        updateCurrentTask { t ->
            t.copy(
                status = TaskStatus.CANCELLING,
                cancelReason = reason,
                accumulatedData = t.accumulatedData
            )
        }

        Log.i(TAG, "请求取消任务: ${task.taskId}, 原因: $reason")
        return task.taskId
    }

    /**
     * 任务取消已确认（收到后端确认）
     */
    fun onTaskCancelled() {
        updateCurrentTask { task ->
            task.copy(
                status = TaskStatus.CANCELLED,
                endTime = System.currentTimeMillis(),
                accumulatedData = task.accumulatedData
            )
        }
        moveCurrentTaskToHistory()
        Log.i(TAG, "任务取消已确认: ${getCurrentTaskId()}")
    }

    /**
     * 本地强制取消（不等待后端确认）
     */
    fun forceCancel(reason: String) {
        updateCurrentTask { task ->
            task.copy(
                status = TaskStatus.CANCELLED,
                cancelReason = reason,
                endTime = System.currentTimeMillis(),
                accumulatedData = task.accumulatedData
            )
        }
        moveCurrentTaskToHistory()
        Log.i(TAG, "任务强制取消: ${getCurrentTaskId()}, 原因: $reason")
    }

    /**
     * 任务出错
     */
    fun onTaskError(error: String) {
        updateCurrentTask { task ->
            task.copy(
                status = TaskStatus.FAILED,
                errorMessage = error,
                endTime = System.currentTimeMillis(),
                accumulatedData = task.accumulatedData
            )
        }
        moveCurrentTaskToHistory()
        Log.e(TAG, "任务出错: ${getCurrentTaskId()}, 错误: $error")
    }

    /**
     * 清除当前任务（恢复空闲状态）
     */
    fun clearCurrentTask() {
        _currentTask.value?.let { task ->
            if (task.isActive()) {
                Log.w(TAG, "清除活跃任务: ${task.taskId}")
            }
        }
        _currentTask.value = null
        stepCount = 0
    }

    /**
     * 更新任务状态（通用方法）
     */
    fun updateTaskStatus(status: TaskStatus, message: String? = null) {
        updateCurrentTask { task ->
            task.copy(
                status = status,
                lastMessage = message ?: task.lastMessage,
                accumulatedData = task.accumulatedData
            ).also {
                if (status in listOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
                    it.endTime = System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * 从 API 同步任务信息（从 TaskWithStepsDTO 同步）
     * 用于将从服务器获取的最新任务状态同步到 TaskManager
     */
    fun syncTaskFromApi(
        taskId: String,
        description: String,
        status: String,
        currentStep: Int? = null,
        totalSteps: Int? = null,
        progress: Int? = null,
        lastMessage: String? = null,
        lastAction: String? = null,
        result: String? = null,
        errorMessage: String? = null,
        cancelReason: String? = null,
        createdAt: Long? = null,
        updatedAt: Long? = null,
        endedAt: Long? = null,
        steps: List<top.yling.ozx.guiagent.StepInfo>? = null
    ) {
        // 将 API 状态字符串映射到 TaskStatus 枚举
        val taskStatus = when (status) {
            "PENDING" -> TaskStatus.PENDING
            "RUNNING" -> TaskStatus.RUNNING
            "PROCESSING" -> TaskStatus.PROCESSING
            "EXECUTING" -> TaskStatus.EXECUTING
            "COMPLETED" -> TaskStatus.COMPLETED
            "FAILED" -> TaskStatus.FAILED
            "CANCELLED" -> TaskStatus.CANCELLED
            "CANCELLING" -> TaskStatus.CANCELLING
            else -> {
                Log.w(TAG, "未知的任务状态: $status")
                TaskStatus.PENDING
            }
        }

        val currentTask = _currentTask.value

        if (currentTask == null || currentTask.taskId != taskId) {
            // 如果当前没有任务或任务ID不匹配
            if (currentTask != null && currentTask.isActive()) {
                // 如果当前有活跃任务但ID不匹配，先移动到历史记录
                Log.w(TAG, "检测到新任务 $taskId，当前活跃任务 ${currentTask.taskId} 将被替换")
                moveCurrentTaskToHistory()
            }
            
            // 清空已处理的 life_assistant 标签位置记录（新任务）
            processedLifeAssistantPositions.clear()
            
            // 创建新任务（使用服务器返回的 taskId）
            val newTask = TaskInfo(
                taskId = taskId,
                description = description,
                status = taskStatus,
                startTime = createdAt ?: System.currentTimeMillis(),
                endTime = endedAt,
                currentStep = currentStep ?: 0,
                totalSteps = totalSteps ?: 0,
                progress = progress ?: 0,
                lastMessage = lastMessage ?: "",
                lastAction = lastAction,
                result = result,
                errorMessage = errorMessage,
                cancelReason = cancelReason,
                steps = steps
            )
            _currentTask.value = newTask
            notifyStatusChanged(newTask)
            Log.i(TAG, "从 API 同步创建任务: $taskId, 状态: $taskStatus")
        } else {
            // 更新现有任务
            updateCurrentTask { task ->
                task.copy(
                    description = description, // 更新描述（可能服务器端有更新）
                    status = taskStatus,
                    currentStep = currentStep ?: task.currentStep,
                    totalSteps = totalSteps ?: task.totalSteps,
                    progress = progress ?: task.progress,
                    lastMessage = lastMessage ?: task.lastMessage,
                    lastAction = lastAction ?: task.lastAction,
                    result = result ?: task.result,
                    errorMessage = errorMessage ?: task.errorMessage,
                    cancelReason = cancelReason ?: task.cancelReason,
                    endTime = endedAt ?: task.endTime,
                    accumulatedData = task.accumulatedData,  // 保留原有的StringBuffer（线程安全）
                    steps = steps ?: task.steps  // 更新步骤列表
                )
            }
            Log.d(TAG, "从 API 同步更新任务: $taskId, 状态: $taskStatus, 步骤: $currentStep/$totalSteps, 进度: $progress%")
        }

        // 如果任务已终结，移动到历史记录
        if (taskStatus in listOf(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
            moveCurrentTaskToHistory()
        }
    }

    // ==================== 私有方法 ====================

    private fun updateCurrentTask(update: (TaskInfo) -> TaskInfo) {
        _currentTask.update { task ->
            task?.let {
                val updated = update(it)
                notifyStatusChanged(updated)
                updated
            }
        }
    }

    private fun notifyStatusChanged(task: TaskInfo) {
        onTaskStatusChanged?.invoke(task)
    }

    private fun moveCurrentTaskToHistory() {
        _currentTask.value?.let { task ->
            _taskHistory.update { history ->
                // 保留最近10条
                (listOf(task) + history).take(10)
            }
        }
        // 延迟清除当前任务，给UI时间显示最终状态
        // 实际清除由外部调用clearCurrentTask()
    }

    /**
     * 判断是否为完成内容
     */
    private fun isCompletionContent(content: String): Boolean {
        return content.contains("attempt_completion") ||
               content.contains("任务完成") ||
               content.contains("发送结果:") ||
               (content.contains("<chat>") && content.contains("</chat>"))
    }

    /**
     * 判断是否为失败内容
     */
    private fun isFailureContent(content: String): Boolean {
        return content.contains("任务失败") ||
               content.contains("无法完成") ||
               content.contains("error") && content.contains("failed")
    }

    /**
     * 提取 attempt_completion 标签内容
     * @return 如果找到 attempt_completion 标签，返回标签内容；否则返回 null
     */
    private fun extractAttemptCompletion(content: String): String? {
        val regex = "<attempt_completion>(.*?)</attempt_completion>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(content)
        return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }
    
    /**
     * 从 attempt_completion 标签内容中提取 result 标签内容
     * @param attemptCompletionContent attempt_completion 标签的内部内容
     * @return 如果找到 result 标签，返回标签内容；否则返回 null
     */
    private fun extractResultFromAttemptCompletion(attemptCompletionContent: String): String? {
        val regex = "<result>(.*?)</result>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(attemptCompletionContent)
        val result = match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        if (result != null) {
            Log.d(TAG, "从 attempt_completion 中提取到 result 标签，内容长度: ${result.length}")
        }
        return result
    }
    
    /**
     * 提取结果内容
     */
    private fun extractResult(content: String): String {
        // 提取 <attempt_completion>...</attempt_completion> 内容
        extractAttemptCompletion(content)?.let { return it }
        
        // 提取 <result>...</result> 内容
        val resultRegex = "<result>(.*?)</result>".toRegex(RegexOption.DOT_MATCHES_ALL)
        resultRegex.find(content)?.groupValues?.get(1)?.trim()?.let { return it }

        // 提取 <chat><message>...</message></chat> 内容
        val chatRegex = "<message>(.*?)</message>".toRegex(RegexOption.DOT_MATCHES_ALL)
        chatRegex.find(content)?.groupValues?.get(1)?.trim()?.let { return it }

        return "任务已完成"
    }
    
    /**
     * 提取 chat 标签中的所有 message，使用 \n 分割
     * @return 如果找到 chat 标签，返回所有 message 用 \n 连接的字符串；否则返回 null
     */
    fun extractChatMessages(content: String): String? {
        // 匹配 <chat>...</chat> 标签
        val chatOuterRegex = "<chat>(.*?)</chat>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val chatMatch = chatOuterRegex.find(content)
        
        if (chatMatch == null) {
            return null
        }
        
        // 提取标签内部内容
        val innerContent = chatMatch.groupValues[1]
        
        // 提取所有 <message>...</message> 标签
        val messageRegex = "<message>(.*?)</message>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val messages = messageRegex.findAll(innerContent)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()
        
        if (messages.isEmpty()) {
            return null
        }
        
        // 使用 \n 连接所有消息
        return messages.joinToString("\n")
    }

    /**
     * 提取错误内容
     */
    private fun extractError(content: String): String {
        val errorRegex = "<error>(.*?)</error>".toRegex(RegexOption.DOT_MATCHES_ALL)
        return errorRegex.find(content)?.groupValues?.get(1)?.trim() ?: "未知错误"
    }

    /**
     * 提取可显示的内容
     */
    private fun extractDisplayContent(content: String): String {
        // 提取thinking标签内容
        val thinkingRegex = "<thinking>(.*?)</thinking>".toRegex(RegexOption.DOT_MATCHES_ALL)
        thinkingRegex.find(content)?.groupValues?.get(1)?.trim()?.let {
            if (it.isNotEmpty()) return it.take(100)
        }

        // 去除XML标签后的内容
        val plainText = content.replace("<[^>]+>".toRegex(), "").trim()
        if (plainText.isNotEmpty()) {
            return plainText.take(100)
        }

        return "处理中..."
    }
    
    /**
     * 提取 task_progress 标签内容
     * 匹配格式：
     * <task_progress>
     * - [x] 查询美团应用的SKILL规则
     * - [x] 打开美团应用
     * - [x] 等待应用加载完成
     * - [ ] 准备询问用户搜索内容
     * </task_progress>
     * @return 如果找到 task_progress 标签，返回最新的标签内容（保留换行和格式）；否则返回 null
     */
    private fun extractTaskProgress(content: String): String? {
        // 匹配所有 <task_progress>...</task_progress> 标签
        // 使用 DOT_MATCHES_ALL 选项以匹配换行符和空白字符
        val regex = "<task_progress>(.*?)</task_progress>".toRegex(RegexOption.DOT_MATCHES_ALL)
        val matches = regex.findAll(content)
        
        // 获取最后一个匹配（最新的 task_progress）
        val lastMatch = matches.lastOrNull()
        
        if (lastMatch == null) {
            return null
        }
        
        // 提取标签内部内容并去除首尾空白字符（但保留内部格式）
        var progressContent = lastMatch.groupValues[1].trim()
        
        if (progressContent.isEmpty()) {
            return null
        }
        
        // 将转义的 \n 字符串转换为实际的换行符
        progressContent = progressContent.replace("\\n", "\n")
        
        Log.d(TAG, "提取到 task_progress 内容，长度: ${progressContent.length}")
        return progressContent
    }
    
    /**
     * 获取当前任务的最新 task_progress 内容
     * 从 accumulatedData 中提取最新的 <task_progress> 标签
     * @return 如果找到 task_progress 标签，返回标签内容；否则返回 null
     */
    fun getCurrentTaskProgress(): String? {
        val currentTask = _currentTask.value ?: return null
        val accumulatedContent = currentTask.accumulatedData.toString()
        return extractTaskProgress(accumulatedContent)
    }
    
    /**
     * 解析 taskProgress 字符串为步骤列表
     * 格式：
     * - [x] xxxx  (已完成)
     * - [] xxxx   (未完成)
     * @param taskProgress taskProgress 字符串内容
     * @return 解析后的步骤列表，如果解析失败返回 null
     */
    private fun parseTaskProgressToSteps(taskProgress: String): List<top.yling.ozx.guiagent.StepInfo>? {
        if (taskProgress.isBlank()) {
            return null
        }
        
        val lines = taskProgress.lines()
        val steps = mutableListOf<top.yling.ozx.guiagent.StepInfo>()
        var stepIndex = 1 // 步骤索引从1开始
        
        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                return@forEach
            }
            
            // 匹配格式：- [x] 或 - []
            val regex = """^-\s*\[([xX]?)\]\s*(.+)$""".toRegex()
            val match = regex.find(trimmedLine)
            
            if (match != null) {
                val isCompleted = match.groupValues[1].isNotEmpty() // [x] 或 [X] 表示已完成
                val stepName = match.groupValues[2].trim()
                
                if (stepName.isNotEmpty()) {
                    val stepInfo = top.yling.ozx.guiagent.StepInfo(
                        stepIndex = stepIndex,
                        stepName = stepName,
                        status = if (isCompleted) "COMPLETED" else "PENDING",
                        startTime = null,
                        endTime = null,
                        errorMsg = null
                    )
                    steps.add(stepInfo)
                    stepIndex++
                }
            } else {
                // 如果格式不匹配，尝试提取文本内容作为步骤名称
                val stepName = trimmedLine.removePrefix("-").trim()
                if (stepName.isNotEmpty()) {
                    val stepInfo = top.yling.ozx.guiagent.StepInfo(
                        stepIndex = stepIndex,
                        stepName = stepName,
                        status = "PENDING",
                        startTime = null,
                        endTime = null,
                        errorMsg = null
                    )
                    steps.add(stepInfo)
                    stepIndex++
                }
            }
        }
        
        return if (steps.isNotEmpty()) steps else null
    }

    // ==================== 回放相关方法 ====================

    // 当前回放的原始任务 ID（用于匹配服务端消息）
    private var currentReplayTaskId: String? = null

    /**
     * 开始回放任务
     * 创建一个临时任务用于灵动岛显示
     * @param originalTaskId 原任务 ID（用于匹配服务端消息）
     * @param description 任务描述
     */
    fun startReplay(originalTaskId: String, description: String) {
        // 如果有活跃任务，先结束它
        _currentTask.value?.let { existingTask ->
            if (existingTask.isActive()) {
                Log.w(TAG, "存在活跃任务 ${existingTask.taskId}，标记为取消")
                updateTaskStatus(TaskStatus.CANCELLED, "被回放任务覆盖")
            }
        }

        // 记录原始任务 ID
        currentReplayTaskId = originalTaskId
        
        // 清空已处理的 life_assistant 标签位置记录（新回放任务）
        processedLifeAssistantPositions.clear()

        // 创建回放任务（使用 replay_ 前缀的临时 ID）
        val replayTaskId = "replay_${System.currentTimeMillis()}"
        val task = TaskInfo(
            taskId = replayTaskId,
            description = "回放: $description",
            status = TaskStatus.RUNNING,
            lastMessage = "回放任务中..."
        )
        _currentTask.value = task

        Log.i(TAG, "回放任务已创建: $replayTaskId, 原任务ID: $originalTaskId")
        notifyStatusChanged(task)
    }

    /**
     * 回放进度更新
     * @param originalTaskId 原任务 ID
     * @param step 当前步骤
     * @param total 总步骤数
     * @param action 动作名称
     * @param success 是否成功
     */
    fun onReplayProgress(originalTaskId: String, step: Int, total: Int, action: String, success: Boolean) {
        // 检查是否是当前回放任务
        if (currentReplayTaskId != originalTaskId) {
            Log.w(TAG, "收到非当前回放任务的进度: $originalTaskId, 当前: $currentReplayTaskId")
            return
        }

        updateCurrentTask { task ->
            task.copy(
                currentStep = step,
                totalSteps = total,
                progress = if (total > 0) (step * 100 / total) else 0,
                lastMessage = "回放中 ($step/$total): $action",
                lastAction = action,
                accumulatedData = task.accumulatedData
            )
        }
        Log.d(TAG, "回放进度: $step/$total, 动作: $action, 成功: $success")
    }

    /**
     * 回放完成
     * @param originalTaskId 原任务 ID
     * @param success 是否成功
     * @param successCount 成功步骤数
     * @param failCount 失败步骤数
     * @param message 完成消息
     */
    fun onReplayComplete(originalTaskId: String, success: Boolean, successCount: Int, failCount: Int, message: String?) {
        // 检查是否是当前回放任务
        if (currentReplayTaskId != originalTaskId) {
            Log.w(TAG, "收到非当前回放任务的完成消息: $originalTaskId, 当前: $currentReplayTaskId")
            return
        }

        updateCurrentTask { task ->
            task.copy(
                status = if (success) TaskStatus.COMPLETED else TaskStatus.FAILED,
                result = message ?: "回放完成: $successCount 成功, $failCount 失败",
                endTime = System.currentTimeMillis(),
                progress = 100,
                accumulatedData = task.accumulatedData
            )
        }

        // 清除回放任务 ID
        currentReplayTaskId = null

        // 移动到历史（但不持久化，因为是临时任务）
        // 延迟清除当前任务
        Log.i(TAG, "回放完成: 原任务=$originalTaskId, 成功=$success, 成功数=$successCount, 失败数=$failCount")
    }

    /**
     * 回放错误
     * @param originalTaskId 原任务 ID
     * @param error 错误信息
     */
    fun onReplayError(originalTaskId: String, error: String) {
        // 检查是否是当前回放任务
        if (currentReplayTaskId != originalTaskId) {
            Log.w(TAG, "收到非当前回放任务的错误消息: $originalTaskId, 当前: $currentReplayTaskId")
            return
        }

        updateCurrentTask { task ->
            task.copy(
                status = TaskStatus.FAILED,
                errorMessage = error,
                endTime = System.currentTimeMillis(),
                accumulatedData = task.accumulatedData
            )
        }

        // 清除回放任务 ID
        currentReplayTaskId = null

        Log.e(TAG, "回放错误: 原任务=$originalTaskId, 错误=$error")
    }

    /**
     * 检查是否正在回放
     */
    fun isReplaying(): Boolean {
        return currentReplayTaskId != null
    }

    /**
     * 获取当前回放的原始任务 ID
     */
    fun getCurrentReplayTaskId(): String? {
        return currentReplayTaskId
    }

    /**
     * 回放被取消
     * @param originalTaskId 原任务 ID
     * @param cancelledAtStep 取消时的步骤
     * @param total 总步骤数
     * @param reason 取消原因
     */
    fun onReplayCancelled(originalTaskId: String, cancelledAtStep: Int, total: Int, reason: String) {
        // 检查是否是当前回放任务
        if (currentReplayTaskId != originalTaskId) {
            Log.w(TAG, "收到非当前回放任务的取消消息: $originalTaskId, 当前: $currentReplayTaskId")
            return
        }

        updateCurrentTask { task ->
            task.copy(
                status = TaskStatus.CANCELLED,
                cancelReason = reason,
                currentStep = cancelledAtStep,
                totalSteps = total,
                endTime = System.currentTimeMillis(),
                accumulatedData = task.accumulatedData
            )
        }

        // 清除回放任务 ID
        currentReplayTaskId = null

        Log.i(TAG, "回放已取消: 原任务=$originalTaskId, 步骤=$cancelledAtStep/$total, 原因=$reason")
    }

    /**
     * 取消当前回放
     * 需要通过 WebSocket 发送取消请求
     * @return 是否有正在进行的回放可以取消
     */
    fun cancelCurrentReplay(): Boolean {
        val replayTaskId = currentReplayTaskId
        if (replayTaskId == null) {
            Log.w(TAG, "没有正在进行的回放")
            return false
        }

        // 更新本地状态为取消中
        updateCurrentTask { task ->
            task.copy(
                status = TaskStatus.CANCELLING,
                accumulatedData = task.accumulatedData
            )
        }

        Log.i(TAG, "请求取消回放: $replayTaskId")
        return true
    }
}
