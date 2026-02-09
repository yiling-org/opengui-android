package top.yling.ozx.guiagent.websocket.handler

import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 获取今日事件处理器
 */
class GetTodayEventsHandler : ActionHandler {
    override val actionName = "get_today_events"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val events = context.calendarService.getTodayEvents()
        val eventsList = events.map { event ->
            mapOf(
                "id" to event.id,
                "title" to event.title,
                "description" to event.description,
                "location" to event.location,
                "startTime" to event.startTime,
                "endTime" to event.endTime,
                "allDay" to event.allDay,
                "formattedStartTime" to event.getFormattedStartTime(),
                "formattedEndTime" to event.getFormattedEndTime(),
                "timeRange" to event.getTimeRangeDescription()
            )
        }
        context.wrappedCallback(CommandResult(true, "获取今日事件成功", mapOf(
            "events" to eventsList,
            "count" to events.size
        )))
    }
}
