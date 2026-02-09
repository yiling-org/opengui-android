package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * Ping 处理器
 */
class PingHandler : ActionHandler {
    override val actionName = "ping"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        context.wrappedCallback(CommandResult(true, "服务正常"))
    }
}