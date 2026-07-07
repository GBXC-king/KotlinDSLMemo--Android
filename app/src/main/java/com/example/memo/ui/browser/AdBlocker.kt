package com.example.memo.ui.browser

import android.content.Context

/**
 * 广告拦截器（基于 RuleEngine + 兜底 DOM 清理）
 *
 * 分层策略：
 * 1. 网络层：RuleEngine.shouldBlock() 拦截第三方广告/跟踪请求
 * 2. 美化层：RuleEngine.getCosmeticCss() 注入 CSS 隐藏已知广告 class
 *    - 关键：能拦截"网站开发者内置"的第一方广告（请求层抓不到）
 * 3. 兜底层：JS 扫描 DOM 移除残留广告 + MutationObserver 持续清理
 * 4. 弹窗拦截：重写 window.open + 屏蔽敏感外链
 *
 * 加载时机：BrowserScreen 启动时调用一次 AdBlocker.init(context)
 */
object AdBlocker {

    private var initialized = false

    /**
     * 初始化：触发规则加载
     */
    fun init(context: Context) {
        if (initialized) return
        RuleEngine.ensureLoaded(context.applicationContext)
        initialized = true
    }

    /**
     * 释放资源（页面退出时）
     */
    fun release() {
        // 规则保持在内存，下次 BrowserScreen 复用
        initialized = false
    }

    /**
     * 网络层：判断单个 URL 是否应被拦截
     * 委托给 RuleEngine
     */
    fun shouldBlock(url: String, pageUrl: String? = null): Boolean {
        if (url.isBlank()) return false
        return RuleEngine.shouldBlock(url, pageUrl)
    }

    /**
     * 注入 CSS（onPageStarted 立即生效）
     * 用于在 DOM 解析前就 display:none 已知广告 class
     */
    fun getInjectionCss(pageUrl: String?): String {
        return RuleEngine.getCosmeticCss(pageUrl)
    }

    /**
     * 生成主注入 JS（onPageFinished 时执行）
     *
     * 三层防御：
     * 1. 注入/追加全局 + 当前域名的 cosmetic CSS（应对开发者内置广告）
     * 2. 扫描 DOM 移除含敏感关键词的元素（兜底）
     * 3. 重写 window.open + 阻止 target=_blank + MutationObserver 持续监控
     */
    fun getInjectionJs(pageUrl: String?): String {
        val cosmeticJs = RuleEngine.getCosmeticJs(pageUrl)
        return """
            (function() {
                try {
                    // ===== 1. 注入/追加 cosmetic CSS（应对开发者内置广告） =====
                    $cosmeticJs

                    // ===== 2. 兜底：扫描 DOM 移除含敏感关键词的元素 =====
                    var sensitiveKeywords = [
                        'porn', 'sex', 'xxx', 'adult', 'nude', 'cam-', 'livecam',
                        'casino', 'gambling', 'bet365', 'poker', 'slots',
                        'dating', 'meet-', 'hookup',
                        '色情', '成人', '黄色', '裸', '赌博', '博彩', '彩票',
                        '约炮', '一夜情', '同城约'
                    ];

                    // 已知广告/弹窗 class 模式
                    var adClassPatterns = [
                        'ad-', 'ads-', 'advert', 'banner', 'sponsor', 'promo',
                        'popup', 'popunder', 'modal-ad', 'float-ad', 'floating-ad',
                        'sticky-ad', 'top-ad', 'bottom-ad', 'sidebar-ad', 'side-ad',
                        'player-rm', 'player-ad', 'video-ad', 'adsbox', 'ads-list',
                        'module-ads', 'module-adslist', 'recommend-list', 'hot-list',
                        'right-ad', 'left-ad', 'index-ad', 'content-ad',
                        // freeokk 专用模式
                        'gg-icon', 'go2yd.com', 'yd_qualify', 'yd-qualify', 'link.php?u=',
                        'border-radius:7px', 'border-radius: 7px'
                    ];

                    function isAdElement(el) {
                        if (!el || !el.getAttribute) return false;
                        var cls = (el.getAttribute('class') || '').toLowerCase();
                        var id = (el.getAttribute('id') || '').toLowerCase();
                        var src = (el.getAttribute('src') || '').toLowerCase();
                        var href = (el.getAttribute('href') || '').toLowerCase();
                        var alt = (el.getAttribute('alt') || '').toLowerCase();
                        var style = (el.getAttribute('style') || '').toLowerCase();
                        if (!cls && !id && !src && !href && !style) return false;
                        for (var i = 0; i < adClassPatterns.length; i++) {
                            var p = adClassPatterns[i];
                            if (cls.indexOf(p) !== -1 || id.indexOf(p) !== -1 ||
                                src.indexOf(p) !== -1 || href.indexOf(p) !== -1 ||
                                style.indexOf(p) !== -1) return true;
                        }
                        // alt 通常是版本号/广告标识（5.16 / 6.6 / 7.10）
                        if (alt) {
                            // 形如 "5.16" "6.6" "7.10" 的短字符串基本是广告
                            if (/^\d+\.\d+$/.test(alt) && alt.length <= 5) return true;
                        }
                        return false;
                    }

                    function isSensitiveElement(el) {
                        if (!el || !el.getAttribute) return false;
                        var text = ((el.textContent || '').substring(0, 200) + ' ' +
                                    (el.getAttribute('href') || '') + ' ' +
                                    (el.getAttribute('src') || '') + ' ' +
                                    (el.getAttribute('title') || '') + ' ' +
                                    (el.getAttribute('alt') || '')).toLowerCase();
                        if (text.length > 500) return false;  // 元素过大不检查
                        for (var i = 0; i < sensitiveKeywords.length; i++) {
                            if (text.indexOf(sensitiveKeywords[i]) !== -1) return true;
                        }
                        return false;
                    }

                    function removeAds() {
                        // 通用广告 class（含 a 链接和 img 图片，覆盖 link.php / go2yd / gg-icon）
                        var all = document.querySelectorAll('div, section, aside, span, iframe, ins, a, img');
                        for (var k = 0; k < all.length; k++) {
                            var el = all[k];
                            try {
                                if (isAdElement(el) || isSensitiveElement(el)) {
                                    el.remove();
                                }
                            } catch(e) {}
                        }
                    }

                    removeAds();

                    // ===== 2.5 跳转链接中性化：解码 link.php?u=base64 的真实目标 =====
                    // 影视站常用 link.php 跳转套 base64 编码的外链，广告图被隐藏后
                    // 用户仍可能点击残留的容器触发跳转。这里在 DOM 层就解决：
                    // 解码 base64 → 检查目标 host → 不是当前站就移除或断链
                    function isBase64Redirect(adHref) {
                        return adHref && (adHref.indexOf('link.php?u=') !== -1 ||
                                          adHref.indexOf('link.php?u=') >= 0);
                    }

                    function decodeBase64(str) {
                        try {
                            // 处理 URL-safe base64（- → +, _ → /）
                            var normalized = str.replace(/-/g, '+').replace(/_/g, '/');
                            // 补齐 padding
                            while (normalized.length % 4) normalized += '=';
                            return decodeURIComponent(escape(atob(normalized)));
                        } catch(e) {
                            return null;
                        }
                    }

                    // 已知恶意目标域名（解码后的真实地址）
                    var knownMaliciousHosts = [
                        '9hp.top', 'u9bbbb.com', 'wedsdxcfvfgtygh', 'hongguotuiguang',
                        // 影视站跳转常见黑产域名前缀
                        'jp.', 'av.', 'livecam', 'casino', 'bet365', 'poker', 'slots',
                        'xvideos', 'pornhub', 'xnxx', 'xhamster', 'chaturbate',
                        'bongacams', 'livejasmin', 'stripchat', 'onlyfans',
                        'freeokk.com',  // 同名异站混淆
                        'freeokk.cn', 'freeokk.vip', 'freeokk.org'
                    ];

                    function isExternalOrMalicious(decodedUrl) {
                        if (!decodedUrl) return true;
                        try {
                            var u = new URL(decodedUrl, location.href);
                            var host = (u.hostname || '').toLowerCase();
                            var currentHost = (location.hostname || '').toLowerCase();

                            // 当前站（freeokk.pro）的子域都放行
                            if (host === currentHost || host.endsWith('.' + currentHost)) {
                                return false;
                            }

                            // 常见合法子域白名单
                            if (host.indexOf('baidu.com') >= 0 ||
                                host.indexOf('qq.com') >= 0 ||
                                host.indexOf('weixin.qq.com') >= 0 ||
                                host.indexOf('alipay.com') >= 0 ||
                                host.indexOf('taobao.com') >= 0 ||
                                host.indexOf('jd.com') >= 0) {
                                return false;
                            }

                            // 命中恶意列表
                            for (var i = 0; i < knownMaliciousHosts.length; i++) {
                                if (host === knownMaliciousHosts[i] ||
                                    host.endsWith('.' + knownMaliciousHosts[i]) ||
                                    host.indexOf(knownMaliciousHosts[i]) !== -1) {
                                    return true;
                                }
                            }

                            // 影视站 link.php 跳转外链默认拦截（用户场景：只要是 link.php 都视为广告）
                            return true;
                        } catch(e) {
                            return true;  // 解析失败视为可疑
                        }
                    }

                    function neutralizeLink(a) {
                        if (!a || a.nodeType !== 1) return;
                        var href = a.getAttribute('href') || '';
                        if (!isBase64Redirect(href)) return;
                        // 解析 base64 u 参数
                        try {
                            var u = new URL(href, location.href);
                            var encoded = u.searchParams.get('u');
                            if (!encoded) return;
                            var decoded = decodeBase64(encoded);
                            if (decoded && isExternalOrMalicious(decoded)) {
                                // 三选一：删除 / 改 href / 阻塞点击
                                a.setAttribute('href', 'javascript:void(0)');
                                a.setAttribute('data-blocked-redirect', '1');
                                a.removeAttribute('target');
                                a.style.setProperty('pointer-events', 'none', 'important');
                                a.style.setProperty('cursor', 'default', 'important');
                                a.setAttribute('title', '广告链接已拦截');
                            }
                        } catch(e) {}
                    }

                    function neutralizeAllLinks() {
                        var links = document.querySelectorAll("a[href*='link.php?u=']");
                        for (var i = 0; i < links.length; i++) {
                            neutralizeLink(links[i]);
                        }
                    }
                    neutralizeAllLinks();

                    // ===== 3. 弹窗拦截 =====
                    window.open = function() {
                        return null;
                    };

                    // 阻止敏感外链（点击 + 触摸双拦截）
                    function blockClick(e) {
                        var target = e.target;
                        while (target && target.tagName !== 'A' && target !== document.body) {
                            target = target.parentElement;
                        }
                        if (target && target.tagName === 'A') {
                            var href = (target.getAttribute('href') || '').toLowerCase();
                            // 已被中性化的链接：彻底阻止
                            if (target.getAttribute('data-blocked-redirect') === '1') {
                                e.preventDefault();
                                e.stopPropagation();
                                e.stopImmediatePropagation();
                                return false;
                            }
                            if (href) {
                                // link.php 跳转但还没来得及中性化（动态插入）：直接阻止
                                if (href.indexOf('link.php?u=') !== -1) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    e.stopImmediatePropagation();
                                    neutralizeLink(target);
                                    return false;
                                }
                                for (var i = 0; i < sensitiveKeywords.length; i++) {
                                    if (href.indexOf(sensitiveKeywords[i]) !== -1) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        target.remove();
                                        return false;
                                    }
                                }
                            }
                            if (target.getAttribute('target') === '_blank') {
                                target.removeAttribute('target');
                            }
                        }
                    }
                    document.addEventListener('click', blockClick, true);
                    document.addEventListener('touchstart', blockClick, true);
                    document.addEventListener('mousedown', blockClick, true);

                    // ===== 3.5 动态插入的 link.php 链接持续扫描 =====
                    if (window.__memoLinkTimer) clearInterval(window.__memoLinkTimer);
                    window.__memoLinkTimer = setInterval(neutralizeAllLinks, 1000);

                    // ===== 4. MutationObserver 持续监控 =====
                    if (window.MutationObserver) {
                        var observer = new MutationObserver(function(mutations) {
                            for (var i = 0; i < mutations.length; i++) {
                                var added = mutations[i].addedNodes;
                                for (var j = 0; j < added.length; j++) {
                                    var node = added[j];
                                    if (node.nodeType === 1) {
                                        try {
                                            if (isAdElement(node) || isSensitiveElement(node)) {
                                                node.remove();
                                            } else if (node.querySelectorAll) {
                                                var inner = node.querySelectorAll('div, section, aside, iframe, ins, a, img');
                                                for (var m = 0; m < inner.length; m++) {
                                                    if (isAdElement(inner[m]) || isSensitiveElement(inner[m])) {
                                                        inner[m].remove();
                                                    }
                                                }
                                            }
                                        } catch(e) {}
                                    }
                                }
                            }
                        });
                        observer.observe(document.documentElement || document.body, {
                            childList: true, subtree: true
                        });
                    }

                    // ===== 5. 兜底定时清理 =====
                    setInterval(removeAds, 2000);

                } catch(e) {
                    console.log('AdBlocker error: ' + e);
                }
            })();
        """.trimIndent()
    }
}
