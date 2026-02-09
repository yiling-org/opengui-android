package top.yling.ozx.guiagent.util

import android.content.Context
import android.util.Log
import top.yling.ozx.guiagent.BuildConfig

/**
 * 讯飞配置提供者
 * 统一管理讯飞 SDK 配置的获取，支持多种配置来源
 * 
 * 配置来源优先级：
 * 1. 用户手动配置（最高优先级）- 存储在 EncryptedSharedPreferences
 * 2. BuildConfig 默认值（编译时配置）- 来自 local.properties
 * 
 * @author shanwb
 */
object IFlytekConfigProvider {

    private const val TAG = "IFlytekConfigProvider"

    /**
     * 获取讯飞 App ID
     * 优先使用用户配置，其次使用编译时配置
     */
    fun getAppId(context: Context): String {
        // 1. 检查用户配置
        val userConfig = AppSettings.getIFlytekAppId(context)
        if (userConfig.isNotEmpty()) {
            Log.d(TAG, "使用用户配置的 App ID")
            return userConfig
        }

        // 2. 使用编译时默认值
        val buildConfig = BuildConfig.IFLYTEK_APP_ID
        if (buildConfig.isNotEmpty()) {
            Log.d(TAG, "使用 BuildConfig 中的 App ID")
            return buildConfig
        }

        Log.w(TAG, "未找到 App ID 配置")
        return ""
    }

    /**
     * 获取讯飞 API Key
     * 优先使用用户配置，其次使用编译时配置
     */
    fun getApiKey(context: Context): String {
        // 1. 检查用户配置
        val userConfig = AppSettings.getIFlytekApiKey(context)
        if (userConfig.isNotEmpty()) {
            Log.d(TAG, "使用用户配置的 API Key")
            return userConfig
        }

        // 2. 使用编译时默认值
        val buildConfig = BuildConfig.IFLYTEK_API_KEY
        if (buildConfig.isNotEmpty()) {
            Log.d(TAG, "使用 BuildConfig 中的 API Key")
            return buildConfig
        }

        Log.w(TAG, "未找到 API Key 配置")
        return ""
    }

    /**
     * 获取讯飞 API Secret
     * 优先使用用户配置，其次使用编译时配置
     */
    fun getApiSecret(context: Context): String {
        // 1. 检查用户配置
        val userConfig = AppSettings.getIFlytekApiSecret(context)
        if (userConfig.isNotEmpty()) {
            Log.d(TAG, "使用用户配置的 API Secret")
            return userConfig
        }

        // 2. 使用编译时默认值
        val buildConfig = BuildConfig.IFLYTEK_API_SECRET
        if (buildConfig.isNotEmpty()) {
            Log.d(TAG, "使用 BuildConfig 中的 API Secret")
            return buildConfig
        }

        Log.w(TAG, "未找到 API Secret 配置")
        return ""
    }

    /**
     * 检查讯飞配置是否完整
     * @return true 如果所有必需的配置都已填写（来自任意来源）
     */
    fun isConfigured(context: Context): Boolean {
        val appId = getAppId(context)
        val apiKey = getApiKey(context)
        val apiSecret = getApiSecret(context)
        val configured = appId.isNotEmpty() && apiKey.isNotEmpty() && apiSecret.isNotEmpty()
        Log.d(TAG, "讯飞配置检查: configured=$configured")
        return configured
    }

    /**
     * 检查是否有用户自定义配置
     * @return true 如果用户已在设置中配置了讯飞参数
     */
    fun hasUserConfig(context: Context): Boolean {
        return AppSettings.isIFlytekConfigured(context)
    }

    /**
     * 获取配置来源描述（用于调试）
     */
    fun getConfigSourceDescription(context: Context): String {
        val hasUserConfig = hasUserConfig(context)
        val hasBuildConfig = BuildConfig.IFLYTEK_APP_ID.isNotEmpty()
        
        return when {
            hasUserConfig -> "用户配置"
            hasBuildConfig -> "编译时配置"
            else -> "未配置"
        }
    }

    /**
     * 获取配置状态摘要（用于 UI 显示）
     */
    fun getConfigStatusSummary(context: Context): ConfigStatus {
        val isConfigured = isConfigured(context)
        val hasUserConfig = hasUserConfig(context)
        
        return when {
            hasUserConfig -> ConfigStatus.USER_CONFIGURED
            isConfigured -> ConfigStatus.BUILD_CONFIGURED
            else -> ConfigStatus.NOT_CONFIGURED
        }
    }

    /**
     * 配置状态枚举
     */
    enum class ConfigStatus {
        /** 用户已配置 */
        USER_CONFIGURED,
        /** 使用编译时配置 */
        BUILD_CONFIGURED,
        /** 未配置 */
        NOT_CONFIGURED
    }
}

