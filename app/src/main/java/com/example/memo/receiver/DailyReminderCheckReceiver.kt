package com.example.memo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.memo.data.Alarm
import com.example.memo.data.AlarmHelper
import com.example.memo.data.LongTermReminder
import com.example.memo.data.PendingReminderStore
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 每日 00:01 长期提醒检查广播接收器
 *
 * 触发流程：
 * 1. 读取 pending_reminders.json 中的所有长期提醒
 * 2. 遍历每条提醒：若其目标日期 == 当天本地日期 → 创建应用内闹钟并从文件中移除
 * 3. 处理完后调用 [DailyReminderScheduler.scheduleNext] 调度下一天的 00:01
 *
 * 注意：此接收器只在精确闹钟触发时被唤醒，运行完即结束，
 * 不依赖任何正在运行的 Activity / Service。
 */
class DailyReminderCheckReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CHECK = "com.example.memo.ACTION_DAILY_REMINDER_CHECK"
        private const val TAG = "DailyReminderCheck"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK) return

        val appContext = context.applicationContext

        // 接收器生命周期极短，用 GlobalScope 等价物来处理（CoroutineScope + Dispatchers.IO）
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                processTodayReminders(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "每日检查异常: ${e.message}", e)
            } finally {
                // 不论成功失败，都必须调度下一天的 00:01，否则链路会断
                DailyReminderScheduler.scheduleNext(appContext)
                pendingResult.finish()
            }
        }
    }

    /**
     * 处理当天需要响铃的长期提醒
     */
    private suspend fun processTodayReminders(context: Context) {
        val all = PendingReminderStore.loadAll(context)
        if (all.isEmpty()) {
            Log.d(TAG, "无长期提醒待处理")
            return
        }

        val now = Calendar.getInstance()
        val todayYear = now.get(Calendar.YEAR)
        val todayMonth = now.get(Calendar.MONTH) + 1
        val todayDay = now.get(Calendar.DAY_OF_MONTH)

        val todayList = all.filter {
            it.year == todayYear && it.month == todayMonth && it.day == todayDay
        }
        if (todayList.isEmpty()) {
            Log.d(TAG, "今天($todayYear-$todayMonth-$todayDay)无待响铃提醒")
            return
        }

        Log.d(TAG, "今天有 ${todayList.size} 条长期提醒需要响铃")
        todayList.forEach { reminder ->
            try {
                createAlarmForReminder(context, reminder)
            } catch (e: Exception) {
                Log.e(TAG, "为提醒[${reminder.title}]创建闹钟失败: ${e.message}", e)
            }
            // 无论成功失败都从文件移除，避免无限循环
            PendingReminderStore.remove(context, reminder.id)
        }
    }

    /**
     * 将长期提醒转换为应用内闹钟并调度。
     * 闹钟属性：一次性、关闭后自动删除、稍后提醒开启。
     */
    private suspend fun createAlarmForReminder(context: Context, reminder: LongTermReminder) {
        val alarm = Alarm(
            hour = reminder.hour,
            minute = reminder.minute,
            title = reminder.title.ifBlank { "提醒" },
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
        Log.d(
            TAG,
            "已为长期提醒创建闹钟: id=$id, title=${alarm.title}, " +
                "time=${reminder.hour}:${reminder.minute}"
        )
    }
}
