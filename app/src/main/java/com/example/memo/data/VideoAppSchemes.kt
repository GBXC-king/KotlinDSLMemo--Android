package com.example.memo.data

import android.content.Context
import android.net.Uri
import java.net.URLEncoder

/**
 * 视频播放软件的跨应用跳转协议（Scheme）映射表
 *
 * 用于"我想看..."功能：根据用户已安装的视频软件，弹出选择框，
 * 用户选择后通过对应的 Scheme 跳转到该应用的搜索页或免费专区。
 */
object VideoAppSchemes {

    /**
     * @param displayName 应用显示名称
     * @param packageNames 可能的包名列表（用于检测是否已安装，取第一个已安装的）
     * @param searchScheme 搜索协议前缀（已包含参数名和=，如 "txvideo://search?key="），追加 URL 编码后的关键词
     * @param freeScheme 免费专区协议（如 "txvideo://free"），null 表示该应用无免费专区
     */
    data class VideoAppScheme(
        val displayName: String,
        val packageNames: List<String>,
        val searchScheme: String,
        val freeScheme: String?
    )

    // 从配置文件加载的数据（延迟加载）
    private var videoAppSchemes: List<VideoAppScheme> = emptyList()
    private var configLoaded = false

    /**
     * 加载配置（首次使用时自动加载）
     */
    private fun ensureConfigLoaded(context: Context) {
        if (configLoaded) return
        try {
            val config = kotlinx.coroutines.runBlocking { AppConfigLoader.getConfig(context) }
            videoAppSchemes = config.videoAppSchemes.map { scheme ->
                VideoAppScheme(
                    displayName = scheme.displayName,
                    packageNames = scheme.packageNames,
                    searchScheme = scheme.searchScheme,
                    freeScheme = scheme.freeScheme
                )
            }
            configLoaded = true
        } catch (e: Exception) {
            android.util.Log.e("VideoAppSchemes", "Failed to load config", e)
        }
    }

    /**
     * 获取本机已安装的视频软件列表
     */
    fun getInstalledVideoApps(context: Context): List<VideoAppScheme> {
        ensureConfigLoaded(context)
        return videoAppSchemes.filter { scheme ->
            scheme.packageNames.any { AppHelper.isAppInstalled(context, it) }
        }
    }

    /**
     * 返回该应用在本机已安装的包名（取 packageNames 中第一个已安装的），未安装返回 null
     */
    fun getInstalledPackageName(context: Context, scheme: VideoAppScheme): String? {
        return scheme.packageNames.firstOrNull { AppHelper.isAppInstalled(context, it) }
    }

    /**
     * 构建搜索跳转 URI：scheme 前缀 + URL 编码后的关键词
     * 例如 buildSearchUri("txvideo://search?key=", "庆余年") -> "txvideo://search?key=%E5%BA%86%E4%BD%99%E5%B9%B4"
     */
    fun buildSearchUri(searchScheme: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "$searchScheme$encoded"
    }

    /**
     * 判断一个 URI 字符串是否合法（可被 Uri.parse 解析且 scheme 非空）
     */
    fun isValidUri(uriStr: String): Boolean {
        return try {
            val uri = Uri.parse(uriStr)
            !uri.scheme.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }
}
