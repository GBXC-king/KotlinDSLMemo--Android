package com.example.memo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.memo.data.AlarmHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设备启动完成广播接收器
 *
 * 系统重启后 AlarmManager 中保存的闹钟会丢失，因此在 BOOT_COMPLETED 时：
 * 1. 重新调度数据库中所有已启用的闹钟
 * 2. 重新调度"每日 00:01 长期提醒检查"广播（系统重启后链条会断，必须重建）
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                AlarmHelper.rescheduleAllAlarms(context.applicationContext)
            }
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                DailyReminderScheduler.scheduleNext(context.applicationContext)
            }
        }
    }
}
