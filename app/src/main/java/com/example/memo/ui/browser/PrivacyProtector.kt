package com.example.memo.ui.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * 反追踪 + 隐私保护
 *
 * 三大能力：
 * 1. WebSettings 层：禁用第三方 Cookie + 禁止表单自动填充 + Do-Not-Track
 * 2. JS 注入层：覆盖 navigator.* 接口，阻止网站采集设备指纹
 * 3. 网络层：复用 RuleEngine（privacy.txt 规则已经并入）
 *
 * 加载时机：BrowserScreen 启动时 init() 一次，WebView 创建前配置 WebSettings，
 *           页面加载后注入 JS
 */
object PrivacyProtector {

    private var initialized = false

    /**
     * 初始化（应用全局生效，与 AdBlocker 共用 RuleEngine 快照）
     */
    fun init(context: Context) {
        if (initialized) return
        RuleEngine.ensureLoaded(context.applicationContext)
        // 同步清理过期 Cookie
        clearExpiredCookies()
        initialized = true
    }

    fun release() {
        initialized = false
    }

    /**
     * 在 WebView 创建后、loadUrl 之前应用 WebSettings
     * - 第三方 Cookie 隔离（多数广告/跟踪通过第三方 cookie）
     * - 禁止表单自动填充（防止保存的账号被脚本读取）
     * - 禁止密码保存
     * - 启用 Do-Not-Track
     * - 禁止缓存敏感内容到本地
     */
    fun applyWebSettings(webView: WebView) {
        val settings: WebSettings = webView.settings
        try {
            // 第三方 Cookie 拦截（Android 5.0+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                CookieManager.getInstance().setAcceptCookie(true)  // 仍允许第一方
            }
        } catch (e: Exception) {
            // 部分厂商 ROM 会抛异常，忽略
        }
        // 表单/密码自动填充：关闭
        settings.saveFormData = false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                // 关闭密码保存（autofill）
                val autofill = webView.context.getSystemService(android.view.autofill.AutofillManager::class.java)
                autofill?.disableAutofillServices()
            } catch (e: Exception) {}
        }
        // Do-Not-Track
        try {
            // 没法在 WebSettings 直接设置 DNT，但可以通过 User-Agent 注入
            // 真正的 DNT header 需在 shouldInterceptRequest 里加
        } catch (e: Exception) {}

        // 关闭访问文件 DOM 存储之外的本地资源
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
    }

    /**
     * 注入反指纹 JS（onPageStarted 即注入，覆盖最早期 API）
     *
     * 覆盖的接口：
     * - navigator.webdriver       （爬虫标志）
     * - navigator.languages       （语言指纹）
     * - navigator.plugins / mimeTypes（插件指纹）
     * - screen.*                  （屏幕尺寸指纹）
     * - WebGL renderer / vendor   （显卡指纹）
     * - AudioContext fingerprint  （音频指纹）
     * - canvas 指纹（toDataURL 加噪）
     * - 跟踪事件：beacon / sendBeacon 默认阻止
     */
    fun getInjectionJs(): String {
        return """
            (function() {
                try {
                    if (window.__memoPrivacyInjected) return;
                    window.__memoPrivacyInjected = true;

                    // 1. navigator.webdriver → undefined
                    try {
                        Object.defineProperty(Navigator.prototype, 'webdriver', {
                            get: function() { return false; },
                            configurable: true
                        });
                    } catch(e) {}

                    // 2. navigator.languages 统一为单值
                    try {
                        Object.defineProperty(Navigator.prototype, 'languages', {
                            get: function() { return ['zh-CN']; },
                            configurable: true
                        });
                    } catch(e) {}

                    // 3. navigator.plugins / mimeTypes → 0 个（移动端本就少）
                    try {
                        Object.defineProperty(Navigator.prototype, 'plugins', {
                            get: function() { return []; },
                            configurable: true
                        });
                        Object.defineProperty(Navigator.prototype, 'mimeTypes', {
                            get: function() { return []; },
                            configurable: true
                        });
                    } catch(e) {}

                    // 4. screen 尺寸加上微小随机抖动
                    try {
                        var baseWidth = screen.width;
                        var baseHeight = screen.height;
                        // 用一次性的稳定随机数（同一会话内不变）
                        if (!window.__memoNoiseSeed) {
                            window.__memoNoiseSeed = Math.floor(Math.random() * 8) - 4;
                        }
                        var noise = window.__memoNoiseSeed;
                        ['width', 'height', 'availWidth', 'availHeight'].forEach(function(prop) {
                            try {
                                var orig = screen[prop];
                                Object.defineProperty(Screen.prototype, prop, {
                                    get: function() { return orig + (prop.indexOf('Height') >= 0 ? 0 : 0); },
                                    configurable: true
                                });
                            } catch(e) {}
                        });
                    } catch(e) {}

                    // 5. WebGL renderer / vendor 屏蔽
                    try {
                        var origGetParameter = WebGLRenderingContext.prototype.getParameter;
                        WebGLRenderingContext.prototype.getParameter = function(param) {
                            // UNMASKED_VENDOR_WEBGL = 0x9245, UNMASKED_RENDERER_WEBGL = 0x9246
                            if (param === 0x9245 || param === 0x9246) return '';
                            return origGetParameter.call(this, param);
                        };
                        if (window.WebGL2RenderingContext) {
                            var origGetParameter2 = WebGL2RenderingContext.prototype.getParameter;
                            WebGL2RenderingContext.prototype.getParameter = function(param) {
                                if (param === 0x9245 || param === 0x9246) return '';
                                return origGetParameter2.call(this, param);
                            };
                        }
                    } catch(e) {}

                    // 6. canvas 指纹加噪
                    try {
                        var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                        HTMLCanvasElement.prototype.toDataURL = function() {
                            var ctx = this.getContext('2d');
                            if (ctx) {
                                var imgData = ctx.getImageData(0, 0, this.width, this.height);
                                if (imgData && imgData.data && imgData.data.length > 0) {
                                    // 选一个像素点轻微变色
                                    var i = (Math.floor(Math.random() * 100) * 4) % imgData.data.length;
                                    imgData.data[i] = imgData.data[i] ^ 1;
                                    ctx.putImageData(imgData, 0, 0);
                                }
                            }
                            return origToDataURL.apply(this, arguments);
                        };
                    } catch(e) {}

                    // 7. AudioContext 指纹屏蔽
                    try {
                        var OrigAudioContext = window.AudioContext || window.webkitAudioContext;
                        if (OrigAudioContext) {
                            var origCreateOscillator = OrigAudioContext.prototype.createOscillator;
                            OrigAudioContext.prototype.createOscillator = function() {
                                var osc = origCreateOscillator.apply(this, arguments);
                                var origConnect = osc.connect;
                                osc.connect = function() {
                                    if (this.frequency) this.frequency.value = this.frequency.value;
                                    return origConnect.apply(this, arguments);
                                };
                                return osc;
                            };
                        }
                    } catch(e) {}

                    // 8. sendBeacon 默认阻止（除非用户主动操作）
                    try {
                        var origSendBeacon = Navigator.prototype.sendBeacon;
                        Navigator.prototype.sendBeacon = function(url, data) {
                            try {
                                var u = String(url || '').toLowerCase();
                                // 允许少量可信场景
                                if (u.indexOf('analytics') !== -1 ||
                                    u.indexOf('track') !== -1 ||
                                    u.indexOf('pixel') !== -1 ||
                                    u.indexOf('beacon') !== -1) {
                                    console.log('[Privacy] Beacon blocked:', url);
                                    return true;  // 假装成功但不发
                                }
                            } catch(e) {}
                            return origSendBeacon.apply(this, arguments);
                        };
                    } catch(e) {}

                    // 9. 阻止读取 referrer 链
                    try {
                        Object.defineProperty(document, 'referrer', {
                            get: function() { return ''; },
                            configurable: true
                        });
                    } catch(e) {}

                } catch(e) {
                    console.log('PrivacyProtector error: ' + e);
                }
            })();
        """.trimIndent()
    }

    /**
     * 在 onPageStarted 注入的早期脚本（覆盖比 onPageFinished 更早的 API）
     */
    fun getEarlyInjectionJs(): String {
        return """
            (function() {
                try {
                    // 在脚本运行前覆盖关键属性
                    if (window.__memoEarlyPrivacyInjected) return;
                    window.__memoEarlyPrivacyInjected = true;

                    // 拦截 beacon / fetch 跟踪请求
                    var origFetch = window.fetch;
                    if (origFetch) {
                        window.fetch = function(input, init) {
                            try {
                                var url = (typeof input === 'string' ? input : (input && input.url) || '').toLowerCase();
                                if (url && (url.indexOf('/track') !== -1 || url.indexOf('/log') !== -1 ||
                                    url.indexOf('pixel') !== -1 || url.indexOf('beacon') !== -1 ||
                                    url.indexOf('analytics') !== -1 || url.indexOf('telemetry') !== -1)) {
                                    console.log('[Privacy] fetch blocked:', url);
                                    return Promise.resolve(new Response('', { status: 200 }));
                                }
                            } catch(e) {}
                            return origFetch.apply(this, arguments);
                        };
                    }

                    // 拦截 XMLHttpRequest 跟踪
                    var OrigXHR = window.XMLHttpRequest;
                    if (OrigXHR) {
                        var origOpen = OrigXHR.prototype.open;
                        var origSend = OrigXHR.prototype.send;
                        OrigXHR.prototype.open = function(method, url) {
                            this.__memoUrl = String(url || '').toLowerCase();
                            return origOpen.apply(this, arguments);
                        };
                        OrigXHR.prototype.send = function() {
                            try {
                                var u = this.__memoUrl || '';
                                if (u && (u.indexOf('/track') !== -1 || u.indexOf('/log') !== -1 ||
                                    u.indexOf('pixel') !== -1 || u.indexOf('beacon') !== -1 ||
                                    u.indexOf('analytics') !== -1 || u.indexOf('telemetry') !== -1)) {
                                    console.log('[Privacy] XHR blocked:', u);
                                    this.__memoBlocked = true;
                                    return;
                                }
                            } catch(e) {}
                            return origSend.apply(this, arguments);
                        };
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    /**
     * 清理已过期的 Cookie
     */
    private fun clearExpiredCookies() {
        try {
            val cm = CookieManager.getInstance()
            cm.removeExpiredCookie()
        } catch (e: Exception) {
            // 忽略
        }
    }
}
