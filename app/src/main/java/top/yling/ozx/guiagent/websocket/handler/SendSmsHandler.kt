package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 发送短信处理器
 */
class SendSmsHandler : ActionHandler {
    override val actionName = "send_sms"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val phoneNumber = context.params?.phoneNumber ?: run {
            callback(CommandResult(false, "缺少参数 phone_number"))
            return
        }
        val message = context.params.message ?: run {
            callback(CommandResult(false, "缺少参数 message"))
            return
        }
        val success = context.smsService.sendSms(phoneNumber, message)
        context.wrappedCallback(CommandResult(success,
            if (success) "短信发送成功: $phoneNumber" else "短信发送失败"))
    }
}
