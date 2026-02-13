package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 滚动处理器
 *
 * @author shanwb
 */
class ScrollHandler : ActionHandler {
    override val actionName = "scroll"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val (x, y) = context.extractXY() ?: run {
            callback(CommandResult(false, "缺少参数 x 或 y"))
            return
        }
        val direction = context.params?.direction ?: run {
            callback(CommandResult(false, "缺少参数 direction"))
            return
        }
        val distance = context.params.distance ?: 500f

        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        service.scroll(x, y, direction, distance) { success ->
            context.wrappedCallback(CommandResult(success,
                if (success) "滑动成功 $direction" else "滑动失败"))
        }
    }
}
