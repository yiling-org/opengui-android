package top.yling.ozx.guiagent.websocket.handler

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 空闲等待处理器
 */
class IdleHandler : ActionHandler {
    override val actionName = "idle"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val duration = context.params?.duration ?: 1000L
        context.coroutineScope.launch {
            delay(duration)
            context.wrappedCallback(CommandResult(true, "等待完成: ${duration}ms"))
        }
    }
}