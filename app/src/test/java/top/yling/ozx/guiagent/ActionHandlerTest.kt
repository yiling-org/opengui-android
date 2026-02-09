package top.yling.ozx.guiagent

import org.junit.Assert.*
import org.junit.Test
import top.yling.ozx.guiagent.websocket.CommandParams
import top.yling.ozx.guiagent.websocket.handler.*

/**
 * ActionHandler 各类 Handler 逻辑测试
 *
 * 测试各 Handler 的基本属性和参数提取逻辑：
 * - Handler actionName 与类名的对应关系
 * - CommandParams 参数提取
 * - Handler 接口契约验证
 *
 * 注意：由于 ActionHandler.handle() 需要 Android Context 构建 ActionContext，
 * 此处测试聚焦于纯 JVM 可验证的逻辑。涉及无障碍服务的行为需要集成测试。
 */
class ActionHandlerTest {

    // ========== Handler actionName 与类名对应关系 ==========

    @Test
    fun `HeartbeatHandler的actionName应为heartbeat`() {
        assertEquals("heartbeat", HeartbeatHandler().actionName)
    }

    @Test
    fun `PingHandler的actionName应为ping`() {
        assertEquals("ping", PingHandler().actionName)
    }

    @Test
    fun `StatusHandler的actionName应为status`() {
        assertEquals("status", StatusHandler().actionName)
    }

    @Test
    fun `IdleHandler的actionName应为idle`() {
        assertEquals("idle", IdleHandler().actionName)
    }

    @Test
    fun `ClickHandler的actionName应为click`() {
        assertEquals("click", ClickHandler().actionName)
    }

    @Test
    fun `LongPressHandler的actionName应为long_press`() {
        assertEquals("long_press", LongPressHandler().actionName)
    }

    @Test
    fun `TypeHandler的actionName应为type`() {
        assertEquals("type", TypeHandler().actionName)
    }

    @Test
    fun `ScrollHandler的actionName应为scroll`() {
        assertEquals("scroll", ScrollHandler().actionName)
    }

    @Test
    fun `DragHandler的actionName应为drag`() {
        assertEquals("drag", DragHandler().actionName)
    }

    @Test
    fun `CopyTextHandler的actionName应为copy_text`() {
        assertEquals("copy_text", CopyTextHandler().actionName)
    }

    @Test
    fun `PressHomeHandler的actionName应为press_home`() {
        assertEquals("press_home", PressHomeHandler().actionName)
    }

    @Test
    fun `PressBackHandler的actionName应为press_back`() {
        assertEquals("press_back", PressBackHandler().actionName)
    }

    @Test
    fun `PressRecentsHandler的actionName应为press_recents`() {
        assertEquals("press_recents", PressRecentsHandler().actionName)
    }

    @Test
    fun `OpenAppHandler的actionName应为open_app`() {
        assertEquals("open_app", OpenAppHandler().actionName)
    }

    @Test
    fun `OpenUrlHandler的actionName应为open_url`() {
        assertEquals("open_url", OpenUrlHandler().actionName)
    }

    @Test
    fun `ScreenshotHandler的actionName应为screenshot`() {
        assertEquals("screenshot", ScreenshotHandler().actionName)
    }

    @Test
    fun `ScheduleTaskHandler的actionName应为schedule_task`() {
        assertEquals("schedule_task", ScheduleTaskHandler().actionName)
    }

    // ========== CommandParams 参数提取测试 ==========

    @Test
    fun `CommandParams应正确存储click坐标`() {
        val params = CommandParams(x = 500.0f, y = 300.0f)

        assertEquals("x 应为 500", 500.0f, params.x!!, 0.01f)
        assertEquals("y 应为 300", 300.0f, params.y!!, 0.01f)
    }

    @Test
    fun `CommandParams应正确存储drag的四个坐标`() {
        val params = CommandParams(x = 100f, y = 200f, x2 = 300f, y2 = 400f)

        assertEquals("x 应正确", 100f, params.x!!, 0.01f)
        assertEquals("y 应正确", 200f, params.y!!, 0.01f)
        assertEquals("x2 应正确", 300f, params.x2!!, 0.01f)
        assertEquals("y2 应正确", 400f, params.y2!!, 0.01f)
    }

    @Test
    fun `CommandParams应正确存储type的content`() {
        val params = CommandParams(content = "测试输入")

        assertEquals("content 应正确", "测试输入", params.content)
    }

    @Test
    fun `CommandParams应正确存储scroll的方向和距离`() {
        val params = CommandParams(x = 500f, y = 1000f, direction = "down", distance = 800f)

        assertEquals("direction 应正确", "down", params.direction)
        assertEquals("distance 应正确", 800f, params.distance!!, 0.01f)
    }

    @Test
    fun `CommandParams应正确存储open_app的包名`() {
        val params = CommandParams(packageName = "com.example.app", appName = "测试App")

        assertEquals("packageName 应正确", "com.example.app", params.packageName)
        assertEquals("appName 应正确", "测试App", params.appName)
    }

    @Test
    fun `CommandParams默认值应全为null`() {
        val params = CommandParams()

        assertNull("x 默认应为 null", params.x)
        assertNull("y 默认应为 null", params.y)
        assertNull("content 默认应为 null", params.content)
        assertNull("direction 默认应为 null", params.direction)
        assertNull("packageName 默认应为 null", params.packageName)
        assertNull("isLast 默认应为 null", params.isLast)
        assertNull("selector 默认应为 null", params.selector)
        assertNull("duration 默认应为 null", params.duration)
    }

    @Test
    fun `CommandParams的isLast标记应正确`() {
        val paramsTrue = CommandParams(x = 100f, y = 200f, isLast = true)
        val paramsFalse = CommandParams(x = 100f, y = 200f, isLast = false)
        val paramsNull = CommandParams(x = 100f, y = 200f)

        assertTrue("isLast 为 true", paramsTrue.isLast == true)
        assertFalse("isLast 为 false", paramsFalse.isLast == true)
        assertNull("isLast 为 null", paramsNull.isLast)
    }

    @Test
    fun `CommandParams的duration应正确存储`() {
        val params = CommandParams(duration = 1500L)
        assertEquals("duration 应为 1500", 1500L, params.duration)
    }

    // ========== Handler 接口契约验证 ==========

    @Test
    fun `所有Handler都实现了ActionHandler接口`() {
        val handlers = listOf(
            HeartbeatHandler(),
            PingHandler(),
            ClickHandler(),
            TypeHandler(),
            ScrollHandler(),
            DragHandler(),
            PressHomeHandler(),
            PressBackHandler(),
            OpenAppHandler(),
            ScreenshotHandler(),
            IdleHandler(),
            LongPressHandler(),
            CopyTextHandler()
        )

        handlers.forEach { handler ->
            assertTrue(
                "${handler.javaClass.simpleName} 应实现 ActionHandler 接口",
                handler is ActionHandler
            )
        }
    }

    @Test
    fun `每个Handler的actionName应与其功能匹配`() {
        // 验证 Handler 类名和 actionName 之间的逻辑对应关系
        val handlerActionPairs = mapOf(
            ClickHandler::class.simpleName to "click",
            LongPressHandler::class.simpleName to "long_press",
            TypeHandler::class.simpleName to "type",
            ScrollHandler::class.simpleName to "scroll",
            DragHandler::class.simpleName to "drag",
            PressHomeHandler::class.simpleName to "press_home",
            PressBackHandler::class.simpleName to "press_back",
            OpenAppHandler::class.simpleName to "open_app",
            HeartbeatHandler::class.simpleName to "heartbeat",
            PingHandler::class.simpleName to "ping",
            IdleHandler::class.simpleName to "idle"
        )

        val allHandlers = listOf(
            ClickHandler(), LongPressHandler(), TypeHandler(), ScrollHandler(),
            DragHandler(), PressHomeHandler(), PressBackHandler(), OpenAppHandler(),
            HeartbeatHandler(), PingHandler(), IdleHandler()
        )

        allHandlers.forEach { handler ->
            val expected = handlerActionPairs[handler.javaClass.simpleName]
            assertNotNull("${handler.javaClass.simpleName} 应有预期的 actionName", expected)
            assertEquals(
                "${handler.javaClass.simpleName} 的 actionName 不匹配",
                expected, handler.actionName
            )
        }
    }

    // ========== 参数组合场景 ==========

    @Test
    fun `click操作需要x和y坐标或selector`() {
        // 验证 click 操作的两种参数模式
        val coordParams = CommandParams(x = 500f, y = 300f)
        assertNotNull("坐标模式：x 应存在", coordParams.x)
        assertNotNull("坐标模式：y 应存在", coordParams.y)

        val selectorParams = CommandParams(selector = "@Button[text='确定']")
        assertNotNull("选择器模式：selector 应存在", selectorParams.selector)
    }

    @Test
    fun `drag操作需要四个坐标参数`() {
        val params = CommandParams(x = 100f, y = 200f, x2 = 300f, y2 = 400f, duration = 500L)

        assertNotNull("x 应存在", params.x)
        assertNotNull("y 应存在", params.y)
        assertNotNull("x2 应存在", params.x2)
        assertNotNull("y2 应存在", params.y2)
        assertNotNull("duration 应存在", params.duration)
    }

    @Test
    fun `scroll操作需要坐标和方向`() {
        val params = CommandParams(x = 500f, y = 1000f, direction = "up", distance = 600f)

        assertNotNull("x 应存在", params.x)
        assertNotNull("y 应存在", params.y)
        assertNotNull("direction 应存在", params.direction)
        assertNotNull("distance 应存在", params.distance)
    }

    @Test
    fun `send_sms操作需要phone_number和message`() {
        val params = CommandParams(phoneNumber = "13800138000", message = "测试短信")

        assertNotNull("phoneNumber 应存在", params.phoneNumber)
        assertNotNull("message 应存在", params.message)
    }

    @Test
    fun `add_event操作需要标题和时间参数`() {
        val params = CommandParams(
            title = "会议",
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            description = "项目会议",
            location = "会议室",
            allDay = false,
            reminderMinutes = 15
        )

        assertNotNull("title 应存在", params.title)
        assertNotNull("startTime 应存在", params.startTime)
        assertNotNull("endTime 应存在", params.endTime)
    }
}
