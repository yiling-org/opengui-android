package top.yling.ozx.guiagent.a11y

import android.graphics.Rect
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.FastQuery
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke
import li.songe.selector.getBooleanInvoke
import top.yling.ozx.guiagent.a11y.data.*
import top.yling.ozx.guiagent.MyAccessibilityService

/**
 * 无障碍上下文
 * 提供节点缓存、遍历、查询等功能
 * 
 * 参考 GKD: li.songe.gkd.a11y.A11yContext
 */
class A11yContext {
    companion object {
        private const val MAX_CACHE_SIZE = MAX_DESCENDANTS_SIZE
    }

    // 节点缓存
    private var childCache = LruCache<Pair<AccessibilityNodeInfo, Int>, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    private var indexCache = LruCache<AccessibilityNodeInfo, Int>(MAX_CACHE_SIZE)
    private var parentCache = LruCache<AccessibilityNodeInfo, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    @Volatile
    var rootCache: AccessibilityNodeInfo? = null
        private set

    /**
     * 检查节点是否未过期
     */
    private val AccessibilityNodeInfo?.notExpiredNode: AccessibilityNodeInfo?
        get() {
            if (this != null) {
                val expiryMillis = if (text == null) 2000L else 1000L
                if (isExpired(expiryMillis)) {
                    return null
                }
            }
            return this
        }

    /**
     * 清除子节点缓存
     */
    private fun clearChildCache(node: AccessibilityNodeInfo) {
        repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
            childCache.remove(node to i)?.let {
                clearChildCache(it)
            }
        }
    }

    /**
     * 清除所有节点缓存
     */
    fun clearNodeCache() {
        try {
            childCache.evictAll()
            parentCache.evictAll()
            indexCache.evictAll()
            rootCache = null
        } catch (_: Exception) {
            childCache = LruCache(MAX_CACHE_SIZE)
            indexCache = LruCache(MAX_CACHE_SIZE)
            parentCache = LruCache(MAX_CACHE_SIZE)
            rootCache = null
        }
    }

    /**
     * 获取活动窗口根节点
     */
    private fun getA11Root(): AccessibilityNodeInfo? {
        return MyAccessibilityService.instance?.rootInActiveWindow
    }

    /**
     * 获取子节点
     */
    private fun getA11Child(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        return node.getChild(index)?.setGeneratedTime()
    }

    /**
     * 获取父节点
     */
    private fun getA11Parent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return node.parent?.setGeneratedTime()
    }

    /**
     * 通过文本查找节点
     */
    private fun getA11ByText(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        return node.findAccessibilityNodeInfosByText(value).apply {
            forEach { it.setGeneratedTime() }
        }
    }

    /**
     * 通过 ID 查找节点
     */
    private fun getA11ById(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        return node.findAccessibilityNodeInfosByViewId(value).apply {
            forEach { it.setGeneratedTime() }
        }
    }

    /**
     * 获取快速查询节点
     */
    private fun getFastQueryNodes(
        node: AccessibilityNodeInfo,
        fastQuery: FastQuery
    ): List<AccessibilityNodeInfo> {
        return when (fastQuery) {
            is FastQuery.Id -> getA11ById(node, fastQuery.value)
            is FastQuery.Text -> getA11ByText(node, fastQuery.value)
            is FastQuery.Vid -> getA11ById(node, "${node.packageName}:id/${fastQuery.value}")
        }
    }

    /**
     * 获取缓存的根节点
     */
    private fun getCacheRoot(node: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {
        if (rootCache.notExpiredNode == null) {
            rootCache = getA11Root()
        }
        if (node == rootCache) return null
        return rootCache
    }

    /**
     * 获取缓存的父节点
     */
    private fun getCacheParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (getCacheRoot() == node) {
            return null
        }
        parentCache[node].notExpiredNode?.let { return it }
        return getA11Parent(node).apply {
            if (this != null) {
                parentCache.put(node, this)
            } else {
                rootCache = node
            }
        }
    }

    /**
     * 获取缓存的子节点
     */
    private fun getCacheChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        if (index !in 0 until node.childCount) {
            return null
        }
        return childCache[node to index].notExpiredNode ?: getA11Child(node, index)?.also { child ->
            indexCache.put(child, index)
            parentCache.put(child, node)
            childCache.put(node to index, child)
        }
    }

    /**
     * 获取纯索引
     */
    private fun getPureIndex(node: AccessibilityNodeInfo): Int? {
        return indexCache[node]
    }

    /**
     * 获取缓存的索引
     */
    private fun getCacheIndex(node: AccessibilityNodeInfo): Int {
        indexCache[node]?.let { return it }
        getCacheChildren(getCacheParent(node)).forEachIndexed { index, child ->
            if (child == node) {
                indexCache.put(node, index)
                return index
            }
        }
        return 0
    }

    /**
     * 获取缓存的深度
     */
    private fun getCacheDepth(node: AccessibilityNodeInfo): Int {
        var p: AccessibilityNodeInfo = node
        var depth = 0
        while (true) {
            val p2 = getCacheParent(p)
            if (p2 != null) {
                p = p2
                depth++
            } else {
                break
            }
        }
        return depth
    }

    /**
     * 获取缓存的子节点序列
     */
    private fun getCacheChildren(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> {
        if (node == null) return emptySequence()
        return sequence {
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { index ->
                val child = getCacheChild(node, index) ?: return@sequence
                yield(child)
            }
        }
    }

    // 临时 Rect 用于获取边界
    private val tempRect = Rect()
    private var tempRectNode: AccessibilityNodeInfo? = null
    private fun getTempRect(n: AccessibilityNodeInfo): Rect {
        if (n !== tempRectNode) {
            n.getBoundsInScreen(tempRect)
            tempRectNode = n
        }
        return tempRect
    }

    // 临时 vid
    private var tempVid: CharSequence? = null
    private var tempVidNode: AccessibilityNodeInfo? = null
    private fun getTempVid(n: AccessibilityNodeInfo): CharSequence? {
        if (n !== tempVidNode) {
            tempVid = n.getVid()
            tempVidNode = n
        }
        return tempVid
    }

    /**
     * 获取节点属性
     */
    private fun getCacheAttr(node: AccessibilityNodeInfo, name: String): Any? = when (name) {
        "id" -> node.viewIdResourceName
        "vid" -> getTempVid(node)

        "name" -> node.className
        "text" -> node.text
        "desc" -> node.contentDescription

        "clickable" -> node.isClickable
        "focusable" -> node.isFocusable
        "checkable" -> node.isCheckable
        "checked" -> node.compatChecked

        "editable" -> node.isEditable
        "longClickable" -> node.isLongClickable
        "visibleToUser" -> node.isVisibleToUser

        "left" -> getTempRect(node).left
        "top" -> getTempRect(node).top
        "right" -> getTempRect(node).right
        "bottom" -> getTempRect(node).bottom

        "width" -> getTempRect(node).width()
        "height" -> getTempRect(node).height()

        "index" -> getCacheIndex(node)
        "depth" -> getCacheDepth(node)
        "childCount" -> node.childCount

        "parent" -> getCacheParent(node)

        else -> null
    }

    /**
     * Selector Transform 适配器
     */
    val transform = Transform(
        getAttr = { target, name ->
            when (target) {
                is QueryContext<*> -> when (name) {
                    "prev" -> target.prev
                    "current" -> target.current
                    else -> getCacheAttr(target.current as AccessibilityNodeInfo, name)
                }

                is AccessibilityNodeInfo -> getCacheAttr(target, name)
                is CharSequence -> getCharSequenceAttr(target, name)
                else -> null
            }
        },
        getInvoke = { target, name, args ->
            when (target) {
                is AccessibilityNodeInfo -> when (name) {
                    "getChild" -> {
                        getCacheChild(target, args.getInt())
                    }
                    else -> null
                }

                is QueryContext<*> -> when (name) {
                    "getPrev" -> {
                        args.getInt().let { target.getPrev(it) }
                    }
                    "getChild" -> {
                        getCacheChild(target.current as AccessibilityNodeInfo, args.getInt())
                    }
                    else -> null
                }

                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)
                is Boolean -> getBooleanInvoke(target, name, args)

                else -> null
            }
        },
        getName = { node -> node.className },
        getChildren = ::getCacheChildren,
        getParent = ::getCacheParent,
        getRoot = ::getCacheRoot,
        getDescendants = { node ->
            sequence {
                val stack = getCacheChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                do {
                    val top = stack.removeAt(stack.lastIndex)
                    yield(top)
                    for (childNode in getCacheChildren(top)) {
                        tempNodes.add(childNode)
                    }
                    if (tempNodes.isNotEmpty()) {
                        for (i in tempNodes.size - 1 downTo 0) {
                            stack.add(tempNodes[i])
                        }
                        tempNodes.clear()
                    }
                } while (stack.isNotEmpty())
            }.take(MAX_DESCENDANTS_SIZE)
        },
        traverseChildren = { node, connectExpression ->
            sequence {
                repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { offset ->
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    if (connectExpression.checkOffset(offset)) {
                        val child = getCacheChild(node, offset) ?: return@sequence
                        yield(child)
                    }
                }
            }
        },
        traverseBeforeBrothers = { node, connectExpression ->
            sequence {
                val parentVal = getCacheParent(node) ?: return@sequence
                val index = getPureIndex(node)
                if (index != null) {
                    var i = index - 1
                    var offset = 0
                    while (0 <= i && i < parentVal.childCount) {
                        connectExpression.maxOffset?.let { maxOffset ->
                            if (offset > maxOffset) return@sequence
                        }
                        if (connectExpression.checkOffset(offset)) {
                            val child = getCacheChild(parentVal, i) ?: return@sequence
                            yield(child)
                        }
                        i--
                        offset++
                    }
                } else {
                    val list = getCacheChildren(parentVal).takeWhile { it != node }.toMutableList()
                    list.reverse()
                    yieldAll(list.filterIndexed { i, _ ->
                        connectExpression.checkOffset(i)
                    })
                }
            }
        },
        traverseAfterBrothers = { node, connectExpression ->
            val parentVal = getCacheParent(node)
            if (parentVal != null) {
                val index = getPureIndex(node)
                if (index != null) {
                    sequence {
                        var i = index + 1
                        var offset = 0
                        while (0 <= i && i < parentVal.childCount) {
                            connectExpression.maxOffset?.let { maxOffset ->
                                if (offset > maxOffset) return@sequence
                            }
                            if (connectExpression.checkOffset(offset)) {
                                val child = getCacheChild(parentVal, i) ?: return@sequence
                                yield(child)
                            }
                            i++
                            offset++
                        }
                    }
                } else {
                    getCacheChildren(parentVal).dropWhile { it != node }
                        .drop(1)
                        .let {
                            if (connectExpression.maxOffset != null) {
                                it.take(connectExpression.maxOffset!! + 1)
                            } else {
                                it
                            }
                        }
                        .filterIndexed { i, _ ->
                            connectExpression.checkOffset(i)
                        }
                }
            } else {
                emptySequence()
            }
        },
        traverseDescendants = { node, connectExpression ->
            sequence {
                val stack = getCacheChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                var offset = 0
                do {
                    val top = stack.removeAt(stack.lastIndex)
                    if (connectExpression.checkOffset(offset)) {
                        yield(top)
                    }
                    offset++
                    if (offset > MAX_DESCENDANTS_SIZE) {
                        return@sequence
                    }
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    for (childNode in getCacheChildren(top)) {
                        tempNodes.add(childNode)
                    }
                    if (tempNodes.isNotEmpty()) {
                        for (i in tempNodes.size - 1 downTo 0) {
                            stack.add(tempNodes[i])
                        }
                        tempNodes.clear()
                    }
                } while (stack.isNotEmpty())
            }
        },
        traverseFastQueryDescendants = { node, list ->
            sequence {
                for (fastQuery in list) {
                    val nodes = getFastQueryNodes(node, fastQuery)
                    nodes.forEach { childNode ->
                        yield(childNode)
                    }
                }
            }
        }
    )

    /**
     * 查询节点
     */
    fun querySelector(
        node: AccessibilityNodeInfo,
        selector: Selector,
        option: MatchOption = MatchOption(),
    ): AccessibilityNodeInfo? {
        if (selector.isMatchRoot) {
            return selector.match(
                getCacheRoot() ?: return null,
                transform,
                option
            )
        }
        selector.match(node, transform, option)?.let {
            return it
        }
        return transform.querySelector(node, selector, option)
    }

    /**
     * 查询所有匹配的节点
     */
    fun querySelectorAll(
        node: AccessibilityNodeInfo,
        selector: Selector,
        option: MatchOption = MatchOption(),
    ): Sequence<AccessibilityNodeInfo> {
        return transform.querySelectorAll(node, selector, option)
    }
}

/**
 * 辅助扩展函数
 */
private fun List<Any>.getInt(i: Int = 0) = get(i) as Int

/**
 * 全局 A11yContext 实例
 */
val a11yContext = A11yContext()

