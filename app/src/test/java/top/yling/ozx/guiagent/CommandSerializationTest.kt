package top.yling.ozx.guiagent

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import top.yling.ozx.guiagent.websocket.AccessibilityCommand
import top.yling.ozx.guiagent.websocket.CommandParams
import top.yling.ozx.guiagent.websocket.CommandResponse

/**
 * 消息序列化/反序列化测试
 *
 * 测试 AccessibilityCommand、CommandParams、CommandResponse 的
 * JSON 序列化与反序列化正确性，确保消息在客户端与服务端之间传输时不丢失数据。
 */
class CommandSerializationTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = Gson()
    }

    // ========== AccessibilityCommand 序列化测试 ==========

    @Test
    fun `应正确序列化和反序列化click指令`() {
        val command = AccessibilityCommand(
            action = "click",
            params = CommandParams(x = 500.0f, y = 300.0f)
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("action 应正确", "click", parsed.action)
        assertNotNull("params 应存在", parsed.params)
        assertEquals("x 坐标应正确", 500.0f, parsed.params!!.x!!, 0.01f)
        assertEquals("y 坐标应正确", 300.0f, parsed.params!!.y!!, 0.01f)
    }

    @Test
    fun `应正确序列化和反序列化type指令`() {
        val command = AccessibilityCommand(
            action = "type",
            params = CommandParams(content = "测试输入内容")
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("action 应正确", "type", parsed.action)
        assertEquals("content 应正确", "测试输入内容", parsed.params?.content)
    }

    @Test
    fun `应正确序列化和反序列化scroll指令`() {
        val command = AccessibilityCommand(
            action = "scroll",
            params = CommandParams(
                x = 500f,
                y = 1000f,
                direction = "down",
                distance = 800f
            )
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("action 应正确", "scroll", parsed.action)
        assertEquals("direction 应正确", "down", parsed.params?.direction)
        assertEquals("distance 应正确", 800f, parsed.params?.distance!!, 0.01f)
    }

    @Test
    fun `应正确序列化和反序列化drag指令`() {
        val command = AccessibilityCommand(
            action = "drag",
            params = CommandParams(
                x = 100f, y = 200f,
                x2 = 300f, y2 = 400f,
                duration = 500L
            )
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("action 应正确", "drag", parsed.action)
        assertEquals("x 应正确", 100f, parsed.params?.x!!, 0.01f)
        assertEquals("y 应正确", 200f, parsed.params?.y!!, 0.01f)
        assertEquals("x2 应正确", 300f, parsed.params?.x2!!, 0.01f)
        assertEquals("y2 应正确", 400f, parsed.params?.y2!!, 0.01f)
        assertEquals("duration 应正确", 500L, parsed.params?.duration)
    }

    @Test
    fun `应正确序列化和反序列化open_app指令`() {
        val command = AccessibilityCommand(
            action = "open_app",
            params = CommandParams(packageName = "com.example.app", appName = "测试应用")
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("action 应正确", "open_app", parsed.action)
        assertEquals("packageName 应正确", "com.example.app", parsed.params?.packageName)
        assertEquals("appName 应正确", "测试应用", parsed.params?.appName)
    }

    @Test
    fun `应正确序列化和反序列化带isLast的指令`() {
        val command = AccessibilityCommand(
            action = "click",
            params = CommandParams(x = 100f, y = 200f, isLast = true)
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertTrue("isLast 应为 true", parsed.params?.isLast == true)
    }

    @Test
    fun `应正确处理无params的指令`() {
        val command = AccessibilityCommand(action = "press_back")

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("action 应正确", "press_back", parsed.action)
        assertNull("params 应为 null", parsed.params)
    }

    @Test
    fun `应正确处理带type字段的指令`() {
        val command = AccessibilityCommand(
            action = "click",
            params = CommandParams(x = 100f, y = 200f),
            type = "accessibility_command"
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("type 字段应正确", "accessibility_command", parsed.type)
    }

    // ========== CommandParams 详细字段测试 ==========

    @Test
    fun `应正确序列化send_sms参数`() {
        val params = CommandParams(
            phoneNumber = "13800138000",
            message = "测试短信内容"
        )

        val json = gson.toJson(params)
        val parsed = gson.fromJson(json, CommandParams::class.java)

        assertEquals("phone_number 应正确", "13800138000", parsed.phoneNumber)
        assertEquals("message 应正确", "测试短信内容", parsed.message)
    }

    @Test
    fun `应正确序列化日历事件参数`() {
        val params = CommandParams(
            title = "团队会议",
            description = "讨论项目进度",
            location = "会议室A",
            startTime = 1700000000000L,
            endTime = 1700003600000L,
            allDay = false,
            reminderMinutes = 15
        )

        val json = gson.toJson(params)
        val parsed = gson.fromJson(json, CommandParams::class.java)

        assertEquals("title 应正确", "团队会议", parsed.title)
        assertEquals("description 应正确", "讨论项目进度", parsed.description)
        assertEquals("location 应正确", "会议室A", parsed.location)
        assertEquals("startTime 应正确", 1700000000000L, parsed.startTime)
        assertEquals("endTime 应正确", 1700003600000L, parsed.endTime)
        assertEquals("allDay 应正确", false, parsed.allDay)
        assertEquals("reminderMinutes 应正确", 15, parsed.reminderMinutes)
    }

    @Test
    fun `应正确序列化selector相关参数`() {
        val params = CommandParams(
            selector = "@Button[text='确定']",
            selectorAction = "click"
        )

        val json = gson.toJson(params)
        val parsed = gson.fromJson(json, CommandParams::class.java)

        assertEquals("selector 应正确", "@Button[text='确定']", parsed.selector)
        assertEquals("selectorAction 应正确", "click", parsed.selectorAction)
    }

    @Test
    fun `应正确序列化用户介入参数`() {
        val params = CommandParams(
            interventionId = "intervention_001",
            sceneType = "login_input",
            timeout = 120
        )

        val json = gson.toJson(params)
        val parsed = gson.fromJson(json, CommandParams::class.java)

        assertEquals("interventionId 应正确", "intervention_001", parsed.interventionId)
        assertEquals("sceneType 应正确", "login_input", parsed.sceneType)
        assertEquals("timeout 应正确", 120, parsed.timeout)
    }

    @Test
    fun `应正确序列化定时任务参数`() {
        val params = CommandParams(
            intent_type = "reminder",
            task_content = "提醒喝水",
            schedule_expression = "每天下午3点"
        )

        val json = gson.toJson(params)
        val parsed = gson.fromJson(json, CommandParams::class.java)

        assertEquals("intent_type 应正确", "reminder", parsed.intent_type)
        assertEquals("task_content 应正确", "提醒喝水", parsed.task_content)
        assertEquals("schedule_expression 应正确", "每天下午3点", parsed.schedule_expression)
    }

    // ========== CommandResponse 序列化测试 ==========

    @Test
    fun `应正确序列化成功响应`() {
        val response = CommandResponse(
            action = "click",
            success = true,
            message = "点击成功 (500, 300)"
        )

        val json = gson.toJson(response)
        val parsed = gson.fromJson(json, CommandResponse::class.java)

        assertEquals("action 应正确", "click", parsed.action)
        assertTrue("success 应为 true", parsed.success)
        assertEquals("message 应正确", "点击成功 (500, 300)", parsed.message)
        assertTrue("timestamp 应大于0", parsed.timestamp > 0)
    }

    @Test
    fun `应正确序列化失败响应`() {
        val response = CommandResponse(
            action = "type",
            success = false,
            message = "输入失败，请确保有输入框获得焦点"
        )

        val json = gson.toJson(response)
        val parsed = gson.fromJson(json, CommandResponse::class.java)

        assertFalse("success 应为 false", parsed.success)
        assertTrue("message 应包含错误信息", parsed.message!!.contains("输入失败"))
    }

    // ========== 从JSON字符串解析测试 ==========

    @Test
    fun `应正确从服务端JSON解析click指令`() {
        val serverJson = """
            {
                "action": "click",
                "params": {
                    "x": 720.0,
                    "y": 1600.0,
                    "isLast": true
                }
            }
        """.trimIndent()

        val command = gson.fromJson(serverJson, AccessibilityCommand::class.java)

        assertEquals("action 应为 click", "click", command.action)
        assertEquals("x 应正确", 720.0f, command.params?.x!!, 0.01f)
        assertEquals("y 应正确", 1600.0f, command.params?.y!!, 0.01f)
        assertTrue("isLast 应为 true", command.params?.isLast == true)
    }

    @Test
    fun `应正确从服务端JSON解析带reqId的指令`() {
        val serverJson = """
            {
                "reqId": "req_12345",
                "action": "type",
                "params": {
                    "content": "苹果",
                    "isLast": true
                }
            }
        """.trimIndent()

        // 模拟 WebSocketClient.parseCommand 的逻辑
        @Suppress("UNCHECKED_CAST")
        val jsonObject = gson.fromJson(serverJson, Map::class.java) as? Map<String, Any?>
        val reqId = jsonObject?.get("reqId") as? String
        val action = jsonObject?.get("action") as? String
        val paramsObj = jsonObject?.get("params")
        val params = if (paramsObj != null) {
            val paramsJson = gson.toJson(paramsObj)
            gson.fromJson(paramsJson, CommandParams::class.java)
        } else null

        assertEquals("reqId 应正确", "req_12345", reqId)
        assertEquals("action 应为 type", "type", action)
        assertEquals("content 应正确", "苹果", params?.content)
        assertTrue("isLast 应为 true", params?.isLast == true)
    }

    @Test
    fun `应正确处理空JSON对象`() {
        val emptyJson = "{}"
        val command = gson.fromJson(emptyJson, AccessibilityCommand::class.java)

        // Gson 会使用默认值
        assertNull("action 应为 null", command.action)
    }

    @Test
    fun `应正确处理未知字段的JSON`() {
        val jsonWithExtra = """
            {
                "action": "click",
                "unknown_field": "some_value",
                "params": {
                    "x": 100,
                    "y": 200,
                    "extra_param": "extra"
                }
            }
        """.trimIndent()

        val command = gson.fromJson(jsonWithExtra, AccessibilityCommand::class.java)

        assertEquals("action 应正确", "click", command.action)
        assertEquals("x 应正确", 100f, command.params?.x!!, 0.01f)
        // Gson 默认忽略未知字段，不应报错
    }

    @Test
    fun `应正确处理中文content的序列化`() {
        val command = AccessibilityCommand(
            action = "type",
            params = CommandParams(content = "你好世界！Hello World! 特殊字符: @#\$%")
        )

        val json = gson.toJson(command)
        val parsed = gson.fromJson(json, AccessibilityCommand::class.java)

        assertEquals("中文 content 应正确", "你好世界！Hello World! 特殊字符: @#\$%", parsed.params?.content)
    }

    // ========== CommandResult 测试 ==========

    @Test
    fun `CommandResult应正确携带附加数据`() {
        val result = top.yling.ozx.guiagent.websocket.CommandResult(
            success = true,
            message = "截图成功",
            data = mapOf(
                "image" to "base64data",
                "imageWidth" to 1440,
                "imageHeight" to 3200,
                "packageName" to "com.example.app",
                "activityName" to "MainActivity"
            )
        )

        assertTrue("success 应为 true", result.success)
        assertEquals("message 应正确", "截图成功", result.message)
        assertNotNull("data 应存在", result.data)
        assertEquals("imageWidth 应正确", 1440, result.data!!["imageWidth"])
        assertEquals("packageName 应正确", "com.example.app", result.data!!["packageName"])
    }

    @Test
    fun `CommandResult默认data应为null`() {
        val result = top.yling.ozx.guiagent.websocket.CommandResult(
            success = false,
            message = "操作失败"
        )

        assertNull("默认 data 应为 null", result.data)
    }
}

