package com.example.memo.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

/**
 * 内置浏览器页面
 *
 * 集成四大能力：
 * 1. AdBlocker  - 广告拦截（RuleEngine 驱动的网络层 + cosmetic 美化层 + DOM 兜底）
 * 2. PrivacyProtector - 反指纹 + 第三方 Cookie 隔离 + 跟踪请求拦截
 * 3. SecurityShield - 恶意/钓鱼 URL 拦截 + 原生告警页
 * 4. PlayerIsolator - 视频播放页白名单重建（针对 freeokk 等影视站）
 *
 * @param initialUrl 初始加载的 URL
 * @param onClose 关闭按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    initialUrl: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val webViewRefState = remember { mutableStateOf<WebView?>(null) }
    val webViewRef by webViewRefState
    val currentUrlState = remember { mutableStateOf(initialUrl) }
    val currentUrl by currentUrlState
    val addressInputState = remember { mutableStateOf(initialUrl) }
    var addressInput by addressInputState
    val isLoadingState = remember { mutableStateOf(false) }
    val isLoading by isLoadingState
    val canGoBackState = remember { mutableStateOf(false) }
    val canGoBack by canGoBackState
    val canGoForwardState = remember { mutableStateOf(false) }
    val canGoForward by canGoForwardState
    val titleState = remember { mutableStateOf("") }
    val title by titleState

    // 记录"仍然访问"时使用：被警告拦截后用户点继续，原 URL 存这里
    val overrideUrlState = remember { mutableStateOf<String?>(null) }
    val overrideUrl by overrideUrlState

    // 视频全屏管理器
    val fullscreenManager = remember { VideoFullscreenManager() }

    // 进入页面：初始化三大拦截模块
    DisposableEffect(Unit) {
        AdBlocker.init(context)
        PrivacyProtector.init(context)
        SecurityShield.init(context)
        onDispose {
            // 规则快照保留在内存供下次复用
        }
    }

    BackHandler(enabled = true) {
        when {
            fullscreenManager.isFullscreen() -> {
                (context as? android.app.Activity)?.let { fullscreenManager.exitFullscreen(it) }
            }
            canGoBack && webViewRef?.canGoBack() == true -> {
                webViewRef?.goBack()
            }
            else -> {
                onClose()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶栏
        TopAppBar(
            title = {
                Text(
                    text = if (title.isNotBlank()) title else "内置浏览器",
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            },
            actions = {
                IconButton(
                    onClick = { webViewRef?.goBack() },
                    enabled = canGoBack
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "后退")
                }
                IconButton(
                    onClick = { webViewRef?.goForward() },
                    enabled = canGoForward
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "前进")
                }
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新")
                }
            }
        )

        // 地址栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFFF5F5F5)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = addressInput,
                    onValueChange = { addressInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val input = addressInput.trim()
                            val urlToLoad = when {
                                input.startsWith("http://") || input.startsWith("https://") -> input
                                input.contains(".") -> "https://$input"
                                else -> "https://www.baidu.com/s?wd=" +
                                        java.net.URLEncoder.encode(input, "UTF-8")
                            }
                            overrideUrlState.value = null
                            webViewRef?.loadUrl(urlToLoad)
                        }
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxSize(),
                    strokeWidth = 2.dp
                )
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                        blockNetworkImage = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "$userAgentString MemoBrowser/1.0"
                        mediaPlaybackRequiresUserGesture = false
                        setSupportMultipleWindows(false)
                        javaScriptCanOpenWindowsAutomatically = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }

                    // 隐私保护 WebSettings（Cookie 隔离、表单不保存、文件访问限制）
                    PrivacyProtector.applyWebSettings(this)

                    // JavascriptInterface 1：广告检测到时通知原生层点击刷新
                    addJavascriptInterface(
                        object {
                            @android.webkit.JavascriptInterface
                            fun requestRefresh() {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    webViewRefState.value?.reload()
                                }
                            }
                        },
                        "AndroidPlayer"
                    )

                    // JavascriptInterface 2：安全告警页与原生层通信
                    addJavascriptInterface(
                        object {
                            @android.webkit.JavascriptInterface
                            fun goBack() {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    webViewRefState.value?.let { wv ->
                                        if (wv.canGoBack()) wv.goBack() else onClose()
                                    }
                                }
                            }
                            @android.webkit.JavascriptInterface
                            fun continueAnyway() {
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    val url = overrideUrlState.value
                                    if (url != null) {
                                        // 标记为"用户已确认"，本轮不再做安全检查
                                        overrideUrlState.value = null
                                        webViewRefState.value?.loadUrl(url)
                                    }
                                }
                            }
                        },
                        "AndroidShield"
                    )

                    webViewClient = object : WebViewClient() {

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoadingState.value = true
                            url?.let {
                                currentUrlState.value = it
                                addressInputState.value = it
                            }

                            // 第一层：CSS 预隐藏（DOM 解析前就 display:none 已知广告 class）
                            val css = AdBlocker.getInjectionCss(currentUrl)
                            val earlyJs = PrivacyProtector.getEarlyInjectionJs()
                            val injectCssJs = """
                                (function() {
                                    try {
                                        var css = ${jsStringLiteral(css)};
                                        var early = ${jsStringLiteral(earlyJs)};
                                        if (early) {
                                            (new Function(early))();
                                        }
                                        if (css) {
                                            var style = document.getElementById('__memoAdBlockCss');
                                            if (!style) {
                                                style = document.createElement('style');
                                                style.id = '__memoAdBlockCss';
                                                style.type = 'text/css';
                                                (document.head || document.documentElement).appendChild(style);
                                            }
                                            style.textContent = (style.textContent || '') + '\n' + css;
                                        }
                                    } catch(e) {}
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(injectCssJs, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoadingState.value = false
                            canGoBackState.value = view?.canGoBack() == true
                            canGoForwardState.value = view?.canGoForward() == true
                            titleState.value = view?.title ?: ""
                            // 第二层：广告拦截 JS（含 cosmetic + DOM 兜底）
                            view?.evaluateJavascript(AdBlocker.getInjectionJs(currentUrl), null)
                            // 第三层：反指纹 JS
                            view?.evaluateJavascript(PrivacyProtector.getInjectionJs(), null)
                            // 视频元数据追踪（全屏用）
                            view?.evaluateJavascript(VideoFullscreenManager.getVideoTrackerJs(), null)
                            // 视频播放页白名单重建（仅 freeokk 等视频页触发）
                            view?.evaluateJavascript(PlayerIsolator.getInjectionJs(), null)
                        }

                        // 拦截外链跳转 + 恶意 URL
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val reqUrl = request?.url?.toString() ?: ""
                            if (reqUrl.isBlank()) return false
                            // 安全护盾：命中恶意库 → 渲染告警页
                            if (SecurityShield.isMalicious(reqUrl)) {
                                overrideUrlState.value = reqUrl
                                view?.let { SecurityShield.showWarningPage(it, reqUrl) }
                                return true
                            }
                            // link.php?u=base64 跳转：解码真实目标，外链全部拦截
                            if (isLinkPhpRedirect(reqUrl)) {
                                val decoded = decodeLinkPhpTarget(reqUrl)
                                if (decoded != null) {
                                    val decodedHost = extractHost(decoded)?.lowercase()
                                    val currentHost = extractHost(currentUrl)?.lowercase()
                                    // 目标不是当前站 → 拦截（防色情/赌博跳转）
                                    if (decodedHost != null && currentHost != null &&
                                        decodedHost != currentHost &&
                                        !decodedHost.endsWith(".$currentHost")) {
                                        view?.let { SecurityShield.showWarningPage(it, decoded) }
                                        return true
                                    }
                                }
                                // 解码失败或可疑：直接拦截
                                return true
                            }
                            // 广告拦截：命中拦截规则 → 阻止加载
                            if (AdBlocker.shouldBlock(reqUrl, currentUrl)) {
                                return true
                            }
                            return false
                        }

                        // 子资源请求拦截
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            // 安全护盾：直接阻止对恶意域名的子资源
                            if (SecurityShield.isMalicious(reqUrl)) {
                                return emptyResponse()
                            }
                            // 广告拦截
                            if (AdBlocker.shouldBlock(reqUrl, currentUrl)) {
                                return emptyResponse()
                            }
                            // 隐私保护：跟踪请求加 DNT 头
                            // (shouldInterceptRequest 不能直接改 header，仅作占位)
                            return null
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {}

                        override fun onReceivedTitle(view: WebView?, newTitle: String?) {
                            super.onReceivedTitle(view, newTitle)
                            newTitle?.let { titleState.value = it }
                        }

                        override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                            if (view == null) return
                            (context as? android.app.Activity)?.let { activity ->
                                fullscreenManager.enterFullscreen(activity, this@apply, view)
                            }
                        }

                        override fun onHideCustomView() {
                            (context as? android.app.Activity)?.let { activity ->
                                fullscreenManager.exitFullscreen(activity)
                            }
                        }
                    }

                    loadUrl(initialUrl)
                    webViewRefState.value = this
                }
            },
            update = { _ -> }
        )

        // 页面退出时清理
        DisposableEffect(Unit) {
            onDispose {
                (context as? android.app.Activity)?.let { activity ->
                    if (fullscreenManager.isFullscreen()) {
                        fullscreenManager.exitFullscreen(activity)
                    }
                }
                webViewRef?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    (parent as? ViewGroup)?.removeView(this)
                    destroy()
                }
                webViewRefState.value = null
            }
        }
    }
}

private fun emptyResponse(): WebResourceResponse {
    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}

/**
 * 将 Kotlin 字符串转换为 JS 字符串字面量
 * 用于在 evaluateJavascript 中嵌入多行文本（CSS/JS 规则）
 */
private fun jsStringLiteral(s: String): String {
    val sb = StringBuilder("'")
    for (c in s) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '\'' -> sb.append("\\'")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
    }
    sb.append("'")
    return sb.toString()
}

/**
 * 判断 URL 是否为 link.php?u=base64 跳转形式
 */
private fun isLinkPhpRedirect(url: String): Boolean {
    val u = url.lowercase()
    return u.contains("/link.php?") && (u.contains("u=") || u.contains("&u=") || u.contains("?u="))
}

/**
 * 解码 link.php?u=base64 后的真实目标 URL
 * 失败返回 null
 */
private fun decodeLinkPhpTarget(url: String): String? {
    return try {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return null
        val query = url.substring(queryStart + 1)
        val params = query.split('&')
        for (p in params) {
            val eq = p.indexOf('=')
            if (eq > 0 && p.substring(0, eq).equals("u", ignoreCase = true)) {
                var encoded = p.substring(eq + 1)
                // URL 解码（应对 u=aHR0cHM6Ly... 中的 % 编码）
                encoded = java.net.URLDecoder.decode(encoded, "UTF-8")
                // 处理 URL-safe base64
                encoded = encoded.replace('-', '+').replace('_', '/')
                // 补齐 padding
                while (encoded.length % 4 != 0) encoded += "="
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                return String(bytes, Charsets.UTF_8).trim()
            }
        }
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * 从 URL 中提取 host（去除协议、路径、端口）
 */
private fun extractHost(url: String): String? {
    if (url.isBlank()) return null
    return try {
        val u = if (url.contains("://")) url else "http://$url"
        java.net.URI(u).host?.lowercase()
    } catch (e: Exception) {
        url.substringAfter("://", "").substringBefore("/").substringAfter("@").lowercase()
            .takeIf { it.isNotBlank() }
    }
}
