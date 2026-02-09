package top.yling.ozx.guiagent.services

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 短信服务类
 * 提供发送短信、接收短信回调、查询短信列表等功能
 */
class SmsService(private val context: Context) {

    companion object {
        private const val TAG = "SmsService"
        
        // 广播 Action
        private const val SMS_SENT_ACTION = "top.yling.ozx.guiagent.SMS_SENT"
        private const val SMS_DELIVERED_ACTION = "top.yling.ozx.guiagent.SMS_DELIVERED"
    }

    // 短信发送结果回调
    interface SmsSendCallback {
        fun onSent(success: Boolean, message: String)
        fun onDelivered(success: Boolean, message: String)
    }

    // 短信接收回调
    interface SmsReceivedCallback {
        fun onReceived(smsMessage: SmsMessage)
    }

    // 短信接收监听器列表
    private val receivedCallbacks = mutableListOf<SmsReceivedCallback>()
    
    // 短信接收广播接收器
    private var smsReceiver: SmsBroadcastReceiver? = null

    /**
     * 发送短信
     * @param phoneNumber 收件人号码
     * @param message 短信内容
     * @param callback 发送结果回调（可选）
     * @return 是否成功发起发送（不代表发送成功）
     */
    fun sendSms(
        phoneNumber: String, 
        message: String, 
        callback: SmsSendCallback? = null
    ): Boolean {
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            Log.e(TAG, "没有发送短信权限")
            callback?.onSent(false, "没有发送短信权限")
            return false
        }

        if (phoneNumber.isBlank()) {
            Log.e(TAG, "电话号码不能为空")
            callback?.onSent(false, "电话号码不能为空")
            return false
        }

        if (message.isBlank()) {
            Log.e(TAG, "短信内容不能为空")
            callback?.onSent(false, "短信内容不能为空")
            return false
        }

        return try {
            val smsManager = getSmsManager()

            // 创建发送结果 PendingIntent
            val sentIntent = if (callback != null) {
                val sentIntentFilter = IntentFilter(SMS_SENT_ACTION)
                val sentReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                Log.d(TAG, "短信发送成功")
                                callback.onSent(true, "短信发送成功")
                            }
                            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                                callback.onSent(false, "发送失败: 通用错误")
                            }
                            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                                callback.onSent(false, "发送失败: 无服务")
                            }
                            SmsManager.RESULT_ERROR_NULL_PDU -> {
                                callback.onSent(false, "发送失败: PDU为空")
                            }
                            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                                callback.onSent(false, "发送失败: 无线电关闭")
                            }
                            else -> {
                                callback.onSent(false, "发送失败: 未知错误 ($resultCode)")
                            }
                        }
                        try {
                            context?.unregisterReceiver(this)
                        } catch (_: Exception) {}
                    }
                }
                registerReceiverCompat(sentReceiver, sentIntentFilter)
                
                PendingIntent.getBroadcast(
                    context, 
                    0, 
                    Intent(SMS_SENT_ACTION),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            // 创建送达结果 PendingIntent
            val deliveredIntent = if (callback != null) {
                val deliveredIntentFilter = IntentFilter(SMS_DELIVERED_ACTION)
                val deliveredReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (resultCode) {
                            Activity.RESULT_OK -> {
                                Log.d(TAG, "短信已送达")
                                callback.onDelivered(true, "短信已送达")
                            }
                            Activity.RESULT_CANCELED -> {
                                callback.onDelivered(false, "短信未送达")
                            }
                            else -> {
                                callback.onDelivered(false, "送达状态未知 ($resultCode)")
                            }
                        }
                        try {
                            context?.unregisterReceiver(this)
                        } catch (_: Exception) {}
                    }
                }
                registerReceiverCompat(deliveredReceiver, deliveredIntentFilter)
                
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(SMS_DELIVERED_ACTION),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else null

            // 发送短信（支持长短信分段发送）
            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                // 长短信，分段发送
                val sentIntents = if (sentIntent != null) {
                    ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { add(sentIntent) }
                    }
                } else null
                val deliveredIntents = if (deliveredIntent != null) {
                    ArrayList<PendingIntent>().apply {
                        repeat(parts.size) { add(deliveredIntent) }
                    }
                } else null
                smsManager.sendMultipartTextMessage(
                    phoneNumber, 
                    null, 
                    parts, 
                    sentIntents, 
                    deliveredIntents
                )
            } else {
                // 普通短信
                smsManager.sendTextMessage(
                    phoneNumber, 
                    null, 
                    message, 
                    sentIntent, 
                    deliveredIntent
                )
            }

            Log.d(TAG, "短信发送请求已发起: $phoneNumber")
            true
        } catch (e: Exception) {
            Log.e(TAG, "发送短信失败", e)
            callback?.onSent(false, "发送短信异常: ${e.message}")
            false
        }
    }

    /**
     * 查询短信列表
     * @param type 短信类型（可选），参考 SmsMessage.TYPE_* 常量
     * @param limit 返回条数限制
     * @param offset 偏移量（用于分页）
     * @return 短信列表
     */
    fun querySms(
        type: Int? = null, 
        limit: Int = 50, 
        offset: Int = 0
    ): List<SmsMessage> {
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            Log.e(TAG, "没有读取短信权限")
            return emptyList()
        }

        val smsList = mutableListOf<SmsMessage>()
        var cursor: Cursor? = null

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID
            )

            val selection = if (type != null) "${Telephony.Sms.TYPE} = ?" else null
            val selectionArgs = if (type != null) arrayOf(type.toString()) else null
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit OFFSET $offset"

            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)
                val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex) ?: ""
                    val sms = SmsMessage(
                        id = it.getLong(idIndex),
                        address = address,
                        body = it.getString(bodyIndex) ?: "",
                        date = it.getLong(dateIndex),
                        type = it.getInt(typeIndex),
                        read = it.getInt(readIndex) == 1,
                        threadId = if (threadIdIndex >= 0) it.getLong(threadIdIndex) else null,
                        contactName = getContactName(address)
                    )
                    smsList.add(sms)
                }
            }

            Log.d(TAG, "查询到 ${smsList.size} 条短信")
        } catch (e: Exception) {
            Log.e(TAG, "查询短信失败", e)
        } finally {
            cursor?.close()
        }

        return smsList
    }

    /**
     * 查询收件箱短信
     */
    fun queryInbox(limit: Int = 50, offset: Int = 0): List<SmsMessage> {
        return querySms(SmsMessage.TYPE_INBOX, limit, offset)
    }

    /**
     * 查询已发送短信
     */
    fun querySent(limit: Int = 50, offset: Int = 0): List<SmsMessage> {
        return querySms(SmsMessage.TYPE_SENT, limit, offset)
    }

    /**
     * 根据号码查询短信会话
     * @param phoneNumber 电话号码
     * @param limit 返回条数限制
     * @return 与该号码的短信列表
     */
    fun queryConversation(phoneNumber: String, limit: Int = 50): List<SmsMessage> {
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            Log.e(TAG, "没有读取短信权限")
            return emptyList()
        }

        val smsList = mutableListOf<SmsMessage>()
        var cursor: Cursor? = null

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID
            )

            // 处理号码格式（去掉+86等前缀）
            val normalizedNumber = normalizePhoneNumber(phoneNumber)
            val selection = "${Telephony.Sms.ADDRESS} LIKE ?"
            val selectionArgs = arrayOf("%$normalizedNumber")
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)
                val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex) ?: ""
                    val sms = SmsMessage(
                        id = it.getLong(idIndex),
                        address = address,
                        body = it.getString(bodyIndex) ?: "",
                        date = it.getLong(dateIndex),
                        type = it.getInt(typeIndex),
                        read = it.getInt(readIndex) == 1,
                        threadId = if (threadIdIndex >= 0) it.getLong(threadIdIndex) else null,
                        contactName = getContactName(address)
                    )
                    smsList.add(sms)
                }
            }

            Log.d(TAG, "查询到与 $phoneNumber 的 ${smsList.size} 条短信")
        } catch (e: Exception) {
            Log.e(TAG, "查询短信会话失败", e)
        } finally {
            cursor?.close()
        }

        return smsList
    }

    /**
     * 搜索短信内容
     * @param keyword 搜索关键词
     * @param limit 返回条数限制
     * @return 匹配的短信列表
     */
    fun searchSms(keyword: String, limit: Int = 50): List<SmsMessage> {
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            Log.e(TAG, "没有读取短信权限")
            return emptyList()
        }

        if (keyword.isBlank()) {
            return emptyList()
        }

        val smsList = mutableListOf<SmsMessage>()
        var cursor: Cursor? = null

        try {
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.THREAD_ID
            )

            val selection = "${Telephony.Sms.BODY} LIKE ?"
            val selectionArgs = arrayOf("%$keyword%")
            val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

            cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIndex = it.getColumnIndex(Telephony.Sms.READ)
                val threadIdIndex = it.getColumnIndex(Telephony.Sms.THREAD_ID)

                while (it.moveToNext()) {
                    val address = it.getString(addressIndex) ?: ""
                    val sms = SmsMessage(
                        id = it.getLong(idIndex),
                        address = address,
                        body = it.getString(bodyIndex) ?: "",
                        date = it.getLong(dateIndex),
                        type = it.getInt(typeIndex),
                        read = it.getInt(readIndex) == 1,
                        threadId = if (threadIdIndex >= 0) it.getLong(threadIdIndex) else null,
                        contactName = getContactName(address)
                    )
                    smsList.add(sms)
                }
            }

            Log.d(TAG, "搜索到 ${smsList.size} 条包含 '$keyword' 的短信")
        } catch (e: Exception) {
            Log.e(TAG, "搜索短信失败", e)
        } finally {
            cursor?.close()
        }

        return smsList
    }

    /**
     * 注册短信接收监听
     * @param callback 短信接收回调
     */
    fun registerSmsReceivedListener(callback: SmsReceivedCallback) {
        receivedCallbacks.add(callback)
        
        // 如果还没有注册广播接收器，则注册
        if (smsReceiver == null) {
            smsReceiver = SmsBroadcastReceiver { smsMessage ->
                // 通知所有监听器
                receivedCallbacks.forEach { it.onReceived(smsMessage) }
            }
            
            val intentFilter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            registerReceiverCompat(smsReceiver!!, intentFilter)
            Log.d(TAG, "短信接收监听已注册")
        }
    }

    /**
     * 取消注册短信接收监听
     * @param callback 要移除的回调
     */
    fun unregisterSmsReceivedListener(callback: SmsReceivedCallback) {
        receivedCallbacks.remove(callback)
        
        // 如果没有监听器了，注销广播接收器
        if (receivedCallbacks.isEmpty() && smsReceiver != null) {
            try {
                context.unregisterReceiver(smsReceiver)
            } catch (_: Exception) {}
            smsReceiver = null
            Log.d(TAG, "短信接收监听已注销")
        }
    }

    /**
     * 清理资源
     */
    fun destroy() {
        receivedCallbacks.clear()
        smsReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        smsReceiver = null
    }

    /**
     * 获取未读短信数量
     */
    fun getUnreadCount(): Int {
        if (!hasPermission(Manifest.permission.READ_SMS)) {
            return 0
        }

        var cursor: Cursor? = null
        try {
            val uri = Telephony.Sms.CONTENT_URI
            val selection = "${Telephony.Sms.READ} = 0 AND ${Telephony.Sms.TYPE} = ${SmsMessage.TYPE_INBOX}"
            
            cursor = context.contentResolver.query(
                uri,
                arrayOf("COUNT(*)"),
                selection,
                null,
                null
            )
            
            if (cursor?.moveToFirst() == true) {
                return cursor.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取未读短信数量失败", e)
        } finally {
            cursor?.close()
        }
        return 0
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
     * 标准化电话号码（去掉国家代码前缀）
     */
    private fun normalizePhoneNumber(phoneNumber: String): String {
        var number = phoneNumber.replace(Regex("[^0-9]"), "")
        // 去掉中国区号
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

    /**
     * 获取 SmsManager 实例
     */
    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    /**
     * 兼容注册广播接收器
     */
    private fun registerReceiverCompat(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}

/**
 * 短信接收广播接收器
 */
class SmsBroadcastReceiver(
    private val onSmsReceived: (SmsMessage) -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        Log.d(TAG, "收到短信广播")

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) {
                Log.w(TAG, "短信内容为空")
                return
            }

            // 合并多段短信内容
            val address = messages[0].displayOriginatingAddress ?: ""
            val body = StringBuilder()
            var timestamp = 0L

            messages.forEach { message ->
                body.append(message.displayMessageBody ?: "")
                timestamp = message.timestampMillis
            }

            // 获取联系人名称
            val contactName = context?.let { getContactName(it, address) }

            val smsMessage = SmsMessage(
                id = -1, // 新收到的短信还没有 ID
                address = address,
                body = body.toString(),
                date = timestamp,
                type = SmsMessage.TYPE_INBOX,
                read = false,
                contactName = contactName
            )

            Log.d(TAG, "收到新短信 - 发送方: $address, 内容: ${body.toString().take(50)}...")
            onSmsReceived(smsMessage)
        } catch (e: Exception) {
            Log.e(TAG, "处理短信广播失败", e)
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) 
            != PackageManager.PERMISSION_GRANTED) {
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
            Log.e(TAG, "查询联系人失败", e)
        } finally {
            cursor?.close()
        }
        return null
    }
}

