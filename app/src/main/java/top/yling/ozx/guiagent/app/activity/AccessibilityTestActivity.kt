package top.yling.ozx.guiagent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import top.yling.ozx.guiagent.databinding.ActivityAccessibilityTestBinding

class AccessibilityTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccessibilityTestBinding
    private lateinit var notificationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    companion object {
        private const val CHANNEL_ID = "test_notification_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private fun handlePermissionResult(isGranted: Boolean) {
        if (isGranted) {
            sendTestNotification()
        } else {
            Toast.makeText(this, "需要通知权限才能发送通知", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize the launcher before calling super.onCreate()
        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
            ::handlePermissionResult
        )
        super.onCreate(savedInstanceState)
        
        // 启用边到边显示（Edge-to-Edge）
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityAccessibilityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // 处理系统栏的内边距（避免与状态栏、导航栏重叠）
        setupWindowInsets()

        createNotificationChannel()
        setupUI()
    }

    /**
     * 设置窗口内边距，处理系统栏重叠问题
     */
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or 
                WindowInsetsCompat.Type.displayCutout()
            )
            
            // 为根布局设置内边距，避免内容被系统栏遮挡
            view.setPadding(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )
            
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun setupUI() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            finish()
        }

        // 开启无障碍服务
        binding.enableServiceButton.setOnClickListener {
            openAccessibilitySettings()
        }

        // 1. 发送通知测试
        binding.testNotificationButton.setOnClickListener {
            checkAndSendNotification()
        }

        // 2. 点击测试
        binding.testClickButton.setOnClickListener {
            testClick()
        }

        // 3. 滑动测试
        binding.testScrollButton.setOnClickListener {
            testScroll()
        }

        // 4. 按键测试
        binding.testBackButton.setOnClickListener {
            testPressBack()
        }

        binding.testHomeButton.setOnClickListener {
            testPressHome()
        }

        // 5. 系统操作测试
        binding.testNotificationBarButton.setOnClickListener {
            testOpenNotifications()
        }

        binding.testRecentButton.setOnClickListener {
            testOpenRecents()
        }

        // 6. 输入测试
        binding.testTypeButton.setOnClickListener {
            testType()
        }

        // 7. 截图测试
        binding.testScreenshotButton.setOnClickListener {
            testScreenshot()
        }

        // 8. 虚拟屏幕测试
        binding.testVirtualDisplayButton.setOnClickListener {
            openVirtualDisplayTest()
        }
    }

    private fun openVirtualDisplayTest() {
        val intent = Intent(this, VirtualDisplayTestActivity::class.java)
        startActivity(intent)
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = MyAccessibilityService.isServiceEnabled()

        if (isEnabled) {
            binding.statusDot.setBackgroundResource(R.drawable.status_dot_green)
            binding.statusText.text = "无障碍服务已启用"
            binding.statusText.setTextColor(getColor(R.color.success))
            binding.enableServiceButton.visibility = android.view.View.GONE
        } else {
            binding.statusDot.setBackgroundResource(R.drawable.status_dot_red)
            binding.statusText.text = "无障碍服务未启用"
            binding.statusText.setTextColor(getColor(R.color.error))
            binding.enableServiceButton.visibility = android.view.View.VISIBLE
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, getString(R.string.app_name_enable_hint, getString(R.string.app_name)), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 通知测试 ====================

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "测试通知"
            val descriptionText = "用于测试的通知渠道"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndSendNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        sendTestNotification()
    }

    private fun sendTestNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_microphone)
            .setContentTitle("测试通知")
            .setContentText("这是一条测试通知消息 - ${System.currentTimeMillis() % 10000}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(this)) {
            try {
                notify(NOTIFICATION_ID, notification)
                Toast.makeText(this@AccessibilityTestActivity, "通知已发送", Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                Toast.makeText(this@AccessibilityTestActivity, "发送通知失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== 无障碍功能测试 ====================

    private fun checkAccessibilityService(): MyAccessibilityService? {
        val service = MyAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_SHORT).show()
        }
        return service
    }

    private fun testClick() {
        val service = checkAccessibilityService() ?: return

        // 获取屏幕中心坐标
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        Toast.makeText(this, "3秒后点击屏幕中心 ($centerX, $centerY)", Toast.LENGTH_SHORT).show()

        binding.testClickButton.postDelayed({
            service.click(centerX, centerY) { success ->
                runOnUiThread {
                    Toast.makeText(this, if (success) "点击成功" else "点击失败", Toast.LENGTH_SHORT).show()
                }
            }
        }, 3000)
    }

    private fun testScroll() {
        val service = checkAccessibilityService() ?: return

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        Toast.makeText(this, "3秒后向下滑动", Toast.LENGTH_SHORT).show()

        binding.testScrollButton.postDelayed({
            service.scroll(centerX, centerY, "down", 500f) { success ->
                runOnUiThread {
                    Toast.makeText(this, if (success) "滑动成功" else "滑动失败", Toast.LENGTH_SHORT).show()
                }
            }
        }, 3000)
    }

    private fun testPressBack() {
        val service = checkAccessibilityService() ?: return

        Toast.makeText(this, "3秒后按下返回键", Toast.LENGTH_SHORT).show()

        binding.testBackButton.postDelayed({
            val success = service.pressBack()
            // 注意：这个 Toast 可能不会显示，因为返回键会关闭当前 Activity
            if (!success) {
                runOnUiThread {
                    Toast.makeText(this, "返回键按下失败", Toast.LENGTH_SHORT).show()
                }
            }
        }, 3000)
    }

    private fun testPressHome() {
        val service = checkAccessibilityService() ?: return

        Toast.makeText(this, "3秒后按下Home键", Toast.LENGTH_SHORT).show()

        binding.testHomeButton.postDelayed({
            val success = service.pressHome()
            if (!success) {
                runOnUiThread {
                    Toast.makeText(this, "Home键按下失败", Toast.LENGTH_SHORT).show()
                }
            }
        }, 3000)
    }

    private fun testOpenNotifications() {
        val service = checkAccessibilityService() ?: return

        val success = service.openNotifications()
        Toast.makeText(this, if (success) "通知栏已打开" else "打开通知栏失败", Toast.LENGTH_SHORT).show()
    }

    private fun testOpenRecents() {
        val service = checkAccessibilityService() ?: return

        val success = service.pressRecents()
        Toast.makeText(this, if (success) "最近任务已打开" else "打开最近任务失败", Toast.LENGTH_SHORT).show()
    }

    private fun testType() {
        val service = checkAccessibilityService() ?: return

        // 先让输入框获取焦点
        binding.testInputField.requestFocus()

        Toast.makeText(this, "1秒后输入测试文本", Toast.LENGTH_SHORT).show()

        binding.testTypeButton.postDelayed({
            val testText = "Hello 你好 ${System.currentTimeMillis() % 1000}"
            val success = service.type(testText)
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (success) "输入成功: $testText" else "输入失败，请确保输入框已获取焦点",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, 1000)
    }

    private fun testScreenshot() {
        val service = checkAccessibilityService() ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "截图功能需要 Android 9 (API 28) 及以上版本", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "3秒后执行截图", Toast.LENGTH_SHORT).show()

        binding.testScreenshotButton.postDelayed({
            service.takeScreenshot(callback = { success ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        if (success) "截图成功" else "截图失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }, 3000)
    }
}
