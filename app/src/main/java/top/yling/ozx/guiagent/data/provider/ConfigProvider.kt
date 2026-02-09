package top.yling.ozx.guiagent.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * ConfigProvider - 配置数据提供者
 *
 * 用于跨进程共享配置数据，如 displayId
 *
 * 其他应用查询方式：
 * ```kotlin
 * val uri = Uri.parse("content://top.yling.ozx.guiagent.provider/config")
 * val cursor = contentResolver.query(uri, null, null, null, null)
 * cursor?.use {
 *     if (it.moveToFirst()) {
 *         val displayId = it.getInt(it.getColumnIndexOrThrow("value"))
 *     }
 * }
 * ```
 */
class ConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "top.yling.ozx.guiagent.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")
        const val KEY_DISPLAY_ID = "display_id"

        private const val CODE_CONFIG = 1

        // 静态存储 displayId，供 Provider 实例访问
        @Volatile
        private var displayId: Int = -1

        /**
         * 更新 displayId（供 MainActivity 等调用）
         */
        fun updateDisplayId(id: Int) {
            displayId = id
            android.util.Log.d("ConfigProvider", "displayId 已更新: $displayId")
        }

        /**
         * 获取当前 displayId
         */
        fun getDisplayId(): Int = displayId

        /**
         * 清除 displayId（设置为 -1）
         */
        fun clearDisplayId() {
            displayId = -1
            android.util.Log.d("ConfigProvider", "displayId 已清除")
        }
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "config", CODE_CONFIG)
    }

    override fun onCreate(): Boolean {
        android.util.Log.d("ConfigProvider", "ConfigProvider 已创建")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            CODE_CONFIG -> {
                val cursor = MatrixCursor(arrayOf("key", "value"))
                cursor.addRow(arrayOf(KEY_DISPLAY_ID, displayId))
                android.util.Log.d("ConfigProvider", "查询 displayId: $displayId")
                cursor
            }
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return when (uriMatcher.match(uri)) {
            CODE_CONFIG -> {
                values?.getAsInteger("value")?.let { newValue ->
                    displayId = newValue
                    android.util.Log.d("ConfigProvider", "通过 insert 更新 displayId: $displayId")
                    // 通知数据变化
                    context?.contentResolver?.notifyChange(uri, null)
                }
                uri
            }
            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return when (uriMatcher.match(uri)) {
            CODE_CONFIG -> {
                values?.getAsInteger("value")?.let { newValue ->
                    displayId = newValue
                    android.util.Log.d("ConfigProvider", "通过 update 更新 displayId: $displayId")
                    // 通知数据变化
                    context?.contentResolver?.notifyChange(uri, null)
                    return 1
                }
                0
            }
            else -> 0
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return when (uriMatcher.match(uri)) {
            CODE_CONFIG -> {
                // delete 操作：清除 displayId（设置为 -1）
                displayId = -1
                android.util.Log.d("ConfigProvider", "通过 delete 清除 displayId")
                context?.contentResolver?.notifyChange(uri, null)
                1
            }
            else -> 0
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_CONFIG -> "vnd.android.cursor.item/vnd.$AUTHORITY.config"
            else -> null
        }
    }
}