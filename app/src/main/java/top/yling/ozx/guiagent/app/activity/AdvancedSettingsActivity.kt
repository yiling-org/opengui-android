package top.yling.ozx.guiagent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import top.yling.ozx.guiagent.databinding.ActivityAdvancedSettingsBinding
import top.yling.ozx.guiagent.websocket.WebSocketService
import top.yling.ozx.guiagent.util.ImageCompressionConfig
import top.yling.ozx.guiagent.util.PermissionHelper
import top.yling.ozx.guiagent.util.AppSettings
import top.yling.ozx.guiagent.network.RetrofitClient
import top.yling.ozx.guiagent.model.ModelType
import top.yling.ozx.guiagent.shizuku.ShizukuApi
import top.yling.ozx.guiagent.util.IFlytekConfigProvider
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class AdvancedSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedSettingsBinding
    private var webSocketService: WebSocketService? = null
    private var serviceBound = false

    private val logBuilder = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var lastLogUpdateTime = 0L
    private var pendingLogUpdate = false
    private val MAX_LOG_LENGTH = 8000
    private var isActivityVisible = false
    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // Shizuku 权限结果监听器
    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        ShizukuApi.onRequestPermissionResult(requestCode, grantResult)
        runOnUiThread {
            updateShizukuStatus()
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku 授权成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Shizuku 授权被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WebSocketService.LocalBinder
            webSocketService = binder.getService()
            serviceBound = true

            // 设置回调
            webSocketService?.onMessageReceived = { message ->
                runOnUiThread {
                    appendLog("收到: $message")
                }
            }
            webSocketService?.onError = { error ->
                runOnUiThread {
                    appendLog("错误: $error")
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            webSocketService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityAdvancedSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupWindowInsets()
        setupUI()
        updateAccessibilityStatus()
        updateBatteryOptimizationStatus()
        updateOverlayPermissionStatus()
        updateShizukuStatus()

        Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)

        // 检查小米设备
        if (PermissionHelper.isXiaomiDevice()) {
            binding.xiaomiPermissionLayout.visibility = android.view.View.VISIBLE
        } else {
            binding.xiaomiPermissionLayout.visibility = android.view.View.GONE
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            view.setPadding(
                insets.left,
                insets.top,
                insets.right,
                insets.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        updateHandler.removeCallbacksAndMessages(null)
        pendingLogUpdate = false
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        updateAccessibilityStatus()
        updateBatteryOptimizationStatus()
        updateOverlayPermissionStatus()
        updateShizukuStatus()

        if (WebSocketService.isRunning) {
            bindToService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun setupUI() {
        // 返回按钮
        binding.backButton.setOnClickListener {
            finish()
        }

        // 无障碍服务开关
        binding.openAccessibilityButton.setOnClickListener {
            openAccessibilitySettings()
        }

        // 功能测试按钮
        binding.testAccessibilityButton.setOnClickListener {
            startActivity(Intent(this, AccessibilityTestActivity::class.java))
        }

        // 电池优化豁免
        binding.requestBatteryButton.setOnClickListener {
            WebSocketService.requestIgnoreBatteryOptimizations(this)
        }

        // 悬浮窗权限
        binding.requestOverlayButton.setOnClickListener {
            PermissionHelper.requestOverlayPermission(this)
        }

        // 小米设备权限设置
        binding.xiaomiPermissionButton.setOnClickListener {
            PermissionHelper.guideToXiaomiAutoStart(this)
        }

        // Shizuku 授权按钮
        binding.requestShizukuButton.setOnClickListener {
            requestShizukuPermission()
        }

        // 虚拟屏模式开关
        setupVirtualDisplaySwitch()

        // Debug 模式开关
        setupDebugModeSwitch()

        // 图片压缩配置
        setupImageCompressionConfig()

        // 模型配置
        setupLlmConfig()

        // 讯飞语音配置
        setupIFlytekConfig()

        // 清除日志
        binding.clearLogButton.setOnClickListener {
            logBuilder.clear()
            binding.logText.text = "日志已清除"
        }
    }

    /**
     * 设置虚拟屏模式开关（原后台运行开关）
     */
    private fun setupVirtualDisplaySwitch() {
        val isEnabled = AppSettings.isBackgroundRunEnabled(this)
        binding.virtualDisplaySwitch.isChecked = isEnabled

        binding.virtualDisplaySwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setBackgroundRunEnabled(this, isChecked)

            if (isChecked) {
                Toast.makeText(this, "已开启虚拟屏模式", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "已关闭虚拟屏模式，使用物理屏", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 设置 Debug 模式开关
     */
    private fun setupDebugModeSwitch() {
        val isEnabled = AppSettings.isDebugModeEnabled(this)
        binding.debugModeSwitch.isChecked = isEnabled

        binding.debugModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setDebugModeEnabled(this, isChecked)

            if (isChecked) {
                Toast.makeText(this, "已开启 Debug 模式", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "已关闭 Debug 模式", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 设置图片压缩配置
     */
    private fun setupImageCompressionConfig() {
        val savedJpegQuality = ImageCompressionConfig.getJpegQuality(this)
        val savedScaleFactor = ImageCompressionConfig.getScaleFactor(this)

        binding.jpegQualitySlider.value = savedJpegQuality.toFloat()
        binding.jpegQualityValue.text = "${savedJpegQuality}%"

        binding.jpegQualitySlider.addOnChangeListener { _, value, fromUser ->
            val quality = value.toInt()
            binding.jpegQualityValue.text = "${quality}%"
            if (fromUser) {
                ImageCompressionConfig.setJpegQuality(this, quality)
            }
        }

        binding.scaleFactorSlider.value = (savedScaleFactor * 100).toInt().toFloat()
        binding.scaleFactorValue.text = "${(savedScaleFactor * 100).toInt()}%"

        binding.scaleFactorSlider.addOnChangeListener { _, value, fromUser ->
            val scalePercent = value.toInt()
            binding.scaleFactorValue.text = "${scalePercent}%"
            if (fromUser) {
                ImageCompressionConfig.setScaleFactor(this, scalePercent / 100f)
            }
        }

        binding.resetCompressionButton.setOnClickListener {
            ImageCompressionConfig.resetToDefaults(this)

            val defaultQuality = ImageCompressionConfig.DEFAULT_JPEG_QUALITY
            val defaultScale = ImageCompressionConfig.getDefaultScaleFactorForScreen(this)

            binding.jpegQualitySlider.value = defaultQuality.toFloat()
            binding.jpegQualityValue.text = "${defaultQuality}%"

            binding.scaleFactorSlider.value = (defaultScale * 100)
            binding.scaleFactorValue.text = "${(defaultScale * 100).toInt()}%"

            Toast.makeText(this, "已恢复默认值", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindToService() {
        val intent = Intent(this, WebSocketService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = MyAccessibilityService.isServiceEnabled()

        if (isEnabled) {
            binding.accessibilityStatusDot.setBackgroundResource(R.drawable.status_dot_green)
            binding.accessibilityStatusText.text = "无障碍服务: 已启用"
            binding.accessibilityStatusText.setTextColor(getColor(R.color.success))
            binding.openAccessibilityButton.text = "已开启"
            binding.openAccessibilityButton.isEnabled = false
        } else {
            binding.accessibilityStatusDot.setBackgroundResource(R.drawable.status_dot_red)
            binding.accessibilityStatusText.text = "无障碍服务: 未启用"
            binding.accessibilityStatusText.setTextColor(getColor(R.color.error))
            binding.openAccessibilityButton.text = "去开启"
            binding.openAccessibilityButton.isEnabled = true
        }
    }

    private fun updateBatteryOptimizationStatus() {
        val isIgnoring = WebSocketService.isIgnoringBatteryOptimizations(this)

        if (isIgnoring) {
            binding.batteryStatusDot.setBackgroundResource(R.drawable.status_dot_green)
            binding.batteryStatusText.text = "电池优化: 已豁免"
            binding.batteryStatusText.setTextColor(getColor(R.color.success))
            binding.requestBatteryButton.text = "已豁免"
            binding.requestBatteryButton.isEnabled = false
        } else {
            binding.batteryStatusDot.setBackgroundResource(R.drawable.status_dot_red)
            binding.batteryStatusText.text = "电池优化: 未豁免"
            binding.batteryStatusText.setTextColor(getColor(R.color.error))
            binding.requestBatteryButton.text = "申请豁免"
            binding.requestBatteryButton.isEnabled = true
        }
    }

    private fun updateOverlayPermissionStatus() {
        val hasPermission = PermissionHelper.hasOverlayPermission(this)

        if (hasPermission) {
            binding.overlayStatusDot.setBackgroundResource(R.drawable.status_dot_green)
            binding.overlayStatusText.text = "悬浮窗权限: 已授予"
            binding.overlayStatusText.setTextColor(getColor(R.color.success))
            binding.requestOverlayButton.text = "已授予"
            binding.requestOverlayButton.isEnabled = false
        } else {
            binding.overlayStatusDot.setBackgroundResource(R.drawable.status_dot_red)
            binding.overlayStatusText.text = "悬浮窗权限: 未授予"
            binding.overlayStatusText.setTextColor(getColor(R.color.error))
            binding.requestOverlayButton.text = "去授予"
            binding.requestOverlayButton.isEnabled = true
        }
    }

    private fun updateShizukuStatus() {
        val isAvailable = ShizukuApi.isAvailable()
        val isGranted = ShizukuApi.shizukuGrantedFlow.value

        when {
            !isAvailable -> {
                binding.shizukuStatusDot.setBackgroundResource(R.drawable.status_dot_red)
                binding.shizukuStatusText.text = "Shizuku: 未安装/未运行"
                binding.shizukuStatusText.setTextColor(getColor(R.color.error))
                binding.requestShizukuButton.text = "去安装"
                binding.requestShizukuButton.isEnabled = true
            }
            isGranted -> {
                binding.shizukuStatusDot.setBackgroundResource(R.drawable.status_dot_green)
                binding.shizukuStatusText.text = "Shizuku: 已授权"
                binding.shizukuStatusText.setTextColor(getColor(R.color.success))
                binding.requestShizukuButton.text = "已授权"
                binding.requestShizukuButton.isEnabled = false
            }
            else -> {
                binding.shizukuStatusDot.setBackgroundResource(R.drawable.status_dot_red)
                binding.shizukuStatusText.text = "Shizuku: 未授权"
                binding.shizukuStatusText.setTextColor(getColor(R.color.error))
                binding.requestShizukuButton.text = "去授权"
                binding.requestShizukuButton.isEnabled = true
            }
        }
    }

    private fun requestShizukuPermission() {
        if (!ShizukuApi.isAvailable()) {
            Toast.makeText(this, "请先安装并启动 Shizuku 应用", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = android.net.Uri.parse("https://shizuku.rikka.app/")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "请手动搜索并安装 Shizuku", Toast.LENGTH_SHORT).show()
            }
            return
        }

        ShizukuApi.requestPermission()
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

    private fun appendLog(message: String) {
        val timestamp = dateFormat.format(Date())
        logBuilder.append("[$timestamp] $message\n")

        if (logBuilder.length > MAX_LOG_LENGTH) {
            val keepLength = MAX_LOG_LENGTH / 2
            logBuilder.delete(0, logBuilder.length - keepLength)
            logBuilder.insert(0, "... (旧日志已删除)\n")
        }

        if (!isActivityVisible) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastLogUpdateTime > 100 && !pendingLogUpdate) {
            updateLogUI()
            lastLogUpdateTime = now
        } else if (!pendingLogUpdate) {
            pendingLogUpdate = true
            updateHandler.postDelayed({
                updateLogUI()
                pendingLogUpdate = false
                lastLogUpdateTime = System.currentTimeMillis()
            }, 150)
        }
    }

    private fun updateLogUI() {
        binding.logText.text = logBuilder.toString()
        binding.logScrollView.post {
            binding.logScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    // 模型类型列表
    private var modelTypes: List<ModelType> = emptyList()

    /**
     * 设置模型配置
     */
    private fun setupLlmConfig() {
        val isEnabled = AppSettings.isLlmEnabled(this)
        binding.llmEnabledSwitch.isChecked = isEnabled
        binding.llmConfigContent.visibility = if (isEnabled) android.view.View.VISIBLE else android.view.View.GONE

        initLlmTypeSpinnerWithDefault()
        loadModelTypesFromServer()

        binding.llmApiKeyInput.setText(AppSettings.getLlmApiKey(this))
        binding.llmModelKeyInput.setText(AppSettings.getLlmModelKey(this))

        binding.llmEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            AppSettings.setLlmEnabled(this, isChecked)
            binding.llmConfigContent.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE

            if (isChecked) {
                Toast.makeText(this, "已启用自定义模型配置", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "已关闭自定义模型配置", Toast.LENGTH_SHORT).show()
            }
        }

        binding.saveLlmConfigButton.setOnClickListener {
            saveLlmConfig()
        }
    }

    private fun initLlmTypeSpinnerWithDefault() {
        val defaultTypes = arrayOf("DOUBAO_VISION")
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, defaultTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.llmTypeSpinner.adapter = adapter

        val savedType = AppSettings.getLlmType(this)
        val typeIndex = defaultTypes.indexOf(savedType)
        if (typeIndex >= 0) {
            binding.llmTypeSpinner.setSelection(typeIndex)
        }
    }

    private fun loadModelTypesFromServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@AdvancedSettingsActivity)
                val response = apiService.getModelTypes()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.isSuccess == true && apiResponse.data != null) {
                            modelTypes = apiResponse.data
                            updateLlmTypeSpinner()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AdvancedSettings", "Failed to load model types: ${e.message}")
            }
        }
    }

    private fun updateLlmTypeSpinner() {
        if (modelTypes.isEmpty()) return

        val displayNames = modelTypes.map { it.displayName }.toTypedArray()
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, displayNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.llmTypeSpinner.adapter = adapter

        // 添加选择监听，根据模型类型显示/隐藏 Model Key 输入框
        binding.llmTypeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateModelKeyVisibility(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        val savedType = AppSettings.getLlmType(this)
        val typeIndex = modelTypes.indexOfFirst { it.name == savedType }
        if (typeIndex >= 0) {
            binding.llmTypeSpinner.setSelection(typeIndex)
        }
        // 初始化时也更新一次可见性
        updateModelKeyVisibility(if (typeIndex >= 0) typeIndex else 0)
    }

    /**
     * 根据选中的模型类型更新 Model Key 输入框的可见性
     */
    private fun updateModelKeyVisibility(position: Int) {
        val requiresModelKey = if (modelTypes.isNotEmpty() && position in modelTypes.indices) {
            modelTypes[position].requiresModelKey
        } else {
            true // 默认显示（兼容旧版本）
        }
        binding.llmModelKeyLayout.visibility = if (requiresModelKey) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun saveLlmConfig() {
        val selectedIndex = binding.llmTypeSpinner.selectedItemPosition
        val selectedModel = if (modelTypes.isNotEmpty() && selectedIndex in modelTypes.indices) {
            modelTypes[selectedIndex]
        } else {
            null
        }
        val selectedType = selectedModel?.name ?: "DOUBAO_VISION"
        val requiresModelKey = selectedModel?.requiresModelKey ?: true

        val apiKey = binding.llmApiKeyInput.text.toString().trim()
        val modelKey = binding.llmModelKeyInput.text.toString().trim()

        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        if (requiresModelKey && modelKey.isEmpty()) {
            Toast.makeText(this, "请输入 Model Key", Toast.LENGTH_SHORT).show()
            return
        }

        AppSettings.setLlmType(this, selectedType)
        AppSettings.setLlmApiKey(this, apiKey)
        AppSettings.setLlmModelKey(this, if (requiresModelKey) modelKey else "")

        Toast.makeText(this, "模型配置已保存", Toast.LENGTH_SHORT).show()
    }

    // ================== 讯飞语音配置 ==================

    /**
     * 设置讯飞语音配置
     */
    private fun setupIFlytekConfig() {
        // 加载已保存的配置
        binding.iflytekAppIdInput.setText(AppSettings.getIFlytekAppId(this))
        binding.iflytekApiKeyInput.setText(AppSettings.getIFlytekApiKey(this))
        binding.iflytekApiSecretInput.setText(AppSettings.getIFlytekApiSecret(this))

        // 更新状态显示
        updateIFlytekConfigStatus()

        // 配置指引链接
        binding.iflytekGuideLink.setOnClickListener {
            showIFlytekGuideDialog()
        }

        // 保存按钮
        binding.saveIflytekConfigButton.setOnClickListener {
            saveIFlytekConfig()
        }

        // 清除配置按钮
        binding.clearIflytekConfigButton.setOnClickListener {
            clearIFlytekConfig()
        }
    }

    /**
     * 更新讯飞配置状态显示
     */
    private fun updateIFlytekConfigStatus() {
        val configStatus = IFlytekConfigProvider.getConfigStatusSummary(this)

        when (configStatus) {
            IFlytekConfigProvider.ConfigStatus.USER_CONFIGURED -> {
                binding.iflytekStatusDot.setBackgroundResource(R.drawable.status_dot_green)
                binding.iflytekStatusText.text = "已配置（用户自定义）"
                binding.iflytekStatusText.setTextColor(getColor(R.color.success))
            }
            IFlytekConfigProvider.ConfigStatus.BUILD_CONFIGURED -> {
                binding.iflytekStatusDot.setBackgroundResource(R.drawable.status_dot_green)
                binding.iflytekStatusText.text = "已配置（编译时配置）"
                binding.iflytekStatusText.setTextColor(getColor(R.color.success))
            }
            IFlytekConfigProvider.ConfigStatus.NOT_CONFIGURED -> {
                binding.iflytekStatusDot.setBackgroundResource(R.drawable.status_dot_red)
                binding.iflytekStatusText.text = "未配置，语音功能不可用"
                binding.iflytekStatusText.setTextColor(getColor(R.color.error))
            }
        }
    }

    /**
     * 显示讯飞配置指引对话框
     */
    private fun showIFlytekGuideDialog() {
        val message = """
            |获取讯飞语音识别配置步骤：
            |
            |1. 访问讯飞开放平台
            |   https://console.xfyun.cn
            |
            |2. 注册/登录账号
            |
            |3. 创建应用
            |   - 点击「我的应用」→「创建新应用」
            |   - 填写应用名称等信息
            |
            |4. 开通服务
            |   - 进入应用详情
            |   - 开通「语音听写（流式版）」服务
            |   - 免费额度通常足够个人使用
            |
            |5. 获取配置
            |   - 在应用详情页找到：
            |   - APPID（App ID）
            |   - APIKey（API Key）
            |   - APISecret（API Secret）
            |
            |6. 填入上方配置框并保存
        """.trimMargin()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("如何获取讯飞配置")
            .setMessage(message)
            .setPositiveButton("打开讯飞控制台") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://console.xfyun.cn/app/myapp")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法打开浏览器", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /**
     * 保存讯飞配置
     */
    private fun saveIFlytekConfig() {
        val appId = binding.iflytekAppIdInput.text.toString().trim()
        val apiKey = binding.iflytekApiKeyInput.text.toString().trim()
        val apiSecret = binding.iflytekApiSecretInput.text.toString().trim()

        // 验证输入
        if (appId.isEmpty()) {
            Toast.makeText(this, "请输入 App ID", Toast.LENGTH_SHORT).show()
            binding.iflytekAppIdInput.requestFocus()
            return
        }
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
            binding.iflytekApiKeyInput.requestFocus()
            return
        }
        if (apiSecret.isEmpty()) {
            Toast.makeText(this, "请输入 API Secret", Toast.LENGTH_SHORT).show()
            binding.iflytekApiSecretInput.requestFocus()
            return
        }

        // 保存配置
        AppSettings.setIFlytekAppId(this, appId)
        AppSettings.setIFlytekApiKey(this, apiKey)
        AppSettings.setIFlytekApiSecret(this, apiSecret)

        // 更新状态
        updateIFlytekConfigStatus()

        // 尝试重新初始化 SDK
        val app = application as? MyApplication
        val success = app?.reinitializeIFlytekSDK() ?: false

        if (success) {
            Toast.makeText(this, "配置已保存，语音识别已启用", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "配置已保存，重启应用后生效", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 清除讯飞配置
     */
    private fun clearIFlytekConfig() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("清除配置")
            .setMessage("确定要清除讯飞语音配置吗？清除后语音功能将不可用。")
            .setPositiveButton("清除") { _, _ ->
                AppSettings.clearIFlytekConfig(this)
                binding.iflytekAppIdInput.setText("")
                binding.iflytekApiKeyInput.setText("")
                binding.iflytekApiSecretInput.setText("")
                updateIFlytekConfigStatus()
                Toast.makeText(this, "配置已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

}
