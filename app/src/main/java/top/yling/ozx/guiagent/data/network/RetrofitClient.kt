package top.yling.ozx.guiagent.network

import android.content.Context
import top.yling.ozx.guiagent.BuildConfig
import top.yling.ozx.guiagent.util.TokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit client singleton for API calls
 * @author shanwb
 */
object RetrofitClient {

    // 从 BuildConfig 读取默认 URL（配置在 local.properties）
    private val DEFAULT_BASE_URL: String = BuildConfig.API_BASE_URL
    private const val PREF_NAME = "api_prefs"
    private const val PREF_BASE_URL = "base_url"

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    /**
     * Get base URL from SharedPreferences
     */
    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    /**
     * Save base URL to SharedPreferences
     */
    fun saveBaseUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_BASE_URL, url).apply()
        // Reset retrofit instance to use new URL
        retrofit = null
        apiService = null
    }

    /**
     * Get OkHttpClient with logging and auth interceptor
     * 注意：Release 版本中禁用日志拦截器以保护敏感数据
     */
    private fun getOkHttpClient(context: Context): OkHttpClient {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val token = TokenManager.getToken(context)

            val newRequest = if (token != null && !request.header("Authorization").isNullOrEmpty()) {
                request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                request
            }

            chain.proceed(newRequest)
        }

        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)  // LLM 生成需要较长时间
            .writeTimeout(30, TimeUnit.SECONDS)
            .apply {
                // 仅在 Debug 版本中启用日志拦截器
                if (BuildConfig.DEBUG) {
                    val loggingInterceptor = HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                    addInterceptor(loggingInterceptor)
                }
            }
            .addInterceptor(authInterceptor)
            .build()
    }

    /**
     * Get Retrofit instance
     */
    private fun getRetrofit(context: Context): Retrofit {
        if (retrofit == null) {
            retrofit = Retrofit.Builder()
                .baseUrl(getBaseUrl(context))
                .client(getOkHttpClient(context))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!
    }

    /**
     * Get ApiService instance
     */
    fun getApiService(context: Context): ApiService {
        if (apiService == null) {
            apiService = getRetrofit(context).create(ApiService::class.java)
        }
        return apiService!!
    }

    /**
     * Reset client (call when base URL changes)
     */
    fun reset() {
        retrofit = null
        apiService = null
    }
}
