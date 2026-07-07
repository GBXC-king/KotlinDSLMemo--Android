package com.example.memo.data

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object CalendarHelper {

    private const val TAG = "CalendarHelper"
    private const val DEFAULT_REMINDER_MINUTES = 5 // 默认提前5分钟提醒

    fun extractDateTimeFromTitle(title: String): Pair<String, Date?> {
        val regex = """(\d{4})\.(\d{2})\.(\d{2})\.(\d{2})\.(\d{2})""".toRegex()
        val matchResult = regex.find(title)

        if (matchResult != null) {
            val (year, month, day, hour, minute) = matchResult.destructured
            val dateTimeStr = "$year-$month-$day $hour:$minute"
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            val date = dateFormat.parse(dateTimeStr)

            val cleanTitle = title.replace(regex, "").trim()
            return Pair(cleanTitle, date)
        }

        return Pair(title, null)
    }

    private data class CalendarInfo(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val isPrimary: Boolean,
        val isVisible: Boolean,
        val canModify: Boolean,
        val calendarColor: Int
    )

    private fun getAvailableCalendars(context: Context): List<CalendarInfo> {
        val calendars = mutableListOf<CalendarInfo>()
        return try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.IS_PRIMARY,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CALENDAR_COLOR
            )

            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountNameCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val accountTypeCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val isPrimaryCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
                val visibleCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
                val accessLevelCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                val colorCol = it.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val displayName = it.getString(nameCol)
                    val accountName = it.getString(accountNameCol)
                    val accountType = it.getString(accountTypeCol)
                    val isPrimary = it.getInt(isPrimaryCol) == 1
                    val isVisible = it.getInt(visibleCol) == 1
                    val accessLevel = it.getInt(accessLevelCol)
                    val canModify = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    val color = it.getInt(colorCol)

                    calendars.add(
                        CalendarInfo(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            accountType = accountType,
                            isPrimary = isPrimary,
                            isVisible = isVisible,
                            canModify = canModify,
                            calendarColor = color
                        )
                    )

                    Timber.tag(TAG).d(
                        "发现日历: id=$id, name=$displayName, account=$accountName, " +
                            "type=$accountType, primary=$isPrimary, visible=$isVisible, " +
                            "canModify=$canModify, accessLevel=$accessLevel"
                    )
                }
            }

            calendars
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "查询日历列表失败")
            calendars
        }
    }

    private fun selectBestCalendar(calendars: List<CalendarInfo>): Long? {
        if (calendars.isEmpty()) {
            Timber.tag(TAG).e("没有找到任何日历")
            return null
        }

        val modifiableCalendars = calendars.filter { it.canModify }
        Timber.tag(TAG).d("可修改的日历数量: ${modifiableCalendars.size}")

        if (modifiableCalendars.isEmpty()) {
            Timber.tag(TAG).e("没有可修改的日历，尝试使用任意日历")
            return calendars.firstOrNull()?.id
        }

        val primaryCalendar = modifiableCalendars.find { it.isPrimary }
        if (primaryCalendar != null) {
            Timber.tag(TAG).d("选择主日历: ${primaryCalendar.displayName} (id=${primaryCalendar.id})")
            return primaryCalendar.id
        }

        val huaweiCalendars = modifiableCalendars.filter {
            it.accountType.contains("huawei", ignoreCase = true) ||
                it.accountName.contains("huawei", ignoreCase = true) ||
                it.displayName.contains("华为", ignoreCase = true)
        }
        if (huaweiCalendars.isNotEmpty()) {
            val best = huaweiCalendars.maxByOrNull { if (it.isVisible) 1 else 0 }!!
            Timber.tag(TAG).d("选择华为日历: ${best.displayName} (id=${best.id})")
            return best.id
        }

        val localCalendars = modifiableCalendars.filter {
            it.accountType == "LOCAL" ||
                it.accountType == "com.android.localcalendar" ||
                it.accountName == "Phone" ||
                it.accountName == "本地" ||
                it.accountName == "手机"
        }
        if (localCalendars.isNotEmpty()) {
            val best = localCalendars.maxByOrNull { if (it.isVisible) 1 else 0 }!!
            Timber.tag(TAG).d("选择本地日历: ${best.displayName} (id=${best.id})")
            return best.id
        }

        val visibleCalendars = modifiableCalendars.filter { it.isVisible }
        if (visibleCalendars.isNotEmpty()) {
            val best = visibleCalendars.first()
            Timber.tag(TAG).d("选择可见日历: ${best.displayName} (id=${best.id})")
            return best.id
        }

        val best = modifiableCalendars.first()
        Timber.tag(TAG).d("选择第一个可修改日历: ${best.displayName} (id=${best.id})")
        return best.id
    }

    private fun verifyEventExists(context: Context, eventId: Long): Boolean {
        return try {
            val projection = arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.CALENDAR_ID
            )

            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                "${CalendarContract.Events._ID} = ?",
                arrayOf(eventId.toString()),
                null
            )?.use { cursor ->
                val exists = cursor.count > 0
                Timber.tag(TAG).d("验证事件是否存在: eventId=$eventId, exists=$exists")

                if (exists && cursor.moveToFirst()) {
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE))
                    val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                    val calId = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))
                    Timber.tag(TAG).d("事件详情: title=$title, dtStart=$dtStart, calendarId=$calId")
                }

                return exists
            } ?: false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "验证事件存在性失败")
            false
        }
    }

    fun createCalendarEvent(context: Context, title: String, content: String, dateTime: Date): Boolean {
        return try {
            Timber.tag(TAG).d("开始创建日程: title=$title, dateTime=$dateTime")

            val calendars = getAvailableCalendars(context)
            val calendarId = selectBestCalendar(calendars)

            if (calendarId == null) {
                Timber.tag(TAG).e("无法找到可用的日历")
                return false
            }

            val calendar = Calendar.getInstance().apply {
                time = dateTime
            }

            val endTime = Calendar.getInstance().apply {
                time = dateTime
                add(Calendar.HOUR, 1)
            }

            val timeZone = TimeZone.getTimeZone("Asia/Shanghai")

            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, calendar.timeInMillis)
                put(CalendarContract.Events.DTEND, endTime.timeInMillis)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DESCRIPTION, content.ifBlank { "备忘录提醒" })
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.id)
                put(CalendarContract.Events.HAS_ALARM, 1)
                put(CalendarContract.Events.ALL_DAY, 0)
                put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                put(CalendarContract.Events.GUESTS_CAN_MODIFY, 0)
            }

            Timber.tag(TAG).d("插入事件到日历 $calendarId: ${calendar.timeInMillis} - ${endTime.timeInMillis}")

            val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            if (eventUri == null) {
                Timber.tag(TAG).e("创建日程失败: insert返回null")
                return false
            }

            val eventId = eventUri.lastPathSegment?.toLong()
            Timber.tag(TAG).d("日程创建成功, eventId=$eventId, uri=$eventUri")

            if (eventId != null) {
                val verified = verifyEventExists(context, eventId)
                if (!verified) {
                    Timber.tag(TAG).w("事件插入后查询不到，可能存在问题")
                }

                try {
                    val reminderValues = ContentValues().apply {
                        put(CalendarContract.Reminders.EVENT_ID, eventId)
                        put(CalendarContract.Reminders.MINUTES, DEFAULT_REMINDER_MINUTES)
                        put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                    }
                    val reminderUri = context.contentResolver.insert(
                        CalendarContract.Reminders.CONTENT_URI,
                        reminderValues
                    )
                    Timber.tag(TAG).d("日历提醒创建成功，提醒时间: ${DEFAULT_REMINDER_MINUTES}分钟")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "创建提醒失败，但日程已创建")
                }
            }

            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "创建日程异常")
            false
        }
    }
}
