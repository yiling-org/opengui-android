package top.yling.ozx.guiagent

import org.junit.Assert.*
import org.junit.Test
import top.yling.ozx.guiagent.a11y.NodeExplorer
import top.yling.ozx.guiagent.a11y.data.AttrInfo
import top.yling.ozx.guiagent.a11y.data.NodeInfo

/**
 * NodeExplorer 节点查找算法测试
 *
 * 测试 findNodeByPositionFromSnapshot 方法：
 * - 基本坐标匹配
 * - 最小面积优先选择
 * - 可见性和可点击性过滤
 * - 边界条件处理
 */
class NodeExplorerSnapshotTest {

    /**
     * 创建测试用的 NodeInfo
     */
    private fun createNodeInfo(
        id: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        visibleToUser: Boolean = true,
        clickable: Boolean = true,
        text: String? = null,
        desc: String? = null,
        name: String? = "android.widget.Button"
    ): NodeInfo {
        return NodeInfo(
            id = id,
            pid = -1,
            idQf = null,
            textQf = null,
            attr = AttrInfo(
                id = null,
                vid = null,
                name = name,
                text = text,
                desc = desc,
                clickable = clickable,
                focusable = false,
                checkable = false,
                checked = null,
                editable = false,
                longClickable = false,
                visibleToUser = visibleToUser,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                width = right - left,
                height = bottom - top,
                childCount = 0,
                index = 0,
                depth = 0
            )
        )
    }

    // ========== 基本查找测试 ==========

    @Test
    fun `应找到包含坐标的节点`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 300, bottom = 300)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 200f)

        assertNotNull("应找到节点", result)
        assertEquals("应为正确的节点", 0, result!!.id)
    }

    @Test
    fun `坐标不在任何节点内时应返回null`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 300, bottom = 300)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 500f, 500f)

        assertNull("坐标不在节点内应返回 null", result)
    }

    @Test
    fun `空节点列表应返回null`() {
        val result = NodeExplorer.findNodeByPositionFromSnapshot(emptyList(), 100f, 100f)

        assertNull("空列表应返回 null", result)
    }

    // ========== 最小面积优先 ==========

    @Test
    fun `多个重叠节点应返回面积最小的`() {
        val nodes = listOf(
            // 大节点 (400x400 = 160000)
            createNodeInfo(id = 0, left = 0, top = 0, right = 400, bottom = 400),
            // 中节点 (200x200 = 40000)
            createNodeInfo(id = 1, left = 100, top = 100, right = 300, bottom = 300),
            // 小节点 (100x100 = 10000)
            createNodeInfo(id = 2, left = 150, top = 150, right = 250, bottom = 250)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 200f)

        assertNotNull("应找到节点", result)
        assertEquals("应返回面积最小的节点", 2, result!!.id)
    }

    @Test
    fun `面积相同的节点应返回后出现的`() {
        val nodes = listOf(
            // 两个面积相同的节点 (100x100 = 10000)
            createNodeInfo(id = 0, left = 100, top = 100, right = 200, bottom = 200),
            createNodeInfo(id = 1, left = 100, top = 100, right = 200, bottom = 200)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 150f, 150f)

        assertNotNull("应找到节点", result)
        // 两个面积相同，由于 area < minArea（严格小于），第一个会被选中
        assertEquals("面积相同时应返回第一个匹配的", 0, result!!.id)
    }

    // ========== 可见性和可点击性过滤 ==========

    @Test
    fun `不可见的节点应被忽略`() {
        val nodes = listOf(
            // 不可见但面积更小的节点
            createNodeInfo(id = 0, left = 150, top = 150, right = 250, bottom = 250, visibleToUser = false),
            // 可见但面积更大的节点
            createNodeInfo(id = 1, left = 100, top = 100, right = 300, bottom = 300, visibleToUser = true)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 200f)

        assertNotNull("应找到可见节点", result)
        assertEquals("应返回可见节点", 1, result!!.id)
    }

    @Test
    fun `不可点击的节点应被忽略`() {
        val nodes = listOf(
            // 不可点击但面积更小的节点
            createNodeInfo(id = 0, left = 150, top = 150, right = 250, bottom = 250, clickable = false),
            // 可点击但面积更大的节点
            createNodeInfo(id = 1, left = 100, top = 100, right = 300, bottom = 300, clickable = true)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 200f)

        assertNotNull("应找到可点击节点", result)
        assertEquals("应返回可点击节点", 1, result!!.id)
    }

    @Test
    fun `既不可见又不可点击的节点应被忽略`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 300, bottom = 300,
                visibleToUser = false, clickable = false)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 200f)

        assertNull("不可见不可点击的节点应被忽略", result)
    }

    @Test
    fun `所有节点都不匹配时应返回null`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 300, bottom = 300, visibleToUser = false),
            createNodeInfo(id = 1, left = 100, top = 100, right = 300, bottom = 300, clickable = false)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 200f)

        assertNull("所有节点都不匹配时应返回 null", result)
    }

    // ========== 边界条件 ==========

    @Test
    fun `坐标在节点左上角边界应匹配`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 300, bottom = 300)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 100f, 100f)

        assertNotNull("左上角边界应匹配", result)
    }

    @Test
    fun `坐标在节点右下角边界应匹配`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 300, bottom = 300)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 300f, 300f)

        assertNotNull("右下角边界应匹配", result)
    }

    @Test
    fun `坐标刚好在节点外一像素应不匹配`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 300, bottom = 300)
        )

        val resultRight = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 301f, 200f)
        val resultBottom = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 301f)
        val resultLeft = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 99f, 200f)
        val resultTop = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 99f)

        assertNull("右侧超出应不匹配", resultRight)
        assertNull("底部超出应不匹配", resultBottom)
        assertNull("左侧超出应不匹配", resultLeft)
        assertNull("顶部超出应不匹配", resultTop)
    }

    @Test
    fun `零面积的节点应被忽略`() {
        val nodes = listOf(
            // 宽度为0的节点（left == right）
            createNodeInfo(id = 0, left = 200, top = 100, right = 200, bottom = 300),
            // 正常节点
            createNodeInfo(id = 1, left = 100, top = 100, right = 300, bottom = 300)
        )

        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 200f, 200f)

        assertNotNull("应找到非零面积节点", result)
        assertEquals("应返回非零面积节点", 1, result!!.id)
    }

    // ========== 复杂场景 ==========

    @Test
    fun `模拟真实UI层级查找最精确的按钮`() {
        val nodes = listOf(
            // 根布局 (全屏 1440x3200)
            createNodeInfo(
                id = 0, left = 0, top = 0, right = 1440, bottom = 3200,
                name = "android.widget.FrameLayout"
            ),
            // 内容区域
            createNodeInfo(
                id = 1, left = 0, top = 100, right = 1440, bottom = 3100,
                name = "android.widget.LinearLayout"
            ),
            // 按钮行
            createNodeInfo(
                id = 2, left = 100, top = 500, right = 1340, bottom = 650,
                name = "android.widget.LinearLayout"
            ),
            // 目标按钮（最小可点击元素）
            createNodeInfo(
                id = 3, left = 500, top = 520, right = 700, bottom = 630,
                text = "确定",
                name = "android.widget.Button"
            ),
            // 相邻按钮
            createNodeInfo(
                id = 4, left = 740, top = 520, right = 940, bottom = 630,
                text = "取消",
                name = "android.widget.Button"
            )
        )

        // 点击 "确定" 按钮区域
        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 600f, 575f)

        assertNotNull("应找到确定按钮", result)
        assertEquals("应返回确定按钮", 3, result!!.id)
        assertEquals("按钮文本应为确定", "确定", result.attr.text)
    }

    @Test
    fun `模拟点击列表项`() {
        val nodes = listOf(
            // 列表容器
            createNodeInfo(
                id = 0, left = 0, top = 0, right = 1440, bottom = 3200,
                name = "android.widget.RecyclerView"
            ),
            // 第一项
            createNodeInfo(
                id = 1, left = 0, top = 0, right = 1440, bottom = 200,
                text = "苹果"
            ),
            // 第二项
            createNodeInfo(
                id = 2, left = 0, top = 200, right = 1440, bottom = 400,
                text = "香蕉"
            ),
            // 第三项
            createNodeInfo(
                id = 3, left = 0, top = 400, right = 1440, bottom = 600,
                text = "橘子"
            )
        )

        // 点击第二项
        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 720f, 300f)

        assertNotNull("应找到列表项", result)
        assertEquals("应返回第二项（面积最小）", 2, result!!.id)
        assertEquals("文本应为香蕉", "香蕉", result.attr.text)
    }

    @Test
    fun `浮点坐标应正确转换为整数进行比较`() {
        val nodes = listOf(
            createNodeInfo(id = 0, left = 100, top = 100, right = 200, bottom = 200)
        )

        // 浮点坐标 100.7 转换为 int = 100，刚好在边界上
        val result = NodeExplorer.findNodeByPositionFromSnapshot(nodes, 100.7f, 100.3f)

        assertNotNull("浮点坐标转换后应匹配", result)
    }
}

