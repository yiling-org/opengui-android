package top.yling.ozx.guiagent

import com.google.gson.Gson
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * WebSocket 消息解析单元测试
 */
class WebSocketMessageParserTest {

    private lateinit var gson: Gson

    @Before
    fun setup() {
        gson = Gson()
    }

    @Test
    fun `应正确解析agent_response消息`() {
        val message = """
            {
                "type": "agent_response",
                "data": "正在处理您的请求...",
                "timestamp": 1766136467370
            }
        """.trimIndent()

        val jsonObject = gson.fromJson(message, Map::class.java) as? Map<String, Any?>
        val type = jsonObject?.get("type") as? String
        val data = jsonObject?.get("data") as? String

        assertNotNull("消息应被成功解析", jsonObject)
        assertEquals("类型应为 agent_response", "agent_response", type)
        assertNotNull("data 应存在", data)
        assertEquals("data 内容应正确", "正在处理您的请求...", data)
    }
    
    @Test
    fun `应正确拼接多个agent_response消息`() {
        val messages = listOf(
            """{"type": "agent_response", "data": "第一部分", "timestamp": 1766136467370}""",
            """{"type": "agent_response", "data": "第二部分", "timestamp": 1766136467371}""",
            """{"type": "agent_response", "data": "第三部分", "timestamp": 1766136467372}"""
        )
        
        val buffer = StringBuilder()
        
        messages.forEach { message ->
            val jsonObject = gson.fromJson(message, Map::class.java) as? Map<String, Any?>
            val data = jsonObject?.get("data") as? String
            
            if (!data.isNullOrEmpty()) {
                buffer.append(data)
            }
        }
        
        assertEquals("应正确拼接所有片段", "第一部分第二部分第三部分", buffer.toString())
    }

    @Test
    fun `应正确解析agent_complete消息并检测attempt_completion标签`() {
        val messageWithCompletion = """
            {
                "type": "agent_complete",
                "data": "<attempt_completion>任务已完成</attempt_completion>",
                "timestamp": 1766136467373
            }
        """.trimIndent()

        val jsonObject = gson.fromJson(messageWithCompletion, Map::class.java) as? Map<String, Any?>
        val type = jsonObject?.get("type") as? String
        val data = jsonObject?.get("data") as? String

        assertNotNull("消息应被成功解析", jsonObject)
        assertEquals("类型应为 agent_complete", "agent_complete", type)
        assertNotNull("data 应存在", data)
        assertTrue("应包含 attempt_completion 标签", data?.contains("attempt_completion") == true)
    }
    
    @Test
    fun `应正确处理完整的消息流程`() {
        // 模拟接收多个 agent_response 和最后的 agent_complete
        val messages = listOf(
            """{"type": "agent_response", "data": "我正在", "timestamp": 1766136467370}""",
            """{"type": "agent_response", "data": "处理您的", "timestamp": 1766136467371}""",
            """{"type": "agent_response", "data": "请求...", "timestamp": 1766136467372}""",
            """{"type": "agent_complete", "data": "<attempt_completion>完成</attempt_completion>", "timestamp": 1766136467373}"""
        )
        
        val buffer = StringBuilder()
        var isCompleted = false
        var hasCompletionTag = false
        
        messages.forEach { message ->
            val jsonObject = gson.fromJson(message, Map::class.java) as? Map<String, Any?>
            val type = jsonObject?.get("type") as? String
            val data = jsonObject?.get("data") as? String
            
            when (type) {
                "agent_response" -> {
                    if (!data.isNullOrEmpty()) {
                        buffer.append(data)
                    }
                }
                "agent_complete" -> {
                    if (!data.isNullOrEmpty()) {
                        buffer.append(data)
                    }
                    isCompleted = true
                    hasCompletionTag = buffer.toString().contains("attempt_completion")
                }
            }
        }
        
        val fullText = buffer.toString()
        
        assertTrue("应标记为已完成", isCompleted)
        assertTrue("应检测到 completion 标签", hasCompletionTag)
        assertTrue("完整文本应包含所有片段", fullText.contains("我正在处理您的请求..."))
        assertTrue("完整文本应包含 attempt_completion", fullText.contains("attempt_completion"))
    }

    @Test
    fun `应正确识别不包含attempt_completion的agent_complete消息`() {
        val messageWithoutCompletion = """
            {
                "type": "agent_complete",
                "data": "这是一个普通的响应，没有完成标签",
                "timestamp": 1766136467373
            }
        """.trimIndent()

        val jsonObject = gson.fromJson(messageWithoutCompletion, Map::class.java) as? Map<String, Any?>
        val data = jsonObject?.get("data") as? String

        assertNotNull("data 应存在", data)
        assertFalse("不应包含 attempt_completion 标签", data?.contains("attempt_completion") == true)
    }
    
    @Test
    fun `应正确处理没有完成标签的完整消息流程`() {
        // 模拟接收多个 agent_response 和最后的 agent_complete（但没有 completion 标签）
        val messages = listOf(
            """{"type": "agent_response", "data": "正在执行", "timestamp": 1766136467370}""",
            """{"type": "agent_response", "data": "操作...", "timestamp": 1766136467371}""",
            """{"type": "agent_complete", "data": "操作完成", "timestamp": 1766136467372}"""
        )
        
        val buffer = StringBuilder()
        var isCompleted = false
        var hasCompletionTag = false
        
        messages.forEach { message ->
            val jsonObject = gson.fromJson(message, Map::class.java) as? Map<String, Any?>
            val type = jsonObject?.get("type") as? String
            val data = jsonObject?.get("data") as? String
            
            when (type) {
                "agent_response" -> {
                    if (!data.isNullOrEmpty()) {
                        buffer.append(data)
                    }
                }
                "agent_complete" -> {
                    if (!data.isNullOrEmpty()) {
                        buffer.append(data)
                    }
                    isCompleted = true
                    hasCompletionTag = buffer.toString().contains("attempt_completion")
                }
            }
        }
        
        assertTrue("应标记为已完成", isCompleted)
        assertFalse("不应检测到 completion 标签", hasCompletionTag)
        assertEquals("完整文本应正确拼接", "正在执行操作...操作完成", buffer.toString())
    }

    @Test
    fun `应正确解析error消息`() {
        val errorMessage = """
            {
                "type": "error",
                "data": {
                    "message": "处理请求时发生错误"
                }
            }
        """.trimIndent()

        val jsonObject = gson.fromJson(errorMessage, Map::class.java) as? Map<String, Any?>
        val type = jsonObject?.get("type") as? String

        assertNotNull("消息应被成功解析", jsonObject)
        assertEquals("类型应为 error", "error", type)
    }

    @Test
    fun `应正确构造cancel_task消息`() {
        val cancelMessage = gson.toJson(mapOf(
            "type" to "cancel_task",
            "data" to mapOf(
                "userId" to "android-user",
                "agentId" to "coder",
                "reason" to "user_cancelled"
            )
        ))

        val jsonObject = gson.fromJson(cancelMessage, Map::class.java) as? Map<String, Any?>
        val type = jsonObject?.get("type") as? String
        val data = jsonObject?.get("data") as? Map<String, Any?>

        assertNotNull("消息应被成功构造", jsonObject)
        assertEquals("类型应为 cancel_task", "cancel_task", type)
        assertNotNull("data 应存在", data)
        assertEquals("userId 应正确", "android-user", data?.get("userId"))
        assertEquals("agentId 应正确", "coder", data?.get("agentId"))
        assertEquals("reason 应正确", "user_cancelled", data?.get("reason"))
    }

    @Test
    fun `应正确处理空消息`() {
        val emptyMessage = "{}"
        val jsonObject = gson.fromJson(emptyMessage, Map::class.java) as? Map<String, Any?>

        assertNotNull("空消息应被成功解析", jsonObject)
        assertTrue("空消息应为空 Map", jsonObject?.isEmpty() == true)
    }

    @Test
    fun `应正确处理包含中文内容的消息`() {
        val chineseMessage = """
            {
                "type": "agent_response",
                "data": {
                    "content": "正在处理您的中文请求，请稍候..."
                }
            }
        """.trimIndent()

        val jsonObject = gson.fromJson(chineseMessage, Map::class.java) as? Map<String, Any?>
        val data = jsonObject?.get("data") as? Map<String, Any?>
        val content = data?.get("content") as? String

        assertNotNull("消息应被成功解析", jsonObject)
        assertNotNull("content 应存在", content)
        assertTrue("content 应包含中文", content?.contains("中文") == true)
    }

    @Test
    fun `应正确识别attempt_completion的不同变体`() {
        val testCases = listOf(
            "<attempt_completion>完成</attempt_completion>",
            "前文<attempt_completion>完成</attempt_completion>后文",
            "<attempt_completion>\n多行\n内容\n</attempt_completion>",
            "  <attempt_completion>  带空格  </attempt_completion>  "
        )

        testCases.forEach { content ->
            assertTrue(
                "应识别 attempt_completion 标签: $content",
                content.contains("attempt_completion")
            )
        }
    }
}

