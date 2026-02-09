package top.yling.ozx.guiagent

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.InternalSerializationApi
import top.yling.ozx.guiagent.databinding.ActivityVirtualDisplayTestBinding
import top.yling.ozx.guiagent.shizuku.ShizukuApi

/**
 * 虚拟屏幕测试 Activity
 * 使用 VirtualDisplay + ImageReader 方案：
 * 1. 通过 DisplayManager.createVirtualDisplay 创建真正的虚拟屏幕
 * 2. 使用 Shizuku shell 在虚拟屏幕上启动美团
 * 3. 通过 ImageReader 获取虚拟屏幕画面
 */
@OptIn(InternalSerializationApi::class)
class VirtualDisplayTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVirtualDisplayTestBinding
    private lateinit var displayManager: DisplayManager

    // 虚拟屏幕参数
    private val virtualDisplayWidth = 1080
    private val virtualDisplayHeight = 1920
    private val virtualDisplayDpi = 320

    // 虚拟屏幕
    private var virtualDisplay: VirtualDisplay? = null
    private var virtualDisplayId: Int = -1
    private var imageReader: ImageReader? = null
    private var presentation: VirtualDisplayPresentation? = null

    // ImageReader 处理线程
    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null

    // 主线程 Handler
    private val mainHandler = Handler(Looper.getMainLooper())

    // 自动刷新控制
    private var autoRefresh = false
    private val refreshInterval = 100L // 刷新间隔 (ms)
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (autoRefresh) {
                captureFrame()
                mainHandler.postDelayed(this, refreshInterval)
            }
        }
    }

    // 美团包名和主 Activity
    private val meituanPackage = "com.sankuai.meituan"
    private val meituanMainActivity = "com.meituan.android.pt.homepage.activity.MainActivity"

    companion object {
        private const val TAG = "VirtualDisplayTest"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityVirtualDisplayTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        setupWindowInsets()
        setupUI()

        // 初始化 ImageReader 线程
        initImageReaderThread()

        // 初始化时创建虚拟屏幕
        createVirtualDisplay()
    }

    private fun initImageReaderThread() {
        imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }
        imageReaderHandler = Handler(imageReaderThread!!.looper)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        // 启动美团按钮
        binding.btnStartMeituan.setOnClickListener {
            startMeituanOnVirtualDisplay()
        }

        // 关闭美团按钮
        binding.btnStopMeituan.setOnClickListener {
            stopMeituan()
        }

        // 截图按钮 - 切换自动刷新
        binding.btnScreenshot.setOnClickListener {
            toggleAutoRefresh()
        }

        // 上滑按钮
        binding.btnSwipeUp.setOnClickListener {
            performSwipeUp()
        }

        // 上滑按钮 (无障碍服务)
        binding.btnSwipeUpA11y.setOnClickListener {
            performSwipeUpA11y()
        }

        // 初始状态
        binding.btnStartMeituan.isEnabled = false
        binding.btnStopMeituan.isEnabled = false
        binding.btnScreenshot.isEnabled = false
        binding.btnSwipeUp.isEnabled = false
        binding.btnSwipeUpA11y.isEnabled = false

        updateStatus("正在创建虚拟屏幕...")
    }

    /**
     * 切换自动刷新状态
     */
    private fun toggleAutoRefresh() {
        autoRefresh = !autoRefresh
        if (autoRefresh) {
            binding.btnScreenshot.text = "停止刷新"
            mainHandler.post(refreshRunnable)
        } else {
            binding.btnScreenshot.text = "开始刷新"
            mainHandler.removeCallbacks(refreshRunnable)
        }
    }

    /**
     * 创建真正的虚拟屏幕
     * 使用 DisplayManager.createVirtualDisplay + ImageReader
     */
    private fun createVirtualDisplay() {
        try {
            // 1. 创建 ImageReader 作为渲染目标
            imageReader = ImageReader.newInstance(
                virtualDisplayWidth,
                virtualDisplayHeight,
                PixelFormat.RGBA_8888,
                3
            )

            val surface = imageReader!!.surface

            // 2. 创建 VirtualDisplay
            virtualDisplay = displayManager.createVirtualDisplay(
                "SecondScreen",
                virtualDisplayWidth,
                virtualDisplayHeight,
                virtualDisplayDpi,
                surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            )

            if (virtualDisplay != null) {
                virtualDisplayId = virtualDisplay!!.display.displayId

                Log.d(TAG, "✅ 虚拟屏幕创建成功！Display ID: $virtualDisplayId")
                Log.d(TAG, "Display Name: ${virtualDisplay!!.display.name}")
                Log.d(TAG, "Display Size: ${virtualDisplayWidth}x${virtualDisplayHeight}")

                // 显示 Presentation
                showPresentation(virtualDisplay!!.display)

                updateStatus("虚拟屏幕已创建 (ID: $virtualDisplayId)")
                binding.btnStartMeituan.isEnabled = true
                binding.btnStopMeituan.isEnabled = true
                binding.btnScreenshot.isEnabled = true
                binding.btnSwipeUp.isEnabled = true
                binding.btnSwipeUpA11y.isEnabled = true

                Toast.makeText(this, "虚拟屏幕创建成功", Toast.LENGTH_SHORT).show()
            } else {
                updateStatus("创建虚拟屏幕失败")
                Toast.makeText(this, "创建虚拟屏幕失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建虚拟屏幕异常", e)
            updateStatus("创建失败: ${e.message}")
            Toast.makeText(this, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从 ImageReader 捕获一帧画面
     */
    private fun captureFrame() {
        imageReaderHandler?.post {
            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    if (bitmap != null) {
                        mainHandler.post {
                            showBitmap(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "捕获帧失败", e)
            }
        }
    }

    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            // 创建 Bitmap
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            // 如果有 padding，需要裁剪
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

    /**
     * 显示 Bitmap 到界面
     */
    private fun showBitmap(bitmap: Bitmap) {
        binding.screenshotImage.setImageBitmap(bitmap)
        binding.placeholderContainer.visibility = View.GONE
    }

    /**
     * 在虚拟屏幕上显示 Presentation
     */
    private fun showPresentation(display: Display) {
        try {
            presentation?.dismiss()
            presentation = VirtualDisplayPresentation(this, display)
            presentation?.show()
            Log.d(TAG, "Presentation 显示在 Display ${display.displayId}")
        } catch (e: Exception) {
            Log.e(TAG, "显示 Presentation 失败", e)
        }
    }

    /**
     * 在虚拟屏幕上启动美团
     */
    private fun startMeituanOnVirtualDisplay() {
        if (virtualDisplayId == -1) {
            Toast.makeText(this, "虚拟屏幕未创建", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            updateStatus("正在启动美团...")

            val context = ShizukuApi.getOrConnect()
            if (context == null) {
                updateStatus("Shizuku 未连接")
                return@launch
            }

            withContext(Dispatchers.IO) {
                val command = "am start --display $virtualDisplayId --user 0 -n $meituanPackage/$meituanMainActivity"
                Log.d(TAG, "执行: $command")

                val result = context.execCommand(command)

                withContext(Dispatchers.Main) {
                    if (result.ok) {
                        updateStatus("美团已启动 (Display: $virtualDisplayId)")
                        Toast.makeText(this@VirtualDisplayTestActivity, "美团启动成功", Toast.LENGTH_SHORT).show()

                        // 更新 Presentation 状态
                        presentation?.setStatus("美团运行中")

                        // 自动开始刷新
                        if (!autoRefresh) {
                            toggleAutoRefresh()
                        }
                    } else {
                        updateStatus("启动失败: ${result.error}")
                        Toast.makeText(this@VirtualDisplayTestActivity, "启动失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 关闭美团
     */
    private fun stopMeituan() {
        lifecycleScope.launch {
            updateStatus("正在关闭美团...")

            val context = ShizukuApi.getOrConnect()
            if (context == null) {
                updateStatus("Shizuku 未连接")
                return@launch
            }

            withContext(Dispatchers.IO) {
                val command = "am force-stop $meituanPackage"
                val result = context.execCommand(command)

                withContext(Dispatchers.Main) {
                    if (result.ok) {
                        updateStatus("美团已关闭")
                        Toast.makeText(this@VirtualDisplayTestActivity, "美团已关闭", Toast.LENGTH_SHORT).show()

                        // 更新 Presentation 状态
                        presentation?.setStatus("等待启动应用...")

                        // 停止自动刷新
                        if (autoRefresh) {
                            toggleAutoRefresh()
                        }
                    } else {
                        updateStatus("关闭失败: ${result.error}")
                    }
                }
            }
        }
    }

    /**
     * 在虚拟屏幕上执行上滑操作
     * 等效于: adb shell input -d <displayId> swipe 250 400 250 100 300
     */
    private fun performSwipeUp() {
        if (virtualDisplayId == -1) {
            Toast.makeText(this, "虚拟屏幕未创建", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val context = ShizukuApi.getOrConnect()
            if (context == null) {
                Toast.makeText(this@VirtualDisplayTestActivity, "Shizuku 未连接", Toast.LENGTH_SHORT).show()
                return@launch
            }

            withContext(Dispatchers.IO) {
                // input -d <displayId> swipe <startX> <startY> <endX> <endY> <duration>
                val command = "input -d $virtualDisplayId swipe 250 400 250 100 300"
                Log.d(TAG, "执行: $command")

                val result = context.execCommand(command)

                withContext(Dispatchers.Main) {
                    if (result.ok) {
                        Log.d(TAG, "上滑执行成功")
                    } else {
                        Log.e(TAG, "上滑执行失败: ${result.error}")
                        Toast.makeText(this@VirtualDisplayTestActivity, "上滑失败: ${result.error}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 使用无障碍服务在虚拟屏幕上执行上滑操作
     * 等效于: adb shell input -d <displayId> swipe 250 400 250 100 300
     * 需要 Android 11+ (API 30+) 支持
     */
    private fun performSwipeUpA11y() {
        if (virtualDisplayId == -1) {
            Toast.makeText(this, "虚拟屏幕未创建", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查是否支持
        if (!VirtualDisplayGestureHelper.isSupported()) {
            Toast.makeText(this, "需要 Android 11+ 才支持此功能", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查无障碍服务是否启用
        if (!VirtualDisplayGestureHelper.isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_SHORT).show()
            return
        }

        // 执行上滑：从 (250, 400) 滑动到 (250, 100)，持续 300ms
        val success = VirtualDisplayGestureHelper.swipe(
            displayId = virtualDisplayId,
            startX = 250f,
            startY = 400f,
            endX = 250f,
            endY = 100f,
            duration = 300
        ) { result ->
            runOnUiThread {
                if (result) {
                    Log.d(TAG, "无障碍服务上滑执行成功")
                } else {
                    Log.e(TAG, "无障碍服务上滑执行失败")
                    Toast.makeText(this, "上滑失败", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (!success) {
            Toast.makeText(this, "无法执行手势", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 释放虚拟屏幕资源
     */
    private fun releaseVirtualDisplay() {
        // 停止自动刷新
        autoRefresh = false
        mainHandler.removeCallbacks(refreshRunnable)

        virtualDisplay?.release()
        virtualDisplay = null
        virtualDisplayId = -1

        imageReader?.close()
        imageReader = null

        Log.d(TAG, "虚拟屏幕已释放")
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            binding.statusText.text = message
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presentation?.dismiss()
        presentation = null
        releaseVirtualDisplay()

        // 停止 ImageReader 线程
        imageReaderThread?.quitSafely()
        imageReaderThread = null
        imageReaderHandler = null
    }
}

/**
 * 虚拟屏幕上的 Presentation
 * 显示在虚拟屏幕上，作为初始内容和状态指示
 */
class VirtualDisplayPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 创建简单的 UI
        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#1A1A2E"))
        }

        // 状态文字
        statusText = TextView(context).apply {
            text = "虚拟屏幕已就绪\n等待启动应用..."
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
        }

        container.addView(statusText, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        // 加载指示器
        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
        }
        container.addView(progressBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER or Gravity.BOTTOM
        ).apply {
            bottomMargin = 100
        })

        setContentView(container)
    }

    fun setStatus(status: String) {
        if (::statusText.isInitialized) {
            statusText.text = status
        }
    }
}
