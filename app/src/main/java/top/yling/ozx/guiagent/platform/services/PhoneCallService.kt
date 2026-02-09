package top.yling.ozx.guiagent.services

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * 通话记录数据类
 */
data class CallRecord(
    val id: Long,                      // 记录ID
    val number: String,                // 电话号码
    val contactName: String?,          // 联系人名称
    val type: Int,                     // 类型: 1=来电, 2=去电, 3=未接
    val date: Long,                    // 通话时间戳
    val duration: Long,                // 通话时长(秒)
    val isNew: Boolean = false         // 是否未查看
) {
    companion object {
        const val TYPE_INCOMING = CallLog.Calls.INCOMING_TYPE  // 来电 = 1
        const val TYPE_OUTGOING = CallLog.Calls.OUTGOING_TYPE  // 去电 = 2
        const val TYPE_MISSED = CallLog.Calls.MISSED_TYPE      // 未接 = 3
        const val TYPE_VOICEMAIL = CallLog.Calls.VOICEMAIL_TYPE // 语音邮件 = 4
        const val TYPE_REJECTED = CallLog.Calls.REJECTED_TYPE   // 拒接 = 5
        const val TYPE_BLOCKED = CallLog.Calls.BLOCKED_TYPE     // 已拦截 = 6
    }

    /**
     * 获取类型描述
     */
    fun getTypeDescription(): String {
        return when (type) {
            TYPE_INCOMING -> "来电"
            TYPE_OUTGOING -> "去电"
            TYPE_MISSED -> "未接"
            TYPE_VOICEMAIL -> "语音邮件"
            TYPE_REJECTED -> "已拒接"
            TYPE_BLOCKED -> "已拦截"
            else -> "未知"
        }
    }

    /**
     * 获取格式化的日期时间
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(date))
    }

    /**
     * 获取格式化的通话时长
     */
    fun getFormattedDuration(): String {
        val hours = duration / 3600
        val minutes = (duration % 3600) / 60
        val seconds = duration % 60
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%d:%02d", minutes, seconds)
            else -> String.format("0:%02d", seconds)
        }
    }
}

/**
 * 通话状态
 */
enum class CallState {
    IDLE,       // 空闲
    RINGING,    // 响铃（来电）
    OFFHOOK,    // 摘机（通话中或拨号中）
}

/**
 * 电话服务类
 * 提供拨打电话、挂断电话、查询通话记录、监听通话状态等功能
 */
class PhoneCallService(private val context: Context) {

    companion object {
        private const val TAG = "PhoneCallService"
    }

    // 通话状态回调
    interface CallStateCallback {
        fun onStateChanged(state: CallState, phoneNumber: String?)
        fun onIncomingCall(phoneNumber: String?, contactName: String?)
        fun onCallEnded()
    }

    // 通话状态监听器列表
    private val stateCallbacks = mutableListOf<CallStateCallback>()
    
    // TelephonyManager
    private val telephonyManager: TelephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }
    
    // TelecomManager (用于接听/挂断电话)
    private val telecomManager: TelecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    // 旧版本的监听器
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    
    // Android 12+ 的监听器
    @RequiresApi(Build.VERSION_CODES.S)
    private var telephonyCallback: TelephonyCallback? = null

    // 来电广播接收器
    private var incomingCallReceiver: BroadcastReceiver? = null
    
    // 当前通话状态
    private var currentState: CallState = CallState.IDLE
    private var currentNumber: String? = null

    /**
     * 拨打电话
     * @param phoneNumber 要拨打的号码
     * @param directCall 是否直接拨打（需要 CALL_PHONE 权限），false 则跳转到拨号界面
     * @return 是否成功发起
     */
    fun makeCall(phoneNumber: String, directCall: Boolean = true): Boolean {
        if (phoneNumber.isBlank()) {
            Log.e(TAG, "电话号码不能为空")
            return false
        }

        val uri = Uri.parse("tel:${phoneNumber.trim()}")

        return try {
            if (directCall && hasPermission(Manifest.permission.CALL_PHONE)) {
                // 直接拨打电话
                val intent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "直接拨打电话: $phoneNumber")
            } else {
                // 跳转到拨号界面
                val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "跳转到拨号界面: $phoneNumber")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "拨打电话失败", e)
            false
        }
    }

    /**
     * 挂断电话
     * 需要 ANSWER_PHONE_CALLS 权限 (Android 9+)
     * @return 是否成功
     */
    fun endCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
                    val success = telecomManager.endCall()
                    Log.d(TAG, "挂断电话: $success")
                    success
                } else {
                    Log.e(TAG, "没有 ANSWER_PHONE_CALLS 权限")
                    false
                }
            } else {
                // Android 9 以下需要使用反射或者 ITelephony 接口
                endCallLegacy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "挂断电话失败", e)
            false
        }
    }

    /**
     * 接听电话
     * 需要 ANSWER_PHONE_CALLS 权限 (Android 8.0+)
     * @return 是否成功
     */
    fun answerCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (hasPermission(Manifest.permission.ANSWER_PHONE_CALLS)) {
                    telecomManager.acceptRingingCall()
                    Log.d(TAG, "接听电话成功")
                    true
                } else {
                    Log.e(TAG, "没有 ANSWER_PHONE_CALLS 权限")
                    false
                }
            } else {
                // Android 8.0 以下需要使用其他方式
                answerCallLegacy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "接听电话失败", e)
            false
        }
    }

    /**
     * 拒接电话
     * @return 是否成功
     */
    fun rejectCall(): Boolean {
        return endCall()
    }

    /**
     * 查询通话记录
     * @param type 通话类型（可选），参考 CallRecord.TYPE_* 常量
     * @param limit 返回条数限制
     * @param offset 偏移量（用于分页）
     * @return 通话记录列表
     */
    fun queryCallLog(
        type: Int? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<CallRecord> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            Log.e(TAG, "没有读取通话记录权限")
            return emptyList()
        }

        val callList = mutableListOf<CallRecord>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.NEW
            )

            val selection = if (type != null) "${CallLog.Calls.TYPE} = ?" else null
            val selectionArgs = if (type != null) arrayOf(type.toString()) else null
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT $limit OFFSET $offset"

            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val newIndex = it.getColumnIndex(CallLog.Calls.NEW)

                while (it.moveToNext()) {
                    val number = it.getString(numberIndex) ?: ""
                    val cachedName = it.getString(nameIndex)
                    
                    val record = CallRecord(
                        id = it.getLong(idIndex),
                        number = number,
                        contactName = cachedName ?: getContactName(number),
                        type = it.getInt(typeIndex),
                        date = it.getLong(dateIndex),
                        duration = it.getLong(durationIndex),
                        isNew = it.getInt(newIndex) == 1
                    )
                    callList.add(record)
                }
            }

            Log.d(TAG, "查询到 ${callList.size} 条通话记录")
        } catch (e: Exception) {
            Log.e(TAG, "查询通话记录失败", e)
        } finally {
            cursor?.close()
        }

        return callList
    }

    /**
     * 查询来电记录
     */
    fun queryIncomingCalls(limit: Int = 50, offset: Int = 0): List<CallRecord> {
        return queryCallLog(CallRecord.TYPE_INCOMING, limit, offset)
    }

    /**
     * 查询去电记录
     */
    fun queryOutgoingCalls(limit: Int = 50, offset: Int = 0): List<CallRecord> {
        return queryCallLog(CallRecord.TYPE_OUTGOING, limit, offset)
    }

    /**
     * 查询未接来电
     */
    fun queryMissedCalls(limit: Int = 50, offset: Int = 0): List<CallRecord> {
        return queryCallLog(CallRecord.TYPE_MISSED, limit, offset)
    }

    /**
     * 根据号码查询通话记录
     * @param phoneNumber 电话号码
     * @param limit 返回条数限制
     * @return 与该号码的通话记录
     */
    fun queryCallsByNumber(phoneNumber: String, limit: Int = 50): List<CallRecord> {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            Log.e(TAG, "没有读取通话记录权限")
            return emptyList()
        }

        val callList = mutableListOf<CallRecord>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.NEW
            )

            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            val selection = "${CallLog.Calls.NUMBER} LIKE ?"
            val selectionArgs = arrayOf("%$normalizedNumber")
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT $limit"

            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val newIndex = it.getColumnIndex(CallLog.Calls.NEW)

                while (it.moveToNext()) {
                    val number = it.getString(numberIndex) ?: ""
                    val cachedName = it.getString(nameIndex)
                    
                    val record = CallRecord(
                        id = it.getLong(idIndex),
                        number = number,
                        contactName = cachedName ?: getContactName(number),
                        type = it.getInt(typeIndex),
                        date = it.getLong(dateIndex),
                        duration = it.getLong(durationIndex),
                        isNew = it.getInt(newIndex) == 1
                    )
                    callList.add(record)
                }
            }

            Log.d(TAG, "查询到与 $phoneNumber 的 ${callList.size} 条通话记录")
        } catch (e: Exception) {
            Log.e(TAG, "查询通话记录失败", e)
        } finally {
            cursor?.close()
        }

        return callList
    }

    /**
     * 获取未接来电数量
     */
    fun getMissedCallCount(): Int {
        if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
            return 0
        }

        var cursor: Cursor? = null
        try {
            val selection = "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1"
            val selectionArgs = arrayOf(CallRecord.TYPE_MISSED.toString())

            cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf("COUNT(*)"),
                selection,
                selectionArgs,
                null
            )

            if (cursor?.moveToFirst() == true) {
                return cursor.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取未接来电数量失败", e)
        } finally {
            cursor?.close()
        }
        return 0
    }

    /**
     * 注册通话状态监听
     * @param callback 通话状态回调
     */
    fun registerCallStateListener(callback: CallStateCallback) {
        stateCallbacks.add(callback)

        // 如果是第一个监听器，启动监听
        if (stateCallbacks.size == 1) {
            startCallStateListener()
        }
    }

    /**
     * 取消注册通话状态监听
     * @param callback 要移除的回调
     */
    fun unregisterCallStateListener(callback: CallStateCallback) {
        stateCallbacks.remove(callback)

        // 如果没有监听器了，停止监听
        if (stateCallbacks.isEmpty()) {
            stopCallStateListener()
        }
    }

    /**
     * 获取当前通话状态
     */
    fun getCurrentCallState(): CallState = currentState

    /**
     * 获取当前通话号码
     */
    fun getCurrentCallNumber(): String? = currentNumber

    /**
     * 清理资源
     */
    fun destroy() {
        stateCallbacks.clear()
        stopCallStateListener()
    }

    /**
     * 启动通话状态监听
     */
    private fun startCallStateListener() {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            Log.e(TAG, "没有 READ_PHONE_STATE 权限")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 使用新的 TelephonyCallback
            startTelephonyCallback()
        } else {
            // 旧版本使用 PhoneStateListener
            startPhoneStateListener()
        }

        // 注册来电广播接收器（用于获取来电号码）
        registerIncomingCallReceiver()
        
        Log.d(TAG, "通话状态监听已启动")
    }

    /**
     * 停止通话状态监听
     */
    private fun stopCallStateListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                telephonyManager.unregisterTelephonyCallback(it)
            }
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            phoneStateListener?.let {
                telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            }
            phoneStateListener = null
        }

        // 注销来电广播接收器
        unregisterIncomingCallReceiver()
        
        Log.d(TAG, "通话状态监听已停止")
    }

    /**
     * Android 12+ 的通话状态监听
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun startTelephonyCallback() {
        telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallStateChange(state)
            }
        }

        try {
            telephonyManager.registerTelephonyCallback(
                context.mainExecutor,
                telephonyCallback!!
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "注册 TelephonyCallback 失败", e)
        }
    }

    /**
     * 旧版本的通话状态监听
     */
    @Suppress("DEPRECATION")
    private fun startPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                currentNumber = phoneNumber
                handleCallStateChange(state)
            }
        }

        try {
            telephonyManager.listen(
                phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "注册 PhoneStateListener 失败", e)
        }
    }

    /**
     * 处理通话状态变化
     */
    private fun handleCallStateChange(state: Int) {
        val newState = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> CallState.IDLE
            TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
            else -> CallState.IDLE
        }

        val previousState = currentState
        currentState = newState

        Log.d(TAG, "通话状态变化: $previousState -> $newState, 号码: $currentNumber")

        // 通知所有监听器
        stateCallbacks.forEach { callback ->
            callback.onStateChanged(newState, currentNumber)
            
            when {
                newState == CallState.RINGING -> {
                    val contactName = currentNumber?.let { getContactName(it) }
                    callback.onIncomingCall(currentNumber, contactName)
                }
                previousState != CallState.IDLE && newState == CallState.IDLE -> {
                    callback.onCallEnded()
                }
            }
        }

        // 通话结束后清除号码
        if (newState == CallState.IDLE) {
            currentNumber = null
        }
    }

    /**
     * 注册来电广播接收器
     */
    private fun registerIncomingCallReceiver() {
        incomingCallReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
                    val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                    if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                        // 获取来电号码
                        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                        if (!number.isNullOrBlank()) {
                            currentNumber = number
                            Log.d(TAG, "来电号码: $number")
                        }
                    }
                }
            }
        }

        val filter = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(incomingCallReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(incomingCallReceiver, filter)
        }
    }

    /**
     * 注销来电广播接收器
     */
    private fun unregisterIncomingCallReceiver() {
        incomingCallReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        incomingCallReceiver = null
    }

    /**
     * 旧版本挂断电话（使用反射）
     */
    @Suppress("DEPRECATION")
    private fun endCallLegacy(): Boolean {
        return try {
            val telephonyClass = Class.forName("android.telephony.TelephonyManager")
            val getITelephonyMethod = telephonyClass.getDeclaredMethod("getITelephony")
            getITelephonyMethod.isAccessible = true
            
            val iTelephony = getITelephonyMethod.invoke(telephonyManager)
            val endCallMethod = iTelephony.javaClass.getDeclaredMethod("endCall")
            endCallMethod.invoke(iTelephony) as Boolean
        } catch (e: Exception) {
            Log.e(TAG, "旧版本挂断电话失败", e)
            false
        }
    }

    /**
     * 旧版本接听电话
     */
    private fun answerCallLegacy(): Boolean {
        return try {
            // 模拟耳机按键接听
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(
                    Intent.EXTRA_KEY_EVENT,
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_HEADSETHOOK
                    )
                )
            }
            context.sendOrderedBroadcast(intent, null)
            Log.d(TAG, "旧版本接听电话")
            true
        } catch (e: Exception) {
            Log.e(TAG, "旧版本接听电话失败", e)
            false
        }
    }

    /**
     * 根据号码获取联系人名称
     */
    private fun getContactName(phoneNumber: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS) || phoneNumber.isBlank()) {
            return null
        }

        var cursor: Cursor? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询联系人失败: $phoneNumber", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * 标准化电话号码
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        var number = phoneNumber.replace(Regex("[^0-9]"), "")
        if (number.startsWith("86") && number.length > 11) {
            number = number.substring(2)
        }
        return number
    }

    /**
     * 检查权限
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}

