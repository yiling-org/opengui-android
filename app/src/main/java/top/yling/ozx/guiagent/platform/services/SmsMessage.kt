package top.yling.ozx.guiagent.services

/**
 * 短信数据类
 */
data class SmsMessage(
    val id: Long,                    // 短信ID
    val address: String,             // 发送方/接收方号码
    val body: String,                // 短信内容
    val date: Long,                  // 时间戳
    val type: Int,                   // 类型: 1=收件箱, 2=已发送, 3=草稿
    val read: Boolean,               // 是否已读
    val threadId: Long? = null,      // 会话ID
    val contactName: String? = null  // 联系人名称（如果能匹配到）
) {
    companion object {
        const val TYPE_INBOX = 1     // 收件箱
        const val TYPE_SENT = 2      // 已发送
        const val TYPE_DRAFT = 3     // 草稿
        const val TYPE_OUTBOX = 4    // 发件箱
        const val TYPE_FAILED = 5    // 发送失败
        const val TYPE_QUEUED = 6    // 待发送队列
    }

    /**
     * 获取类型描述
     */
    fun getTypeDescription(): String {
        return when (type) {
            TYPE_INBOX -> "收件箱"
            TYPE_SENT -> "已发送"
            TYPE_DRAFT -> "草稿"
            TYPE_OUTBOX -> "发件箱"
            TYPE_FAILED -> "发送失败"
            TYPE_QUEUED -> "待发送"
            else -> "未知"
        }
    }

    /**
     * 获取格式化的日期时间
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(date))
    }
}
