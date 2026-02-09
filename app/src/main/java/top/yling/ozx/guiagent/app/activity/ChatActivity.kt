package top.yling.ozx.guiagent

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import top.yling.ozx.guiagent.databinding.ActivityChatBinding
import top.yling.ozx.guiagent.databinding.ItemMessageAiBinding
import top.yling.ozx.guiagent.databinding.ItemMessageUserBinding
import top.yling.ozx.guiagent.model.ChatMessage
import top.yling.ozx.guiagent.websocket.WebSocketClient
import top.yling.ozx.guiagent.transport.api.ConnectionFactory
import top.yling.ozx.guiagent.util.DeviceUtils
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var messageAdapter: MessageAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var agentId: String = ""
    private var agentName: String = ""
    private var agentAvatar: Int = 0

    // 独立的 WebSocket 客户端
    private var webSocketClient: WebSocketClient? = null
    private val gson = Gson()

    // 用于追踪是否正在接收流式响应
    private var isStreamingResponse = false

    companion object {
        private const val TAG = "ChatActivity"
        private const val PREF_NAME = "websocket_prefs"
        private const val PREF_SERVER_URL = "server_url"
        // 默认的 WebSocket 服务器地址（从 BuildConfig 读取，配置在 local.properties）
        private val DEFAULT_SERVER_URL = BuildConfig.WEBSOCKET_URL
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // 获取传递的Agent信息
        agentId = intent.getStringExtra("AGENT_ID") ?: ""
        agentName = intent.getStringExtra("AGENT_NAME") ?: "AI Agent"
        agentAvatar = intent.getIntExtra("AGENT_AVATAR", R.mipmap.ic_launcher)

        setupWindowInsets()
        setupHeader()
        setupRecyclerView()
        setupInputArea()
        loadMessages()

        // 创建独立的 WebSocket 连接
        setupWebSocketConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 断开 WebSocket 连接
        webSocketClient?.disconnect()
        webSocketClient = null
    }

    private fun setupWebSocketConnection() {
        val prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val serverUrl = prefs.getString(PREF_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

        android.util.Log.d(TAG, "创建独立 WebSocket 连接: $serverUrl")

        // 创建独立的 WebSocketClient（通过 ConnectionFactory 创建传输层）
        val agentConnection = ConnectionFactory.create(this)
        webSocketClient = WebSocketClient(this, serverUrl, agentConnection)

        // 设置消息监听器
        webSocketClient?.onMessageReceived = { message ->
            handleWebSocketMessage(message)
        }

        webSocketClient?.onConnectionStateChanged = { connected ->
            runOnUiThread {
                if (connected) {
                    android.util.Log.d(TAG, "WebSocket 已连接")
                } else {
                    android.util.Log.d(TAG, "WebSocket 已断开")
                }
            }
        }

        webSocketClient?.onError = { error ->
            runOnUiThread {
                android.util.Log.e(TAG, "WebSocket 错误: $error")
                Toast.makeText(this, "连接错误: $error", Toast.LENGTH_SHORT).show()
            }
        }

        // 连接 WebSocket (使用 "chat" 后缀)
        webSocketClient?.connect("chat")
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleWebSocketMessage(json: String) {
        try {
            val jsonObject = gson.fromJson(json, Map::class.java) as? Map<String, Any?>
            val type = jsonObject?.get("type") as? String

            when (type) {
                "agent_response" -> {
                    val content = jsonObject?.get("data") as? String ?: ""
                    // 流式添加 AI 回复消息
                    runOnUiThread {
                        appendOrAddAiMessage(content)
                    }
                }
                "agent_complete" -> {
                    val content = jsonObject?.get("data") as? String ?: ""
                    // 任务完成，结束流式响应
                    runOnUiThread {
                        if (content.isNotEmpty()) {
                            appendOrAddAiMessage(content)
                        }
                        // 完成后重置流式标志（可选，因为用户发送新消息时会重置）
                        // isStreamingResponse = false
                    }
                }
                "agent_error" -> {
                    val error = jsonObject?.get("error") as? String ?: "未知错误"
                    runOnUiThread {
                        Toast.makeText(this, "错误: $error", Toast.LENGTH_SHORT).show()
                        // 错误时也重置流式标志
                        isStreamingResponse = false
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "处理消息失败: ${e.message}", e)
        }
    }

    /**
     * 添加或拼接 AI 消息
     * 如果正在流式响应，则拼接到最后一条 AI 消息
     * 否则创建新的 AI 消息
     */
    private fun appendOrAddAiMessage(content: String) {
        if (isStreamingResponse && messages.isNotEmpty()) {
            // 查找最后一条 AI 消息
            val lastIndex = messages.size - 1
            val lastMessage = messages[lastIndex]

            if (!lastMessage.isFromUser) {
                // 拼接内容到最后一条 AI 消息
                lastMessage.content += content
                messageAdapter.notifyItemChanged(lastIndex)
                binding.messagesRecyclerView.smoothScrollToPosition(lastIndex)
                return
            }
        }

        // 创建新的 AI 消息（第一次响应或者没有找到 AI 消息）
        val aiMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            agentId = agentId,
            content = content,
            timestamp = Date(),
            isFromUser = false
        )

        messages.add(aiMessage)
        messageAdapter.notifyItemInserted(messages.size - 1)
        binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
        updateEmptyState()

        // 标记为流式响应
        isStreamingResponse = true
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val systemInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )

            // 获取键盘（IME）的insets
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val isKeyboardVisible = imeInsets.bottom > systemInsets.bottom

            // 设置顶部header的padding
            binding.headerCard.setPadding(
                systemInsets.left,
                systemInsets.top,
                systemInsets.right,
                0
            )

            // 调整输入容器的底部margin，让它随键盘上移
            val inputContainerLayoutParams = binding.inputContainer.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

            if (isKeyboardVisible) {
                // 键盘可见时，设置底部margin = 键盘高度，让输入框在键盘上方
                inputContainerLayoutParams.bottomMargin = imeInsets.bottom
            } else {
                // 键盘不可见时，设置底部margin = 系统栏高度
                inputContainerLayoutParams.bottomMargin = systemInsets.bottom
            }
            binding.inputContainer.layoutParams = inputContainerLayoutParams

            // 给RecyclerView设置padding
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            binding.messagesRecyclerView.setPadding(
                basePadding,
                basePadding,
                basePadding,
                basePadding
            )

            // 当键盘弹出时，滚动到最后一条消息
            if (isKeyboardVisible && messages.isNotEmpty()) {
                binding.messagesRecyclerView.postDelayed({
                    binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
                }, 100)
            }

            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupHeader() {
        binding.agentNameHeader.text = agentName
        binding.agentAvatarHeader.setImageResource(agentAvatar)

        binding.backButton.setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter(messages, agentAvatar)

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = false // 从顶部开始显示消息
            }
            adapter = messageAdapter

            // 监听布局变化，确保内容可见
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                // 如果高度变小了（键盘弹出），并且有消息，自动滚动到最后一条
                val oldHeight = oldBottom - oldTop
                val newHeight = bottom - top
                if (newHeight < oldHeight && messages.isNotEmpty()) {
                    postDelayed({
                        smoothScrollToPosition(messages.size - 1)
                    }, 100)
                }
            }
        }
    }

    private fun setupInputArea() {
        // 监听输入框变化，控制发送按钮状态
        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.sendButton.isEnabled = !s.isNullOrBlank()
            }
        })

        // 发送按钮点击
        binding.sendButton.setOnClickListener {
            sendMessage()
        }
    }

    private fun loadMessages() {
        // 加载历史消息（示例数据）
        // 在实际应用中应从数据库或服务器加载
        messages.clear()

        // 添加一条欢迎消息
        messages.add(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                agentId = agentId,
                content = "你好！我是$agentName，有什么可以帮助你的吗？",
                timestamp = Date(),
                isFromUser = false
            )
        )

        messageAdapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun sendMessage() {
        val messageText = binding.messageInput.text.toString().trim()
        if (messageText.isEmpty()) return

        // 检查 WebSocket 连接
        val client = webSocketClient
        if (client == null || !client.isConnected()) {
            Toast.makeText(this, "WebSocket 未连接，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        // 重置流式响应标志，下次响应将创建新的 AI 消息
        isStreamingResponse = false

        // 创建用户消息
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            agentId = agentId,
            content = messageText,
            timestamp = Date(),
            isFromUser = true
        )

        // 添加到列表并更新UI
        messages.add(userMessage)
        messageAdapter.notifyItemInserted(messages.size - 1)
        binding.messagesRecyclerView.smoothScrollToPosition(messages.size - 1)

        // 清空输入框
        binding.messageInput.text.clear()

        // 更新空状态
        updateEmptyState()

        // 通过独立的 WebSocket 发送消息
        // androidId 加上 "_chat" 后缀
        val androidId = DeviceUtils.getDeviceId(this) + "_chat"

        // 使用 WebSocketClient 的 sendAgentMessageWithTask 方法
        val taskId = client.sendAgentMessageWithTask(messageText, androidId)

        if (taskId != null) {
            android.util.Log.d(TAG, "消息已发送: $messageText, taskId: $taskId")
        } else {
            runOnUiThread {
                Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show()
                // 移除用户消息（因为发送失败）
                val lastIndex = messages.size - 1
                if (lastIndex >= 0 && messages[lastIndex].id == userMessage.id) {
                    messages.removeAt(lastIndex)
                    messageAdapter.notifyItemRemoved(lastIndex)
                }
            }
        }
    }

    private fun updateEmptyState() {
        val isEmpty = messages.isEmpty()
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.messagesRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
}

class MessageAdapter(
    private val messages: List<ChatMessage>,
    private val aiAvatarRes: Int
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2
    }

    inner class UserMessageViewHolder(private val binding: ItemMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.messageText.text = message.content
            binding.timestampText.text = timeFormat.format(message.timestamp)
        }
    }

    inner class AiMessageViewHolder(private val binding: ItemMessageAiBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(message: ChatMessage) {
            binding.messageText.text = message.content
            binding.timestampText.text = timeFormat.format(message.timestamp)
            binding.aiAvatar.setImageResource(aiAvatarRes)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isFromUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemMessageUserBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                UserMessageViewHolder(binding)
            }
            else -> {
                val binding = ItemMessageAiBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                AiMessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserMessageViewHolder -> holder.bind(messages[position])
            is AiMessageViewHolder -> holder.bind(messages[position])
        }
    }

    override fun getItemCount() = messages.size
}
