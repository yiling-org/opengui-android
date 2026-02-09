package top.yling.ozx.guiagent

import org.junit.Test
import org.junit.Assert.*
import top.yling.ozx.guiagent.websocket.handler.*

/**
 * CommandExecutor Handler 注册机制测试
 * 验证所有 Handler 正确注册，action 名称唯一且完整
 */
class CommandExecutorRegistrationTest {

    // 所有已注册的 Handler 实例（与 CommandExecutor.init 中的注册表保持一致）
    private val allHandlers: List<ActionHandler> = listOf(
        // 基础操作
        HeartbeatHandler(),
        PingHandler(),
        StatusHandler(),
        IdleHandler(),
        // 窗口信息
        GetWindowIdHandler(),
        // 交互操作
        ClickHandler(),
        LongPressHandler(),
        CopyTextHandler(),
        ScrollHandler(),
        DragHandler(),
        TypeHandler(),
        // 系统按键
        PressHomeHandler(),
        PressBackHandler(),
        PressRecentsHandler(),
        OpenNotificationsHandler(),
        OpenQuickSettingsHandler(),
        // 截图和节点
        ScreenshotHandler(),
        GetNodeInfoAtPositionHandler(),
        SelectorActionHandler(),
        // 应用和 URL
        OpenAppHandler(),
        OpenUrlHandler(),
        // 通讯
        SendSmsHandler(),
        MakeCallHandler(),
        GetAllContactsHandler(),
        // 通知和日历
        PostNotificationHandler(),
        GetTodayEventsHandler(),
        AddEventHandler(),
        // 用户介入
        ShowInterventionHandler(),
        HideInterventionHandler(),
        // 定时任务
        ScheduleTaskHandler()
    )

    @Test
    fun `所有Handler的actionName应唯一`() {
        val actionNames = allHandlers.map { it.actionName }
        val uniqueNames = actionNames.toSet()

        assertEquals(
            "存在重复的 actionName: ${actionNames.groupBy { it }.filter { it.value.size > 1 }.keys}",
            actionNames.size,
            uniqueNames.size
        )
    }

    @Test
    fun `应包含所有基础操作Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 heartbeat", "heartbeat" in actionNames)
        assertTrue("应包含 ping", "ping" in actionNames)
        assertTrue("应包含 status", "status" in actionNames)
        assertTrue("应包含 idle", "idle" in actionNames)
    }

    @Test
    fun `应包含所有交互操作Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 click", "click" in actionNames)
        assertTrue("应包含 long_press", "long_press" in actionNames)
        assertTrue("应包含 scroll", "scroll" in actionNames)
        assertTrue("应包含 drag", "drag" in actionNames)
        assertTrue("应包含 type", "type" in actionNames)
        assertTrue("应包含 copy_text", "copy_text" in actionNames)
    }

    @Test
    fun `应包含所有系统按键Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 press_home", "press_home" in actionNames)
        assertTrue("应包含 press_back", "press_back" in actionNames)
        assertTrue("应包含 press_recents", "press_recents" in actionNames)
        assertTrue("应包含 open_notifications", "open_notifications" in actionNames)
        assertTrue("应包含 open_quick_settings", "open_quick_settings" in actionNames)
    }

    @Test
    fun `应包含截图和节点相关Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 screenshot", "screenshot" in actionNames)
        assertTrue("应包含 get_node_info_at_position", "get_node_info_at_position" in actionNames)
        assertTrue("应包含 selector_action", "selector_action" in actionNames)
    }

    @Test
    fun `应包含应用和URL操作Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 open_app", "open_app" in actionNames)
        assertTrue("应包含 open_url", "open_url" in actionNames)
    }

    @Test
    fun `应包含通讯相关Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 send_sms", "send_sms" in actionNames)
        assertTrue("应包含 make_call", "make_call" in actionNames)
        assertTrue("应包含 get_all_contacts", "get_all_contacts" in actionNames)
    }

    @Test
    fun `应包含通知和日历Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 post_notification", "post_notification" in actionNames)
        assertTrue("应包含 get_today_events", "get_today_events" in actionNames)
        assertTrue("应包含 add_event", "add_event" in actionNames)
    }

    @Test
    fun `应包含用户介入Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 show_intervention", "show_intervention" in actionNames)
        assertTrue("应包含 hide_intervention", "hide_intervention" in actionNames)
    }

    @Test
    fun `应包含定时任务Handler`() {
        val actionNames = allHandlers.map { it.actionName }.toSet()

        assertTrue("应包含 schedule_task", "schedule_task" in actionNames)
    }

    @Test
    fun `Handler总数应与CommandExecutor注册数一致`() {
        // CommandExecutor.init 中注册了 30 个 Handler
        assertEquals("Handler 总数应为 30", 30, allHandlers.size)
    }

    @Test
    fun `每个Handler的actionName不应为空`() {
        allHandlers.forEach { handler ->
            assertFalse(
                "${handler.javaClass.simpleName} 的 actionName 不应为空",
                handler.actionName.isEmpty()
            )
        }
    }

    @Test
    fun `actionName应为小写字母和下划线组成`() {
        val validPattern = Regex("^[a-z_]+$")
        allHandlers.forEach { handler ->
            assertTrue(
                "${handler.javaClass.simpleName} 的 actionName '${handler.actionName}' 应只包含小写字母和下划线",
                validPattern.matches(handler.actionName)
            )
        }
    }

    @Test
    fun `Handler可通过actionName构建查找表`() {
        // 模拟 CommandExecutor 中的 associateBy 逻辑
        val handlerMap = allHandlers.associateBy { it.actionName }

        assertEquals("查找表大小应与 Handler 数量一致", allHandlers.size, handlerMap.size)

        // 验证通过 actionName 可以正确找到对应 Handler
        assertEquals("click 应映射到 ClickHandler", ClickHandler::class.java, handlerMap["click"]?.javaClass)
        assertEquals("type 应映射到 TypeHandler", TypeHandler::class.java, handlerMap["type"]?.javaClass)
        assertEquals("scroll 应映射到 ScrollHandler", ScrollHandler::class.java, handlerMap["scroll"]?.javaClass)
        assertEquals("open_app 应映射到 OpenAppHandler", OpenAppHandler::class.java, handlerMap["open_app"]?.javaClass)
        assertEquals("heartbeat 应映射到 HeartbeatHandler", HeartbeatHandler::class.java, handlerMap["heartbeat"]?.javaClass)
    }
}

