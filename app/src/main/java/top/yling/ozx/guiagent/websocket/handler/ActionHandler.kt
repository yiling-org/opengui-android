package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * Action 处理器接口
 * 每个 action 对应一个处理器实现
 */
interface ActionHandler {
    /**
     * 支持的 action 名称
     */
    val actionName: String

    /**
     * 执行 action
     * @param context 执行上下文
     * @param callback 执行结果回调
     */
    fun handle(context: ActionContext, callback: (CommandResult) -> Unit)
}