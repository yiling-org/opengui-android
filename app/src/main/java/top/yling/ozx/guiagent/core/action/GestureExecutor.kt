package top.yling.ozx.guiagent.core.action

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * 手势执行器
 * 负责执行点击、长按、滑动、拖拽等手势操作
 * 支持 Shizuku 增强和无障碍服务回退
 *
 * @author shanwb
 */
class GestureExecutor(
    private val service: AccessibilityService,
    private val getDefaultDisplayId: () -> Int
) {

    companion object {
        private const val TAG = "GestureExecutor"

        // 点击时长范围（模拟真人操作）
        private const val CLICK_DURATION_MIN = 50L
        private const val CLICK_DURATION_MAX = 180L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * 点击屏幕指定坐标
     * 优先使用 Shizuku，失败时回退到无障碍服务
     *
     * @param x X坐标
     * @param y Y坐标
     * @param displayId 目标显示器ID（用于虚拟屏幕场景）
     * @param callback 操作完成回调
     */
    fun click(
        x: Float,
        y: Float,
        displayId: Int = getDefaultDisplayId(),
        callback: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "点击坐标: ($x, $y), displayId: $displayId")

        // 优先尝试使用 Shizuku
        val shizukuContext = top.yling.ozx.guiagent.shizuku.ShizukuApi.getContextOrNull()
        if (shizukuContext != null) {
            Log.d(TAG, "使用 Shizuku 执行点击")
            executor.execute {
                try {
                    val success = shizukuContext.tap(x, y, displayId = displayId)
                    Log.d(TAG, "Shizuku 点击${if (success) "成功" else "失败"}")
                    mainHandler.post { callback?.invoke(success) }
                } catch (e: Exception) {
                    Log.e(TAG, "Shizuku 点击异常: ${e.message}", e)
                    // Shizuku 失败时回退到无障碍服务
                    mainHandler.post { clickByAccessibility(x, y, displayId, callback) }
                }
            }
            return
        }

        // Shizuku 不可用，使用无障碍服务手势
        Log.d(TAG, "Shizuku 不可用，使用无障碍服务执行点击")
        clickByAccessibility(x, y, displayId, callback)
    }

    /**
     * 使用无障碍服务手势执行点击
     * 点击时长采用随机值模拟真人操作，降低风控检测风险
     */
    private fun clickByAccessibility(
        x: Float,
        y: Float,
        displayId: Int,
        callback: ((Boolean) -> Unit)?
    ) {
        val path = Path().apply {
            moveTo(x, y)
        }

        // 随机点击时长：50~180ms，模拟真人操作
        val clickDuration = (CLICK_DURATION_MIN..CLICK_DURATION_MAX).random()
        Log.d(TAG, "点击时长: ${clickDuration}ms")

        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration))
            .build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "无障碍服务点击完成")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "无障碍服务点击被取消")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 长按屏幕指定坐标
     *
     * @param x X坐标
     * @param y Y坐标
     * @param duration 长按时长（毫秒），默认1000ms
     * @param callback 操作完成回调
     */
    fun longPress(
        x: Float,
        y: Float,
        duration: Long = 1000,
        callback: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "长按坐标: ($x, $y), 时长: ${duration}ms")

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "长按完成")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "长按被取消")
                callback?.invoke(false)
            }
        }, null)

        Thread.sleep(duration)
    }

    /**
     * 滑动操作
     *
     * @param x 起始X坐标
     * @param y 起始Y坐标
     * @param direction 滑动方向: "up", "down", "left", "right"
     * @param distance 滑动距离（像素），默认500
     * @param duration 滑动时长（毫秒），默认300ms
     * @param callback 操作完成回调
     */
    fun scroll(
        x: Float,
        y: Float,
        direction: String,
        distance: Float = 500f,
        duration: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "滑动: 起点($x, $y), 方向: $direction, 距离: $distance")

        val (endX, endY) = when (direction.lowercase()) {
            "up" -> x to (y - distance)
            "down" -> x to (y + distance)
            "left" -> (x - distance) to y
            "right" -> (x + distance) to y
            else -> {
                Log.e(TAG, "无效的滑动方向: $direction")
                callback?.invoke(false)
                return
            }
        }

        val path = Path().apply {
            moveTo(x, y)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "滑动完成")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "滑动被取消")
                callback?.invoke(false)
            }
        }, null)
    }

    /**
     * 拖拽操作
     *
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 结束X坐标
     * @param endY 结束Y坐标
     * @param duration 拖拽时长（毫秒），默认500ms
     * @param callback 操作完成回调
     */
    fun drag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 500,
        callback: ((Boolean) -> Unit)? = null
    ) {
        Log.d(TAG, "拖拽: ($startX, $startY) -> ($endX, $endY)")

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "拖拽完成")
                callback?.invoke(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "拖拽被取消")
                callback?.invoke(false)
            }
        }, null)
    }
}
