package top.yling.ozx.guiagent.model

/**
 * Login request data class
 * 
 * @param username 用户名
 * @param password 密码
 * @param accessCode 准入码（小零 App 登录必填）
 * @param deviceId 设备ID（小零 App 登录必填）
 * @param deviceInfo 设备信息（可选，JSON格式）
 */
data class LoginRequest(
    val username: String,
    val password: String,
    val accessCode: String? = null,
    val deviceId: String? = null,
    val deviceInfo: String? = null
)

/**
 * Register request data class
 */
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String?
)

/**
 * Auth response data class
 */
data class AuthResponse(
    val token: String?,
    val username: String?,
    val nickname: String?,
    val profile: String?
)

/**
 * Update profile request data class
 */
data class UpdateProfileRequest(
    val profile: String
)

/**
 * API response wrapper
 * 对应服务端的 run.mone.common.Result 结构
 *
 * 错误码定义:
 * - 0: 成功
 * - 400: 参数错误
 * - 401: 未授权
 * - 403: 禁止访问
 * - 404: 未找到
 * - 408: 超时
 * - 429: 请求过多
 * - 500: 内部错误
 * - 503: 服务不可用
 */
data class ApiResponse<T>(
    val code: Int = 0,
    val message: String?,
    val data: T?,
    val traceId: String? = null,
    val attributes: Map<String, String>? = null
) {
    /**
     * 判断请求是否成功 (code == 0)
     */
    val isSuccess: Boolean
        get() = code == 0

    companion object {
        const val CODE_SUCCESS = 0
        const val CODE_PARAM_ERROR = 400
        const val CODE_UNAUTHORIZED = 401
        const val CODE_FORBIDDEN = 403
        const val CODE_NOT_FOUND = 404
        const val CODE_TIMEOUT = 408
        const val CODE_TOO_MANY_REQUESTS = 429
        const val CODE_INTERNAL_ERROR = 500
        const val CODE_SERVICE_UNAVAILABLE = 503

        /**
         * 根据错误码获取用户友好的错误提示
         */
        fun getErrorMessage(code: Int, defaultMessage: String?): String {
            return when (code) {
                CODE_PARAM_ERROR -> defaultMessage ?: "请求参数错误"
                CODE_UNAUTHORIZED -> defaultMessage ?: "登录已过期，请重新登录"
                CODE_FORBIDDEN -> defaultMessage ?: "没有访问权限"
                CODE_NOT_FOUND -> defaultMessage ?: "请求的资源不存在"
                CODE_TIMEOUT -> defaultMessage ?: "请求超时，请重试"
                CODE_TOO_MANY_REQUESTS -> defaultMessage ?: "请求过于频繁，请稍后重试"
                CODE_INTERNAL_ERROR -> defaultMessage ?: "服务器内部错误"
                CODE_SERVICE_UNAVAILABLE -> defaultMessage ?: "服务暂时不可用"
                else -> defaultMessage ?: "未知错误 ($code)"
            }
        }
    }
}
