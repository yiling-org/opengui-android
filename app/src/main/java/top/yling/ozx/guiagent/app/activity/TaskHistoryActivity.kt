package top.yling.ozx.guiagent

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import top.yling.ozx.guiagent.databinding.ActivityTaskHistoryBinding
import top.yling.ozx.guiagent.databinding.ItemTaskHistoryBinding
import top.yling.ozx.guiagent.util.DeviceUtils
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 任务历史页面
 * 显示当前设备的历史任务列表及执行状态
 */
class TaskHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskHistoryBinding
    private lateinit var taskAdapter: TaskHistoryAdapter
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 启用边到边显示（Edge-to-Edge）
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityTaskHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // 处理系统栏的内边距（避免与状态栏、导航栏重叠）
        setupWindowInsets()

        setupUI()
        loadTasks()
    }

    /**
     * 设置窗口内边距，处理系统栏重叠问题
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )

            // 为根布局设置内边距，避免内容被系统栏遮挡
            view.setPadding(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupUI() {
        // 初始化 Adapter，传入回放和删除回调
        taskAdapter = TaskHistoryAdapter(
            onReplayClick = { task ->
                replayTask(task)
            },
            onDeleteClick = { task, position ->
                deleteTask(task, position)
            }
        )

        // 返回按钮
        binding.backButton.setOnClickListener {
            finish()
        }

        // 刷新按钮
        binding.refreshButton.setOnClickListener {
            // 旋转动画
            binding.refreshButton.animate()
                .rotation(binding.refreshButton.rotation + 360f)
                .setDuration(500)
                .start()
            loadTasks()
        }

        // 重试按钮
        binding.retryButton.setOnClickListener {
            loadTasks()
        }

        // 设置 RecyclerView
        binding.taskRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TaskHistoryActivity)
            adapter = taskAdapter
        }
    }

    /**
     * 回放任务
     */
    private fun replayTask(task: TaskInfo) {
        val taskId = task.taskId ?: return

        // 显示自定义确认对话框
        showReplayConfirmDialog(task.description ?: "未知任务") {
            executeReplay(taskId, task.description)
        }
    }

    /**
     * 显示回放确认弹窗
     */
    private fun showReplayConfirmDialog(taskDescription: String, onConfirm: () -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_replay_confirm, null)

        val dialog = android.app.AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .create()

        // 设置弹窗背景透明
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 绑定视图
        dialogView.findViewById<android.widget.TextView>(R.id.dialogTaskDescription).text = taskDescription

        dialogView.findViewById<android.widget.TextView>(R.id.dialogCancelButton).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<android.widget.TextView>(R.id.dialogConfirmButton).setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.show()

        // 设置弹窗宽度为屏幕宽度的85%
        dialog.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.85).toInt()
            window.setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    /**
     * 删除任务
     */
    private fun deleteTask(task: TaskInfo, position: Int) {
        val taskId = task.taskId ?: return

        // 显示确认对话框
        android.app.AlertDialog.Builder(this)
            .setTitle("删除任务")
            .setMessage("确定要删除任务「${task.description ?: "未知任务"}」吗？\n\n删除后无法恢复。")
            .setPositiveButton("删除") { _, _ ->
                executeDelete(taskId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行删除任务
     */
    private fun executeDelete(taskId: String) {
        val baseUrl = getApiBaseUrl()

        if (baseUrl.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "$baseUrl/api/tasks/$taskId"
        android.util.Log.d("TaskHistory", "Deleting task: $url")

        val request = Request.Builder()
            .url(url)
            .delete()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("TaskHistory", "Failed to delete task", e)
                runOnUiThread {
                    Toast.makeText(this@TaskHistoryActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = response.body?.string()
                    android.util.Log.d("TaskHistory", "Delete response: $body")

                    runOnUiThread {
                        if (response.isSuccessful) {
                            try {
                                val deleteResponse = gson.fromJson(body, DeleteTaskResponse::class.java)
                                if (deleteResponse.isSuccess) {
                                    android.util.Log.d("TaskHistory", "Task deleted successfully")
                                    Toast.makeText(this@TaskHistoryActivity, "删除成功", Toast.LENGTH_SHORT).show()
                                    // 通过 taskId 从列表中移除
                                    val removed = taskAdapter.removeByTaskId(taskId)
                                    // 如果列表为空，显示空状态
                                    if (taskAdapter.isEmpty()) {
                                        showEmpty()
                                    }
                                    if (!removed) {
                                        android.util.Log.w("TaskHistory", "Task not found in adapter, reloading list")
                                        // 如果找不到任务，重新加载列表
                                        loadTasks()
                                    }
                                } else {
                                    val errorMsg = getErrorMessage(deleteResponse.code, deleteResponse.message)
                                    android.util.Log.e("TaskHistory", "Failed to delete task: $errorMsg")
                                    Toast.makeText(this@TaskHistoryActivity, "删除失败: $errorMsg", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("TaskHistory", "Failed to parse delete response", e)
                                Toast.makeText(this@TaskHistoryActivity, "删除失败: 解析响应失败", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            android.util.Log.e("TaskHistory", "Failed to delete task: ${response.code}")
                            Toast.makeText(this@TaskHistoryActivity, "删除失败: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    /**
     * 执行回放（通过 WebSocket）
     */
    private fun executeReplay(taskId: String, taskDesc: String?) {
        val androidId = DeviceUtils.getDeviceId(this)

        android.util.Log.d("TaskHistory", "Sending replay request via WebSocket: taskId=$taskId")
        Toast.makeText(this, "开始回放任务...", Toast.LENGTH_SHORT).show()

        // 通过 WebSocket 发送回放请求
        val intent = android.content.Intent(this, top.yling.ozx.guiagent.websocket.WebSocketService::class.java)
        bindService(intent, object : android.content.ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
                val binder = service as? top.yling.ozx.guiagent.websocket.WebSocketService.LocalBinder
                val wsService = binder?.getService()

                if (wsService != null) {
                    wsService.sendReplayRequest(
                        taskId = taskId,
                        androidId = androidId,
                        onSuccess = {
                            android.util.Log.d("TaskHistory", "Replay request sent successfully")
                            runOnUiThread {
                                // 创建回放任务（临时任务，用于灵动岛显示）
                                top.yling.ozx.guiagent.task.TaskManager.startReplay(
                                    originalTaskId = taskId,
                                    description = taskDesc ?: "回放任务"
                                )

                                // 启动 AgentOverlayService 显示灵动岛任务状态
                                if (!AgentOverlayService.isServiceRunning()) {
                                    AgentOverlayService.startService(this@TaskHistoryActivity)
                                    android.util.Log.d("TaskHistory", "启动 AgentOverlayService 用于任务状态显示")
                                }

                                // 延迟后最小化到悬浮球
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    minimizeAndClose()
                                }, 500)
                            }
                        },
                        onError = { error ->
                            android.util.Log.e("TaskHistory", "Replay request failed: $error")
                            runOnUiThread {
                                Toast.makeText(this@TaskHistoryActivity, "回放失败: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                } else {
                    Toast.makeText(this@TaskHistoryActivity, "WebSocket 服务未连接", Toast.LENGTH_LONG).show()
                }

                // 解绑服务
                try {
                    unbindService(this)
                } catch (e: Exception) {
                    android.util.Log.w("TaskHistory", "Unbind service error: ${e.message}")
                }
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                // 服务断开
            }
        }, android.content.Context.BIND_AUTO_CREATE)
    }

    /**
     * 最小化应用到后台
     */
    private fun minimizeAndClose() {
        // 将整个应用移到后台
        moveTaskToBack(true)
        android.util.Log.d("TaskHistory", "回放任务已发送，应用已最小化到后台")
    }

    private fun loadTasks() {
        showLoading()

        val androidId = DeviceUtils.getDeviceId(this)
        val baseUrl = getApiBaseUrl()

        if (baseUrl.isEmpty()) {
            showError("请先在设置中配置服务器地址")
            return
        }

        // 使用新的 API 获取包含步骤的任务列表
        val url = "$baseUrl/api/tasks/android/$androidId/with-steps?page=0&size=50"
        android.util.Log.d("TaskHistory", "Loading tasks from: $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("TaskHistory", "Failed to load tasks", e)
                runOnUiThread {
                    showError("加载失败: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            showError("服务器错误: ${response.code}")
                        }
                        return
                    }

                    val body = response.body?.string()
                    android.util.Log.d("TaskHistory", "Response: $body")

                    try {
                        val apiResponse = gson.fromJson(body, TaskApiResponse::class.java)
                        runOnUiThread {
                            if (apiResponse.isSuccess) {
                                val pageData = apiResponse.data
                                if (pageData == null || pageData.content.isEmpty()) {
                                    showEmpty()
                                } else {
                                    showTasks(pageData.content)
                                }
                            } else {
                                // 使用错误码和消息显示错误
                                val errorMsg = getErrorMessage(apiResponse.code, apiResponse.message)
                                showError(errorMsg)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TaskHistory", "Failed to parse response", e)
                        runOnUiThread {
                            showError("解析失败: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    /**
     * 根据错误码获取用户友好的错误提示
     */
    private fun getErrorMessage(code: Int, defaultMessage: String?): String {
        return when (code) {
            400 -> defaultMessage ?: "请求参数错误"
            401 -> defaultMessage ?: "登录已过期，请重新登录"
            403 -> defaultMessage ?: "没有访问权限"
            404 -> defaultMessage ?: "请求的资源不存在"
            408 -> defaultMessage ?: "请求超时，请重试"
            429 -> defaultMessage ?: "请求过于频繁，请稍后重试"
            500 -> defaultMessage ?: "服务器内部错误"
            503 -> defaultMessage ?: "服务暂时不可用"
            else -> defaultMessage ?: "未知错误 ($code)"
        }
    }

    /**
     * 获取 API 基础 URL
     * 从 WebSocket URL 转换为 HTTP URL
     */
    private fun getApiBaseUrl(): String {
        val prefs = getSharedPreferences("websocket_prefs", Context.MODE_PRIVATE)
        val wsUrl = prefs.getString("server_url", "") ?: ""

        if (wsUrl.isEmpty()) {
            return ""
        }

        // 转换 ws:// -> http://, wss:// -> https://
        // 并移除路径部分，只保留 host:port
        return try {
            val httpUrl = wsUrl
                .replace("wss://", "https://")
                .replace("ws://", "http://")

            // 提取 scheme + host + port
            val uri = java.net.URI(httpUrl)
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$port"
        } catch (e: Exception) {
            android.util.Log.e("TaskHistory", "Failed to parse URL: $wsUrl", e)
            ""
        }
    }

    private fun showLoading() {
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.taskRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.loadingIndicator.visibility = View.GONE
        binding.taskRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
        binding.errorStateLayout.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.loadingIndicator.visibility = View.GONE
        binding.taskRecyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.VISIBLE
        binding.errorText.text = message
    }

    private fun showTasks(tasks: List<TaskInfo>) {
        binding.loadingIndicator.visibility = View.GONE
        binding.taskRecyclerView.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE
        binding.errorStateLayout.visibility = View.GONE
        taskAdapter.submitList(tasks)
    }
}

/**
 * 任务历史适配器
 */
class TaskHistoryAdapter(
    private val onReplayClick: (TaskInfo) -> Unit,
    private val onDeleteClick: (TaskInfo, Int) -> Unit
) : RecyclerView.Adapter<TaskHistoryAdapter.TaskViewHolder>() {

    private val tasks = mutableListOf<TaskInfo>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    // 记录步骤展开状态
    private val expandedPositions = mutableSetOf<Int>()
    // 记录结果文本展开状态
    private val expandedResultPositions = mutableSetOf<Int>()

    fun submitList(newTasks: List<TaskInfo>) {
        tasks.clear()
        tasks.addAll(newTasks)
        expandedPositions.clear()
        expandedResultPositions.clear()
        notifyDataSetChanged()
    }

    fun removeAt(position: Int) {
        if (position >= 0 && position < tasks.size) {
            tasks.removeAt(position)
            expandedPositions.remove(position)
            expandedResultPositions.remove(position)
            // 更新展开状态中大于被删除位置的索引
            val newExpandedPositions = expandedPositions.map {
                if (it > position) it - 1 else it
            }.toMutableSet()
            expandedPositions.clear()
            expandedPositions.addAll(newExpandedPositions)

            val newExpandedResultPositions = expandedResultPositions.map {
                if (it > position) it - 1 else it
            }.toMutableSet()
            expandedResultPositions.clear()
            expandedResultPositions.addAll(newExpandedResultPositions)

            notifyItemRemoved(position)
        }
    }

    /**
     * 通过 taskId 删除任务
     * @return 是否成功删除
     */
    fun removeByTaskId(taskId: String): Boolean {
        val position = tasks.indexOfFirst { it.taskId == taskId }
        if (position >= 0) {
            removeAt(position)
            return true
        }
        return false
    }

    fun isEmpty(): Boolean = tasks.isEmpty()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position], position)
    }

    override fun getItemCount(): Int = tasks.size

    inner class TaskViewHolder(
        private val binding: ItemTaskHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskInfo, position: Int) {
            val context = binding.root.context

            // 任务描述
            binding.taskDescription.text = task.description ?: "未知任务"

            // 状态
            val (statusText, statusColor, dotDrawable) = when (task.status) {
                "COMPLETED" -> Triple("已完成", R.color.success, R.drawable.status_dot_green)
                "RUNNING", "PROCESSING", "EXECUTING" -> Triple("执行中", R.color.processing, R.drawable.status_dot_yellow)
                "FAILED" -> Triple("失败", R.color.error, R.drawable.status_dot_red)
                "CANCELLED" -> Triple("已取消", R.color.text_secondary, R.drawable.status_dot_red)
                "PENDING" -> Triple("等待中", R.color.text_secondary, R.drawable.status_dot_yellow)
                else -> Triple(task.status ?: "未知", R.color.text_secondary, R.drawable.status_dot_red)
            }

            binding.statusText.text = statusText
            binding.statusText.setTextColor(context.getColor(statusColor))
            binding.statusIndicator.setBackgroundResource(dotDrawable)

            // 时间和耗时（合并显示）
            val createdAtDate = task.getCreatedAtDate()
            val timeStr = if (createdAtDate != null) {
                try {
                    dateFormat.format(createdAtDate)
                } catch (e: Exception) {
                    "未知时间"
                }
            } else {
                "未知时间"
            }

            val duration = task.durationMs
            val durationStr = if (duration != null && duration > 0) {
                "耗时: ${formatDuration(duration)}"
            } else if (task.status == "RUNNING" || task.status == "PROCESSING") {
                "执行中..."
            } else {
                null
            }

            binding.timeText.text = if (durationStr != null) {
                "$timeStr | $durationStr"
            } else {
                timeStr
            }

            // 结果/错误信息
            val resultOrError = when (task.status) {
                "COMPLETED" -> task.result
                "FAILED" -> task.errorMessage
                "CANCELLED" -> task.cancelReason
                else -> null
            }

            if (!resultOrError.isNullOrEmpty()) {
                binding.resultContainer.visibility = View.VISIBLE
                binding.resultText.text = resultOrError

                // 根据状态设置文本颜色
                when (task.status) {
                    "FAILED" -> binding.resultText.setTextColor(context.getColor(R.color.error))
                    "CANCELLED" -> binding.resultText.setTextColor(context.getColor(R.color.text_secondary))
                    else -> binding.resultText.setTextColor(context.getColor(R.color.text_primary))
                }

                // 展开/收起结果文本逻辑
                val isResultExpanded = expandedResultPositions.contains(position)
                updateResultExpandState(isResultExpanded, resultOrError)

                binding.expandResultButton.setOnClickListener {
                    if (expandedResultPositions.contains(position)) {
                        expandedResultPositions.remove(position)
                        updateResultExpandState(false, resultOrError)
                    } else {
                        expandedResultPositions.add(position)
                        updateResultExpandState(true, resultOrError)
                    }
                }

                // 检查文本是否需要展开按钮（延迟检测）
                binding.resultText.post {
                    val layout = binding.resultText.layout
                    if (layout != null && layout.lineCount > 0) {
                        val lastLine = layout.lineCount - 1
                        val isEllipsized = layout.getEllipsisCount(lastLine) > 0
                        binding.expandResultButton.visibility = if (isEllipsized || isResultExpanded) View.VISIBLE else View.GONE
                    }
                }
            } else {
                binding.resultContainer.visibility = View.GONE
            }

            // 删除按钮 - 所有任务都显示
            binding.deleteButton.setOnClickListener {
                onDeleteClick(task, position)
            }

            // 回放按钮 - 只有已完成的任务显示
            val isReplayable = task.status == "COMPLETED" && !task.taskId.isNullOrEmpty()
            binding.replayButton.visibility = if (isReplayable) View.VISIBLE else View.GONE
            // 设置图标颜色
            binding.replayButton.compoundDrawableTintList = android.content.res.ColorStateList.valueOf(
                context.getColor(R.color.accent_indigo)
            )
            binding.replayButton.setOnClickListener {
                onReplayClick(task)
            }

            // 步骤展示
            val hasSteps = !task.steps.isNullOrEmpty()
            binding.expandCollapseButton.visibility = if (hasSteps) View.VISIBLE else View.GONE

            // 底部操作区可见性：回放按钮或步骤按钮有一个显示时，底部操作区显示
            binding.bottomActionBar.visibility = if (isReplayable || hasSteps) View.VISIBLE else View.GONE

            val isExpanded = expandedPositions.contains(position)
            updateExpandState(isExpanded, task, context)

            // 展开/收起按钮点击
            binding.expandCollapseButton.setOnClickListener {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position)
                    updateExpandState(false, task, context)
                } else {
                    expandedPositions.add(position)
                    updateExpandState(true, task, context)
                }
            }

            // 点击卡片也可以展开/收起
            binding.root.setOnClickListener {
                if (hasSteps) {
                    if (expandedPositions.contains(position)) {
                        expandedPositions.remove(position)
                        updateExpandState(false, task, context)
                    } else {
                        expandedPositions.add(position)
                        updateExpandState(true, task, context)
                    }
                }
            }
        }

        private fun updateResultExpandState(isExpanded: Boolean, resultText: String) {
            if (isExpanded) {
                binding.resultText.maxLines = Int.MAX_VALUE
                binding.resultText.ellipsize = null
                binding.expandResultButton.text = "收起"
            } else {
                binding.resultText.maxLines = 3
                binding.resultText.ellipsize = android.text.TextUtils.TruncateAt.END
                binding.expandResultButton.text = "展开全文"
            }
        }

        private fun updateExpandState(isExpanded: Boolean, task: TaskInfo, context: Context) {
            if (isExpanded && !task.steps.isNullOrEmpty()) {
                binding.stepsContainer.visibility = View.VISIBLE
                binding.expandCollapseText.text = "收起步骤"
                binding.expandCollapseIcon.setImageResource(R.drawable.ic_expand_less)

                // 显示步骤进度
                val completedCount = task.steps.count { it.status == "COMPLETED" }
                binding.stepsProgress.text = "$completedCount/${task.steps.size} 完成"

                // 动态添加步骤项
                binding.stepsListContainer.removeAllViews()
                task.steps.forEachIndexed { index, step ->
                    val stepView = LayoutInflater.from(context)
                        .inflate(R.layout.item_task_step, binding.stepsListContainer, false)

                    val stepStatusIcon = stepView.findViewById<ImageView>(R.id.stepStatusIcon)
                    val stepNumber = stepView.findViewById<TextView>(R.id.stepNumber)
                    val stepName = stepView.findViewById<TextView>(R.id.stepName)

                    // 设置步骤序号
                    stepNumber.text = "${index + 1}."

                    // 设置步骤名称
                    stepName.text = step.stepName ?: "未知步骤"

                    // 根据状态设置图标和颜色
                    when (step.status) {
                        "COMPLETED" -> {
                            stepStatusIcon.setImageResource(R.drawable.ic_check_circle)
                            stepName.setTextColor(context.getColor(R.color.white))
                        }
                        "IN_PROGRESS" -> {
                            stepStatusIcon.setImageResource(R.drawable.ic_running_circle)
                            stepName.setTextColor(context.getColor(R.color.processing))
                        }
                        "FAILED" -> {
                            stepStatusIcon.setImageResource(R.drawable.ic_error_circle)
                            stepName.setTextColor(context.getColor(R.color.error))
                        }
                        "SKIPPED" -> {
                            stepStatusIcon.setImageResource(R.drawable.ic_pending_circle)
                            stepName.setTextColor(context.getColor(R.color.text_secondary))
                            stepName.alpha = 0.6f
                        }
                        else -> { // PENDING
                            stepStatusIcon.setImageResource(R.drawable.ic_pending_circle)
                            stepName.setTextColor(context.getColor(R.color.text_secondary))
                        }
                    }

                    binding.stepsListContainer.addView(stepView)
                }
            } else {
                binding.stepsContainer.visibility = View.GONE
                binding.expandCollapseText.text = "查看步骤详情"
                binding.expandCollapseIcon.setImageResource(R.drawable.ic_expand_more)
            }
        }

        private fun formatDuration(ms: Long): String {
            return when {
                ms < 1000 -> "${ms}ms"
                ms < 60000 -> String.format("%.1f秒", ms / 1000.0)
                else -> String.format("%.1f分钟", ms / 60000.0)
            }
        }
    }
}

/**
 * 任务信息数据类
 */
data class TaskInfo(
    @SerializedName("taskId")
    val taskId: String?,

    @SerializedName("clientId")
    val clientId: String?,

    @SerializedName("androidId")
    val androidId: String?,

    @SerializedName("userId")
    val userId: String?,

    @SerializedName("agentId")
    val agentId: String?,

    @SerializedName("description")
    val description: String?,

    @SerializedName("status")
    val status: String?,

    @SerializedName("currentStep")
    val currentStep: Int?,

    @SerializedName("totalSteps")
    val totalSteps: Int?,

    @SerializedName("progress")
    val progress: Int?,

    @SerializedName("lastMessage")
    val lastMessage: String?,

    @SerializedName("lastAction")
    val lastAction: String?,

    @SerializedName("result")
    val result: String?,

    @SerializedName("errorMessage")
    val errorMessage: String?,

    @SerializedName("cancelReason")
    val cancelReason: String?,

    @SerializedName("createdAt")
    val createdAt: Long?,  // 毫秒时间戳

    @SerializedName("updatedAt")
    val updatedAt: Long?,

    @SerializedName("endedAt")
    val endedAt: Long?,

    @SerializedName("durationMs")
    val durationMs: Long?,

    @SerializedName("steps")
    val steps: List<StepInfo>?
) {
    /**
     * 获取创建时间的 Date 对象
     */
    fun getCreatedAtDate(): Date? {
        return createdAt?.let { Date(it) }
    }

    fun getUpdatedAtDate(): Date? {
        return updatedAt?.let { Date(it) }
    }

    fun getEndedAtDate(): Date? {
        return endedAt?.let { Date(it) }
    }
}

/**
 * 步骤信息数据类
 */
data class StepInfo(
    @SerializedName("stepIndex")
    val stepIndex: Int?,

    @SerializedName("stepName")
    val stepName: String?,

    @SerializedName("status")
    val status: String?,

    @SerializedName("startTime")
    val startTime: Long?,

    @SerializedName("endTime")
    val endTime: Long?,

    @SerializedName("errorMsg")
    val errorMsg: String?
)

/**
 * 分页数据
 */
data class PageData(
    @SerializedName("content")
    val content: List<TaskInfo>,

    @SerializedName("pageable")
    val pageable: Any?,

    @SerializedName("totalPages")
    val totalPages: Int?,

    @SerializedName("totalElements")
    val totalElements: Int?,

    @SerializedName("size")
    val size: Int?,

    @SerializedName("number")
    val number: Int?
)

/**
 * API 响应包装类
 * 对应服务端的 run.mone.common.Result 结构
 */
data class TaskApiResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String?,

    @SerializedName("data")
    val data: PageData?
) {
    val isSuccess: Boolean
        get() = code == 0
}

/**
 * 删除任务响应
 */
data class DeleteTaskResponse(
    @SerializedName("code")
    val code: Int = 0,

    @SerializedName("message")
    val message: String?,

    @SerializedName("data")
    val data: DeleteTaskData?
) {
    val isSuccess: Boolean
        get() = code == 0
}

data class DeleteTaskData(
    @SerializedName("taskId")
    val taskId: String?,

    @SerializedName("message")
    val message: String?
)
