package top.yling.ozx.guiagent.websocket

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.InternalSerializationApi
import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.shizuku.ShizukuApi
import top.yling.ozx.guiagent.util.AppSettings
import top.yling.ozx.guiagent.util.VirtualDisplayManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 应用启动器
 *
 * 启动策略优先级：
 * 1. Shizuku am start 命令（最可靠，shell权限可绕过所有后台限制）
 * 2. 无障碍服务启动（备选方案）
 */
@OptIn(InternalSerializationApi::class)
class AppLauncher(private val context: Context) {

    companion object {
        private const val TAG = "AppLauncher"
    }

    /**
     * 通过包名打开应用
     */
    fun openByPackage(packageName: String): Boolean {
        return try {
            Log.i(TAG, "========== 开始启动应用: $packageName ==========")

            // 检查目标应用是否存在
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Log.e(TAG, "找不到应用: $packageName")
                return false
            }

            // 获取启动 Activity 组件名
            val componentName = launchIntent.component

            // 策略1: 优先使用 Shizuku am start 命令（最可靠）
            // 使用 runBlocking 同步获取完整的 ShizukuContext（包含 serviceWrapper）
            val shizukuContext = runBlocking { ShizukuApi.getOrConnect() }
            if (shizukuContext?.serviceWrapper != null) {
                try {
                    // 检查是否使用虚拟屏幕
                    val useVirtualDisplay = AppSettings.isBackgroundRunEnabled(context)
                            && VirtualDisplayManager.isCreated
                    val displayId = if (useVirtualDisplay) VirtualDisplayManager.displayId else -1

                    if (useVirtualDisplay) {

                        Log.d(TAG, "使用虚拟屏幕 (Display ID: $displayId) 启动应用: $packageName")
                    } else {
                        Log.d(TAG, "使用 Shizuku am start 启动应用: $packageName")
                    }

                    // 构建 am start 命令
                    val displayParam = if (useVirtualDisplay && displayId != -1) {
                        "--display $displayId "
                    } else {
                        ""
                    }

                    val command = if (componentName != null) {
                        "am start ${displayParam}-n ${componentName.flattenToString()}"
                    } else {
                        "am start ${displayParam}-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName"
                    }

                    Log.d(TAG, "执行命令: $command")
                    val result = shizukuContext.execCommand(command)
                    if (result.ok) {
                        Log.i(TAG, "Shizuku am start 启动成功: $packageName" +
                            if (useVirtualDisplay) " (虚拟屏幕)" else "")

                        // 如果使用虚拟屏幕，自动开启刷新（通过全局帧监听器通知 UI）
                        if (useVirtualDisplay) {
                            VirtualDisplayManager.startAutoRefresh(100L)
                            Log.d(TAG, "已开启虚拟屏幕自动刷新")
                        }

                        return true
                    } else {
                        Log.w(TAG, "Shizuku am start 启动失败: ${result.error}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Shizuku am start 异常: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Shizuku serviceWrapper 不可用，跳过 am start")
            }

            // 策略2: 使用无障碍服务启动（备选）
            val accessibilityService = MyAccessibilityService.instance
            if (accessibilityService != null) {
                try {
                    Log.d(TAG, "使用无障碍服务启动应用: $packageName")

                    val targetIntent = launchIntent.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }

                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        accessibilityService.startActivity(targetIntent)
                    } else {
                        val latch = CountDownLatch(1)
                        var success = false

                        Handler(Looper.getMainLooper()).post {
                            try {
                                accessibilityService.startActivity(targetIntent)
                                success = true
                            } catch (e: Exception) {
                                Log.e(TAG, "无障碍服务启动失败: ${e.message}", e)
                            } finally {
                                latch.countDown()
                            }
                        }

                        if (!latch.await(2, TimeUnit.SECONDS)) {
                            Log.e(TAG, "无障碍服务启动超时")
                            return false
                        }

                        if (!success) return false
                    }

                    Thread.sleep(300)
                    Log.i(TAG, "无障碍服务启动完成: $packageName")
                    return true

                } catch (e: Exception) {
                    Log.e(TAG, "无障碍服务启动异常: ${e.message}", e)
                }
            }

            Log.e(TAG, """
                ========== 启动失败 ==========
                应用包名: $packageName
                Shizuku: ${if (shizukuContext != null) "✓ 可用" else "✗ 不可用"}
                无障碍服务: ${if (accessibilityService != null) "✓ 已启用" else "✗ 未启用"}

                建议：
                1. 启用 Shizuku（推荐，最可靠）
                2. 启用无障碍服务（备选）
                ==============================
            """.trimIndent())
            false

        } catch (e: Exception) {
            Log.e(TAG, "打开应用异常: ${e.message}", e)
            false
        }
    }

    /**
     * 通过应用名称打开应用
     */
    fun openByName(appName: String): Boolean {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)

            for (app in apps) {
                val label = pm.getApplicationLabel(app).toString()
                if (label.equals(appName, ignoreCase = true)) {
                    return openByPackage(app.packageName)
                }
            }

            Log.e(TAG, "找不到应用: $appName")
            false
        } catch (e: Exception) {
            Log.e(TAG, "查找应用失败: ${e.message}", e)
            false
        }
    }
}