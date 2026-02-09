package top.yling.ozx.guiagent.a11y

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import top.yling.ozx.guiagent.a11y.data.MAX_CHILD_SIZE
import top.yling.ozx.guiagent.util.AndroidTarget

/**
 * AccessibilityNodeInfo 扩展方法
 * 参考 GKD: li.songe.gkd.a11y.A11yExt
 */

// 事件类型常量
const val STATE_CHANGED = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
const val CONTENT_CHANGED = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

// 节点时间戳 key
private const val A11Y_NODE_TIME_KEY = "generatedTime"

/**
 * 设置节点生成时间
 */
fun AccessibilityNodeInfo.setGeneratedTime(): AccessibilityNodeInfo {
    extras.putLong(A11Y_NODE_TIME_KEY, System.currentTimeMillis())
    return this
}

/**
 * 检查节点是否已过期
 */
fun AccessibilityNodeInfo.isExpired(expiryMillis: Long): Boolean {
    val generatedTime = extras.getLong(A11Y_NODE_TIME_KEY, -1)
    if (generatedTime == -1L) {
        return true
    }
    return (System.currentTimeMillis() - generatedTime) > expiryMillis
}

/**
 * 获取节点的简化 viewId（去掉包名前缀）
 */
fun AccessibilityNodeInfo.getVid(): CharSequence? {
    val id = viewIdResourceName ?: return null
    val appId = packageName ?: return null
    if (id.startsWith(appId) && id.startsWith(":id/", appId.length)) {
        return id.subSequence(
            appId.length + ":id/".length,
            id.length
        )
    }
    return null
}

/**
 * 兼容获取 checked 状态
 */
val AccessibilityNodeInfo.compatChecked: Boolean?
    get() = if (AndroidTarget.BAKLAVA) {
        when (checked) {
            AccessibilityNodeInfo.CHECKED_STATE_TRUE -> true
            AccessibilityNodeInfo.CHECKED_STATE_FALSE -> false
            AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> null
            else -> null
        }
    } else {
        @Suppress("DEPRECATION")
        isChecked
    }

/**
 * 获取子节点序列
 */
fun AccessibilityNodeInfo.getChildSequence(): Sequence<AccessibilityNodeInfo> = sequence {
    repeat(childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
        val child = getChild(i) ?: return@sequence
        yield(child)
    }
}

/**
 * 获取父节点链（从当前节点到根节点）
 */
fun AccessibilityNodeInfo.getAncestorChain(): List<AccessibilityNodeInfo> {
    val chain = mutableListOf<AccessibilityNodeInfo>()
    var current: AccessibilityNodeInfo? = this
    while (current != null) {
        chain.add(current)
        current = current.parent
    }
    return chain
}

/**
 * 获取节点深度
 */
fun AccessibilityNodeInfo.getDepth(): Int {
    var depth = 0
    var current: AccessibilityNodeInfo? = parent
    while (current != null) {
        depth++
        current = current.parent
    }
    return depth
}

/**
 * 判断事件是否有用
 */
private const val interestedEvents = STATE_CHANGED or CONTENT_CHANGED
fun AccessibilityEvent?.isUseful(): Boolean {
    return (this != null && packageName != null && className != null && eventType and interestedEvents != 0)
}

/**
 * 安全获取事件源节点
 */
val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() = if (className == null) {
        null
    } else {
        try {
            source?.setGeneratedTime()
        } catch (_: Exception) {
            null
        }
    }

/**
 * A11y 事件数据类
 */
data class A11yEvent(
    val type: Int,
    val time: Long,
    val appId: String,
    val name: String,
    val event: AccessibilityEvent,
) {
    val safeSource: AccessibilityNodeInfo?
        get() = event.safeSource

    fun sameAs(other: A11yEvent): Boolean {
        if (other === this) return true
        return type == other.type && appId == other.appId && name == other.name
    }
}

/**
 * 将 AccessibilityEvent 转换为 A11yEvent
 */
fun AccessibilityEvent.toA11yEvent(): A11yEvent? {
    val appId = packageName ?: return null
    val b = className ?: return null
    return A11yEvent(
        type = eventType,
        time = System.currentTimeMillis(),
        appId = appId.toString(),
        name = b.toString(),
        event = this,
    )
}

