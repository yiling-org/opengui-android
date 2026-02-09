package top.yling.ozx.guiagent.model

import java.util.Date

data class AiAgent(
    val id: String,
    val name: String,
    val avatarRes: Int, // 头像资源ID
    val description: String,
    val lastMessage: String? = null,
    val lastMessageTime: Date? = null,
    val unreadCount: Int = 0
)

data class ChatMessage(
    val id: String,
    val agentId: String,
    var content: String, // 改为 var 以支持流式响应拼接
    val timestamp: Date,
    val isFromUser: Boolean // true=用户发送, false=AI回复
)
