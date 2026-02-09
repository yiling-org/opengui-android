package top.yling.ozx.guiagent.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 联系人选择结果
 */
data class PickedContact(
    val id: Long,                   // 联系人ID
    val lookupKey: String,          // 查找键
    val displayName: String,        // 显示名称
    val phoneNumber: String?,       // 电话号码（主要）
    val photoUri: String? = null,   // 头像URI
    val contactUri: String          // 联系人URI（用于后续操作）
)

/**
 * 联系人选择器服务
 * 
 * 提供通过 API 方式选择联系人的功能，替代系统 PICK Intent
 * 对应 adb shell am start -a android.intent.action.PICK -t vnd.android.cursor.dir/contact
 */
class ContactPickerService(private val context: Context) {

    companion object {
        private const val TAG = "ContactPickerService"
    }

    /**
     * 获取所有可选择的联系人列表
     * 用于替代系统联系人选择器
     * 
     * @param onlyWithPhone 是否只返回有电话号码的联系人
     * @param limit 返回条数限制，0表示不限制
     * @param offset 偏移量（用于分页）
     * @return 可选择的联系人列表
     */
    fun getContactsForPick(
        onlyWithPhone: Boolean = true,
        limit: Int = 0,
        offset: Int = 0
    ): List<PickedContact> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return emptyList()
        }

        val contacts = mutableListOf<PickedContact>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            val selection = if (onlyWithPhone) {
                "${ContactsContract.Contacts.HAS_PHONE_NUMBER} > 0"
            } else {
                null
            }

            val sortOrder = if (limit > 0) {
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit OFFSET $offset"
            } else {
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            }

            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val lookupKey = it.getString(lookupKeyIndex) ?: ""
                    val hasPhone = it.getInt(hasPhoneIndex) > 0
                    
                    // 获取主要电话号码
                    val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                    val contact = PickedContact(
                        id = id,
                        lookupKey = lookupKey,
                        displayName = it.getString(nameIndex) ?: "(无姓名)",
                        phoneNumber = phoneNumber,
                        photoUri = it.getString(photoIndex),
                        contactUri = buildContactUri(id, lookupKey)
                    )
                    contacts.add(contact)
                }
            }

            Log.d(TAG, "获取到 ${contacts.size} 个可选择的联系人")
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人列表失败", e)
        } finally {
            cursor?.close()
        }

        return contacts
    }

    /**
     * 搜索联系人用于选择
     * 
     * @param keyword 搜索关键词（姓名或电话号码）
     * @param limit 返回条数限制
     * @return 匹配的联系人列表
     */
    fun searchContactsForPick(keyword: String, limit: Int = 50): List<PickedContact> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return emptyList()
        }

        if (keyword.isBlank()) {
            return emptyList()
        }

        val contacts = mutableListOf<PickedContact>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val selectionArgs = arrayOf("%$keyword%")
            val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit"

            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val lookupKey = it.getString(lookupKeyIndex) ?: ""
                    val hasPhone = it.getInt(hasPhoneIndex) > 0
                    val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                    val contact = PickedContact(
                        id = id,
                        lookupKey = lookupKey,
                        displayName = it.getString(nameIndex) ?: "(无姓名)",
                        phoneNumber = phoneNumber,
                        photoUri = it.getString(photoIndex),
                        contactUri = buildContactUri(id, lookupKey)
                    )
                    contacts.add(contact)
                }
            }

            // 如果按姓名没找到，尝试按电话号码搜索
            if (contacts.isEmpty() && keyword.all { it.isDigit() || it == '+' || it == '-' }) {
                contacts.addAll(searchByPhoneNumber(keyword, limit))
            }

            Log.d(TAG, "搜索到 ${contacts.size} 个联系人")
        } catch (e: Exception) {
            Log.e(TAG, "搜索联系人失败", e)
        } finally {
            cursor?.close()
        }

        return contacts
    }

    /**
     * 根据ID选择联系人
     * 模拟用户点击选择某个联系人后的操作
     * 
     * @param contactId 联系人ID
     * @return 选中的联系人信息，不存在返回null
     */
    fun pickContactById(contactId: Long): PickedContact? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return null
        }

        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_URI,
                contactId.toString()
            )

            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            if (cursor?.moveToFirst() == true) {
                val id = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                val lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)) ?: ""
                val hasPhone = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
                val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                return PickedContact(
                    id = id,
                    lookupKey = lookupKey,
                    displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: "(无姓名)",
                    phoneNumber = phoneNumber,
                    photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)),
                    contactUri = buildContactUri(id, lookupKey)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人失败: $contactId", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * 根据姓名选择联系人
     * 精确匹配联系人姓名
     * 
     * @param displayName 联系人姓名
     * @return 选中的联系人信息，不存在返回null
     */
    fun pickContactByName(displayName: String): PickedContact? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return null
        }

        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?"
            val selectionArgs = arrayOf(displayName)

            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            if (cursor?.moveToFirst() == true) {
                val id = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                val lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)) ?: ""
                val hasPhone = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
                val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                return PickedContact(
                    id = id,
                    lookupKey = lookupKey,
                    displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: "(无姓名)",
                    phoneNumber = phoneNumber,
                    photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)),
                    contactUri = buildContactUri(id, lookupKey)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "根据姓名获取联系人失败: $displayName", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * 根据电话号码选择联系人
     * 
     * @param phoneNumber 电话号码
     * @return 选中的联系人信息，不存在返回null
     */
    fun pickContactByPhoneNumber(phoneNumber: String): PickedContact? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return null
        }

        val contacts = searchByPhoneNumber(phoneNumber, 1)
        return contacts.firstOrNull()
    }

    /**
     * 获取所有联系人的电话号码列表（用于快速选择）
     * 每个联系人可能有多个号码，这里返回所有号码
     * 
     * @param limit 返回条数限制
     * @return 联系人电话号码列表，格式为 Pair(联系人名称, 电话号码)
     */
    fun getAllPhoneNumbersForPick(limit: Int = 100): List<Pair<String, String>> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return emptyList()
        }

        val phoneList = mutableListOf<Pair<String, String>>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC LIMIT $limit"

            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.let {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = it.getString(nameIndex) ?: "(无姓名)"
                    val number = it.getString(numberIndex) ?: continue
                    phoneList.add(Pair(name, number))
                }
            }

            Log.d(TAG, "获取到 ${phoneList.size} 个电话号码")
        } catch (e: Exception) {
            Log.e(TAG, "获取电话号码列表失败", e)
        } finally {
            cursor?.close()
        }

        return phoneList
    }

    /**
     * 解析联系人 URI 获取联系人信息
     * 用于处理从系统选择器返回的 URI
     * 
     * @param contactUri 联系人 URI
     * @return 选中的联系人信息，解析失败返回null
     */
    fun parseContactUri(contactUri: String): PickedContact? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return null
        }

        var cursor: Cursor? = null

        try {
            val uri = Uri.parse(contactUri)
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )

            cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )

            if (cursor?.moveToFirst() == true) {
                val id = cursor.getLong(cursor.getColumnIndex(ContactsContract.Contacts._ID))
                val lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)) ?: ""
                val hasPhone = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) > 0
                val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                return PickedContact(
                    id = id,
                    lookupKey = lookupKey,
                    displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: "(无姓名)",
                    phoneNumber = phoneNumber,
                    photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)),
                    contactUri = contactUri
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析联系人URI失败: $contactUri", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    // ===== 私有辅助方法 =====

    /**
     * 根据电话号码搜索联系人
     */
    private fun searchByPhoneNumber(phoneNumber: String, limit: Int): List<PickedContact> {
        val contacts = mutableListOf<PickedContact>()
        var cursor: Cursor? = null

        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.CONTACT_ID,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.NUMBER
            )

            cursor = context.contentResolver.query(uri, projection, null, null, null)

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val photoIndex = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val numberIndex = it.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val id = it.getLong(idIndex)
                    val lookupKey = it.getString(lookupKeyIndex) ?: ""

                    val contact = PickedContact(
                        id = id,
                        lookupKey = lookupKey,
                        displayName = it.getString(nameIndex) ?: "(无姓名)",
                        phoneNumber = it.getString(numberIndex),
                        photoUri = it.getString(photoIndex),
                        contactUri = buildContactUri(id, lookupKey)
                    )
                    contacts.add(contact)
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "按电话号码搜索失败", e)
        } finally {
            cursor?.close()
        }

        return contacts
    }

    /**
     * 获取主要电话号码
     */
    private fun getPrimaryPhoneNumber(contactId: Long): String? {
        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC LIMIT 1"
            )
            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取电话号码失败", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * 构建联系人 URI
     */
    private fun buildContactUri(contactId: Long, lookupKey: String): String {
        return Uri.withAppendedPath(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey),
            contactId.toString()
        ).toString()
    }

    /**
     * 检查权限
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}

