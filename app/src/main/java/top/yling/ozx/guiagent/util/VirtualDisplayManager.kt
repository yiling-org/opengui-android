package top.yling.ozx.guiagent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import top.yling.ozx.guiagent.shizuku.ShizukuApi

/**
 * 虚拟屏幕管理器（单例）
 *
 * 功能：
 * 1. 创建和管理虚拟屏幕
 * 2. 在虚拟屏幕上启动指定 App
 * 3. 获取虚拟屏幕截图
 * 4. 获取虚拟屏幕状态
 */
@OptIn(InternalSerializationApi::class)
object VirtualDisplayManager {

    private const val TAG = "VirtualDisplayManager"

    // 虚拟屏幕参数（从系统读取，与主屏幕一致）
    private var screenWidth = 1080
    private var screenHeight = 1920
    private var screenDpi = 320

    // 虚拟屏幕相关
    private var displayManager: DisplayManager? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // 状态
    private var _displayId: Int = -1
    private var _currentPackage: String? = null
    private var _isCreated: Boolean = false

    // 自动刷新
    private var autoRefreshEnabled = false
    private var refreshInterval = 100L
    private var onFrameCaptured: ((Bitmap) -> Unit)? = null

    // 缓存最后一帧（解决静止画面无法截图的问题）
    private var lastBitmap: Bitmap? = null
    private val bitmapLock = Object()

    // 全局帧监听器（供 UI 层使用）
    private var globalFrameListener: ((Bitmap) -> Unit)? = null

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (autoRefreshEnabled) {
                captureFrameInternal()
                mainHandler.postDelayed(this, refreshInterval)
            }
        }
    }

    // ============================================
    // 公开属性 - 获取状态
    // ============================================

    /**
     * 虚拟屏幕 Display ID
     */
    val displayId: Int get() = _displayId

    /**
     * 当前运行的 App 包名
     */
    val currentPackage: String? get() = _currentPackage

    /**
     * 虚拟屏幕是否已创建
     */
    val isCreated: Boolean get() = _isCreated

    /**
     * 获取虚拟屏幕状态
     */
    fun getStatus(): VirtualDisplayStatus {
        return VirtualDisplayStatus(
            isCreated = _isCreated,
            displayId = _displayId,
            currentPackage = _currentPackage,
            width = if (_isCreated) screenWidth else 0,
            height = if (_isCreated) screenHeight else 0
        )
    }

    // ============================================
    // 公开方法 - 虚拟屏幕管理
    // ============================================

    /**
     * 初始化虚拟屏幕
     * @param context 上下文
     * @return 是否创建成功
     */
    fun create(context: Context): Boolean {
        if (_isCreated) {
            Log.d(TAG, "虚拟屏幕已存在，Display ID: $_displayId")
            return true
        }

        return try {
            // 读取系统屏幕参数
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDpi = metrics.densityDpi
            Log.d(TAG, "读取系统屏幕参数: ${screenWidth}x${screenHeight}, DPI: $screenDpi")

            // 初始化 DisplayManager
            displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

            // 初始化 ImageReader 线程
            imageReaderThread = HandlerThread("VirtualDisplayImageReader").apply { start() }
            imageReaderHandler = Handler(imageReaderThread!!.looper)

            // 创建 ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth,
                screenHeight,
                PixelFormat.RGBA_8888,
                3
            )

            // 设置帧监听器，持续缓存最新帧（解决静止画面无法截图的问题）
            imageReader!!.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image)
                        image.close()
                        if (bitmap != null) {
                            synchronized(bitmapLock) {
                                lastBitmap?.recycle()
                                lastBitmap = bitmap
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "缓存帧失败", e)
                }
            }, imageReaderHandler)

            // 创建虚拟屏幕
            virtualDisplay = displayManager!!.createVirtualDisplay(
                "VirtualDisplayManager",
                screenWidth,
                screenHeight,
                screenDpi,
                imageReader!!.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )

            if (virtualDisplay != null) {
                _displayId = virtualDisplay!!.display.displayId
                _isCreated = true
                Log.d(TAG, "✅ 虚拟屏幕创建成功！Display ID: $_displayId")
                true
            } else {
                Log.e(TAG, "虚拟屏幕创建失败")
                release()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建虚拟屏幕异常", e)
            release()
            false
        }
    }

    /**
     * 释放虚拟屏幕资源
     */
    fun release() {
        Log.d(TAG, "释放虚拟屏幕资源...")

        // 停止自动刷新
        stopAutoRefresh()

        // 释放虚拟屏幕
        virtualDisplay?.release()
        virtualDisplay = null

        // 关闭 ImageReader
        imageReader?.close()
        imageReader = null

        // 停止线程
        imageReaderThread?.quitSafely()
        imageReaderThread = null
        imageReaderHandler = null

        // 清理缓存帧
        synchronized(bitmapLock) {
            lastBitmap?.recycle()
            lastBitmap = null
        }

        // 重置状态
        _displayId = -1
        _currentPackage = null
        _isCreated = false
        displayManager = null

        Log.d(TAG, "虚拟屏幕资源已释放")
    }

    // ============================================
    // 公开方法 - App 启动
    // ============================================

    /**
     * 在虚拟屏幕上启动指定 App
     * @param packageName 包名
     * @param activityName Activity 名称（可选，为空则启动主 Activity）
     * @return 启动结果
     */
    suspend fun startApp(packageName: String, activityName: String? = null): LaunchResult {
        if (!_isCreated || _displayId == -1) {
            return LaunchResult(false, "虚拟屏幕未创建")
        }

        return withContext(Dispatchers.IO) {
            val context = ShizukuApi.getOrConnect()
            if (context == null) {
                return@withContext LaunchResult(false, "Shizuku 未连接")
            }

            val command = if (activityName != null) {
                "am start --display $_displayId --user 0 -n $packageName/$activityName"
            } else {
                "monkey -p $packageName --display $_displayId -c android.intent.category.LAUNCHER 1"
            }

            Log.d(TAG, "执行命令: $command")
            val result = context.execCommand(command)

            if (result.ok) {
                _currentPackage = packageName
                Log.d(TAG, "✅ App 启动成功: $packageName")
                LaunchResult(true, null)
            } else {
                Log.e(TAG, "App 启动失败: ${result.error}")
                LaunchResult(false, result.error)
            }
        }
    }

    /**
     * 停止当前运行的 App
     * @return 停止结果
     */
    suspend fun stopCurrentApp(): LaunchResult {
        val pkg = _currentPackage ?: return LaunchResult(false, "没有运行中的 App")

        return withContext(Dispatchers.IO) {
            val context = ShizukuApi.getOrConnect()
            if (context == null) {
                return@withContext LaunchResult(false, "Shizuku 未连接")
            }

            val command = "am force-stop $pkg"
            val result = context.execCommand(command)

            if (result.ok) {
                _currentPackage = null
                Log.d(TAG, "✅ App 已停止: $pkg")
                LaunchResult(true, null)
            } else {
                Log.e(TAG, "停止 App 失败: ${result.error}")
                LaunchResult(false, result.error)
            }
        }
    }

    /**
     * 停止指定 App
     * @param packageName 包名
     * @return 停止结果
     */
    suspend fun stopApp(packageName: String): LaunchResult {
        return withContext(Dispatchers.IO) {
            val context = ShizukuApi.getOrConnect()
            if (context == null) {
                return@withContext LaunchResult(false, "Shizuku 未连接")
            }

            val command = "am force-stop $packageName"
            val result = context.execCommand(command)

            if (result.ok) {
                if (_currentPackage == packageName) {
                    _currentPackage = null
                }
                Log.d(TAG, "✅ App 已停止: $packageName")
                LaunchResult(true, null)
            } else {
                Log.e(TAG, "停止 App 失败: ${result.error}")
                LaunchResult(false, result.error)
            }
        }
    }

    // ============================================
    // 公开方法 - 截图
    // ============================================

    /**
     * 获取当前虚拟屏幕截图（返回缓存的最后一帧）
     * @return Bitmap 或 null
     */
    fun captureScreenshot(): Bitmap? {
        if (!_isCreated) {
            Log.w(TAG, "虚拟屏幕未创建，无法截图")
            return null
        }

        synchronized(bitmapLock) {
            val cached = lastBitmap
            if (cached != null && !cached.isRecycled) {
                // 返回副本，避免外部回收影响缓存
                return cached.copy(cached.config ?: Bitmap.Config.ARGB_8888, false)
            }
        }

        Log.w(TAG, "缓存帧为空，虚拟屏幕可能还未渲染任何内容")
        return null
    }

    /**
     * 设置全局帧监听器（供 UI 层使用，如 MainActivity）
     * @param listener 帧回调，传 null 可移除监听
     */
    fun setGlobalFrameListener(listener: ((Bitmap) -> Unit)?) {
        globalFrameListener = listener
        Log.d(TAG, if (listener != null) "已设置全局帧监听器" else "已移除全局帧监听器")
    }

    /**
     * 开始自动刷新截图（无回调版本，使用全局帧监听器）
     * @param intervalMs 刷新间隔（毫秒）
     */
    fun startAutoRefresh(intervalMs: Long = 100L) {
        if (autoRefreshEnabled) return

        refreshInterval = intervalMs
        autoRefreshEnabled = true
        mainHandler.post(refreshRunnable)
        Log.d(TAG, "开始自动刷新，间隔: ${intervalMs}ms（使用全局监听器）")
    }

    /**
     * 开始自动刷新截图
     * @param intervalMs 刷新间隔（毫秒）
     * @param onFrame 每帧回调
     */
    fun startAutoRefresh(intervalMs: Long = 100L, onFrame: (Bitmap) -> Unit) {
        if (autoRefreshEnabled) return

        refreshInterval = intervalMs
        onFrameCaptured = onFrame
        autoRefreshEnabled = true
        mainHandler.post(refreshRunnable)
        Log.d(TAG, "开始自动刷新，间隔: ${intervalMs}ms")
    }

    /**
     * 停止自动刷新
     */
    fun stopAutoRefresh() {
        autoRefreshEnabled = false
        mainHandler.removeCallbacks(refreshRunnable)
        onFrameCaptured = null
        Log.d(TAG, "停止自动刷新")
    }

    /**
     * 是否正在自动刷新
     */
    val isAutoRefreshing: Boolean get() = autoRefreshEnabled

    // ============================================
    // 内部方法
    // ============================================

    private fun captureFrameInternal() {
        imageReaderHandler?.post {
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    if (bitmap != null) {
                        mainHandler.post {
                            // 调用局部回调
                            onFrameCaptured?.invoke(bitmap)
                            // 调用全局监听器
                            globalFrameListener?.invoke(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "捕获帧失败", e)
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding > 0) {
                Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Image 转 Bitmap 失败", e)
            null
        }
    }

    // ============================================
    // 数据类
    // ============================================

    /**
     * 虚拟屏幕状态
     */
    data class VirtualDisplayStatus(
        val isCreated: Boolean,
        val displayId: Int,
        val currentPackage: String?,
        val width: Int,
        val height: Int
    )

    /**
     * App 启动结果
     */
    data class LaunchResult(
        val success: Boolean,
        val error: String?
    )
}
