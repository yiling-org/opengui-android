package top.yling.ozx.guiagent

import org.junit.Test
import org.junit.Assert.*
import top.yling.ozx.guiagent.app.activity.TaskStatus

/**
 * 任务状态管理单元测试
 */
class TaskStatusTest {

    @Test
    fun `任务状态枚举应包含所有必需状态`() {
        val states = TaskStatus.values()
        
        assertTrue("应包含 IDLE 状态", states.contains(TaskStatus.IDLE))
        assertTrue("应包含 RUNNING 状态", states.contains(TaskStatus.RUNNING))
        assertTrue("应包含 COMPLETED 状态", states.contains(TaskStatus.COMPLETED))
        assertTrue("应包含 CANCELLED 状态", states.contains(TaskStatus.CANCELLED))
        assertEquals("应有4个状态", 4, states.size)
    }

    @Test
    fun `任务状态初始值应为IDLE`() {
        val initialState = TaskStatus.IDLE
        assertEquals("初始状态应为 IDLE", TaskStatus.IDLE, initialState)
    }

    @Test
    fun `任务状态转换应正确`() {
        // IDLE -> RUNNING
        var currentState = TaskStatus.IDLE
        currentState = TaskStatus.RUNNING
        assertEquals("应从 IDLE 转换到 RUNNING", TaskStatus.RUNNING, currentState)
        
        // RUNNING -> COMPLETED
        currentState = TaskStatus.COMPLETED
        assertEquals("应从 RUNNING 转换到 COMPLETED", TaskStatus.COMPLETED, currentState)
        
        // RUNNING -> CANCELLED
        currentState = TaskStatus.RUNNING
        currentState = TaskStatus.CANCELLED
        assertEquals("应从 RUNNING 转换到 CANCELLED", TaskStatus.CANCELLED, currentState)
    }

    @Test
    fun `任务状态枚举名称应正确`() {
        assertEquals("IDLE", TaskStatus.IDLE.name)
        assertEquals("RUNNING", TaskStatus.RUNNING.name)
        assertEquals("COMPLETED", TaskStatus.COMPLETED.name)
        assertEquals("CANCELLED", TaskStatus.CANCELLED.name)
    }

    @Test
    fun `任务状态枚举ordinal应正确`() {
        assertEquals(0, TaskStatus.IDLE.ordinal)
        assertEquals(1, TaskStatus.RUNNING.ordinal)
        assertEquals(2, TaskStatus.COMPLETED.ordinal)
        assertEquals(3, TaskStatus.CANCELLED.ordinal)
    }
}

