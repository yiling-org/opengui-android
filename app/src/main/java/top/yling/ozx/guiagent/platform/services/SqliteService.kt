package top.yling.ozx.guiagent.services

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 查询结果数据类
 */
data class QueryResult(
    val success: Boolean,           // 是否成功
    val message: String,            // 结果消息
    val rowCount: Int = 0,          // 影响/返回的行数
    val lastInsertId: Long = -1,    // 最后插入的ID（仅insert操作）
    val data: List<Map<String, Any?>> = emptyList()  // 查询结果数据
) {
    /**
     * 转换为JSON字符串
     */
    fun toJson(): String {
        val json = JSONObject()
        json.put("success", success)
        json.put("message", message)
        json.put("rowCount", rowCount)
        json.put("lastInsertId", lastInsertId)
        
        val dataArray = JSONArray()
        data.forEach { row ->
            val rowJson = JSONObject()
            row.forEach { (key, value) ->
                rowJson.put(key, value ?: JSONObject.NULL)
            }
            dataArray.put(rowJson)
        }
        json.put("data", dataArray)
        
        return json.toString()
    }
}

/**
 * SQLite 数据库服务类
 * 提供对 SQLite 数据库的增删改查操作
 */
class SqliteService(private val context: Context) {

    companion object {
        private const val TAG = "SqliteService"
        private const val DEFAULT_DB_NAME = "app_database.db"
        private const val DEFAULT_DB_VERSION = 1
    }

    private var database: SQLiteDatabase? = null
    private var dbHelper: DatabaseHelper? = null
    private var currentDbName: String = DEFAULT_DB_NAME

    /**
     * 数据库帮助类
     */
    private inner class DatabaseHelper(
        context: Context,
        dbName: String,
        version: Int
    ) : SQLiteOpenHelper(context, dbName, null, version) {

        override fun onCreate(db: SQLiteDatabase) {
            Log.d(TAG, "数据库创建: $currentDbName")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Log.d(TAG, "数据库升级: $oldVersion -> $newVersion")
        }
    }

    /**
     * 打开或创建数据库
     * @param dbName 数据库名称（不带路径，将存储在应用私有目录）
     * @param version 数据库版本号
     * @return 是否成功
     */
    fun openDatabase(dbName: String = DEFAULT_DB_NAME, version: Int = DEFAULT_DB_VERSION): Boolean {
        return try {
            closeDatabase()
            currentDbName = dbName
            dbHelper = DatabaseHelper(context, dbName, version)
            database = dbHelper?.writableDatabase
            Log.d(TAG, "数据库打开成功: $dbName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "打开数据库失败: $dbName", e)
            false
        }
    }

    /**
     * 关闭数据库
     */
    fun closeDatabase() {
        try {
            database?.close()
            dbHelper?.close()
            database = null
            dbHelper = null
            Log.d(TAG, "数据库已关闭")
        } catch (e: Exception) {
            Log.e(TAG, "关闭数据库失败", e)
        }
    }

    /**
     * 确保数据库已打开
     */
    private fun ensureDatabaseOpen(): SQLiteDatabase {
        if (database == null || !database!!.isOpen) {
            openDatabase(currentDbName)
        }
        return database ?: throw IllegalStateException("数据库未打开")
    }

    // ==================== 表操作 ====================

    /**
     * 创建表
     * @param tableName 表名
     * @param columns 列定义，例如: "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, age INTEGER"
     * @return 操作结果
     */
    fun createTable(tableName: String, columns: String): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            val sql = "CREATE TABLE IF NOT EXISTS $tableName ($columns)"
            db.execSQL(sql)
            Log.d(TAG, "表创建成功: $tableName")
            QueryResult(true, "表 $tableName 创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "创建表失败: $tableName", e)
            QueryResult(false, "创建表失败: ${e.message}")
        }
    }

    /**
     * 删除表
     * @param tableName 表名
     * @return 操作结果
     */
    fun dropTable(tableName: String): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            val sql = "DROP TABLE IF EXISTS $tableName"
            db.execSQL(sql)
            Log.d(TAG, "表删除成功: $tableName")
            QueryResult(true, "表 $tableName 删除成功")
        } catch (e: Exception) {
            Log.e(TAG, "删除表失败: $tableName", e)
            QueryResult(false, "删除表失败: ${e.message}")
        }
    }

    /**
     * 检查表是否存在
     * @param tableName 表名
     * @return 是否存在
     */
    fun tableExists(tableName: String): Boolean {
        return try {
            val db = ensureDatabaseOpen()
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf(tableName)
            )
            val exists = cursor.count > 0
            cursor.close()
            exists
        } catch (e: Exception) {
            Log.e(TAG, "检查表存在失败: $tableName", e)
            false
        }
    }

    /**
     * 获取所有表名
     * @return 表名列表
     */
    fun getAllTables(): List<String> {
        val tables = mutableListOf<String>()
        try {
            val db = ensureDatabaseOpen()
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                null
            )
            while (cursor.moveToNext()) {
                tables.add(cursor.getString(0))
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "获取表列表失败", e)
        }
        return tables
    }

    /**
     * 获取表结构
     * @param tableName 表名
     * @return 列信息列表
     */
    fun getTableSchema(tableName: String): List<Map<String, String>> {
        val schema = mutableListOf<Map<String, String>>()
        try {
            val db = ensureDatabaseOpen()
            val cursor = db.rawQuery("PRAGMA table_info($tableName)", null)
            while (cursor.moveToNext()) {
                val column = mapOf(
                    "cid" to cursor.getString(0),
                    "name" to cursor.getString(1),
                    "type" to cursor.getString(2),
                    "notnull" to cursor.getString(3),
                    "dflt_value" to (cursor.getString(4) ?: ""),
                    "pk" to cursor.getString(5)
                )
                schema.add(column)
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e(TAG, "获取表结构失败: $tableName", e)
        }
        return schema
    }

    // ==================== 增删改查操作 ====================

    /**
     * 插入数据
     * @param tableName 表名
     * @param values 键值对数据
     * @return 操作结果（包含插入的ID）
     */
    fun insert(tableName: String, values: Map<String, Any?>): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            val contentValues = mapToContentValues(values)
            val id = db.insert(tableName, null, contentValues)
            
            if (id != -1L) {
                Log.d(TAG, "插入成功: $tableName, ID=$id")
                QueryResult(true, "插入成功", rowCount = 1, lastInsertId = id)
            } else {
                Log.e(TAG, "插入失败: $tableName")
                QueryResult(false, "插入失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "插入数据失败: $tableName", e)
            QueryResult(false, "插入失败: ${e.message}")
        }
    }

    /**
     * 批量插入数据
     * @param tableName 表名
     * @param valuesList 多条数据
     * @return 操作结果
     */
    fun insertBatch(tableName: String, valuesList: List<Map<String, Any?>>): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            var successCount = 0
            
            db.beginTransaction()
            try {
                valuesList.forEach { values ->
                    val contentValues = mapToContentValues(values)
                    val id = db.insert(tableName, null, contentValues)
                    if (id != -1L) successCount++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            
            Log.d(TAG, "批量插入完成: $tableName, 成功=$successCount/${valuesList.size}")
            QueryResult(true, "批量插入完成", rowCount = successCount)
        } catch (e: Exception) {
            Log.e(TAG, "批量插入失败: $tableName", e)
            QueryResult(false, "批量插入失败: ${e.message}")
        }
    }

    /**
     * 更新数据
     * @param tableName 表名
     * @param values 要更新的键值对
     * @param whereClause WHERE条件（不含WHERE关键字），例如: "id = ?"
     * @param whereArgs 条件参数
     * @return 操作结果
     */
    fun update(
        tableName: String,
        values: Map<String, Any?>,
        whereClause: String? = null,
        whereArgs: Array<String>? = null
    ): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            val contentValues = mapToContentValues(values)
            val rowsAffected = db.update(tableName, contentValues, whereClause, whereArgs)
            
            Log.d(TAG, "更新完成: $tableName, 影响行数=$rowsAffected")
            QueryResult(true, "更新成功", rowCount = rowsAffected)
        } catch (e: Exception) {
            Log.e(TAG, "更新数据失败: $tableName", e)
            QueryResult(false, "更新失败: ${e.message}")
        }
    }

    /**
     * 删除数据
     * @param tableName 表名
     * @param whereClause WHERE条件（不含WHERE关键字），例如: "id = ?"
     * @param whereArgs 条件参数
     * @return 操作结果
     */
    fun delete(
        tableName: String,
        whereClause: String? = null,
        whereArgs: Array<String>? = null
    ): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            val rowsAffected = db.delete(tableName, whereClause, whereArgs)
            
            Log.d(TAG, "删除完成: $tableName, 影响行数=$rowsAffected")
            QueryResult(true, "删除成功", rowCount = rowsAffected)
        } catch (e: Exception) {
            Log.e(TAG, "删除数据失败: $tableName", e)
            QueryResult(false, "删除失败: ${e.message}")
        }
    }

    /**
     * 查询数据
     * @param tableName 表名
     * @param columns 要查询的列名数组，null表示所有列
     * @param selection WHERE条件（不含WHERE关键字）
     * @param selectionArgs 条件参数
     * @param groupBy GROUP BY子句
     * @param having HAVING子句
     * @param orderBy ORDER BY子句
     * @param limit LIMIT子句（如 "10" 或 "10 OFFSET 5"）
     * @return 查询结果
     */
    fun query(
        tableName: String,
        columns: Array<String>? = null,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        groupBy: String? = null,
        having: String? = null,
        orderBy: String? = null,
        limit: String? = null
    ): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            val cursor = db.query(
                tableName,
                columns,
                selection,
                selectionArgs,
                groupBy,
                having,
                orderBy,
                limit
            )
            
            val data = cursorToList(cursor)
            cursor.close()
            
            Log.d(TAG, "查询完成: $tableName, 返回行数=${data.size}")
            QueryResult(true, "查询成功", rowCount = data.size, data = data)
        } catch (e: Exception) {
            Log.e(TAG, "查询数据失败: $tableName", e)
            QueryResult(false, "查询失败: ${e.message}")
        }
    }

    /**
     * 查询单条数据
     * @param tableName 表名
     * @param selection WHERE条件
     * @param selectionArgs 条件参数
     * @return 单条数据，不存在则返回null
     */
    fun queryOne(
        tableName: String,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Map<String, Any?>? {
        val result = query(tableName, null, selection, selectionArgs, null, null, null, "1")
        return if (result.success && result.data.isNotEmpty()) {
            result.data[0]
        } else {
            null
        }
    }

    /**
     * 查询记录数
     * @param tableName 表名
     * @param selection WHERE条件
     * @param selectionArgs 条件参数
     * @return 记录数
     */
    fun count(
        tableName: String,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): Long {
        return try {
            val db = ensureDatabaseOpen()
            val sql = buildString {
                append("SELECT COUNT(*) FROM $tableName")
                if (!selection.isNullOrBlank()) {
                    append(" WHERE $selection")
                }
            }
            val cursor = db.rawQuery(sql, selectionArgs)
            val count = if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            cursor.close()
            count
        } catch (e: Exception) {
            Log.e(TAG, "查询记录数失败: $tableName", e)
            0L
        }
    }

    // ==================== 原始SQL操作 ====================

    /**
     * 执行原始SQL查询（SELECT）
     * @param sql SQL语句
     * @param selectionArgs 参数数组
     * @return 查询结果
     */
    fun rawQuery(sql: String, selectionArgs: Array<String>? = null): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            val cursor = db.rawQuery(sql, selectionArgs)
            val data = cursorToList(cursor)
            cursor.close()
            
            Log.d(TAG, "原始查询完成, 返回行数=${data.size}")
            QueryResult(true, "查询成功", rowCount = data.size, data = data)
        } catch (e: Exception) {
            Log.e(TAG, "原始查询失败: $sql", e)
            QueryResult(false, "查询失败: ${e.message}")
        }
    }

    /**
     * 执行原始SQL语句（INSERT/UPDATE/DELETE/CREATE等）
     * @param sql SQL语句
     * @param bindArgs 绑定参数数组
     * @return 操作结果
     */
    fun execSQL(sql: String, bindArgs: Array<Any>? = null): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            if (bindArgs != null) {
                db.execSQL(sql, bindArgs)
            } else {
                db.execSQL(sql)
            }
            Log.d(TAG, "SQL执行成功: $sql")
            QueryResult(true, "执行成功")
        } catch (e: Exception) {
            Log.e(TAG, "SQL执行失败: $sql", e)
            QueryResult(false, "执行失败: ${e.message}")
        }
    }

    /**
     * 在事务中执行多条SQL
     * @param sqlList SQL语句列表
     * @return 操作结果
     */
    fun execSQLInTransaction(sqlList: List<String>): QueryResult {
        return try {
            val db = ensureDatabaseOpen()
            var successCount = 0
            
            db.beginTransaction()
            try {
                sqlList.forEach { sql ->
                    db.execSQL(sql)
                    successCount++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            
            Log.d(TAG, "事务执行完成, 成功=$successCount/${sqlList.size}")
            QueryResult(true, "事务执行成功", rowCount = successCount)
        } catch (e: Exception) {
            Log.e(TAG, "事务执行失败", e)
            QueryResult(false, "事务执行失败: ${e.message}")
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * Map转ContentValues
     */
    private fun mapToContentValues(map: Map<String, Any?>): ContentValues {
        val contentValues = ContentValues()
        map.forEach { (key, value) ->
            when (value) {
                null -> contentValues.putNull(key)
                is String -> contentValues.put(key, value)
                is Int -> contentValues.put(key, value)
                is Long -> contentValues.put(key, value)
                is Float -> contentValues.put(key, value)
                is Double -> contentValues.put(key, value)
                is Boolean -> contentValues.put(key, if (value) 1 else 0)
                is ByteArray -> contentValues.put(key, value)
                else -> contentValues.put(key, value.toString())
            }
        }
        return contentValues
    }

    /**
     * Cursor转List<Map>
     */
    private fun cursorToList(cursor: Cursor): List<Map<String, Any?>> {
        val list = mutableListOf<Map<String, Any?>>()
        val columnNames = cursor.columnNames
        
        while (cursor.moveToNext()) {
            val row = mutableMapOf<String, Any?>()
            columnNames.forEachIndexed { index, name ->
                row[name] = when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                    else -> cursor.getString(index)
                }
            }
            list.add(row)
        }
        return list
    }

    /**
     * 获取数据库路径
     */
    fun getDatabasePath(): String {
        return context.getDatabasePath(currentDbName).absolutePath
    }

    /**
     * 获取数据库大小（字节）
     */
    fun getDatabaseSize(): Long {
        return try {
            context.getDatabasePath(currentDbName).length()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 删除数据库文件
     * @param dbName 数据库名称
     * @return 是否成功
     */
    fun deleteDatabase(dbName: String = currentDbName): Boolean {
        return try {
            closeDatabase()
            context.deleteDatabase(dbName)
        } catch (e: Exception) {
            Log.e(TAG, "删除数据库失败: $dbName", e)
            false
        }
    }
}

