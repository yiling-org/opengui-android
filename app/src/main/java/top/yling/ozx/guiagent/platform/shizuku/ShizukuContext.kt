package top.yling.ozx.guiagent.shizuku

import android.content.ComponentName
import android.util.Log
import androidx.annotation.WorkerThread
import kotlinx.serialization.InternalSerializationApi

/**
 * Shizuku 上下文
 * 封装 Shizuku 服务的各种功能
 * 
 * 参考 GKD: li.songe.gkd.shizuku.ShizukuApi.ShizukuContext
 */
@OptIn(InternalSerializationApi::class)
class ShizukuContext(
    val serviceWrapper: UserServiceWrapper?,
    val inputManager: SafeInputManager?,
    val activityManager: SafeActivityManager? = null,
    val activityTaskManager: SafeActivityTaskManager? = null,
    val appOpsService: SafeAppOpsService? = null,
    val packageManager: SafePackageManager? = null,
) {
    companion object {
        private const val TAG = "ShizukuContext"
    }

    /**
     * 检查上下文是否有效
     */
    val isValid: Boolean
        get() = serviceWrapper != null || inputManager != null

    /**
     * 销毁上下文
     */
    fun destroy() {
        serviceWrapper?.destroy()
        Log.d(TAG, "ShizukuContext 已销毁")
    }

    /**
     * 执行点击操作
     * @param x X 坐标
     * @param y Y 坐标
     * @param duration 持续时间（毫秒），用于长按
     * @param displayId 目标显示屏 ID，-1 表示默认显示屏
     * @return 是否成功
     */
    @WorkerThread
    fun tap(x: Float, y: Float, duration: Long = 0, displayId: Int = -1): Boolean {
        return serviceWrapper?.tap(x, y, duration, displayId)
            ?: (inputManager?.tap(x, y, duration, displayId) != null)
    }

    /**
     * 执行滑动操作
     * @param x1 起始 X 坐标
     * @param y1 起始 Y 坐标
     * @param x2 结束 X 坐标
     * @param y2 结束 Y 坐标
     * @param duration 滑动时间（毫秒）
     * @return 是否成功
     */
    @OptIn(InternalSerializationApi::class)
    @WorkerThread
    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long = 300): Boolean {
        val command = "input swipe $x1 $y1 $x2 $y2 $duration"
        return serviceWrapper?.execCommandForResult(command)?.ok ?: false
    }

    /**
     * 执行按键操作
     * @param keyCode 按键码
     * @return 是否成功
     */
    @WorkerThread
    fun key(keyCode: Int): Boolean {
        inputManager?.key(keyCode)
        return true
    }

    /**
     * 执行 shell 命令
     * @param command 命令字符串
     * @return 命令结果
     */
    @WorkerThread
    fun execCommand(command: String): CommandResult {
        return serviceWrapper?.execCommandForResult(command)
            ?: CommandResult(code = null, result = "", error = "服务未连接")
    }

    /**
     * 获取当前顶部 Activity 的 ComponentName
     * 优先使用 activityTaskManager（Android 10+），否则使用 activityManager
     * @return 当前顶部 Activity 的 ComponentName，如果获取失败返回 null
     */
    fun topCpn(): ComponentName? {
        return (activityTaskManager?.getTasks(1)
            ?: activityManager?.getTasks(1))?.firstOrNull()?.topActivity
    }

    /**
     * 状态信息
     */
    val states: List<Pair<String, Any?>>
        get() = listOf(
            "IUserService" to serviceWrapper,
            "IInputManager" to inputManager,
            "IActivityManager" to activityManager,
            "IActivityTaskManager" to activityTaskManager,
            "IAppOpsService" to appOpsService,
            "IPackageManager" to packageManager,
        )

    /**
     * 自动授予所有必要权限
     * 包括 AppOps 权限和运行时权限
     */
    fun grantSelf() {
        Log.d(TAG, "开始自动授予权限...")
        appOpsService?.allowAllSelfMode()
        packageManager?.allowAllSelfPermission()
        Log.d(TAG, "自动授予权限完成")
    }

    /**
     * 自动启用无障碍服务
     * @return 是否成功启用
     */
    fun enableAccessibilityService(): Boolean {
        val wrapper = serviceWrapper
        if (wrapper == null) {
            Log.w(TAG, "无法启用无障碍服务: serviceWrapper 为空")
            return false
        }

        val componentName = "$APP_PACKAGE_NAME/.MyAccessibilityService"
        Log.d(TAG, "尝试启用无障碍服务: $componentName")

        try {
            // 1. 读取当前已启用的无障碍服务列表
            val getResult = wrapper.execCommandForResult("settings get secure enabled_accessibility_services")
            val currentServices = if (getResult.ok) {
                getResult.result.trim().let { if (it == "null" || it.isEmpty()) "" else it }
            } else {
                Log.w(TAG, "获取无障碍服务列表失败: ${getResult.error}")
                ""
            }
            Log.d(TAG, "当前无障碍服务列表: $currentServices")

            // 2. 检查是否已启用
            if (currentServices.contains(componentName) || currentServices.contains("$APP_PACKAGE_NAME/top.yling.ozx.guiagent.MyAccessibilityService")) {
                Log.d(TAG, "无障碍服务已启用")
                return true
            }

            // 3. 添加我们的服务到列表
            val newServices = if (currentServices.isEmpty()) {
                componentName
            } else {
                "$currentServices:$componentName"
            }

            // 4. 写入新的服务列表
            val putResult = wrapper.execCommandForResult("settings put secure enabled_accessibility_services '$newServices'")
            if (!putResult.ok) {
                Log.e(TAG, "设置无障碍服务列表失败: ${putResult.error}")
                return false
            }

            // 5. 确保无障碍主开关打开
            val enableResult = wrapper.execCommandForResult("settings put secure accessibility_enabled 1")
            if (!enableResult.ok) {
                Log.w(TAG, "启用无障碍主开关失败: ${enableResult.error}")
            }

            Log.d(TAG, "无障碍服务启用成功")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "启用无障碍服务异常", e)
            return false
        }
    }

    /**
     * 禁用无障碍服务
     * @return 是否成功禁用
     */
    fun disableAccessibilityService(): Boolean {
        val wrapper = serviceWrapper
        if (wrapper == null) {
            Log.w(TAG, "无法禁用无障碍服务: serviceWrapper 为空")
            return false
        }

        val componentName = "$APP_PACKAGE_NAME/.MyAccessibilityService"
        val fullComponentName = "$APP_PACKAGE_NAME/top.yling.ozx.guiagent.MyAccessibilityService"

        try {
            // 读取当前列表
            val getResult = wrapper.execCommandForResult("settings get secure enabled_accessibility_services")
            val currentServices = if (getResult.ok) {
                getResult.result.trim().let { if (it == "null" || it.isEmpty()) "" else it }
            } else ""

            // 移除我们的服务
            val newServices = currentServices
                .split(":")
                .filter { it != componentName && it != fullComponentName }
                .joinToString(":")

            // 写回
            val putResult = wrapper.execCommandForResult("settings put secure enabled_accessibility_services '$newServices'")
            return putResult.ok
        } catch (e: Exception) {
            Log.e(TAG, "禁用无障碍服务异常", e)
            return false
        }
    }
}

