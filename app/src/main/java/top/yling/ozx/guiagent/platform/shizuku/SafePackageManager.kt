package top.yling.ozx.guiagent.shizuku

import android.Manifest
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.util.Log
import top.yling.ozx.guiagent.util.AndroidTarget
import top.yling.ozx.guiagent.util.checkExistClass

/**
 * 安全封装的 PackageManager
 * 用于授予运行时权限
 * 
 * 参考 GKD: li.songe.gkd.shizuku.SafePackageManager
 */
@Suppress("unused")
class SafePackageManager(private val value: IPackageManager) {
    companion object {
        private const val TAG = "SafePackageManager"
        
        val isAvailable: Boolean
            get() = checkExistClass("android.content.pm.IPackageManager")

        fun newBinder() = getStubService(
            "package",
            isAvailable
        )?.let { binder ->
            asInterface<IPackageManager>("android.content.pm.IPackageManager", binder)?.let {
                SafePackageManager(it)
            }
        }

        private var canUseGetInstalledApps = true
    }

    /**
     * 检查是否处于安全模式
     */
    val isSafeMode get() = safeInvokeMethod { value.isSafeMode }

    /**
     * 获取已安装的包列表
     */
    fun getInstalledPackages(
        flags: Int,
        userId: Int = ShizukuApi.currentUserId,
    ): List<PackageInfo> = safeInvokeMethod {
        if (AndroidTarget.TIRAMISU) {
            value.getInstalledPackages(flags.toLong(), userId).list
        } else {
            value.getInstalledPackages(flags, userId).list
        }
    } ?: emptyList()

    /**
     * 获取包信息
     */
    fun getPackageInfo(
        packageName: String,
        flags: Int,
        userId: Int,
    ): PackageInfo? = safeInvokeMethod {
        if (AndroidTarget.TIRAMISU) {
            value.getPackageInfo(packageName, flags.toLong(), userId)
        } else {
            value.getPackageInfo(packageName, flags, userId)
        }
    }

    /**
     * 获取应用启用状态
     */
    fun getApplicationEnabledSetting(
        packageName: String,
        userId: Int,
    ): Int = safeInvokeMethod {
        value.getApplicationEnabledSetting(packageName, userId)
    } ?: 0

    /**
     * 授予运行时权限
     */
    fun grantRuntimePermission(
        packageName: String,
        permissionName: String,
        userId: Int = ShizukuApi.currentUserId,
    ) = safeInvokeMethod {
        value.grantRuntimePermission(
            packageName,
            permissionName,
            userId
        )
    }

    /**
     * 授予自身应用权限
     */
    private fun grantSelfPermission(permissionName: String) = grantRuntimePermission(
        packageName = APP_PACKAGE_NAME,
        permissionName = permissionName,
    )

    /**
     * 自动授予所有必要的运行时权限
     */
    fun allowAllSelfPermission() {
        Log.d(TAG, "开始授予运行时权限...")
        
        // 读取已安装应用列表权限（部分设备可能没有此权限）
        if (canUseGetInstalledApps) {
            try {
                grantSelfPermission("com.android.permission.GET_INSTALLED_APPS")
                Log.d(TAG, "已授予 GET_INSTALLED_APPS")
            } catch (_: Throwable) {
                canUseGetInstalledApps = false
                Log.w(TAG, "设备不支持 GET_INSTALLED_APPS 权限")
            }
        }
        
        // AppOps 统计权限
        grantSelfPermission(Manifest_permission_GET_APP_OPS_STATS)
        Log.d(TAG, "已授予 GET_APP_OPS_STATS")
        
        // 写入安全设置权限（用于自动开启无障碍服务）
        grantSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
        Log.d(TAG, "已授予 WRITE_SECURE_SETTINGS")
        
        // Android 13+ 通知权限
        if (AndroidTarget.TIRAMISU) {
            grantSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            Log.d(TAG, "已授予 POST_NOTIFICATIONS")
        }
        
        Log.d(TAG, "运行时权限授予完成")
    }
}

/**
 * AppOps 统计权限常量
 */
const val Manifest_permission_GET_APP_OPS_STATS = "android.permission.GET_APP_OPS_STATS"

