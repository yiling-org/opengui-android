package top.yling.ozx.guiagent.shizuku

import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.view.Display
import top.yling.ozx.guiagent.util.checkExistClass

/**
 * 安全的 ActivityTaskManager 封装
 * 用于获取当前顶部 Activity 信息（Android 10+ 首选）
 *
 * 参考 GKD: li.songe.gkd.shizuku.ActivityTaskManager
 */
class SafeActivityTaskManager(private val value: IActivityTaskManager) {
    companion object {
        val isAvailable: Boolean
            get() = checkExistClass("android.app.IActivityTaskManager")

        fun newBinder() = getStubService(
            "activity_task",
            isAvailable,
        )?.let { binder ->
            asInterface<IActivityTaskManager>("android.app.IActivityTaskManager", binder)?.let {
                SafeActivityTaskManager(it)
            }
        }

        /**
         * 检测 getTasks 方法的签名类型
         * 不同 Android 版本签名不同
         */
        private val getTasksType by lazy {
            IActivityTaskManager::class.java.detectHiddenMethod(
                "getTasks",
                1 to listOf(Int::class.java),
                2 to listOf(Int::class.java, Boolean::class.java, Boolean::class.java),
                3 to listOf(
                    Int::class.java,
                    Boolean::class.java,
                    Boolean::class.java,
                    Int::class.java
                ),
            )
        }
    }

    /**
     * 获取最近的任务列表
     * @param maxNum 最大数量
     * @return 运行中的任务信息列表
     */
    fun getTasks(maxNum: Int): List<ActivityManager.RunningTaskInfo>? = safeInvokeMethod {
        when (getTasksType) {
            1 -> value.getTasks(maxNum)
            2 -> value.getTasks(maxNum, false, false)
            3 -> value.getTasks(maxNum, false, false, Display.INVALID_DISPLAY)
            else -> value.getTasks(maxNum)
        }
    }
}

