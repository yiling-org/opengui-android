package top.yling.ozx.guiagent.data.api

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import top.yling.ozx.guiagent.data.model.TaskApiResultResponse
import top.yling.ozx.guiagent.data.model.TaskWithStepsDTO
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 任务 API 客户端
 * 封装与服务端的 HTTP 通信
 *
 * @author shanwb
 */
object TaskApiClient {
    private const val TAG = "TaskApiClient"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * 获取 API 基础 URL
     * 从 WebSocket URL 转换为 HTTP URL
     *
     * @param context Android Context
     * @return API 基础 URL，如果未配置则返回空字符串
     */
    fun getApiBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences("websocket_prefs", Context.MODE_PRIVATE)
        val wsUrl = prefs.getString("server_url", "") ?: ""

        if (wsUrl.isEmpty()) {
            return ""
        }

        return try {
            val httpUrl = wsUrl
                .replace("wss://", "https://")
                .replace("ws://", "http://")

            val uri = java.net.URI(httpUrl)
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$port"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse URL: $wsUrl", e)
            ""
        }
    }

    /**
     * 获取当前任务
     *
     * @param context Android Context
     * @param androidId 设备 Android ID
     * @param onSuccess 成功回调，参数为任务数据（可能为 null 表示无任务）
     * @param onError 错误回调
     */
    fun getCurrentTask(
        context: Context,
        androidId: String,
        onSuccess: (TaskWithStepsDTO?) -> Unit,
        onError: (String) -> Unit
    ) {
        val baseUrl = getApiBaseUrl(context)

        if (baseUrl.isEmpty()) {
            Log.d(TAG, "API base URL 未配置")
            onSuccess(null)
            return
        }

        val url = "$baseUrl/api/tasks/android/$androidId/current"
        Log.d(TAG, "获取当前任务: $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "获取当前任务失败", e)
                onError(e.message ?: "网络请求失败")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.code == 404) {
                        Log.d(TAG, "没有当前任务")
                        onSuccess(null)
                        return
                    }

                    if (!response.isSuccessful) {
                        Log.e(TAG, "获取当前任务失败: ${response.code}")
                        onError("HTTP ${response.code}")
                        return
                    }

                    val body = response.body?.string()
                    if (body.isNullOrEmpty()) {
                        onSuccess(null)
                        return
                    }

                    try {
                        val apiResponse = gson.fromJson(body, TaskApiResultResponse::class.java)

                        if (apiResponse.code == 404) {
                            Log.d(TAG, "没有当前任务(code=404)")
                            onSuccess(null)
                            return
                        }

                        if (apiResponse.isSuccess && apiResponse.data != null) {
                            onSuccess(apiResponse.data)
                        } else {
                            Log.w(TAG, "获取当前任务失败: code=${apiResponse.code}, message=${apiResponse.message}")
                            onSuccess(null)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析任务数据失败", e)
                        onError("解析失败: ${e.message}")
                    }
                }
            }
        })
    }

    /**
     * 判断任务是否处于运行中状态
     *
     * @param status 任务状态字符串
     * @return true 表示运行中
     */
    fun isTaskRunning(status: String?): Boolean {
        return when (status) {
            "PENDING", "RUNNING", "PROCESSING", "EXECUTING" -> true
            else -> false
        }
    }

    /**
     * 释放资源
     * 应在应用退出时调用
     */
    fun shutdown() {
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }
}
