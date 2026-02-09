package top.yling.ozx.guiagent.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import top.yling.ozx.guiagent.websocket.WebSocketService

/**
 * 应用变化广播接收器
 * 监听应用安装、卸载、更新事件，并触发同步到后端
 */
class AppChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AppChangeReceiver"

        /**
         * 创建 IntentFilter 用于动态注册
         */
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart
        if (packageName.isNullOrEmpty()) {
            Log.w(TAG, "Received package event but package name is null")
            return
        }

        // 忽略自己的包变化
        if (packageName == context.packageName) {
            return
        }

        val action = intent.action
        Log.i(TAG, "Received package event: action=$action, package=$packageName")

        when (action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    // 新安装的应用
                    handleAppInstalled(context, packageName)
                }
                // 如果 isReplacing=true，会收到 ACTION_PACKAGE_REPLACED 事件，在那里处理
            }

            Intent.ACTION_PACKAGE_REPLACED -> {
                // 应用更新
                handleAppUpdated(context, packageName)
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing) {
                    // 真正的卸载
                    handleAppUninstalled(context, packageName)
                }
                // 如果 isReplacing=true，说明只是更新过程中的临时移除，不需要处理
            }
        }
    }

    /**
     * 处理应用安装事件
     */
    private fun handleAppInstalled(context: Context, packageName: String) {
        Log.i(TAG, "App installed: $packageName")

        // 检查是否是第三方应用
        val collector = InstalledAppCollector(context)
        if (!collector.isThirdPartyApp(packageName)) {
            Log.d(TAG, "Ignoring system app installation: $packageName")
            return
        }

        // 通知 WebSocketService 同步
        WebSocketService.instance?.sendAppInstalled(packageName)
    }

    /**
     * 处理应用更新事件
     */
    private fun handleAppUpdated(context: Context, packageName: String) {
        Log.i(TAG, "App updated: $packageName")

        // 检查是否是第三方应用
        val collector = InstalledAppCollector(context)
        if (!collector.isThirdPartyApp(packageName)) {
            Log.d(TAG, "Ignoring system app update: $packageName")
            return
        }

        // 通知 WebSocketService 同步
        WebSocketService.instance?.sendAppUpdated(packageName)
    }

    /**
     * 处理应用卸载事件
     */
    private fun handleAppUninstalled(context: Context, packageName: String) {
        Log.i(TAG, "App uninstalled: $packageName")

        // 卸载事件不需要检查是否是第三方应用，因为应用已经不存在了
        // 直接通知后端删除记录

        // 通知 WebSocketService 同步
        WebSocketService.instance?.sendAppUninstalled(packageName)
    }
}
