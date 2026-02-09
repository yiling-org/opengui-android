package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 拨打电话处理器
 */
class MakeCallHandler : ActionHandler {
    override val actionName = "make_call"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val phoneNumber = context.params?.phoneNumber ?: run {
            callback(CommandResult(false, "缺少参数 phone_number"))
            return
        }
        // directCall 参数决定是直接拨打还是跳转到拨号界面，默认为 true（直接拨打）
        val directCall = context.params.directCall ?: true
        val success = context.phoneCallService.makeCall(phoneNumber, directCall)
        context.wrappedCallback(CommandResult(success,
            if (success) "拨打电话成功: $phoneNumber" else "拨打电话失败"))
    }
}
