package top.yling.ozx.guiagent.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.UUID

/**
 * 设备工具类
 */
object DeviceUtils {

    private const val PREF_NAME = "device_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    /**
     * 获取屏幕真实尺寸（像素）
     * @return Pair<width, height>
     */
    fun getScreenSize(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }

    /**
     * 获取设备唯一ID
     * 优先使用 ANDROID_ID，如果获取失败则生成 UUID 并持久化存储
     */
    @SuppressLint("HardwareIds")
    fun getDeviceId(context: Context): String {
        // 1. 尝试获取 ANDROID_ID
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // ANDROID_ID 有效则直接返回
        if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
            return androidId
        }

        // 2. 如果 ANDROID_ID 无效，使用本地存储的 UUID
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId.isNullOrEmpty()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        return deviceId
    }
}
