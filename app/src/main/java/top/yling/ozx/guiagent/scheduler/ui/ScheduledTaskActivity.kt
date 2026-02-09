package top.yling.ozx.guiagent.scheduler.ui

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.R
import top.yling.ozx.guiagent.databinding.ActivityScheduledTaskBinding
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.TaskStatus

/**
 * 定时任务管理页面
 *
 * @author shanwb
 * @date 2026/01/30
 */
class ScheduledTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduledTaskBinding
    private val viewModel: ScheduledTaskViewModel by viewModels()
    private lateinit var adapter: ScheduledTaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduledTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupTabs()
        setupRecyclerView()
        observeData()

        // 初始化用户ID（从登录状态获取）
        val userId = getUserId()
        viewModel.init(userId)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "定时任务管理"
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val taskTab = when (tab.position) {
                    0 -> TaskTab.ALL
                    1 -> TaskTab.REMINDER
                    2 -> TaskTab.AUTOMATION
                    else -> TaskTab.ALL
                }
                viewModel.selectTab(taskTab)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = ScheduledTaskAdapter(
            onToggleClick = { task ->
                if (task.status == TaskStatus.ACTIVE.name) {
                    viewModel.pauseTask(task.id)
                } else if (task.status == TaskStatus.PAUSED.name) {
                    viewModel.resumeTask(task.id)
                }
            },
            onDeleteClick = { task ->
                showDeleteConfirmDialog(task.id, task.taskContent)
            },
            onItemClick = { task ->
                showTaskDetailDialog(task)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ScheduledTaskActivity)
            adapter = this@ScheduledTaskActivity.adapter
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            // 观察任务列表
            viewModel.tasks.collectLatest { tasks ->
                adapter.submitList(tasks)
                binding.emptyView.visibility = if (tasks.isEmpty())
                    View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            // 观察任务数量，更新 Tab 标题
            viewModel.reminderCount.collectLatest { count ->
                binding.tabLayout.getTabAt(1)?.text = "提醒任务 $count"
            }
        }

        lifecycleScope.launch {
            viewModel.automationCount.collectLatest { count ->
                binding.tabLayout.getTabAt(2)?.text = "自动化任务 $count"
            }
        }

        lifecycleScope.launch {
            // 观察操作结果
            viewModel.operationResult.collectLatest { result ->
                when (result) {
                    is OperationResult.Success -> {
                        Toast.makeText(this@ScheduledTaskActivity, result.message, Toast.LENGTH_SHORT).show()
                    }
                    is OperationResult.Error -> {
                        Toast.makeText(this@ScheduledTaskActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(taskId: Long, taskContent: String) {
        AlertDialog.Builder(this)
            .setTitle("删除任务")
            .setMessage("确定删除定时任务「$taskContent」？\n\n删除后无法恢复。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteTask(taskId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showTaskDetailDialog(task: ScheduledTask) {
        lifecycleScope.launch {
            val history = viewModel.getTaskHistory(task.id)

            val detailMessage = buildString {
                appendLine("任务内容: ${task.taskContent}")
                appendLine("类型: ${if (task.intentType == "REMINDER") "提醒" else "自动化"}")
                appendLine("重复: ${getRepeatDescription(task.repeatType)}")
                appendLine("下次执行: ${formatTime(task.nextTriggerTimeMillis)}")
                appendLine("已执行: ${task.executeCount} 次")
                appendLine("状态: ${getStatusDescription(task.status)}")

                if (history.isNotEmpty()) {
                    appendLine()
                    appendLine("最近执行记录:")
                    history.take(5).forEach { record ->
                        val status = if (record.success) "✓" else "✗"
                        appendLine("  $status ${formatTime(record.executedAt)}")
                    }
                }
            }

            AlertDialog.Builder(this@ScheduledTaskActivity)
                .setTitle("任务详情")
                .setMessage(detailMessage)
                .setPositiveButton("确定", null)
                .setNegativeButton("删除") { _, _ ->
                    showDeleteConfirmDialog(task.id, task.taskContent)
                }
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_scheduled_task, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_filter -> {
                showFilterDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val options = arrayOf("全部", "活跃", "已暂停", "已完成", "已失败")
        AlertDialog.Builder(this)
            .setTitle("筛选状态")
            .setItems(options) { _, which ->
                val status = when (which) {
                    1 -> TaskStatus.ACTIVE
                    2 -> TaskStatus.PAUSED
                    3 -> TaskStatus.SUCCESS
                    4 -> TaskStatus.FAILED
                    else -> null
                }
                viewModel.setStatusFilter(status)
            }
            .show()
    }

    private fun getUserId(): String {
        // 从 SharedPreferences 或登录状态获取
        return getSharedPreferences("user", Context.MODE_PRIVATE)
            .getString("userId", "") ?: ""
    }

    private fun getRepeatDescription(repeatType: String): String {
        return when (repeatType) {
            "DAILY" -> "每天"
            "WEEKLY" -> "每周"
            "WEEKDAYS" -> "工作日"
            "MONTHLY" -> "每月"
            else -> "单次"
        }
    }

    private fun getStatusDescription(status: String): String {
        return when (status) {
            "ACTIVE" -> "活跃"
            "PAUSED" -> "已暂停"
            "RUNNING" -> "执行中"
            "SUCCESS" -> "已完成"
            "FAILED" -> "已失败"
            else -> status
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}

