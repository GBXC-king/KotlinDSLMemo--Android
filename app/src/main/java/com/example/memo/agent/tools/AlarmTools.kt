package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import com.example.memo.data.Alarm
import com.example.memo.data.AlarmHelper
import com.example.memo.data.repeatText
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 创建闹钟工具
 */
class CreateAlarmTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "create_alarm"
    override val description = "在应用内创建一个新的闹钟提醒"
    override val parameters = listOf(
        ParamSpec("title", ParamType.STRING, "闹钟标题/主题", required = false, defaultValue = "闹钟"),
        ParamSpec("hour", ParamType.INT, "闹钟小时（0-23），例如7点就传7，下午3点传15"),
        ParamSpec("minute", ParamType.INT, "闹钟分钟（0-59），默认0", required = false, defaultValue = "0"),
        ParamSpec("repeat_days", ParamType.INT, "重复天数位掩码：bit0=周一...bit6=周日；每天=127，工作日=31，周末=96，永不=0", required = false, defaultValue = "0"),
        ParamSpec("ringtone_type", ParamType.STRING, "铃声类型", required = false, defaultValue = "system", enumValues = listOf("system", "silent", "custom")),
        ParamSpec("vibrate", ParamType.BOOLEAN, "响铃时是否震动", required = false, defaultValue = "true"),
        ParamSpec("delete_after_dismiss", ParamType.BOOLEAN, "关闭提醒后是否删除（仅一次性闹钟有效）", required = false, defaultValue = "false"),
        ParamSpec("snooze_enabled", ParamType.BOOLEAN, "是否启用稍后提醒", required = false, defaultValue = "false"),
        ParamSpec("snooze_interval", ParamType.INT, "稍后提醒间隔（分钟）：5/10/15/30", required = false, defaultValue = "10"),
        ParamSpec("snooze_count", ParamType.INT, "稍后提醒次数：2/3/5/10", required = false, defaultValue = "5")
    )

    override val promptFragment = """
        ### 闹钟创建规则
        - 用户说"设个闹钟"、"几点叫我"、"定个闹钟"、"设闹钟"等明确要设闹钟时 → 使用 create_alarm
        - 用户说"提醒我"、"记个日程"、"安排一下"等 → 使用 create_event（会自动联动创建闹钟，不要重复调用 create_alarm）
        - **直接传 hour 和 minute，不要计算时间戳**。例如7点就传 hour=7，下午3点传 hour=15，9:30就传 hour=9 minute=30
        - repeat_days 是位掩码：bit0=周一，bit1=周二，...，bit6=周日。常用值：每天=127，工作日=31，周末=96，永不（一次性）=0
        - **铃声默认系统铃声即可，不要传 ringtone_type 参数**
        - **除非用户明确说"静音"、"不要响铃"、"我在图书馆/会议室"等安静场所，否则不要设置 ringtone_type=silent**
        - **明显的一次性提醒**（如"今晚9点写作业"、"明天早上10点开会"、"下午3点取快递"），repeat_days 设为 0（永不），同时 delete_after_dismiss 设为 true（提醒关闭后自动删除，避免闹钟列表堆积）
        - **重复性提醒**（如"每天早上7点叫我起床"、"每周一提醒我交周报"），repeat_days 设为对应掩码，delete_after_dismiss 设为 false
        - 稍后提醒（snooze_enabled）默认开启，提醒间隔和提醒次数使用默认值即可，不需要额外设置
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val title = params["title"]?.ifBlank { "闹钟" } ?: "闹钟"
        val hour = params["hour"]?.toIntOrNull()?.coerceIn(0, 23)
            ?: return ToolResult.failure("缺少小时参数（hour，0-23）")
        val minute = params["minute"]?.toIntOrNull()?.coerceIn(0, 59) ?: 0

        val repeatDays = params["repeat_days"]?.toIntOrNull()?.coerceIn(0, 127) ?: Alarm.NEVER_MASK
        val ringtoneType = parseRingtoneType(params["ringtone_type"])
        val vibrate = parseBoolean(params["vibrate"], true)
        val deleteAfterDismiss = parseBoolean(params["delete_after_dismiss"], false)
        val snoozeEnabled = parseBoolean(params["snooze_enabled"], false)
        val snoozeInterval = params["snooze_interval"]?.toIntOrNull() ?: 10
        val snoozeCount = params["snooze_count"]?.toIntOrNull() ?: 5

        val alarm = Alarm(
            hour = hour,
            minute = minute,
            title = title,
            repeatDays = repeatDays,
            ringtoneType = ringtoneType,
            vibrate = vibrate,
            deleteAfterDismiss = if (repeatDays == Alarm.NEVER_MASK) deleteAfterDismiss else false,
            snoozeEnabled = snoozeEnabled,
            snoozeInterval = snoozeInterval,
            snoozeCount = snoozeCount,
            isEnabled = true
        )

        return withContext(Dispatchers.IO) {
            try {
                val id = RepositoryProvider.getAlarmRepository().insertAlarm(alarm)
                val insertedAlarm = alarm.copy(id = id)
                AlarmHelper.setAlarm(deps.context, insertedAlarm)
                val timeStr = String.format("%02d:%02d", hour, minute)
                ToolResult.success(
                    "已创建闹钟：$title（时间：$timeStr，重复：${alarm.repeatText()}）",
                    data = mapOf("alarm_id" to id, "time" to timeStr)
                )
            } catch (e: Exception) {
                ToolResult.failure("创建闹钟失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun parseRingtoneType(value: String?): Int {
        return when (value?.lowercase()) {
            "silent" -> Alarm.RINGTONE_SILENT
            "custom" -> Alarm.RINGTONE_CUSTOM
            else -> Alarm.RINGTONE_SYSTEM
        }
    }
}

/**
 * 更新闹钟工具
 */
class UpdateAlarmTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "update_alarm"
    override val description = "修改指定ID的闹钟"
    override val parameters = listOf(
        ParamSpec("id", ParamType.LONG, "要修改的闹钟ID（必须）"),
        ParamSpec("title", ParamType.STRING, "新的标题", required = false),
        ParamSpec("hour", ParamType.INT, "新的小时（0-23）", required = false),
        ParamSpec("minute", ParamType.INT, "新的分钟（0-59）", required = false),
        ParamSpec("repeat_days", ParamType.INT, "新的重复天数位掩码", required = false),
        ParamSpec("ringtone_type", ParamType.STRING, "铃声类型：system/silent/custom", required = false, enumValues = listOf("system", "silent", "custom")),
        ParamSpec("vibrate", ParamType.BOOLEAN, "是否震动", required = false),
        ParamSpec("delete_after_dismiss", ParamType.BOOLEAN, "关闭后是否删除（仅一次性闹钟有效）", required = false),
        ParamSpec("snooze_enabled", ParamType.BOOLEAN, "是否启用稍后提醒", required = false),
        ParamSpec("snooze_interval", ParamType.INT, "稍后提醒间隔（分钟）", required = false),
        ParamSpec("snooze_count", ParamType.INT, "稍后提醒次数", required = false),
        ParamSpec("is_enabled", ParamType.BOOLEAN, "是否启用", required = false)
    )

    override val promptFragment = """
        ### 闹钟修改规则
        - 修改闹钟前，必须先调用 query_alarm 获取闹钟ID
        - 只传入需要修改的参数，未传入的参数保持原值
        - 修改完成后系统会自动重新调度该闹钟
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val idStr = params["id"] ?: return ToolResult.failure("缺少闹钟ID（必须参数）")
        val id = idStr.toLongOrNull() ?: return ToolResult.failure("闹钟ID格式不正确")

        return withContext(Dispatchers.IO) {
            try {
                val existing = RepositoryProvider.getAlarmRepository().getAlarmById(id)
                    ?: return@withContext ToolResult.failure("未找到ID为 $id 的闹钟")

                var alarm = existing

                params["title"]?.takeIf { it.isNotBlank() }?.let { alarm = alarm.copy(title = it) }
                params["hour"]?.toIntOrNull()?.coerceIn(0, 23)?.let { alarm = alarm.copy(hour = it) }
                params["minute"]?.toIntOrNull()?.coerceIn(0, 59)?.let { alarm = alarm.copy(minute = it) }
                params["repeat_days"]?.toIntOrNull()?.coerceIn(0, 127)?.let { alarm = alarm.copy(repeatDays = it) }
                params["ringtone_type"]?.let { alarm = alarm.copy(ringtoneType = parseRingtoneType(it)) }
                params["vibrate"]?.let { alarm = alarm.copy(vibrate = parseBoolean(it, alarm.vibrate)) }
                params["delete_after_dismiss"]?.let {
                    alarm = alarm.copy(deleteAfterDismiss = if (alarm.repeatDays == Alarm.NEVER_MASK) parseBoolean(it, alarm.deleteAfterDismiss) else false)
                }
                params["snooze_enabled"]?.let { alarm = alarm.copy(snoozeEnabled = parseBoolean(it, alarm.snoozeEnabled)) }
                params["snooze_interval"]?.toIntOrNull()?.let { alarm = alarm.copy(snoozeInterval = it) }
                params["snooze_count"]?.toIntOrNull()?.let { alarm = alarm.copy(snoozeCount = it) }
                params["is_enabled"]?.let { alarm = alarm.copy(isEnabled = parseBoolean(it, alarm.isEnabled)) }

                RepositoryProvider.getAlarmRepository().insertAlarm(alarm)
                AlarmHelper.setAlarm(deps.context, alarm)

                val timeStr = String.format("%02d:%02d", alarm.hour, alarm.minute)
                ToolResult.success(
                    "已修改闹钟【${alarm.title}】（时间：$timeStr，重复：${alarm.repeatText()}）",
                    data = mapOf("alarm_id" to alarm.id)
                )
            } catch (e: Exception) {
                ToolResult.failure("修改闹钟失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun parseRingtoneType(value: String?): Int {
        return when (value?.lowercase()) {
            "silent" -> Alarm.RINGTONE_SILENT
            "custom" -> Alarm.RINGTONE_CUSTOM
            else -> Alarm.RINGTONE_SYSTEM
        }
    }
}

/**
 * 删除闹钟工具
 */
class DeleteAlarmTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "delete_alarm"
    override val description = "删除指定ID的闹钟"
    override val parameters = listOf(
        ParamSpec("id", ParamType.LONG, "要删除的闹钟ID（必须）")
    )

    override val promptFragment = """
        ### 闹钟删除规则
        - 删除闹钟前，必须先调用 query_alarm 获取闹钟ID
        - 删除成功后系统会自动取消已调度的提醒
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val idStr = params["id"] ?: return ToolResult.failure("缺少闹钟ID（必须参数）")
        val id = idStr.toLongOrNull() ?: return ToolResult.failure("闹钟ID格式不正确")

        return withContext(Dispatchers.IO) {
            try {
                val existing = RepositoryProvider.getAlarmRepository().getAlarmById(id)
                    ?: return@withContext ToolResult.failure("未找到ID为 $id 的闹钟，删除失败")

                RepositoryProvider.getAlarmRepository().deleteAlarm(existing)
                AlarmHelper.cancelAlarm(deps.context, id)
                ToolResult.success("已删除闹钟【${existing.title}】")
            } catch (e: Exception) {
                ToolResult.failure("删除闹钟失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}

/**
 * 查询闹钟工具
 */
class QueryAlarmTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "query_alarm"
    override val description = "查询应用内闹钟列表，支持按标题关键词或ID搜索"
    override val parameters = listOf(
        ParamSpec("keyword", ParamType.STRING, "标题搜索关键词", required = false),
        ParamSpec("id", ParamType.LONG, "闹钟ID", required = false)
    )

    override val promptFragment = """
        ### 闹钟查询规则
        - 修改或删除闹钟前，必须先调用 query_alarm 获取准确的闹钟ID
        - 返回结果中的 alarm_id 就是后续 update_alarm/delete_alarm 需要的 id 参数
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val id = params["id"]?.toLongOrNull()
                if (id != null) {
                    val alarm = RepositoryProvider.getAlarmRepository().getAlarmById(id)
                    return@withContext if (alarm != null) {
                        ToolResult.success(
                            "找到闹钟：${formatAlarm(alarm)}",
                            data = mapOf("alarm_id" to alarm.id)
                        )
                    } else {
                        ToolResult.failure("未找到ID为 $id 的闹钟")
                    }
                }

                val keyword = params["keyword"]
                val alarms = RepositoryProvider.getAlarmRepository().allAlarms.first()
                val filtered = if (keyword.isNullOrBlank()) {
                    alarms
                } else {
                    alarms.filter { it.title.contains(keyword, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    return@withContext ToolResult.failure(
                        if (keyword.isNullOrBlank()) "当前没有闹钟" else "未找到标题包含\"$keyword\"的闹钟"
                    )
                }

                val sb = StringBuilder("找到 ${filtered.size} 个闹钟：\n")
                filtered.sortedByDescending { it.createdAt }.take(20).forEach { alarm ->
                    sb.append(formatAlarm(alarm)).append("\n")
                }
                if (filtered.size > 20) {
                    sb.append("...还有${filtered.size - 20}个闹钟")
                }
                sb.append("\n\n💡 如需修改或删除，请告诉我闹钟ID")
                ToolResult.success(sb.toString().trim())
            } catch (e: Exception) {
                ToolResult.failure("查询闹钟失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun formatAlarm(alarm: Alarm): String {
        val timeStr = String.format("%02d:%02d", alarm.hour, alarm.minute)
        val status = if (alarm.isEnabled) "启用" else "禁用"
        return "【闹钟${alarm.id}】${alarm.title} $timeStr ${alarm.repeatText()}（$status）"
    }
}

private fun parseBoolean(value: String?, defaultValue: Boolean): Boolean {
    return when (value?.trim()?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> defaultValue
    }
}
