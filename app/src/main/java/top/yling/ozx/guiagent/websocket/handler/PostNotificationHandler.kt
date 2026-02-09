package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 发送通知处理器
 */
class PostNotificationHandler : ActionHandler {
    override val actionName = "post_notification"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val title = context.params?.title ?: run {
            callback(CommandResult(false, "缺少参数 title"))
            return
        }
        val content = context.params.content ?: run {
            callback(CommandResult(false, "缺少参数 content"))
            return
        }
        val result = context.notificationService.postSimpleNotification(title, content)
        context.wrappedCallback(CommandResult(result.success, result.message, mapOf(
            "notificationId" to result.notificationId
        )))
    }
}
