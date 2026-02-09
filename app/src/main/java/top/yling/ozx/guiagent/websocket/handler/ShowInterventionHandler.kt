package top.yling.ozx.guiagent.websocket.handler

import android.util.Log
import top.yling.ozx.guiagent.intervention.InterventionOverlayManager
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 显示用户介入提示处理器
 * 当服务端检测到需要用户手动处理的场景（登录、验证码等）时，
 * 显示悬浮提示通知用户，并提供"我已完成"按钮
 */
class ShowInterventionHandler : ActionHandler {

    companion object {
        private const val TAG = "ShowInterventionHandler"
    }

    override val actionName = "show_intervention"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val params = context.params

        // 获取参数
        val interventionId = params?.interventionId
        val sceneType = params?.sceneType ?: "custom"
        val description = params?.description ?: "请在手机上完成当前操作"
        val timeout = params?.timeout ?: 30

        if (interventionId == null) {
            Log.e(TAG, "缺少 interventionId 参数")
            callback(CommandResult(false, "缺少 interventionId 参数"))
            return
        }

        Log.i(TAG, "显示用户介入提示: id=$interventionId, scene=$sceneType, timeout=${timeout}s")

        // 检查悬浮窗权限
        val overlayManager = InterventionOverlayManager.getInstance(context.applicationContext)
        val hasOverlayPermission = overlayManager.canDrawOverlays()
        Log.i(TAG, "悬浮窗权限检查: hasPermission=$hasOverlayPermission")

        if (!hasOverlayPermission) {
            Log.e(TAG, "没有悬浮窗权限")
            callback(CommandResult(false, "没有悬浮窗权限，请在设置中授权"))
            return
        }

        Log.i(TAG, "准备显示悬浮窗...")

        // 显示悬浮提示（确认消息发送已在 InterventionOverlayManager 中处理）
        overlayManager.showIntervention(
            interventionId = interventionId,
            sceneType = sceneType,
            description = description,
            timeoutSeconds = timeout
        ) { confirmedId ->
            // 本地回调，用于日志记录
            Log.i(TAG, "用户确认完成: id=$confirmedId")
        }

        callback(CommandResult(true, "用户介入提示已显示"))
    }
}
