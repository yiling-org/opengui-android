package top.yling.ozx.guiagent.a11y.data

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

/**
 * 节点属性信息
 * 参考 GKD: li.songe.gkd.data.AttrInfo
 */
@Serializable
@OptIn(kotlinx.serialization.InternalSerializationApi::class)
data class AttrInfo(
    val id: String?,           // viewIdResourceName
    val vid: String?,          // 简化的 viewId (去掉包名前缀)
    val name: String?,         // className
    val text: String?,         // 文本内容
    val desc: String?,         // contentDescription

    val clickable: Boolean,    // 可点击
    val focusable: Boolean,    // 可聚焦
    val checkable: Boolean,    // 可选中
    val checked: Boolean?,     // 选中状态
    val editable: Boolean,     // 可编辑
    val longClickable: Boolean,// 可长按
    val visibleToUser: Boolean,// 用户可见

    val left: Int,             // 左边界
    val top: Int,              // 上边界
    val right: Int,            // 右边界
    val bottom: Int,           // 下边界

    val width: Int,            // 宽度
    val height: Int,           // 高度

    val childCount: Int,       // 子节点数
    val index: Int,            // 在父节点中的索引
    val depth: Int,            // 深度
) {
    companion object {
        /**
         * 注意：rect 不是线程安全的，不要在多线程中使用
         */
        private val rect = Rect()

        /**
         * 从 AccessibilityNodeInfo 转换为 AttrInfo
         */
        fun fromNode(
            node: AccessibilityNodeInfo,
            index: Int,
            depth: Int,
        ): AttrInfo {
            node.getBoundsInScreen(rect)
            val appId = node.packageName?.toString() ?: ""
            val id: String? = node.viewIdResourceName
            val idPrefix = "$appId:id/"
            val vid = if (id != null && id.startsWith(idPrefix)) {
                id.substring(idPrefix.length)
            } else {
                null
            }
            return AttrInfo(
                id = id,
                vid = vid,
                name = node.className?.toString(),
                text = node.text?.toString(),
                desc = node.contentDescription?.toString(),

                clickable = node.isClickable,
                focusable = node.isFocusable,
                checkable = node.isCheckable,
                checked = node.isChecked,
                editable = node.isEditable,
                longClickable = node.isLongClickable,
                visibleToUser = node.isVisibleToUser,

                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,

                width = rect.width(),
                height = rect.height(),

                childCount = node.childCount,

                index = index,
                depth = depth,
            )
        }
    }
}

