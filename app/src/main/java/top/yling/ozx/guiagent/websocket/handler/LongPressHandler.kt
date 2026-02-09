package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.AgentOverlayService
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 长按处理器
 *
 * @author shanwb
 */
class LongPressHandler : ActionHandler {
    override val actionName = "long_press"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val (x, y) = context.extractXY() ?: run {
            callback(CommandResult(false, "缺少参数 x 或 y"))
            return
        }
        val duration = context.params?.duration ?: 1000L

        // 显示长按反馈效果（开始）
        AgentOverlayService.instance?.showLongPressFeedback(x, y)

        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        service.longPress(x, y, duration) { success ->
            // 隐藏长按反馈效果（结束）
            AgentOverlayService.instance?.hideLongPressFeedback()

            context.wrappedCallback(CommandResult(success,
                if (success) "长按成功 ($x, $y)" else "长按失败"))
        }
    }
}