package com.example.memo.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.example.memo.receiver.AlarmReceiver
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.flow.first
import java.util.*

/**
 * 闹钟调度帮助类
 *
 * 负责通过 AlarmManager 设置、取消和重新调度闹钟。
 */
object AlarmHelper {

    private const val ACTION_ALARM = "com.example.memo.ACTION_ALARM"
    private const val EXTRA_ALARM_ID = "alarm_id"
    private const val EXTRA_SNOOZE_REMAINING = "snooze_remaining"

    /**
     * 设置或更新一个闹钟。
     *
     * @param context 上下文
     * @param alarm   闹钟对象
     */
    fun setAlarm(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) {
            cancelAlarm(context, alarm.id)
            return
        }
        val triggerTime = calculateNextTriggerTime(alarm)
        if (triggerTime <= 0) return
        schedule(context, alarm.id, triggerTime)
    }

    /**
     * 取消一个闹钟
     */
    fun cancelAlarm(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createAlarmPendingIntent(context, alarmId, 0)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * 设置稍后提醒。
     *
     * @param context       上下文
     * @param alarmId       原闹钟 ID
     * @param intervalMinutes 间隔分钟数
     * @param remaining     剩余提醒次数
     */
    fun scheduleSnooze(context: Context, alarmId: Long, intervalMinutes: Int, remaining: Int) {
        if (remaining <= 0) return
        val triggerTime = System.currentTimeMillis() + intervalMinutes * 60 * 1000L
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createAlarmPendingIntent(context, alarmId, remaining)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "请允许设置精确闹钟", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val info = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 计算闹钟下一次触发时间（毫秒）。
     * 如果 repeatDays == 0 表示一次性闹钟，返回当天或次日的触发时间。
     */
    fun calculateNextTriggerTime(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentDayBit = calendarDayToBit(now.get(Calendar.DAY_OF_WEEK))

        if (alarm.repeatDays == 0) {
            // 一次性：若今天时间已过则设置为明天
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        // 重复闹钟：找下一个选中的天
        var daysToAdd = 0
        var found = false
        for (i in 0..6) {
            val bit = (currentDayBit + i) % 7
            if (alarm.repeatDays.hasDay(bit)) {
                val candidate = target.clone() as Calendar
                candidate.add(Calendar.DAY_OF_YEAR, i)
                if (candidate.timeInMillis > now.timeInMillis) {
                    daysToAdd = i
                    found = true
                    break
                }
            }
        }

        if (!found) {
            // 绕到下周
            for (i in 1..7) {
                val bit = (currentDayBit + i) % 7
                if (alarm.repeatDays.hasDay(bit)) {
                    daysToAdd = i
                    break
                }
            }
        }

        target.add(Calendar.DAY_OF_YEAR, daysToAdd)
        return target.timeInMillis
    }

    private fun schedule(context: Context, alarmId: Long, triggerTime: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createAlarmPendingIntent(context, alarmId, 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(context, "请允许设置精确闹钟", Toast.LENGTH_SHORT).show()
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 使用 AlarmClock 在华为等 OEM 机型上更可靠，系统会将其视为高优先级闹钟
            val showIntent = PendingIntent.getActivity(
                context,
                alarmId.hashCode(),
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val info = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    private fun createAlarmPendingIntent(
        context: Context,
        alarmId: Long,
        snoozeRemaining: Int
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SNOOZE_REMAINING, snoozeRemaining)
        }
        val requestCode = alarmId.hashCode() + snoozeRemaining
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 将 Calendar 的 DAY_OF_WEEK 转换为位掩码使用的位索引。
     * Calendar.MONDAY=2 ... Calendar.SUNDAY=1
     */
    fun calendarDayToBit(dayOfWeek: Int): Int {
        return when (dayOfWeek) {
            Calendar.MONDAY -> Alarm.MONDAY
            Calendar.TUESDAY -> Alarm.TUESDAY
            Calendar.WEDNESDAY -> Alarm.WEDNESDAY
            Calendar.THURSDAY -> Alarm.THURSDAY
            Calendar.FRIDAY -> Alarm.FRIDAY
            Calendar.SATURDAY -> Alarm.SATURDAY
            Calendar.SUNDAY -> Alarm.SUNDAY
            else -> Alarm.MONDAY
        }
    }

    fun getAlarmId(intent: Intent?): Long {
        return intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
    }

    fun getSnoozeRemaining(intent: Intent?): Int {
        return intent?.getIntExtra(EXTRA_SNOOZE_REMAINING, 0) ?: 0
    }

    /**
     * 重新调度数据库中所有已启用的闹钟。
     * 用于保存闹钟、应用启动时确保 pending alarms 是最新的。
     */
    suspend fun rescheduleAllAlarms(context: Context) {
        val alarms = RepositoryProvider.getAlarmRepository().allAlarms.first()
        // 先取消所有闹钟，再重新调度
        alarms.forEach { alarm ->
            cancelAlarm(context, alarm.id)
        }
        alarms.filter { it.isEnabled }.forEach { alarm ->
            setAlarm(context.applicationContext, alarm)
        }
    }
}
