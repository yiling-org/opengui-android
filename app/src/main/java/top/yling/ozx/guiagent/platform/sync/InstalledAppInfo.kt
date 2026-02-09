package top.yling.ozx.guiagent.sync

import kotlinx.serialization.Serializable

/**
 * 已安装应用信息数据类
 * 用于收集和传输设备上已安装的应用信息
 */
@Serializable
data class InstalledAppInfo(
    /** 应用包名 */
    val packageName: String,

    /** 应用显示名称 */
    val appName: String,

    /** 版本名称（如 "8.0.50"） */
    val versionName: String?,

    /** 版本代码（如 3560） */
    val versionCode: Long,

    /** 应用安装时间（毫秒时间戳） */
    val installedTime: Long,

    /** 应用最后更新时间（毫秒时间戳） */
    val lastUpdatedTime: Long
) {
    /**
     * 转换为 Map 格式，用于 WebSocket 消息发送
     */
    fun toMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "appName" to appName,
        "versionName" to versionName,
        "versionCode" to versionCode,
        "installedTime" to installedTime,
        "lastUpdatedTime" to lastUpdatedTime
    )
}
