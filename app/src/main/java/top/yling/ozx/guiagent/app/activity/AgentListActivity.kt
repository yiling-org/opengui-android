package top.yling.ozx.guiagent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import top.yling.ozx.guiagent.databinding.ActivityAgentListBinding
import top.yling.ozx.guiagent.databinding.ItemAgentBinding
import top.yling.ozx.guiagent.model.AiAgent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AgentListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentListBinding
    private lateinit var agentAdapter: AgentAdapter
    private val agents = mutableListOf<AiAgent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAgentListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupWindowInsets()
        setupRecyclerView()
        loadAgents()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )

            view.setPadding(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupRecyclerView() {
        agentAdapter = AgentAdapter(agents) { agent ->
            openChat(agent)
        }

        binding.agentRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@AgentListActivity)
            adapter = agentAdapter
        }
    }

    private fun loadAgents() {
        // 示例数据 - 在实际应用中应从服务器或数据库加载
        agents.clear()
        agents.addAll(listOf(
            AiAgent(
                id = "1",
                name = "通用助手",
                avatarRes = R.mipmap.ic_launcher,
                description = "全能AI助手，可以回答各种问题",
                lastMessage = "你好！有什么可以帮助你的吗？",
                lastMessageTime = Date(),
                unreadCount = 0
            ),
            AiAgent(
                id = "2",
                name = "代码专家",
                avatarRes = R.mipmap.ic_launcher,
                description = "专业的编程和技术问题解答",
                lastMessage = "让我帮你解决代码问题",
                lastMessageTime = Date(System.currentTimeMillis() - 3600000),
                unreadCount = 2
            ),
            AiAgent(
                id = "3",
                name = "文案助手",
                avatarRes = R.mipmap.ic_launcher,
                description = "帮你写作、润色和翻译",
                lastMessage = "准备好开始创作了吗？",
                lastMessageTime = Date(System.currentTimeMillis() - 7200000),
                unreadCount = 0
            ),
            AiAgent(
                id = "4",
                name = "学习伙伴",
                avatarRes = R.mipmap.ic_launcher,
                description = "陪你学习，解答疑问",
                lastMessage = "今天想学什么呢？",
                lastMessageTime = Date(System.currentTimeMillis() - 86400000),
                unreadCount = 1
            )
        ))

        agentAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding.emptyStateLayout.visibility = if (agents.isEmpty()) View.VISIBLE else View.GONE
        binding.agentRecyclerView.visibility = if (agents.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openChat(agent: AiAgent) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("AGENT_ID", agent.id)
            putExtra("AGENT_NAME", agent.name)
            putExtra("AGENT_AVATAR", agent.avatarRes)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

class AgentAdapter(
    private val agents: List<AiAgent>,
    private val onAgentClick: (AiAgent) -> Unit
) : RecyclerView.Adapter<AgentAdapter.AgentViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

    inner class AgentViewHolder(private val binding: ItemAgentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(agent: AiAgent) {
            binding.agentAvatar.setImageResource(agent.avatarRes)
            binding.agentName.text = agent.name
            binding.agentDescription.text = agent.description

            // 设置最后一条消息
            if (agent.lastMessage != null) {
                binding.lastMessageText.text = agent.lastMessage
                binding.lastMessageText.visibility = View.VISIBLE
            } else {
                binding.lastMessageText.visibility = View.GONE
            }

            // 设置时间
            if (agent.lastMessageTime != null) {
                val now = System.currentTimeMillis()
                val messageTime = agent.lastMessageTime.time
                val diff = now - messageTime

                binding.timeText.text = when {
                    diff < 86400000 -> timeFormat.format(agent.lastMessageTime) // 今天显示时间
                    else -> dateFormat.format(agent.lastMessageTime) // 其他显示日期
                }
                binding.timeText.visibility = View.VISIBLE
            } else {
                binding.timeText.visibility = View.GONE
            }

            // 设置未读消息徽章
            if (agent.unreadCount > 0) {
                binding.unreadBadge.text = if (agent.unreadCount > 99) "99+" else agent.unreadCount.toString()
                binding.unreadBadge.visibility = View.VISIBLE
            } else {
                binding.unreadBadge.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onAgentClick(agent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AgentViewHolder {
        val binding = ItemAgentBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AgentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AgentViewHolder, position: Int) {
        holder.bind(agents[position])
    }

    override fun getItemCount() = agents.size
}
