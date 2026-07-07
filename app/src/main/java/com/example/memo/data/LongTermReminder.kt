package com.example.memo.data

/**
 * 长期提醒条目
 *
 * 用于"超过 24 小时、无法直接创建闹钟"的提醒场景：
 * - AI 解析出目标日期时间后，本地比较发现距离当前时间 > 24 小时
 * - 写入本地 pending_reminders.json 暂存
 * - 每天 00:01 由 [com.example.memo.receiver.DailyReminderCheckReceiver] 遍历
 * - 若目标日期 == 当天则立刻创建应用内闹钟并从文件中移除
 *
 * @property id         唯一标识（UUID）
 * @property year       目标年份
 * @property month      目标月份（1-12）
 * @property day        目标日期（1-31）
 * @property hour       目标小时（0-23）
 * @property minute     目标分钟（0-59）
 * @property title      提醒标题
 * @property content    提醒内容/描述
 * @property createdAt  创建时间戳（毫秒）
 */
data class LongTermReminder(
    val id: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val title: String,
    val content: String,
    val createdAt: Long
)
