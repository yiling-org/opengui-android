package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.services.AddEventParams
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 添加事件处理器
 */
class AddEventHandler : ActionHandler {
    override val actionName = "add_event"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val params = context.params
        val title = params?.title ?: run {
            callback(CommandResult(false, "缺少参数 title"))
            return
        }
        val startTime = params.startTime ?: run {
            callback(CommandResult(false, "缺少参数 start_time"))
            return
        }
        val endTime = params.endTime ?: run {
            callback(CommandResult(false, "缺少参数 end_time"))
            return
        }
        val eventParams = AddEventParams(
            title = title,
            startTime = startTime,
            endTime = endTime,
            description = params.description,
            location = params.location,
            allDay = params.allDay ?: false,
            reminderMinutes = params.reminderMinutes
        )
        val eventId = context.calendarService.addEvent(eventParams)
        if (eventId > 0) {
            context.wrappedCallback(CommandResult(true, "添加事件成功", mapOf(
                "eventId" to eventId
            )))
        } else {
            context.wrappedCallback(CommandResult(false, "添加事件失败"))
        }
    }
}
