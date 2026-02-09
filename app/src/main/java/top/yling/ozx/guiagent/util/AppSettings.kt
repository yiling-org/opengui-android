package top.yling.ozx.guiagent.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 应用全局设置管理
 * 用于存储和读取应用级别的配置项
 * @author shanwb
 */
object AppSettings {

    private const val TAG = "AppSettings"
    private const val PREF_NAME = "app_settings"
    private const val SECURE_PREF_NAME = "app_settings_secure"
    private const val KEY_BACKGROUND_RUN_ENABLED = "background_run_enabled"
    private const val KEY_DEBUG_MODE_ENABLED = "debug_mode_enabled"

    // 模型配置相关
    private const val KEY_LLM_ENABLED = "llm_enabled"
    private const val KEY_LLM_TYPE = "llm_type"
    private const val KEY_LLM_API_KEY = "llm_api_key"
    private const val KEY_LLM_MODEL_KEY = "llm_model_key"

    // 讯飞语音配置相关
    private const val KEY_IFLYTEK_APP_ID = "iflytek_app_id"
    private const val KEY_IFLYTEK_API_KEY = "iflytek_api_key"
    private const val KEY_IFLYTEK_API_SECRET = "iflytek_api_secret"

    // 缓存加密 SharedPreferences 实例
    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    /**
     * 获取加密的 SharedPreferences 实例
     * 用于存储敏感数据如 API Key
     */
    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: createEncryptedPrefs(context).also { encryptedPrefs = it }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                SECURE_PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences, fallback to normal", e)
            // 降级到普通 SharedPreferences（不推荐，仅作为最后手段）
            context.getSharedPreferences(SECURE_PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 设置是否允许后台运行
     */
    fun setBackgroundRunEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BACKGROUND_RUN_ENABLED, enabled).apply()
    }

    /**
     * 获取是否允许后台运行（虚拟屏模式）
     * 默认值为 false（使用物理屏模式）
     */
    fun isBackgroundRunEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_BACKGROUND_RUN_ENABLED, false)
    }

    /**
     * 设置是否启用 Debug 模式
     */
    fun setDebugModeEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEBUG_MODE_ENABLED, enabled).apply()
    }

    /**
     * 获取是否启用 Debug 模式
     * 默认值为 false（Debug 模式关闭）
     */
    fun isDebugModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEBUG_MODE_ENABLED, false)
    }

    // ================== 模型配置相关方法 ==================

    /**
     * 设置是否启用自定义模型配置
     */
    fun setLlmEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LLM_ENABLED, enabled).apply()
    }

    /**
     * 获取是否启用自定义模型配置
     * 默认值为 false
     */
    fun isLlmEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_LLM_ENABLED, false)
    }

    /**
     * 设置模型类型（如：DOUBAO_VISION）
     */
    fun setLlmType(context: Context, type: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LLM_TYPE, type).apply()
    }

    /**
     * 获取模型类型
     * 默认值为 "DOUBAO_VISION"
     */
    fun getLlmType(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LLM_TYPE, "DOUBAO_VISION") ?: "DOUBAO_VISION"
    }

    /**
     * 设置 API Key（使用加密存储）
     */
    fun setLlmApiKey(context: Context, apiKey: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(KEY_LLM_API_KEY, apiKey).apply()
        // 迁移：清除旧的明文存储
        migrateApiKeyIfNeeded(context)
    }

    /**
     * 获取 API Key（从加密存储读取）
     */
    fun getLlmApiKey(context: Context): String {
        // 先尝试迁移旧数据
        migrateApiKeyIfNeeded(context)
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_LLM_API_KEY, "") ?: ""
    }

    /**
     * 迁移旧的明文 API Key 到加密存储
     */
    private fun migrateApiKeyIfNeeded(context: Context) {
        val oldPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val oldApiKey = oldPrefs.getString(KEY_LLM_API_KEY, null)
        if (!oldApiKey.isNullOrEmpty()) {
            Log.i(TAG, "Migrating API Key from plain storage to encrypted storage")
            val encryptedPrefs = getEncryptedPrefs(context)
            // 只有当加密存储中没有值时才迁移
            if (encryptedPrefs.getString(KEY_LLM_API_KEY, "").isNullOrEmpty()) {
                encryptedPrefs.edit().putString(KEY_LLM_API_KEY, oldApiKey).apply()
            }
            // 清除明文存储
            oldPrefs.edit().remove(KEY_LLM_API_KEY).apply()
        }
    }

    /**
     * 设置 Model Key（模型标识）
     */
    fun setLlmModelKey(context: Context, modelKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LLM_MODEL_KEY, modelKey).apply()
    }

    /**
     * 获取 Model Key
     */
    fun getLlmModelKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LLM_MODEL_KEY, "") ?: ""
    }

    /**
     * 获取 LLM 配置 Map（用于 WebSocket 传递）
     * 只有在启用自定义配置且 API Key 不为空时才返回配置
     */
    fun getLlmConfigMap(context: Context): Map<String, String>? {
        if (!isLlmEnabled(context)) {
            return null
        }
        val apiKey = getLlmApiKey(context)
        if (apiKey.isEmpty()) {
            return null
        }
        return mapOf(
            "llmName" to getLlmType(context),
            "llmApiKey" to apiKey,
            "llmModelKey" to getLlmModelKey(context)
        )
    }

    // ================== 讯飞语音配置相关方法 ==================

    /**
     * 设置讯飞 App ID（使用加密存储）
     */
    fun setIFlytekAppId(context: Context, appId: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(KEY_IFLYTEK_APP_ID, appId).apply()
    }

    /**
     * 获取讯飞 App ID
     */
    fun getIFlytekAppId(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_IFLYTEK_APP_ID, "") ?: ""
    }

    /**
     * 设置讯飞 API Key（使用加密存储）
     */
    fun setIFlytekApiKey(context: Context, apiKey: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(KEY_IFLYTEK_API_KEY, apiKey).apply()
    }

    /**
     * 获取讯飞 API Key
     */
    fun getIFlytekApiKey(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_IFLYTEK_API_KEY, "") ?: ""
    }

    /**
     * 设置讯飞 API Secret（使用加密存储）
     */
    fun setIFlytekApiSecret(context: Context, apiSecret: String) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit().putString(KEY_IFLYTEK_API_SECRET, apiSecret).apply()
    }

    /**
     * 获取讯飞 API Secret
     */
    fun getIFlytekApiSecret(context: Context): String {
        val prefs = getEncryptedPrefs(context)
        return prefs.getString(KEY_IFLYTEK_API_SECRET, "") ?: ""
    }

    /**
     * 检查讯飞配置是否完整
     * @return true 如果所有必需的配置都已填写
     */
    fun isIFlytekConfigured(context: Context): Boolean {
        val appId = getIFlytekAppId(context)
        val apiKey = getIFlytekApiKey(context)
        val apiSecret = getIFlytekApiSecret(context)
        return appId.isNotEmpty() && apiKey.isNotEmpty() && apiSecret.isNotEmpty()
    }

    /**
     * 清除讯飞配置
     */
    fun clearIFlytekConfig(context: Context) {
        val prefs = getEncryptedPrefs(context)
        prefs.edit()
            .remove(KEY_IFLYTEK_APP_ID)
            .remove(KEY_IFLYTEK_API_KEY)
            .remove(KEY_IFLYTEK_API_SECRET)
            .apply()
    }
}
