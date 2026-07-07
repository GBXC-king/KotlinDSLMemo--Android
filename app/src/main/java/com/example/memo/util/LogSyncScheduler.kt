package com.example.memo.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import timber.log.Timber

/**
 * 日志定时同步管理器
 * 使用 AlarmManager 实现定时任务
 */
object LogSyncScheduler {

    private const val PREFS_NAME = "log_sync_prefs"
    private const val KEY_SMTP_HOST = "smtp_host"
    private const val KEY_SMTP_PORT = "smtp_port"
    private const val KEY_EMAIL_USER = "email_user"
    private const val KEY_EMAIL_PASSWORD = "email_password"
    private const val KEY_EMAIL_TO = "email_to"
    private const val KEY_SYNC_ENABLED = "sync_enabled"
    private const val KEY_SYNC_HOUR = "sync_hour"
    private const val KEY_SYNC_MINUTE = "sync_minute"

    private const val ACTION_SYNC_LOGS = "com.example.memo.ACTION_SYNC_LOGS"
    private const val REQUEST_CODE_SYNC = 1001

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }

    /**
     * 保存邮箱配置
     */
    fun saveEmailConfig(
        context: Context,
        smtpHost: String,
        smtpPort: String,
        emailUser: String,
        emailPassword: String,
        emailTo: String
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_SMTP_HOST, smtpHost)
            putString(KEY_SMTP_PORT, smtpPort)
            putString(KEY_EMAIL_USER, emailUser)
            putString(KEY_EMAIL_PASSWORD, emailPassword)
            putString(KEY_EMAIL_TO, emailTo)
            apply()
        }
    }

    /**
     * 获取邮箱配置
     * 未配置或配置为空时返回预设默认值，并写入 SharedPreferences
     */
    fun getEmailConfig(context: Context): EmailConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // 预设默认配置（用户指定）
        val defaultUser = "你的发件人邮箱"
        val defaultPassword = "你的邮箱授权码"
        val defaultTo = "你的收件人邮箱"

        val savedUser = prefs.getString(KEY_EMAIL_USER, "") ?: ""
        val savedPassword = prefs.getString(KEY_EMAIL_PASSWORD, "") ?: ""
        val savedTo = prefs.getString(KEY_EMAIL_TO, "") ?: ""

        // 如果未配置或关键字段为空，写入默认值
        if (savedUser.isBlank() || savedPassword.isBlank() || savedTo.isBlank()) {
            val userToSave = if (savedUser.isBlank()) defaultUser else savedUser
            val passwordToSave = if (savedPassword.isBlank()) defaultPassword else savedPassword
            val toToSave = if (savedTo.isBlank()) defaultTo else savedTo
            prefs.edit().apply {
                putString(KEY_SMTP_HOST, prefs.getString(KEY_SMTP_HOST, "smtp.qq.com") ?: "smtp.qq.com")
                putString(KEY_SMTP_PORT, prefs.getString(KEY_SMTP_PORT, "465") ?: "465")
                putString(KEY_EMAIL_USER, userToSave)
                putString(KEY_EMAIL_PASSWORD, passwordToSave)
                putString(KEY_EMAIL_TO, toToSave)
                apply()
            }
            return EmailConfig(
                smtpHost = prefs.getString(KEY_SMTP_HOST, "smtp.qq.com") ?: "smtp.qq.com",
                smtpPort = prefs.getString(KEY_SMTP_PORT, "465") ?: "465",
                emailUser = userToSave,
                emailPassword = passwordToSave,
                emailTo = toToSave
            )
        }

        return EmailConfig(
            smtpHost = prefs.getString(KEY_SMTP_HOST, "smtp.qq.com") ?: "smtp.qq.com",
            smtpPort = prefs.getString(KEY_SMTP_PORT, "465") ?: "465",
            emailUser = savedUser,
            emailPassword = savedPassword,
            emailTo = savedTo
        )
    }

    /**
     * 保存同步时间配置
     */
    fun saveSyncTime(context: Context, hour: Int, minute: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_SYNC_HOUR, hour)
            putInt(KEY_SYNC_MINUTE, minute)
            apply()
        }
    }

    /**
     * 获取同步时间配置
     */
    fun getSyncTime(context: Context): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hour = prefs.getInt(KEY_SYNC_HOUR, 8)
        val minute = prefs.getInt(KEY_SYNC_MINUTE, 0)
        return Pair(hour, minute)
    }

    /**
     * 设置定时同步开关
     */
    fun setSyncEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()

        if (enabled) {
            scheduleNextSync(context)
        } else {
            cancelSync(context)
        }
    }

    /**
     * 获取定时同步开关状态
     */
    fun isSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SYNC_ENABLED, false)
    }

    /**
     * 调度下一次同步
     */
    fun scheduleNextSync(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, LogSyncReceiver::class.java).apply {
            action = ACTION_SYNC_LOGS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SYNC,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 计算下次执行时间
        val (hour, minute) = getSyncTime(context)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // 如果今天的时间已过，设置为明天
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        // 设置每天重复执行
        val interval = AlarmManager.INTERVAL_DAY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        Timber.tag("LogSyncScheduler").d("已调度日志同步: ${dateFormat.format(Date(calendar.timeInMillis))}")
    }

    /**
     * 取消定时同步
     */
    fun cancelSync(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, LogSyncReceiver::class.java).apply {
            action = ACTION_SYNC_LOGS
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_SYNC,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        Timber.tag("LogSyncScheduler").d("已取消日志同步")
    }

    /**
     * 执行同步任务
     */
    fun performSync(context: Context) {
        scope.launch {
            try {
                val config = getEmailConfig(context)

                // 检查配置是否完整
                if (config.smtpHost.isBlank() || config.emailUser.isBlank() ||
                    config.emailPassword.isBlank() || config.emailTo.isBlank()) {
                    Timber.tag("LogSyncScheduler").w("邮箱配置不完整，跳过同步")
                    return@launch
                }

                // 打包日志
                val zipUri = LogZipHelper.zipLogs(context)
                if (zipUri == null) {
                    Timber.tag("LogSyncScheduler").w("打包日志失败，跳过同步")
                    return@launch
                }

                // 发送邮件
                val today = dateFormat.format(Date())
                val subject = "备忘录日志同步 - $today"
                val body = "这是来自备忘录应用的自动日志同步。\n\n同步时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())}"

                val (success, msg) = EmailSender.sendEmail(
                    context = context,
                    smtpHost = config.smtpHost,
                    smtpPort = config.smtpPort,
                    username = config.emailUser,
                    password = config.emailPassword,
                    toEmail = config.emailTo,
                    subject = subject,
                    body = body,
                    attachmentUri = zipUri
                )

                if (success) {
                    Timber.tag("LogSyncScheduler").d("日志同步成功")
                } else {
                    Timber.tag("LogSyncScheduler").e("日志同步失败: $msg")
                }

                // 调度下一次同步
                if (isSyncEnabled(context)) {
                    scheduleNextSync(context)
                }
            } catch (e: Exception) {
                Timber.tag("LogSyncScheduler").e(e, "执行同步任务异常")
            }
        }
    }

    /**
     * 邮箱配置数据类
     */
    data class EmailConfig(
        val smtpHost: String,
        val smtpPort: String,
        val emailUser: String,
        val emailPassword: String,
        val emailTo: String
    )
}

/**
 * 定时同步广播接收器
 */
class LogSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.memo.ACTION_SYNC_LOGS") {
            Timber.tag("LogSyncReceiver").d("收到同步广播，开始执行同步")
            LogSyncScheduler.performSync(context)
        }
    }
}
