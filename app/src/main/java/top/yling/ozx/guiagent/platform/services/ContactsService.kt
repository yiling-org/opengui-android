package top.yling.ozx.guiagent.services

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 联系人电话号码
 */
data class PhoneNumber(
    val number: String,           // 电话号码
    val type: Int,                // 类型
    val label: String? = null     // 自定义标签
) {
    companion object {
        const val TYPE_HOME = ContactsContract.CommonDataKinds.Phone.TYPE_HOME
        const val TYPE_MOBILE = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
        const val TYPE_WORK = ContactsContract.CommonDataKinds.Phone.TYPE_WORK
        const val TYPE_OTHER = ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
    }

    fun getTypeDescription(): String {
        return when (type) {
            TYPE_HOME -> "家庭"
            TYPE_MOBILE -> "手机"
            TYPE_WORK -> "工作"
            TYPE_OTHER -> "其他"
            else -> label ?: "其他"
        }
    }
}

/**
 * 联系人邮箱
 */
data class EmailAddress(
    val email: String,            // 邮箱地址
    val type: Int,                // 类型
    val label: String? = null     // 自定义标签
) {
    companion object {
        const val TYPE_HOME = ContactsContract.CommonDataKinds.Email.TYPE_HOME
        const val TYPE_WORK = ContactsContract.CommonDataKinds.Email.TYPE_WORK
        const val TYPE_OTHER = ContactsContract.CommonDataKinds.Email.TYPE_OTHER
    }

    fun getTypeDescription(): String {
        return when (type) {
            TYPE_HOME -> "家庭"
            TYPE_WORK -> "工作"
            TYPE_OTHER -> "其他"
            else -> label ?: "其他"
        }
    }
}

/**
 * 联系人地址
 */
data class PostalAddress(
    val address: String,          // 地址
    val type: Int,                // 类型
    val label: String? = null     // 自定义标签
) {
    companion object {
        const val TYPE_HOME = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
        const val TYPE_WORK = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
        const val TYPE_OTHER = ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
    }

    fun getTypeDescription(): String {
        return when (type) {
            TYPE_HOME -> "家庭"
            TYPE_WORK -> "工作"
            TYPE_OTHER -> "其他"
            else -> label ?: "其他"
        }
    }
}

/**
 * 联系人基本信息（列表用）
 */
data class ContactInfo(
    val id: Long,                           // 联系人ID
    val lookupKey: String,                  // 查找键
    val displayName: String,                // 显示名称
    val photoUri: String? = null,           // 头像URI
    val phoneNumber: String? = null,        // 主要电话号码
    val starred: Boolean = false            // 是否收藏
)

/**
 * 联系人详细信息
 */
data class ContactDetail(
    val id: Long,                           // 联系人ID
    val lookupKey: String,                  // 查找键
    val displayName: String,                // 显示名称
    val photoUri: String? = null,           // 头像URI
    val starred: Boolean = false,           // 是否收藏
    val phoneNumbers: List<PhoneNumber>,    // 电话号码列表
    val emails: List<EmailAddress>,         // 邮箱列表
    val addresses: List<PostalAddress>,     // 地址列表
    val organization: String? = null,       // 公司/组织
    val jobTitle: String? = null,           // 职位
    val note: String? = null,               // 备注
    val birthday: String? = null,           // 生日
    val nickname: String? = null            // 昵称
)

/**
 * 添加联系人的参数
 */
data class AddContactParams(
    val displayName: String,                            // 姓名（必填）
    val phoneNumbers: List<PhoneNumber> = emptyList(),  // 电话号码
    val emails: List<EmailAddress> = emptyList(),       // 邮箱
    val addresses: List<PostalAddress> = emptyList(),   // 地址
    val organization: String? = null,                   // 公司
    val jobTitle: String? = null,                       // 职位
    val note: String? = null,                           // 备注
    val starred: Boolean = false                        // 是否收藏
)

/**
 * 通讯录服务类
 * 提供联系人的增删改查功能
 */
class ContactsService(private val context: Context) {

    companion object {
        private const val TAG = "ContactsService"
    }

    /**
     * 获取所有联系人列表
     * @param limit 返回条数限制，0表示不限制
     * @param offset 偏移量（用于分页）
     * @return 联系人列表
     */
    fun getAllContacts(limit: Int = 0, offset: Int = 0): List<ContactInfo> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return emptyList()
        }

        val contacts = mutableListOf<ContactInfo>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.STARRED
            )

            val sortOrder = if (limit > 0) {
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT $limit OFFSET $offset"
            } else {
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            }

            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val photoIndex = it.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val starredIndex = it.getColumnIndex(ContactsContract.Contacts.STARRED)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val hasPhone = it.getInt(hasPhoneIndex) > 0
                    
                    // 获取主要电话号码
                    val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                    val contact = ContactInfo(
                        id = id,
                        lookupKey = it.getString(lookupKeyIndex) ?: "",
                        displayName = it.getString(nameIndex) ?: "(无姓名)",
                        photoUri = it.getString(photoIndex),
                        phoneNumber = phoneNumber,
                        starred = it.getInt(starredIndex) == 1
                    )
                    contacts.add(contact)
                }
            }

            Log.d(TAG, "获取到 ${contacts.size} 个联系人")
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人列表失败", e)
        } finally {
            cursor?.close()
        }

        return contacts
    }

    /**
     * 搜索联系人
     * @param keyword 搜索关键词（姓名或电话号码）
     * @param limit 返回条数限制
     * @return 匹配的联系人列表
     */
    fun searchContacts(keyword: String, limit: Int = 50): List<ContactInfo> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return emptyList()
        }

        if (keyword.isBlank()) {
            return emptyList()
        }

        val contacts = mutableListOf<ContactInfo>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.STARRED
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
                val starredIndex = it.getColumnIndex(ContactsContract.Contacts.STARRED)

                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val hasPhone = it.getInt(hasPhoneIndex) > 0
                    val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                    val contact = ContactInfo(
                        id = id,
                        lookupKey = it.getString(lookupKeyIndex) ?: "",
                        displayName = it.getString(nameIndex) ?: "(无姓名)",
                        photoUri = it.getString(photoIndex),
                        phoneNumber = phoneNumber,
                        starred = it.getInt(starredIndex) == 1
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
     * 根据电话号码搜索联系人
     */
    private fun searchByPhoneNumber(phoneNumber: String, limit: Int): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()
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
                ContactsContract.PhoneLookup.NUMBER,
                ContactsContract.PhoneLookup.STARRED
            )

            cursor = context.contentResolver.query(uri, projection, null, null, null)

            cursor?.let {
                val idIndex = it.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
                val lookupKeyIndex = it.getColumnIndex(ContactsContract.PhoneLookup.LOOKUP_KEY)
                val nameIndex = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val photoIndex = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val numberIndex = it.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                val starredIndex = it.getColumnIndex(ContactsContract.PhoneLookup.STARRED)

                var count = 0
                while (it.moveToNext() && count < limit) {
                    val contact = ContactInfo(
                        id = it.getLong(idIndex),
                        lookupKey = it.getString(lookupKeyIndex) ?: "",
                        displayName = it.getString(nameIndex) ?: "(无姓名)",
                        photoUri = it.getString(photoIndex),
                        phoneNumber = it.getString(numberIndex),
                        starred = it.getInt(starredIndex) == 1
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
     * 获取联系人详细信息
     * @param contactId 联系人ID
     * @return 联系人详情，不存在返回null
     */
    fun getContactDetail(contactId: Long): ContactDetail? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            Log.e(TAG, "没有读取联系人权限")
            return null
        }

        var cursor: Cursor? = null

        try {
            // 获取基本信息
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.STARRED
            )

            cursor = context.contentResolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                projection,
                null,
                null,
                null
            )

            if (cursor?.moveToFirst() != true) {
                Log.w(TAG, "联系人不存在: $contactId")
                return null
            }

            val lookupKey = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)) ?: ""
            val displayName = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)) ?: "(无姓名)"
            val photoUri = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI))
            val starred = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.STARRED)) == 1
            cursor.close()

            // 获取详细信息
            val phoneNumbers = getPhoneNumbers(contactId)
            val emails = getEmails(contactId)
            val addresses = getAddresses(contactId)
            val organization = getOrganization(contactId)
            val note = getNote(contactId)
            val nickname = getNickname(contactId)
            val birthday = getBirthday(contactId)

            return ContactDetail(
                id = contactId,
                lookupKey = lookupKey,
                displayName = displayName,
                photoUri = photoUri,
                starred = starred,
                phoneNumbers = phoneNumbers,
                emails = emails,
                addresses = addresses,
                organization = organization?.first,
                jobTitle = organization?.second,
                note = note,
                nickname = nickname,
                birthday = birthday
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人详情失败", e)
            return null
        } finally {
            cursor?.close()
        }
    }

    /**
     * 根据电话号码获取联系人详情
     */
    fun getContactByPhoneNumber(phoneNumber: String): ContactDetail? {
        val contacts = searchByPhoneNumber(phoneNumber, 1)
        return contacts.firstOrNull()?.let { getContactDetail(it.id) }
    }

    /**
     * 添加联系人
     * @param params 联系人参数
     * @return 新创建的联系人ID，失败返回-1
     */
    fun addContact(params: AddContactParams): Long {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            Log.e(TAG, "没有写入联系人权限")
            return -1
        }

        if (params.displayName.isBlank()) {
            Log.e(TAG, "联系人姓名不能为空")
            return -1
        }

        val operations = ArrayList<ContentProviderOperation>()

        // 创建 RawContact
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build()
        )

        // 添加姓名
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, 
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, 
                    params.displayName)
                .build()
        )

        // 添加电话号码
        for (phone in params.phoneNumbers) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, 
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, phone.type)
                    .apply {
                        phone.label?.let {
                            withValue(ContactsContract.CommonDataKinds.Phone.LABEL, it)
                        }
                    }
                    .build()
            )
        }

        // 添加邮箱
        for (email in params.emails) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, 
                        ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, email.type)
                    .apply {
                        email.label?.let {
                            withValue(ContactsContract.CommonDataKinds.Email.LABEL, it)
                        }
                    }
                    .build()
            )
        }

        // 添加地址
        for (address in params.addresses) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, 
                        ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, 
                        address.address)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, address.type)
                    .apply {
                        address.label?.let {
                            withValue(ContactsContract.CommonDataKinds.StructuredPostal.LABEL, it)
                        }
                    }
                    .build()
            )
        }

        // 添加公司和职位
        if (params.organization != null || params.jobTitle != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, 
                        ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, 
                        params.organization)
                    .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, 
                        params.jobTitle)
                    .build()
            )
        }

        // 添加备注
        if (params.note != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, 
                        ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Note.NOTE, params.note)
                    .build()
            )
        }

        return try {
            val results = context.contentResolver.applyBatch(
                ContactsContract.AUTHORITY, 
                operations
            )
            
            // 获取创建的 RawContact ID
            val rawContactId = ContentUris.parseId(results[0].uri!!)
            
            // 查询对应的 Contact ID
            val contactId = getContactIdFromRawContact(rawContactId)
            
            // 设置收藏状态
            if (params.starred && contactId > 0) {
                setContactStarred(contactId, true)
            }

            Log.d(TAG, "添加联系人成功: ${params.displayName}, ID=$contactId")
            contactId
        } catch (e: Exception) {
            Log.e(TAG, "添加联系人失败", e)
            -1
        }
    }

    /**
     * 快速添加联系人（只有姓名和电话）
     */
    fun addContact(displayName: String, phoneNumber: String): Long {
        return addContact(AddContactParams(
            displayName = displayName,
            phoneNumbers = listOf(PhoneNumber(phoneNumber, PhoneNumber.TYPE_MOBILE))
        ))
    }

    /**
     * 删除联系人
     * @param contactId 联系人ID
     * @return 是否删除成功
     */
    fun deleteContact(contactId: Long): Boolean {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            Log.e(TAG, "没有写入联系人权限")
            return false
        }

        return try {
            // 通过 lookup key 删除更安全
            val detail = getContactDetail(contactId) ?: return false
            
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_LOOKUP_URI,
                detail.lookupKey
            )
            
            val rows = context.contentResolver.delete(uri, null, null)
            val success = rows > 0
            Log.d(TAG, "删除联系人: ID=$contactId, 成功=$success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "删除联系人失败", e)
            false
        }
    }

    /**
     * 更新联系人信息
     * @param contactId 联系人ID
     * @param displayName 新姓名（可选）
     * @param starred 是否收藏（可选）
     * @return 是否更新成功
     */
    fun updateContact(
        contactId: Long,
        displayName: String? = null,
        starred: Boolean? = null
    ): Boolean {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            Log.e(TAG, "没有写入联系人权限")
            return false
        }

        var success = true

        // 更新姓名
        if (displayName != null) {
            success = success && updateContactName(contactId, displayName)
        }

        // 更新收藏状态
        if (starred != null) {
            success = success && setContactStarred(contactId, starred)
        }

        return success
    }

    /**
     * 添加电话号码到现有联系人
     */
    fun addPhoneNumberToContact(contactId: Long, phoneNumber: String, type: Int = PhoneNumber.TYPE_MOBILE): Boolean {
        if (!hasPermission(Manifest.permission.WRITE_CONTACTS)) {
            Log.e(TAG, "没有写入联系人权限")
            return false
        }

        return try {
            val rawContactId = getRawContactId(contactId) ?: return false
            
            val values = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, 
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, phoneNumber)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, type)
            }

            val uri = context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
            uri != null
        } catch (e: Exception) {
            Log.e(TAG, "添加电话号码失败", e)
            false
        }
    }

    /**
     * 设置联系人收藏状态
     */
    fun setContactStarred(contactId: Long, starred: Boolean): Boolean {
        return try {
            val values = ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
            }
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            val rows = context.contentResolver.update(uri, values, null, null)
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "设置收藏状态失败", e)
            false
        }
    }

    /**
     * 获取收藏的联系人
     */
    fun getStarredContacts(): List<ContactInfo> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return emptyList()
        }

        val contacts = mutableListOf<ContactInfo>()
        var cursor: Cursor? = null

        try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.HAS_PHONE_NUMBER,
                ContactsContract.Contacts.STARRED
            )

            val selection = "${ContactsContract.Contacts.STARRED} = 1"
            val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"

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
                    val hasPhone = it.getInt(hasPhoneIndex) > 0
                    val phoneNumber = if (hasPhone) getPrimaryPhoneNumber(id) else null

                    val contact = ContactInfo(
                        id = id,
                        lookupKey = it.getString(lookupKeyIndex) ?: "",
                        displayName = it.getString(nameIndex) ?: "(无姓名)",
                        photoUri = it.getString(photoIndex),
                        phoneNumber = phoneNumber,
                        starred = true
                    )
                    contacts.add(contact)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取收藏联系人失败", e)
        } finally {
            cursor?.close()
        }

        return contacts
    }

    /**
     * 获取联系人总数
     */
    fun getContactCount(): Int {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return 0
        }

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf("COUNT(*)"),
                null,
                null,
                null
            )
            if (cursor?.moveToFirst() == true) {
                return cursor.getInt(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取联系人总数失败", e)
        } finally {
            cursor?.close()
        }
        return 0
    }

    // ===== 私有辅助方法 =====

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
     * 获取所有电话号码
     */
    private fun getPhoneNumbers(contactId: Long): List<PhoneNumber> {
        val phones = mutableListOf<PhoneNumber>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )

            cursor?.let {
                while (it.moveToNext()) {
                    phones.add(PhoneNumber(
                        number = it.getString(0) ?: "",
                        type = it.getInt(1),
                        label = it.getString(2)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取电话号码列表失败", e)
        } finally {
            cursor?.close()
        }

        return phones
    }

    /**
     * 获取所有邮箱
     */
    private fun getEmails(contactId: Long): List<EmailAddress> {
        val emails = mutableListOf<EmailAddress>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE,
                    ContactsContract.CommonDataKinds.Email.LABEL
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )

            cursor?.let {
                while (it.moveToNext()) {
                    emails.add(EmailAddress(
                        email = it.getString(0) ?: "",
                        type = it.getInt(1),
                        label = it.getString(2)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取邮箱列表失败", e)
        } finally {
            cursor?.close()
        }

        return emails
    }

    /**
     * 获取所有地址
     */
    private fun getAddresses(contactId: Long): List<PostalAddress> {
        val addresses = mutableListOf<PostalAddress>()
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
                    ContactsContract.CommonDataKinds.StructuredPostal.TYPE,
                    ContactsContract.CommonDataKinds.StructuredPostal.LABEL
                ),
                "${ContactsContract.CommonDataKinds.StructuredPostal.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )

            cursor?.let {
                while (it.moveToNext()) {
                    addresses.add(PostalAddress(
                        address = it.getString(0) ?: "",
                        type = it.getInt(1),
                        label = it.getString(2)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取地址列表失败", e)
        } finally {
            cursor?.close()
        }

        return addresses
    }

    /**
     * 获取公司和职位
     */
    private fun getOrganization(contactId: Long): Pair<String?, String?>? {
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Organization.COMPANY,
                    ContactsContract.CommonDataKinds.Organization.TITLE
                ),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(
                    contactId.toString(),
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE
                ),
                null
            )

            if (cursor?.moveToFirst() == true) {
                return Pair(cursor.getString(0), cursor.getString(1))
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取公司信息失败", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * 获取备注
     */
    private fun getNote(contactId: Long): String? {
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Note.NOTE),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(
                    contactId.toString(),
                    ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE
                ),
                null
            )

            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取备注失败", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * 获取昵称
     */
    private fun getNickname(contactId: Long): String? {
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Nickname.NAME),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(
                    contactId.toString(),
                    ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
                ),
                null
            )

            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取昵称失败", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * 获取生日
     */
    private fun getBirthday(contactId: Long): String? {
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Event.START_DATE),
                "${ContactsContract.Data.CONTACT_ID} = ? AND " +
                "${ContactsContract.Data.MIMETYPE} = ? AND " +
                "${ContactsContract.CommonDataKinds.Event.TYPE} = ?",
                arrayOf(
                    contactId.toString(),
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
                ),
                null
            )

            if (cursor?.moveToFirst() == true) {
                return cursor.getString(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取生日失败", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * 从 RawContact ID 获取 Contact ID
     */
    private fun getContactIdFromRawContact(rawContactId: Long): Long {
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts._ID} = ?",
                arrayOf(rawContactId.toString()),
                null
            )

            if (cursor?.moveToFirst() == true) {
                return cursor.getLong(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 Contact ID 失败", e)
        } finally {
            cursor?.close()
        }

        return -1
    }

    /**
     * 获取 RawContact ID
     */
    private fun getRawContactId(contactId: Long): Long? {
        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                "${ContactsContract.RawContacts._ID} ASC LIMIT 1"
            )

            if (cursor?.moveToFirst() == true) {
                return cursor.getLong(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 RawContact ID 失败", e)
        } finally {
            cursor?.close()
        }

        return null
    }

    /**
     * 更新联系人姓名
     */
    private fun updateContactName(contactId: Long, displayName: String): Boolean {
        return try {
            val rawContactId = getRawContactId(contactId) ?: return false
            
            val values = ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
            }

            val rows = context.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(
                    rawContactId.toString(),
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                )
            )
            rows > 0
        } catch (e: Exception) {
            Log.e(TAG, "更新联系人姓名失败", e)
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

