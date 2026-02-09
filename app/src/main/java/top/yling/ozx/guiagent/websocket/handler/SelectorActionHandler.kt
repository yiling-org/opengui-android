package top.yling.ozx.guiagent.websocket.handler

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.a11y.NodeExplorer
import top.yling.ozx.guiagent.a11y.a11yContext
import top.yling.ozx.guiagent.shizuku.ShizukuApi
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 选择器操作处理器
 */
class SelectorActionHandler : ActionHandler {
    companion object {
        private const val TAG = "SelectorActionHandler"
    }

    override val actionName = "selector_action"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val selectorString = context.params?.selector ?: run {
            callback(CommandResult(false, "缺少参数 selector"))
            return
        }
        val actionType = context.params.selectorAction ?: "click"

        // 解析选择器
        val selector = try {
            li.songe.selector.Selector.parse(selectorString)
        } catch (e: Exception) {
            callback(CommandResult(false, "选择器解析失败: ${e.message}"))
            return
        }

        // 获取根节点
        val root = MyAccessibilityService.instance?.rootInActiveWindow ?: run {
            callback(CommandResult(false, "无法获取当前窗口"))
            return
        }

        // 查找节点
        val node = a11yContext.querySelector(root, selector) ?: run {
            callback(CommandResult(false, "未找到匹配的节点: $selectorString"))
            return
        }

        // 执行操作
        val actionResult = when (actionType.lowercase()) {
            "click" -> {
                // 优先使用 Shizuku 点击节点中心坐标
                val shizukuContext = ShizukuApi.getContextOrNull()
                if (shizukuContext != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val centerX = rect.centerX().toFloat()
                    val centerY = rect.centerY().toFloat()
                    Log.d(TAG, "使用 Shizuku 点击: ($centerX, $centerY)")
                    shizukuContext.tap(centerX, centerY)
                } else {
                    // 回退到 AccessibilityNodeInfo.ACTION_CLICK
                    Log.d(TAG, "Shizuku 不可用，使用 ACTION_CLICK")
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            "long_click" -> {
                // 优先使用 Shizuku 长按节点中心坐标
                val shizukuContext = ShizukuApi.getContextOrNull()
                if (shizukuContext != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val centerX = rect.centerX().toFloat()
                    val centerY = rect.centerY().toFloat()
                    val duration = context.params.duration ?: 1000L
                    Log.d(TAG, "使用 Shizuku 长按: ($centerX, $centerY), duration=$duration")
                    shizukuContext.tap(centerX, centerY, duration)
                } else {
                    // 回退到 AccessibilityNodeInfo.ACTION_LONG_CLICK
                    Log.d(TAG, "Shizuku 不可用，使用 ACTION_LONG_CLICK")
                    node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                }
            }
            "scroll_forward" -> {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            "scroll_backward" -> {
                node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            }
            "focus" -> {
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            }
            "clear_focus" -> {
                node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS)
            }
            "select" -> {
                node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
            }
            else -> {
                callback(CommandResult(false, "不支持的操作类型: $actionType"))
                return
            }
        }

        // 获取节点信息用于返回
        val nodeInfo = NodeExplorer.getFullNodeInfo(node)
        val nodeInfoMap = mapOf(
            "className" to nodeInfo.node.attr.name,
            "text" to nodeInfo.node.attr.text,
            "vid" to nodeInfo.node.attr.vid,
            "bounds" to nodeInfo.position
        )

        if (actionResult) {
            context.wrappedCallback(CommandResult(true, "$actionType 操作成功", mapOf(
                "selector" to selectorString,
                "action" to actionType,
                "node" to nodeInfoMap
            )))
        } else {
            context.wrappedCallback(CommandResult(false, "$actionType 操作失败", mapOf(
                "selector" to selectorString,
                "action" to actionType,
                "node" to nodeInfoMap
            )))
        }
    }
}
