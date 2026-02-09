package top.yling.ozx.guiagent.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog

/**
 * 权限管理工具类
 * 用于检查和申请后台启动、悬浮窗、电池优化等权限
 */
object PermissionHelper {
    private const val TAG = "PermissionHelper"

    /**
     * 检查是否有悬浮窗权限
     *
     * 优先使用 Settings.canDrawOverlays() 检查，如果返回 false 且 Shizuku 可用，
     * 则通过 appops 检查悬浮窗权限状态（因为 Shizuku 授权的悬浮窗权限可能无法被
     * Settings.canDrawOverlays() 正确检测到）
     */
    fun hasOverlayPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        // 首先使用标准 API 检查
        if (Settings.canDrawOverlays(context)) {
            return true
        }

        // 如果标准 API 返回 false，尝试通过 appops 检查（针对 Shizuku 授权的情况）
        return checkOverlayPermissionViaAppOps(context)
    }

    /**
     * 通过 AppOps 检查悬浮窗权限
     * 用于检测通过 Shizuku 授权的悬浮窗权限
     */
    private fun checkOverlayPermissionViaAppOps(context: Context): Boolean {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
                ?: return false

            val packageName = context.packageName
            val uid = context.applicationInfo.uid

            // OPSTR_SYSTEM_ALERT_WINDOW 从 API 23 (M) 开始可用
            @Suppress("DEPRECATION")
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    uid,
                    packageName
                )
            } else {
                // API 23-28 使用 checkOpNoThrow，参数类型是 String
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    uid,
                    packageName
                )
            }

            val allowed = mode == android.app.AppOpsManager.MODE_ALLOWED
            if (allowed) {
                Log.d(TAG, "悬浮窗权限通过 AppOps 检测到已授权")
            }
            return allowed
        } catch (e: Exception) {
            Log.w(TAG, "通过 AppOps 检查悬浮窗权限失败: ${e.message}")
            return false
        }
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission(activity: Activity, requestCode: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                AlertDialog.Builder(activity)
                    .setTitle("需要悬浮窗权限")
                    .setMessage("为了在后台打开应用，需要授予悬浮窗权限")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}")
                        )
                        activity.startActivityForResult(intent, requestCode)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    /**
     * 检查是否在电池优化白名单中
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    /**
     * 请求加入电池优化白名单
     */
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations(activity)) {
                AlertDialog.Builder(activity)
                    .setTitle("需要电池优化白名单")
                    .setMessage("为了保持后台运行和及时响应指令，需要加入电池优化白名单")
                    .setPositiveButton("去设置") { _, _ ->
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:${activity.packageName}")
                            activity.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "打开电池优化设置失败: ${e.message}")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    /**
     * 检查是否是小米设备
     */
    fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Redmi", ignoreCase = true)
    }

    /**
     * 检查是否是华为设备
     */
    fun isHuaweiDevice(): Boolean {
        return Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ||
                Build.BRAND.equals("Huawei", ignoreCase = true) ||
                Build.BRAND.equals("Honor", ignoreCase = true)
    }

    /**
     * 检查是否是OPPO设备
     */
    fun isOppoDevice(): Boolean {
        return Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
                Build.BRAND.equals("OPPO", ignoreCase = true) ||
                Build.BRAND.equals("OnePlus", ignoreCase = true) ||
                Build.BRAND.equals("realme", ignoreCase = true)
    }

    /**
     * 检查是否是VIVO设备
     */
    fun isVivoDevice(): Boolean {
        return Build.MANUFACTURER.equals("vivo", ignoreCase = true) ||
                Build.BRAND.equals("vivo", ignoreCase = true)
    }

    /**
     * 检查自启动权限是否已开启
     * 对于不同厂商设备使用不同的检查方式
     */
    fun isAutoStartEnabled(context: Context): Boolean {
        return when {
            isXiaomiDevice() -> isXiaomiAutoStartEnabled(context)
            isHuaweiDevice() -> isHuaweiAutoStartEnabled(context)
            isOppoDevice() -> isOppoAutoStartEnabled(context)
            isVivoDevice() -> isVivoAutoStartEnabled(context)
            else -> true // 其他设备默认认为已开启
        }
    }

    /**
     * 检查是否需要自启动权限
     * 对于特定厂商设备，如果自启动权限未开启，则返回true
     * 
     * 注意：当前已暂时禁用自启动权限检查，直接返回false，不再显示弹窗
     */
    fun needsAutoStartPermission(context: Context): Boolean {
        // 暂时禁用自启动权限检查，不再显示弹窗
        return false
        
        // 以下代码已暂时注释，需要时可以恢复
        /*
        // 如果不是需要检查自启动权限的设备，直接返回false
        if (!isXiaomiDevice() && !isHuaweiDevice() && !isOppoDevice() && !isVivoDevice()) {
            return false
        }
        
        // 检查用户是否选择了"不再提示"
        val prefs = context.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
        if (isXiaomiDevice()) {
            val dontShowAgain = prefs.getBoolean("xiaomi_autostart_dont_show_again", false)
            if (dontShowAgain) {
                Log.d(TAG, "用户已选择不再显示小米自启动权限提示")
                return false
            }
        }
        
        // 检查自启动权限是否已开启
        return !isAutoStartEnabled(context)
        */
    }

    /**
     * 检查小米设备自启动权限是否已开启
     * 由于MIUI没有标准的API检查自启动权限，使用多种方法尝试检查
     */
    private fun isXiaomiAutoStartEnabled(context: Context): Boolean {
        // 首先检查用户是否已经设置过（通过SharedPreferences记录）
        val prefs = context.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
        val autoStartSetByUser = prefs.getBoolean("xiaomi_autostart_set_by_user", false)
        if (autoStartSetByUser) {
            Log.d(TAG, "用户已设置过小米自启动权限（通过SharedPreferences记录）")
            return true
        }

        try {
            // 方法1: 通过反射调用 MIUI AppOpsUtils 检查（最可靠的方法）
            try {
                val clazz = Class.forName("android.miui.AppOpsUtils")
                val method = clazz.getMethod("getApplicationAutoStart", Context::class.java, String::class.java)
                val result = method.invoke(null, context, context.packageName) as? Boolean
                if (result != null) {
                    Log.d(TAG, "小米自启动权限状态（反射AppOpsUtils）: $result")
                    // 如果检查到已开启，记录到SharedPreferences
                    if (result) {
                        prefs.edit().putBoolean("xiaomi_autostart_set_by_user", true).apply()
                    }
                    return result
                }
            } catch (e: ClassNotFoundException) {
                Log.w(TAG, "MIUI AppOpsUtils 类不存在，可能不是MIUI系统")
            } catch (e: Exception) {
                Log.w(TAG, "通过反射检查小米自启动权限失败: ${e.message}")
            }

            // 方法2: 通过 ContentResolver 查询 MIUI 自启动权限状态
            try {
                val uri = Uri.parse("content://settings/secure")
                val cursor = context.contentResolver.query(
                    uri,
                    null,
                    "name=?",
                    arrayOf("autostart_${context.packageName}"),
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val valueIndex = it.getColumnIndex("value")
                        if (valueIndex >= 0) {
                            val value = it.getInt(valueIndex)
                            Log.d(TAG, "小米自启动权限状态（ContentResolver）: $value")
                            val enabled = value == 1
                            // 如果检查到已开启，记录到SharedPreferences
                            if (enabled) {
                                prefs.edit().putBoolean("xiaomi_autostart_set_by_user", true).apply()
                            }
                            return enabled
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "通过ContentResolver检查小米自启动权限失败: ${e.message}")
            }

            // 方法3: 通过 Settings.Secure 查询
            try {
                val value = Settings.Secure.getInt(
                    context.contentResolver,
                    "autostart_${context.packageName}",
                    -1
                )
                if (value != -1) {
                    Log.d(TAG, "小米自启动权限状态（Settings.Secure）: $value")
                    val enabled = value == 1
                    // 如果检查到已开启，记录到SharedPreferences
                    if (enabled) {
                        prefs.edit().putBoolean("xiaomi_autostart_set_by_user", true).apply()
                    }
                    return enabled
                }
            } catch (e: Exception) {
                Log.w(TAG, "通过Settings.Secure检查小米自启动权限失败: ${e.message}")
            }

            // 如果所有方法都失败，无法确定状态，返回false（需要设置）
            Log.w(TAG, "无法检查小米自启动权限状态，需要用户手动设置")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检查小米自启动权限失败: ${e.message}", e)
            // 检查失败时，返回false（需要设置）
            return false
        }
    }

    /**
     * 标记小米自启动权限已设置（用户从设置页面返回后调用）
     */
    fun markXiaomiAutoStartAsSet(context: Context) {
        val prefs = context.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("xiaomi_autostart_set_by_user", true).apply()
        Log.d(TAG, "已标记小米自启动权限为已设置")
    }

    /**
     * 检查华为设备自启动权限是否已开启
     */
    private fun isHuaweiAutoStartEnabled(context: Context): Boolean {
        try {
            // 华为设备无法直接检查，需要通过其他方式判断
            // 这里暂时返回false，让用户去设置
            // 实际可以通过检查应用是否在后台运行正常来判断
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检查华为自启动权限失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 检查OPPO设备自启动权限是否已开启
     */
    private fun isOppoAutoStartEnabled(context: Context): Boolean {
        try {
            // OPPO设备无法直接检查，需要通过其他方式判断
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检查OPPO自启动权限失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 检查VIVO设备自启动权限是否已开启
     */
    private fun isVivoAutoStartEnabled(context: Context): Boolean {
        try {
            // VIVO设备无法直接检查，需要通过其他方式判断
            return false
        } catch (e: Exception) {
            Log.e(TAG, "检查VIVO自启动权限失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 引导用户设置自启动权限（根据设备厂商）
     */
    fun requestAutoStartPermission(activity: Activity) {
        when {
            isXiaomiDevice() -> guideToXiaomiAutoStart(activity)
            isHuaweiDevice() -> guideToHuaweiAutoStart(activity)
            isOppoDevice() -> guideToOppoAutoStart(activity)
            isVivoDevice() -> guideToVivoAutoStart(activity)
            else -> {
                // 其他设备，引导到应用详情页
                AlertDialog.Builder(activity)
                    .setTitle("需要自启动权限")
                    .setMessage("为了在后台正常打开应用，请在应用设置中开启自启动权限")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${activity.packageName}")
                        }
                        activity.startActivity(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    /**
     * 直接跳转到自启动权限设置页面（不弹确认对话框）
     * 用于权限引导页面的流程式授权
     */
    fun requestAutoStartPermissionDirect(activity: Activity, onComplete: () -> Unit) {
        when {
            isXiaomiDevice() -> {
                openXiaomiAutoStartSettings(activity)
                onComplete()
            }
            isHuaweiDevice() -> {
                openHuaweiAutoStartSettings(activity)
                onComplete()
            }
            isOppoDevice() -> {
                openOppoAutoStartSettings(activity)
                onComplete()
            }
            isVivoDevice() -> {
                openVivoAutoStartSettings(activity)
                onComplete()
            }
            else -> {
                // 其他设备，直接跳转到应用详情页
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
                onComplete()
            }
        }
    }

    /**
     * 直接请求悬浮窗权限（不弹确认对话框）
     */
    fun requestOverlayPermissionDirect(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    /**
     * 直接请求电池优化白名单（不弹确认对话框）
     */
    fun requestIgnoreBatteryOptimizationsDirect(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(activity)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "打开电池优化设置失败: ${e.message}")
            }
        }
    }

    /**
     * 引导用户打开华为自启动权限
     */
    private fun guideToHuaweiAutoStart(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要自启动权限")
            .setMessage(
                "检测到您使用的是华为/荣耀设备。\n\n" +
                "为了在后台正常打开应用，需要授予自启动权限。\n\n" +
                "点击确定将跳转到权限设置页面"
            )
            .setPositiveButton("去设置") { _, _ ->
                openHuaweiAutoStartSettings(activity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 打开华为自启动权限设置页面
     */
    private fun openHuaweiAutoStartSettings(context: Context) {
        try {
            val intents = listOf(
                // 优先1: 华为应用启动管理
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                },
                // 优先2: 华为权限管理
                Intent().apply {
                    component = ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.permissionmanager.ui.MainActivity"
                    )
                },
                // 备用: 应用详情页
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )

            for (intent in intents) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "成功打开华为自启动设置")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "打开华为自启动设置失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开华为自启动设置失败: ${e.message}", e)
        }
    }

    /**
     * 引导用户打开OPPO自启动权限
     */
    private fun guideToOppoAutoStart(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要自启动权限")
            .setMessage(
                "检测到您使用的是OPPO/OnePlus/realme设备。\n\n" +
                "为了在后台正常打开应用，需要授予自启动权限。\n\n" +
                "点击确定将跳转到权限设置页面"
            )
            .setPositiveButton("去设置") { _, _ ->
                openOppoAutoStartSettings(activity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 打开OPPO自启动权限设置页面
     */
    private fun openOppoAutoStartSettings(context: Context) {
        try {
            val intents = listOf(
                // 优先1: OPPO自启动管理
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                },
                // 优先2: OPPO权限管理
                Intent().apply {
                    component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.PermissionManagerActivity"
                    )
                },
                // 备用: 应用详情页
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )

            for (intent in intents) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "成功打开OPPO自启动设置")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "打开OPPO自启动设置失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开OPPO自启动设置失败: ${e.message}", e)
        }
    }

    /**
     * 引导用户打开VIVO自启动权限
     */
    private fun guideToVivoAutoStart(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("需要自启动权限")
            .setMessage(
                "检测到您使用的是VIVO设备。\n\n" +
                "为了在后台正常打开应用，需要授予自启动权限。\n\n" +
                "点击确定将跳转到权限设置页面"
            )
            .setPositiveButton("去设置") { _, _ ->
                openVivoAutoStartSettings(activity)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 打开VIVO自启动权限设置页面
     */
    private fun openVivoAutoStartSettings(context: Context) {
        try {
            val intents = listOf(
                // 优先1: VIVO自启动管理
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                },
                // 优先2: VIVO权限管理
                Intent().apply {
                    component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                    )
                    putExtra("packagename", context.packageName)
                },
                // 备用: 应用详情页
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )

            for (intent in intents) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "成功打开VIVO自启动设置")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "打开VIVO自启动设置失败: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开VIVO自启动设置失败: ${e.message}", e)
        }
    }

    /**
     * 引导用户打开小米后台弹出界面权限
     */
    fun guideToXiaomiAutoStart(activity: Activity) {
        val prefs = activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
        val dontShowAgain = prefs.getBoolean("xiaomi_autostart_dont_show_again", false)
        
        // 如果用户选择了"不再提示"，直接返回
        if (dontShowAgain) {
            Log.d(TAG, "用户已选择不再显示小米自启动权限提示")
            return
        }

        AlertDialog.Builder(activity)
            .setTitle("需要后台弹出权限")
            .setMessage(
                "检测到您使用的是小米设备。\n\n" +
                "为了在后台正常打开应用，需要授予：\n" +
                "✓ 后台弹出界面权限\n" +
                "✓ 自启动权限（推荐）\n\n" +
                "点击确定将跳转到权限设置页面"
            )
            .setPositiveButton("去设置") { _, _ ->
                openXiaomiAutoStartSettings(activity)
            }
            .setNeutralButton("不再提示") { _, _ ->
                // 用户选择不再提示，记录到SharedPreferences
                prefs.edit().putBoolean("xiaomi_autostart_dont_show_again", true).apply()
                // 同时标记为已设置，避免后续检查
                markXiaomiAutoStartAsSet(activity)
                Log.d(TAG, "用户选择不再显示小米自启动权限提示")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 打开小米后台弹出界面权限设置页面
     * 目标：设置 -> 应用设置 -> 授权管理 -> 自启动管理
     */
    private fun openXiaomiAutoStartSettings(context: Context) {
        try {
            // 尝试多种方式打开小米自启动管理页面
            val intents = listOf(
                // 优先1: 直接打开自启动管理（设置 -> 应用设置 -> 授权管理 -> 自启动管理）
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                },

                // 优先2: 通过授权管理页面打开自启动管理
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                    // 尝试跳转到自启动管理
                    putExtra("target", "autostart")
                },

                // 优先3: 直接打开后台弹出界面权限管理（MIUI 新版本）
                Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                    setClassName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                },

                // 优先4: 使用系统设置跳转（部分MIUI版本支持）
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    // 尝试添加 fragment 参数跳转到权限页面
                    putExtra(":settings:show_fragment", "com.android.settings.applications.AppPermissionsFragment")
                    putExtra(":settings:show_fragment_args", android.os.Bundle().apply {
                        putString("package", context.packageName)
                    })
                },

                // 优先5: 安全中心的后台弹出界面管理
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
                    )
                    putExtra("extra_pkgname", context.packageName)
                },

                // 备用1: 安全中心授权管理
                Intent().apply {
                    action = "miui.intent.action.POWER_HIDE_MODE_APP_LIST"
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.powercenter.PowerHideApps"
                    )
                },

                // 备用2: 安全中心主页
                Intent().apply {
                    component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.securitycenter.MainActivity"
                    )
                },

                // 备用3: 系统应用详情页
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )

            var success = false
            for ((index, intent) in intents.withIndex()) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    success = true
                    val pageName = when (index) {
                        0 -> "自启动管理（设置 -> 应用设置 -> 授权管理 -> 自启动管理）"
                        1 -> "授权管理页面"
                        2 -> "后台弹出界面权限（新版）"
                        3 -> "系统权限设置"
                        4 -> "应用权限编辑"
                        5 -> "授权管理"
                        6 -> "安全中心主页"
                        else -> "应用详情页"
                    }
                    Log.i(TAG, "成功打开小米设置: $pageName")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "尝试打开小米设置失败 (方式${index + 1}): ${e.message}")
                }
            }

            if (!success) {
                Log.e(TAG, "所有尝试都失败，无法打开小米权限设置")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开小米权限设置失败: ${e.message}", e)
        }
    }

    /**
     * 一次性检查所有必要权限
     */
    fun checkAllPermissions(activity: Activity, showDialog: Boolean = true): PermissionStatus {
        val overlayGranted = hasOverlayPermission(activity)
        val batteryOptimized = isIgnoringBatteryOptimizations(activity)
        val isXiaomi = isXiaomiDevice()

        val status = PermissionStatus(
            overlayPermission = overlayGranted,
            batteryOptimization = batteryOptimized,
            isXiaomiDevice = isXiaomi,
            allGranted = overlayGranted && batteryOptimized
        )

        if (showDialog && !status.allGranted) {
            showPermissionGuideDialog(activity, status)
        }

        return status
    }

    /**
     * 显示权限引导对话框
     */
    private fun showPermissionGuideDialog(activity: Activity, status: PermissionStatus) {
        val missingPermissions = mutableListOf<String>()
        if (!status.overlayPermission) {
            missingPermissions.add("• 悬浮窗权限（用于后台打开应用）")
        }
        if (!status.batteryOptimization) {
            missingPermissions.add("• 电池优化白名单（保持后台运行）")
        }
        if (status.isXiaomiDevice) {
            missingPermissions.add("• 自启动和后台弹出权限（小米设备必需）")
        }

        val message = "为了正常使用后台打开应用功能，需要授予以下权限：\n\n" +
                missingPermissions.joinToString("\n") +
                "\n\n请在接下来的页面中授予相应权限"

        AlertDialog.Builder(activity)
            .setTitle("权限设置")
            .setMessage(message)
            .setPositiveButton("开始设置") { _, _ ->
                if (!status.overlayPermission) {
                    requestOverlayPermission(activity)
                } else if (!status.batteryOptimization) {
                    requestIgnoreBatteryOptimizations(activity)
                } else if (status.isXiaomiDevice) {
                    guideToXiaomiAutoStart(activity)
                }
            }
            .setNegativeButton("稍后设置", null)
            .show()
    }

    /**
     * 权限状态数据类
     */
    data class PermissionStatus(
        val overlayPermission: Boolean,
        val batteryOptimization: Boolean,
        val isXiaomiDevice: Boolean,
        val allGranted: Boolean
    )
}
