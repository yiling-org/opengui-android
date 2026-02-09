package top.yling.ozx.guiagent.shizuku

import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import com.android.internal.app.IAppOpsService
import top.yling.ozx.guiagent.util.AndroidTarget
import top.yling.ozx.guiagent.util.checkExistClass

/**
 * 安全封装的 AppOpsService
 * 用于自动授予 AppOps 权限
 * 
 * 参考 GKD: li.songe.gkd.shizuku.SafeAppOpsService
 */
class SafeAppOpsService(
    private val value: IAppOpsService
) {
    companion object {
        private const val TAG = "SafeAppOpsService"
        
        val isAvailable: Boolean
            get() = checkExistClass("com.android.internal.app.IAppOpsService")

        fun newBinder() = getStubService(
            Context.APP_OPS_SERVICE,
            isAvailable,
        )?.let { binder ->
            asInterface<IAppOpsService>("com.android.internal.app.IAppOpsService", binder)?.let {
                SafeAppOpsService(it)
            }
        }

        private val a11yOverlayOk by lazy {
            AndroidTarget.UPSIDE_DOWN_CAKE && try {
                AppOpsManager::class.java.getField("OP_CREATE_ACCESSIBILITY_OVERLAY")
            } catch (_: NoSuchFieldException) {
                null
            } != null
        }

        @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        val supportCreateA11yOverlay get() = a11yOverlayOk
    }

    /**
     * 设置 AppOps 模式
     */
    fun setMode(
        code: Int,
        uid: Int = ShizukuApi.currentUserId,
        packageName: String,
        mode: Int
    ) = safeInvokeMethod {
        value.setMode(code, uid, packageName, mode)
    }

    /**
     * 设置自身应用的 AppOps 为允许模式
     */
    private fun setAllowSelfMode(code: Int) = setMode(
        code = code,
        packageName = APP_PACKAGE_NAME,
        mode = AppOpsManager.MODE_ALLOWED,
    )

    /**
     * 自动授予所有必要的 AppOps 权限
     */
    fun allowAllSelfMode() {
        Log.d(TAG, "开始授予 AppOps 权限...")

        // 通知权限
        trySetAllowSelfMode("OP_POST_NOTIFICATION") {
            setAllowSelfMode(AppOpsManagerHidden.OP_POST_NOTIFICATION)
        }

        // 悬浮窗权限
        trySetAllowSelfMode("OP_SYSTEM_ALERT_WINDOW") {
            setAllowSelfMode(AppOpsManagerHidden.OP_SYSTEM_ALERT_WINDOW)
        }

        // Android 10+ 无障碍权限
        if (AndroidTarget.Q) {
            trySetAllowSelfMode("OP_ACCESS_ACCESSIBILITY") {
                setAllowSelfMode(AppOpsManagerHidden.OP_ACCESS_ACCESSIBILITY)
            }
        }

        // Android 13+ 受限设置权限
        if (AndroidTarget.TIRAMISU) {
            trySetAllowSelfMode("OP_ACCESS_RESTRICTED_SETTINGS") {
                setAllowSelfMode(AppOpsManagerHidden.OP_ACCESS_RESTRICTED_SETTINGS)
            }
        }

        // Android 14+ 前台服务特殊用途权限
        if (AndroidTarget.UPSIDE_DOWN_CAKE) {
            trySetAllowSelfMode("OP_FOREGROUND_SERVICE_SPECIAL_USE") {
                setAllowSelfMode(AppOpsManagerHidden.OP_FOREGROUND_SERVICE_SPECIAL_USE)
            }
        }

        // Android 14+ 无障碍悬浮窗权限
        if (supportCreateA11yOverlay) {
            trySetAllowSelfMode("OP_CREATE_ACCESSIBILITY_OVERLAY") {
                setAllowSelfMode(AppOpsManagerHidden.OP_CREATE_ACCESSIBILITY_OVERLAY)
            }
        }

        // 电话权限
        trySetAllowSelfMode("OP_CALL_PHONE") {
            setAllowSelfMode(AppOpsManagerHidden.OP_CALL_PHONE)
        }

        trySetAllowSelfMode("OP_READ_PHONE_STATE") {
            setAllowSelfMode(AppOpsManagerHidden.OP_READ_PHONE_STATE)
        }

        Log.d(TAG, "AppOps 权限授予完成")
    }

    /**
     * 安全执行 AppOps 权限设置，捕获字段不存在的情况
     */
    private inline fun trySetAllowSelfMode(opName: String, block: () -> Unit) {
        try {
            block()
            Log.d(TAG, "已授予 $opName")
        } catch (e: NoSuchFieldError) {
            Log.w(TAG, "$opName 在当前系统不可用")
        } catch (e: Exception) {
            Log.w(TAG, "授予 $opName 失败: ${e.message}")
        }
    }
}

/**
 * 应用包名常量
 */
const val APP_PACKAGE_NAME = "top.yling.ozx.guiagent"

