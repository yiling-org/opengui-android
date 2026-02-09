package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 心跳处理器
 */
class HeartbeatHandler : ActionHandler {
    override val actionName = "heartbeat"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        callback(CommandResult(true, "ok"))
    }
}