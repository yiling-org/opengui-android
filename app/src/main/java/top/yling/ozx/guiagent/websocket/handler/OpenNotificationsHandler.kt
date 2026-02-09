package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 打开通知栏处理器
 */
class OpenNotificationsHandler : ActionHandler {
    override val actionName = "open_notifications"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val success = service.openNotifications()
        context.wrappedCallback(CommandResult(success,
            if (success) "通知栏打开成功" else "通知栏打开失败"))
    }
}
