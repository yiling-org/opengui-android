package top.yling.ozx.guiagent.app.activity.main

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.launch
import top.yling.ozx.guiagent.databinding.ActivityMainBinding
import top.yling.ozx.guiagent.util.AppSettings
import top.yling.ozx.guiagent.util.VirtualDisplayManager

/**
 * 虚拟屏幕控制器
 * 负责管理虚拟屏幕的创建、销毁和应用启动
 * @author shanwb
 */
class VirtualDisplayController(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val lifecycleScope: LifecycleCoroutineScope
) {
    // 美团包名和主 Activity
    private val meituanPackage = "com.sankuai.meituan"
    private val meituanMainActivity = "com.meituan.android.pt.homepage.activity.MainActivity"

    /**
     * 检查后台运行设置并初始化虚拟屏幕
     */
    fun initIfEnabled() {
        // 检查是否允许后台运行
        if (!AppSettings.isBackgroundRunEnabled(context)) {
            android.util.Log.d("VirtualDisplayController", "后台运行已禁用，不创建虚拟屏幕")
            binding.virtualDisplayCard.visibility = View.GONE
            return
        }

        android.util.Log.d("VirtualDisplayController", "后台运行已启用，初始化虚拟屏幕")

        // 显示虚拟屏幕卡片
        binding.virtualDisplayCard.visibility = View.VISIBLE

        // 更新按钮可见性（根据 debug 模式）
        updateButtonVisibility()

        // 设置按钮点击事件
        setupButtons()

        // 设置全局帧监听器（用于更新 UI）
        VirtualDisplayManager.setGlobalFrameListener { bitmap ->
            binding.virtualDisplayPreview.setImageBitmap(bitmap)
            binding.virtualDisplayPreview.visibility = View.VISIBLE
            binding.virtualDisplayPlaceholder.visibility = View.GONE
        }

        // 创建虚拟屏幕
        val success = VirtualDisplayManager.create(context)
        if (success) {
            val displayId = VirtualDisplayManager.displayId
            updateStatus("已就绪 (ID: $displayId)")
            binding.btnStartMeituanMain.isEnabled = true
            binding.btnStopMeituanMain.isEnabled = true
            Toast.makeText(context, "虚拟屏幕创建成功", Toast.LENGTH_SHORT).show()

            // 将 displayId 存储到 ConfigProvider，供跨进程访问
            saveDisplayIdToSettings(displayId)
        } else {
            updateStatus("创建失败")
            Toast.makeText(context, "创建虚拟屏幕失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换虚拟屏/物理屏模式
     * @param enableVirtualDisplay true-启用虚拟屏模式，false-使用物理屏模式
     */
    fun toggleDisplayMode(enableVirtualDisplay: Boolean) {
        // 保存设置
        AppSettings.setBackgroundRunEnabled(context, enableVirtualDisplay)

        if (enableVirtualDisplay) {
            // 启用虚拟屏模式
            android.util.Log.d("VirtualDisplayController", "切换到虚拟屏模式")

            // 显示虚拟屏幕卡片
            binding.virtualDisplayCard.visibility = View.VISIBLE

            // 更新按钮可见性（根据 debug 模式）
            updateButtonVisibility()

            // 设置按钮点击事件
            setupButtons()

            // 设置全局帧监听器（用于更新 UI）
            VirtualDisplayManager.setGlobalFrameListener { bitmap ->
                binding.virtualDisplayPreview.setImageBitmap(bitmap)
                binding.virtualDisplayPreview.visibility = View.VISIBLE
                binding.virtualDisplayPlaceholder.visibility = View.GONE
            }

            // 创建虚拟屏幕
            val success = VirtualDisplayManager.create(context)
            if (success) {
                val displayId = VirtualDisplayManager.displayId
                updateStatus("已就绪 (ID: $displayId)")
                binding.btnStartMeituanMain.isEnabled = true
                binding.btnStopMeituanMain.isEnabled = true
                Toast.makeText(context, "虚拟屏幕已创建", Toast.LENGTH_SHORT).show()

                // 将 displayId 存储到 ConfigProvider，供跨进程访问
                saveDisplayIdToSettings(displayId)
            } else {
                updateStatus("创建失败")
                Toast.makeText(context, "创建虚拟屏幕失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            // 切换到物理屏模式
            android.util.Log.d("VirtualDisplayController", "切换到物理屏模式")

            // 隐藏虚拟屏幕卡片
            binding.virtualDisplayCard.visibility = View.GONE

            // 释放虚拟屏幕资源
            if (VirtualDisplayManager.isCreated) {
                VirtualDisplayManager.release()
                Toast.makeText(context, "虚拟屏幕已关闭", Toast.LENGTH_SHORT).show()
            }

            // 清除 displayId 设置（设为 -1 表示使用物理屏）
            saveDisplayIdToSettings(-1)
        }
    }

    /**
     * 更新按钮可见性（根据 debug 模式）
     */
    fun updateButtonVisibility() {
        val isDebugModeEnabled = AppSettings.isDebugModeEnabled(context)
        val visibility = if (isDebugModeEnabled) View.VISIBLE else View.GONE
        binding.virtualDisplayButtonsLayout.visibility = visibility

        // 根据 debug 模式更新占位符文本
        if (binding.virtualDisplayPreview.visibility != View.VISIBLE) {
            // 只有当预览图不可见时才显示占位符文本
            val placeholderText = if (isDebugModeEnabled) {
                "点击下方按钮启动美团"
            } else {
                "虚拟屏幕已就绪"
            }
            binding.virtualDisplayPlaceholder.text = placeholderText
        }

        android.util.Log.d("VirtualDisplayController", "Debug 模式: $isDebugModeEnabled, 按钮可见性: $visibility")
    }

    /**
     * 设置虚拟屏幕相关按钮
     */
    private fun setupButtons() {
        binding.btnStartMeituanMain.setOnClickListener {
            startMeituanOnVirtualDisplay()
        }
        binding.btnStopMeituanMain.setOnClickListener {
            stopMeituan()
        }
    }

    /**
     * 更新虚拟屏幕状态文本
     */
    private fun updateStatus(status: String) {
        binding.virtualDisplayStatus.text = status
    }

    /**
     * 在虚拟屏幕上启动美团
     */
    private fun startMeituanOnVirtualDisplay() {
        if (!VirtualDisplayManager.isCreated) {
            Toast.makeText(context, "虚拟屏幕未创建", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            updateStatus("正在启动美团...")

            val result = VirtualDisplayManager.startApp(meituanPackage, meituanMainActivity)

            if (result.success) {
                val displayId = VirtualDisplayManager.displayId
                updateStatus("美团运行中 (Display: $displayId)")
                Toast.makeText(context, "美团启动成功", Toast.LENGTH_SHORT).show()

                // 开始自动刷新（使用全局帧监听器更新 UI）
                VirtualDisplayManager.startAutoRefresh(100L)
            } else {
                updateStatus("启动失败: ${result.error}")
                Toast.makeText(context, "启动失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 关闭美团
     */
    private fun stopMeituan() {
        lifecycleScope.launch {
            updateStatus("正在关闭美团...")

            val result = VirtualDisplayManager.stopApp(meituanPackage)

            if (result.success) {
                updateStatus("美团已关闭")
                Toast.makeText(context, "美团已关闭", Toast.LENGTH_SHORT).show()

                // 停止自动刷新
                VirtualDisplayManager.stopAutoRefresh()

                // 隐藏预览图，显示占位符
                binding.virtualDisplayPreview.visibility = View.GONE
                binding.virtualDisplayPlaceholder.visibility = View.VISIBLE
            } else {
                updateStatus("关闭失败: ${result.error}")
            }
        }
    }

    // ============================================
    // displayId 存储（通过 ConfigProvider 供跨进程访问）
    // ============================================

    /**
     * 将 displayId 存储到 ConfigProvider
     * 其他应用可通过 ContentResolver 查询
     * @param displayId 要存储的 displayId，-1 表示清除
     */
    private fun saveDisplayIdToSettings(displayId: Int) {
        // 更新静态变量（同进程可直接访问）
        top.yling.ozx.guiagent.provider.ConfigProvider.updateDisplayId(displayId)
        android.util.Log.d("VirtualDisplayController", "displayId 已存储到 ConfigProvider: $displayId")
    }

    /**
     * 清理 displayId（设置为 -1）
     */
    fun clearDisplayIdFromSettings() {
        top.yling.ozx.guiagent.provider.ConfigProvider.clearDisplayId()
        android.util.Log.d("VirtualDisplayController", "displayId 已从 ConfigProvider 清理")
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        // 清理 displayId
        if (VirtualDisplayManager.isCreated) {
            clearDisplayIdFromSettings()
        }
        // 释放虚拟屏幕资源
        VirtualDisplayManager.release()
    }
}

