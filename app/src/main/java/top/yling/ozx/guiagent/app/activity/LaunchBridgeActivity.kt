package top.yling.ozx.guiagent

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * 透明跳板 Activity，用于从后台启动其他应用
 * 解决 Android 10+ 和小米手机的后台启动限制
 */
class LaunchBridgeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LaunchBridgeActivity"
        const val EXTRA_TARGET_INTENT = "target_intent"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "跳板Activity已创建")

        // 获取目标 Intent
        val targetIntent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_TARGET_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_TARGET_INTENT)
        }

        if (targetIntent != null) {
            try {
                // 等待一小段时间，确保当前Activity已经进入前台
                // 这样启动目标应用时，我们就是"前台应用启动其他应用"，不受后台限制
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        Log.d(TAG, "从跳板Activity启动目标应用: ${targetIntent.component}")
                        startActivity(targetIntent)
                        Log.d(TAG, "目标应用启动命令已发送")
                    } catch (e: Exception) {
                        Log.e(TAG, "启动目标应用失败: ${e.message}", e)
                    } finally {
                        // 启动完成后立即关闭跳板Activity
                        finish()
                    }
                }, 100) // 延迟100ms，确保跳板Activity已经进入前台
            } catch (e: Exception) {
                Log.e(TAG, "创建延迟任务失败: ${e.message}", e)
                finish()
            }
        } else {
            Log.e(TAG, "未找到目标 Intent")
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "跳板Activity已进入前台（onResume）")
    }
}
