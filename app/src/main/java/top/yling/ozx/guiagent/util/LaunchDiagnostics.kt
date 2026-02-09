package top.yling.ozx.guiagent.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import top.yling.ozx.guiagent.MyAccessibilityService

/**
 * 应用启动诊断工具
 * 用于检测和诊断后台启动应用的问题
 */
object LaunchDiagnostics {
    private const val TAG = "LaunchDiagnostics"

    /**
     * 完整的系统状态检查
     */
    fun checkSystemStatus(context: Context): DiagnosticReport {
        val report = DiagnosticReport()

        // 1. 检查无障碍服务
        report.accessibilityServiceEnabled = MyAccessibilityService.isServiceEnabled()
        report.accessibilityServiceInstance = MyAccessibilityService.instance != null

        // 2. 检查悬浮窗权限
        report.overlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        // 3. 检查电池优化
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            report.batteryOptimizationWhitelisted = pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            report.batteryOptimizationWhitelisted = true
        }

        // 4. 检查应用是否在前台
        report.appInForeground = isAppInForeground(context)

        // 5. 设备信息
        report.deviceManufacturer = Build.MANUFACTURER
        report.deviceModel = Build.MODEL
        report.androidVersion = Build.VERSION.SDK_INT
        report.isXiaomiDevice = PermissionHelper.isXiaomiDevice()

        // 6. 上下文类型
        report.contextType = context.javaClass.simpleName

        return report
    }

    /**
     * 检查应用是否在前台
     */
    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Android 5.0+ 使用 getRunningAppProcesses
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val processes = activityManager.runningAppProcesses ?: return false
            val packageName = context.packageName

            for (process in processes) {
                if (process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    process.processName == packageName) {
                    return true
                }
            }
            return false
        }

        // 旧版本使用 getRunningTasks（已废弃）
        @Suppress("DEPRECATION")
        val tasks = activityManager.getRunningTasks(1)
        if (tasks.isNotEmpty()) {
            val topActivity = tasks[0].topActivity
            return topActivity?.packageName == context.packageName
        }

        return false
    }

    /**
     * 打印诊断报告
     */
    fun printReport(report: DiagnosticReport) {
        val sb = StringBuilder()
        sb.appendLine("==================== 启动诊断报告 ====================")
        sb.appendLine("设备信息:")
        sb.appendLine("  厂商: ${report.deviceManufacturer}")
        sb.appendLine("  型号: ${report.deviceModel}")
        sb.appendLine("  Android版本: ${report.androidVersion}")
        sb.appendLine("  是否小米设备: ${if (report.isXiaomiDevice) "是" else "否"}")
        sb.appendLine()

        sb.appendLine("应用状态:")
        sb.appendLine("  应用在前台: ${if (report.appInForeground) "✓ 是" else "✗ 否"}")
        sb.appendLine("  上下文类型: ${report.contextType}")
        sb.appendLine()

        sb.appendLine("权限状态:")
        sb.appendLine("  无障碍服务已启用: ${if (report.accessibilityServiceEnabled) "✓ 是" else "✗ 否"}")
        sb.appendLine("  无障碍服务实例: ${if (report.accessibilityServiceInstance) "✓ 存在" else "✗ 不存在"}")
        sb.appendLine("  悬浮窗权限: ${if (report.overlayPermission) "✓ 已授予" else "✗ 未授予"}")
        sb.appendLine("  电池优化白名单: ${if (report.batteryOptimizationWhitelisted) "✓ 已加入" else "✗ 未加入"}")
        sb.appendLine()

        sb.appendLine("建议:")
        val recommendations = generateRecommendations(report)
        if (recommendations.isEmpty()) {
            sb.appendLine("  ✓ 所有配置正常")
        } else {
            recommendations.forEachIndexed { index, recommendation ->
                sb.appendLine("  ${index + 1}. $recommendation")
            }
        }
        sb.appendLine("======================================================")

        Log.i(TAG, sb.toString())
    }

    /**
     * 生成建议
     */
    private fun generateRecommendations(report: DiagnosticReport): List<String> {
        val recommendations = mutableListOf<String>()

        if (!report.accessibilityServiceEnabled) {
            recommendations.add("⚠️ 请启用无障碍服务（最重要，可绕过大部分后台限制）")
        }

        if (!report.accessibilityServiceInstance) {
            recommendations.add("⚠️ 无障碍服务实例不存在，请检查服务是否正常运行")
        }

        if (!report.overlayPermission) {
            recommendations.add("⚠️ 请授予悬浮窗权限 (SYSTEM_ALERT_WINDOW)")
        }

        if (!report.batteryOptimizationWhitelisted) {
            recommendations.add("⚠️ 请将应用加入电池优化白名单")
        }

        if (report.isXiaomiDevice) {
            recommendations.add("⚠️ 小米设备需要额外配置：")
            recommendations.add("   - 打开\"安全中心\" → \"应用管理\"")
            recommendations.add("   - 找到应用，开启\"自启动\"")
            recommendations.add("   - 找到应用，开启\"后台弹出界面\"")
        }

        if (!report.appInForeground) {
            recommendations.add("ℹ️ 应用当前不在前台，后台启动可能受限")
        }

        return recommendations
    }

    /**
     * 诊断报告数据类
     */
    data class DiagnosticReport(
        var accessibilityServiceEnabled: Boolean = false,
        var accessibilityServiceInstance: Boolean = false,
        var overlayPermission: Boolean = false,
        var batteryOptimizationWhitelisted: Boolean = false,
        var appInForeground: Boolean = false,
        var deviceManufacturer: String = "",
        var deviceModel: String = "",
        var androidVersion: Int = 0,
        var isXiaomiDevice: Boolean = false,
        var contextType: String = ""
    )

    /**
     * 在启动应用前进行检查
     */
    fun preflightCheck(context: Context, packageName: String): PreflightResult {
        val report = checkSystemStatus(context)
        val warnings = mutableListOf<String>()
        var canProceed = true

        // 检查目标应用是否存在
        val pm = context.packageManager
        val targetIntent = pm.getLaunchIntentForPackage(packageName)
        if (targetIntent == null) {
            return PreflightResult(
                canProceed = false,
                warnings = listOf("目标应用不存在: $packageName"),
                report = report
            )
        }

        // 评估成功率
        if (!report.accessibilityServiceEnabled) {
            warnings.add("无障碍服务未启用，成功率降低")
        }

        if (!report.overlayPermission) {
            warnings.add("悬浮窗权限未授予，部分启动策略不可用")
        }

        if (!report.appInForeground && !report.accessibilityServiceEnabled) {
            warnings.add("应用在后台且无障碍服务未启用，启动可能失败")
            canProceed = false
        }

        if (report.isXiaomiDevice && !report.accessibilityServiceEnabled) {
            warnings.add("小米设备需要额外配置权限")
        }

        return PreflightResult(
            canProceed = canProceed,
            warnings = warnings,
            report = report
        )
    }

    /**
     * 启动前检查结果
     */
    data class PreflightResult(
        val canProceed: Boolean,
        val warnings: List<String>,
        val report: DiagnosticReport
    )
}
