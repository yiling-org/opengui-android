package top.yling.ozx.guiagent.websocket.handler

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import top.yling.ozx.guiagent.util.ImageCompressionConfig
import top.yling.ozx.guiagent.websocket.CommandResult
import java.io.ByteArrayOutputStream

/**
 * 截图处理器
 */
class ScreenshotHandler : ActionHandler {
    companion object {
        private const val TAG = "ScreenshotHandler"
    }

    override val actionName = "screenshot"

    override fun handle(context: ActionContext, callback: (CommandResult) -> Unit) {
        val service = context.service ?: run {
            callback(CommandResult(false, "无障碍服务未启用"))
            return
        }
        service.takeScreenshot(
            callback = { success ->
                if (!success) {
                    callback(CommandResult(false, "截图失败"))
                }
            },
            bitmapCallback = { bitmap ->
                if (bitmap != null) {
                    // 先获取尺寸信息，因为 recycle 后无法访问
                    val width = bitmap.width
                    val height = bitmap.height
                    val base64Image = bitmapToBase64(context, bitmap)
                    // 回收原始 Bitmap，防止内存泄漏
                    bitmap.recycle()
                    callback(CommandResult(true, "截图成功", mapOf(
                        "image" to base64Image,
                        "width" to width,
                        "height" to height
                    )))
                }
            }
        )
    }

    /**
     * 将 Bitmap 转换为 Base64 字符串（带缩放和压缩优化）
     */
    private fun bitmapToBase64(context: ActionContext, bitmap: Bitmap): String {
        val quality = ImageCompressionConfig.getJpegQuality(context.applicationContext)
        val scaleFactor = ImageCompressionConfig.getScaleFactor(context.applicationContext)
        val scaledBitmap = scaleBitmap(bitmap, scaleFactor)

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        val byteArray = outputStream.toByteArray()
        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)

        Log.d(TAG, "图片压缩: 原始${bitmap.width}x${bitmap.height} -> " +
                "缩放后${(bitmap.width * scaleFactor).toInt()}x${(bitmap.height * scaleFactor).toInt()}, " +
                "JPEG质量=$quality, JPEG=${byteArray.size/1024}KB, Base64=${base64.length/1024}KB")

        return base64
    }

    private fun scaleBitmap(bitmap: Bitmap, scale: Float): Bitmap {
        if (scale >= 1.0f) return bitmap

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
