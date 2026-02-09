package top.yling.ozx.guiagent.websocket

import com.google.gson.annotations.SerializedName

/**
 * 无障碍指令数据类
 */
data class AccessibilityCommand(
    @SerializedName("action")
    val action: String,

    @SerializedName("params")
    val params: CommandParams? = null,

    @SerializedName("type")
    val type: String? = null
)

data class CommandParams(
    // 坐标参数
    @SerializedName("x")
    val x: Float? = null,

    @SerializedName("y")
    val y: Float? = null,

    // 结束坐标（用于 drag）
    @SerializedName("x2")
    val x2: Float? = null,

    @SerializedName("y2")
    val y2: Float? = null,

    // 方向（用于 scroll）
    @SerializedName("direction")
    val direction: String? = null,

    // 距离（用于 scroll）
    @SerializedName("distance")
    val distance: Float? = null,

    // 时长（用于 long_press, drag）
    @SerializedName("duration")
    val duration: Long? = null,

    // 文本内容（用于 type）
    @SerializedName("content")
    val content: String? = null,

    // 要复制的文本（用于 copy_text）
    @SerializedName("text")
    val text: String? = null,

    // 应用包名（用于 open_app）
    @SerializedName("app_name")
    val appName: String? = null,

    @SerializedName("package_name")
    val packageName: String? = null,

    // URL（用于 open_url）
    @SerializedName("url")
    val url: String? = null,

    // 电话号码（用于 send_sms, make_call）
    @SerializedName("phone_number")
    val phoneNumber: String? = null,

    // 是否直接拨打电话（用于 make_call）
    @SerializedName("direct_call")
    val directCall: Boolean? = null,

    // 短信内容（用于 send_sms）
    @SerializedName("message")
    val message: String? = null,

    // 分页参数（用于 get_all_contacts 等）
    @SerializedName("limit")
    val limit: Int? = null,

    @SerializedName("offset")
    val offset: Int? = null,

    // 通知标题（用于 post_notification）
    @SerializedName("title")
    val title: String? = null,

    // 日历事件参数（用于 add_event）
    @SerializedName("start_time")
    val startTime: Long? = null,

    @SerializedName("end_time")
    val endTime: Long? = null,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("location")
    val location: String? = null,

    @SerializedName("all_day")
    val allDay: Boolean? = null,

    @SerializedName("reminder_minutes")
    val reminderMinutes: Int? = null,

    // 是否为最后一个请求（如果为 true，则执行后延时截屏并返回 base64 图片）
    @SerializedName("isLast")
    val isLast: Boolean? = null,

    // 选择器字符串（用于 selector_action）
    @SerializedName("selector")
    val selector: String? = null,

    // 选择器操作类型：click, long_click, scroll_forward, scroll_backward, focus
    @SerializedName("selector_action")
    val selectorAction: String? = null,

    // 是否获取完整快照（用于 get_node_info_at_position）
    // 如果为 true，返回完整窗口信息，忽略 x, y 参数
    @SerializedName("full_snapshot")
    val fullSnapshot: Boolean? = null,

    // 用户介入相关参数（用于 show_intervention, hide_intervention）
    // 介入请求 ID
    @SerializedName("interventionId")
    val interventionId: String? = null,

    // 场景类型：login_input, captcha_slide, captcha_sms, payment_confirm, custom
    @SerializedName("sceneType")
    val sceneType: String? = null,

    // 超时时间（秒）
    @SerializedName("timeout")
    val timeout: Int? = null,

    // ==================== 定时任务相关参数 ====================

    // 意图类型：reminder | automation
    @SerializedName("intent_type")
    val intent_type: String? = null,

    // 任务内容
    @SerializedName("task_content")
    val task_content: String? = null,

    // 时间表达式（自然语言）
    @SerializedName("schedule_expression")
    val schedule_expression: String? = null,

    // 时间信息（解析后的结构化数据）
    @SerializedName("time_info")
    val time_info: Any? = null
)

/**
 * 响应数据类
 */
data class CommandResponse(
    @SerializedName("action")
    val action: String,

    @SerializedName("success")
    val success: Boolean,

    @SerializedName("message")
    val message: String? = null,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
