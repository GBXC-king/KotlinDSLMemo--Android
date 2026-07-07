package com.example.memo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 闹钟数据实体类
 *
 * 对应 Room 数据库中的 "alarms" 表，用于存储用户创建的闹钟。
 *
 * 重复天数使用位掩码表示：
 * - bit 0 = 周一
 * - bit 1 = 周二
 * - bit 2 = 周三
 * - bit 3 = 周四
 * - bit 4 = 周五
 * - bit 5 = 周六
 * - bit 6 = 周日
 *
 * 常用掩码：
 * - 每天   = 0b1111111 = 127
 * - 工作日 = 0b0011111 = 31
 * - 周末   = 0b1100000 = 96
 * - 永不   = 0
 *
 * @property id                闹钟唯一标识
 * @property hour              小时（0-23）
 * @property minute            分钟（0-59）
 * @property title             闹钟标题/主题
 * @property repeatDays        重复天位掩码
 * @property ringtoneType      铃声类型：0=系统默认，1=静音，2=本地音乐
 * @property ringtoneUri       本地音乐 URI（当 ringtoneType=2 时使用）
 * @property vibrate           响铃时是否震动
 * @property deleteAfterDismiss 关闭提醒后是否删除（仅对一次性闹钟有效）
 * @property snoozeEnabled     是否启用稍后提醒
 * @property snoozeInterval    稍后提醒间隔（分钟）
 * @property snoozeCount       稍后提醒次数
 * @property isEnabled         闹钟是否启用
 * @property createdAt         创建时间戳
 */
@Entity(
    tableName = "alarms",
    indices = [Index(value = ["createdAt"])]
)
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val title: String = "闹钟",
    val repeatDays: Int = 0,
    val ringtoneType: Int = RINGTONE_SYSTEM,
    val ringtoneUri: String = "",
    val vibrate: Boolean = true,
    val deleteAfterDismiss: Boolean = false,
    val snoozeEnabled: Boolean = false,
    val snoozeInterval: Int = 10,
    val snoozeCount: Int = 5,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val RINGTONE_SYSTEM = 0
        const val RINGTONE_SILENT = 1
        const val RINGTONE_CUSTOM = 2

        const val MONDAY = 0
        const val TUESDAY = 1
        const val WEDNESDAY = 2
        const val THURSDAY = 3
        const val FRIDAY = 4
        const val SATURDAY = 5
        const val SUNDAY = 6

        const val EVERYDAY_MASK = 0b1111111
        const val WEEKDAY_MASK = 0b0011111
        const val WEEKEND_MASK = 0b1100000
        const val NEVER_MASK = 0

        val SNOOZE_INTERVALS = listOf(5, 10, 15, 30)
        val SNOOZE_COUNTS = listOf(2, 3, 5, 10)
    }
}

/**
 * 判断某一位是否被选中
 */
fun Int.hasDay(dayBit: Int): Boolean {
    return (this shr dayBit) and 1 == 1
}

/**
 * 切换某一位的选中状态
 */
fun Int.toggleDay(dayBit: Int): Int {
    return this xor (1 shl dayBit)
}

/**
 * 设置某一位的选中状态
 */
fun Int.withDay(dayBit: Int, selected: Boolean): Int {
    return if (selected) {
        this or (1 shl dayBit)
    } else {
        this and (1 shl dayBit).inv()
    }
}

/**
 * 根据 repeatDays 返回可读的重复方式描述
 */
fun Alarm.repeatText(): String {
    return when (repeatDays) {
        Alarm.EVERYDAY_MASK -> "每天"
        Alarm.WEEKDAY_MASK -> "工作日"
        Alarm.WEEKEND_MASK -> "周末"
        Alarm.NEVER_MASK -> "永不"
        else -> {
            val dayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
            val selected = (0..6).filter { repeatDays.hasDay(it) }.map { dayNames[it] }
            if (selected.isEmpty()) "永不" else selected.joinToString("、")
        }
    }
}
