package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 获取窗口 ID 处理器
 */
class GetWindowIdHandler : ActionHandler {
    override val actionName = "get_window_id"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val windowInfo = service.getCurrentWindowId()
        if (windowInfo != null) {
            callback(CommandResult(true, "获取窗口标识成功", windowInfo))
        } else {
            callback(CommandResult(false, "无法获取当前窗口标识"))
        }
    }
}