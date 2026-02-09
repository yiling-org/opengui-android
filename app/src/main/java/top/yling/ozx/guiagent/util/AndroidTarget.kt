package top.yling.ozx.guiagent.util

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

/**
 * Android 版本检查工具类
 * 参考 GKD: li.songe.gkd.util.AndroidTarget
 */
object AndroidTarget {
    /** Android 9+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.P)
    val P = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Android 10+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val Q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Android 11+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    val R = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /** Android 12+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val S = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Android 13+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    val TIRAMISU = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** Android 14+ */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    val UPSIDE_DOWN_CAKE = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Android 16+ (Baklava) */
    @get:ChecksSdkIntAtLeast(api = 36)
    val BAKLAVA = Build.VERSION.SDK_INT >= 36
}

/**
 * 检查类是否存在
 */
private val clazzMap = HashMap<String, Boolean>()
fun checkExistClass(className: String): Boolean = clazzMap[className] ?: try {
    Class.forName(className)
    true
} catch (_: Throwable) {
    false
}.apply {
    clazzMap[className] = this
}

