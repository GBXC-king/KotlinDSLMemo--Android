package com.example.memo.ui.browser

import android.app.Activity
import android.content.Context
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * 视频全屏管理器
 *
 * 核心策略：
 * 1. 页面加载时预注入 JS，追踪所有 <video> 元素的尺寸（通过 loadedmetadata 事件）
 *    存储到 window.__memoVideoMeta，供全屏时读取
 * 2. 进入全屏时立即默认横屏（绝大多数全屏视频是横屏），再异步读取预追踪数据修正
 * 3. 横屏视频：SENSOR_LANDSCAPE，系统自动处理左右横屏切换
 * 4. 竖屏视频：PORTRAIT，锁定竖屏
 * 5. 退出全屏：恢复 USER 方向（跟随系统自动旋转设置）
 */
class VideoFullscreenManager {

    private var customView: View? = null
    private var originalOrientation: Int = 0
    private var orientationEventListener: OrientationEventListener? = null

    companion object {
        /**
         * 视频元数据追踪 JS，在 onPageFinished 时注入。
         * 监听所有 <video> 元素的 loadedmetadata 事件，存储尺寸到全局变量。
         * 使用 MutationObserver 捕获动态插入的视频。
         */
        fun getVideoTrackerJs(): String {
            return """
                (function() {
                    if (window.__memoVideoTracking) return;
                    window.__memoVideoTracking = true;

                    function trackVideo(v) {
                        if (v.__memoTracked) return;
                        v.__memoTracked = true;
                        function saveMeta() {
                            if (v.videoWidth > 0) {
                                window.__memoVideoMeta = {
                                    width: v.videoWidth,
                                    height: v.videoHeight,
                                    isLandscape: v.videoWidth > v.videoHeight
                                };
                            }
                        }
                        v.addEventListener('loadedmetadata', saveMeta);
                        v.addEventListener('loadeddata', saveMeta);
                        v.addEventListener('canplay', saveMeta);
                        saveMeta();
                    }

                    function trackAllVideos() {
                        try {
                            document.querySelectorAll('video').forEach(trackVideo);
                        } catch(e) {}
                    }

                    trackAllVideos();

                    if (window.MutationObserver) {
                        new MutationObserver(function() {
                            trackAllVideos();
                        }).observe(document.documentElement, {
                            childList: true, subtree: true
                        });
                    }
                })();
            """.trimIndent()
        }
    }

    /**
     * 进入全屏
     */
    fun enterFullscreen(activity: Activity, webView: WebView, videoView: View) {
        if (customView != null) {
            exitFullscreen(activity)
        }

        customView = videoView
        originalOrientation = activity.requestedOrientation

        // 挂载全屏视图到 DecorView
        val decorView = activity.window.decorView as FrameLayout
        decorView.addView(videoView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // 沉浸式全屏
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUi(activity)

        // 立即默认横屏（绝大多数全屏视频是横屏），解决查询延迟期间方向不对的问题
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        startOrientationListener(activity)

        // 异步读取预追踪的视频尺寸，若是竖屏视频则切换回竖屏
        webView.evaluateJavascript(
            """(window.__memoVideoMeta ? (window.__memoVideoMeta.isLandscape ? 'landscape' : 'portrait') : 'unknown')"""
        ) { result ->
            val cleaned = result?.removeSurrounding("\"")?.trim() ?: "unknown"
            if (cleaned == "portrait") {
                // 竖屏视频，切换回竖屏
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                stopOrientationListener()
            }
            // landscape 或 unknown：保持横屏（已设置）
        }
    }

    /**
     * 退出全屏
     */
    fun exitFullscreen(activity: Activity) {
        if (customView == null) return
        val decorView = activity.window.decorView as FrameLayout
        customView?.let { decorView.removeView(it) }
        customView = null

        stopOrientationListener()

        // 恢复屏幕方向（跟随系统自动旋转设置）
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER

        // 恢复系统 UI
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showSystemUi(activity)
    }

    /**
     * 当前是否处于全屏状态
     */
    fun isFullscreen(): Boolean = customView != null

    /**
     * 启用方向监听
     * SCREEN_ORIENTATION_SENSOR_LANDSCAPE 本身已依赖系统传感器自动切换左右横屏，
     * OrientationEventListener 保留用于将来扩展（如手动锁定方向）。
     */
    private fun startOrientationListener(context: Context) {
        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                // 系统的 SENSOR_LANDSCAPE 已自动处理左右横屏切换
            }
        }
        orientationEventListener?.enable()
    }

    private fun stopOrientationListener() {
        orientationEventListener?.disable()
        orientationEventListener = null
    }

    private fun hideSystemUi(activity: Activity) {
        val decorView = activity.window.decorView
        decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )
    }

    private fun showSystemUi(activity: Activity) {
        val decorView = activity.window.decorView
        decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
