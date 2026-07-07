package com.example.memo.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * 每日 00:01 长期提醒检查调度器
 *
 * 负责通过 AlarmManager 在每天 00:01 触发 [DailyReminderCheckReceiver]，
 * 接收器处理完成后会再次调用本类调度下一天的 00:01，形成自循环。
 *
 * 关键点：
 * - 使用 setAlarmClock：在华为等 OEM 机型上被视为高优先级闹钟，系统杀进程后仍能恢复
 * - 设备重启后由 [BootReceiver] 重新调度
 * - 应用启动时由 [com.example.memo.MemoApplication] 重新调度
 */
object DailyReminderScheduler {

    private const val TAG = "DailyReminderScheduler"
    private const val REQUEST_CODE = 0x1A2B3C // 与常规闹钟的 hashCode 不会冲突的固定 requestCode

    /**
     * 调度下一次 00:01 触发。
     * 如果当前时间已过 00:01，则调度到明天的 00:01。
     */
    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "无精确闹钟权限，跳过调度每日检查（仅尝试，不弹权限申请以免打扰用户）")
            return
        }

        val triggerAt = nextCheckTime()
        val pendingIntent = buildPendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val showIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE + 1,
                context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val info = AlarmManager.AlarmClockInfo(triggerAt, showIntent)
            alarmManager.setAlarmClock(info, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
        Log.d(TAG, "已调度每日长期提醒检查: ${formatTime(triggerAt)}")
    }

    /**
     * 取消已调度的每日检查（用于调试或重置）。
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildPendingIntent(context))
        buildPendingIntent(context).cancel()
    }

    /**
     * 计算下一次 00:01 触发的毫秒时间戳。
     */
    fun nextCheckTime(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyReminderCheckReceiver::class.java).apply {
            action = DailyReminderCheckReceiver.ACTION_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(
            "%04d-%02d-%02d %02d:%02d:%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
}
