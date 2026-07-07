package com.example.memo.data.parser

import com.example.memo.data.AIAction
import com.example.memo.data.ActionType
import com.example.memo.data.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 正则表达式解析器（注册表模式）
 * 通过注册表统一管理所有动作类型的正则匹配规则
 */
class RegexActionParser : ActionParser {
    
    /**
     * 动作模式定义
     * @param regex 匹配正则表达式
     * @param parser 解析函数，将匹配结果转换为 AIAction
     */
    private data class ActionPattern(
        val regex: Regex,
        val parser: (MatchResult) -> AIAction?
    )
    
    // 注册所有动作模式
    private val patterns = listOf(
        // 提醒
        ActionPattern(Regex("""\{提醒:"([^"]+)",时间:"([^"]+)"\}""")) { match ->
            val title = match.groupValues[1]
            val timeStr = match.groupValues[2]
            val dateTime = parseDateTimeString(timeStr)
            AIAction(
                type = ActionType.CREATE_EVENT,
                title = title,
                content = title,
                dateTime = dateTime,
                reminderMessage = "${title}时间到了"
            )
        },
        
        // 笔记
        ActionPattern(Regex("""\{笔记:"([^"]+)",内容:"([^"]+)"\}""")) { match ->
            AIAction(
                type = ActionType.CREATE_NOTE,
                title = match.groupValues[1],
                content = match.groupValues[2]
            )
        },
        
        // 联系人
        ActionPattern(Regex("""\{联系人:"([^"]+)",号码:"([^"]+)"\}""")) { match ->
            AIAction(
                type = ActionType.CREATE_CONTACT,
                contactName = match.groupValues[1],
                phoneNumber = match.groupValues[2]
            )
        },
        
        // 拨打电话（需要先判断是号码还是姓名）
        ActionPattern(Regex("""\{拨打电话:"([^"]+)"\}""")) { match ->
            val target = match.groupValues[1]
            val isPhoneNumber = target.matches(Regex("1[3-9]\\d{9}"))
            if (isPhoneNumber) {
                AIAction(type = ActionType.CALL_PHONE, phoneNumber = target)
            } else {
                AIAction(type = ActionType.SEARCH_CONTACT_FOR_CALL, contactName = target)
            }
        },
        
        // 删除联系人
        ActionPattern(Regex("""\{删除联系人:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.DELETE_CONTACT, contactName = match.groupValues[1])
        },
        
        // 查询联系人
        ActionPattern(Regex("""\{查询联系人:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.SEARCH_CONTACT, contactName = match.groupValues[1])
        },
        
        // 查询号码
        ActionPattern(Regex("""\{查询号码:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.SEARCH_PHONE_NUMBER, phoneNumber = match.groupValues[1])
        },
        
        // 新建账本
        ActionPattern(Regex("""\{新建账本:"([^"]+)",单位:"([^"]*)"\}""")) { match ->
            val name = match.groupValues[1]
            val unit = match.groupValues[2].ifBlank { "元" }
            AIAction(type = ActionType.CREATE_LEDGER, ledgerName = name, ledgerUnit = unit)
        },
        
        // 删除账本
        ActionPattern(Regex("""\{删除账本:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.DELETE_LEDGER, ledgerName = match.groupValues[1])
        },
        
        // 记账
        ActionPattern(Regex("""\{记账:"([^"]+)",金额:"([^"]+)",备注:"([^"]*)",日期:"([^"]*)"\}""")) { match ->
            val ledgerName = match.groupValues[1]
            val amountStr = match.groupValues[2]
            val note = match.groupValues[3]
            val dateStr = match.groupValues[4]
            val amount = amountStr.toDoubleOrNull() ?: 0.0
            val date = parseDateString(dateStr)
            AIAction(
                type = ActionType.CREATE_TRANSACTION,
                ledgerName = ledgerName,
                transactionType = Transaction.TYPE_EXPENSE,
                transactionAmount = amount,
                transactionNote = note,
                transactionDate = date
            )
        },
        
        // 匹配账本
        ActionPattern(Regex("""\{匹配账本:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.MATCH_LEDGER, matchKeyword = match.groupValues[1])
        },
        
        // 推荐应用
        ActionPattern(Regex("""\{推荐应用:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.RECOMMEND_APP, recommendCategory = match.groupValues[1])
        },
        
        // 看免费视频（必须在"看视频"之前）
        ActionPattern(Regex("""\{看免费视频:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.WATCH_VIDEO, videoQuery = match.groupValues[1], videoMode = "free")
        },
        
        // 看视频
        ActionPattern(Regex("""\{看视频:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.WATCH_VIDEO, videoQuery = match.groupValues[1], videoMode = "search")
        },
        
        // 打开应用
        ActionPattern(Regex("""\{打开应用:"([^"]+)"\}""")) { match ->
            AIAction(type = ActionType.OPEN_APP, appName = match.groupValues[1])
        },
        
        // 手电筒
        ActionPattern(Regex("""\{手电筒:"(开|关)"\}""")) { match ->
            val state = match.groupValues[1]
            if (state == "开") {
                AIAction(type = ActionType.FLASHLIGHT_ON)
            } else {
                AIAction(type = ActionType.FLASHLIGHT_OFF)
            }
        }
    )
    
    override fun parse(response: String, toolCalls: List<ToolCall>?): List<AIAction> {
        val actions = mutableListOf<AIAction>()
        
        // 遍历所有模式，提取动作
        for (pattern in patterns) {
            val matches = pattern.regex.findAll(response)
            for (match in matches) {
                val action = pattern.parser(match)
                if (action != null) {
                    actions.add(action)
                }
            }
        }
        
        return actions
    }
    
    /**
     * 清理响应文本，移除所有匹配的动作标记
     */
    fun cleanResponse(response: String): String {
        var cleaned = response
        for (pattern in patterns) {
            cleaned = cleaned.replace(pattern.regex, "")
        }
        return cleaned.trim()
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
