package top.yling.ozx.guiagent.scheduler.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import top.yling.ozx.guiagent.R
import top.yling.ozx.guiagent.databinding.ItemScheduledTaskBinding
import top.yling.ozx.guiagent.data.room.entity.ScheduledTask
import top.yling.ozx.guiagent.data.room.entity.TaskStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * 定时任务列表适配器
 *
 * @author shanwb
 * @date 2026/01/30
 */
class ScheduledTaskAdapter(
    private val onToggleClick: (ScheduledTask) -> Unit,
    private val onDeleteClick: (ScheduledTask) -> Unit,
    private val onItemClick: (ScheduledTask) -> Unit
) : ListAdapter<ScheduledTask, ScheduledTaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemScheduledTaskBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val binding: ItemScheduledTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: ScheduledTask) {
            val context = binding.root.context

            // 任务内容
            binding.tvTaskContent.text = task.taskContent

            // 时间信息
            val timeText = buildTimeText(task)
            binding.tvTimeInfo.text = timeText

            // 重复类型
            binding.tvRepeatType.text = getRepeatDescription(task.repeatType)

            // 任务类型图标
            val typeIcon = if (task.intentType == "REMINDER") {
                R.drawable.ic_alarm
            } else {
                R.drawable.ic_automation
            }
            binding.ivTypeIcon.setImageResource(typeIcon)

            // 状态开关
            val isActive = task.status == TaskStatus.ACTIVE.name
            binding.switchToggle.setOnCheckedChangeListener(null) // 先移除监听
            binding.switchToggle.isChecked = isActive
            binding.switchToggle.setOnCheckedChangeListener { _, _ ->
                onToggleClick(task)
            }

            // 状态样式
            when (task.status) {
                TaskStatus.PAUSED.name -> {
                    binding.root.alpha = 0.6f
                    binding.tvStatus.text = "已暂停"
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.switchToggle.visibility = View.VISIBLE
                }
                TaskStatus.SUCCESS.name -> {
                    binding.root.alpha = 0.5f
                    binding.tvStatus.text = "已完成"
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.switchToggle.visibility = View.GONE
                }
                TaskStatus.FAILED.name -> {
                    binding.root.alpha = 0.5f
                    binding.tvStatus.text = "已失败"
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.switchToggle.visibility = View.GONE
                }
                else -> {
                    binding.root.alpha = 1f
                    binding.tvStatus.visibility = View.GONE
                    binding.switchToggle.visibility = View.VISIBLE
                }
            }

            // 执行次数
            if (task.executeCount > 0) {
                binding.tvExecuteCount.text = "已执行 ${task.executeCount} 次"
                binding.tvExecuteCount.visibility = View.VISIBLE
            } else {
                binding.tvExecuteCount.visibility = View.GONE
            }

            // 点击事件
            binding.root.setOnClickListener {
                onItemClick(task)
            }

            // 长按删除
            binding.root.setOnLongClickListener {
                onDeleteClick(task)
                true
            }
        }

        private fun buildTimeText(task: ScheduledTask): String {
            val now = System.currentTimeMillis()
            val triggerTime = task.nextTriggerTimeMillis

            return when {
                task.status != TaskStatus.ACTIVE.name && task.status != TaskStatus.PAUSED.name -> {
                    // 已完成或失败的任务
                    formatDateTime(task.lastExecuteTime ?: triggerTime)
                }
                triggerTime < now -> {
                    // 已过期
                    "已过期"
                }
                triggerTime - now < 60 * 60 * 1000 -> {
                    // 1小时内
                    val minutes = (triggerTime - now) / 60000
                    "${minutes}分钟后"
                }
                triggerTime - now < 24 * 60 * 60 * 1000 -> {
                    // 今天
                    "今天 ${formatTime(triggerTime)}"
                }
                triggerTime - now < 48 * 60 * 60 * 1000 -> {
                    // 明天
                    "明天 ${formatTime(triggerTime)}"
                }
                else -> {
                    formatDateTime(triggerTime)
                }
            }
        }

        private fun getRepeatDescription(repeatType: String): String {
            return when (repeatType) {
                "DAILY" -> "每天"
                "WEEKLY" -> "每周"
                "WEEKDAYS" -> "工作日"
                "MONTHLY" -> "每月"
                "ONCE" -> "单次"
                else -> repeatType
            }
        }

        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        private fun formatDateTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<ScheduledTask>() {
        override fun areItemsTheSame(oldItem: ScheduledTask, newItem: ScheduledTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScheduledTask, newItem: ScheduledTask): Boolean {
            return oldItem == newItem
        }
    }
}

