package com.example.memo

import android.app.Application
import com.example.memo.data.AlarmHelper
import com.example.memo.receiver.DailyReminderScheduler
import com.example.memo.repository.RepositoryProvider
import com.example.memo.util.CrashReporter
import com.example.memo.util.OperationLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Application 类
 * 负责初始化全局依赖：日志、错误上报等
 */
class MemoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化 Repository 单例
        RepositoryProvider.init(this)

        // 初始化 Timber 日志
        initTimber()

        // 初始化全局异常捕获
        CrashReporter.init(this)

        // 初始化操作日志
        OperationLogger.init(this)

        // 应用启动时重新调度所有闹钟，确保进程被杀后闹钟不丢失
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                AlarmHelper.rescheduleAllAlarms(this@MemoApplication)
            }
        }

        // 应用启动时调度"每日 00:01 长期提醒检查"广播
        // （如果进程被杀或设备重启，重新调度保证链路不断）
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                DailyReminderScheduler.scheduleNext(this@MemoApplication)
            }
        }
    }

    private fun initTimber() {
        // Debug 模式：输出完整日志到 Logcat
        // Release 模式：只将 Warning 及以上级别写入本地日志文件
        if (isDebugBuild()) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
    }

    private fun isDebugBuild(): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Release 模式下的日志树
     * 只将 Warning/Error 级别的日志上报到 CrashReporter
     */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            // 只处理 Warning 及以上级别
            if (priority >= android.util.Log.WARN) {
                CrashReporter.report(tag, message, t)
            }
        }
    }

    companion object {
        lateinit var instance: MemoApplication
            private set
    }
}
