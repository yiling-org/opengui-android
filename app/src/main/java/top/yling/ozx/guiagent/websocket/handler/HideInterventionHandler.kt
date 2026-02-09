package top.yling.ozx.guiagent.websocket.handler

import android.util.Log
import top.yling.ozx.guiagent.intervention.InterventionOverlayManager
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 隐藏用户介入提示处理器
 * 当用户介入超时或服务端处理完成时，隐藏悬浮提示
 */
class HideInterventionHandler : ActionHandler {

    companion object {
        private const val TAG = "HideInterventionHandler"
    }

    override val actionName = "hide_intervention"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val params = context.params
        val interventionId = params?.interventionId

        Log.i(TAG, "隐藏用户介入提示: id=$interventionId")

        // 隐藏悬浮提示
        val overlayManager = InterventionOverlayManager.getInstance(context.applicationContext)
        overlayManager.hideIntervention(interventionId)

        callback(CommandResult(true, "用户介入提示已隐藏"))
    }
}
