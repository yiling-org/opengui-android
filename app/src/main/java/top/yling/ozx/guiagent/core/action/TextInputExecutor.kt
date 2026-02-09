package top.yling.ozx.guiagent.core.action

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 文本输入执行器
 * 负责执行文本输入操作，支持 ACTION_SET_TEXT 和剪贴板粘贴两种方式
 * 包含风控优化：随机延迟模拟人类输入节奏
 *
 * @author shanwb
 */
class TextInputExecutor(
    private val service: AccessibilityService
) {

    companion object {
        private const val TAG = "TextInputExecutor"

        // 输入前延迟范围（模拟定位输入框时间）
        private const val PRE_INPUT_DELAY_MIN = 200L
        private const val PRE_INPUT_DELAY_MAX = 500L

        // 每字符输入延迟范围（模拟打字速度）
        private const val PER_CHAR_DELAY_MIN = 30L
        private const val PER_CHAR_DELAY_MAX = 80L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 输入文本到当前焦点输入框
     * 首先尝试使用 ACTION_SET_TEXT，如果失败则回退到剪贴板粘贴方式
     *
     * 风控优化：添加随机延迟模拟人类输入节奏，降低被检测风险
     *
     * @param text 要输入的文本
     * @return 是否输入成功
     */
    fun type(text: String): Boolean {
        Log.d(TAG, "输入文本: $text")

        // 风控优化：输入前随机延迟，模拟人类定位输入框的时间
        val preInputDelay = (PRE_INPUT_DELAY_MIN..PRE_INPUT_DELAY_MAX).random()
        Log.d(TAG, "输入前延迟: ${preInputDelay}ms")
        Thread.sleep(preInputDelay)

        val rootNode = service.rootInActiveWindow ?: run {
            Log.e(TAG, "无法获取当前窗口")
            return false
        }

        // 查找当前获得焦点的节点
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            rootNode.recycle()
            Log.e(TAG, "未找到获得焦点的输入框")
            return false
        }

        try {
            // 如果节点是可编辑的，首先尝试 ACTION_SET_TEXT
            if (focusedNode.isEditable) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                val setTextResult = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

                if (setTextResult) {
                    Log.d(TAG, "ACTION_SET_TEXT 成功")
                    // 风控优化：根据文本长度添加模拟输入时间
                    simulateTypingDelay(text.length)
                    return true
                }
                Log.w(TAG, "ACTION_SET_TEXT 失败")
            } else {
                Log.w(TAG, "节点不是 isEditable，跳过 ACTION_SET_TEXT")
            }

            // ACTION_SET_TEXT 失败或节点不可编辑，尝试使用剪贴板粘贴方式
            Log.d(TAG, "尝试使用剪贴板粘贴方式")
            val pasteResult = typeViaClipboard(text, focusedNode)

            if (pasteResult) {
                // 风控优化：粘贴成功后也添加模拟延迟
                simulateTypingDelay(text.length)
            }

            return pasteResult
        } finally {
            focusedNode.recycle()
            rootNode.recycle()
        }
    }

    /**
     * 在当前焦点位置执行长按操作（用于触发选择等操作）
     *
     * @param duration 长按时长（毫秒），默认1000ms
     * @param callback 操作完成回调
     */
    fun longPressAtFocus(duration: Long = 1000, callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "在当前焦点位置长按, 时长: ${duration}ms")

        val rootNode = service.rootInActiveWindow ?: run {
            Log.e(TAG, "无法获取当前窗口")
            callback?.invoke(false)
            return
        }

        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            rootNode.recycle()
            Log.e(TAG, "未找到获得焦点的输入框")
            callback?.invoke(false)
            return
        }

        try {
            val longClickResult = focusedNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            if (longClickResult) {
                Log.d(TAG, "焦点位置长按成功")
                Thread.sleep(duration)
            } else {
                Log.e(TAG, "焦点位置长按失败")
            }
            callback?.invoke(longClickResult)
        } finally {
            focusedNode.recycle()
            rootNode.recycle()
        }
    }

    /**
     * 复制指定文本到剪贴板（不依赖焦点节点）
     *
     * @param text 要复制的文本
     * @return 是否复制成功
     */
    fun copyText(text: String): Boolean {
        Log.d(TAG, "复制指定文本到剪贴板: $text")

        return try {
            val success = AtomicBoolean(false)

            mainHandler.post {
                try {
                    val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("text", text)
                    clipboard.setPrimaryClip(clip)
                    success.set(true)
                    Log.d(TAG, "已将文本复制到剪贴板")
                } catch (e: Exception) {
                    Log.e(TAG, "复制到剪贴板失败: ${e.message}")
                }
            }

            // 等待剪贴板操作完成
            Thread.sleep(100)

            success.get()
        } catch (e: Exception) {
            Log.e(TAG, "复制文本失败: ${e.message}")
            false
        }
    }

    /**
     * 通过剪贴板粘贴方式输入文本
     *
     * @param text 要输入的文本
     * @param node 目标输入框节点
     * @return 是否成功
     */
    private fun typeViaClipboard(text: String, node: AccessibilityNodeInfo): Boolean {
        try {
            val clipboardSet = AtomicBoolean(false)

            mainHandler.post {
                try {
                    val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("text", text)
                    clipboard.setPrimaryClip(clip)
                    clipboardSet.set(true)
                    Log.d(TAG, "已将文本复制到剪贴板")
                } catch (e: Exception) {
                    Log.e(TAG, "复制到剪贴板失败: ${e.message}")
                }
            }

            // 等待剪贴板操作完成
            Thread.sleep(100)

            if (clipboardSet.get()) {
                // 同步方式再试一次确保剪贴板已设置
                val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
            }

            // 执行粘贴操作
            val pasteResult = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasteResult) {
                Log.d(TAG, "粘贴成功")
            } else {
                Log.e(TAG, "粘贴失败")
            }
            return pasteResult
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板粘贴方式失败: ${e.message}")
            return false
        }
    }

    /**
     * 模拟打字延迟
     * 根据文本长度添加随机延迟，模拟人类打字速度
     */
    private fun simulateTypingDelay(textLength: Int) {
        val typingDelay = textLength * (PER_CHAR_DELAY_MIN..PER_CHAR_DELAY_MAX).random()
        Log.d(TAG, "模拟输入延迟: ${typingDelay}ms (${textLength}字符)")
        Thread.sleep(typingDelay)
    }
}
