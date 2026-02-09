package top.yling.ozx.guiagent.a11y.data

import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

/**
 * 节点信息
 * 参考 GKD: li.songe.gkd.data.NodeInfo
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class NodeInfo(
    val id: Int,              // 节点ID（深度优先遍历顺序）
    val pid: Int,             // 父节点ID（-1表示根节点）
    val idQf: Boolean?,       // 是否支持ID快速查询
    val textQf: Boolean?,     // 是否支持Text快速查询
    val attr: AttrInfo,       // 节点属性详情
)

/**
 * 临时节点数据（用于构建节点树）
 */
internal data class TempNodeData(
    val node: AccessibilityNodeInfo,
    val parent: TempNodeData?,
    val index: Int,
    val depth: Int,
) {
    var id = 0
    val attr = AttrInfo.fromNode(node, index, depth)
    var children: List<TempNodeData> = emptyList()

    var idQfInit = false
    var idQf: Boolean? = null
        set(value) {
            field = value
            idQfInit = true
        }
    var textQfInit = false
    var textQf: Boolean? = null
        set(value) {
            field = value
            textQfInit = true
        }
}

/**
 * 最大节点数限制
 */
const val MAX_KEEP_SIZE = 5000

/**
 * 最大子节点数限制
 */
const val MAX_CHILD_SIZE = 512

/**
 * 最大后代节点数限制
 */
const val MAX_DESCENDANTS_SIZE = 4096

/**
 * 获取子节点序列
 */
private fun getChildren(node: AccessibilityNodeInfo) = sequence {
    repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
        val child = node.getChild(i) ?: return@sequence
        yield(child)
    }
}

/**
 * 将 AccessibilityNodeInfo 树转换为 NodeInfo 列表
 */
fun nodeToNodeInfoList(root: AccessibilityNodeInfo?): List<NodeInfo> {
    if (root == null) {
        return emptyList()
    }
    val nodes = mutableListOf<TempNodeData>()
    
    // 收集所有节点
    val stack = mutableListOf<TempNodeData>()
    var times = 0
    stack.add(TempNodeData(root, null, 0, 0))
    while (stack.isNotEmpty()) {
        times++
        val node = stack.removeAt(stack.lastIndex)
        node.id = times - 1
        val children = getChildren(node.node).mapIndexed { i, child ->
            TempNodeData(
                child, node, i, node.depth + 1
            )
        }.toList()
        node.children = children
        nodes.add(node)
        repeat(children.size) { i ->
            stack.add(children[children.size - i - 1])
        }
        if (times > MAX_KEEP_SIZE) {
            break
        }
    }

    // 构建快速查询标记
    val idQfCache = mutableMapOf<String, List<AccessibilityNodeInfo>>()
    val textQfCache = mutableMapOf<String, List<AccessibilityNodeInfo>>()
    var idTextQf = false
    
    fun updateQf(n: TempNodeData) {
        if (!n.idQfInit && !n.attr.id.isNullOrEmpty()) {
            n.idQf = (idQfCache[n.attr.id]
                ?: root.findAccessibilityNodeInfosByViewId(n.attr.id)).apply {
                idQfCache[n.attr.id!!] = this
            }.any { t -> t == n.node }
        }

        if (!n.textQfInit && !n.attr.text.isNullOrEmpty()) {
            n.textQf = (textQfCache[n.attr.text]
                ?: root.findAccessibilityNodeInfosByText(n.attr.text)).apply {
                textQfCache[n.attr.text!!] = this
            }.any { t -> t == n.node }
        }

        if (n.idQf == true && n.textQf == true) {
            idTextQf = true
        }

        // 传播标记
        if (!n.idQfInit && n.idQf != null) {
            n.parent?.children?.forEach { c ->
                c.idQf = n.idQf
                if (idTextQf) {
                    c.textQf = n.textQf
                }
            }
            if (n.idQf == true) {
                var p = n.parent
                while (p != null && !p.idQfInit) {
                    p.idQf = n.idQf
                    if (idTextQf) {
                        p.textQf = n.textQf
                    }
                    p = p.parent
                    p?.children?.forEach { bro ->
                        bro.idQf = n.idQf
                        if (idTextQf) {
                            bro.textQf = n.textQf
                        }
                    }
                }
            }
        }

        n.idQfInit = true
        n.textQfInit = true
    }
    
    // 先处理叶子节点
    for (i in (nodes.size - 1) downTo 0) {
        val n = nodes[i]
        if (n.children.isEmpty()) {
            updateQf(n)
        }
    }
    // 再处理非叶子节点
    for (i in (nodes.size - 1) downTo 0) {
        val n = nodes[i]
        if (n.children.isNotEmpty()) {
            updateQf(n)
        }
    }

    return nodes.map { n ->
        NodeInfo(
            id = n.id,
            pid = n.parent?.id ?: -1,
            idQf = n.idQf,
            textQf = n.textQf,
            attr = n.attr
        )
    }
}

