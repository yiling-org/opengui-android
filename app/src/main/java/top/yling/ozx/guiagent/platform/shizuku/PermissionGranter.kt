@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package top.yling.ozx.guiagent.shizuku

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi

/**
 * Shizuku 权限自动授予工具类
 *
 * 在应用启动时自动检查并授予所有必要的权限，包括：
 * - 运行时权限（相机、麦克风、存储等）
 * - 特殊权限（悬浮窗、修改系统设置、使用统计等）
 * - 无障碍服务
 * - 电池优化豁免
 * - 后台弹出界面权限
 */
@OptIn(InternalSerializationApi::class)
object PermissionGranter {

    private const val TAG = "PermissionGranter"

    // 授权状态标记，避免重复授权
    @Volatile
    private var hasGranted = false

    /**
     * 在应用启动时自动授予所有权限
     * 这是主入口方法，应在 Application.onCreate() 中调用
     */
    suspend fun autoGrantOnStartup() {
        if (hasGranted) {
            Log.d(TAG, "权限已授予，跳过")
            return
        }

        // 检查 Shizuku 是否可用
        if (!ShizukuApi.isAvailable()) {
            Log.w(TAG, "Shizuku 不可用，跳过自动授权")
            return
        }

        // 检查是否已授权
        if (!ShizukuApi.shizukuGrantedFlow.value) {
            Log.w(TAG, "Shizuku 未授权，跳过自动授权")
            return
        }

        Log.d(TAG, "=== 开始 Shizuku 自动授权 ===")

        try {
            // 连接 Shizuku 服务（这会自动调用 grantSelf()）
            val context = ShizukuApi.getOrConnect()
            if (context == null) {
                Log.w(TAG, "Shizuku 连接失败")
                return
            }

            // 授予所有常用权限
            grantAllCommonPermissions(APP_PACKAGE_NAME)

            // 启用无障碍服务
            enableAccessibilityService(
                APP_PACKAGE_NAME,
                "top.yling.ozx.guiagent.MyAccessibilityService"
            )

            // 忽略电池优化
            ignoreBatteryOptimization(APP_PACKAGE_NAME)

            // 授予后台弹出界面权限
            grantBackgroundActivityPermission(APP_PACKAGE_NAME)

            hasGranted = true
            Log.d(TAG, "=== Shizuku 自动授权完成 ===")
        } catch (e: Exception) {
            Log.e(TAG, "自动授权异常: ${e.message}", e)
        }
    }

    /**
     * 一键授予所有常用权限
     */
    suspend fun grantAllCommonPermissions(packageName: String) {
        val context = ShizukuApi.getOrConnect() ?: return

        withContext(Dispatchers.IO) {
            Log.d(TAG, "开始授予运行时权限...")

            // 1. 运行时权限
            val runtimePermissions = listOf(
                "android.permission.CAMERA",
                "android.permission.RECORD_AUDIO",
                "android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.READ_CONTACTS",
                "android.permission.WRITE_CONTACTS",
                "android.permission.READ_CALENDAR",
                "android.permission.WRITE_CALENDAR",
                "android.permission.READ_PHONE_STATE",
                "android.permission.CALL_PHONE",
                "android.permission.READ_SMS",
                "android.permission.SEND_SMS",
                "android.permission.READ_CALL_LOG",
                "android.permission.WRITE_CALL_LOG"
            )

            runtimePermissions.forEach { permission ->
                try {
                    val result = context.execCommand("pm grant $packageName $permission")
                    if (result.ok) {
                        Log.d(TAG, "已授予: $permission")
                    }
                } catch (e: Exception) {
                    // 某些权限可能不存在或无法授予，忽略
                }
                // 增加延迟，避免系统安全机制触发
                delay(50)
            }

            // 2. 特殊权限（使用 appops）
            // 注意：REQUEST_INSTALL_PACKAGES 和 MANAGE_EXTERNAL_STORAGE 在 Android 11 及以下会导致应用重启
            // Android 12+ 可以安全授予这些权限
            Log.d(TAG, "开始授予特殊权限...")
            val baseSpecialPermissions = mutableMapOf(
                "SYSTEM_ALERT_WINDOW" to "allow",      // 悬浮窗
                "WRITE_SETTINGS" to "allow",          // 修改系统设置
                "GET_USAGE_STATS" to "allow",         // 使用统计
                "RUN_IN_BACKGROUND" to "allow",       // 后台运行
                "RUN_ANY_IN_BACKGROUND" to "allow"    // 任意后台运行
            )
            
            // Android 12+ (API 31+) 可以安全授予这些权限
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//                baseSpecialPermissions["REQUEST_INSTALL_PACKAGES"] = "allow"  // 安装应用
//                baseSpecialPermissions["MANAGE_EXTERNAL_STORAGE"] = "allow"  // 管理外部存储
//                Log.d(TAG, "Android 12+ 检测到，将授予 REQUEST_INSTALL_PACKAGES 和 MANAGE_EXTERNAL_STORAGE")
//            } else {
//                Log.d(TAG, "Android 11 及以下，跳过 REQUEST_INSTALL_PACKAGES 和 MANAGE_EXTERNAL_STORAGE（避免应用重启）")
//            }
            
            val specialPermissions = baseSpecialPermissions

            specialPermissions.forEach { (op, mode) ->
                try {
                    val result = context.execCommand("appops set $packageName $op $mode")
                    if (result.ok) {
                        Log.d(TAG, "已设置 appops: $op -> $mode")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "设置 appops $op 失败: ${e.message}")
                }
                // 增加延迟，避免系统安全机制触发
                delay(100)
            }

            // 3. 通知权限（Android 13+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    context.execCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
                    Log.d(TAG, "已授予: POST_NOTIFICATIONS")
                } catch (e: Exception) {
                    Log.w(TAG, "授予通知权限失败: ${e.message}")
                }
            }

            Log.d(TAG, "运行时权限和特殊权限授予完成")
        }
    }

    /**
     * 启用无障碍服务
     */
    suspend fun enableAccessibilityService(
        packageName: String,
        serviceClassName: String
    ) {
        val context = ShizukuApi.getOrConnect() ?: return

        withContext(Dispatchers.IO) {
            val fullName = "$packageName/$serviceClassName"
            Log.d(TAG, "尝试启用无障碍服务: $fullName")

            try {
                // 获取当前已启用的服务
                val getCurrentCmd = "settings get secure enabled_accessibility_services"
                val currentResult = context.execCommand(getCurrentCmd)
                val current = currentResult.result.trim()

                // 检查是否已启用
                if (current.contains(fullName) || current.contains("$packageName/.MyAccessibilityService")) {
                    Log.d(TAG, "无障碍服务已启用，跳过")
                    return@withContext
                }

                // 追加新服务
                val newValue = if (current.isEmpty() || current == "null") {
                    fullName
                } else {
                    "$current:$fullName"
                }

                // 设置服务列表
                val putResult = context.execCommand("settings put secure enabled_accessibility_services \"$newValue\"")
                if (!putResult.ok) {
                    Log.e(TAG, "设置无障碍服务失败: ${putResult.error}")
                }

                // 启用无障碍总开关
                context.execCommand("settings put secure accessibility_enabled 1")

                Log.d(TAG, "无障碍服务已启用: $fullName")
            } catch (e: Exception) {
                Log.e(TAG, "启用无障碍服务异常: ${e.message}", e)
            }
        }
    }

    /**
     * 忽略电池优化
     */
    suspend fun ignoreBatteryOptimization(packageName: String) {
        val context = ShizukuApi.getOrConnect() ?: return

        withContext(Dispatchers.IO) {
            Log.d(TAG, "设置电池优化豁免...")

            try {
                // 方法1：通过 dumpsys deviceidle 添加白名单
                val result1 = context.execCommand("dumpsys deviceidle whitelist +$packageName")
                if (result1.ok) {
                    Log.d(TAG, "已添加到电池优化白名单（dumpsys）")
                }

                // 方法2：通过 cmd 命令（Android 9+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val result2 = context.execCommand("cmd deviceidle whitelist +$packageName")
                    if (result2.ok) {
                        Log.d(TAG, "已添加到电池优化白名单（cmd）")
                    }
                }

                // 方法3：通过 appops 设置
                context.execCommand("appops set $packageName RUN_IN_BACKGROUND allow")
                context.execCommand("appops set $packageName RUN_ANY_IN_BACKGROUND allow")
                Log.d(TAG, "已设置后台运行权限")

            } catch (e: Exception) {
                Log.e(TAG, "设置电池优化豁免异常: ${e.message}", e)
            }
        }
    }

    /**
     * 授予后台弹出界面权限
     * 适配不同厂商的权限
     */
    suspend fun grantBackgroundActivityPermission(packageName: String) {
        val context = ShizukuApi.getOrConnect() ?: return

        withContext(Dispatchers.IO) {
            Log.d(TAG, "设置后台弹出界面权限...")

            try {
                // 通用 Android 10+ 后台启动 Activity 权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.execCommand("appops set $packageName android:system_exempt_from_activity_bg_start_restriction allow")
                    Log.d(TAG, "已设置后台启动 Activity 权限")
                }

                // 小米 MIUI 后台弹出界面权限
                // opCode 10021 是 MIUI 特有的后台弹出界面权限
                context.execCommand("appops set $packageName 10021 allow")
                Log.d(TAG, "已尝试设置 MIUI 后台弹出界面权限")

                // OPPO/Realme ColorOS 后台弹出权限
                context.execCommand("appops set $packageName 10020 allow")

                // VIVO OriginOS 后台弹出权限
                context.execCommand("appops set $packageName 20000 allow")

                // 华为 EMUI/HarmonyOS
                context.execCommand("appops set $packageName 100001 allow")

            } catch (e: Exception) {
                Log.e(TAG, "设置后台弹出界面权限异常: ${e.message}", e)
            }
        }
    }

    /**
     * 检查权限是否已授予
     */
    suspend fun checkPermission(packageName: String, permission: String): Boolean {
        val context = ShizukuApi.getOrConnect() ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val command = "dumpsys package $packageName | grep '$permission.*granted=true'"
                val result = context.execCommand(command)
                result.ok && result.result.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 列出所有已授予的权限
     */
    suspend fun listGrantedPermissions(packageName: String): List<String> {
        val context = ShizukuApi.getOrConnect() ?: return emptyList()

        return withContext(Dispatchers.IO) {
            try {
                val command = "dumpsys package $packageName | grep 'granted=true'"
                val result = context.execCommand(command)

                if (result.ok) {
                    result.result.split("\n")
                        .mapNotNull { line ->
                            val match = Regex("(android\\.permission\\.[A-Z_]+)").find(line)
                            match?.value
                        }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 检查无障碍服务是否已启用
     */
    suspend fun isAccessibilityServiceEnabled(
        packageName: String,
        serviceClassName: String
    ): Boolean {
        val context = ShizukuApi.getOrConnect() ?: return false

        return withContext(Dispatchers.IO) {
            try {
                val fullName = "$packageName/$serviceClassName"
                val result = context.execCommand("settings get secure enabled_accessibility_services")
                result.ok && result.result.contains(fullName)
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 禁用无障碍服务
     */
    suspend fun disableAccessibilityService(
        packageName: String,
        serviceClassName: String
    ) {
        val context = ShizukuApi.getOrConnect() ?: return

        withContext(Dispatchers.IO) {
            val fullName = "$packageName/$serviceClassName"
            Log.d(TAG, "禁用无障碍服务: $fullName")

            try {
                // 获取当前服务列表
                val result = context.execCommand("settings get secure enabled_accessibility_services")
                val current = result.result.trim()

                // 移除指定服务
                val newValue = current
                    .split(":")
                    .filter { !it.contains(packageName) }
                    .joinToString(":")

                // 写回
                context.execCommand("settings put secure enabled_accessibility_services \"$newValue\"")
                Log.d(TAG, "无障碍服务已禁用")
            } catch (e: Exception) {
                Log.e(TAG, "禁用无障碍服务异常: ${e.message}", e)
            }
        }
    }

    /**
     * 授予安装应用权限（REQUEST_INSTALL_PACKAGES）
     * 
     * ⚠️ 警告：此权限会导致应用被系统强制终止并重启
     * 建议在用户明确需要时再调用此方法
     * 
     * @param packageName 包名
     * @return 是否成功授予
     */
    suspend fun grantInstallPackagesPermission(packageName: String): Boolean {
        val context = ShizukuApi.getOrConnect() ?: return false

        return withContext(Dispatchers.IO) {
            try {
                Log.w(TAG, "⚠️ 授予 REQUEST_INSTALL_PACKAGES 权限，这会导致应用重启")
                val result = context.execCommand("appops set $packageName REQUEST_INSTALL_PACKAGES allow")
                if (result.ok) {
                    Log.d(TAG, "已设置 appops: REQUEST_INSTALL_PACKAGES -> allow")
                    Log.w(TAG, "⚠️ 应用即将被系统终止并重启")
                    return@withContext true
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "设置 REQUEST_INSTALL_PACKAGES 失败: ${e.message}", e)
                false
            }
        }
    }

    /**
     * 重置授权状态（用于测试）
     */
    fun resetGrantedState() {
        hasGranted = false
    }
}