package top.yling.ozx.guiagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log

/**
 * 虚拟屏幕手势辅助类
 * 使用无障碍服务API在指定的虚拟屏幕上执行手势操作
 *
 * 注意：需要 Android 11 (API 30) 及以上版本才支持在指定 displayId 上执行手势
 */
object VirtualDisplayGestureHelper {

    private const val TAG = "VirtualDisplayGesture"

    /**
     * 检查是否支持在虚拟屏幕上执行手势
     * @return true 如果支持，false 否则
     */
    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * 检查无障碍服务是否已启用
     * @return true 如果已启用，false 否则
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        return MyAccessibilityService.isServiceEnabled()
    }

    /**
     * 在指定的虚拟屏幕上执行滑动操作
     *
     * @param displayId 目标屏幕ID
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 结束X坐标
     * @param endY 结束Y坐标
     * @param duration 滑动持续时间（毫秒）
     * @param callback 操作完成回调 (success: Boolean)
     * @return true 如果手势已提交，false 如果失败（不支持或服务未启用）
     */
    fun swipe(
        displayId: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        Log.d(TAG, "swipe: displayId=$displayId, ($startX, $startY) -> ($endX, $endY), duration=$duration")

        // 检查 API 版本
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.e(TAG, "不支持：需要 Android 11 (API 30) 及以上版本")
            callback?.invoke(false)
            return false
        }

        // 获取无障碍服务实例
        val service = MyAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "无障碍服务未启用")
            callback?.invoke(false)
            return false
        }

        return performSwipeGesture(service, displayId, startX, startY, endX, endY, duration, callback)
    }

    /**
     * 在指定的虚拟屏幕上执行上滑操作
     *
     * @param displayId 目标屏幕ID
     * @param centerX 滑动中心X坐标（默认屏幕中间位置）
     * @param startY 起始Y坐标
     * @param distance 滑动距离
     * @param duration 滑动持续时间（毫秒）
     * @param callback 操作完成回调
     * @return true 如果手势已提交
     */
    fun swipeUp(
        displayId: Int,
        centerX: Float = 250f,
        startY: Float = 400f,
        distance: Float = 300f,
        duration: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        return swipe(
            displayId = displayId,
            startX = centerX,
            startY = startY,
            endX = centerX,
            endY = startY - distance,
            duration = duration,
            callback = callback
        )
    }

    /**
     * 在指定的虚拟屏幕上执行下滑操作
     */
    fun swipeDown(
        displayId: Int,
        centerX: Float = 250f,
        startY: Float = 400f,
        distance: Float = 300f,
        duration: Long = 300,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        return swipe(
            displayId = displayId,
            startX = centerX,
            startY = startY,
            endX = centerX,
            endY = startY + distance,
            duration = duration,
            callback = callback
        )
    }

    /**
     * 在指定的虚拟屏幕上执行点击操作
     *
     * @param displayId 目标屏幕ID
     * @param x X坐标
     * @param y Y坐标
     * @param callback 操作完成回调
     * @return true 如果手势已提交
     */
    fun click(
        displayId: Int,
        x: Float,
        y: Float,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        Log.d(TAG, "click: displayId=$displayId, ($x, $y)")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.e(TAG, "不支持：需要 Android 11 (API 30) 及以上版本")
            callback?.invoke(false)
            return false
        }

        val service = MyAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "无障碍服务未启用")
            callback?.invoke(false)
            return false
        }

        return performClickGesture(service, displayId, x, y, callback)
    }

    /**
     * 在指定的虚拟屏幕上执行长按操作
     *
     * @param displayId 目标屏幕ID
     * @param x X坐标
     * @param y Y坐标
     * @param duration 长按持续时间（毫秒）
     * @param callback 操作完成回调
     * @return true 如果手势已提交
     */
    fun longPress(
        displayId: Int,
        x: Float,
        y: Float,
        duration: Long = 1000,
        callback: ((Boolean) -> Unit)? = null
    ): Boolean {
        Log.d(TAG, "longPress: displayId=$displayId, ($x, $y), duration=$duration")

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.e(TAG, "不支持：需要 Android 11 (API 30) 及以上版本")
            callback?.invoke(false)
            return false
        }

        val service = MyAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "无障碍服务未启用")
            callback?.invoke(false)
            return false
        }

        return performLongPressGesture(service, displayId, x, y, duration, callback)
    }

    // ==================== 内部实现 ====================

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun performSwipeGesture(
        service: AccessibilityService,
        displayId: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long,
        callback: ((Boolean) -> Unit)?
    ): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "滑动手势完成 (displayId=$displayId)")
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "滑动手势被取消 (displayId=$displayId)")
                    callback?.invoke(false)
                }
            },
            null
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun performClickGesture(
        service: AccessibilityService,
        displayId: Int,
        x: Float,
        y: Float,
        callback: ((Boolean) -> Unit)?
    ): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "点击手势完成 (displayId=$displayId)")
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "点击手势被取消 (displayId=$displayId)")
                    callback?.invoke(false)
                }
            },
            null
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun performLongPressGesture(
        service: AccessibilityService,
        displayId: Int,
        x: Float,
        y: Float,
        duration: Long,
        callback: ((Boolean) -> Unit)?
    ): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        return service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "长按手势完成 (displayId=$displayId)")
                    callback?.invoke(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "长按手势被取消 (displayId=$displayId)")
                    callback?.invoke(false)
                }
            },
            null
        )
    }
}
