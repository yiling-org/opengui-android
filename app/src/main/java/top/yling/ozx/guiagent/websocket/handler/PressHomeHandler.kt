package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * Home 键处理器
 */
class PressHomeHandler : ActionHandler {
    override val actionName = "press_home"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val success = service.pressHome()
        context.wrappedCallback(CommandResult(success,
            if (success) "Home键按下成功" else "Home键按下失败"))
    }
}
