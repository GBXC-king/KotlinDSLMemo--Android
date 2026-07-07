package com.example.memo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.memo.R
import com.example.memo.data.Alarm
import com.example.memo.data.AlarmHelper
import com.example.memo.repository.RepositoryProvider
import com.example.memo.ui.AlarmRingingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 闹钟响铃前台服务
 *
 * 负责播放铃声、震动、弹出全屏提醒界面，并处理关闭/稍后提醒操作。
 */
class AlarmService : Service() {

    companion object {
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SNOOZE_REMAINING = "snooze_remaining"

        const val ACTION_START = "com.example.memo.ACTION_ALARM_START"
        const val ACTION_STOP = "com.example.memo.ACTION_ALARM_STOP"
        const val ACTION_SNOOZE = "com.example.memo.ACTION_ALARM_SNOOZE"

        private const val CHANNEL_ID = "alarm_channel"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_TAG = "memo:alarm_wakelock"
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentAlarmId: Long = -1
    private var currentSnoozeRemaining: Int = 0
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> handleStop()
            ACTION_SNOOZE -> handleSnooze()
            else -> handleStart(intent)
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent?) {
        currentAlarmId = intent?.getLongExtra(EXTRA_ALARM_ID, -1L) ?: -1L
        currentSnoozeRemaining = intent?.getIntExtra(EXTRA_SNOOZE_REMAINING, 0) ?: 0

        // 必须先调 startForeground，否则 Android 12+ 会抛 ForegroundServiceDidNotStartInTimeException
        startForeground(NOTIFICATION_ID, buildNotification())

        if (currentAlarmId < 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // 先获取 WakeLock 并点亮屏幕，再启动界面
        acquireWakeLock()
        startRingingActivity()
        playRingtone()
        startVibrate()

        // 华为等机型在后台启动 Activity 可能被延迟，延迟再尝试一次拉起界面
        Handler(Looper.getMainLooper()).postDelayed({
            if (isRunning) {
                startRingingActivity()
            }
        }, 800)
    }

    private fun handleStop() {
        CoroutineScope(Dispatchers.IO).launch {
            val alarm = RepositoryProvider.getAlarmRepository().getAlarmById(currentAlarmId)
            if (alarm != null) {
                // 一次性闹钟且设置了关闭后删除，则删除该闹钟
                if (alarm.repeatDays == Alarm.NEVER_MASK && alarm.deleteAfterDismiss) {
                    RepositoryProvider.getAlarmRepository().deleteAlarm(alarm)
                } else if (alarm.repeatDays != Alarm.NEVER_MASK && alarm.isEnabled) {
                    // 重复闹钟重新调度下一次
                    AlarmHelper.setAlarm(this@AlarmService, alarm)
                }
            }
            stopAlarm()
        }
    }

    private fun handleSnooze() {
        CoroutineScope(Dispatchers.IO).launch {
            val alarm = RepositoryProvider.getAlarmRepository().getAlarmById(currentAlarmId)
            if (alarm != null && alarm.snoozeEnabled) {
                val remaining = if (currentSnoozeRemaining > 0) currentSnoozeRemaining - 1 else alarm.snoozeCount - 1
                AlarmHelper.scheduleSnooze(
                    this@AlarmService,
                    alarm.id,
                    alarm.snoozeInterval,
                    remaining
                )
            }
            stopAlarm()
        }
    }

    private fun stopAlarm() {
        isRunning = false
        stopRingtone()
        stopVibrate()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRingingActivity() {
        val activityIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, currentAlarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        try {
            startActivity(activityIntent)
        } catch (e: Exception) {
            // 华为等机型后台启动 Activity 受限时，通过全屏通知意图兜底
        }
    }

    private fun playRingtone() {
        CoroutineScope(Dispatchers.IO).launch {
            val alarm = RepositoryProvider.getAlarmRepository().getAlarmById(currentAlarmId)
            val ringtoneUri = when {
                alarm == null -> null
                alarm.ringtoneType == Alarm.RINGTONE_SILENT -> null
                alarm.ringtoneUri.isNotBlank() -> Uri.parse(alarm.ringtoneUri)
                else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }

            ringtoneUri?.let { uri ->
                ringtone = RingtoneManager.getRingtone(this@AlarmService, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        isLooping = true
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    play()
                }
            }
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private fun startVibrate() {
        CoroutineScope(Dispatchers.IO).launch {
            val alarm = RepositoryProvider.getAlarmRepository().getAlarmById(currentAlarmId)
            if (alarm?.vibrate != true) return@launch

            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
            }
        }
    }

    private fun stopVibrate() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        // 使用 SCREEN_DIM_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP 确保黑屏时亮屏
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK
                    or PowerManager.ACQUIRE_CAUSES_WAKEUP
                    or PowerManager.ON_AFTER_RELEASE,
            WAKE_LOCK_TAG
        ).apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "闹钟提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "闹钟响铃时显示的通知"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(this, AlarmService::class.java).apply { action = ACTION_SNOOZE }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, AlarmRingingActivity::class.java).apply {
            putExtra(EXTRA_ALARM_ID, currentAlarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            2,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("闹钟响了")
            .setContentText("点击查看")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "关闭", stopPendingIntent)
            .addAction(0, "稍后提醒", snoozePendingIntent)
            .setContentIntent(fullScreenPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
        }

        return builder.build()
    }
}
