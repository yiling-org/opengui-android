package top.yling.ozx.guiagent.websocket.handler

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.services.BrowserService
import top.yling.ozx.guiagent.services.CalendarService
import top.yling.ozx.guiagent.services.ContactsService
import top.yling.ozx.guiagent.services.NotificationService
import top.yling.ozx.guiagent.services.PhoneCallService
import top.yling.ozx.guiagent.services.SmsService
import top.yling.ozx.guiagent.websocket.AccessibilityCommand
import top.yling.ozx.guiagent.websocket.AppLauncher
import top.yling.ozx.guiagent.websocket.CommandParams
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * Action 执行上下文
 * 包含执行 action 所需的所有依赖和参数
 * 
 * @author shanwb
 */
data class ActionContext(
    val applicationContext: Context,
    val service: MyAccessibilityService?,  // 可空，某些命令（如 schedule_task）不需要无障碍服务
    val command: AccessibilityCommand,
    val params: CommandParams?,
    val appLauncher: AppLauncher,
    val browserService: BrowserService,
    val smsService: SmsService,
    val contactsService: ContactsService,
    val notificationService: NotificationService,
    val calendarService: CalendarService,
    val phoneCallService: PhoneCallService,
    val coroutineScope: CoroutineScope,
    val takeScreenshotAfter: Boolean = false,
    val wrappedCallback: (CommandResult) -> Unit
) {
    /**
     * 提取 x, y 坐标参数
     */
    fun extractXY(): Pair<Float, Float>? {
        val x = params?.x ?: return null
        val y = params.y ?: return null
        return x to y
    }
}