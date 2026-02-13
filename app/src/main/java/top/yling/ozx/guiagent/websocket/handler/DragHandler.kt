package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 拖拽处理器
 *
 * @author shanwb
 */
class DragHandler : ActionHandler {
    override val actionName = "drag"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val (x1, y1) = context.extractXY() ?: run {
            callback(CommandResult(false, "缺少参数 x 或 y"))
            return
        }
        val x2 = context.params?.x2 ?: run {
            callback(CommandResult(false, "缺少参数 x2"))
            return
        }
        val y2 = context.params.y2 ?: run {
            callback(CommandResult(false, "缺少参数 y2"))
            return
        }
        val duration = context.params.duration ?: 500L

        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        service.drag(x1, y1, x2, y2, duration) { success ->
            context.wrappedCallback(CommandResult(success,
                if (success) "拖拽成功" else "拖拽失败"))
        }
    }
}
