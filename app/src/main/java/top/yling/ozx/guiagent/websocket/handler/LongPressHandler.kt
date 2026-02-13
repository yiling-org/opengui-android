package top.yling.ozx.guiagent.websocket.handler

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

        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        service.longPress(x, y, duration) { success ->
            context.wrappedCallback(CommandResult(success,
                if (success) "长按成功 ($x, $y)" else "长按失败"))
        }
    }
}