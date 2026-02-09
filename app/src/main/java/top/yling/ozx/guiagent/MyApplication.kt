package top.yling.ozx.guiagent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Environment
import android.util.Log
import com.iflytek.sparkchain.core.SparkChain
import com.iflytek.sparkchain.core.SparkChainConfig
import com.iflytek.aikit.core.AiHelper
import com.iflytek.aikit.core.BaseLibrary
import com.iflytek.aikit.core.CoreListener
import com.iflytek.aikit.core.ErrType
import com.iflytek.aikit.core.LogLvl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import top.yling.ozx.guiagent.shizuku.PermissionGranter
import top.yling.ozx.guiagent.shizuku.ShizukuApi
import top.yling.ozx.guiagent.util.IFlytekConfigProvider
import java.io.File

/**
 * 授权状态监听器接口
 */
interface AuthStateListener {
    /**
     * 授权状态变化回调
     * @param authorized true 表示授权成功，false 表示授权失败
     * @param errorCode 错误码，授权成功时为 0
     */
    fun onAuthStateChanged(authorized: Boolean, errorCode: Int)
}

class MyApplication : Application() {

    // Application 级别的协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "MyApplication"

        // 应用实例引用（用于获取 Context）
        @Volatile
        private var instance: MyApplication? = null
        
        /**
         * 获取应用实例
         */
        fun getInstance(): MyApplication? = instance
        
        // 工作目录缓存（避免重复计算）
        @Volatile
        private var cachedWorkDir: String? = null
        
        /**
         * 获取 AIKit SDK 工作目录（单例模式，避免重复计算）
         * @param context Application 上下文
         * @return 工作目录路径（以 / 结尾）
         */
        fun getAIKitWorkDir(context: Application): String {
            if (cachedWorkDir != null) {
                return cachedWorkDir!!
            }
            
            synchronized(MyApplication::class.java) {
                if (cachedWorkDir != null) {
                    return cachedWorkDir!!
                }
                
                val workDir = getAIKitWorkDirInternal(context)
                cachedWorkDir = workDir
                return workDir
            }
        }
        
        /**
         * 内部方法：获取工作目录
         * 优先使用外部存储，如果不可用则使用应用私有目录
         */
        private fun getAIKitWorkDirInternal(context: Application): String {
            // 尝试使用外部存储
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                // Android 10 及以下，使用传统外部存储
                val externalDir = Environment.getExternalStorageDirectory()
                if (externalDir != null && externalDir.canWrite()) {
                    val workDir = File(externalDir, "iflytek")
                    if (!workDir.exists()) {
                        workDir.mkdirs()
                    }
                    if (workDir.exists() && workDir.canWrite()) {
                        return workDir.absolutePath + File.separator
                    }
                }
            }
            
            // 备选方案：使用应用私有目录
            val privateDir = File(context.filesDir, "iflytek")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }
            return privateDir.absolutePath + File.separator
        }
        
        // 通知渠道
        const val CHANNEL_ID_DEFAULT = "default"
        
        // AIKit SDK 授权状态
        @Volatile
        var aikitAuthResult: Int = -1
            private set
        
        fun setAikitAuthResult(result: Int) {
            aikitAuthResult = result
        }
        
        // 授权状态监听器列表
        private val authStateListeners = mutableListOf<AuthStateListener>()
        
        /**
         * 注册授权状态监听器
         */
        fun registerAuthStateListener(listener: AuthStateListener) {
            synchronized(authStateListeners) {
                if (!authStateListeners.contains(listener)) {
                    authStateListeners.add(listener)
                    // 如果已经授权，立即通知
                    if (aikitAuthResult == 0) {
                        listener.onAuthStateChanged(true, 0)
                    } else if (aikitAuthResult != -1) {
                        listener.onAuthStateChanged(false, aikitAuthResult)
                    }
                }
            }
        }
        
        /**
         * 取消注册授权状态监听器
         */
        fun unregisterAuthStateListener(listener: AuthStateListener) {
            synchronized(authStateListeners) {
                authStateListeners.remove(listener)
            }
        }
        
        /**
         * 通知所有监听器授权状态变化
         */
        private fun notifyAuthStateChanged(authorized: Boolean, errorCode: Int) {
            synchronized(authStateListeners) {
                authStateListeners.forEach { listener ->
                    try {
                        listener.onAuthStateChanged(authorized, errorCode)
                    } catch (e: Exception) {
                        Log.e(TAG, "通知授权状态监听器异常: ${e.message}", e)
                    }
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // 保存实例引用
        instance = this

        // 绕过 Android 隐藏 API 限制（Android 9+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
            Log.d(TAG, "HiddenApiBypass 已启用")
        }

        // 创建通知渠道
        createNotificationChannels()

        // 初始化 Shizuku API
        try {
            ShizukuApi.init()
            Log.d(TAG, "Shizuku API 初始化成功")

            // 在后台自动授予权限（如果 Shizuku 可用）
            applicationScope.launch {
                PermissionGranter.autoGrantOnStartup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku API 初始化异常: ${e.message}", e)
        }

        // 初始化讯飞 SparkChain SDK（支持用户配置和编译时配置）
        initIFlytekSDK()
        
        // 初始化 AIKit SDK（用于离线唤醒功能 - 已禁用）
        // initAIKitSDK()
    }
    
    /**
     * 初始化讯飞 SparkChain SDK
     * 支持用户在设置中配置，也支持编译时配置
     */
    private fun initIFlytekSDK() {
        val configStatus = IFlytekConfigProvider.getConfigStatusSummary(this)
        Log.d(TAG, "讯飞配置状态: $configStatus")
        
        if (!IFlytekConfigProvider.isConfigured(this)) {
            Log.i(TAG, "讯飞 SDK 未配置，跳过初始化。")
            Log.i(TAG, "如需使用语音功能，请在设置中配置讯飞参数，或在 local.properties 中配置 IFLYTEK_* 参数")
            return
        }
        
        try {
            val appId = IFlytekConfigProvider.getAppId(this)
            val apiKey = IFlytekConfigProvider.getApiKey(this)
            val apiSecret = IFlytekConfigProvider.getApiSecret(this)
            
            val config = SparkChainConfig.builder()
                .appID(appId)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .logLevel(1)  // 0:VERBOSE, 1:DEBUG, 2:INFO

            val ret = SparkChain.getInst().init(this, config)

            if (ret == 0) {
                Log.d(TAG, "讯飞 SparkChain SDK 初始化成功 (配置来源: ${IFlytekConfigProvider.getConfigSourceDescription(this)})")
            } else {
                Log.e(TAG, "讯飞 SparkChain SDK 初始化失败，错误码: $ret")
            }
        } catch (e: Exception) {
            Log.e(TAG, "讯飞 SparkChain SDK 初始化异常: ${e.message}", e)
        }
    }
    
    /**
     * 重新初始化讯飞 SDK（用于用户更新配置后）
     * @return true 如果初始化成功
     */
    fun reinitializeIFlytekSDK(): Boolean {
        Log.d(TAG, "重新初始化讯飞 SDK...")
        
        if (!IFlytekConfigProvider.isConfigured(this)) {
            Log.w(TAG, "讯飞配置不完整，无法初始化")
            return false
        }
        
        return try {
            val appId = IFlytekConfigProvider.getAppId(this)
            val apiKey = IFlytekConfigProvider.getApiKey(this)
            val apiSecret = IFlytekConfigProvider.getApiSecret(this)
            
            val config = SparkChainConfig.builder()
                .appID(appId)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .logLevel(1)

            val ret = SparkChain.getInst().init(this, config)

            if (ret == 0) {
                Log.d(TAG, "讯飞 SparkChain SDK 重新初始化成功")
                true
            } else {
                Log.e(TAG, "讯飞 SparkChain SDK 重新初始化失败，错误码: $ret")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "讯飞 SparkChain SDK 重新初始化异常: ${e.message}", e)
            false
        }
    }
    
    /**
     * 初始化 AIKit SDK（新版语音唤醒）
     * 参考文档：https://www.xfyun.cn/doc/asr/AIkit_awaken/Android-SDK.html
     * 
     * 新版本授权说明：
     * - 支持设备授权和应用授权两种方式
     * - 在线激活：首次使用时需要联网，SDK初始化时获取授权license激活
     * - 设备激活后，即可在无网环境下使用
     * - 如果恢复出厂设置或清空应用缓存，将license清除后，需要重新联网激活
     */
    private fun initAIKitSDK() {
        try {
            // 获取工作目录（自动选择可用目录）
            val workDirPath = getAIKitWorkDir(this)
            val workDir = File(workDirPath)
            
            // 确保目录存在
            if (!workDir.exists()) {
                val created = workDir.mkdirs()
                if (!created) {
                    Log.e(TAG, "无法创建工作目录: $workDirPath")
                    return
                }
            }
            
            // 检查目录权限
            val canRead = workDir.canRead()
            val canWrite = workDir.canWrite()
            Log.d(TAG, "工作目录: $workDirPath")
            Log.d(TAG, "工作目录权限检查: 可读=$canRead, 可写=$canWrite")
            
            if (!canRead || !canWrite) {
                Log.e(TAG, "工作目录无读写权限: $workDirPath")
                Log.e(TAG, "请检查存储权限是否已授予")
            }
            
            // 设置日志目录
            val logDir = File(workDir, "aikit")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, "aeeLog.txt").absolutePath
            AiHelper.getInst().setLogInfo(LogLvl.VERBOSE, 1, logFile)
            Log.d(TAG, "日志文件路径: $logFile")
            
            // 复制唤醒资源文件到工作目录（如果不存在）
            copyIVWResources(workDirPath)
            
            // 获取配置
            val appId = IFlytekConfigProvider.getAppId(this)
            val apiKey = IFlytekConfigProvider.getApiKey(this)
            val apiSecret = IFlytekConfigProvider.getApiSecret(this)
            
            // 设定初始化参数
            val params = BaseLibrary.Params.builder()
                .appId(appId)
                .apiKey(apiKey)
                .apiSecret(apiSecret)
                .workDir(workDirPath)
                .build()
            
            // 验证配置参数
            Log.d(TAG, "AIKit SDK 配置（新版语音唤醒）:")
            Log.d(TAG, "  AppID: $appId")
            Log.d(TAG, "  APIKey: ${apiKey.take(10)}...")
            Log.d(TAG, "  APISecret: ${apiSecret.take(10)}...")
            Log.d(TAG, "  WorkDir: $workDirPath")
            Log.d(TAG, "  授权方式: 在线激活（首次使用需要联网）")
            
            // 注册 SDK 初始化状态监听
            AiHelper.getInst().registerListener(coreListener)
            
            // 在后台线程初始化 SDK（新版本支持在线激活）
            Thread {
                try {
                    Log.d(TAG, "开始初始化 AIKit SDK（新版）...")
                    Log.d(TAG, "提示：首次使用需要联网以激活授权 license")
                    AiHelper.getInst().initEntry(applicationContext, params)
                    Log.d(TAG, "AIKit SDK 初始化请求已发送")
                } catch (e: Exception) {
                    Log.e(TAG, "AIKit SDK 初始化异常: ${e.message}", e)
                    e.printStackTrace()
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "AIKit SDK 初始化准备异常: ${e.message}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * AIKit SDK 核心监听器
     * 参考 demo: MainActivity.coreListener
     */
    private val coreListener = object : CoreListener {
        override fun onAuthStateChange(type: ErrType, code: Int) {
            Log.i(TAG, "AIKit SDK 状态变化: type=$type, code=$code")
            
            when (type) {
                ErrType.AUTH -> {
                    aikitAuthResult = code
                    if (code == 0) {
                        Log.d(TAG, "AIKit SDK 授权成功（新版语音唤醒）")
                        Log.d(TAG, "授权 license 已缓存，可在无网环境下使用")
                        // 通知所有监听器授权成功
                        notifyAuthStateChanged(true, 0)
                    } else {
                        val errorMsg = getAuthErrorDescription(code)
                        Log.e(TAG, "AIKit SDK 授权失败，授权码: $code")
                        Log.e(TAG, "错误说明: $errorMsg")
                        
                        // 新版本授权失败提示
                        if (code == 10118) {
                            Log.e(TAG, "=== 新版本授权提示 ===")
                            Log.e(TAG, "应用未开通相应服务，请在讯飞控制台开通'离线语音唤醒（新版）'服务")
                            Log.e(TAG, "控制台地址: https://console.xfyun.cn/app/myapp")
                        } else if (code == 10119) {
                            Log.e(TAG, "=== 新版本授权提示 ===")
                            Log.e(TAG, "首次使用需要联网以激活授权 license")
                            Log.e(TAG, "请确保设备已连接网络，然后重启应用")
                        }
                        
                        // 通知所有监听器授权失败
                        notifyAuthStateChanged(false, code)
                        
                        // 输出详细的排查建议
                        when (code) {
                            18405 -> {
                                val currentWorkDir = cachedWorkDir ?: "未初始化"
                                Log.e(TAG, "=== 排查建议 ===")
                                Log.e(TAG, "1. 检查工作目录是否存在: $currentWorkDir")
                                Log.e(TAG, "2. 检查存储权限是否已授予（READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE）")
                                Log.e(TAG, "3. 在 Android 11+ 上，会自动使用应用私有目录")
                                Log.e(TAG, "4. 检查目录是否有读写权限")
                            }
                            18400 -> {
                                Log.e(TAG, "=== 排查建议 ===")
                                Log.e(TAG, "工作目录无读写权限，请检查存储权限")
                            }
                            10114 -> {
                                Log.e(TAG, "=== 排查建议 ===")
                                Log.e(TAG, "AppID 配置错误，请检查 MyApplication.IFLYTEK_APP_ID")
                            }
                            10115 -> {
                                Log.e(TAG, "=== 排查建议 ===")
                                Log.e(TAG, "APIKey 配置错误，请检查 MyApplication.IFLYTEK_API_KEY")
                            }
                            10116 -> {
                                Log.e(TAG, "=== 排查建议 ===")
                                Log.e(TAG, "APISecret 配置错误，请检查 MyApplication.IFLYTEK_API_SECRET")
                            }
                        }
                    }
                }
                ErrType.HTTP -> {
                    Log.w(TAG, "AIKit SDK HTTP 认证结果: $code")
                    if (code != 0) {
                        Log.e(TAG, "HTTP 认证失败，请检查网络连接和 API 配置")
                    }
                }
                else -> {
                    Log.w(TAG, "AIKit SDK 其他错误: type=$type, code=$code")
                }
            }
        }
    }
    
    /**
     * 获取授权错误码的描述
     */
    private fun getAuthErrorDescription(code: Int): String {
        return when (code) {
            0 -> "授权成功"
            18400 -> "工作目录无读写权限"
            18405 -> "工作目录不存在"
            10114 -> "AppID 配置错误"
            10115 -> "APIKey 配置错误"
            10116 -> "APISecret 配置错误"
            10117 -> "AppID 与 APIKey/APISecret 不匹配"
            10118 -> "应用未开通相应服务，请在讯飞控制台开通"
            10119 -> "网络错误，请检查网络连接"
            else -> "未知错误码: $code"
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // 逆初始化 AIKit SDK
        try {
            AiHelper.getInst().unInit()
            Log.d(TAG, "AIKit SDK 已逆初始化")
        } catch (e: Exception) {
            Log.e(TAG, "AIKit SDK 逆初始化异常: ${e.message}", e)
        }
    }
    
    /**
     * 复制唤醒资源文件到工作目录
     * 资源文件应放在 app/src/main/assets/ivw/ 目录中（作为静态资源）
     * 应用启动时自动从 assets 复制到工作目录
     */
    private fun copyIVWResources(workDirPath: String) {
        try {
            val workDir = File(workDirPath)
            val ivwDir = File(workDir, "ivw")
            if (!ivwDir.exists()) {
                ivwDir.mkdirs()
            }
            
            // 需要复制的资源文件列表（这些文件应放在 app/src/main/assets/ivw/ 目录）
            val resourceFiles = listOf(
                "IVW_FILLER_1",
                "IVW_GRAM_1", 
                "IVW_KEYWORD_1",
                "IVW_MLP_1"
            )
            
            var allCopied = true
            var copiedCount = 0
            
            for (fileName in resourceFiles) {
                val targetFile = File(ivwDir, fileName)
                
                // 如果目标文件已存在，跳过（避免重复复制）
                if (targetFile.exists()) {
                    Log.d(TAG, "资源文件已存在: $fileName")
                    continue
                }
                
                // 从 assets 目录读取并复制
                try {
                    val inputStream = assets.open("ivw/$fileName")
                    val outputStream = java.io.FileOutputStream(targetFile)
                    
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    copiedCount++
                    Log.d(TAG, "已复制资源文件: $fileName -> ${targetFile.absolutePath}")
                } catch (e: java.io.FileNotFoundException) {
                    allCopied = false
                    Log.e(TAG, "缺少资源文件: $fileName")
                    Log.e(TAG, "请将文件放在: app/src/main/assets/ivw/$fileName")
                } catch (e: Exception) {
                    allCopied = false
                    Log.e(TAG, "复制资源文件失败: $fileName, 错误: ${e.message}", e)
                }
            }
            
            if (allCopied && copiedCount == 0) {
                Log.d(TAG, "所有唤醒资源文件已存在，无需复制")
            } else if (allCopied) {
                Log.d(TAG, "成功复制 $copiedCount 个资源文件到工作目录")
            } else {
                Log.e(TAG, "=== 重要提示 ===")
                Log.e(TAG, "唤醒功能需要资源文件，请执行以下步骤：")
                Log.e(TAG, "1. 将以下文件放入 app/src/main/assets/ivw/ 目录：")
                resourceFiles.forEach { 
                    Log.e(TAG, "   - $it")
                }
                Log.e(TAG, "2. 这些文件可以从 SDK 包的 resource/ivw/ 目录获取")
                Log.e(TAG, "3. 文件会作为静态资源打包到 APK 中")
                Log.e(TAG, "4. 应用启动时会自动复制到工作目录: $workDirPath/ivw/")
            }
        } catch (e: Exception) {
            Log.e(TAG, "复制唤醒资源文件异常: ${e.message}", e)
        }
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // 默认通知渠道
            val defaultChannel = NotificationChannel(
                CHANNEL_ID_DEFAULT,
                "默认通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "应用默认通知"
                enableVibration(false)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(defaultChannel)
            
            Log.d(TAG, "通知渠道创建成功")
        }
    }
}
