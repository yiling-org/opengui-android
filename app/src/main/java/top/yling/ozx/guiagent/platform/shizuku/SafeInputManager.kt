package top.yling.ozx.guiagent.shizuku

import android.content.Context
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties
import androidx.annotation.WorkerThread
import top.yling.ozx.guiagent.util.AndroidTarget
import top.yling.ozx.guiagent.util.checkExistClass
import kotlin.math.floor

/**
 * 安全的 InputManager 封装
 * 通过 Shizuku 注入输入事件
 * 
 * 参考 GKD: li.songe.gkd.shizuku.InputManager
 */
class SafeInputManager private constructor(private val inputManager: Any) {
    companion object {
        private const val TAG = "SafeInputManager"
        private const val DEFAULT_DEVICE_ID = 0
        private const val DEFAULT_SIZE = 1.0f
        private const val DEFAULT_META_STATE = 0
        private const val DEFAULT_PRECISION_X = 1.0f
        private const val DEFAULT_PRECISION_Y = 1.0f
        private const val DEFAULT_EDGE_FLAGS = 0
        private const val DEFAULT_BUTTON_STATE = 0
        private const val DEFAULT_FLAGS = 0
        private const val SECOND_IN_MILLISECONDS = 1000L
        private const val SWIPE_EVENT_HZ_DEFAULT = 120

        // 注入模式常量
        private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH = 2

        val isAvailable: Boolean
            get() = checkExistClass("android.hardware.input.IInputManager")

        fun newBinder(): SafeInputManager? {
            return getStubService(
                Context.INPUT_SERVICE,
                isAvailable,
            )?.let { binder ->
                try {
                    val iInputManagerClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                    val asInterface = iInputManagerClass.getMethod("asInterface", android.os.IBinder::class.java)
                    val inputManager = asInterface.invoke(null, binder)
                    inputManager?.let { SafeInputManager(it) }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    // 缓存反射方法
    private val injectInputEventMethod by lazy {
        try {
            inputManager.javaClass.getMethod("injectInputEvent", InputEvent::class.java, Int::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 注入输入事件
     */
    private fun injectInputEvent(event: InputEvent, mode: Int): Boolean {
        return try {
            injectInputEventMethod?.invoke(inputManager, event, mode) as? Boolean ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 执行点击
     */
    @WorkerThread
    fun tap(x: Float, y: Float, duration: Long = 0, displayId: Int = -1) {
        if (duration > 0) {
            runSwipe(x, y, x, y, duration, displayId)
        } else {
            runTap(x, y, displayId)
        }
    }

    /**
     * 执行按键
     */
    fun key(keyCode: Int) {
        runKeyEvent(keyCode)
    }

    /**
     * 执行点击
     */
    private fun runTap(x: Float, y: Float, displayId: Int = -1) {
        sendTap(InputDevice.SOURCE_TOUCHSCREEN, x, y, displayId)
    }

    /**
     * 执行滑动
     */
    private fun runSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long, displayId: Int = -1) {
        sendSwipe(
            InputDevice.SOURCE_TOUCHSCREEN,
            x1, y1, x2, y2,
            duration, displayId, false
        )
    }

    private fun sendTap(inputSource: Int, x: Float, y: Float, displayId: Int) {
        val now = SystemClock.uptimeMillis()
        injectMotionEvent(inputSource, MotionEvent.ACTION_DOWN, now, now, x, y, 1.0f, displayId)
        injectMotionEvent(inputSource, MotionEvent.ACTION_UP, now, now, x, y, 0.0f, displayId)
    }

    private fun sendSwipe(
        inputSource: Int,
        x1: Float, y1: Float,
        x2: Float, y2: Float,
        duration: Long,
        displayId: Int,
        isDragDrop: Boolean,
    ) {
        val down = SystemClock.uptimeMillis()
        injectMotionEvent(inputSource, MotionEvent.ACTION_DOWN, down, down, x1, y1, 1.0f, displayId)
        
        if (isDragDrop) {
            Thread.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong())
        }
        
        var now = SystemClock.uptimeMillis()
        val endTime = down + duration
        val swipeEventPeriodMillis: Float = SECOND_IN_MILLISECONDS.toFloat() / SWIPE_EVENT_HZ_DEFAULT
        var injected = 1
        
        while (now < endTime) {
            var elapsedTime = now - down
            val errorMillis = floor((injected * swipeEventPeriodMillis - elapsedTime).toDouble()).toLong()
            if (errorMillis > 0) {
                if (errorMillis > endTime - now) {
                    Thread.sleep(endTime - now)
                    break
                }
                Thread.sleep(errorMillis)
            }
            now = SystemClock.uptimeMillis()
            elapsedTime = now - down
            val alpha = elapsedTime.toFloat() / duration
            injectMotionEvent(
                inputSource, MotionEvent.ACTION_MOVE, down, now,
                lerp(x1, x2, alpha), lerp(y1, y2, alpha), 1.0f, displayId
            )
            injected++
            now = SystemClock.uptimeMillis()
        }
        
        injectMotionEvent(inputSource, MotionEvent.ACTION_UP, down, now, x2, y2, 0.0f, displayId)
    }

    private fun injectMotionEvent(
        inputSource: Int,
        action: Int,
        downTime: Long,
        mWhen: Long,
        x: Float,
        y: Float,
        pressure: Float,
        displayId: Int
    ) {
        val event = MotionEvent.obtain(
            downTime, mWhen, action, x, y, pressure, DEFAULT_SIZE,
            DEFAULT_META_STATE, DEFAULT_PRECISION_X, DEFAULT_PRECISION_Y,
            getInputDeviceId(inputSource), DEFAULT_EDGE_FLAGS
        )
        event.setSource(inputSource)
        injectInputEvent(event, INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH)
        event.recycle()
    }

    private fun getInputDeviceId(inputSource: Int): Int {
        val devIds = InputDevice.getDeviceIds()
        for (devId in devIds) {
            val inputDev = InputDevice.getDevice(devId)!!
            if (inputDev.supportsSource(inputSource)) {
                return devId
            }
        }
        return DEFAULT_DEVICE_ID
    }

    private fun lerp(a: Float, b: Float, alpha: Float): Float {
        return (b - a) * alpha + a
    }

    private fun runKeyEvent(keyCode: Int) {
        sendKeyEvent(keyCode)
    }

    private fun sendKeyEvent(keyCode: Int) {
        val inputSource = InputDevice.SOURCE_UNKNOWN
        val now = SystemClock.uptimeMillis()
        
        val downEvent = KeyEvent(
            now, now, KeyEvent.ACTION_DOWN, keyCode, 0,
            0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
            inputSource
        )
        injectInputEvent(downEvent, INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH)
        
        val upEvent = KeyEvent.changeTimeRepeat(downEvent, SystemClock.uptimeMillis(), 0)
        injectInputEvent(KeyEvent.changeAction(upEvent, KeyEvent.ACTION_UP), INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH)
    }
}

