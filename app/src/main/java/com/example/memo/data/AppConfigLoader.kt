package com.example.memo.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用配置加载器
 * 支持从本地 assets 和远程 URL 加载应用配置
 * 配置会缓存到本地，优先使用缓存
 */
object AppConfigLoader {
    private const val TAG = "AppConfigLoader"
    private const val CONFIG_FILE_NAME = "app_config_cache.json"
    private const val DEFAULT_REMOTE_URL = "https://example.com/api/app_config.json" // 可配置的远程 URL
    
    // 配置数据缓存
    private var cachedConfig: AppConfig? = null
    
    /**
     * 应用配置数据类
     */
    data class AppConfig(
        val version: String,
        val lastUpdated: String,
        val appCategoryMap: Map<String, String>,
        val gameKeywords: List<String>,
        val videoApps: Map<String, List<String>>,
        val shortVideoApps: Map<String, List<String>>,
        val commonApps: Map<String, List<String>>,
        val videoAppSchemes: List<VideoAppScheme>,
        val videoSites: List<VideoSite>
    )

    /**
     * 视频应用 Scheme 配置
     */
    data class VideoAppScheme(
        val displayName: String,
        val packageNames: List<String>,
        val searchScheme: String,
        val freeScheme: String?
    )

    /**
     * 内置浏览器影视站配置
     * searchUrlTemplate 中的 {keyword} 占位符会被替换为 URL 编码后的搜索关键词
     */
    data class VideoSite(
        val name: String,
        val searchUrlTemplate: String,
        val isDefault: Boolean
    )
    
    /**
     * 获取应用配置（优先缓存，其次本地，最后 assets 默认）
     *
     * 版本检查机制：当 assets 中的 version 比本地缓存新时，丢弃本地缓存，
     * 从 assets 重新加载并覆盖本地缓存。这样修改 app_config.json 并提升 version
     * 后，应用重启即可生效，无需用户手动清数据。
     */
    suspend fun getConfig(context: Context): AppConfig {
        // 1. 如果内存中有缓存，直接返回
        cachedConfig?.let {
            Log.d(TAG, "Using in-memory cached config")
            return it
        }

        // 2. 尝试从本地缓存文件加载
        val localConfig = loadFromLocalCache(context)
        if (localConfig != null) {
            // 版本检查：assets 版本更新时丢弃本地缓存
            val assetsConfig = loadFromAssets(context)
            if (assetsConfig != null && isVersionNewer(assetsConfig.version, localConfig.version)) {
                Log.d(TAG, "Assets version (${assetsConfig.version}) is newer than local (${localConfig.version}), using assets")
                cachedConfig = assetsConfig
                saveToLocalCache(context, assetsConfig)
                return assetsConfig
            }
            // 本地缓存版本相同或更新，使用本地缓存（可能包含远程更新）
            Log.d(TAG, "Loaded config from local cache (version=${localConfig.version})")
            cachedConfig = localConfig
            return localConfig
        }

        // 3. 从 assets 加载默认配置
        val defaultConfig = loadFromAssets(context)
        if (defaultConfig != null) {
            Log.d(TAG, "Loaded default config from assets")
            cachedConfig = defaultConfig
            // 保存到本地缓存
            saveToLocalCache(context, defaultConfig)
            return defaultConfig
        }

        // 4. 如果都失败，返回空配置
        Log.w(TAG, "Failed to load any config, using empty config")
        return createEmptyConfig()
    }

    /**
     * 比较版本号，判断 v1 是否比 v2 更新
     * 支持语义化版本号（如 1.0.0, 1.2.3），逐段比较整数大小。
     */
    private fun isVersionNewer(v1: String, v2: String): Boolean {
        return try {
            val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
            val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(parts1.size, parts2.size)) {
                val p1 = parts1.getOrElse(i) { 0 }
                val p2 = parts2.getOrElse(i) { 0 }
                if (p1 > p2) return true
                if (p1 < p2) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从远程 URL 更新配置
     */
    suspend fun updateFromRemote(context: Context, remoteUrl: String = DEFAULT_REMOTE_URL): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Updating config from remote: $remoteUrl")
                
                val url = URL(remoteUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val config = parseConfig(json)
                    
                    if (config != null) {
                        // 保存到本地缓存
                        saveToLocalCache(context, config)
                        cachedConfig = config
                        Log.d(TAG, "Successfully updated config from remote")
                        true
                    } else {
                        Log.w(TAG, "Failed to parse remote config")
                        false
                    }
                } else {
                    Log.w(TAG, "Remote config request failed: ${connection.responseCode}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating from remote", e)
                false
            }
        }
    }
    
    /**
     * 从本地缓存文件加载配置
     */
    private fun loadFromLocalCache(context: Context): AppConfig? {
        return try {
            val cacheFile = File(context.filesDir, CONFIG_FILE_NAME)
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                parseConfig(json)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from local cache", e)
            null
        }
    }
    
    /**
     * 从 assets 加载默认配置
     */
    private fun loadFromAssets(context: Context): AppConfig? {
        return try {
            val json = context.assets.open("app_config.json").bufferedReader().use { it.readText() }
            parseConfig(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading from assets", e)
            null
        }
    }
    
    /**
     * 保存配置到本地缓存
     */
    private fun saveToLocalCache(context: Context, config: AppConfig) {
        try {
            val cacheFile = File(context.filesDir, CONFIG_FILE_NAME)
            val json = buildConfigJson(config)
            cacheFile.writeText(json)
            Log.d(TAG, "Saved config to local cache")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving to local cache", e)
        }
    }
    
    /**
     * 解析 JSON 配置
     */
    private fun parseConfig(json: String): AppConfig? {
        return try {
            val jsonObj = JSONObject(json)
            
            // 解析 appCategoryMap
            val appCategoryMap = mutableMapOf<String, String>()
            jsonObj.optJSONObject("appCategoryMap")?.let { categoryObj ->
                categoryObj.keys().forEach { key ->
                    appCategoryMap[key] = categoryObj.getString(key)
                }
            }
            
            // 解析 gameKeywords
            val gameKeywords = mutableListOf<String>()
            jsonObj.optJSONArray("gameKeywords")?.let { keywordsArray ->
                for (i in 0 until keywordsArray.length()) {
                    gameKeywords.add(keywordsArray.getString(i))
                }
            }
            
            // 解析 videoApps
            val videoApps = mutableMapOf<String, List<String>>()
            jsonObj.optJSONObject("videoApps")?.let { appsObj ->
                appsObj.keys().forEach { key ->
                    val packages = mutableListOf<String>()
                    val packagesArray = appsObj.getJSONArray(key)
                    for (i in 0 until packagesArray.length()) {
                        packages.add(packagesArray.getString(i))
                    }
                    videoApps[key] = packages
                }
            }
            
            // 解析 shortVideoApps
            val shortVideoApps = mutableMapOf<String, List<String>>()
            jsonObj.optJSONObject("shortVideoApps")?.let { appsObj ->
                appsObj.keys().forEach { key ->
                    val packages = mutableListOf<String>()
                    val packagesArray = appsObj.getJSONArray(key)
                    for (i in 0 until packagesArray.length()) {
                        packages.add(packagesArray.getString(i))
                    }
                    shortVideoApps[key] = packages
                }
            }
            
            // 解析 commonApps
            val commonApps = mutableMapOf<String, List<String>>()
            jsonObj.optJSONObject("commonApps")?.let { appsObj ->
                appsObj.keys().forEach { key ->
                    val packages = mutableListOf<String>()
                    val packagesArray = appsObj.getJSONArray(key)
                    for (i in 0 until packagesArray.length()) {
                        packages.add(packagesArray.getString(i))
                    }
                    commonApps[key] = packages
                }
            }
            
            // 解析 videoAppSchemes
            val videoAppSchemes = mutableListOf<VideoAppScheme>()
            jsonObj.optJSONArray("videoAppSchemes")?.let { schemesArray ->
                for (i in 0 until schemesArray.length()) {
                    val schemeObj = schemesArray.getJSONObject(i)
                    val displayName = schemeObj.getString("displayName")

                    val packageNames = mutableListOf<String>()
                    val packagesArray = schemeObj.getJSONArray("packageNames")
                    for (j in 0 until packagesArray.length()) {
                        packageNames.add(packagesArray.getString(j))
                    }

                    val searchScheme = schemeObj.getString("searchScheme")
                    val freeScheme = if (schemeObj.has("freeScheme") && !schemeObj.isNull("freeScheme")) {
                        schemeObj.getString("freeScheme")
                    } else {
                        null
                    }

                    videoAppSchemes.add(
                        VideoAppScheme(
                            displayName = displayName,
                            packageNames = packageNames,
                            searchScheme = searchScheme,
                            freeScheme = freeScheme
                        )
                    )
                }
            }

            // 解析 videoSites（内置浏览器影视站）
            val videoSites = mutableListOf<VideoSite>()
            jsonObj.optJSONArray("videoSites")?.let { sitesArray ->
                for (i in 0 until sitesArray.length()) {
                    val siteObj = sitesArray.getJSONObject(i)
                    videoSites.add(
                        VideoSite(
                            name = siteObj.getString("name"),
                            searchUrlTemplate = siteObj.getString("searchUrlTemplate"),
                            isDefault = siteObj.optBoolean("isDefault", false)
                        )
                    )
                }
            }

            AppConfig(
                version = jsonObj.optString("version", "1.0.0"),
                lastUpdated = jsonObj.optString("lastUpdated", ""),
                appCategoryMap = appCategoryMap,
                gameKeywords = gameKeywords,
                videoApps = videoApps,
                shortVideoApps = shortVideoApps,
                commonApps = commonApps,
                videoAppSchemes = videoAppSchemes,
                videoSites = videoSites
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing config", e)
            null
        }
    }
    
    /**
     * 构建配置 JSON（用于保存缓存）
     */
    private fun buildConfigJson(config: AppConfig): String {
        val jsonObj = JSONObject()
        jsonObj.put("version", config.version)
        jsonObj.put("lastUpdated", config.lastUpdated)
        
        // appCategoryMap
        val categoryObj = JSONObject()
        config.appCategoryMap.forEach { (key, value) ->
            categoryObj.put(key, value)
        }
        jsonObj.put("appCategoryMap", categoryObj)
        
        // gameKeywords
        val keywordsArray = org.json.JSONArray()
        config.gameKeywords.forEach { keywordsArray.put(it) }
        jsonObj.put("gameKeywords", keywordsArray)
        
        // videoApps
        val videoAppsObj = JSONObject()
        config.videoApps.forEach { (key, packages) ->
            val packagesArray = org.json.JSONArray()
            packages.forEach { packagesArray.put(it) }
            videoAppsObj.put(key, packagesArray)
        }
        jsonObj.put("videoApps", videoAppsObj)
        
        // shortVideoApps
        val shortVideoAppsObj = JSONObject()
        config.shortVideoApps.forEach { (key, packages) ->
            val packagesArray = org.json.JSONArray()
            packages.forEach { packagesArray.put(it) }
            shortVideoAppsObj.put(key, packagesArray)
        }
        jsonObj.put("shortVideoApps", shortVideoAppsObj)
        
        // commonApps
        val commonAppsObj = JSONObject()
        config.commonApps.forEach { (key, packages) ->
            val packagesArray = org.json.JSONArray()
            packages.forEach { packagesArray.put(it) }
            commonAppsObj.put(key, packagesArray)
        }
        jsonObj.put("commonApps", commonAppsObj)
        
        // videoAppSchemes
        val schemesArray = org.json.JSONArray()
        config.videoAppSchemes.forEach { scheme ->
            val schemeObj = JSONObject()
            schemeObj.put("displayName", scheme.displayName)

            val packagesArray = org.json.JSONArray()
            scheme.packageNames.forEach { packagesArray.put(it) }
            schemeObj.put("packageNames", packagesArray)

            schemeObj.put("searchScheme", scheme.searchScheme)
            scheme.freeScheme?.let { schemeObj.put("freeScheme", it) }

            schemesArray.put(schemeObj)
        }
        jsonObj.put("videoAppSchemes", schemesArray)

        // videoSites
        val sitesArray = org.json.JSONArray()
        config.videoSites.forEach { site ->
            val siteObj = JSONObject()
            siteObj.put("name", site.name)
            siteObj.put("searchUrlTemplate", site.searchUrlTemplate)
            siteObj.put("isDefault", site.isDefault)
            sitesArray.put(siteObj)
        }
        jsonObj.put("videoSites", sitesArray)

        return jsonObj.toString(2)
    }
    
    /**
     * 创建空配置
     */
    private fun createEmptyConfig(): AppConfig {
        return AppConfig(
            version = "1.0.0",
            lastUpdated = "",
            appCategoryMap = emptyMap(),
            gameKeywords = emptyList(),
            videoApps = emptyMap(),
            shortVideoApps = emptyMap(),
            commonApps = emptyMap(),
            videoAppSchemes = emptyList(),
            videoSites = emptyList()
        )
    }
    
    /**
     * 清除内存缓存（用于测试或强制刷新）
     */
    fun clearMemoryCache() {
        cachedConfig = null
    }
}
