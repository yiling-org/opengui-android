package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 返回键处理器
 */
class PressBackHandler : ActionHandler {
    override val actionName = "press_back"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val success = service.pressBack()
        context.wrappedCallback(CommandResult(success,
            if (success) "返回键按下成功" else "返回键按下失败"))
    }
}
