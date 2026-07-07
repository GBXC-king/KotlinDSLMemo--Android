package com.example.memo.data

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import timber.log.Timber

/**
 * 电话辅助工具类
 *
 * 该类提供联系人搜索、拨打电话、创建联系人的功能。
 * 使用 object 关键字声明为 Kotlin 单例对象。
 *
 * 核心功能：
 * 1. 从标题中识别 "打电话给xxx" 或 "给xxx打电话" 格式
 * 2. 搜索通讯录中匹配的联系人
 * 3. 拨打电话或创建联系人
 */
object PhoneHelper {

    /**
     * 从标题中提取打电话相关的信息
     *
     * 支持的格式：
     * - "打电话给张三" -> name = "张三"
     * - "给张三打电话" -> name = "张三"
     * - "张三13912345678" -> name = "张三", phone = "13912345678"
     * - "13912345678张三" -> name = "张三", phone = "13912345678"
     * - "13912345678" -> phone = "13912345678"
     *
     * @param title 原始笔记标题
     * @return PhoneInfo 解析结果，包含清理后的标题、姓名、电话号码
     */
    fun extractPhoneInfoFromTitle(title: String): PhoneInfo {
        // 匹配电话号码的正则（中国大陆手机号格式：1开头的11位数字）
        val phoneRegex = """(1[3-9]\d{9})""".toRegex()

        // 匹配 "打电话给xxx" 或 "给xxx打电话" 格式
        val callPattern1 = """(?:打电话给|给)(.+)打电话""".toRegex()
        val callPattern2 = """(?:打电话给|给)(.+?)(?:打电话|$)""".toRegex()

        // 首先检查是否是"打电话给xxx"格式
        val callMatch1 = callPattern1.find(title)
        val callMatch2 = callPattern2.find(title)

        var name: String? = null
        var phone: String? = null
        var cleanTitle = title

        if (callMatch1 != null) {
            name = callMatch1.groupValues[1].trim()
            cleanTitle = title.replace(callMatch1.value, "").trim()
        } else if (callMatch2 != null) {
            val extracted = callMatch2.groupValues[1].trim()
            // 检查提取的内容是否是电话号码
            val phoneInExtracted = phoneRegex.find(extracted)
            if (phoneInExtracted != null) {
                phone = phoneInExtracted.groupValues[1]
                // 如果名字在电话号码后面或前面
                val namePart = extracted.replace(phoneRegex, "").trim()
                if (namePart.isNotEmpty()) {
                    name = namePart
                }
            } else {
                name = extracted
            }
            cleanTitle = title.replace(callMatch2.value, "").trim()
        }

        // 检查是否包含电话号码（可能在任意位置）
        val phoneMatch = phoneRegex.find(title)
        if (phoneMatch != null) {
            phone = phoneMatch.groupValues[1]
            // 如果还没提取到名字，尝试从电话号码前后获取
            if (name == null) {
                val beforePhone = title.substring(0, phoneMatch.range.first)
                val afterPhone = title.substring(phoneMatch.range.last + 1)
                val potentialName = (beforePhone + afterPhone).trim()
                if (potentialName.isNotEmpty() && !potentialName.matches(phoneRegex)) {
                    name = potentialName
                }
            }
        }

        return PhoneInfo(
            cleanTitle = cleanTitle.ifEmpty { null },
            name = name,
            phone = phone
        )
    }

    /**
     * 根据姓名搜索通讯录联系人
     * 先按完整名称搜索，如未找到精确匹配，则将名字拆分成单个汉字，
     * 搜索通讯录中包含任意一个汉字的相似联系人
     *
     * @param context 上下文
     * @param name 要搜索的姓名
     * @return 匹配的联系人列表
     */
    fun searchContactsByName(context: Context, name: String): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()

        // 先按完整名称搜索
        val exactCursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            ),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%$name%"),
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )

        exactCursor?.use {
            while (it.moveToNext()) {
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                addContactWithPhone(context, contactId, displayName, hasPhone, contacts)
            }
        }

        // 如果找到了完全匹配的（名字完全相同），且只有一个，直接返回
        val exactMatch = contacts.find { it.name == name }
        if (exactMatch != null && contacts.size == 1) {
            return contacts
        }

        // 否则，搜索相似联系人（名字中包含任意一个汉字）
        val chars = name.toCharArray().filter { it.isLetter() }.map { it.toString() }.distinct()
        if (chars.isNotEmpty()) {
            val seenIds = contacts.map { it.id }.toMutableSet()

            for (char in chars) {
                val charCursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    ),
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
                    arrayOf("%$char%"),
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
                )

                charCursor?.use {
                    while (it.moveToNext()) {
                        val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                        if (contactId !in seenIds) {
                            val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                            val hasPhone = it.getInt(it.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER))
                            if (addContactWithPhone(context, contactId, displayName, hasPhone, contacts)) {
                                seenIds.add(contactId)
                            }
                        }
                    }
                }
            }
        }

        return contacts.sortedWith(compareBy<ContactInfo> { contact ->
            when {
                contact.name == name -> 0
                contact.name.startsWith(name) || contact.name.endsWith(name) -> 1
                contact.name.contains(name) -> 2
                else -> 3
            }
        }.thenBy { it.name })
    }

    private fun addContactWithPhone(
        context: Context,
        contactId: String,
        displayName: String,
        hasPhone: Int,
        contacts: MutableList<ContactInfo>
    ): Boolean {
        if (hasPhone > 0) {
            val phoneCursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )

            phoneCursor?.use { pc ->
                while (pc.moveToNext()) {
                    val phoneNumber = pc.getString(pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    contacts.add(ContactInfo(id = contactId, name = displayName, phone = phoneNumber))
                    return true
                }
            }
            return false
        } else {
            contacts.add(ContactInfo(id = contactId, name = displayName, phone = null))
            return true
        }
    }

    /**
     * 根据电话号码搜索通讯录联系人
     *
     * @param context 上下文
     * @param phone 要搜索的电话号码
     * @return 匹配的联系人（如果有）
     */
    fun searchContactByPhone(context: Context, phone: String): ContactInfo? {
        // 规范化电话号码（去除所有非数字字符）
        val normalizedPhone = phone.replace(Regex("[^0-9]"), "")

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val contactId = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                val displayName = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val contactPhone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                val normalizedContactPhone = contactPhone.replace(Regex("[^0-9]"), "")

                // 模糊匹配：检查号码是否以相同数字结尾（兼容带区号和不带区号的情况）
                if (normalizedContactPhone.endsWith(normalizedPhone) || normalizedPhone.endsWith(normalizedContactPhone)) {
                    return ContactInfo(id = contactId, name = displayName, phone = contactPhone)
                }
            }
        }

        return null
    }

    /**
     * 创建新联系人
     *
     * @param context 上下文
     * @param name 联系人姓名
     * @param phone 联系人电话
     * @return true 创建成功，false 创建失败
     */
    fun createContact(context: Context, name: String, phone: String): Boolean {
        return try {
            // 先创建原始联系人
            val rawContactValues = ContentValues().apply {
                put(ContactsContract.RawContacts.ACCOUNT_TYPE, null as String?)
                put(ContactsContract.RawContacts.ACCOUNT_NAME, null as String?)
            }
            val rawContactUri = context.contentResolver.insert(ContactsContract.RawContacts.CONTENT_URI, rawContactValues)
            val rawContactId = rawContactUri?.lastPathSegment?.toLongOrNull() ?: return false

            // 添加姓名
            val nameValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
            }
            context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, nameValues)

            // 添加电话
            val phoneValues = ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
            }
            context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, phoneValues)

            true
        } catch (e: Exception) {
            Timber.e(e, "创建联系人失败")
            false
        }
    }

    /**
     * 更新联系人姓名
     *
     * @param context 上下文
     * @param contactId 联系人ID
     * @param newName 新的姓名
     * @return true 更新成功，false 更新失败
     */
    fun updateContactName(context: Context, contactId: String, newName: String): Boolean {
        return try {
            val nameValues = ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newName)
            }
            val rows = context.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                nameValues,
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            )
            rows > 0
        } catch (e: Exception) {
            Timber.e(e, "更新联系人姓名失败")
            false
        }
    }

    /**
     * 删除联系人
     *
     * @param context 上下文
     * @param contactId 联系人ID
     * @return true 删除成功，false 删除失败
     */
    fun deleteContact(context: Context, contactId: String): Boolean {
        return try {
            val rows = context.contentResolver.delete(
                ContactsContract.RawContacts.CONTENT_URI,
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId)
            )
            rows > 0
        } catch (e: Exception) {
            Timber.e(e, "删除联系人失败")
            false
        }
    }
}

/**
 * 电话信息数据类
 *
 * @property cleanTitle 清理后的标题（去除电话相关部分）
 * @property name 提取的姓名（如果有）
 * @property phone 提取的电话号码（如果有）
 */
data class PhoneInfo(
    val cleanTitle: String?,
    val name: String?,
    val phone: String?
)

/**
 * 联系人信息数据类
 *
 * @property id 联系人ID
 * @property name 联系人姓名
 * @property phone 联系人电话（如果没有则为空）
 */
data class ContactInfo(
    val id: String,
    val name: String,
    val phone: String?
)