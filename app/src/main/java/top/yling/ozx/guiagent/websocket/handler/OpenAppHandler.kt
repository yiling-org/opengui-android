package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 打开应用处理器
 */
class OpenAppHandler : ActionHandler {
    override val actionName = "open_app"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val packageName = context.params?.packageName
        val appName = context.params?.appName

        val success = when {
            packageName != null -> context.appLauncher.openByPackage(packageName)
            appName != null -> context.appLauncher.openByName(appName)
            else -> {
                callback(CommandResult(false, "缺少参数 packageName 或 appName"))
                return
            }
        }
        context.wrappedCallback(CommandResult(success,
            if (success) "应用打开成功" else "应用打开失败"))
    }
}
