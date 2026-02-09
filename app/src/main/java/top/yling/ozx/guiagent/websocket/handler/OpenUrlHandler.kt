package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 打开 URL 处理器
 */
class OpenUrlHandler : ActionHandler {
    override val actionName = "open_url"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val url = context.params?.url ?: run {
            callback(CommandResult(false, "缺少参数 url"))
            return
        }
        val success = context.browserService.openUrl(url)
        context.wrappedCallback(CommandResult(success,
            if (success) "打开URL成功: $url" else "打开URL失败"))
    }
}
