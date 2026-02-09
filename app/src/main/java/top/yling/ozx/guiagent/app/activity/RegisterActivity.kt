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
import top.yling.ozx.guiagent.databinding.ActivityRegisterBinding
import top.yling.ozx.guiagent.model.ApiResponse
import top.yling.ozx.guiagent.model.RegisterRequest
import top.yling.ozx.guiagent.network.RetrofitClient
import top.yling.ozx.guiagent.util.TokenManager
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityRegisterBinding.inflate(layoutInflater)
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
        // Register button click with animation
        binding.registerButton.setOnClickListener {
            animateButtonClick(it) {
                val username = binding.usernameInput.text.toString().trim()
                val nickname = binding.nicknameInput.text.toString().trim().ifEmpty { null }
                val password = binding.passwordInput.text.toString().trim()
                val confirmPassword = binding.confirmPasswordInput.text.toString().trim()

                if (validateInput(username, password, confirmPassword)) {
                    performRegister(username, password, nickname)
                }
            }
        }

        // Login link click
        binding.loginLink.setOnClickListener {
            finish()
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
        binding.subtitleText.alpha = 0f
        binding.subtitleText.translationY = 20f
        binding.registerCard.alpha = 0f
        binding.registerCard.translationY = 60f
        binding.loginLinkLayout.alpha = 0f

        // Logo animation
        val logoGlowAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.logoGlow, "alpha", 0f, 0.8f),
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleY", 0.5f, 1f)
            )
            duration = 500
            interpolator = OvershootInterpolator(1.2f)
        }

        val logoIconAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.logoIcon, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.logoIcon, "scaleX", 0.5f, 1f),
                ObjectAnimator.ofFloat(binding.logoIcon, "scaleY", 0.5f, 1f)
            )
            duration = 400
            startDelay = 100
            interpolator = OvershootInterpolator(1.5f)
        }

        // Title animation
        val titleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.titleText, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.titleText, "translationY", 30f, 0f)
            )
            duration = 350
            startDelay = 200
            interpolator = DecelerateInterpolator()
        }

        val subtitleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.subtitleText, "alpha", 0f, 0.85f),
                ObjectAnimator.ofFloat(binding.subtitleText, "translationY", 20f, 0f)
            )
            duration = 350
            startDelay = 280
            interpolator = DecelerateInterpolator()
        }

        // Card animation
        val cardAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.registerCard, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(binding.registerCard, "translationY", 60f, 0f)
            )
            duration = 450
            startDelay = 350
            interpolator = DecelerateInterpolator()
        }

        // Link animation
        val linkAnim = ObjectAnimator.ofFloat(binding.loginLinkLayout, "alpha", 0f, 1f).apply {
            duration = 350
            startDelay = 550
        }

        // Play all animations
        AnimatorSet().apply {
            playTogether(logoGlowAnim, logoIconAnim, titleAnim, subtitleAnim, cardAnim, linkAnim)
            start()
        }
    }

    private fun startLogoGlowAnimation() {
        val pulseAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleX", 1f, 1.12f, 1f),
                ObjectAnimator.ofFloat(binding.logoGlow, "scaleY", 1f, 1.12f, 1f),
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
        binding.root.postDelayed({ pulseAnim.start() }, 800)
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

    private fun validateInput(username: String, password: String, confirmPassword: String): Boolean {
        if (username.isEmpty()) {
            showError("Please enter username")
            shakeView(binding.usernameInput)
            return false
        }
        if (username.length < 3) {
            showError("Username must be at least 3 characters")
            shakeView(binding.usernameInput)
            return false
        }
        if (password.isEmpty()) {
            showError("Please enter password")
            shakeView(binding.passwordInput)
            return false
        }
        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            shakeView(binding.passwordInput)
            return false
        }
        if (password != confirmPassword) {
            showError("Passwords do not match")
            shakeView(binding.confirmPasswordInput)
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

    private fun performRegister(username: String, password: String, nickname: String?) {
        showLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                val apiService = RetrofitClient.getApiService(this@RegisterActivity)
                val response = apiService.register(RegisterRequest(username, password, nickname))

                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse?.isSuccess == true) {
                        val authResponse = apiResponse.data
                        if (authResponse?.token != null) {
                            TokenManager.saveAuthData(
                                this@RegisterActivity,
                                authResponse.token,
                                authResponse.username ?: username,
                                authResponse.nickname
                            )
                            // 同步保存用户profile
                            authResponse.profile?.let { profile ->
                                TokenManager.saveUserProfile(this@RegisterActivity, profile)
                            }
                            Toast.makeText(this@RegisterActivity, getString(R.string.app_name_welcome), Toast.LENGTH_SHORT).show()
                            navigateToMain()
                        } else {
                            showError("注册失败: 未获取到令牌")
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
                android.util.Log.e("RegisterActivity", "Registration error", e)
                showError("网络错误: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.registerButton.isEnabled = !show
        binding.usernameInput.isEnabled = !show
        binding.nicknameInput.isEnabled = !show
        binding.passwordInput.isEnabled = !show
        binding.confirmPasswordInput.isEnabled = !show
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

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
