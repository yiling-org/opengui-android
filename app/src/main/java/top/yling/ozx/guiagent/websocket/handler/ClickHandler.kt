package top.yling.ozx.guiagent.websocket.handler

import android.graphics.Rect
import top.yling.ozx.guiagent.AgentOverlayService
import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.a11y.a11yContext
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 点击处理器
 *
 * @author shanwb
 */
class ClickHandler : ActionHandler {
    override val actionName = "click"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        // 优先使用 selector 查询坐标
        val selectorString = context.params?.selector
        val (x, y) = if (selectorString != null) {
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

            // 获取节点中心坐标
            val rect = Rect()
            node.getBoundsInScreen(rect)
            Pair(rect.centerX().toFloat(), rect.centerY().toFloat())
        } else {
            // 使用传入的 x, y 坐标
            context.extractXY() ?: run {
                callback(CommandResult(false, "缺少参数: 需要 x/y 坐标 或 selector 选择器"))
                return
            }
        }

        // 显示点击反馈效果
        AgentOverlayService.instance?.showClickFeedback(x, y)

        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        service.click(x, y) { success ->
            val msg = if (selectorString != null) {
                if (success) "点击成功 selector=$selectorString ($x, $y)" else "点击失败"
            } else {
                if (success) "点击成功 ($x, $y)" else "点击失败"
            }
            context.wrappedCallback(CommandResult(success, msg))
        }
    }
}