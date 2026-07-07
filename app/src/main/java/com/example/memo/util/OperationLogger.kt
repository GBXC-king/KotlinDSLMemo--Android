package com.example.memo.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 操作日志工具
 * 记录用户消息、AI回复、工具调用等操作信息
 * 存储在本地 Download/备忘录日志/ 目录下，按日期分文件
 */
object OperationLogger {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }

    private const val LOG_DIR_NAME = "备忘录日志"
    private const val OP_LOG_PREFIX = "op_"

    /**
     * 初始化
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * 记录用户发送的消息
     */
    fun logUserMessage(message: String) {
        writeEntry("USER", message)
    }

    /**
     * 记录AI回复
     */
    fun logAIResponse(response: String) {
        writeEntry("AI", response)
    }

    /**
     * 记录工具调用
     */
    fun logToolCall(toolName: String, params: Map<String, String>?, result: String?) {
        val paramsStr = params?.entries?.joinToString(", ") { "${it.key}=${it.value}" } ?: "无参数"
        val entry = buildString {
            appendLine("工具: $toolName")
            appendLine("参数: $paramsStr")
            if (!result.isNullOrBlank()) {
                appendLine("结果: $result")
            }
        }
        writeEntry("TOOL", entry)
    }

    /**
     * 记录Agent步骤
     */
    fun logAgentStep(stepIndex: Int, thought: String?, action: String?, actionInput: Map<String, String>?, observation: String?) {
        val entry = buildString {
            appendLine("--- Agent 步骤 $stepIndex ---")
            if (!thought.isNullOrBlank()) appendLine("思考: $thought")
            if (!action.isNullOrBlank()) {
                appendLine("动作: $action")
                if (!actionInput.isNullOrEmpty()) {
                    actionInput.forEach { (k, v) -> appendLine("  $k: $v") }
                }
            }
            if (!observation.isNullOrBlank()) appendLine("观察: $observation")
        }
        writeEntry("AGENT", entry)
    }

    /**
     * 记录错误
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val stackTrace = throwable?.let {
            val sw = java.io.StringWriter()
            it.printStackTrace(java.io.PrintWriter(sw))
            sw.toString()
        } ?: ""
        val entry = buildString {
            appendLine("错误: $message")
            if (stackTrace.isNotBlank()) appendLine(stackTrace)
        }
        writeEntry("ERROR[$tag]", entry)
    }

    /**
     * 写入日志条目
     */
    private fun writeEntry(tag: String, content: String) {
        scope.launch {
            try {
                val timestamp = dateFormat.format(Date())
                val entry = buildString {
                    appendLine("[$timestamp] [$tag]")
                    appendLine(content)
                    appendLine("---")
                }

                // 使用应用专属外部目录，不需要 MediaStore
                writeToFile(entry)
            } catch (_: Exception) {
                // 写入失败静默处理
            }
        }
    }

    private fun writeToFile(entry: String) {
        val fileName = "${OP_LOG_PREFIX}${fileDateFormat.format(Date())}.txt"
        // 使用应用专属外部目录
        val logDir = File(appContext.getExternalFilesDir(null), LOG_DIR_NAME)
        if (!logDir.exists()) logDir.mkdirs()

        val logFile = File(logDir, fileName)
        logFile.appendText(entry)
    }
}
