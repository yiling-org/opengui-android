package top.yling.ozx.guiagent

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import top.yling.ozx.guiagent.databinding.ActivitySettingsBinding
import top.yling.ozx.guiagent.websocket.WebSocketService
import top.yling.ozx.guiagent.util.TokenManager
import top.yling.ozx.guiagent.network.RetrofitClient
import top.yling.ozx.guiagent.model.ApiResponse
import top.yling.ozx.guiagent.model.UpdateProfileRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    companion object {
        private val DEFAULT_WEBSOCKET_URL = BuildConfig.WEBSOCKET_URL
    }

    private lateinit var binding: ActivitySettingsBinding
    private var webSocketService: WebSocketService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as WebSocketService.LocalBinder
            webSocketService = binder.getService()
            serviceBound = true

            webSocketService?.onConnectionStateChanged = { connected ->
                runOnUiThread {
                    updateWebSocketStatus(connected)
                }
            }

            runOnUiThread {
                binding.websocketUrlInput.setText(webSocketService?.getServerUrl())
                updateWebSocketStatus(webSocketService?.isConnected() ?: false)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            webSocketService = null
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startWebSocketService()
        } else {
            Toast.makeText(this, "需要通知权限才能在后台运行", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        setupWindowInsets()
        loadSavedServerUrl()
        setupUI()
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

    override fun onResume() {
        super.onResume()
        updateAccountStatus()

        if (WebSocketService.isRunning) {
            bindToService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
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

        // WebSocket 连接按钮
        binding.connectButton.setOnClickListener {
            val url = binding.websocketUrlInput.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!MyAccessibilityService.isServiceEnabled()) {
                Toast.makeText(this, "请先在高级设置中启用无障碍服务", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            saveServerUrl(url)

            if (WebSocketService.isRunning && serviceBound) {
                webSocketService?.reconnect(url)
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        return@setOnClickListener
                    }
                }
                startWebSocketService()
            }
        }

        // WebSocket 断开按钮
        binding.disconnectButton.setOnClickListener {
            stopWebSocketService()
        }

        // 恢复默认地址按钮
        binding.resetServerUrlButton.setOnClickListener {
            resetToDefaultServerUrl()
        }

        // 注销按钮
        binding.logoutButton.setOnClickListener {
            performLogout()
        }

        // 编辑个人说明按钮
        binding.editProfileButton.setOnClickListener {
            enterProfileEditMode()
        }

        // 保存个人说明按钮
        binding.saveProfileButton.setOnClickListener {
            saveUserProfile()
        }

        // 取消编辑按钮
        binding.cancelProfileButton.setOnClickListener {
            exitProfileEditMode()
        }

        // 高级设置入口
        binding.advancedSettingsCard.setOnClickListener {
            startActivity(Intent(this, AdvancedSettingsActivity::class.java))
        }
    }

    private fun enterProfileEditMode() {
        val savedProfile = TokenManager.getUserProfile(this)
        binding.userProfileInput.setText(savedProfile ?: "")

        binding.userProfileDisplay.visibility = android.view.View.GONE
        binding.editProfileButton.visibility = android.view.View.GONE
        binding.userProfileLayout.visibility = android.view.View.VISIBLE
        binding.profileEditButtonsLayout.visibility = android.view.View.VISIBLE

        binding.userProfileInput.requestFocus()
    }

    private fun exitProfileEditMode() {
        binding.userProfileDisplay.visibility = android.view.View.VISIBLE
        binding.editProfileButton.visibility = android.view.View.VISIBLE
        binding.userProfileLayout.visibility = android.view.View.GONE
        binding.profileEditButtonsLayout.visibility = android.view.View.GONE
    }

    private fun saveUserProfile() {
        val profile = binding.userProfileInput.text.toString().trim()
        val token = TokenManager.getToken(this)

        if (token == null) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        binding.saveProfileButton.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = RetrofitClient.getApiService(this@SettingsActivity)
                val response = apiService.updateProfile(
                    "Bearer $token",
                    UpdateProfileRequest(profile)
                )

                withContext(Dispatchers.Main) {
                    binding.saveProfileButton.isEnabled = true

                    if (response.isSuccessful) {
                        val apiResponse = response.body()
                        if (apiResponse?.isSuccess == true) {
                            TokenManager.saveUserProfile(this@SettingsActivity, profile)
                            updateProfileDisplay(profile)
                            exitProfileEditMode()
                            Toast.makeText(this@SettingsActivity, "个人说明已保存", Toast.LENGTH_SHORT).show()
                        } else {
                            val errorMsg = ApiResponse.getErrorMessage(
                                apiResponse?.code ?: ApiResponse.CODE_INTERNAL_ERROR,
                                apiResponse?.message
                            )
                            Toast.makeText(this@SettingsActivity, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@SettingsActivity, "服务器错误: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.saveProfileButton.isEnabled = true
                    Toast.makeText(this@SettingsActivity, "网络错误: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateProfileDisplay(profile: String?) {
        if (profile.isNullOrEmpty()) {
            binding.userProfileDisplay.text = "点击编辑添加个人说明..."
            binding.userProfileDisplay.alpha = 0.6f
        } else {
            binding.userProfileDisplay.text = profile
            binding.userProfileDisplay.alpha = 0.85f
        }
    }

    private fun startWebSocketService() {
        val intent = Intent(this, WebSocketService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindToService()
    }

    private fun stopWebSocketService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        stopService(Intent(this, WebSocketService::class.java))
        updateWebSocketStatus(false)
    }

    private fun bindToService() {
        val intent = Intent(this, WebSocketService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun updateWebSocketStatus(connected: Boolean) {
        if (connected) {
            binding.wsStatusDot.setBackgroundResource(R.drawable.status_dot_green)
            binding.wsStatusText.text = "已连接"
            binding.wsStatusText.setTextColor(getColor(R.color.success))
            binding.connectButton.isEnabled = false
            binding.disconnectButton.isEnabled = true
        } else {
            binding.wsStatusDot.setBackgroundResource(R.drawable.status_dot_red)
            binding.wsStatusText.text = "未连接"
            binding.wsStatusText.setTextColor(getColor(R.color.error))
            binding.connectButton.isEnabled = true
            binding.disconnectButton.isEnabled = WebSocketService.isRunning
        }
    }

    private fun loadSavedServerUrl() {
        val prefs = getSharedPreferences("websocket_prefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_url", DEFAULT_WEBSOCKET_URL)
        binding.websocketUrlInput.setText(savedUrl)
    }

    private fun saveServerUrl(url: String) {
        val prefs = getSharedPreferences("websocket_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", url).apply()

        val httpUrl = convertWsUrlToHttpUrl(url)
        if (httpUrl != null) {
            RetrofitClient.saveBaseUrl(this, httpUrl)
        }
    }

    private fun convertWsUrlToHttpUrl(wsUrl: String): String? {
        try {
            val trimmedUrl = wsUrl.trim()

            val (httpProtocol, urlWithoutProtocol) = when {
                trimmedUrl.startsWith("wss://", ignoreCase = true) -> "https://" to trimmedUrl.substring(6)
                trimmedUrl.startsWith("ws://", ignoreCase = true) -> "http://" to trimmedUrl.substring(5)
                else -> return null
            }

            val hostPort = urlWithoutProtocol.split("/").firstOrNull() ?: return null

            return "$httpProtocol$hostPort/"
        } catch (e: Exception) {
            return null
        }
    }

    private fun resetToDefaultServerUrl() {
        binding.websocketUrlInput.setText(DEFAULT_WEBSOCKET_URL)
        saveServerUrl(DEFAULT_WEBSOCKET_URL)
        Toast.makeText(this, "已恢复默认服务器地址", Toast.LENGTH_SHORT).show()
    }

    private fun updateAccountStatus() {
        val isLoggedIn = TokenManager.isLoggedIn(this)

        if (isLoggedIn) {
            val nickname = TokenManager.getNickname(this)
            val username = TokenManager.getUsername(this)
            binding.usernameText.text = nickname ?: username ?: "User"
            binding.usernameText.setTextColor(getColor(R.color.text_primary))
            binding.logoutButton.isEnabled = true
            binding.logoutButton.visibility = android.view.View.VISIBLE
            binding.profileHeaderLayout.visibility = android.view.View.VISIBLE
            binding.userProfileDisplay.visibility = android.view.View.VISIBLE
            binding.accountDivider.visibility = android.view.View.VISIBLE
            binding.userProfileLayout.visibility = android.view.View.GONE
            binding.profileEditButtonsLayout.visibility = android.view.View.GONE
        } else {
            binding.usernameText.text = "Not logged in"
            binding.usernameText.setTextColor(getColor(R.color.text_secondary))
            binding.logoutButton.isEnabled = false
            binding.logoutButton.visibility = android.view.View.GONE
            binding.profileHeaderLayout.visibility = android.view.View.GONE
            binding.userProfileDisplay.visibility = android.view.View.GONE
            binding.userProfileLayout.visibility = android.view.View.GONE
            binding.profileEditButtonsLayout.visibility = android.view.View.GONE
            binding.accountDivider.visibility = android.view.View.GONE
        }

        val savedProfile = TokenManager.getUserProfile(this)
        updateProfileDisplay(savedProfile)
    }

    private fun performLogout() {
        TokenManager.clearAuthData(this)
        stopWebSocketService()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

}
