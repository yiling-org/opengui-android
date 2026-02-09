package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 打开快速设置处理器
 */
class OpenQuickSettingsHandler : ActionHandler {
    override val actionName = "open_quick_settings"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val success = service.openQuickSettings()
        context.wrappedCallback(CommandResult(success,
            if (success) "快速设置打开成功" else "快速设置打开失败"))
    }
}
