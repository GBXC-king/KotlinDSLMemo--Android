package com.example.memo.ui.browser

import android.content.Context
import android.util.Log
import android.webkit.WebView
import java.io.BufferedReader
import java.util.concurrent.atomic.AtomicReference

/**
 * 安全护盾：恶意/钓鱼 URL 拦截
 *
 * 工作机制：
 * 1. 启动时加载 assets/adblock/security.txt 到内存 Set
 * 2. 拦截 shouldOverrideUrlLoading：命中恶意库 → 加载原生告警页
 * 3. 拦截 shouldInterceptRequest：阻止对恶意域名的子资源请求
 * 4. 告警页是 data:text/html 渲染的纯 HTML，提供"返回"和"忽略继续"两个选项
 *
 * 体积控制：本地库只放已知高危域名（PhishTank/Google Safe Browsing 可后续接入）
 */
object SecurityShield {

    private const val TAG = "SecurityShield"
    private const val FILE_SECURITY = "adblock/security.txt"

    private val blockedSetRef = AtomicReference<Set<String>?>(null)

    /**
     * 加载恶意 URL 库
     */
    fun init(context: Context) {
        try {
            val text = context.assets.open(FILE_SECURITY).bufferedReader().use(BufferedReader::readText)
            val set = text.lines()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("!") }
                .toSet()
            blockedSetRef.set(set)
            Log.d(TAG, "Security rules loaded: ${set.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load security rules", e)
            blockedSetRef.set(emptySet())
        }
    }

    fun release() {
        // 保持内存中的规则库
    }

    /**
     * 判断 URL 是否在恶意库中
     * 匹配规则：
     *   1. 精确主机名匹配
     *   2. 子域匹配：malicious.com 命中 sub.malicious.com
     */
    fun isMalicious(url: String): Boolean {
        val set = blockedSetRef.get() ?: return false
        if (url.isBlank()) return false
        val host = extractHost(url) ?: return false
        val lower = host.lowercase()
        if (lower in set) return true
        // 子域匹配
        return set.any { lower == it || lower.endsWith(".$it") }
    }

    /**
     * 加载原生告警页到 WebView
     */
    fun showWarningPage(webView: WebView, originalUrl: String) {
        val html = buildWarningHtml(originalUrl)
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    /**
     * 告警页 HTML（含返回/继续两个选项）
     */
    private fun buildWarningHtml(url: String): String {
        val safeUrl = escapeHtml(url)
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>安全告警 - 备忘录浏览器</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
                    background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
                    color: #1f2937;
                    min-height: 100vh;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                }
                .container {
                    max-width: 480px;
                    width: 100%;
                    background: #ffffff;
                    border-radius: 16px;
                    box-shadow: 0 20px 60px rgba(0,0,0,0.08);
                    overflow: hidden;
                }
                .header {
                    background: #dc2626;
                    color: #fff;
                    padding: 32px 24px;
                    text-align: center;
                }
                .icon {
                    font-size: 56px;
                    line-height: 1;
                    margin-bottom: 12px;
                }
                .title {
                    font-size: 22px;
                    font-weight: 600;
                    margin-bottom: 4px;
                }
                .subtitle {
                    font-size: 14px;
                    opacity: 0.92;
                }
                .body {
                    padding: 24px;
                }
                .warning-text {
                    font-size: 15px;
                    line-height: 1.7;
                    color: #4b5563;
                    margin-bottom: 20px;
                }
                .url-box {
                    background: #f9fafb;
                    border: 1px solid #e5e7eb;
                    border-radius: 8px;
                    padding: 12px 16px;
                    word-break: break-all;
                    font-size: 13px;
                    color: #6b7280;
                    font-family: "SF Mono", Consolas, monospace;
                    margin-bottom: 24px;
                }
                .url-label {
                    font-size: 12px;
                    color: #9ca3af;
                    margin-bottom: 4px;
                    font-weight: 500;
                }
                .reasons {
                    background: #fff7ed;
                    border-left: 4px solid #f97316;
                    border-radius: 4px;
                    padding: 12px 16px;
                    margin-bottom: 24px;
                    font-size: 13px;
                    color: #9a3412;
                    line-height: 1.6;
                }
                .reasons ul {
                    margin-left: 18px;
                    margin-top: 4px;
                }
                .actions {
                    display: flex;
                    gap: 12px;
                }
                .btn {
                    flex: 1;
                    padding: 14px 20px;
                    border-radius: 10px;
                    border: none;
                    font-size: 15px;
                    font-weight: 500;
                    cursor: pointer;
                    transition: opacity 0.2s;
                }
                .btn:active {
                    opacity: 0.7;
                }
                .btn-primary {
                    background: #2563eb;
                    color: #fff;
                }
                .btn-secondary {
                    background: #f3f4f6;
                    color: #4b5563;
                }
                .footer {
                    text-align: center;
                    font-size: 12px;
                    color: #9ca3af;
                    padding: 16px;
                    border-top: 1px solid #f3f4f6;
                }
            </style>
            </head>
            <body>
            <div class="container">
                <div class="header">
                    <div class="icon">⚠️</div>
                    <div class="title">已拦截高风险网站</div>
                    <div class="subtitle">备忘录浏览器安全护盾</div>
                </div>
                <div class="body">
                    <p class="warning-text">
                        该网址被识别为钓鱼/恶意站点。继续访问可能导致账号被盗、个人信息泄露或设备感染病毒。
                    </p>
                    <div class="url-box">
                        <div class="url-label">已拦截的 URL</div>
                        $safeUrl
                    </div>
                    <div class="reasons">
                        <strong>可能的风险：</strong>
                        <ul>
                            <li>伪装成银行/支付/快递等机构套取密码</li>
                            <li>诱导下载携带木马的可执行文件</li>
                            <li>窃取浏览器保存的 Cookie 与会话</li>
                        </ul>
                    </div>
                    <div class="actions">
                        <button class="btn btn-primary" onclick="AndroidShield.goBack()">返回上一页</button>
                        <button class="btn btn-secondary" onclick="AndroidShield.continueAnyway()">仍然访问</button>
                    </div>
                </div>
                <div class="footer">
                    规则数据来自本地安全库 · 命中域名：$safeUrl
                </div>
            </div>
            <script>
                // 与原生层通信
                if (window.AndroidShield) {
                    window.__shieldUrl = ${jsStringLiteral(url)};
                }
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun extractHost(url: String): String? {
        return try {
            val u = if (url.contains("://")) url else "http://$url"
            java.net.URI(u).host?.lowercase()
        } catch (e: Exception) {
            val noProto = url.substringAfter("://", "").substringBefore("/")
            noProto.substringAfter("@").lowercase().takeIf { it.isNotBlank() }
        }
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

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
}
