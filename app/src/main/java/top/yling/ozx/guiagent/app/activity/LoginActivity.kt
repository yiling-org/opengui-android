package top.yling.ozx.guiagent

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import top.yling.ozx.guiagent.databinding.ActivityLoginBinding
import top.yling.ozx.guiagent.model.ApiResponse
import top.yling.ozx.guiagent.model.LoginRequest
import top.yling.ozx.guiagent.network.RetrofitClient
import top.yling.ozx.guiagent.util.DeviceUtils
import top.yling.ozx.guiagent.util.TokenManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if already logged in
        if (TokenManager.isLoggedIn(this)) {
            navigateToMain()
            return
        }

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // 处理系统栏的内边距（避免与状态栏、导航栏重叠）
        setupWindowInsets()

        setupUI()
        playEnterAnimation()
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

    private fun setupUI() {
        // Login button click with animation
        binding.loginButton.setOnClickListener {
            animateButtonClick(it) {
                val username = binding.usernameInput.text.toString().trim()
                val password = binding.passwordInput.text.toString().trim()
                val accessCode = binding.accessCodeInput.text.toString().trim()

                if (validateInput(username, password, accessCode)) {
                    performLogin(username, password, accessCode)
                }
            }
        }

        // Register link click
        binding.registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        // Logo glow animation
        startLogoGlowAnimation()
    }

    private fun playEnterAnimation() {
        // Initial states
        binding.logoGlow.alpha = 0f
        binding.logoGlow.scaleX = 0.5f
        binding.logoGlow.scaleY = 0.5f
        binding.logoIcon.alpha = 0f
        binding.logoIcon.scaleX = 0.5f
        binding.logoIcon.scaleY = 0.5f
        binding.titleText.alpha = 0f
        binding.titleText.translationY = 30f
        binding.sloganText.alpha = 0f
        binding.sloganText.translationY = 20f
        binding.loginCard.alpha = 0f
        binding.loginCard.translationY = 60f
        binding.registerLinkLayout.alpha = 0f

        // Logo animation
        val logoGlowAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.logoGlow, "alpha", 0f, 0.8f),
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleY", 0.5f, 1f)
            )
            duration = 600
            interpolator = OvershootInterpolator(1.2f)
        }

        val logoIconAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.logoIcon, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.logoIcon, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(binding.logoIcon, "scaleY", 0.5f, 1f)
            )
            duration = 500
            startDelay = 150
            interpolator = OvershootInterpolator(1.5f)
        }

        // Title animation
        val titleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.titleText, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.titleText, "translationY", 30f, 0f)
            )
            duration = 400
            startDelay = 300
            interpolator = DecelerateInterpolator()
        }

        val sloganAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.sloganText, "alpha", 0f, 0.7f),
                ObjectAnimator.ofFloat(binding.sloganText, "translationY", 20f, 0f)
            )
            duration = 400
            startDelay = 400
            interpolator = DecelerateInterpolator()
        }

        // Card animation
        val cardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.loginCard, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.loginCard, "translationY", 60f, 0f)
            )
            duration = 500
            startDelay = 500
            interpolator = DecelerateInterpolator()
        }

        // Link animation
        val linkAnim = ObjectAnimator.ofFloat(binding.registerLinkLayout, "alpha", 0f, 1f).apply {
            duration = 400
            startDelay = 700
        }

        // Play all animations
        AnimatorSet().apply {
            playTogether(logoGlowAnim, logoIconAnim, titleAnim, sloganAnim, cardAnim, linkAnim)
            start()
        }
    }

    private fun startLogoGlowAnimation() {
        val pulseAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleX", 1f, 1.15f, 1f),
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleY", 1f, 1.15f, 1f),
                ObjectAnimator.ofFloat(binding.logoGlow, "alpha", 0.8f, 1f, 0.8f)
            )
            duration = 2500
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulseAnim.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (!isFinishing) {
                    pulseAnim.start()
                }
            }
        })
        binding.root.postDelayed({ pulseAnim.start() }, 1000)
    }

    private fun animateButtonClick(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    private fun validateInput(username: String, password: String, accessCode: String): Boolean {
        if (username.isEmpty()) {
            showError("请输入用户名")
            shakeView(binding.usernameInput)
            return false
        }
        if (password.isEmpty()) {
            showError("请输入密码")
            shakeView(binding.passwordInput)
            return false
        }
        if (password.length < 6) {
            showError("密码至少6个字符")
            shakeView(binding.passwordInput)
            return false
        }
        if (accessCode.isEmpty()) {
            showError("请输入准入码")
            shakeView(binding.accessCodeInput)
            return false
        }
        return true
    }

    private fun shakeView(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, -10f, 10f, -10f, 10f, -5f, 5f, 0f).apply {
            duration = 400
            start()
        }
    }

    private fun performLogin(username: String, password: String, accessCode: String) {
        showLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(this@LoginActivity)
                
                // 获取设备ID和设备信息
                val deviceId = DeviceUtils.getDeviceId(this@LoginActivity)
                val deviceInfo = buildDeviceInfo()
                
                android.util.Log.d("LoginActivity", "Login with accessCode: $accessCode, deviceId: $deviceId")
                
                val response = apiService.login(LoginRequest(
                    username = username,
                    password = password,
                    accessCode = accessCode,
                    deviceId = deviceId,
                    deviceInfo = deviceInfo
                ))

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.isSuccess == true) {
                        val authResponse = apiResponse.data
                        if (authResponse?.token != null) {
                            TokenManager.saveAuthData(
                                this@LoginActivity,
                                authResponse.token,
                                authResponse.username ?: username,
                                authResponse.nickname
                            )
                            // 同步保存用户profile
                            authResponse.profile?.let { profile ->
                                TokenManager.saveUserProfile(this@LoginActivity, profile)
                            }
                            Toast.makeText(this@LoginActivity, "登录成功！", Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        } else {
                            showError("登录失败: 未获取到令牌")
                        }
                    } else {
                        // 使用错误码获取用户友好的错误提示
                        val errorMsg = ApiResponse.getErrorMessage(
                            apiResponse?.code ?: ApiResponse.CODE_INTERNAL_ERROR,
                            apiResponse?.message
                        )
                        showError(errorMsg)
                    }
                } else {
                    showError("服务器错误: ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("LoginActivity", "Login error", e)
                showError("网络错误: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    /**
     * 构建设备信息 JSON
     */
    private fun buildDeviceInfo(): String {
        return try {
            val json = JSONObject()
            json.put("manufacturer", android.os.Build.MANUFACTURER)
            json.put("model", android.os.Build.MODEL)
            json.put("brand", android.os.Build.BRAND)
            json.put("device", android.os.Build.DEVICE)
            json.put("sdkVersion", android.os.Build.VERSION.SDK_INT)
            json.put("release", android.os.Build.VERSION.RELEASE)
            json.toString()
        } catch (e: Exception) {
            "{}"
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !show
        binding.usernameInput.isEnabled = !show
        binding.passwordInput.isEnabled = !show
        binding.accessCodeInput.isEnabled = !show
    }

    private fun showError(message: String) {
        binding.errorText.text = message
        binding.errorText.visibility = View.VISIBLE
        binding.errorText.alpha = 0f
        binding.errorText.animate().alpha(1f).setDuration(200).start()
    }

    private fun hideError() {
        binding.errorText.visibility = View.GONE
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
