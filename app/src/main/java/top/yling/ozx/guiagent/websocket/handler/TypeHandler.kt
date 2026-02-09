package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.AgentOverlayService
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 输入文本处理器
 *
 * @author shanwb
 */
class TypeHandler : ActionHandler {
    override val actionName = "type"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val content = context.params?.content ?: run {
            callback(CommandResult(false, "缺少参数 content"))
            return
        }

        // 显示输入反馈效果
        AgentOverlayService.instance?.showTypeFeedback()

        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val success = service.type(content)
        context.wrappedCallback(CommandResult(success,
            if (success) "输入成功" else "输入失败，请确保有输入框获得焦点"))
    }
}
