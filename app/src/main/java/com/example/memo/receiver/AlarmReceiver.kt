package com.example.memo.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.memo.data.AlarmHelper
import com.example.memo.service.AlarmService

/**
 * 闹钟广播接收器
 *
 * 当 AlarmManager 触发时接收广播，并启动 AlarmService 进行响铃提醒。
 * 即使应用进程被杀死，系统仍会发出此广播。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = AlarmHelper.getAlarmId(intent)
        val snoozeRemaining = AlarmHelper.getSnoozeRemaining(intent)

        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmService.EXTRA_SNOOZE_REMAINING, snoozeRemaining)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
