package top.yling.ozx.guiagent.app.activity

import android.app.Activity
import android.os.Bundle
import android.os.Build
import androidx.appcompat.app.AppCompatActivity

/**
 * 全屏提醒 Activity
 * 当系统闹钟不可用时，降级方案使用的提醒界面
 *
 * @author shanwb
 * @date 2026/01/29
 */
class ReminderAlertActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val taskId = intent.getLongExtra("task_id", -1)
        val message = intent.getStringExtra("message") ?: "提醒时间到了"

        // TODO: 实现全屏提醒 UI
        // 可以包含：
        // - 提醒标题和内容
        // - 关闭按钮
        // - 延迟按钮
        // - 铃声播放和震动
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止铃声和震动
    }
}