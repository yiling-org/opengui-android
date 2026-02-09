package top.yling.ozx.guiagent.services

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.util.Log

/**
 * 浏览器应用信息
 */
data class BrowserApp(
    val packageName: String,      // 包名
    val activityName: String,     // Activity名称
    val appName: String,          // 应用名称
    val isDefault: Boolean        // 是否为默认浏览器
)

/**
 * 浏览器服务类
 * 提供打开URL、获取浏览器列表、使用指定浏览器打开等功能
 */
class BrowserService(private val context: Context) {

    companion object {
        private const val TAG = "BrowserService"
        
        // 常见搜索引擎
        const val SEARCH_ENGINE_GOOGLE = "https://www.google.com/search?q="
        const val SEARCH_ENGINE_BAIDU = "https://www.baidu.com/s?wd="
        const val SEARCH_ENGINE_BING = "https://www.bing.com/search?q="
        const val SEARCH_ENGINE_SOGOU = "https://www.sogou.com/web?query="
        
        // 常见浏览器包名
        const val BROWSER_CHROME = "com.android.chrome"
        const val BROWSER_FIREFOX = "org.mozilla.firefox"
        const val BROWSER_EDGE = "com.microsoft.emmx"
        const val BROWSER_OPERA = "com.opera.browser"
        const val BROWSER_SAMSUNG = "com.sec.android.app.sbrowser"
        const val BROWSER_UC = "com.UCMobile"
        const val BROWSER_QQ = "com.tencent.mtt"
        const val BROWSER_360 = "com.qihoo.browser"
        const val BROWSER_BAIDU = "com.baidu.browser.apps"
        const val BROWSER_MIUI = "com.android.browser"
    }

    /**
     * 打开URL（使用默认浏览器）
     * @param url 要打开的URL
     * @return 是否成功打开
     */
    fun openUrl(url: String): Boolean {
        if (url.isBlank()) {
            Log.e(TAG, "URL不能为空")
            return false
        }

        val finalUrl = normalizeUrl(url)
        
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "打开URL成功: $finalUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开URL失败: $finalUrl", e)
            false
        }
    }

    /**
     * 使用指定浏览器打开URL
     * @param url 要打开的URL
     * @param packageName 浏览器包名
     * @return 是否成功打开
     */
    fun openUrlWithBrowser(url: String, packageName: String): Boolean {
        if (url.isBlank()) {
            Log.e(TAG, "URL不能为空")
            return false
        }

        val finalUrl = normalizeUrl(url)

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage(packageName)
            }
            
            // 检查是否有应用可以处理这个Intent
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "使用 $packageName 打开URL: $finalUrl")
                true
            } else {
                Log.e(TAG, "浏览器 $packageName 未安装")
                // 回退到默认浏览器
                openUrl(url)
            }
        } catch (e: Exception) {
            Log.e(TAG, "使用指定浏览器打开URL失败", e)
            false
        }
    }

    /**
     * 使用Chrome打开URL
     */
    fun openWithChrome(url: String): Boolean {
        return openUrlWithBrowser(url, BROWSER_CHROME)
    }

    /**
     * 使用Firefox打开URL
     */
    fun openWithFirefox(url: String): Boolean {
        return openUrlWithBrowser(url, BROWSER_FIREFOX)
    }

    /**
     * 使用Edge打开URL
     */
    fun openWithEdge(url: String): Boolean {
        return openUrlWithBrowser(url, BROWSER_EDGE)
    }

    /**
     * 在浏览器中搜索
     * @param query 搜索关键词
     * @param searchEngine 搜索引擎URL前缀（默认百度）
     * @return 是否成功
     */
    fun search(query: String, searchEngine: String = SEARCH_ENGINE_BAIDU): Boolean {
        if (query.isBlank()) {
            Log.e(TAG, "搜索关键词不能为空")
            return false
        }

        val encodedQuery = Uri.encode(query)
        val searchUrl = "$searchEngine$encodedQuery"
        
        return openUrl(searchUrl)
    }

    /**
     * 使用Google搜索
     */
    fun searchWithGoogle(query: String): Boolean {
        return search(query, SEARCH_ENGINE_GOOGLE)
    }

    /**
     * 使用百度搜索
     */
    fun searchWithBaidu(query: String): Boolean {
        return search(query, SEARCH_ENGINE_BAIDU)
    }

    /**
     * 使用Bing搜索
     */
    fun searchWithBing(query: String): Boolean {
        return search(query, SEARCH_ENGINE_BING)
    }

    /**
     * 获取已安装的浏览器列表
     * @return 浏览器应用列表
     */
    fun getInstalledBrowsers(): List<BrowserApp> {
        val browsers = mutableListOf<BrowserApp>()
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
            val resolveInfoList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }

            // 获取默认浏览器
            val defaultBrowser = getDefaultBrowser()

            for (info in resolveInfoList) {
                val packageName = info.activityInfo.packageName
                val activityName = info.activityInfo.name
                val appName = info.loadLabel(context.packageManager).toString()
                
                val browser = BrowserApp(
                    packageName = packageName,
                    activityName = activityName,
                    appName = appName,
                    isDefault = packageName == defaultBrowser
                )
                browsers.add(browser)
            }

            Log.d(TAG, "获取到 ${browsers.size} 个浏览器")
        } catch (e: Exception) {
            Log.e(TAG, "获取浏览器列表失败", e)
        }

        return browsers
    }

    /**
     * 获取默认浏览器包名
     * @return 默认浏览器的包名，如果没有则返回null
     */
    fun getDefaultBrowser(): String? {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
            val resolveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.resolveActivity(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "获取默认浏览器失败", e)
            null
        }
    }

    /**
     * 检查浏览器是否已安装
     * @param packageName 浏览器包名
     * @return 是否已安装
     */
    fun isBrowserInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName, 
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 打开应用商店搜索浏览器
     * @param packageName 要安装的浏览器包名
     * @return 是否成功打开应用商店
     */
    fun openAppStoreForBrowser(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                // 如果没有应用商店，则打开网页版
                val webIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(webIntent)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开应用商店失败", e)
            false
        }
    }

    /**
     * 分享URL到其他应用
     * @param url 要分享的URL
     * @param title 分享标题
     * @return 是否成功
     */
    fun shareUrl(url: String, title: String = "分享链接"): Boolean {
        if (url.isBlank()) {
            Log.e(TAG, "URL不能为空")
            return false
        }

        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra(Intent.EXTRA_SUBJECT, title)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            val chooser = Intent.createChooser(intent, title).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chooser)
            Log.d(TAG, "分享URL: $url")
            true
        } catch (e: Exception) {
            Log.e(TAG, "分享URL失败", e)
            false
        }
    }

    /**
     * 打开指定网站（快捷方法）
     */
    fun openBaidu(): Boolean = openUrl("https://www.baidu.com")
    fun openGoogle(): Boolean = openUrl("https://www.google.com")
    fun openBing(): Boolean = openUrl("https://www.bing.com")
    fun openWeibo(): Boolean = openUrl("https://weibo.com")
    fun openZhihu(): Boolean = openUrl("https://www.zhihu.com")
    fun openBilibili(): Boolean = openUrl("https://www.bilibili.com")
    fun openTaobao(): Boolean = openUrl("https://www.taobao.com")
    fun openJD(): Boolean = openUrl("https://www.jd.com")
    fun openDouyin(): Boolean = openUrl("https://www.douyin.com")

    /**
     * 打开地图位置
     * @param latitude 纬度
     * @param longitude 经度
     * @param label 位置标签（可选）
     * @return 是否成功
     */
    fun openMapLocation(latitude: Double, longitude: Double, label: String? = null): Boolean {
        return try {
            val uri = if (label != null) {
                Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})")
            } else {
                Uri.parse("geo:$latitude,$longitude")
            }
            
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                // 回退到网页地图
                openUrl("https://maps.google.com/maps?q=$latitude,$longitude")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开地图位置失败", e)
            false
        }
    }

    /**
     * 打开地图搜索地址
     * @param address 地址
     * @return 是否成功
     */
    fun searchMapAddress(address: String): Boolean {
        if (address.isBlank()) {
            Log.e(TAG, "地址不能为空")
            return false
        }

        return try {
            val uri = Uri.parse("geo:0,0?q=${Uri.encode(address)}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                // 回退到网页地图
                openUrl("https://maps.google.com/maps?q=${Uri.encode(address)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "搜索地址失败", e)
            false
        }
    }

    /**
     * 规范化URL（自动添加协议前缀）
     */
    private fun normalizeUrl(url: String): String {
        val trimmedUrl = url.trim()
        return when {
            trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://") -> trimmedUrl
            trimmedUrl.startsWith("www.") -> "https://$trimmedUrl"
            trimmedUrl.contains(".") && !trimmedUrl.contains(" ") -> "https://$trimmedUrl"
            else -> trimmedUrl
        }
    }
}

