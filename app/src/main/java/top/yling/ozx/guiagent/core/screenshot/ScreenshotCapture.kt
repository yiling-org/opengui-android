package top.yling.ozx.guiagent.core.screenshot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import top.yling.ozx.guiagent.AgentOverlayService
import top.yling.ozx.guiagent.util.AppSettings
import top.yling.ozx.guiagent.util.VirtualDisplayManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 截图捕获器
 * 负责执行截图操作，支持新API (Android 11+) 和旧API (Android 9+)
 * 支持虚拟屏幕截图（后台运行模式）
 *
 * @author shanwb
 */
class ScreenshotCapture(
    private val service: AccessibilityService
) {

    companion object {
        private const val TAG = "ScreenshotCapture"

        // UI隐藏延迟（毫秒），确保UI完全隐藏后再截图
        private const val UI_HIDE_DELAY = 100L
        // UI恢复延迟（毫秒），确保截图完成后再恢复UI
        private const val UI_RESTORE_DELAY = 50L

        /**
         * 截图模式开关
         * true: 使用新的 takeScreenshot API (Android 11+)，可获取 Bitmap
         * false: 使用 GLOBAL_ACTION_TAKE_SCREENSHOT (Android 9+)
         */
        var useNewScreenshotApi: Boolean = true
    }

    private val screenshotExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // 截图回调，用于返回 Bitmap
    var onScreenshotTaken: ((Bitmap?) -> Unit)? = null

    /**
     * 截图
     *
     * @param callback 截图结果回调 (success: Boolean)
     * @param bitmapCallback 可选，截图 Bitmap 回调（仅新 API 模式下有效）
     * @param hideOverlays 是否在截图前隐藏覆盖层UI（默认true）
     */
    fun takeScreenshot(
        callback: ((Boolean) -> Unit)? = null,
        bitmapCallback: ((Bitmap?) -> Unit)? = null,
        hideOverlays: Boolean = true
    ) {
        // 隐藏所有覆盖层UI
        if (hideOverlays) {
            hideOverlayUIs()
        }

        // 延迟执行截图，确保UI已隐藏
        mainHandler.postDelayed({
            performScreenshot(callback, bitmapCallback, hideOverlays)
        }, if (hideOverlays) UI_HIDE_DELAY else 0)
    }

    /**
     * 隐藏所有覆盖层UI
     */
    private fun hideOverlayUIs() {
        Log.d(TAG, "隐藏覆盖层UI用于截图")
        AgentOverlayService.instance?.hideAllForScreenshot()
    }

    /**
     * 恢复显示所有覆盖层UI
     */
    private fun showOverlayUIs() {
        Log.d(TAG, "恢复显示覆盖层UI")
        AgentOverlayService.instance?.showAllAfterScreenshot()
    }

    /**
     * 执行实际的截图操作
     */
    private fun performScreenshot(
        callback: ((Boolean) -> Unit)?,
        bitmapCallback: ((Bitmap?) -> Unit)?,
        restoreOverlays: Boolean
    ) {
        val restoreCallback: (Boolean) -> Unit = { success ->
            // 截图完成后恢复UI
            if (restoreOverlays) {
                mainHandler.postDelayed({
                    showOverlayUIs()
                }, UI_RESTORE_DELAY)
            }
            callback?.invoke(success)
        }

        val restoreBitmapCallback: (Bitmap?) -> Unit = { bitmap ->
            // 截图完成后恢复UI
            if (restoreOverlays) {
                mainHandler.postDelayed({
                    showOverlayUIs()
                }, UI_RESTORE_DELAY)
            }
            bitmapCallback?.invoke(bitmap)
        }

        if (useNewScreenshotApi && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 使用新的 takeScreenshot API (Android 11+)
            takeScreenshotNew(restoreCallback, restoreBitmapCallback)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 使用 GLOBAL_ACTION 方式 (Android 9+)
            takeScreenshotLegacy(restoreCallback)
        } else {
            Log.e(TAG, "截图需要 Android 9 及以上版本")
            if (restoreOverlays) {
                showOverlayUIs()
            }
            callback?.invoke(false)
        }
    }

    /**
     * 使用新的 takeScreenshot API 截图（Android 11+）
     * 可以获取到截图的 Bitmap
     */
    @android.annotation.TargetApi(Build.VERSION_CODES.R)
    private fun takeScreenshotNew(
        callback: ((Boolean) -> Unit)?,
        bitmapCallback: ((Bitmap?) -> Unit)?
    ) {
        // 检查是否使用虚拟屏幕截图
        val useVirtualDisplay = AppSettings.isBackgroundRunEnabled(service)
                && VirtualDisplayManager.isCreated

        if (useVirtualDisplay) {
            Log.d(TAG, "截图 (虚拟屏幕, Display ID: ${VirtualDisplayManager.displayId})")
            try {
                val bitmap = VirtualDisplayManager.captureScreenshot()
                if (bitmap != null) {
                    Log.d(TAG, "虚拟屏幕截图成功")
                    bitmapCallback?.invoke(bitmap)
                    onScreenshotTaken?.invoke(bitmap)
                    callback?.invoke(true)
                } else {
                    Log.e(TAG, "虚拟屏幕截图失败: bitmap 为空")
                    bitmapCallback?.invoke(null)
                    callback?.invoke(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "虚拟屏幕截图异常: ${e.message}", e)
                bitmapCallback?.invoke(null)
                callback?.invoke(false)
            }
            return
        }

        // 使用系统截图 API
        Log.d(TAG, "截图 (新API)")

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        Log.d(TAG, "截图成功 (新API)")
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        screenshot.hardwareBuffer.close()

                        // 回调 Bitmap
                        bitmapCallback?.invoke(bitmap)
                        onScreenshotTaken?.invoke(bitmap)

                        callback?.invoke(true)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "截图失败 (新API), errorCode: $errorCode")
                        bitmapCallback?.invoke(null)
                        callback?.invoke(false)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "截图异常: ${e.message}", e)
            bitmapCallback?.invoke(null)
            callback?.invoke(false)
        }
    }

    /**
     * 使用 GLOBAL_ACTION 方式截图（Android 9+）
     * 触发系统截图，截图保存到相册
     */
    private fun takeScreenshotLegacy(callback: ((Boolean) -> Unit)?) {
        Log.d(TAG, "截图 (GLOBAL_ACTION)")
        val result = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        callback?.invoke(result)
    }

    /**
     * 释放资源
     * 应在服务销毁时调用
     */
    fun release() {
        screenshotExecutor.shutdown()
        try {
            if (!screenshotExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                screenshotExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            screenshotExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
