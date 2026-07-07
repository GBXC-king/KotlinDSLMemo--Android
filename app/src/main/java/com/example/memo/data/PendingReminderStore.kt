package com.example.memo.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 长期提醒本地存储
 *
 * 文件路径：context.filesDir/pending_reminders.json
 * 格式：
 * {
 *   "reminders": [
 *     {
 *       "id": "uuid",
 *       "year": 2026, "month": 1, "day": 3,
 *       "hour": 15, "minute": 0,
 *       "title": "上班", "content": "",
 *       "createdAt": 1735689600000
 *     }
 *   ]
 * }
 *
 * 写入是全量覆盖式（每次重新序列化整个数组），保证并发安全。
 */
object PendingReminderStore {

    private const val FILE_NAME = "pending_reminders.json"
    private const val KEY_REMINDERS = "reminders"

    @Synchronized
    fun loadAll(context: Context): List<LongTermReminder> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText())
            val arr = root.optJSONArray(KEY_REMINDERS) ?: return emptyList()
            val result = mutableListOf<LongTermReminder>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(
                    LongTermReminder(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        year = obj.optInt("year", 1970),
                        month = obj.optInt("month", 1),
                        day = obj.optInt("day", 1),
                        hour = obj.optInt("hour", 0),
                        minute = obj.optInt("minute", 0),
                        title = obj.optString("title", "提醒"),
                        content = obj.optString("content", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun add(context: Context, reminder: LongTermReminder) {
        val list = loadAll(context).toMutableList()
        list.add(reminder)
        saveAll(context, list)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val list = loadAll(context).filterNot { it.id == id }
        saveAll(context, list)
    }

    @Synchronized
    fun clear(context: Context) {
        saveAll(context, emptyList())
    }

    @Synchronized
    private fun saveAll(context: Context, list: List<LongTermReminder>) {
        try {
            val arr = JSONArray()
            list.forEach { r ->
                val obj = JSONObject()
                obj.put("id", r.id)
                obj.put("year", r.year)
                obj.put("month", r.month)
                obj.put("day", r.day)
                obj.put("hour", r.hour)
                obj.put("minute", r.minute)
                obj.put("title", r.title)
                obj.put("content", r.content)
                obj.put("createdAt", r.createdAt)
                arr.put(obj)
            }
            val root = JSONObject()
            root.put(KEY_REMINDERS, arr)
            File(context.filesDir, FILE_NAME).writeText(root.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
