package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 最近任务键处理器
 */
class PressRecentsHandler : ActionHandler {
    override val actionName = "press_recents"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val success = service.pressRecents()
        context.wrappedCallback(CommandResult(success,
            if (success) "最近任务打开成功" else "最近任务打开失败"))
    }
}
