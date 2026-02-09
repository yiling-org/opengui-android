package top.yling.ozx.guiagent.websocket

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import top.yling.ozx.guiagent.MyAccessibilityService
import top.yling.ozx.guiagent.services.BrowserService
import top.yling.ozx.guiagent.services.CalendarService
import top.yling.ozx.guiagent.services.ContactsService
import top.yling.ozx.guiagent.services.NotificationService
import top.yling.ozx.guiagent.services.PhoneCallService
import top.yling.ozx.guiagent.services.SmsService
import top.yling.ozx.guiagent.shizuku.ShizukuApi
import top.yling.ozx.guiagent.util.ImageCompressionConfig
import top.yling.ozx.guiagent.websocket.handler.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * 指令执行结果
 */
data class CommandResult(
    val success: Boolean,
    val message: String,
    val data: Map<String, Any?>? = null
)

/**
 * 指令执行器，统一处理所有无障碍指令
 * 使用处理器模式，每个 action 对应一个独立的处理器类
 */
class CommandExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val browserService: BrowserService,
    private val smsService: SmsService,
    private val contactsService: ContactsService,
    private val notificationService: NotificationService,
    private val calendarService: CalendarService,
    private val phoneCallService: PhoneCallService,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG = "CommandExecutor"

        // 设备性能等级对应的截图延迟时间（毫秒）
        private const val SCREENSHOT_DELAY_HIGH_END = 1600L    // 高端设备：1.6秒
        private const val SCREENSHOT_DELAY_MID_RANGE = 2000L   // 中端设备：2秒
        private const val SCREENSHOT_DELAY_LOW_END = 2500L     // 低端设备：2.5秒

        // 设备性能等级判断阈值（基于屏幕宽度）
        private const val HIGH_END_SCREEN_WIDTH = 1440   // 屏幕宽度 >= 1440 为高端
        private const val MID_RANGE_SCREEN_WIDTH = 1080  // 屏幕宽度 >= 1080 为中端
    }

    // 处理器注册表
    private val handlers: Map<String, ActionHandler>

    init {
        // 注册所有处理器
        handlers = listOf(
            // 基础操作
            HeartbeatHandler(),
            PingHandler(),
            StatusHandler(),
            IdleHandler(),
            // 窗口信息
            GetWindowIdHandler(),
            // 交互操作
            ClickHandler(),
            LongPressHandler(),
            CopyTextHandler(),
            ScrollHandler(),
            DragHandler(),
            TypeHandler(),
            // 系统按键
            PressHomeHandler(),
            PressBackHandler(),
            PressRecentsHandler(),
            OpenNotificationsHandler(),
            OpenQuickSettingsHandler(),
            // 截图和节点
            ScreenshotHandler(),
            GetNodeInfoAtPositionHandler(),
            SelectorActionHandler(),
            // 应用和 URL
            OpenAppHandler(),
            OpenUrlHandler(),
            // 通讯
            SendSmsHandler(),
            MakeCallHandler(),
            GetAllContactsHandler(),
            // 通知和日历
            PostNotificationHandler(),
            GetTodayEventsHandler(),
            AddEventHandler(),
            // 用户介入
            ShowInterventionHandler(),
            HideInterventionHandler(),
            // 定时任务
            ScheduleTaskHandler()
        ).associateBy { it.actionName }
    }

    // 缓存设备性能等级，避免重复计算
    private val screenshotDelay: Long by lazy { calculateScreenshotDelay() }

    /**
     * 根据屏幕分辨率计算截图延迟时间
     */
    private fun calculateScreenshotDelay(): Long {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)

        val screenWidth = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)

        val delay = when {
            screenWidth >= HIGH_END_SCREEN_WIDTH -> SCREENSHOT_DELAY_HIGH_END
            screenWidth >= MID_RANGE_SCREEN_WIDTH -> SCREENSHOT_DELAY_MID_RANGE
            else -> SCREENSHOT_DELAY_LOW_END
        }

        Log.i(TAG, "设备性能检测: 屏幕=${displayMetrics.widthPixels}x${displayMetrics.heightPixels}, " +
                "判断宽度=${screenWidth}, 截图延迟=${delay}ms, 设备=${Build.MANUFACTURER} ${Build.MODEL}")

        return delay
    }

    /**
     * 执行指令
     * @param service 无障碍服务实例（可为 null，某些命令如 schedule_task 不需要）
     * @param command 要执行的指令
     * @param takeScreenshotAfter 是否在执行后截图（用于 isLast=true 的情况）
     * @param callback 执行结果回调
     * @author shanwb
     */
    fun execute(
        service: MyAccessibilityService?,
        command: AccessibilityCommand,
        takeScreenshotAfter: Boolean = false,
        callback: (CommandResult) -> Unit
    ) {
        val params = command.params
        val action = command.action.lowercase()

        // 包装回调：如果需要截图，在成功后延迟截图
        val wrappedCallback: (CommandResult) -> Unit = { result ->
            if (takeScreenshotAfter && result.success && service != null) {
                takeScreenshotAndCallback(service, result, callback)
            } else {
                callback(result)
            }
        }

        // 创建执行上下文
        val context = ActionContext(
            applicationContext = this.context,
            service = service,
            command = command,
            params = params,
            appLauncher = appLauncher,
            browserService = browserService,
            smsService = smsService,
            contactsService = contactsService,
            notificationService = notificationService,
            calendarService = calendarService,
            phoneCallService = phoneCallService,
            coroutineScope = coroutineScope,
            takeScreenshotAfter = takeScreenshotAfter,
            wrappedCallback = wrappedCallback
        )

        // 查找并执行处理器
        val handler = handlers[action]
        if (handler != null) {
            handler.handle(context, callback)
        } else {
            Log.w(TAG, "未知指令: ${command.action}")
            callback(CommandResult(false, "未知指令"))
        }
    }

    /**
     * 延迟截图并回调结果
     */
    private fun takeScreenshotAndCallback(
        service: MyAccessibilityService,
        originalResult: CommandResult,
        callback: (CommandResult) -> Unit
    ) {
        coroutineScope.launch {
            delay(screenshotDelay)
            service.takeScreenshot(
                callback = { success ->
                    if (!success) {
                        callback(originalResult)
                    }
                },
                bitmapCallback = { bitmap ->
                    if (bitmap != null) {
                        val width = bitmap.width
                        val height = bitmap.height

                        val root = MyAccessibilityService.instance?.rootInActiveWindow
                        val packageName = root?.packageName?.toString() ?: "unknown"
                        val activityName = try {
                            val shizukuContext = ShizukuApi.getContextOrNull()
                            val topCpn = shizukuContext?.topCpn()
                            if (topCpn?.packageName == packageName) {
                                topCpn.className
                            } else {
                                root?.className?.toString()
                            }
                        } catch (e: Exception) {
                            root?.className?.toString()
                        } ?: "unknown"

                        val base64Image = bitmapToBase64(bitmap)
                        bitmap.recycle()
                        val data = (originalResult.data?.toMutableMap() ?: mutableMapOf()).apply {
                            put("image", base64Image)
                            put("imageWidth", width)
                            put("imageHeight", height)
                            put("packageName", packageName)
                            put("activityName", activityName)
                        }
                        val message = "${originalResult.message} [$packageName/$activityName]"
                        callback(CommandResult(originalResult.success, message, data))
                    } else {
                        callback(originalResult)
                    }
                }
            )
        }
    }

    /**
     * 将 Bitmap 转换为 Base64 字符串（带缩放和压缩优化）
     */
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = ImageCompressionConfig.getJpegQuality(context)): String {
        val scaleFactor = ImageCompressionConfig.getScaleFactor(context)
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

    /**
     * 缩放Bitmap
     */
    private fun scaleBitmap(bitmap: Bitmap, scale: Float): Bitmap {
        if (scale >= 1.0f) return bitmap

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
