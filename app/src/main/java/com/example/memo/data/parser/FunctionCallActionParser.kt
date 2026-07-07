package com.example.memo.data.parser

import com.example.memo.data.AIAction
import com.example.memo.data.ActionType
import com.example.memo.data.Transaction
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Function Calling 解析器
 * 解析模型返回的 tool_calls，转换为 AIAction 列表
 */
class FunctionCallActionParser : ActionParser {

    override fun parse(response: String, toolCalls: List<ToolCall>?): List<AIAction> {
        if (toolCalls.isNullOrEmpty()) return emptyList()

        return toolCalls.mapNotNull { toolCall ->
            try {
                val args = JSONObject(toolCall.arguments)
                convertToAction(toolCall.functionName, args)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun convertToAction(functionName: String, args: JSONObject): AIAction? {
        return when (functionName) {
            "create_event" -> {
                val title = args.getString("title")
                val timeStr = args.getString("time")
                AIAction(
                    type = ActionType.CREATE_EVENT,
                    title = title,
                    content = title,
                    dateTime = parseDateTimeString(timeStr),
                    reminderMessage = "${title}时间到了"
                )
            }

            "create_note" -> AIAction(
                type = ActionType.CREATE_NOTE,
                title = args.getString("title"),
                content = args.optString("content", "")
            )

            "create_contact" -> AIAction(
                type = ActionType.CREATE_CONTACT,
                contactName = args.getString("name"),
                phoneNumber = args.getString("phone")
            )

            "delete_contact" -> AIAction(
                type = ActionType.DELETE_CONTACT,
                contactName = args.getString("name")
            )

            "search_contact" -> AIAction(
                type = ActionType.SEARCH_CONTACT,
                contactName = args.getString("name")
            )

            "search_phone_number" -> AIAction(
                type = ActionType.SEARCH_PHONE_NUMBER,
                phoneNumber = args.getString("phone")
            )

            "call_phone" -> {
                val target = args.getString("target")
                val isPhoneNumber = target.matches(Regex("1[3-9]\\d{9}"))
                if (isPhoneNumber) {
                    AIAction(type = ActionType.CALL_PHONE, phoneNumber = target)
                } else {
                    AIAction(type = ActionType.SEARCH_CONTACT_FOR_CALL, contactName = target)
                }
            }

            "create_ledger" -> AIAction(
                type = ActionType.CREATE_LEDGER,
                ledgerName = args.getString("name"),
                ledgerUnit = args.optString("unit", "元")
            )

            "delete_ledger" -> AIAction(
                type = ActionType.DELETE_LEDGER,
                ledgerName = args.getString("name")
            )

            "create_transaction" -> {
                val amount = args.optString("amount", "0").toDoubleOrNull() ?: 0.0
                val dateStr = args.optString("date", "")
                AIAction(
                    type = ActionType.CREATE_TRANSACTION,
                    ledgerName = args.getString("ledger_name"),
                    transactionType = Transaction.TYPE_EXPENSE,
                    transactionAmount = amount,
                    transactionNote = args.optString("note", ""),
                    transactionDate = parseDateString(dateStr)
                )
            }

            "match_ledger" -> AIAction(
                type = ActionType.MATCH_LEDGER,
                matchKeyword = args.getString("keyword")
            )

            "open_app" -> AIAction(
                type = ActionType.OPEN_APP,
                appName = args.getString("app_name")
            )

            "recommend_app" -> AIAction(
                type = ActionType.RECOMMEND_APP,
                recommendCategory = args.getString("category")
            )

            "watch_video" -> AIAction(
                type = ActionType.WATCH_VIDEO,
                videoQuery = args.getString("query"),
                videoMode = args.optString("mode", "search")
            )

            "flashlight" -> {
                val state = args.getString("state")
                if (state == "on") {
                    AIAction(type = ActionType.FLASHLIGHT_ON)
                } else {
                    AIAction(type = ActionType.FLASHLIGHT_OFF)
                }
            }

            else -> null
        }
    }

    private fun parseDateString(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            format.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            val cal = Calendar.getInstance()
            cal.time = format.parse(dateStr) ?: Date()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun parseDateTimeString(timeStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
            format.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            format.parse(timeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                val format2 = SimpleDateFormat("yyyy-MM-dd H:mm", Locale.CHINA)
                format2.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                format2.parse(timeStr)?.time ?: System.currentTimeMillis()
            } catch (e2: Exception) {
                System.currentTimeMillis()
            }
        }
    }
}
