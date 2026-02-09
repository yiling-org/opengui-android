package top.yling.ozx.guiagent.services

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.*

/**
 * 日历事件数据类
 */
data class CalendarEvent(
    val id: Long,                       // 事件ID
    val title: String,                  // 事件标题
    val description: String?,           // 事件描述
    val location: String?,              // 事件地点
    val startTime: Long,                // 开始时间戳
    val endTime: Long,                  // 结束时间戳
    val allDay: Boolean,                // 是否全天事件
    val calendarId: Long,               // 所属日历ID
    val calendarName: String?,          // 所属日历名称
    val eventColor: Int? = null,        // 事件颜色
    val hasAlarm: Boolean = false,      // 是否有提醒
    val status: Int = STATUS_CONFIRMED  // 事件状态
) {
    companion object {
        const val STATUS_TENTATIVE = CalendarContract.Events.STATUS_TENTATIVE   // 暂定 = 0
        const val STATUS_CONFIRMED = CalendarContract.Events.STATUS_CONFIRMED   // 已确认 = 1
        const val STATUS_CANCELED = CalendarContract.Events.STATUS_CANCELED     // 已取消 = 2
    }

    /**
     * 获取状态描述
     */
    fun getStatusDescription(): String {
        return when (status) {
            STATUS_TENTATIVE -> "暂定"
            STATUS_CONFIRMED -> "已确认"
            STATUS_CANCELED -> "已取消"
            else -> "未知"
        }
    }

    /**
     * 获取格式化的开始时间
     */
    fun getFormattedStartTime(): String {
        val sdf = if (allDay) {
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        }
        return sdf.format(Date(startTime))
    }

    /**
     * 获取格式化的结束时间
     */
    fun getFormattedEndTime(): String {
        val sdf = if (allDay) {
            java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        }
        return sdf.format(Date(endTime))
    }

    /**
     * 获取时间范围描述
     */
    fun getTimeRangeDescription(): String {
        return if (allDay) {
            "全天"
        } else {
            val startSdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            val endSdf = java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
            "${startSdf.format(Date(startTime))} - ${endSdf.format(Date(endTime))}"
        }
    }

    /**
     * 获取事件时长(分钟)
     */
    fun getDurationMinutes(): Long {
        return (endTime - startTime) / (1000 * 60)
    }
}

/**
 * 日历账户信息
 */
data class CalendarAccount(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean = false
)

/**
 * 添加事件的参数
 */
data class AddEventParams(
    val title: String,                      // 事件标题（必填）
    val startTime: Long,                    // 开始时间戳（必填）
    val endTime: Long,                      // 结束时间戳（必填）
    val description: String? = null,        // 描述（可选）
    val location: String? = null,           // 地点（可选）
    val allDay: Boolean = false,            // 是否全天事件
    val calendarId: Long? = null,           // 日历ID，为空则使用默认日历
    val reminderMinutes: Int? = 15,         // 提前提醒分钟数，null表示不提醒
    val timeZone: String? = null            // 时区，为空则使用默认时区
)

/**
 * 日历服务类
 * 提供查看日历事件、添加事件、获取事件列表等功能
 */
class CalendarService(private val context: Context) {

    companion object {
        private const val TAG = "CalendarService"
    }

    /**
     * 获取今天的所有事件
     * @return 今天的事件列表
     */
    fun getTodayEvents(): List<CalendarEvent> {
        val calendar = Calendar.getInstance()
        
        // 今天的开始时间 (00:00:00)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        // 今天的结束时间 (23:59:59)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        return getEvents(startOfDay, endOfDay)
    }

    /**
     * 获取指定日期的事件
     * @param year 年
     * @param month 月 (1-12)
     * @param day 日
     * @return 该日期的事件列表
     */
    fun getEventsForDate(year: Int, month: Int, day: Int): List<CalendarEvent> {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        
        return getEvents(startOfDay, endOfDay)
    }

    /**
     * 获取未来N天的事件
     * @param days 天数
     * @return 事件列表
     */
    fun getUpcomingEvents(days: Int = 7): List<CalendarEvent> {
        val calendar = Calendar.getInstance()
        val startTime = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, days)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val endTime = calendar.timeInMillis
        
        return getEvents(startTime, endTime)
    }

    /**
     * 获取指定时间范围内的事件
     * @param startTime 开始时间戳
     * @param endTime 结束时间戳
     * @return 事件列表
     */
    fun getEvents(startTime: Long, endTime: Long): List<CalendarEvent> {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            Log.e(TAG, "没有读取日历权限")
            return emptyList()
        }

        val eventList = mutableListOf<CalendarEvent>()
        var cursor: Cursor? = null

        try {
            // 使用 Instances 表来查询，可以正确处理重复事件
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, startTime)
            ContentUris.appendId(builder, endTime)

            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.DESCRIPTION,
                CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                CalendarContract.Instances.EVENT_COLOR,
                CalendarContract.Instances.HAS_ALARM,
                CalendarContract.Instances.STATUS
            )

            val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

            cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val descIndex = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locationIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginIndex = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndex(CalendarContract.Instances.END)
                val allDayIndex = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calIdIndex = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)
                val calNameIndex = it.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)
                val colorIndex = it.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)
                val alarmIndex = it.getColumnIndex(CalendarContract.Instances.HAS_ALARM)
                val statusIndex = it.getColumnIndex(CalendarContract.Instances.STATUS)

                while (it.moveToNext()) {
                    val event = CalendarEvent(
                        id = it.getLong(idIndex),
                        title = it.getString(titleIndex) ?: "(无标题)",
                        description = it.getString(descIndex),
                        location = it.getString(locationIndex),
                        startTime = it.getLong(beginIndex),
                        endTime = it.getLong(endIndex),
                        allDay = it.getInt(allDayIndex) == 1,
                        calendarId = it.getLong(calIdIndex),
                        calendarName = it.getString(calNameIndex),
                        eventColor = if (colorIndex >= 0) it.getInt(colorIndex) else null,
                        hasAlarm = it.getInt(alarmIndex) == 1,
                        status = it.getInt(statusIndex)
                    )
                    eventList.add(event)
                }
            }

            Log.d(TAG, "查询到 ${eventList.size} 个日历事件")
        } catch (e: Exception) {
            Log.e(TAG, "查询日历事件失败", e)
        } finally {
            cursor?.close()
        }

        return eventList
    }

    /**
     * 添加日历事件
     * @param params 事件参数
     * @return 新创建的事件ID，失败返回-1
     */
    fun addEvent(params: AddEventParams): Long {
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            Log.e(TAG, "没有写入日历权限")
            return -1
        }

        if (params.title.isBlank()) {
            Log.e(TAG, "事件标题不能为空")
            return -1
        }

        if (params.endTime <= params.startTime) {
            Log.e(TAG, "结束时间必须大于开始时间")
            return -1
        }

        return try {
            // 获取日历ID
            val calendarId = params.calendarId ?: getDefaultCalendarId()
            if (calendarId == -1L) {
                Log.e(TAG, "没有可用的日历")
                return -1
            }

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, params.title)
                put(CalendarContract.Events.DTSTART, params.startTime)
                put(CalendarContract.Events.DTEND, params.endTime)
                put(CalendarContract.Events.ALL_DAY, if (params.allDay) 1 else 0)
                put(CalendarContract.Events.EVENT_TIMEZONE, 
                    params.timeZone ?: TimeZone.getDefault().id)
                put(CalendarContract.Events.STATUS, CalendarEvent.STATUS_CONFIRMED)
                
                params.description?.let { put(CalendarContract.Events.DESCRIPTION, it) }
                params.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
                
                // 设置提醒
                if (params.reminderMinutes != null) {
                    put(CalendarContract.Events.HAS_ALARM, 1)
                }
            }

            val eventUri = context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI, 
                values
            )

            val eventId = eventUri?.lastPathSegment?.toLongOrNull() ?: -1L

            // 添加提醒
            if (eventId > 0 && params.reminderMinutes != null) {
                addReminder(eventId, params.reminderMinutes)
            }

            Log.d(TAG, "添加日历事件成功: ID=$eventId, 标题=${params.title}")
            eventId
        } catch (e: Exception) {
            Log.e(TAG, "添加日历事件失败", e)
            -1
        }
    }

    /**
     * 快速添加今天的事件
     * @param title 事件标题
     * @param startHour 开始小时 (0-23)
     * @param startMinute 开始分钟 (0-59)
     * @param durationMinutes 持续时间(分钟)
     * @param description 描述（可选）
     * @param location 地点（可选）
     * @return 新创建的事件ID
     */
    fun addTodayEvent(
        title: String,
        startHour: Int,
        startMinute: Int,
        durationMinutes: Int = 60,
        description: String? = null,
        location: String? = null
    ): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, startHour)
        calendar.set(Calendar.MINUTE, startMinute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        calendar.add(Calendar.MINUTE, durationMinutes)
        val endTime = calendar.timeInMillis

        return addEvent(AddEventParams(
            title = title,
            startTime = startTime,
            endTime = endTime,
            description = description,
            location = location
        ))
    }

    /**
     * 添加全天事件
     * @param title 事件标题
     * @param year 年
     * @param month 月 (1-12)
     * @param day 日
     * @param description 描述（可选）
     * @return 新创建的事件ID
     */
    fun addAllDayEvent(
        title: String,
        year: Int,
        month: Int,
        day: Int,
        description: String? = null
    ): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month - 1, day, 0, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endTime = calendar.timeInMillis

        return addEvent(AddEventParams(
            title = title,
            startTime = startTime,
            endTime = endTime,
            description = description,
            allDay = true,
            timeZone = "UTC"  // 全天事件使用 UTC 时区
        ))
    }

    /**
     * 删除日历事件
     * @param eventId 事件ID
     * @return 是否删除成功
     */
    fun deleteEvent(eventId: Long): Boolean {
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            Log.e(TAG, "没有写入日历权限")
            return false
        }

        return try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = context.contentResolver.delete(uri, null, null)
            val success = rows > 0
            Log.d(TAG, "删除日历事件: ID=$eventId, 成功=$success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "删除日历事件失败", e)
            false
        }
    }

    /**
     * 更新日历事件
     * @param eventId 事件ID
     * @param title 新标题（可选）
     * @param description 新描述（可选）
     * @param location 新地点（可选）
     * @param startTime 新开始时间（可选）
     * @param endTime 新结束时间（可选）
     * @return 是否更新成功
     */
    fun updateEvent(
        eventId: Long,
        title: String? = null,
        description: String? = null,
        location: String? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): Boolean {
        if (!hasPermission(Manifest.permission.WRITE_CALENDAR)) {
            Log.e(TAG, "没有写入日历权限")
            return false
        }

        return try {
            val values = ContentValues()
            title?.let { values.put(CalendarContract.Events.TITLE, it) }
            description?.let { values.put(CalendarContract.Events.DESCRIPTION, it) }
            location?.let { values.put(CalendarContract.Events.EVENT_LOCATION, it) }
            startTime?.let { values.put(CalendarContract.Events.DTSTART, it) }
            endTime?.let { values.put(CalendarContract.Events.DTEND, it) }

            if (values.size() == 0) {
                Log.w(TAG, "没有需要更新的字段")
                return false
            }

            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = context.contentResolver.update(uri, values, null, null)
            val success = rows > 0
            Log.d(TAG, "更新日历事件: ID=$eventId, 成功=$success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "更新日历事件失败", e)
            false
        }
    }

    /**
     * 获取指定事件的详细信息
     * @param eventId 事件ID
     * @return 事件详情，不存在返回null
     */
    fun getEventById(eventId: Long): CalendarEvent? {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            Log.e(TAG, "没有读取日历权限")
            return null
        }

        var cursor: Cursor? = null
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                CalendarContract.Events.EVENT_COLOR,
                CalendarContract.Events.HAS_ALARM,
                CalendarContract.Events.STATUS
            )

            cursor = context.contentResolver.query(uri, projection, null, null, null)

            if (cursor?.moveToFirst() == true) {
                return CalendarEvent(
                    id = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events._ID)),
                    title = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.TITLE)) ?: "(无标题)",
                    description = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)),
                    location = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)),
                    startTime = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events.DTSTART)),
                    endTime = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events.DTEND)),
                    allDay = cursor.getInt(cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)) == 1,
                    calendarId = cursor.getLong(cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)),
                    calendarName = cursor.getString(cursor.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)),
                    eventColor = cursor.getInt(cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR)),
                    hasAlarm = cursor.getInt(cursor.getColumnIndex(CalendarContract.Events.HAS_ALARM)) == 1,
                    status = cursor.getInt(cursor.getColumnIndex(CalendarContract.Events.STATUS))
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取事件详情失败", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * 获取所有日历账户
     * @return 日历账户列表
     */
    fun getCalendars(): List<CalendarAccount> {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            Log.e(TAG, "没有读取日历权限")
            return emptyList()
        }

        val calendars = mutableListOf<CalendarAccount>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.IS_PRIMARY
            )

            cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountNameIndex = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                val accountTypeIndex = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
                val colorIndex = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_COLOR)
                val primaryIndex = it.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (it.moveToNext()) {
                    val calendar = CalendarAccount(
                        id = it.getLong(idIndex),
                        name = it.getString(nameIndex) ?: "",
                        accountName = it.getString(accountNameIndex) ?: "",
                        accountType = it.getString(accountTypeIndex) ?: "",
                        color = it.getInt(colorIndex),
                        isPrimary = it.getInt(primaryIndex) == 1
                    )
                    calendars.add(calendar)
                }
            }

            Log.d(TAG, "获取到 ${calendars.size} 个日历账户")
        } catch (e: Exception) {
            Log.e(TAG, "获取日历账户失败", e)
        } finally {
            cursor?.close()
        }

        return calendars
    }

    /**
     * 搜索事件
     * @param keyword 搜索关键词
     * @param limit 返回条数限制
     * @return 匹配的事件列表
     */
    fun searchEvents(keyword: String, limit: Int = 50): List<CalendarEvent> {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            Log.e(TAG, "没有读取日历权限")
            return emptyList()
        }

        if (keyword.isBlank()) {
            return emptyList()
        }

        val eventList = mutableListOf<CalendarEvent>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.CALENDAR_DISPLAY_NAME,
                CalendarContract.Events.EVENT_COLOR,
                CalendarContract.Events.HAS_ALARM,
                CalendarContract.Events.STATUS
            )

            val selection = "${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?"
            val selectionArgs = arrayOf("%$keyword%", "%$keyword%")
            val sortOrder = "${CalendarContract.Events.DTSTART} DESC LIMIT $limit"

            cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(CalendarContract.Events._ID)
                val titleIndex = it.getColumnIndex(CalendarContract.Events.TITLE)
                val descIndex = it.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locationIndex = it.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startIndex = it.getColumnIndex(CalendarContract.Events.DTSTART)
                val endIndex = it.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayIndex = it.getColumnIndex(CalendarContract.Events.ALL_DAY)
                val calIdIndex = it.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
                val calNameIndex = it.getColumnIndex(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                val colorIndex = it.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val alarmIndex = it.getColumnIndex(CalendarContract.Events.HAS_ALARM)
                val statusIndex = it.getColumnIndex(CalendarContract.Events.STATUS)

                while (it.moveToNext()) {
                    val event = CalendarEvent(
                        id = it.getLong(idIndex),
                        title = it.getString(titleIndex) ?: "(无标题)",
                        description = it.getString(descIndex),
                        location = it.getString(locationIndex),
                        startTime = it.getLong(startIndex),
                        endTime = it.getLong(endIndex),
                        allDay = it.getInt(allDayIndex) == 1,
                        calendarId = it.getLong(calIdIndex),
                        calendarName = it.getString(calNameIndex),
                        eventColor = if (colorIndex >= 0) it.getInt(colorIndex) else null,
                        hasAlarm = it.getInt(alarmIndex) == 1,
                        status = it.getInt(statusIndex)
                    )
                    eventList.add(event)
                }
            }

            Log.d(TAG, "搜索到 ${eventList.size} 个包含 '$keyword' 的事件")
        } catch (e: Exception) {
            Log.e(TAG, "搜索日历事件失败", e)
        } finally {
            cursor?.close()
        }

        return eventList
    }

    /**
     * 获取今天的事件数量
     */
    fun getTodayEventCount(): Int {
        return getTodayEvents().size
    }

    /**
     * 获取默认日历ID
     */
    private fun getDefaultCalendarId(): Long {
        val calendars = getCalendars()
        
        // 优先选择主日历
        calendars.find { it.isPrimary }?.let { return it.id }
        
        // 其次选择本地日历
        calendars.find { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }?.let { return it.id }
        
        // 返回第一个可用日历
        return calendars.firstOrNull()?.id ?: -1L
    }

    /**
     * 为事件添加提醒
     */
    private fun addReminder(eventId: Long, minutesBefore: Int): Boolean {
        return try {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutesBefore)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }

            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
            Log.d(TAG, "添加提醒成功: 事件ID=$eventId, 提前${minutesBefore}分钟")
            true
        } catch (e: Exception) {
            Log.e(TAG, "添加提醒失败", e)
            false
        }
    }

    /**
     * 检查权限
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}

