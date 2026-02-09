package top.yling.ozx.guiagent.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * 已安装应用收集器
 * 负责获取设备上已安装的第三方应用列表
 */
class InstalledAppCollector(private val context: Context) {

    companion object {
        private const val TAG = "InstalledAppCollector"
    }

    /**
     * 获取所有已安装的第三方应用
     * @return 应用信息列表
     */
    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val apps = mutableListOf<InstalledAppInfo>()

        try {
            val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }

            for (packageInfo in packages) {
                // 过滤系统应用，只保留第三方应用
                if (isSystemApp(packageInfo)) {
                    continue
                }

                val appInfo = convertToAppInfo(pm, packageInfo)
                if (appInfo != null) {
                    apps.add(appInfo)
                }
            }

            Log.i(TAG, "Collected ${apps.size} third-party apps")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed apps", e)
        }

        return apps.sortedBy { it.appName }
    }

    /**
     * 获取单个应用的信息
     * @param packageName 包名
     * @return 应用信息，如果应用不存在或是系统应用则返回 null
     */
    fun getAppInfo(packageName: String): InstalledAppInfo? {
        val pm = context.packageManager

        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }

            // 检查是否是系统应用
            if (isSystemApp(packageInfo)) {
                Log.d(TAG, "Ignoring system app: $packageName")
                return null
            }

            convertToAppInfo(pm, packageInfo)

        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Package not found: $packageName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app info for $packageName", e)
            null
        }
    }

    /**
     * 检查包名是否是第三方应用（非系统应用）
     */
    fun isThirdPartyApp(packageName: String): Boolean {
        val pm = context.packageManager

        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }

            !isSystemApp(packageInfo)

        } catch (e: Exception) {
            false
        }
    }

    /**
     * 判断是否是系统应用
     */
    private fun isSystemApp(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return true
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    /**
     * 将 PackageInfo 转换为 InstalledAppInfo
     */
    private fun convertToAppInfo(pm: PackageManager, packageInfo: PackageInfo): InstalledAppInfo? {
        return try {
            val appInfo = packageInfo.applicationInfo ?: return null
            val appName = pm.getApplicationLabel(appInfo).toString()

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            InstalledAppInfo(
                packageName = packageInfo.packageName,
                appName = appName,
                versionName = packageInfo.versionName,
                versionCode = versionCode,
                installedTime = packageInfo.firstInstallTime,
                lastUpdatedTime = packageInfo.lastUpdateTime
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to convert package info: ${packageInfo.packageName}", e)
            null
        }
    }
}
