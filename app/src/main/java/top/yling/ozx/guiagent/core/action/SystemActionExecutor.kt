package top.yling.ozx.guiagent.core.action

import android.accessibilityservice.AccessibilityService
import android.util.Log

/**
 * 系统操作执行器
 * 负责执行系统级操作：Home键、返回键、最近任务、通知栏等
 *
 * @author shanwb
 */
class SystemActionExecutor(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "SystemActionExecutor"
    }

    /**
     * 按下Home键
     * @return 是否执行成功
     */
    fun pressHome(): Boolean {
        Log.d(TAG, "按下Home键")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * 按下返回键
     * @return 是否执行成功
     */
    fun pressBack(): Boolean {
        Log.d(TAG, "按下返回键")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /**
     * 打开最近任务
     * @return 是否执行成功
     */
    fun pressRecents(): Boolean {
        Log.d(TAG, "打开最近任务")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    /**
     * 打开通知栏
     * @return 是否执行成功
     */
    fun openNotifications(): Boolean {
        Log.d(TAG, "打开通知栏")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    /**
     * 打开快速设置
     * @return 是否执行成功
     */
    fun openQuickSettings(): Boolean {
        Log.d(TAG, "打开快速设置")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /**
     * 锁屏
     * @return 是否执行成功
     */
    fun lockScreen(): Boolean {
        Log.d(TAG, "锁屏")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }

    /**
     * 截屏（系统截屏功能）
     * @return 是否执行成功
     */
    fun takeSystemScreenshot(): Boolean {
        Log.d(TAG, "系统截屏")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
    }

    /**
     * 打开电源菜单
     * @return 是否执行成功
     */
    fun openPowerDialog(): Boolean {
        Log.d(TAG, "打开电源菜单")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }
}
