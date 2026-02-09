package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 状态查询处理器
 */
class StatusHandler : ActionHandler {
    override val actionName = "status"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        context.wrappedCallback(CommandResult(true,
            "无障碍服务: ${if (MyAccessibilityService.isServiceEnabled()) "已启用" else "未启用"}"))
    }
}