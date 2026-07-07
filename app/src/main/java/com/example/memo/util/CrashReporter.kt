package com.example.memo.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import timber.log.Timber

/**
 * 统一错误日志工具
 * - 捕获未处理的全局异常（崩溃）
 * - 提供 Timber ReleaseTree 的日志写入入口
 * - 日志存储在本地 Download/备忘录日志/ 目录下，按日期分文件
 */
object CrashReporter {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }

    private val logDirName = "备忘录日志"

    /**
     * 初始化：注册全局异常捕获
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                report("UncaughtException", "Thread: ${thread.name}", throwable)
                // 崩溃时自动打包并发送日志邮件
                sendCrashReportEmail(throwable)
            } catch (_: Exception) {
                // 写入失败时忽略，确保原始处理器能执行
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 崩溃时自动发送日志邮件
     */
    private fun sendCrashReportEmail(throwable: Throwable) {
        scope.launch {
            try {
                // 等待日志写入完成
                kotlinx.coroutines.delay(1000)

                // 获取邮箱配置
                val config = LogSyncScheduler.getEmailConfig(appContext)
                if (config.smtpHost.isBlank() || config.emailUser.isBlank() ||
                    config.emailPassword.isBlank() || config.emailTo.isBlank()) {
                    return@launch
                }

                // 打包所有日志
                val zipUri = LogZipHelper.zipLogs(appContext) ?: return@launch

                // 构建错误信息
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val timestamp = dateFormat.format(Date())
                val subject = "【崩溃报告】备忘录应用 - $timestamp"
                val body = buildString {
                    appendLine("应用发生崩溃，以下是错误信息：")
                    appendLine()
                    appendLine("时间: $timestamp")
                    appendLine("异常类型: ${throwable.javaClass.name}")
                    appendLine("异常信息: ${throwable.message}")
                    appendLine()
                    appendLine("堆栈跟踪:")
                    appendLine(stackTrace)
                    appendLine()
                    appendLine("附件中包含完整的错误日志和操作日志。")
                }

                // 发送邮件
                val (success, msg) = EmailSender.sendEmail(
                    context = appContext,
                    smtpHost = config.smtpHost,
                    smtpPort = config.smtpPort,
                    username = config.emailUser,
                    password = config.emailPassword,
                    toEmail = config.emailTo,
                    subject = subject,
                    body = body,
                    attachmentUri = zipUri
                )
                if (!success) {
                    Timber.tag("CrashReporter").e("崩溃报告邮件发送失败: $msg")
                }
            } catch (e: Exception) {
                // 发送失败静默处理
            }
        }
    }

    /**
     * 写入错误日志到本地文件
     * @param tag 日志标签（类名或模块名）
     * @param message 错误描述
     * @param throwable 异常对象（可选）
     */
    fun report(tag: String?, message: String, throwable: Throwable? = null) {
        scope.launch {
            try {
                writeToFile(tag ?: "Unknown", message, throwable)
            } catch (_: Exception) {
                // 写入失败静默处理，避免循环
            }
        }
    }

    private fun writeToFile(tag: String, message: String, throwable: Throwable?) {
        val timestamp = dateFormat.format(Date())
        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        } ?: ""

        val entry = buildString {
            appendLine("[$timestamp] [$tag] $message")
            if (stackTrace.isNotBlank()) {
                appendLine(stackTrace)
            }
            appendLine("---")
        }

        // 统一使用应用专属外部目录
        val logDir = File(appContext.getExternalFilesDir(null), logDirName)
        if (!logDir.exists()) logDir.mkdirs()

        val fileName = "log_${fileDateFormat.format(Date())}.txt"
        val logFile = File(logDir, fileName)
        logFile.appendText(entry)
    }

    /**
     * 获取日志目录路径（供设置页面展示）
     */
    fun getLogDirPath(): String {
        return File(appContext.getExternalFilesDir(null), logDirName).absolutePath
    }

    /**
     * 清理超过指定天数的旧日志
     * @param days 保留天数，默认 7 天
     */
    fun cleanOldLogs(days: Int = 7) {
        scope.launch {
            try {
                cleanLegacyLogs(days)
            } catch (_: Exception) {
                // 清理失败静默处理
            }
        }
    }

    private fun cleanLegacyLogs(days: Int) {
        val logDir = File(appContext.getExternalFilesDir(null), logDirName)
        if (!logDir.exists()) return

        val cutoff = System.currentTimeMillis() - days * 24 * 60 * 60 * 1000L
        logDir.listFiles()?.filter {
            it.isFile && (it.name.startsWith("log_") || it.name.startsWith("op_")) && it.lastModified() < cutoff
        }?.forEach { it.delete() }
    }
}
