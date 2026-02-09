package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 复制文本处理器
 */
class CopyTextHandler : ActionHandler {
    override val actionName = "copy_text"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val text = context.params?.text ?: run {
            callback(CommandResult(false, "缺少参数 text"))
            return
        }
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val success = service.copyText(text)
        context.wrappedCallback(CommandResult(success,
            if (success) "复制成功" else "复制失败"))
    }
}