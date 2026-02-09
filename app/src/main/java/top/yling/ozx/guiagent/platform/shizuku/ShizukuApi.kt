package top.yling.ozx.guiagent.shizuku

import android.content.ComponentName
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import top.yling.ozx.guiagent.util.checkExistClass
import java.lang.reflect.Method

/**
 * Shizuku API 封装
 * 参考 GKD: li.songe.gkd.shizuku.ShizukuApi
 */
object ShizukuApi {
    private const val TAG = "ShizukuApi"

    // Shizuku 授权状态
    private val _shizukuGrantedFlow = MutableStateFlow(false)
    val shizukuGrantedFlow = _shizukuGrantedFlow.asStateFlow()

    // Shizuku 上下文
    private val _shizukuContextFlow = MutableStateFlow<ShizukuContext?>(null)
    val shizukuContextFlow = _shizukuContextFlow.asStateFlow()

    // 当前用户 ID
    val currentUserId by lazy { android.os.Process.myUserHandle().hashCode() }

    /**
     * 初始化 Shizuku
     */
    fun init() {
        Shizuku.addBinderReceivedListener {
            Log.d(TAG, "Shizuku Binder 已接收")
            updateGrantedState()
        }
        Shizuku.addBinderDeadListener {
            Log.d(TAG, "Shizuku Binder 已断开")
            _shizukuGrantedFlow.value = false
            _shizukuContextFlow.value = null
        }
        // 初始检查
        updateGrantedState()
    }

    /**
     * 更新授权状态
     */
    private fun updateGrantedState() {
        try {
            if (Shizuku.pingBinder()) {
                val granted = try {
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                } catch (e: Exception) {
                    false
                }
                _shizukuGrantedFlow.value = granted
                Log.d(TAG, "Shizuku 授权状态: $granted")
            } else {
                _shizukuGrantedFlow.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查 Shizuku 状态失败", e)
            _shizukuGrantedFlow.value = false
        }
    }

    /**
     * 请求 Shizuku 权限
     */
    fun requestPermission(requestCode: Int = 1001) {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku 服务未运行")
            return
        }
        try {
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            } else {
                _shizukuGrantedFlow.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求 Shizuku 权限失败", e)
        }
    }

    /**
     * 处理权限结果
     */
    fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode == 1001) {
            _shizukuGrantedFlow.value = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (_shizukuGrantedFlow.value) {
                Log.d(TAG, "Shizuku 权限已授予")
            } else {
                Log.w(TAG, "Shizuku 权限被拒绝")
            }
        }
    }

    /**
     * 连接 Shizuku 服务
     * 如果已有 context 但 serviceWrapper 为 null，会重新创建完整的 context
     */
    suspend fun connect(): ShizukuContext? = withContext(Dispatchers.IO) {
        if (!_shizukuGrantedFlow.value) {
            Log.w(TAG, "Shizuku 未授权")
            return@withContext null
        }

        try {
            // 检查是否需要补充 serviceWrapper
            val existingContext = _shizukuContextFlow.value
            if (existingContext != null && existingContext.serviceWrapper != null) {
                Log.d(TAG, "已有完整的 Shizuku 上下文，直接返回")
                return@withContext existingContext
            }

            // 创建完整的 context（包含 serviceWrapper）
            Log.d(TAG, "创建完整的 Shizuku 上下文...")
            val context = ShizukuContext(
                serviceWrapper = buildServiceWrapper(),
                inputManager = existingContext?.inputManager ?: SafeInputManager.newBinder(),
                activityManager = existingContext?.activityManager ?: SafeActivityManager.newBinder(),
                activityTaskManager = existingContext?.activityTaskManager ?: SafeActivityTaskManager.newBinder(),
                appOpsService = existingContext?.appOpsService ?: SafeAppOpsService.newBinder(),
                packageManager = existingContext?.packageManager ?: SafePackageManager.newBinder(),
            )
            _shizukuContextFlow.value = context
            Log.d(TAG, "Shizuku 上下文创建成功 (serviceWrapper=${context.serviceWrapper != null})")

            // 自动授予所有必要权限
            context.grantSelf()

            context
        } catch (e: Exception) {
            Log.e(TAG, "连接 Shizuku 服务失败", e)
            null
        }
    }

    /**
     * 获取或连接 Shizuku 上下文
     * 如果已连接且 serviceWrapper 可用直接返回，否则尝试连接
     */
    suspend fun getOrConnect(): ShizukuContext? {
        // 如果已有完整的上下文（serviceWrapper 不为 null），直接返回
        val existingContext = _shizukuContextFlow.value
        if (existingContext != null && existingContext.serviceWrapper != null) {
            return existingContext
        }

        // 如果已授权，尝试连接（会补充 serviceWrapper）
        if (_shizukuGrantedFlow.value) {
            return connect()
        }

        // 检查是否可以授权
        if (isAvailable()) {
            updateGrantedState()
            if (_shizukuGrantedFlow.value) {
                return connect()
            }
        }

        return null
    }

    /**
     * 同步获取 Shizuku 上下文（非挂起版本）
     * 注意：只创建 inputManager 和 activityManager/activityTaskManager，不创建 serviceWrapper（因为后者需要 suspend）
     * 适用于点击等简单操作和获取 topActivity
     */
    fun getContextOrNull(): ShizukuContext? {
        // 如果已有上下文，直接返回
        _shizukuContextFlow.value?.let { return it }

        // 如果已授权但没有上下文，尝试同步连接
        if (_shizukuGrantedFlow.value) {
            return try {
                createContextSync()
            } catch (e: Exception) {
                Log.e(TAG, "同步连接 Shizuku 服务失败", e)
                null
            }
        }

        // 检查是否可以授权
        if (isAvailable()) {
            updateGrantedState()
            if (_shizukuGrantedFlow.value) {
                return try {
                    createContextSync()
                } catch (e: Exception) {
                    Log.e(TAG, "同步连接 Shizuku 服务失败", e)
                    null
                }
            }
        }

        return null
    }
    
    /**
     * 同步创建 Shizuku 上下文（内部方法）
     */
    private fun createContextSync(): ShizukuContext? {
        val inputManager = SafeInputManager.newBinder()
        val activityManager = SafeActivityManager.newBinder()
        val activityTaskManager = SafeActivityTaskManager.newBinder()
        val appOpsService = SafeAppOpsService.newBinder()
        val packageManager = SafePackageManager.newBinder()
        
        if (inputManager != null || activityManager != null || activityTaskManager != null) {
            val context = ShizukuContext(
                serviceWrapper = null,  // serviceWrapper 需要 suspend，这里跳过
                inputManager = inputManager,
                activityManager = activityManager,
                activityTaskManager = activityTaskManager,
                appOpsService = appOpsService,
                packageManager = packageManager,
            )
            _shizukuContextFlow.value = context
            Log.d(TAG, "Shizuku 上下文同步创建成功 (partial)")
            
            // 自动授予所有必要权限
            context.grantSelf()
            
            return context
        } else {
            Log.w(TAG, "所有 Shizuku 服务创建失败")
            return null
        }
    }

    /**
     * 断开 Shizuku 服务
     */
    fun disconnect() {
        _shizukuContextFlow.value?.destroy()
        _shizukuContextFlow.value = null
        Log.d(TAG, "Shizuku 服务已断开")
    }

    /**
     * 检查 Shizuku 是否可用
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 安全调用方法
 */
inline fun <T> safeInvokeMethod(block: () -> T): T? = try {
    block()
} catch (e: IllegalStateException) {
    if (e.message == "binder haven't been received") {
        null
    } else {
        throw e
    }
}

/**
 * 获取系统服务 Binder
 */
fun getStubService(name: String, condition: Boolean): ShizukuBinderWrapper? {
    if (!condition) return null
    val service = SystemServiceHelper.getSystemService(name) ?: return null
    return ShizukuBinderWrapper(service)
}

/**
 * 通过反射调用 AIDL Stub.asInterface 方法
 * 用于避免 hidden_api stub 类的 asInterface 抛出异常
 */
@Suppress("UNCHECKED_CAST")
fun <T> asInterface(stubClassName: String, binder: android.os.IBinder): T? {
    return try {
        val stubClass = Class.forName("$stubClassName\$Stub")
        val method = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
        method.invoke(null, binder) as? T
    } catch (e: Exception) {
        android.util.Log.e("ShizukuApi", "asInterface 反射调用失败: $stubClassName", e)
        null
    }
}

/**
 * 检测隐藏方法
 */
fun Class<*>.detectHiddenMethod(
    methodName: String,
    vararg args: Pair<Int, List<Class<*>>>,
): Int {
    val methodsVal = methods
    methodsVal.forEach { method ->
        if (method.name == methodName) {
            val types = method.parameterTypes.toList()
            args.forEach { (value, argTypes) ->
                if (types == argTypes) {
                    return value
                }
            }
        }
    }
    val result = methodsVal.filter { it.name == methodName }
    if (result.isEmpty()) {
        throw NoSuchMethodException("${name}::${methodName} not found")
    } else {
        throw NoSuchMethodException("${name}::${methodName} not match")
    }
}

private fun Method.simpleString(): String {
    return "${name}(${parameterTypes.joinToString(",") { it.name }}):${returnType.name}"
}

