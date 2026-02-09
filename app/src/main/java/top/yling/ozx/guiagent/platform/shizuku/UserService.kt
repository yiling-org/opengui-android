package top.yling.ozx.guiagent.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import rikka.shizuku.Shizuku
import java.io.DataOutputStream
import kotlin.coroutines.resume
import kotlin.system.exitProcess

/**
 * Shizuku 用户服务实现
 * 参考 GKD: li.songe.gkd.shizuku.UserService
 */
@Suppress("unused")
class UserService : IUserService.Stub {
    companion object {
        private const val TAG = "UserService"
    }

    /**
     * 默认构造函数（Shizuku 要求）
     */
    constructor() {
        Log.i(TAG, "constructor")
    }

    /**
     * 带 Context 的构造函数
     */
    @Keep
    constructor(context: Context) {
        Log.i(TAG, "constructor with Context: context=$context")
    }

    override fun destroy() {
        Log.i(TAG, "destroy")
        exitProcess(0)
    }

    override fun exit() {
        destroy()
    }

    @OptIn(InternalSerializationApi::class)
    override fun execCommand(command: String): String {
        val process = Runtime.getRuntime().exec("sh")
        val outputStream = DataOutputStream(process.outputStream)
        val commandResult = try {
            command.split('\n').filter { it.isNotBlank() }.forEach {
                outputStream.write(it.toByteArray())
                outputStream.writeBytes('\n'.toString())
                outputStream.flush()
            }
            outputStream.writeBytes("exit\n")
            outputStream.flush()
            CommandResult(
                code = process.waitFor(),
                result = process.inputStream.bufferedReader().readText(),
                error = process.errorStream.bufferedReader().readText(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val message = e.message
            val aimErrStr = "error="
            val index = message?.indexOf(aimErrStr)
            val code = if (index != null) {
                message.substring(index + aimErrStr.length)
                    .takeWhile { c -> c.isDigit() }
                    .toIntOrNull()
            } else {
                null
            } ?: 1
            CommandResult(
                code = code,
                result = "",
                error = e.message,
            )
        } finally {
            outputStream.close()
            process.inputStream.close()
            process.outputStream.close()
            process.destroy()
        }
        return Json.encodeToString(commandResult)
    }
}

@InternalSerializationApi
/**
 * 命令执行结果
 */
@Serializable
data class CommandResult(
    val code: Int?,
    val result: String,
    val error: String?
) {
    val ok: Boolean
        get() = code == 0
}

/**
 * 用户服务包装器
 */
data class UserServiceWrapper(
    val userService: IUserService,
    val connection: ServiceConnection,
    val serviceArgs: Shizuku.UserServiceArgs
) {
    companion object {
        private const val TAG = "UserServiceWrapper"
    }

    fun destroy() {
        try {
            Shizuku.unbindUserService(serviceArgs, connection, false)
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (e: Exception) {
            Log.e(TAG, "解绑服务失败", e)
        }
    }

    @OptIn(InternalSerializationApi::class)
    fun execCommandForResult(command: String): CommandResult = try {
        val resultStr = userService.execCommand(command)
        Json.decodeFromString<CommandResult>(resultStr)
    } catch (e: Throwable) {
        e.printStackTrace()
        CommandResult(code = null, result = "", error = e.message)
    }

    @OptIn(InternalSerializationApi::class)
    fun tap(x: Float, y: Float, duration: Long = 0, displayId: Int = -1): Boolean {
        val displayArg = if (displayId >= 0) "-d $displayId " else ""
        val command = if (duration > 0) {
            "input ${displayArg}swipe $x $y $x $y $duration"
        } else {
            "input ${displayArg}tap $x $y"
        }
        return execCommandForResult(command).ok
    }
}

/**
 * 构建服务包装器
 */
suspend fun buildServiceWrapper(): UserServiceWrapper? {
    val serviceArgs = Shizuku
        .UserServiceArgs(ComponentName("top.yling.ozx.guiagent", UserService::class.java.name))
        .daemon(false)
        .processNameSuffix("shizuku-user-service")
        .debuggable(true)
        .version(1)
        .tag("default")
    
    Log.d("UserService", "buildServiceWrapper: $serviceArgs")
    
    var resumeCallback: ((UserServiceWrapper) -> Unit)? = null
    val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
            Log.d("UserService", "onServiceConnected: $componentName")
            resumeCallback ?: return
            if (binder?.pingBinder() == true) {
                resumeCallback?.invoke(
                    UserServiceWrapper(
                        IUserService.Stub.asInterface(binder),
                        this,
                        serviceArgs
                    )
                )
                resumeCallback = null
            } else {
                Log.d("UserService", "invalid binder for $componentName received")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d("UserService", "onServiceDisconnected: $componentName")
        }
    }
    
    return withTimeoutOrNull(3000) {
        suspendCancellableCoroutine { continuation ->
            resumeCallback = { continuation.resume(it) }
            try {
                Shizuku.bindUserService(serviceArgs, connection)
            } catch (_: Throwable) {
                resumeCallback = null
                continuation.resume(null)
            }
        }
    }.apply {
        if (this == null) {
            try {
                Shizuku.unbindUserService(serviceArgs, connection, false)
                Shizuku.unbindUserService(serviceArgs, connection, true)
            } catch (_: Exception) {}
        }
    }
}

