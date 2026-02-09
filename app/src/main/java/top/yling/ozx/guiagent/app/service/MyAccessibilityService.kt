package top.yling.ozx.guiagent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import top.yling.ozx.guiagent.core.action.GestureExecutor
import top.yling.ozx.guiagent.core.action.SystemActionExecutor
import top.yling.ozx.guiagent.core.action.TextInputExecutor
import top.yling.ozx.guiagent.core.screenshot.ScreenshotCapture
import top.yling.ozx.guiagent.util.AppSettings
import top.yling.ozx.guiagent.util.VirtualDisplayManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * 标题候选项
 */
data class TitleCandidate(
    val text: String,
    val score: Int,
    val bounds: Rect,
    val viewId: String?,
    val className: String?
)

/**
 * 搜索框信息
 */
data class SearchBoxInfo(
    val viewId: String?,        // 搜索框控件 ID
    val className: String?,     // 搜索框类名
    val hint: String?,          // 提示文本（placeholder）
    val text: String?,          // 当前输入的文本
    val bounds: Rect            // 位置信息
)

/**
 * 无障碍服务，支持模拟点击、滑动等操作
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"

        // 单例实例，供外部调用
        var instance: MyAccessibilityService? = null
            private set

        // 检查服务是否已启用
        fun isServiceEnabled(): Boolean = instance != null

        /**
         * 截图模式开关
         * true: 使用新的 takeScreenshot API (Android 11+)，可获取 Bitmap
         * false: 使用 GLOBAL_ACTION_TAKE_SCREENSHOT (Android 9+)
         */
        var useNewScreenshotApi: Boolean = true
    }

    // 截图回调，用于返回 Bitmap
    var onScreenshotTaken: ((Bitmap?) -> Unit)? = null
        set(value) {
            field = value
            screenshotCapture?.onScreenshotTaken = value
        }

    private val screenshotExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // UI隐藏延迟（毫秒），确保UI完全隐藏后再截图
    private val UI_HIDE_DELAY = 100L
    // UI恢复延迟（毫秒），确保截图完成后再恢复UI
    private val UI_RESTORE_DELAY = 50L

    private val mainHandler = Handler(Looper.getMainLooper())

    // ==================== 组件化执行器 ====================
    // 手势执行器（点击、长按、滑动、拖拽）
    private var gestureExecutor: GestureExecutor? = null
    // 系统操作执行器（Home、返回、最近任务等）
    private var systemActionExecutor: SystemActionExecutor? = null
    // 文本输入执行器
    private var textInputExecutor: TextInputExecutor? = null
    // 截图捕获器
    private var screenshotCapture: ScreenshotCapture? = null

    // 音量键双击检测
    private var lastVolumeDownTime = 0L
    private val DOUBLE_PRESS_INTERVAL = 500L  // 双击间隔（毫秒）

    // 中断回调
    var onInterruptRequested: (() -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // 初始化组件化执行器
        initExecutors()

        Log.i(TAG, "无障碍服务已连接")
    }

    /**
     * 初始化所有组件化执行器
     */
    private fun initExecutors() {
        gestureExecutor = GestureExecutor(this) { getDefaultDisplayId() }
        systemActionExecutor = SystemActionExecutor(this)
        textInputExecutor = TextInputExecutor(this)
        screenshotCapture = ScreenshotCapture(this).also {
            it.onScreenshotTaken = onScreenshotTaken
        }
        Log.d(TAG, "组件化执行器初始化完成")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可以在这里处理无障碍事件
    }

    /**
     * 处理按键事件（用于音量键紧急中断）
     * 注意：需要在无障碍服务配置中启用 flagRequestFilterKeyEvents
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        // 只处理音量下键的按下事件
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()

            if (now - lastVolumeDownTime < DOUBLE_PRESS_INTERVAL) {
                // 检测到双击音量下键
                Log.i(TAG, "检测到双击音量下键，触发紧急中断")

                // 通知AgentOverlayService
                AgentOverlayService.instance?.handleVolumeKeyDown()

                // 触发中断回调
                onInterruptRequested?.invoke()

                // 重置时间，避免连续触发
                lastVolumeDownTime = 0L

                // 返回true表示消费该事件（不传递给系统）
                return true
            }

            lastVolumeDownTime = now
        }

        return super.onKeyEvent(event)
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null

        // 释放组件资源
        releaseExecutors()

        // 关闭截图线程池，防止线程泄漏
        screenshotExecutor.shutdown()
        try {
            if (!screenshotExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                screenshotExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            screenshotExecutor.shutdownNow()
            Thread.currentThread().interrupt()
        }

        Log.i(TAG, "无障碍服务已销毁")
    }

    /**
     * 释放所有组件化执行器资源
     */
    private fun releaseExecutors() {
        screenshotCapture?.release()
        screenshotCapture = null
        gestureExecutor = null
        systemActionExecutor = null
        textInputExecutor = null
        Log.d(TAG, "组件化执行器资源已释放")
    }

    /**
     * 获取默认的 displayId
     * @return 默认显示屏的 ID
     */
    fun getDefaultDisplayId(): Int {
        val wm = getSystemService(WindowManager::class.java)
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val displayId = display.displayId
        Log.d(TAG, "默认 displayId: $displayId")
        return displayId
    }

    /**
     * 点击屏幕指定坐标
     * 优先使用 Shizuku（如果可用），否则回退到无障碍服务手势
     * @param x X坐标
     * @param y Y坐标
     * @param callback 操作完成回调
     */
    fun click(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "点击坐标: ($x, $y)")

        // 如果开启了后台运行且虚拟屏幕已创建，使用虚拟屏幕的 displayId
        val displayId = if (top.yling.ozx.guiagent.util.AppSettings.isBackgroundRunEnabled(this)
            && top.yling.ozx.guiagent.util.VirtualDisplayManager.isCreated) {
            val vDisplayId = top.yling.ozx.guiagent.util.VirtualDisplayManager.displayId
            Log.d(TAG, "使用虚拟屏幕 displayId: $vDisplayId")
            vDisplayId
        } else {
            getDefaultDisplayId()
        }

        // 优先尝试使用 Shizuku
        val shizukuContext = top.yling.ozx.guiagent.shizuku.ShizukuApi.getContextOrNull()
        if (shizukuContext != null) {
            Log.d(TAG, "使用 Shizuku 执行点击")
            // Shizuku tap 需要在工作线程执行
            Executors.newSingleThreadExecutor().execute {
                try {
                    val success = shizukuContext.tap(x, y, displayId = displayId)
                    Log.d(TAG, "Shizuku 点击${if (success) "成功" else "失败"}")
                    mainHandler.post { callback?.invoke(success) }
                } catch (e: Exception) {
                    Log.e(TAG, "Shizuku 点击异常: ${e.message}", e)
                    // Shizuku 失败时回退到无障碍服务
                    mainHandler.post { clickByAccessibility(x, y, callback) }
                }
            }
            return
        }

        // Shizuku 不可用，使用无障碍服务手势
        Log.d(TAG, "Shizuku 不可用，使用无障碍服务执行点击")
        clickByAccessibility(x, y, callback)
    }

    /**
     * 使用无障碍服务手势执行点击
     * 点击时长采用随机值(50~180ms)模拟真人操作，降低风控检测风险
     */
    private fun clickByAccessibility(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {

        val displayId = getDefaultDisplayId()

        val path = Path().apply {
            moveTo(x, y)
        }

        // 随机点击时长：50~180ms，模拟真人操作
        val clickDuration = (50..180).random().toLong()
        Log.d(TAG, "点击时长: ${clickDuration}ms")

        val gesture = GestureDescription.Builder()
            .setDisplayId(displayId)
            .addStroke(GestureDescription.StrokeDescription(path, 0, clickDuration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
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
     * @param x X坐标
     * @param y Y坐标
     * @param duration 长按时长（毫秒），默认1000ms
     * @param callback 操作完成回调
     */
    fun longPress(x: Float, y: Float, duration: Long = 1000, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "长按坐标: ($x, $y), 时长: ${duration}ms")

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
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
     * 在当前焦点位置执行长按操作（不需要指定坐标）
     * @param duration 长按时长（毫秒），默认1000ms
     * @param callback 操作完成回调
     */
    fun longPressAtFocus(duration: Long = 1000, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "在当前焦点位置长按, 时长: ${duration}ms")

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "无法获取当前窗口")
            callback?.invoke(false)
            return
        }

        // 查找当前获得焦点的节点
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            rootNode.recycle()
            Log.e(TAG, "未找到获得焦点的输入框")
            callback?.invoke(false)
            return
        }

        try {
            // 执行长按操作
            val longClickResult = focusedNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            if (longClickResult) {
                Log.d(TAG, "焦点位置长按成功")
                // 等待长按效果生效
                Thread.sleep(duration)
            } else {
                Log.e(TAG, "焦点位置长按失败")
            }
            callback?.invoke(longClickResult)
        } finally {
            focusedNode.recycle()
            rootNode.recycle()
        }
    }

    /**
     * 复制指定文本到剪贴板（不依赖焦点节点）
     * @param text 要复制的文本
     * @return 是否复制成功
     */
    fun copyText(text: String): Boolean {
        Log.d(TAG, "复制指定文本到剪贴板: $text")

        return try {
            val handler = Handler(Looper.getMainLooper())
            val success = AtomicBoolean(false)

            handler.post {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("text", text)
                    clipboard.setPrimaryClip(clip)
                    success.set(true)
                    Log.d(TAG, "已将文本复制到剪贴板")
                } catch (e: Exception) {
                    Log.e(TAG, "复制到剪贴板失败: ${e.message}")
                }
            }

            // 等待剪贴板操作完成
            Thread.sleep(100)

            success.get()
        } catch (e: Exception) {
            Log.e(TAG, "复制文本失败: ${e.message}")
            false
        }
    }

    /**
     * 滑动操作
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

        dispatchGesture(gesture, object : GestureResultCallback() {
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

        dispatchGesture(gesture, object : GestureResultCallback() {
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

    /**
     * 按下Home键
     */
    fun pressHome(): Boolean {
        Log.d(TAG, "按下Home键")
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * 按下返回键
     */
    fun pressBack(): Boolean {
        Log.d(TAG, "按下返回键")
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * 打开最近任务
     */
    fun pressRecents(): Boolean {
        Log.d(TAG, "打开最近任务")
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * 打开通知栏
     */
    fun openNotifications(): Boolean {
        Log.d(TAG, "打开通知栏")
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * 打开快速设置
     */
    fun openQuickSettings(): Boolean {
        Log.d(TAG, "打开快速设置")
        return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * 输入文本（需要当前有可编辑的输入框获得焦点）
     * 首先尝试使用 ACTION_SET_TEXT，如果失败则回退到剪贴板粘贴方式
     *
     * 风控优化：添加随机延迟模拟人类输入节奏，降低被检测风险
     * @param text 要输入的文本
     */
    fun type(text: String): Boolean {
        Log.d(TAG, "输入文本: $text")

        // 风控优化：输入前随机延迟，模拟人类定位输入框的时间（200~500ms）
        val preInputDelay = (200..500).random().toLong()
        Log.d(TAG, "输入前延迟: ${preInputDelay}ms")
        Thread.sleep(preInputDelay)

        val rootNode = rootInActiveWindow ?: run {
            Log.e(TAG, "无法获取当前窗口")
            return false
        }

        // 查找当前获得焦点的节点
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            rootNode.recycle()
            Log.e(TAG, "未找到获得焦点的输入框")
            return false
        }

        // 如果节点是可编辑的，首先尝试 ACTION_SET_TEXT
        if (focusedNode.isEditable) {
            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setTextResult = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            if (setTextResult) {
                Log.d(TAG, "ACTION_SET_TEXT 成功")
                // 风控优化：根据文本长度添加模拟输入时间（每字符 30~80ms）
                val typingDelay = text.length * (30..80).random().toLong()
                Log.d(TAG, "模拟输入延迟: ${typingDelay}ms (${text.length}字符)")
                Thread.sleep(typingDelay)
                focusedNode.recycle()
                rootNode.recycle()
                return true
            }
            Log.w(TAG, "ACTION_SET_TEXT 失败")
        } else {
            Log.w(TAG, "节点不是 isEditable，跳过 ACTION_SET_TEXT")
        }

        // ACTION_SET_TEXT 失败或节点不可编辑，尝试使用剪贴板粘贴方式
        Log.d(TAG, "尝试使用剪贴板粘贴方式")
        val pasteResult = typeViaClipboard(text, focusedNode)

        if (pasteResult) {
            // 风控优化：粘贴成功后也添加模拟延迟
            val typingDelay = text.length * (30..80).random().toLong()
            Log.d(TAG, "粘贴后模拟延迟: ${typingDelay}ms")
            Thread.sleep(typingDelay)
        }

        focusedNode.recycle()
        rootNode.recycle()
        return pasteResult
    }

    /**
     * 通过剪贴板粘贴方式输入文本
     * @param text 要输入的文本
     * @param node 目标输入框节点
     * @return 是否成功
     */
    private fun typeViaClipboard(text: String, node: AccessibilityNodeInfo): Boolean {
        try {
            // 将文本复制到剪贴板（需要在主线程执行）
            val handler = Handler(Looper.getMainLooper())
            var clipboardSet = AtomicBoolean(false);

            handler.post {
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("text", text)
                    clipboard.setPrimaryClip(clip)
                    clipboardSet.set(true)
                    Log.d(TAG, "已将文本复制到剪贴板")
                } catch (e: Exception) {
                    Log.e(TAG, "复制到剪贴板失败: ${e.message}")
                }
            }

            // 等待剪贴板操作完成
            Thread.sleep(100)

            if (clipboardSet.get()) {
                // 同步方式再试一次
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
            }

            // 执行粘贴操作
            val pasteResult = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasteResult) {
                Log.d(TAG, "粘贴成功")
            } else {
                Log.e(TAG, "粘贴失败")
            }
            return pasteResult
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板粘贴方式失败: ${e.message}")
            return false
        }
    }

    /**
     * 截图
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
        // 隐藏AgentOverlayService的UI
        AgentOverlayService.instance?.hideAllForScreenshot()
    }

    /**
     * 恢复显示所有覆盖层UI
     */
    private fun showOverlayUIs() {
        Log.d(TAG, "恢复显示覆盖层UI")
        // 恢复AgentOverlayService的UI
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
        val useVirtualDisplay = AppSettings.isBackgroundRunEnabled(this)
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
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
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
        val result = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        callback?.invoke(result)
    }

    /**
     * 获取当前窗口的唯一标识信息（增强版，包含更多指纹信息）
     * @return Map包含窗口标识信息，如果无法获取则返回null
     */
    fun getCurrentWindowId(): Map<String, Any?>? {
        Log.d(TAG, "获取当前窗口标识（增强版）")

        // 1. 优先从 windows API 获取活动窗口（更准确）
        val activeWindow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            windows?.firstOrNull { it.isActive }
        } else {
            null
        }

        val rootNode = activeWindow?.root ?: rootInActiveWindow ?: run {
            Log.e(TAG, "无法获取当前窗口")
            return null
        }

        try {
            // 2. 基础窗口信息
            val windowId = rootNode.windowId
            val packageName = rootNode.packageName?.toString() ?: "unknown"
            val className = rootNode.className?.toString() ?: "Unknown"

            // 3. 获取窗口标题（API 24+），使用多种 fallback 方式
            val windowTitle: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // 方式1：从 activeWindow 获取
                activeWindow?.title?.toString()
                    // 方式2：从 rootNode.window 获取
                    ?: rootNode.window?.title?.toString()
                    // 方式3：从 windows 列表中按 windowId 查找
                    ?: windows?.firstOrNull { it.id == windowId }?.title?.toString()
            } else {
                null
            }

            // 4. 提取页面特征
            val screenHeight = getScreenHeightPx()
            val topTitleCandidates = findTopTitles(rootNode, screenHeight)
            val topTitleCandidate = topTitleCandidates.firstOrNull()
            val topTitle = topTitleCandidate?.text
            val titleText = findTitleText(rootNode)
            val keyResourceIds = findKeyResourceIds(rootNode)

            // 优先使用 topTitle，如果没有则使用 titleText
            val primaryTitle = topTitle ?: titleText
            val pageFeature = buildPageFeature(primaryTitle, keyResourceIds)

            // 5. 构建多级标识符
            val baseId = "$packageName/$className"
            val stableId = baseId  // 粗粒度标识（页面级）
            val identifier = "$baseId|$pageFeature"  // 精细粒度标识（包含页面特征）

            // 6. 构建窗口唯一标识（兼容旧格式）
            val legacyIdentifier = "$packageName:$className@$windowId"

            // 7. 构建多候选标题列表
            val titleCandidatesList = topTitleCandidates.map { candidate ->
                mapOf(
                    "text" to candidate.text,
                    "score" to candidate.score,
                    "viewId" to candidate.viewId,
                    "className" to candidate.className,
                    "left" to candidate.bounds.left,
                    "top" to candidate.bounds.top
                )
            }

            // 8. 查找搜索框信息
            val searchBoxes = findSearchBoxes(rootNode, screenHeight)
            val searchBoxesList = searchBoxes.map { box ->
                mapOf(
                    "viewId" to box.viewId,
                    "className" to box.className,
                    "hint" to box.hint,
                    "text" to box.text,
                    "left" to box.bounds.left,
                    "top" to box.bounds.top,
                    "width" to box.bounds.width(),
                    "height" to box.bounds.height()
                )
            }
            val primarySearchBox = searchBoxes.firstOrNull()

            // 9. 生成结构指纹和内容指纹，然后生成 pageId（类似 PageInfo）
            val structureFingerprint = generateViewTreeFingerprint(rootNode)
            val contentFingerprintRaw = generateContentFingerprint(rootNode)
            val pageId = generatePageId(className, structureFingerprint, contentFingerprintRaw, windowTitle)
            // 将 pageId 作为 contentFingerprint 的值
            val contentFingerprint = pageId

            Log.d(TAG, "窗口标识: $identifier")
            Log.d(TAG, "  - 包名: $packageName")
            Log.d(TAG, "  - 类名: $className")
            Log.d(TAG, "  - 系统标题: $windowTitle")
            Log.d(TAG, "  - 顶部标题候选: ${topTitleCandidates.map { "${it.text}(${it.score})" }}")
            Log.d(TAG, "  - 旧版标题: $titleText")
            Log.d(TAG, "  - 页面特征: $pageFeature")
            Log.d(TAG, "  - 内容指纹: $contentFingerprint")
            Log.d(TAG, "  - 搜索框: ${searchBoxes.map { "viewId=${it.viewId}, hint=${it.hint}, text=${it.text}" }}")

            return mapOf(
                // 基础信息
                "windowId" to windowId.toString(),
                "packageName" to packageName,
                "className" to className,
                "windowTitle" to windowTitle,

                // 页面特征
                "topTitle" to topTitle,                       // 最佳标题候选
                "topTitleScore" to topTitleCandidate?.score,  // 最佳标题置信度
                "topTitleViewId" to topTitleCandidate?.viewId,// 最佳标题控件ID
                "titleCandidates" to titleCandidatesList,     // 所有标题候选
                "titleText" to titleText,                     // 旧版标题查找方式
                "keyResourceIds" to keyResourceIds,
                "pageFeature" to pageFeature,
                "contentFingerprint" to contentFingerprint,   // 内容指纹（新增）

                // 搜索框信息（新增）
                "hasSearchBox" to searchBoxes.isNotEmpty(),   // 是否有搜索框
                "searchBoxViewId" to primarySearchBox?.viewId,// 主搜索框控件ID
                "searchBoxClassName" to primarySearchBox?.className, // 主搜索框类名
                "searchBoxHint" to primarySearchBox?.hint,    // 主搜索框提示文本
                "searchBoxText" to primarySearchBox?.text,    // 主搜索框当前文本
                "searchBoxes" to searchBoxesList,             // 所有搜索框列表

                // 标识符（多级）
                "identifier" to identifier,           // 精细标识（推荐用于唯一性判断）
                "stableId" to stableId,               // 粗粒度标识（页面级）
                "legacyIdentifier" to legacyIdentifier // 兼容旧格式
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取窗口标识失败: ${e.message}", e)
            return null
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 查找标题文本（通常在顶部前4层）
     */
    private fun findTitleText(root: AccessibilityNodeInfo): String? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var depth = 0
        while (queue.isNotEmpty() && depth < 4) {  // 只找前4层
            val size = queue.size
            for (i in 0 until size) {
                val node = queue.removeFirst()

                // 找像标题的文本（短文本 + 可见）
                val text = node.text?.toString()
                if (!text.isNullOrBlank() &&
                    text.length in 2..30 &&
                    node.isVisibleToUser) {

                    // 清理队列中剩余节点
                    queue.forEach { it.recycle() }
                    queue.clear()

                    // 注意：不要回收 node，因为它可能是 root 本身
                    if (node != root) {
                        node.recycle()
                    }

                    return text
                }

                // 继续遍历子节点
                for (j in 0 until node.childCount) {
                    node.getChild(j)?.let { queue.add(it) }
                }

                // 只有非根节点才回收
                if (node != root) {
                    node.recycle()
                }
            }
            depth++
        }

        // 清理剩余节点
        queue.forEach {
            if (it != root) {
                it.recycle()
            }
        }
        return null
    }

    /**
     * 查找关键控件 resource ID（前10个）
     */
    private fun findKeyResourceIds(root: AccessibilityNodeInfo): List<String> {
        val ids = mutableSetOf<String>()

        fun collect(node: AccessibilityNodeInfo, depth: Int) {
            if (ids.size >= 10 || depth > 5) return

            node.viewIdResourceName?.let { id ->
                if (id.isNotBlank()) {
                    // 只保留 ID 名称部分（去掉包名前缀）
                    ids.add(id.substringAfterLast("/"))
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collect(child, depth + 1)
                    // 只有非根节点才回收
                    if (child != root) {
                        child.recycle()
                    }
                }
            }
        }

        collect(root, 0)
        return ids.take(10)
    }

    /**
     * 构建页面特征字符串
     */
    private fun buildPageFeature(titleText: String?, resourceIds: List<String>): String {
        val parts = mutableListOf<String>()

        // 添加标题文本哈希（避免标识符过长）
        titleText?.let {
            parts.add("title:${it.hashCode()}")
        }

        // 添加前3个关键 ID
        if (resourceIds.isNotEmpty()) {
            val topIds = resourceIds.take(3).joinToString(",")
            parts.add("ids:$topIds")
        }

        return if (parts.isEmpty()) {
            "default"
        } else {
            parts.joinToString("|")
        }
    }

    /**
     * 获取当前活动窗口的包名
     * @return 当前活动窗口的包名，如果无法获取则返回null
     */
    fun getCurrentPackageName(): String? {
        val rootNode = rootInActiveWindow ?: return null
        val packageName = rootNode.packageName?.toString()
        rootNode.recycle()
        return packageName
    }

    /**
     * 获取当前活动窗口的Activity名称
     * @return 当前活动窗口的Activity名称，如果无法获取则返回null
     */
    fun getCurrentActivityName(): String? {
        val rootNode = rootInActiveWindow ?: return null
        val className = rootNode.className?.toString()
        rootNode.recycle()
        return className
    }

    /**
     * 获取屏幕高度（像素）
     */
    private fun getScreenHeightPx(): Int {
        val wm = getSystemService(WindowManager::class.java)
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val p = android.graphics.Point()
        @Suppress("DEPRECATION")
        display.getRealSize(p)
        return p.y
    }

    /**
     * 在屏幕顶部区域查找标题文本（更智能的算法）
     * 返回多个候选标题，按分数降序排列
     * @param root 根节点
     * @param screenH 屏幕高度
     * @param topRatio 顶部区域占比（0.0-1.0），默认0.15即屏幕上方15%（更严格）
     * @param maxDepth 最大遍历深度
     * @param maxCandidates 最多返回候选数量
     */
    private fun findTopTitles(
        root: AccessibilityNodeInfo,
        screenH: Int,
        topRatio: Double = 0.35,
        maxDepth: Int = 16,
        maxCandidates: Int = 5
    ): List<TitleCandidate> {
        val topLimit = (screenH * topRatio).toInt()
        val candidates = mutableListOf<TitleCandidate>()

        // 用于追踪父节点是否是搜索框/输入框
        val q: ArrayDeque<Triple<AccessibilityNodeInfo, Int, Boolean>> = ArrayDeque()
        q.add(Triple(root, 0, false))

        // 常见的标题相关 viewId 关键词
        val titleKeywords = setOf("title", "toolbar", "header", "nav_title", "tv_title", "action_bar")
        // 搜索框/输入框相关关键词
        val searchKeywords = setOf("search", "edit", "input", "query", "keyword")
        // 需要排除的文本
        val excludeTexts = setOf("返回", "更多", "取消", "搜索", "确定", "关闭", "完成", "分享", "设置")

        while (q.isNotEmpty()) {
            val (node, depth, parentIsSearch) = q.removeFirst()
            if (depth > maxDepth) {
                if (node != root) node.recycle()
                continue
            }

            val r = Rect()
            node.getBoundsInScreen(r)
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""

            // 判断当前节点是否是搜索框相关
            val isSearchRelated = parentIsSearch ||
                    node.isEditable ||
                    className.contains("EditText", ignoreCase = true) ||
                    searchKeywords.any { viewId.contains(it) }

            // 只看屏幕上方区域
            val inTop = r.top in 0..topLimit || r.centerY() in 0..topLimit
            if (inTop && node.isVisibleToUser) {
                val t = node.text?.toString()?.trim().orEmpty()
                val d = node.contentDescription?.toString()?.trim().orEmpty()
                val cand = when {
                    t.isNotEmpty() -> t
                    d.isNotEmpty() -> d
                    else -> ""
                }

                // 标题文本长度限制（2-20字符，太长的大概率不是标题）
                if (cand.length in 2..20 && cand !in excludeTexts) {
                    var score = 10 // 基础分

                    // === 加分项 ===
                    // 位置越靠上越可能是标题
                    score += max(0, (topLimit - r.top) / 30)

                    // 位置越靠左越可能是标题（标题通常在左侧）
                    if (r.left < 300) score += 8
                    else if (r.left < 500) score += 4

                    // viewId 包含标题关键词
                    if (titleKeywords.any { viewId.contains(it) }) score += 15

                    // TextView 类型加分
                    if (className.contains("TextView", ignoreCase = true)) score += 3

                    // 不可点击的文本更可能是标题
                    if (!node.isClickable) score += 3

                    // 短标题加分（2-8字符的短标题更可能是真正的页面标题）
                    if (cand.length in 2..8) score += 5

                    // === 减分项 ===
                    // 搜索框相关大幅减分
                    if (isSearchRelated) score -= 30

                    // 可编辑节点减分
                    if (node.isEditable) score -= 20

                    // viewId 包含搜索关键词减分
                    if (searchKeywords.any { viewId.contains(it) }) score -= 15

                    // 包含特殊字符的可能是搜索内容而非标题
                    if (cand.contains("...") || cand.length > 15) score -= 5

                    // 只有得分大于0的才作为候选
                    if (score > 0) {
                        candidates.add(
                            TitleCandidate(
                                text = cand,
                                score = score,
                                bounds = Rect(r),
                                viewId = node.viewIdResourceName,
                                className = className
                            )
                        )
                    }
                }
            }

            // 遍历子节点，传递搜索框状态
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    q.add(Triple(child, depth + 1, isSearchRelated))
                }
            }

            if (node != root) node.recycle()
        }

        // 按分数降序排列，返回 top N
        return candidates
            .sortedByDescending { it.score }
            .distinctBy { it.text }  // 去重
            .take(maxCandidates)
    }

    /**
     * 兼容旧方法：返回最佳的一个标题候选
     */
    private fun findTopTitle(
        root: AccessibilityNodeInfo,
        screenH: Int,
        topRatio: Double = 0.15,
        maxDepth: Int = 16
    ): TitleCandidate? {
        return findTopTitles(root, screenH, topRatio, maxDepth, 1).firstOrNull()
    }

    /**
     * 查找页面中的搜索框
     * @param root 根节点
     * @param screenH 屏幕高度
     * @param topRatio 顶部区域占比（搜索框通常在顶部）
     * @param maxDepth 最大遍历深度
     * @return 搜索框信息列表（可能有多个搜索框）
     */
    private fun findSearchBoxes(
        root: AccessibilityNodeInfo,
        screenH: Int,
        topRatio: Double = 0.5,
        maxDepth: Int = 20
    ): List<SearchBoxInfo> {
        val topLimit = (screenH * topRatio).toInt()
        val searchBoxes = mutableListOf<SearchBoxInfo>()

        // 搜索框相关关键词
        val searchKeywords = setOf("search", "edit", "input", "query", "keyword", "搜索")

        val q: ArrayDeque<Pair<AccessibilityNodeInfo, Int>> = ArrayDeque()
        q.add(root to 0)

        while (q.isNotEmpty()) {
            val (node, depth) = q.removeFirst()
            if (depth > maxDepth) {
                if (node != root) node.recycle()
                continue
            }

            val r = Rect()
            node.getBoundsInScreen(r)
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""

            // 判断是否是搜索框
            val isSearchBox = node.isVisibleToUser && (
                    // EditText 类型
                    className.contains("EditText", ignoreCase = true) ||
                    // 可编辑节点
                    node.isEditable ||
                    // viewId 包含搜索关键词
                    searchKeywords.any { viewId.contains(it) }
            )

            // 只关注顶部区域的搜索框
            val inTop = r.top in 0..topLimit || r.centerY() in 0..topLimit

            if (isSearchBox && inTop && r.width() > 100) {
                // 获取搜索框的各种文本
                val text = node.text?.toString()?.trim()
                val hint = node.hintText?.toString()?.trim()  // API 26+
                val contentDesc = node.contentDescription?.toString()?.trim()

                // hint 可能在 contentDescription 中
                val finalHint = hint ?: contentDesc?.takeIf { 
                    it.contains("搜索") || it.contains("search", ignoreCase = true) 
                }

                searchBoxes.add(
                    SearchBoxInfo(
                        viewId = node.viewIdResourceName,
                        className = className,
                        hint = finalHint,
                        text = text,
                        bounds = Rect(r)
                    )
                )

                Log.d(TAG, "发现搜索框: viewId=$viewId, class=$className, hint=$finalHint, text=$text")
            }

            // 遍历子节点
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    q.add(child to (depth + 1))
                }
            }

            if (node != root) node.recycle()
        }

        return searchBoxes
    }

    // ==================== 内容指纹生成 ====================

    /** 关键资源ID关键词，用于识别重要文本节点 */
    private val KEY_ID_KEYWORDS = arrayOf(
        "title", "name", "price", "product", "goods", "item",
        "header", "headline", "subject", "content", "desc",
        "label", "text", "info", "detail", "amount", "count"
    )

    /**
     * 生成页面内容指纹
     *
     * 提取页面中的关键文本内容，用于区分相同布局但不同内容的页面：
     * - 标题栏文本（Toolbar 标题）
     * - 选中的 Tab/导航项文本（支持自定义Tab组件）
     * - 关键资源ID节点的文本（如 title, price, name 等）
     * - 列表首项内容
     * - 页面顶部的关键文本（如商品名称、文章标题等）
     *
     * @param node 根节点
     * @return 内容指纹字符串
     */
    private fun generateContentFingerprint(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val keyTexts = mutableListOf<String>()

        // 1. 提取标题栏文本
        extractToolbarTitle(node, keyTexts)

        // 2. 提取选中的 Tab 文本（增强版，支持自定义Tab）
        extractSelectedTabText(node, keyTexts)

        // 3. 提取关键资源ID节点的文本
        extractKeyIdNodeTexts(node, keyTexts, 0)

        // 4. 提取价格文本（对商品详情页很重要）
        extractPriceTexts(node, keyTexts, 0)

        // 5. 提取列表首项内容
        extractListFirstItemText(node, keyTexts, 0)

        // 6. 提取页面关键文本（增加深度和数量）
        extractTopKeyTexts(node, keyTexts, 0)

        // 7. 提取所有可见的大文本（标题类）
        extractLargeTexts(node, keyTexts, 0)

        // 8. 提取所有contentDescription（对于淘宝等电商App很重要）
        extractAllContentDescriptions(node, keyTexts, 0)

        // 9. 更激进地提取所有可见文本（用于详情页）
        if (keyTexts.size < 6) {
            extractAllVisibleTexts(node, keyTexts, 0)
        }

        if (keyTexts.isEmpty()) {
            return ""
        }

        // 合并关键文本，限制长度（增加到12个以提高区分度）
        val sb = StringBuilder()
        for (i in 0 until minOf(keyTexts.size, 12)) {
            if (i > 0) sb.append("|")
            var text = keyTexts[i]
            // 限制单个文本长度
            if (text.length > 50) {
                text = text.substring(0, 50)
            }
            sb.append(text)
        }
        return sb.toString()
    }

    /**
     * 提取 Toolbar 标题文本
     */
    private fun extractToolbarTitle(node: AccessibilityNodeInfo, keyTexts: MutableList<String>) {
        val className = node.className?.toString() ?: ""

        // 查找 Toolbar 或 ActionBar
        if (className.contains("Toolbar") || className.contains("ActionBar")) {
            // 遍历子节点找标题文本（深度遍历）
            extractToolbarTitleFromChildren(node, keyTexts, 0)
            return
        }

        // 递归查找
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractToolbarTitle(child, keyTexts)
                child.recycle()
                if (keyTexts.isNotEmpty()) return
            }
        }
    }

    /**
     * 从Toolbar子节点中提取标题
     */
    private fun extractToolbarTitleFromChildren(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 3) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length < 50) {
            if (isValidKeyText(text) && !containsTextPrefix(keyTexts, text, "T:")) {
                keyTexts.add("T:$text")
                return
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractToolbarTitleFromChildren(child, keyTexts, depth + 1)
                child.recycle()
                if (keyTexts.isNotEmpty()) return
            }
        }
    }

    /**
     * 提取选中的 Tab 文本（增强版）
     * 支持：TabLayout、BottomNavigationView、自定义Tab组件
     */
    private fun extractSelectedTabText(node: AccessibilityNodeInfo, keyTexts: MutableList<String>) {
        val className = node.className?.toString() ?: ""

        // 1. 标准 TabLayout 或 BottomNavigationView
        if (className.contains("TabLayout") || className.contains("BottomNavigation") ||
            className.contains("NavigationBar") || className.contains("TabBar")) {
            extractSelectedFromContainer(node, keyTexts, "TAB")
            return
        }

        // 2. 检查是否是自定义Tab容器（水平LinearLayout包含多个可点击子项）
        if ((className.contains("LinearLayout") || className.contains("HorizontalScrollView") ||
                className.contains("RadioGroup")) && hasSelectedChild(node)) {
            extractSelectedFromContainer(node, keyTexts, "TAB")
        }

        // 递归查找
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractSelectedTabText(child, keyTexts)
                child.recycle()
            }
        }
    }

    /**
     * 检查容器是否有选中的子项
     */
    private fun hasSelectedChild(node: AccessibilityNodeInfo): Boolean {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val selected = child.isSelected || child.isChecked ||
                        (child.isFocused && child.isClickable)
                child.recycle()
                if (selected) return true
            }
        }
        return false
    }

    /**
     * 从容器中提取选中项的文本
     */
    private fun extractSelectedFromContainer(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, prefix: String) {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                // 检查多种选中状态
                val isSelected = child.isSelected || child.isChecked

                if (isSelected) {
                    val tabText = getNodeText(child)
                    if (!tabText.isNullOrEmpty() && isValidKeyText(tabText)) {
                        if (!containsTextPrefix(keyTexts, tabText, "$prefix:")) {
                            keyTexts.add("$prefix:${tabText.trim()}")
                        }
                    }
                }
                child.recycle()
            }
        }
    }

    /**
     * 获取节点的文本（优先getText，其次contentDescription）
     */
    private fun getNodeText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null

        // 优先获取text
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            return text
        }

        // 其次获取contentDescription
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty()) {
            return desc
        }

        // 尝试从子节点获取文本
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val childText = getNodeText(child)
                child.recycle()
                if (!childText.isNullOrEmpty()) {
                    return childText
                }
            }
        }

        return null
    }

    /**
     * 提取关键资源ID节点的文本
     * 查找ID包含 title, name, price, product 等关键词的节点
     */
    private fun extractKeyIdNodeTexts(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 10 || keyTexts.size >= 6) return

        val viewId = node.viewIdResourceName
        if (!viewId.isNullOrEmpty()) {
            var simpleId = viewId.lowercase()
            if (simpleId.contains("/")) {
                simpleId = simpleId.substringAfterLast("/")
            }

            // 检查是否是关键ID
            for (keyword in KEY_ID_KEYWORDS) {
                if (simpleId.contains(keyword)) {
                    val text = node.text?.toString()?.trim()
                    if (!text.isNullOrEmpty() && text.length in 2..100) {
                        if (isValidKeyText(text) && !containsText(keyTexts, text)) {
                            keyTexts.add("ID[$keyword]:$text")
                        }
                    }
                    break
                }
            }
        }

        // 递归处理子节点
        for (i in 0 until node.childCount) {
            if (keyTexts.size >= 6) break
            val child = node.getChild(i)
            if (child != null) {
                extractKeyIdNodeTexts(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 提取列表首项内容（RecyclerView/ListView的第一个可见项）
     */
    private fun extractListFirstItemText(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 8) return

        val className = node.className?.toString() ?: ""

        // 找到 RecyclerView 或 ListView
        if (className.contains("RecyclerView") || className.contains("ListView") ||
            className.contains("GridView")) {
            if (node.childCount > 0) {
                val firstItem = node.getChild(0)
                if (firstItem != null) {
                    // 从第一项中提取关键文本
                    extractFirstValidText(firstItem, keyTexts, 0)
                    firstItem.recycle()
                }
            }
            return
        }

        // 递归查找
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractListFirstItemText(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 从节点中提取第一个有效文本
     */
    private fun extractFirstValidText(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 5 || keyTexts.size >= 8) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length in 3..80) {
            if (isValidKeyText(text) && !containsText(keyTexts, text)) {
                keyTexts.add("L1:$text") // L1 表示列表首项
                return
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractFirstValidText(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 提取页面关键文本（增强版：更深遍历，更多提取）
     */
    private fun extractTopKeyTexts(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 10 || keyTexts.size >= 8) return

        val className = node.className?.toString() ?: ""

        // 跳过 Toolbar 和 TabLayout（已经单独处理）
        if (className.contains("Toolbar") || className.contains("TabLayout") ||
            className.contains("BottomNavigation") || className.contains("ActionBar")) {
            return
        }

        // 跳过 RecyclerView/ListView（已单独处理首项）
        if (className.contains("RecyclerView") || className.contains("ListView") ||
            className.contains("GridView")) {
            return
        }

        // 检查 TextView/Button/EditText 的文本
        if (className.contains("TextView") || className.contains("Button") ||
            className.contains("EditText")) {
            val text = node.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && text.length in 2..100) {
                if (isValidKeyText(text) && !containsText(keyTexts, text)) {
                    keyTexts.add(text)
                }
            }
        }

        // 递归处理子节点
        for (i in 0 until node.childCount) {
            if (keyTexts.size >= 12) break
            val child = node.getChild(i)
            if (child != null) {
                extractTopKeyTexts(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 提取价格文本（用于区分不同商品）
     * 价格是区分商品详情页的重要特征
     */
    private fun extractPriceTexts(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 12 || keyTexts.size >= 12) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length in 2..50) {
            // 检查是否是价格格式
            if (isPriceText(text) && !containsText(keyTexts, text)) {
                keyTexts.add("P:$text") // P: 表示价格
            }
        }

        // 递归处理子节点
        for (i in 0 until node.childCount) {
            if (keyTexts.size >= 12) break
            val child = node.getChild(i)
            if (child != null) {
                extractPriceTexts(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 提取大文本（通常是标题、商品名称等重要信息）
     * 大于10个字符的文本更可能是有意义的内容
     */
    private fun extractLargeTexts(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 15 || keyTexts.size >= 12) return

        val className = node.className?.toString() ?: ""

        // 跳过已处理的容器
        if (className.contains("Toolbar") || className.contains("TabLayout") ||
            className.contains("BottomNavigation")) {
            return
        }

        val text = node.text?.toString()?.trim()
        // 提取较长的文本（更可能是有意义的内容如商品名称）
        if (!text.isNullOrEmpty() && text.length in 10..200) {
            if (isValidKeyText(text) && !containsText(keyTexts, text)) {
                keyTexts.add("C:$text") // C: 表示内容
            }
        }

        // 递归处理子节点
        for (i in 0 until node.childCount) {
            if (keyTexts.size >= 12) break
            val child = node.getChild(i)
            if (child != null) {
                extractLargeTexts(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 提取所有contentDescription（对于淘宝等电商App很重要）
     * 很多电商App会把商品信息放在contentDescription中
     */
    private fun extractAllContentDescriptions(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 15 || keyTexts.size >= 12) return

        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc.length in 4..200) {
            // contentDescription 通常包含更完整的信息
            if (!containsText(keyTexts, desc)) {
                // 检查是否包含有用信息（商品名、价格等）
                if (desc.length >= 8 || isPriceText(desc)) {
                    keyTexts.add("D:$desc") // D: 表示description
                }
            }
        }

        // 递归处理子节点
        for (i in 0 until node.childCount) {
            if (keyTexts.size >= 12) break
            val child = node.getChild(i)
            if (child != null) {
                extractAllContentDescriptions(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 更激进地提取所有可见文本（用于难以提取的详情页）
     * 遍历所有节点，提取任何有意义的文本
     */
    private fun extractAllVisibleTexts(node: AccessibilityNodeInfo, keyTexts: MutableList<String>, depth: Int) {
        if (depth > 20 || keyTexts.size >= 15) return

        // 获取文本
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length in 4..150) {
            if (!containsText(keyTexts, text)) {
                // 检查是否是有意义的文本（不仅仅是UI按钮）
                if (text.length >= 6 || isPriceText(text) ||
                    text.matches(Regex(".*[\\u4e00-\\u9fa5]{3,}.*"))) { // 包含3个以上中文字符
                    keyTexts.add("V:$text") // V: 表示visible text
                }
            }
        }

        // 获取contentDescription
        val desc = node.contentDescription?.toString()?.trim()
        if (!desc.isNullOrEmpty() && desc.length in 4..150) {
            if (!containsText(keyTexts, desc)) {
                if (desc.length >= 6 || isPriceText(desc)) {
                    keyTexts.add("VD:$desc")
                }
            }
        }

        // 递归处理子节点
        for (i in 0 until node.childCount) {
            if (keyTexts.size >= 15) break
            val child = node.getChild(i)
            if (child != null) {
                extractAllVisibleTexts(child, keyTexts, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 判断文本是否是有效的关键文本
     * 优化：保留价格、数量等包含数字的有意义文本
     */
    private fun isValidKeyText(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false

        // 过滤太短的文本
        if (text.length < 2) return false

        // 过滤纯符号和空白
        if (text.matches(Regex("^[\\p{Punct}\\s]+$"))) return false

        // 保留价格格式（如 ¥199.00, $99.99, 199元）
        if (text.matches(Regex(".*[¥$￥€£]\\s*\\d+.*")) ||
            text.matches(Regex(".*\\d+\\s*[元块].*"))) {
            return true
        }

        // 过滤纯数字（但保留带单位的数字如 "100件", "1000+"）
        if (text.matches(Regex("^[\\d.,]+$"))) return false

        // 过滤常见无意义文本（UI按钮等）
        val lower = text.lowercase()
        val invalidTexts = arrayOf(
            "返回", "确定", "取消", "更多", "分享", "收藏", "关闭", "完成",
            "查看", "详情", "全部", "展开", "收起", "加载中", "loading",
            "ok", "cancel", "back", "close", "done", "more", "share",
            "...", "···", "→", "←", "↑", "↓", "立即购买", "加入购物车",
            "添加", "删除", "编辑", "保存", "提交", "下一步", "上一步"
        )
        for (invalid in invalidTexts) {
            if (lower == invalid) return false
        }

        return true
    }

    /**
     * 判断文本是否是价格文本（用于特殊提取）
     */
    private fun isPriceText(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        // 匹配常见价格格式
        return text.matches(Regex(".*[¥$￥€£]\\s*[\\d,.]+.*")) ||
                text.matches(Regex(".*[\\d,.]+\\s*[元块].*")) ||
                (text.matches(Regex("^[\\d,.]+$")) && text.length >= 2)
    }

    /**
     * 检查列表中是否已包含相似文本
     */
    private fun containsText(list: List<String>, text: String?): Boolean {
        if (text.isNullOrEmpty()) return true
        for (item in list) {
            // 去掉前缀后比较
            val itemText = if (item.contains(":")) item.substringAfter(":") else item
            if (itemText == text || itemText.contains(text) || text.contains(itemText)) {
                return true
            }
        }
        return false
    }

    /**
     * 检查列表中是否已包含带特定前缀的相似文本
     */
    private fun containsTextPrefix(list: List<String>, text: String, prefix: String): Boolean {
        for (item in list) {
            if (item.startsWith(prefix)) {
                val itemText = item.substring(prefix.length)
                if (itemText == text) {
                    return true
                }
            }
        }
        return false
    }

    // ==================== 结构指纹和 PageId 生成 ====================

    /**
     * 生成 View Tree 结构指纹
     * 提取关键布局节点和有ID的View，生成可读的结构路径
     * 例如: FL>CL>ABL[toolbar]>RV[list]
     */
    private fun generateViewTreeFingerprint(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""

        val keyNodes = mutableListOf<String>()
        collectKeyNodes(node, keyNodes, 0)

        if (keyNodes.isEmpty()) {
            return getSimpleClassName(node.className?.toString())
        }

        // 限制长度，取前10个关键节点
        val limit = minOf(keyNodes.size, 10)
        val sb = StringBuilder()
        for (i in 0 until limit) {
            if (i > 0) sb.append(">")
            sb.append(keyNodes[i])
        }
        return sb.toString()
    }

    /**
     * 递归收集关键节点（有ID的节点或重要布局节点）
     */
    private fun collectKeyNodes(node: AccessibilityNodeInfo, keyNodes: MutableList<String>, depth: Int) {
        if (depth > 6) return // 最多遍历6层

        val className = node.className?.toString() ?: ""
        val viewId = node.viewIdResourceName
        val simpleClass = getSimpleClassName(className)

        // 判断是否是关键节点
        var isKeyNode = false
        var nodeStr = simpleClass

        // 有资源ID的节点是关键节点
        if (!viewId.isNullOrEmpty()) {
            // 提取ID的简短名称 (去掉包名前缀)
            val simpleId = if (viewId.contains("/")) {
                viewId.substringAfterLast("/")
            } else {
                viewId
            }
            nodeStr = "$simpleClass[$simpleId]"
            isKeyNode = true
        }

        // 重要的布局容器也是关键节点
        if (!isKeyNode && isImportantLayout(className)) {
            isKeyNode = true
        }

        if (isKeyNode && keyNodes.size < 15) {
            keyNodes.add(nodeStr)
        }

        // 递归处理子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                collectKeyNodes(child, keyNodes, depth + 1)
                child.recycle()
            }
        }
    }

    /**
     * 获取类名的简写
     */
    private fun getSimpleClassName(className: String?): String {
        if (className.isNullOrEmpty()) return "?"
        var name = className
        if (name.contains(".")) {
            name = name.substringAfterLast(".")
        }
        // 常见类名缩写
        return when (name) {
            "FrameLayout" -> "FL"
            "LinearLayout" -> "LL"
            "RelativeLayout" -> "RL"
            "ConstraintLayout" -> "CL"
            "CoordinatorLayout" -> "CDL"
            "RecyclerView" -> "RV"
            "ListView" -> "LV"
            "ScrollView" -> "SV"
            "NestedScrollView" -> "NSV"
            "ViewPager" -> "VP"
            "ViewPager2" -> "VP2"
            "AppBarLayout" -> "ABL"
            "Toolbar" -> "TB"
            "TabLayout" -> "TAB"
            "BottomNavigationView" -> "BNV"
            "NavigationView" -> "NV"
            "DrawerLayout" -> "DL"
            "CardView" -> "CV"
            "MaterialCardView" -> "MCV"
            "ImageView" -> "IV"
            "TextView" -> "TV"
            "Button" -> "BTN"
            "EditText" -> "ET"
            "WebView" -> "WV"
            else -> {
                // 如果名字太长，截取前8个字符
                if (name.length > 8) name.substring(0, 8) else name
            }
        }
    }

    /**
     * 判断是否是重要的布局容器
     */
    private fun isImportantLayout(className: String?): Boolean {
        if (className.isNullOrEmpty()) return false
        return className.contains("RecyclerView") ||
                className.contains("ListView") ||
                className.contains("ViewPager") ||
                className.contains("TabLayout") ||
                className.contains("AppBarLayout") ||
                className.contains("Toolbar") ||
                className.contains("BottomNavigation") ||
                className.contains("NavigationView") ||
                className.contains("DrawerLayout") ||
                className.contains("WebView") ||
                className.contains("CoordinatorLayout")
    }

    /**
     * 生成 PageId（类似 PageInfo 的 pageId 格式）
     * 格式：ActivityName#结构hash_内容hash
     * - 结构hash：基于 View Tree 布局结构生成（10位MD5）
     * - 内容hash：基于页面关键文本内容+窗口标题生成（10位MD5）
     */
    private fun generatePageId(
        className: String,
        structureFingerprint: String,
        contentFingerprint: String,
        windowTitle: String?
    ): String {
        // 获取 Activity 简短名称（不含包名）
        val simpleActivity = if (className.contains(".")) {
            className.substringAfterLast(".")
        } else {
            className
        }

        if (structureFingerprint.isEmpty()) {
            // 没有结构指纹时，返回简单格式
            return simpleActivity
        }

        // 生成结构hash（10位）
        val structureHash = generateMd5Hash("$simpleActivity|$structureFingerprint", 10)

        // 生成内容hash（10位），结合内容指纹和窗口标题
        val contentSource = if (!windowTitle.isNullOrEmpty()) {
            "W:$windowTitle|$contentFingerprint"
        } else {
            contentFingerprint
        }

        return if (contentSource.isNotEmpty()) {
            val contentHash = generateMd5Hash(contentSource, 10)
            // 格式: ActivityName#结构hash_内容hash
            "$simpleActivity#${structureHash}_${contentHash}"
        } else {
            // 没有内容指纹时，只使用结构hash
            "$simpleActivity#${structureHash}"
        }
    }

    /**
     * 生成 MD5 哈希值
     *
     * @param input 输入字符串
     * @param length 返回的哈希长度（取前 N 个字符）
     * @return 哈希字符串
     */
    private fun generateMd5Hash(input: String, length: Int): String {
        return try {
            val md = java.security.MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            val sb = StringBuilder()
            for (b in digest) {
                sb.append(String.format("%02x", b))
            }
            sb.substring(0, minOf(length, sb.length))
        } catch (e: java.security.NoSuchAlgorithmException) {
            // MD5 不可用时，使用 hashCode 作为后备方案
            Math.abs(input.hashCode()).toString().take(length)
        }
    }

    // ==================== 节点点击与信息获取功能 ====================
    // 参考设计文档: NODE_CLICK_FEATURE_DESIGN.md

    /**
     * 根据屏幕坐标获取节点完整信息
     * @param x X坐标
     * @param y Y坐标
     * @return 节点查询结果（JSON 字符串）
     */
    fun getNodeInfoAtPosition(x: Float, y: Float): String {
        Log.d(TAG, "获取坐标 ($x, $y) 处的节点信息")
        
        val result = top.yling.ozx.guiagent.a11y.NodeExplorer.queryNodeAtPosition(x, y)
        
        return try {
            kotlinx.serialization.json.Json.encodeToString(
                top.yling.ozx.guiagent.a11y.data.NodeQueryResult.serializer(),
                result
            )
        } catch (e: Exception) {
            Log.e(TAG, "序列化节点信息失败: ${e.message}", e)
            """{"success":false,"error":"序列化失败: ${e.message}"}"""
        }
    }

    /**
     * 获取当前窗口的节点快照
     * @return 快照信息（JSON 字符串）
     */
    fun getNodeSnapshot(): String {
        Log.d(TAG, "获取节点快照")
        
        val snapshot = top.yling.ozx.guiagent.a11y.NodeExplorer.getSnapshot()
        
        return if (snapshot != null) {
            try {
                kotlinx.serialization.json.Json.encodeToString(
                    top.yling.ozx.guiagent.a11y.data.SnapshotNodeInfo.serializer(),
                    snapshot
                )
            } catch (e: Exception) {
                Log.e(TAG, "序列化快照信息失败: ${e.message}", e)
                """{"error":"序列化失败: ${e.message}"}"""
            }
        } else {
            """{"error":"无法获取当前窗口"}"""
        }
    }

    /**
     * 使用选择器查询节点
     * @param selector 选择器字符串（如 "[text='跳过']"）
     * @return 匹配的节点列表（JSON 字符串）
     */
    fun queryNodesBySelector(selector: String): String {
        Log.d(TAG, "使用选择器查询: $selector")
        
        val nodes = top.yling.ozx.guiagent.a11y.NodeExplorer.queryBySelector(selector)
        
        return try {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(
                    top.yling.ozx.guiagent.a11y.data.ClickedNodeInfo.serializer()
                ),
                nodes
            )
        } catch (e: Exception) {
            Log.e(TAG, "序列化查询结果失败: ${e.message}", e)
            "[]"
        }
    }

    /**
     * 使用选择器查询第一个匹配的节点
     * @param selector 选择器字符串
     * @return 节点信息（JSON 字符串），未找到返回 null
     */
    fun queryFirstNodeBySelector(selector: String): String? {
        Log.d(TAG, "使用选择器查询第一个节点: $selector")
        
        val node = top.yling.ozx.guiagent.a11y.NodeExplorer.queryFirstBySelector(selector)
        
        return if (node != null) {
            try {
                kotlinx.serialization.json.Json.encodeToString(
                    top.yling.ozx.guiagent.a11y.data.ClickedNodeInfo.serializer(),
                    node
                )
            } catch (e: Exception) {
                Log.e(TAG, "序列化节点信息失败: ${e.message}", e)
                null
            }
        } else {
            null
        }
    }

    /**
     * 点击选择器匹配的第一个节点
     * @param selector 选择器字符串
     * @param callback 点击结果回调
     */
    fun clickBySelector(selector: String, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "点击选择器匹配的节点: $selector")
        
        val node = top.yling.ozx.guiagent.a11y.NodeExplorer.queryFirstBySelector(selector)
        
        if (node != null) {
            val centerX = node.position.centerX.toFloat()
            val centerY = node.position.centerY.toFloat()
            Log.d(TAG, "找到节点，点击坐标: ($centerX, $centerY)")
            click(centerX, centerY, callback)
        } else {
            Log.w(TAG, "未找到匹配的节点: $selector")
            callback?.invoke(false)
        }
    }

    /**
     * 清除节点缓存
     */
    fun clearNodeCache() {
        top.yling.ozx.guiagent.a11y.a11yContext.clearNodeCache()
        Log.d(TAG, "节点缓存已清除")
    }
}
