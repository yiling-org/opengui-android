package top.yling.ozx.guiagent.util

import android.content.Context
import android.util.DisplayMetrics

/**
 * 图片压缩配置管理类
 * 管理 JPEG 质量和缩放比例的配置
 */
object ImageCompressionConfig {

    private const val PREFS_NAME = "image_compression_prefs"
    private const val KEY_JPEG_QUALITY = "jpeg_quality"
    private const val KEY_SCALE_FACTOR = "scale_factor"
    private const val KEY_SCALE_FACTOR_SET = "scale_factor_set" // 标记用户是否手动设置过

    // 默认值
    const val DEFAULT_JPEG_QUALITY = 85
    // 基于屏幕分辨率的默认缩放比例
    const val DEFAULT_SCALE_FACTOR_HIGH_RES = 0.4f  // 高分辨率屏幕 (>1024*1024) 使用40%
    const val DEFAULT_SCALE_FACTOR_LOW_RES = 0.7f   // 低分辨率屏幕 (<=1024*1024) 使用70%

    // 分辨率阈值
    private const val RESOLUTION_THRESHOLD = 1024 * 1024

    // 范围限制
    const val MIN_JPEG_QUALITY = 70
    const val MAX_JPEG_QUALITY = 100
    const val MIN_SCALE_FACTOR = 0.3f
    const val MAX_SCALE_FACTOR = 1.0f

    // 兼容性保留：DEFAULT_SCALE_FACTOR 现在返回高分辨率默认值
    const val DEFAULT_SCALE_FACTOR = DEFAULT_SCALE_FACTOR_HIGH_RES

    /**
     * 根据屏幕分辨率获取默认缩放比例
     * - 屏幕分辨率 > 1024*1024：返回 40%
     * - 屏幕分辨率 <= 1024*1024：返回 70%
     */
    fun getDefaultScaleFactorForScreen(context: Context): Float {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val screenPixels = displayMetrics.widthPixels * displayMetrics.heightPixels
        return if (screenPixels > RESOLUTION_THRESHOLD) {
            DEFAULT_SCALE_FACTOR_HIGH_RES
        } else {
            DEFAULT_SCALE_FACTOR_LOW_RES
        }
    }

    /**
     * 获取 JPEG 压缩质量 (0-100)
     */
    fun getJpegQuality(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_JPEG_QUALITY, DEFAULT_JPEG_QUALITY)
    }

    /**
     * 设置 JPEG 压缩质量
     */
    fun setJpegQuality(context: Context, quality: Int) {
        val validQuality = quality.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_JPEG_QUALITY, validQuality).apply()
    }

    /**
     * 获取缩放比例 (0.3-1.0)
     * 如果用户没有手动设置过，则根据屏幕分辨率返回动态默认值
     */
    fun getScaleFactor(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasUserSet = prefs.getBoolean(KEY_SCALE_FACTOR_SET, false)

        return if (hasUserSet) {
            // 用户手动设置过，返回保存的值
            prefs.getFloat(KEY_SCALE_FACTOR, getDefaultScaleFactorForScreen(context))
        } else {
            // 用户没有手动设置过，根据屏幕分辨率返回动态默认值
            getDefaultScaleFactorForScreen(context)
        }
    }

    /**
     * 设置缩放比例
     */
    fun setScaleFactor(context: Context, scale: Float) {
        val validScale = scale.coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_SCALE_FACTOR, validScale)
            .putBoolean(KEY_SCALE_FACTOR_SET, true) // 标记用户已手动设置
            .apply()
    }

    /**
     * 重置为默认值（根据屏幕分辨率动态设置）
     */
    fun resetToDefaults(context: Context) {
        val defaultScaleFactor = getDefaultScaleFactorForScreen(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_JPEG_QUALITY, DEFAULT_JPEG_QUALITY)
            .putFloat(KEY_SCALE_FACTOR, defaultScaleFactor)
            .putBoolean(KEY_SCALE_FACTOR_SET, false) // 重置为"未手动设置"状态
            .apply()
    }
}
