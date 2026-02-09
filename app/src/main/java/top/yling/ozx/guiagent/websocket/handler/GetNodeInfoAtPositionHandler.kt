package top.yling.ozx.guiagent.websocket.handler

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.yling.ozx.guiagent.a11y.NodeExplorer
import top.yling.ozx.guiagent.websocket.CommandResult

/**
 * 获取节点信息处理器
 */
class GetNodeInfoAtPositionHandler : ActionHandler {
    companion object {
        private const val TAG = "GetNodeInfoAtPositionHandler"
    }

    override val actionName = "get_node_info_at_position"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        // 如果 fullSnapshot 为 true，返回完整窗口快照，忽略 x, y 参数
        val fullSnapshot = context.params?.fullSnapshot ?: false
        if (fullSnapshot) {
            val snapshot = NodeExplorer.getFullSnapshot(context.applicationContext)
            if (snapshot != null) {
                // 转换为 Map 返回
                val snapshotMap = mapOf(
                    "id" to snapshot.id,
                    "appId" to snapshot.appId,
                    "activityId" to snapshot.activityId,
                    "screenHeight" to snapshot.screenHeight,
                    "screenWidth" to snapshot.screenWidth,
                    "isLandscape" to snapshot.isLandscape,
                    "appInfo" to snapshot.appInfo?.let { mapOf(
                        "id" to it.id,
                        "name" to it.name,
                        "versionCode" to it.versionCode,
                        "versionName" to it.versionName,
                        "isSystem" to it.isSystem
                    )},
                    "device" to mapOf(
                        "device" to snapshot.device.device,
                        "model" to snapshot.device.model,
                        "manufacturer" to snapshot.device.manufacturer,
                        "brand" to snapshot.device.brand,
                        "sdkInt" to snapshot.device.sdkInt,
                        "release" to snapshot.device.release
                    ),
                    "nodes" to snapshot.nodes.map { node -> mapOf(
                        "id" to node.id,
                        "pid" to node.pid,
                        "idQf" to node.idQf,
                        "textQf" to node.textQf,
                        "attr" to mapOf(
                            "id" to node.attr.id,
                            "vid" to node.attr.vid,
                            "name" to node.attr.name,
                            "text" to node.attr.text,
                            "desc" to node.attr.desc,
                            "clickable" to node.attr.clickable,
                            "focusable" to node.attr.focusable,
                            "checkable" to node.attr.checkable,
                            "checked" to node.attr.checked,
                            "editable" to node.attr.editable,
                            "longClickable" to node.attr.longClickable,
                            "visibleToUser" to node.attr.visibleToUser,
                            "left" to node.attr.left,
                            "top" to node.attr.top,
                            "right" to node.attr.right,
                            "bottom" to node.attr.bottom,
                            "width" to node.attr.width,
                            "height" to node.attr.height,
                            "childCount" to node.attr.childCount,
                            "index" to node.attr.index,
                            "depth" to node.attr.depth
                        )
                    )}
                )
                context.wrappedCallback(CommandResult(true, "获取完整快照成功", snapshotMap))
            } else {
                context.wrappedCallback(CommandResult(false, "无法获取当前窗口"))
            }
            return
        }

        val (x, y) = context.extractXY() ?: run {
            callback(CommandResult(false, "缺少参数 x 或 y"))
            return
        }
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        val resultJson = service.getNodeInfoAtPosition(x, y)
        try {
            val resultMap: Map<String, Any?> = Gson().fromJson(
                resultJson,
                object : TypeToken<Map<String, Any?>>() {}.type
            )
            val success = resultMap["success"] as? Boolean ?: false
            if (success) {
                context.wrappedCallback(CommandResult(true, "获取节点信息成功", resultMap))
            } else {
                val error = resultMap["error"] as? String ?: "未知错误"
                context.wrappedCallback(CommandResult(false, error, resultMap))
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析节点信息失败: ${e.message}", e)
            callback(CommandResult(false, "解析节点信息失败: ${e.message}"))
        }
    }
}
