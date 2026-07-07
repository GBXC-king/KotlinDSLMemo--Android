package com.example.memo.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.runBlocking
import timber.log.Timber

object AppHelper {

    data class AppInfo(
        val name: String,
        val packageName: String,
        val description: String = ""
    )

    // 内存缓存：5分钟TTL，避免每次调用都扫描已安装应用
    private var memoryCachedApps: List<AppInfo>? = null
    private var memoryCacheTimestamp: Long = 0L
    private const val MEMORY_CACHE_TTL_MS = 5 * 60 * 1000L

    // 从配置文件加载的数据（延迟加载）
    private var appCategoryMap: Map<String, String> = emptyMap()
    private var gameKeywords: List<String> = emptyList()
    private var videoApps: Map<String, List<String>> = emptyMap()
    private var shortVideoApps: Map<String, List<String>> = emptyMap()
    private var commonApps: Map<String, List<String>> = emptyMap()
    private var configLoaded = false

    /**
     * 加载配置（首次使用时自动加载）
     */
    private fun ensureConfigLoaded(context: Context) {
        if (configLoaded) return
        try {
            val config = runBlocking { AppConfigLoader.getConfig(context) }
            appCategoryMap = config.appCategoryMap
            gameKeywords = config.gameKeywords
            videoApps = config.videoApps
            shortVideoApps = config.shortVideoApps
            commonApps = config.commonApps
            configLoaded = true
        } catch (e: Exception) {
            Timber.e(e, "Failed to load config")
        }
    }

    fun getAppDescription(context: Context, appName: String): String {
        ensureConfigLoaded(context)
        val lowerName = appName.lowercase()
        for ((keyword, desc) in appCategoryMap) {
            if (lowerName.contains(keyword.lowercase()) || keyword.lowercase().contains(lowerName)) {
                return desc
            }
        }
        for (kw in gameKeywords) {
            if (lowerName.contains(kw.lowercase())) {
                return "游戏"
            }
        }
        return ""
    }

    /**
     * 获取所有已安装的应用列表
     * 优先使用内存缓存（5分钟TTL），缓存失效时重新扫描
     * 扫描时优先使用 getInstalledApplications，兼容性更好
     */
    fun getInstalledApps(context: Context): List<AppInfo> {
        // 检查内存缓存是否有效
        val cached = memoryCachedApps
        if (cached != null && System.currentTimeMillis() - memoryCacheTimestamp < MEMORY_CACHE_TTL_MS) {
            Timber.d("getInstalledApps: returning cached result (${cached.size} apps)")
            return cached
        }

        ensureConfigLoaded(context)
        val pm = context.packageManager
        val result = mutableMapOf<String, AppInfo>()

        // 方法1：使用 getInstalledApplications
        try {
            val appList = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in appList) {
                val appName = appInfo.loadLabel(pm)?.toString() ?: continue
                val packageName = appInfo.packageName
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent != null && !result.containsKey(packageName)) {
                    val desc = getAppDescription(context, appName)
                    result[packageName] = AppInfo(name = appName, packageName = packageName, description = desc)
                }
            }
        } catch (e: Exception) {
            Timber.w("getInstalledApplications failed: ${e.message}")
        }

        // 方法2：使用 queryIntentActivities 补充（MIUI 上方法1可能不完整）
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            for (resolveInfo in resolveInfos) {
                val appName = resolveInfo.loadLabel(pm)?.toString() ?: continue
                val packageName = resolveInfo.activityInfo.packageName
                if (!result.containsKey(packageName)) {
                    val desc = getAppDescription(context, appName)
                    result[packageName] = AppInfo(name = appName, packageName = packageName, description = desc)
                }
            }
        } catch (e: Exception) {
            Timber.w("queryIntentActivities failed: ${e.message}")
        }

        val finalList = result.values
            .distinctBy { it.packageName }
            .sortedBy { it.name }

        // 更新内存缓存
        memoryCachedApps = finalList
        memoryCacheTimestamp = System.currentTimeMillis()

        Timber.d("getInstalledApps: found ${finalList.size} apps, cache updated")

        return finalList
    }

    /**
     * 清除内存缓存（应用安装/卸载时调用）
     */
    fun invalidateMemoryCache() {
        memoryCachedApps = null
        memoryCacheTimestamp = 0L
        Timber.d("AppHelper memory cache invalidated")
    }

    /**
     * 根据应用名称搜索已安装的应用（模糊匹配）
     */
    fun searchInstalledApps(context: Context, keyword: String): List<AppInfo> {
        return searchInstalledAppsWithList(getInstalledApps(context), keyword)
    }

    /**
     * 根据应用名称搜索已安装的应用列表（模糊匹配）
     */
    fun searchInstalledAppsWithList(apps: List<AppInfo>, keyword: String): List<AppInfo> {
        val lowerKeyword = keyword.lowercase().trim()
        val matched = apps.filter { app ->
            val lowerName = app.name.lowercase()
            val lowerPkg = app.packageName.lowercase()
            // 双向匹配：应用名包含关键词，或关键词包含应用名
            lowerName.contains(lowerKeyword) ||
            lowerPkg.contains(lowerKeyword) ||
            lowerKeyword.contains(lowerName)
        }
        Timber.d("searchInstalledApps: keyword='$keyword', total=${apps.size}, matched=${matched.size}")
        return matched
    }

    /**
     * 获取"看电视"类别的推荐应用
     */
    fun getVideoAppsRecommendation(context: Context): List<String> {
        ensureConfigLoaded(context)
        return videoApps.keys.toList()
    }

    /**
     * 获取"刷视频"类别的推荐应用
     */
    fun getShortVideoAppsRecommendation(context: Context): List<String> {
        ensureConfigLoaded(context)
        return shortVideoApps.keys.toList()
    }

    /**
     * 根据应用名称查找对应的包名
     */
    fun findPackageNames(context: Context, appName: String): List<String> {
        ensureConfigLoaded(context)
        val allApps = videoApps + shortVideoApps + commonApps
        val lowerName = appName.lowercase()
        val result = mutableListOf<String>()

        allApps.forEach { (name, packages) ->
            if (name.lowercase().contains(lowerName) || lowerName.contains(name.lowercase())) {
                result.addAll(packages)
            }
        }

        return result.distinct()
    }

    /**
     * 检查应用是否已安装
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 打开应用
     */
    fun openApp(context: Context, packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 通过跨应用跳转协议（Scheme）打开应用的特定页面（如视频搜索页、免费专区）
     * 优先用 setPackage 限定目标应用；若失败则回退到普通打开应用主界面
     *
     * @param packageName 目标应用包名
     * @param deepLink    完整的 Scheme URI（如 "txvideo://search?key=庆余年"）
     * @return 是否成功跳转
     */
    fun openAppWithScheme(context: Context, packageName: String, deepLink: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).apply {
                if (isAppInstalled(context, packageName)) {
                    setPackage(packageName)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Timber.w("openAppWithScheme failed ($deepLink): ${e.message}, fallback to openApp")
            openApp(context, packageName)
        }
    }

    /**
     * 根据应用名称搜索已安装的应用，返回匹配的应用列表
     */
    fun searchAppsByName(context: Context, appName: String): List<AppInfo> {
        val installedApps = searchInstalledApps(context, appName)
        if (installedApps.isNotEmpty()) {
            return installedApps
        }

        Timber.w("searchAppsByName: no match for '$appName', trying predefined list")

        val packageNames = findPackageNames(context, appName)
        if (packageNames.isEmpty()) {
            Timber.w("searchAppsByName: no predefined package for '$appName'")
            return emptyList()
        }

        return packageNames
            .filter { isAppInstalled(context, it) }
            .mapNotNull { pkg ->
                val appInfo = getInstalledApps(context).find { it.packageName == pkg }
                appInfo ?: AppInfo(name = pkg, packageName = pkg, description = "")
            }
    }

    /**
     * 根据应用名称打开应用，返回成功打开的应用数量
     * 优先在已安装应用中搜索，其次使用预定义包名
     */
    fun openAppByName(context: Context, appName: String): Pair<Int, List<String>> {
        // 先在已安装应用中搜索
        val installedApps = searchInstalledApps(context, appName)
        if (installedApps.isNotEmpty()) {
            // 找到已安装的应用
            if (installedApps.size == 1) {
                val success = openApp(context, installedApps[0].packageName)
                return if (success) Pair(1, listOf(installedApps[0].packageName)) else Pair(0, emptyList())
            }
            // 多个匹配，返回列表供用户选择
            return Pair(installedApps.size, installedApps.map { it.packageName })
        }

        // 已安装应用中没找到，再用预定义包名尝试
        val packageNames = findPackageNames(context, appName)
        if (packageNames.isEmpty()) {
            return Pair(0, emptyList())
        }

        val validPackages = packageNames.filter { isAppInstalled(context, it) }
        if (validPackages.isEmpty()) {
            return Pair(0, emptyList())
        }

        // 如果只有一个，直接打开
        if (validPackages.size == 1) {
            val success = openApp(context, validPackages[0])
            return if (success) Pair(1, listOf(validPackages[0])) else Pair(0, emptyList())
        }

        // 如果有多个，返回列表供用户选择
        return Pair(validPackages.size, validPackages)
    }

    /**
     * 获取"看电视"类别的本机已安装应用
     */
    fun getInstalledVideoApps(context: Context): List<AppInfo> {
        ensureConfigLoaded(context)
        val allInstalled = getInstalledApps(context)
        val videoKeywords = videoApps.keys
        return allInstalled.filter { app ->
            videoKeywords.any { keyword ->
                app.name.contains(keyword, ignoreCase = true) ||
                keyword.contains(app.name, ignoreCase = true)
            }
        }
    }

    /**
     * 获取"刷视频"类别的本机已安装应用
     */
    fun getInstalledShortVideoApps(context: Context): List<AppInfo> {
        ensureConfigLoaded(context)
        val allInstalled = getInstalledApps(context)
        val shortVideoKeywords = shortVideoApps.keys
        return allInstalled.filter { app ->
            shortVideoKeywords.any { keyword ->
                app.name.contains(keyword, ignoreCase = true) ||
                keyword.contains(app.name, ignoreCase = true)
            }
        }
    }

    /**
     * 获取应用名称
     */
    fun getAppName(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            val ai = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            null
        }
    }
}
