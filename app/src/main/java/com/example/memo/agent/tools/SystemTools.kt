package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import android.content.Context
import com.example.memo.data.Alarm
import com.example.memo.data.AlarmHelper
import com.example.memo.data.CalendarHelper
import com.example.memo.data.FlashlightHelper
import com.example.memo.data.LongTermReminder
import com.example.memo.data.PendingReminderStore
import com.example.memo.receiver.DailyReminderScheduler
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * 创建日程工具
 */
class CreateEventTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "create_event"
    override val description = "在系统日历中创建日程提醒"
    override val parameters = listOf(
        ParamSpec("title", ParamType.STRING, "日程标题", required = false, defaultValue = "日程提醒"),
        ParamSpec("content", ParamType.STRING, "日程描述内容", required = false, defaultValue = ""),
        ParamSpec("hour", ParamType.INT, "日程小时（0-23），例如9点传9，下午3点传15"),
        ParamSpec("minute", ParamType.INT, "日程分钟（0-59），默认0", required = false, defaultValue = "0"),
        ParamSpec("date", ParamType.STRING, "日期：today/tomorrow/具体日期(YYYY-MM-DD)，默认today", required = false, defaultValue = "today")
    )
    
    override val promptFragment = """
        ### 日程创建规则
        - 用户说"提醒我"、"记个日程"、"安排一下"等 → 使用 create_event
        - **直接传 hour 和 minute，不要计算时间戳**。例如9点传 hour=9，下午3点传 hour=15，9:30传 hour=9 minute=30
        - date 参数：今天传"today"，明天传"tomorrow"，具体日期传"2026-06-30"格式，默认today
        - 调用 create_event 创建日程时，系统会自动在应用内创建一个一次性闹钟进行联动提醒
        - 联动闹钟默认系统铃声、稍后提醒开启、提醒关闭后自动删除
        - **长期提醒自动处理**：如果目标时间距离当前时间 > 24 小时（即"后天"、"下周三"、"3月1号"等），系统不会立刻创建闹钟，而是把这条提醒写入本地 pending_reminders.json 暂存；每天 00:01 由系统自动检查当天是否有需要响铃的提醒，到了当天再自动创建闹钟并从文件移除。AI 无需做任何特殊处理，正常传 date/hour/minute 即可
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val title = params["title"] ?: "日程提醒"
        val content = params["content"] ?: ""
        val hour = params["hour"]?.toIntOrNull()?.coerceIn(0, 23)
            ?: return ToolResult.failure("缺少小时参数（hour，0-23）")
        val minute = params["minute"]?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val dateStr = params["date"] ?: "today"

        // 根据 date 参数计算具体时间戳
        val calendar = Calendar.getInstance().apply {
            val now = Calendar.getInstance()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            when (dateStr.lowercase()) {
                "today" -> {
                    // 如果今天的时间已过，自动顺延到明天
                    if (timeInMillis <= now.timeInMillis) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
                "tomorrow" -> {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
                else -> {
                    // 尝试解析 YYYY-MM-DD 格式
                    try {
                        val parts = dateStr.split("-")
                        if (parts.size == 3) {
                            set(Calendar.YEAR, parts[0].toInt())
                            set(Calendar.MONTH, parts[1].toInt() - 1)
                            set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                        }
                    } catch (e: Exception) {
                        return ToolResult.failure("日期格式不正确，请使用 today/tomorrow/YYYY-MM-DD")
                    }
                }
            }
        }
        val timestamp = calendar.timeInMillis
        val now = System.currentTimeMillis()
        val isLongTerm = (timestamp - now) > LONG_TERM_THRESHOLD_MILLIS

        // 1) 系统日历日程（无论长短期都创建，用户在系统日历里能看到）
        val eventSuccess = CalendarHelper.createCalendarEvent(deps.context, title, content, Date(timestamp))
        if (!eventSuccess) {
            return ToolResult.failure("创建日程失败，请检查日历权限")
        }

        // 2) 长期提醒：写入本地暂存文件，由每日 00:01 广播自动到点建闹钟
        if (isLongTerm) {
            val reminder = LongTermReminder(
                id = UUID.randomUUID().toString(),
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH),
                hour = hour,
                minute = minute,
                title = title,
                content = content,
                createdAt = now
            )
            try {
                PendingReminderStore.add(deps.context, reminder)
            } catch (e: Exception) {
                return ToolResult.failure("日程已创建，但记录长期提醒失败：${e.message ?: e.javaClass.simpleName}")
            }
            // 确保下一天 00:01 的检查任务已调度（如果还没调度）
            DailyReminderScheduler.scheduleNext(deps.context)

            val dateStrFmt = String.format(
                Locale.CHINA,
                "%04d-%02d-%02d %02d:%02d",
                reminder.year, reminder.month, reminder.day, reminder.hour, reminder.minute
            )
            return ToolResult.success(
                "已创建日程【$title】（系统日历：$dateStrFmt）。" +
                    "由于距离目标时间超过 24 小时，已记录为长期提醒，\n" +
                    "系统将在当天 00:01 自动为你创建闹钟。",
                data = mapOf(
                    "reminder_id" to reminder.id,
                    "is_long_term" to "true",
                    "target_time" to dateStrFmt
                )
            )
        }

        // 3) 24 小时内的短期提醒：立刻创建联动闹钟
        val alarmResult = createLinkedAlarm(deps.context, title, hour, minute)
        return if (alarmResult) {
            val timeStr = String.format("%02d:%02d", hour, minute)
            ToolResult.success("已创建日程和闹钟：$title（时间：$timeStr）")
        } else {
            ToolResult.success("已创建日程：$title，但联动闹钟创建失败")
        }
    }

    private suspend fun createLinkedAlarm(context: Context, title: String, hour: Int, minute: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val alarm = Alarm(
                    hour = hour,
                    minute = minute,
                    title = title,
                    repeatDays = Alarm.NEVER_MASK,
                    ringtoneType = Alarm.RINGTONE_SYSTEM,
                    vibrate = true,
                    deleteAfterDismiss = true,
                    snoozeEnabled = true,
                    snoozeInterval = 10,
                    snoozeCount = 5,
                    isEnabled = true
                )
                val id = RepositoryProvider.getAlarmRepository().insertAlarm(alarm)
                AlarmHelper.setAlarm(context, alarm.copy(id = id))
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    companion object {
        /** 长期提醒阈值：距离当前时间超过 24 小时则走"记录 + 每日 00:01 触发"路径 */
        private const val LONG_TERM_THRESHOLD_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}

/**
 * 手电筒控制工具
 */
class FlashlightTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "flashlight"
    override val description = "控制手电筒的开关"
    override val parameters = listOf(
        ParamSpec("action", ParamType.STRING, "操作类型", enumValues = listOf("on", "off"))
    )
    
    override val promptFragment = """
        ### 手电筒控制规则
        - 用户说"打开手电筒"、"开灯"、"照一下"等 → action="on"
        - 用户说"关闭手电筒"、"关灯"、"关掉手电"等 → action="off"
        - 如果打开失败，提示用户检查相机权限
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val action = params["action"] ?: return ToolResult.failure("缺少action参数")
        return when (action.lowercase()) {
            "on" -> {
                val success = FlashlightHelper.turnOn(deps.context)
                if (success) {
                    ToolResult.success("手电筒已打开")
                } else {
                    ToolResult.failure("打开手电筒失败，请检查相机权限")
                }
            }
            "off" -> {
                val success = FlashlightHelper.turnOff(deps.context)
                if (success) {
                    ToolResult.success("手电筒已关闭")
                } else {
                    ToolResult.failure("关闭手电筒失败")
                }
            }
            else -> ToolResult.failure("action参数只能是 on 或 off")
        }
    }
}

/**
 * 获取当前时间工具
 */
class CurrentTimeTool : AgentTool {
    override val name = "get_current_time"
    override val description = "获取当前的日期和时间"
    override val parameters = emptyList<ParamSpec>()
    
    override val promptFragment = """
        ### 时间查询规则
        - 用户问"几点了"、"今天星期几"、"现在什么时间"等 → 使用 get_current_time
        - 返回结果包含完整日期和时间信息
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val now = Date()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (EEEE)", Locale.CHINA)
        val calendar = Calendar.getInstance()
        val timestamp = now.time
        
        return ToolResult.success(
            "当前本地时间：${sdf.format(now)}\n" +
            "时间戳（毫秒）：$timestamp\n" +
            "年：${calendar.get(Calendar.YEAR)}\n" +
            "月：${calendar.get(Calendar.MONTH) + 1}\n" +
            "日：${calendar.get(Calendar.DAY_OF_MONTH)}\n" +
            "时：${calendar.get(Calendar.HOUR_OF_DAY)}\n" +
            "分：${calendar.get(Calendar.MINUTE)}\n" +
            "星期：${calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.CHINA)}"
        )
    }
}
