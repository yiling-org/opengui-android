package top.yling.ozx.guiagent.shizuku

import android.app.ActivityManager
import android.app.IActivityManager
import top.yling.ozx.guiagent.util.AndroidTarget
import top.yling.ozx.guiagent.util.checkExistClass

/**
 * 安全的 ActivityManager 封装
 * 用于获取当前顶部 Activity 信息
 *
 * 参考 GKD: li.songe.gkd.shizuku.ActivityManager
 */
class SafeActivityManager(private val value: IActivityManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.app.IActivityManager")

        fun newBinder() = getStubService(
            "activity",
            isAvailable,
        )?.let { binder ->
            asInterface<IActivityManager>("android.app.IActivityManager", binder)?.let {
                SafeActivityManager(it)
            }
        }
    }

    /**
     * 获取最近的任务列表
     * @param maxNum 最大数量
     * @return 运行中的任务信息列表
     */
    @Suppress("DEPRECATION")
    fun getTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo> = safeInvokeMethod {
        if (AndroidTarget.P) {
            value.getTasks(maxNum)
        } else {
            value.getTasks(maxNum, 0)
        }
    } ?: emptyList()
}

