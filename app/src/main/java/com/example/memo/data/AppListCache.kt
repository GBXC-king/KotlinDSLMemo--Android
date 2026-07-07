package com.example.memo.data

import android.content.Context
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AppListCache {
    private const val TAG = "AppListCache"
    private const val CACHE_FILE_NAME = "app_list_cache.txt"
    private const val CACHE_VALIDITY_DAYS = 7
    private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"

    private var cachedApps: List<AppHelper.AppInfo>? = null
    private var lastUpdateTime: Long = 0

    private fun getCacheFile(context: Context): File {
        return File(context.filesDir, CACHE_FILE_NAME)
    }

    private fun getBeijingTime(): Long {
        val beijingTimeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.CHINA)
        dateFormat.timeZone = beijingTimeZone
        return System.currentTimeMillis()
    }

    private fun formatBeijingTime(timestamp: Long): String {
        val beijingTimeZone = TimeZone.getTimeZone("Asia/Shanghai")
        val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.CHINA)
        dateFormat.timeZone = beijingTimeZone
        return dateFormat.format(Date(timestamp))
    }

    private fun parseBeijingTime(timeStr: String): Long? {
        return try {
            val beijingTimeZone = TimeZone.getTimeZone("Asia/Shanghai")
            val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.CHINA)
            dateFormat.timeZone = beijingTimeZone
            dateFormat.parse(timeStr)?.time
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to parse time: $timeStr")
            null
        }
    }

    suspend fun getCachedApps(context: Context): List<AppHelper.AppInfo> {
        cachedApps?.let { return it }

        val cached = loadFromCache(context)
        if (cached != null) {
            cachedApps = cached
            return cached
        }

        val apps = refreshCache(context)
        return apps
    }

    suspend fun refreshCache(context: Context): List<AppHelper.AppInfo> {
        return withContext(Dispatchers.IO) {
            Timber.tag(TAG).d("Refreshing app list cache...")
            val apps = AppHelper.getInstalledApps(context)
            saveToCache(context, apps)
            cachedApps = apps
            lastUpdateTime = System.currentTimeMillis()
            Timber.tag(TAG).d("App cache refreshed with ${apps.size} apps")
            apps
        }
    }

    suspend fun updateCacheIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            val cacheFile = getCacheFile(context)
            if (!cacheFile.exists()) {
                Timber.tag(TAG).d("Cache file does not exist, refreshing...")
                refreshCache(context)
                return@withContext
            }

            val cached = loadFromCache(context)
            if (cached != null) {
                cachedApps = cached
            }

            val cacheTimeStr = cacheFile.readLines().firstOrNull()
            if (cacheTimeStr == null) {
                Timber.tag(TAG).d("Cache file is empty, refreshing...")
                refreshCache(context)
                return@withContext
            }

            val cacheTime = parseBeijingTime(cacheTimeStr)
            if (cacheTime == null) {
                Timber.tag(TAG).d("Failed to parse cache time, refreshing...")
                refreshCache(context)
                return@withContext
            }

            val currentTime = System.currentTimeMillis()
            val daysDiff = (currentTime - cacheTime) / (1000 * 60 * 60 * 24)
            if (daysDiff >= CACHE_VALIDITY_DAYS) {
                Timber.tag(TAG).d("Cache is ${daysDiff.toInt()} days old, refreshing...")
                refreshCache(context)
            } else {
                Timber.tag(TAG).d("Cache is valid, ${daysDiff.toInt()} days old")
            }
        }
    }

    private fun saveToCache(context: Context, apps: List<AppHelper.AppInfo>) {
        try {
            val cacheFile = getCacheFile(context)
            val currentTime = formatBeijingTime(System.currentTimeMillis())
            val lines = mutableListOf<String>()
            lines.add(currentTime)

            apps.forEach { app ->
                lines.add("${app.name}|${app.packageName}|${app.description}")
            }

            cacheFile.writeText(lines.joinToString("\n"))
            Timber.tag(TAG).d("Cache saved to ${cacheFile.absolutePath}")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save cache")
        }
    }

    private fun loadFromCache(context: Context): List<AppHelper.AppInfo>? {
        try {
            val cacheFile = getCacheFile(context)
            if (!cacheFile.exists()) {
                return null
            }

            val lines = cacheFile.readLines()
            if (lines.isEmpty()) {
                return null
            }

            val apps = mutableListOf<AppHelper.AppInfo>()
            for (i in 1 until lines.size) {
                val line = lines[i]
                val parts = line.split("|")
                if (parts.size >= 2) {
                    val name = parts[0]
                    val pkg = parts[1]
                    val desc = if (parts.size >= 3) parts[2] else ""
                    apps.add(AppHelper.AppInfo(name = name, packageName = pkg, description = desc))
                }
            }

            Timber.tag(TAG).d("Loaded ${apps.size} apps from cache")
            return apps
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load cache")
            return null
        }
    }

    fun clearCache(context: Context) {
        cachedApps = null
        lastUpdateTime = 0
        val cacheFile = getCacheFile(context)
        if (cacheFile.exists()) {
            cacheFile.delete()
        }
    }

    fun getCacheInfo(context: Context): String {
        val cacheFile = getCacheFile(context)
        if (!cacheFile.exists()) {
            return "缓存不存在"
        }

        return try {
            val lines = cacheFile.readLines()
            if (lines.isEmpty()) {
                return "缓存为空"
            }
            val cacheTime = lines[0]
            val appCount = lines.size - 1
            "缓存时间: $cacheTime\n应用数量: $appCount"
        } catch (e: Exception) {
            "读取缓存信息失败: ${e.message}"
        }
    }
}
