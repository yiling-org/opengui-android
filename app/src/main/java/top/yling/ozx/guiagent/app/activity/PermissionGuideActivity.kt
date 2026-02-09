package top.yling.ozx.guiagent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import top.yling.ozx.guiagent.databinding.ActivityPermissionGuideBinding
import top.yling.ozx.guiagent.util.PermissionHelper
import top.yling.ozx.guiagent.shizuku.ShizukuApi

/**
 * 权限引导页面
 * 采用步骤式引导，一次显示所有权限状态，用户可以逐个授权
 */
class PermissionGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionGuideBinding

    // 权限项列表
    private val permissionItems = mutableListOf<PermissionItem>()

    // 当前正在请求的权限索引
    private var currentRequestIndex = -1

    // 权限请求回调
    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionStatus(PermissionType.RECORD_AUDIO, granted)
        proceedToNextPermission()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissionStatus(PermissionType.NOTIFICATION, granted)
        proceedToNextPermission()
    }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置页面返回，刷新所有权限状态
        refreshAllPermissionStatus()
        proceedToNextPermission()
    }

    companion object {
        private const val EXTRA_SHOW_SKIP_BUTTON = "show_skip_button"

        /**
         * 启动权限引导页面
         * @param context 上下文
         * @param showSkipButton 是否显示跳过按钮
         */
        fun start(context: Context, showSkipButton: Boolean = true) {
            val intent = Intent(context, PermissionGuideActivity::class.java).apply {
                putExtra(EXTRA_SHOW_SKIP_BUTTON, showSkipButton)
            }
            context.startActivity(intent)
        }

        /**
         * 检查是否有任何必要权限缺失
         */
        fun hasMissingPermissions(context: Context): Boolean {
            // 录音权限
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                return true
            }

            // 悬浮窗权限
            if (!PermissionHelper.hasOverlayPermission(context)) {
                return true
            }

            // 通知权限（Android 13+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    return true
                }
            }

            // 电池优化白名单
            if (!PermissionHelper.isIgnoringBatteryOptimizations(context)) {
                return true
            }

            // 无障碍服务
            if (!isAccessibilityServiceEnabled(context)) {
                return true
            }

            return false
        }

        private fun isAccessibilityServiceEnabled(context: Context): Boolean {
            if (MyAccessibilityService.instance != null) {
                return true
            }

            try {
                val enabledServicesSetting = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

                val packageName = context.packageName
                val fullName = "$packageName/${MyAccessibilityService::class.java.name}"
                val shortName = "$packageName/.${MyAccessibilityService::class.java.simpleName}"

                return enabledServicesSetting.contains(fullName) ||
                       enabledServicesSetting.contains(shortName)
            } catch (e: Exception) {
                return false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPermissionGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        initPermissionItems()
        setupUI()
        refreshAllPermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到页面时刷新权限状态
        refreshAllPermissionStatus()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val systemBarsInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                systemBarsInsets.left,
                systemBarsInsets.top,
                systemBarsInsets.right,
                systemBarsInsets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun initPermissionItems() {
        permissionItems.clear()

        // 1. 录音权限
        permissionItems.add(PermissionItem(
            type = PermissionType.RECORD_AUDIO,
            name = "录音权限",
            description = "用于语音识别和唤醒功能",
            isGranted = false
        ))

        // 2. 悬浮窗权限
        permissionItems.add(PermissionItem(
            type = PermissionType.OVERLAY,
            name = "悬浮窗权限",
            description = "用于后台打开应用和悬浮球功能",
            isGranted = false
        ))

        // 3. 通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionItems.add(PermissionItem(
                type = PermissionType.NOTIFICATION,
                name = "通知权限",
                description = "用于显示任务状态和重要通知",
                isGranted = false
            ))
        }

        // 4. 电池优化白名单
        permissionItems.add(PermissionItem(
            type = PermissionType.BATTERY_OPTIMIZATION,
            name = "电池优化白名单",
            description = "保持后台运行和及时响应指令",
            isGranted = false
        ))

        // 5. 无障碍服务
        permissionItems.add(PermissionItem(
            type = PermissionType.ACCESSIBILITY,
            name = "无障碍服务",
            description = "用于实现自动化操作功能",
            isGranted = false
        ))
    }

    private fun setupUI() {
        val showSkipButton = intent.getBooleanExtra(EXTRA_SHOW_SKIP_BUTTON, true)
        binding.secondaryButton.visibility = if (showSkipButton) View.VISIBLE else View.GONE

        // 构建权限列表视图
        buildPermissionListUI()

        // 主按钮点击
        binding.primaryButton.setOnClickListener {
            startPermissionFlow()
        }

        // 跳过按钮
        binding.secondaryButton.setOnClickListener {
            finish()
        }
    }

    private fun buildPermissionListUI() {
        binding.permissionsList.removeAllViews()

        permissionItems.forEachIndexed { index, item ->
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_permission_row, binding.permissionsList, false)

            val statusIcon = itemView.findViewById<ImageView>(R.id.statusIcon)
            val nameText = itemView.findViewById<TextView>(R.id.permissionName)
            val descText = itemView.findViewById<TextView>(R.id.permissionDescription)
            val actionButton = itemView.findViewById<MaterialButton>(R.id.actionButton)
            val divider = itemView.findViewById<View>(R.id.divider)

            nameText.text = item.name
            descText.text = item.description

            // 最后一项不显示分隔线
            if (index == permissionItems.size - 1) {
                divider.visibility = View.GONE
            }

            // 更新状态
            updateItemUI(itemView, item)

            // 单个权限的授权按钮
            actionButton.setOnClickListener {
                requestSinglePermission(item)
            }

            // 保存 view 引用
            item.view = itemView

            binding.permissionsList.addView(itemView)
        }
    }

    private fun updateItemUI(itemView: View, item: PermissionItem) {
        val statusIcon = itemView.findViewById<ImageView>(R.id.statusIcon)
        val statusBackground = itemView.findViewById<View>(R.id.statusBackground)
        val nameText = itemView.findViewById<TextView>(R.id.permissionName)
        val actionButton = itemView.findViewById<MaterialButton>(R.id.actionButton)

        if (item.isGranted) {
            // 已授权状态
            statusIcon.setImageResource(R.drawable.ic_check_circle)
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.success))
            statusBackground.setBackgroundResource(R.drawable.circle_status_granted)
            nameText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            actionButton.text = "已授权"
            actionButton.isEnabled = false
            actionButton.alpha = 0.5f
            actionButton.setTextColor(ContextCompat.getColor(this, R.color.success))
            actionButton.strokeColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.success)
            )
        } else {
            // 待授权状态
            statusIcon.setImageResource(R.drawable.ic_pending_circle)
            statusIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_tertiary))
            statusBackground.setBackgroundResource(R.drawable.circle_status_pending)
            nameText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            actionButton.text = "授权"
            actionButton.isEnabled = true
            actionButton.alpha = 1f
            actionButton.setTextColor(ContextCompat.getColor(this, R.color.accent_indigo))
            actionButton.strokeColor = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.accent_indigo)
            )
        }
    }

    private fun refreshAllPermissionStatus() {
        permissionItems.forEach { item ->
            item.isGranted = checkPermissionGranted(item.type)
            item.view?.let { updateItemUI(it, item) }
        }

        updateProgressText()
        updatePrimaryButton()
    }

    private fun checkPermissionGranted(type: PermissionType): Boolean {
        return when (type) {
            PermissionType.RECORD_AUDIO -> {
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            }
            PermissionType.OVERLAY -> {
                PermissionHelper.hasOverlayPermission(this)
            }
            PermissionType.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
            }
            PermissionType.BATTERY_OPTIMIZATION -> {
                PermissionHelper.isIgnoringBatteryOptimizations(this)
            }
            PermissionType.ACCESSIBILITY -> {
                isAccessibilityServiceEnabled(this)
            }
            PermissionType.AUTO_START -> {
                // 自启动权限无法准确检测，暂时返回 true
                true
            }
        }
    }

    private fun updateProgressText() {
        val granted = permissionItems.count { it.isGranted }
        val total = permissionItems.size
        binding.progressText.text = "已完成 $granted/$total 项"
    }

    private fun updatePrimaryButton() {
        val allGranted = permissionItems.all { it.isGranted }
        val anyMissing = permissionItems.any { !it.isGranted }

        if (allGranted) {
            binding.primaryButton.text = "完成"
            binding.primaryButton.setOnClickListener {
                finish()
            }
        } else {
            binding.primaryButton.text = "继续设置"
            binding.primaryButton.setOnClickListener {
                startPermissionFlow()
            }
        }
    }

    private fun updatePermissionStatus(type: PermissionType, granted: Boolean) {
        permissionItems.find { it.type == type }?.let { item ->
            item.isGranted = granted
            item.view?.let { updateItemUI(it, item) }
        }
        updateProgressText()
        updatePrimaryButton()
    }

    /**
     * 开始权限授权流程
     * 按顺序请求未授权的权限
     */
    private fun startPermissionFlow() {
        currentRequestIndex = -1
        proceedToNextPermission()
    }

    private fun proceedToNextPermission() {
        // 刷新状态
        refreshAllPermissionStatus()

        // 找到下一个未授权的权限
        currentRequestIndex++
        while (currentRequestIndex < permissionItems.size) {
            val item = permissionItems[currentRequestIndex]
            if (!item.isGranted) {
                requestSinglePermission(item)
                return
            }
            currentRequestIndex++
        }

        // 所有权限都已处理
        val allGranted = permissionItems.all { it.isGranted }
        if (allGranted) {
            finish()
        }
    }

    private fun requestSinglePermission(item: PermissionItem) {
        when (item.type) {
            PermissionType.RECORD_AUDIO -> {
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            PermissionType.NOTIFICATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionType.OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    settingsLauncher.launch(intent)
                }
            }
            PermissionType.BATTERY_OPTIMIZATION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    try {
                        settingsLauncher.launch(intent)
                    } catch (e: Exception) {
                        // 某些设备可能不支持这个 Intent
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        settingsLauncher.launch(fallbackIntent)
                    }
                }
            }
            PermissionType.ACCESSIBILITY -> {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                settingsLauncher.launch(intent)
            }
            PermissionType.AUTO_START -> {
                // 调用 PermissionHelper 的厂商特定处理
                PermissionHelper.requestAutoStartPermissionDirect(this) {
                    refreshAllPermissionStatus()
                    proceedToNextPermission()
                }
            }
        }
    }

    /**
     * 权限类型枚举
     */
    enum class PermissionType {
        RECORD_AUDIO,
        NOTIFICATION,
        OVERLAY,
        BATTERY_OPTIMIZATION,
        ACCESSIBILITY,
        AUTO_START
    }

    /**
     * 权限项数据类
     */
    data class PermissionItem(
        val type: PermissionType,
        val name: String,
        val description: String,
        var isGranted: Boolean,
        var view: View? = null
    )
}
