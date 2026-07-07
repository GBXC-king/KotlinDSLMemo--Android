package com.example.memo.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import timber.log.Timber

/**
 * 日志打包工具
 * 将"备忘录日志"文件夹下的日志文件打包成 ZIP
 */
object LogZipHelper {

    private const val BUFFER_SIZE = 4096
    private const val LOG_DIR_NAME = "备忘录日志"

    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }

    /**
     * 打包日志文件为 ZIP，写入应用缓存目录
     * @param context 上下文
     * @return 打包后的 ZIP 文件 Uri，失败返回 null
     */
    suspend fun zipLogs(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val timestamp = fileDateFormat.format(Date())
            val zipFileName = "备忘录日志_$timestamp.zip"

            // 写入缓存目录
            val cacheDir = File(context.cacheDir, "log_zip")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val zipFile = File(cacheDir, zipFileName)

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zipStream ->
                val logFiles = getLogFiles(context)
                if (logFiles.isEmpty()) {
                    Timber.tag("LogZipHelper").w("没有找到日志文件")
                }
                for (logFile in logFiles) {
                    addFileToZip(zipStream, logFile.first, logFile.second)
                }
            }

            if (zipFile.length() == 0L) {
                Timber.tag("LogZipHelper").w("ZIP 文件为空")
            }

            Timber.tag("LogZipHelper").d("日志打包完成: ${zipFile.absolutePath}, 大小: ${zipFile.length()} bytes")
            Uri.fromFile(zipFile)
        } catch (e: Exception) {
            Timber.tag("LogZipHelper").e(e, "打包日志失败")
            null
        }
    }

    /**
     * 获取日志文件列表（从应用专属外部目录直接读取）
     * @return List<Pair<File, String>> 文件对象和相对路径
     */
    private fun getLogFiles(context: Context): List<Pair<File, String>> {
        val result = mutableListOf<Pair<File, String>>()

        // 使用应用专属外部目录
        val logDir = File(context.getExternalFilesDir(null), LOG_DIR_NAME)

        if (logDir.exists() && logDir.isDirectory) {
            logDir.listFiles()?.filter {
                it.isFile && (it.name.startsWith("log_") || it.name.startsWith("op_")) && it.name.endsWith(".txt")
            }?.forEach {
                result.add(Pair(it, it.name))
            }
        }

        return result
    }

    /**
     * 将文件添加到 ZIP
     */
    private fun addFileToZip(zipStream: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                val entry = ZipEntry(entryName)
                zipStream.putNextEntry(entry)

                val buffer = ByteArray(BUFFER_SIZE)
                var count: Int
                while (bis.read(buffer, 0, BUFFER_SIZE).also { count = it } != -1) {
                    zipStream.write(buffer, 0, count)
                }

                zipStream.closeEntry()
            }
        }
    }
}
