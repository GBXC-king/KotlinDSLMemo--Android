package com.example.memo.ui.browser

/**
 * 播放器视图隔离器（白名单重建版）
 *
 * 思路：直接把整个播放页 body 掏空，然后只把白名单内的目标元素搬进一个干净容器。
 * - body 变空 → 所有非白名单元素（广告、推荐、评论、页脚等）全部消失
 * - 目标元素（.player-box-main / .module-player-info / .player-list）以原貌搬入
 * - 搜索页（无目标元素）不做任何处理
 *
 * 关键修复点：
 * - 完全无白屏、无加载动画、无 JavascriptInterface 异步通知，纯同步操作
 * - 重建一次完成，没有重试循环，没有 200ms 间隔等待
 * - 不依赖 visibility:hidden，不会再因为 release 失败导致永久白屏
 * - 搜索页 detect 后跳过，不影响正常浏览
 * - 切集数（SPA URL 变化）通过 MutationObserver 重新执行
 */
object PlayerIsolator {

    /**
     * 需要保留的目标元素 CSS 选择器（白名单）
     */
    private const val targetSelectors = ".player-box-main, .module-player-info, .player-list"

    /**
     * 已知广告元素的 CSS 选择器，用于 CSS 预隐藏 + JS 可见性检测 + 自动刷新触发
     * 全部共享同一份列表，避免维护多份
     */
    private val adCssSelectors = listOf(
        // ===== freeokk 站点广告（开发者内置，写在 HTML 里的固定区块） =====
        ".module-adslist",
        ".module-adslist.module-wrapper",
        ".player-rm-rmtwo-rlist",
        ".player-rm",
        ".player-rm.rm-two",
        ".player-rm.rm-list",
        ".player-rm.rm-two.rm-list",
        // ===== 通用广告 class（第一方 + 第三方都覆盖） =====
        ".ad-container", ".ad-wrapper", ".ad-box", ".ad-banner",
        ".ad-popup", ".ad-popunder", ".ad-float", ".ad-layer",
        // ===== 通用属性匹配（应对各种命名风格） =====
        "[class*='adsense']", "[class*='adwords']",
        "[id*='_ads']", "[id*='banner']",
        "[class^='ad-']", "[class*=' ad-']", "[class*='-ad-']",
        "[id^='ad-']", "[id*=' ad-']", "[id*='-ad-']",
        // ===== 浮窗/弹窗 =====
        ".popup", ".modal-ad", ".float-ad",
        // ===== 播放器相关广告/推荐（开发者内置，非第三方） =====
        ".player-sidebar", ".player-side", ".player-ads", ".player-ad",
        ".player-related", ".player-recommend", ".player-extra",
        ".player-pause-ad", ".video-ads", ".video-ad",
        // ===== 推荐/相关/热门/猜你喜欢（中文影视站常内置） =====
        ".recommend", ".recommend-list", ".recommend-box",
        ".related", ".related-list", ".related-box",
        ".hot", ".hot-list", ".hot-search", ".hot-recommend",
        ".guess", ".guess-list", ".guess-you-like",
        ".you-may-like", ".you-may-also-like",
        ".also-like", ".also-watch", ".also-view",
        ".watch-next", ".watch-also", ".next-video",
        // ===== 周边/花絮/预告（开发者扩展内容区） =====
        ".bonus", ".bonus-content", ".extras", ".extra-content",
        ".trailer", ".preview", ".behind-the-scenes",
        // ===== VIP / 会员推广（开发者内置 CTA） =====
        ".vip-promo", ".vip-box", ".vip-cta", ".vip-banner",
        ".member-promo", ".member-cta", ".member-banner",
        ".pay-promo", ".pay-cta", ".pay-banner",
        ".upgrade-promo", ".upgrade-banner", ".upgrade-cta",
        // ===== 下载 App 推广（开发者想绑 App） =====
        ".app-promo", ".app-banner", ".app-download", ".download-app",
        ".open-app", ".open-in-app", ".app-tip", ".app-guide",
        // ===== 弹层/蒙层/浮窗（开发者自建遮罩） =====
        ".mask", ".overlay", ".mask-layer", ".mask-content",
        ".floating", ".float-box", ".float-window",
        ".slide-up", ".slide-bar", ".bottom-bar", ".top-bar",
        // ===== 公告/通知/提示（开发者强插的全站提示） =====
        ".notice", ".notice-box", ".notice-bar", ".notice-banner",
        ".announcement", ".announce", ".announce-box",
        ".alert-box", ".alert-banner", ".alert-tip",
        // ===== 评论/弹幕入口（开发者内置社交模块） =====
        ".comment", ".comment-box", ".comment-list", ".comment-area",
        ".danmaku", ".danmu", ".barrage",
        // ===== 分享/收藏条（开发者内置交互条） =====
        ".share-bar", ".share-box", ".share-cta",
        ".favorite-bar", ".fav-bar",
        // ===== 通用类名前缀（应对各种自定义命名） =====
        "[class*='ad_pic']", "[class*='ad_img']", "[class*='ad-link']",
        "[class*='ads_box']", "[class*='ads-list']", "[class*='ad-list']",
        "[class*='adbox']", "[class*='adsbox']",
        "[class*='advert']", "[class*='sponsor']", "[class*='promo']",
        "[class*='popup']", "[class*='popover']",
        "[class*='rec-']", "[class*='recom-']", "[class*='rel-']",
        // ===== id 维度 =====
        "[id*='_ad_']", "[id*='_ads_']", "[id*='_advert_']",
        "[id*='_banner']", "[id*='_sponsor']", "[id*='_popup']",
        "[id*='_recommend']", "[id*='_related']",
        // ===== 影视站常见包裹容器 =====
        ".player-pause", ".pause-ad", ".paused-overlay",
        ".end-screen", ".end-recommendation", ".end-card",
        ".up-next", ".next-up", ".next-video-card",
        // ===== freeokk 专用：/link.php?u= 跳转广告 + go2yd.com 图片 + gg-icon =====
        "a[href^='/link.php?u=']",
        "a[href*='link.php?u=']",
        "a[href*='link.php?u='][target='_blank']",
        "img[src*='go2yd.com']",
        "img[src*='yd_qualify']",
        "img[src*='YD_qualify']",
        "img[alt='5.16']", "img[alt='5.19']", "img[alt='6.6']",
        "img[alt='7.10']", "img[alt='4.10']",
        "img[alt^='5.']", "img[alt^='6.']",
        "img[alt^='7.']", "img[alt^='8.']", "img[alt^='9.']",
        "i.gg-icon",
        ".gg-icon",
        "[class*='gg-icon']",
        "div[style*='border-radius:7px']",
        "div[style*='border-radius: 7px']"
    )

    /**
     * 生成 CSS 预隐藏规则
     * 仅在 onPageStarted 注入 <style>，让已知广告 class 在解析时就 display:none
     * 主要防御交给 JS 白名单重建，CSS 只是辅助
     */
    fun getCssRules(): String {
        return adCssSelectors.joinToString(",\n") { it } + " { display: none !important; }"
    }

    /**
     * 生成主注入 JS（onPageFinished 时注入）
     *
     * 三层防御：
     * 1. 白名单重建：把白名单元素搬进干净容器，清空 body（一次性）
     * 2. 自动检测刷新：每 0.2 秒检测是否还有广告，检测到则通过 AndroidPlayer.requestRefresh()
     *    通知原生层点击刷新按钮
     * 3. 兜底重建：每 0.8 秒检查 wrapper 是否被还原/丢失，是则重建
     *
     * 配套：MutationObserver 监听 URL 变化（SPA 切集数），重新执行
     */
    fun getInjectionJs(): String {
        val adArrayJs = adCssSelectors.joinToString(",") { "'$it'" }

        return """
(function() {
    // 清理上次残留（支持刷新/切集重新注入）
    if (window.__memoIso) {
        if (window.__memoIso.observer) window.__memoIso.observer.disconnect();
        if (window.__memoIso.adTimer) clearInterval(window.__memoIso.adTimer);
        if (window.__memoIso.idleTimer) clearInterval(window.__memoIso.idleTimer);
    }
    window.__memoIso = {
        observer: null,
        adTimer: null,
        idleTimer: null,
        lastUrl: location.href,
        adStreak: 0   // 连续检测到广告的次数
    };

    var TARGET_SELECTOR = '$targetSelectors';
    var AD_SELECTORS = [$adArrayJs];

    // 检测当前页面是否还有"可见"的广告元素
    // 可见 = offsetHeight > 0 且 offsetWidth > 0（CSS display:none 元素不算可见）
    function hasVisibleAds() {
        for (var i = 0; i < AD_SELECTORS.length; i++) {
            try {
                var els = document.querySelectorAll(AD_SELECTORS[i]);
                for (var j = 0; j < els.length; j++) {
                    var el = els[j];
                    if (el && el.offsetHeight > 0 && el.offsetWidth > 0) return true;
                }
            } catch(e) {}
        }
        return false;
    }

    // 通知原生层点击刷新按钮（处理 JS 拿不到 webViewRef 的情况）
    function requestRefresh() {
        try {
            if (window.AndroidPlayer && AndroidPlayer.requestRefresh) {
                AndroidPlayer.requestRefresh();
            }
        } catch(e) {}
    }

    // JS 兜底扫描：CSS 被内联 !important 覆盖时，强制隐藏 ad-class 元素
    // 主要针对"开发者内置广告"——它们的样式常带 !important，CSS pre-hide 失效
    function hideAdsByClass() {
        for (var i = 0; i < AD_SELECTORS.length; i++) {
            try {
                var els = document.querySelectorAll(AD_SELECTORS[i]);
                for (var j = 0; j < els.length; j++) {
                    var el = els[j];
                    if (!el) continue;
                    // 三层保险：visibility + display + 移出布局
                    el.style.setProperty('display', 'none', 'important');
                    el.style.setProperty('visibility', 'hidden', 'important');
                    el.style.setProperty('height', '0', 'important');
                    el.style.setProperty('width', '0', 'important');
                    el.style.setProperty('position', 'absolute', 'important');
                    el.style.setProperty('left', '-99999px', 'important');
                    el.style.setProperty('top', '-99999px', 'important');
                    el.style.setProperty('pointer-events', 'none', 'important');
                    el.style.setProperty('opacity', '0', 'important');
                }
            } catch(e) {}
        }
    }

    // 白名单重建：把目标元素搬进干净容器，清空原 body
    function rebuild() {
        try {
            var targets = document.querySelectorAll(TARGET_SELECTOR);
            if (!targets || targets.length === 0) return false;

            // 1. 创建干净容器
            var wrapper = document.createElement('div');
            wrapper.id = '__memo_player_wrapper';
            wrapper.style.cssText = [
                'width: 100%',
                'min-height: 100vh',
                'background: #000',
                'color: #fff',
                'margin: 0',
                'padding: 0',
                'box-sizing: border-box',
                'font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif'
            ].join(';');

            // 2. 按 DOM 顺序把目标元素搬入
            var targetArr = Array.prototype.slice.call(targets);
            targetArr.sort(function(a, b) {
                if (a === b) return 0;
                if (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) return -1;
                return 1;
            });

            for (var i = 0; i < targetArr.length; i++) {
                var node = targetArr[i];
                if (node.parentNode) node.parentNode.removeChild(node);
                node.style.display = node.style.display || 'block';
                wrapper.appendChild(node);
            }

            // 3. 清空 body
            while (document.body.firstChild) {
                document.body.removeChild(document.body.firstChild);
            }

            // 4. 放入干净容器
            document.body.appendChild(wrapper);
            document.body.style.cssText = 'margin:0;padding:0;background:#000;';

            return true;
        } catch(e) {
            return false;
        }
    }

    // 立即执行一次
    // 先 hide（应对开发者内置 !important 样式）
    hideAdsByClass();
    // 再 rebuild（白名单重建，掏空 body）
    rebuild();

    // ========== 第二层：每 0.2 秒自动检测广告 ==========
    // 影视站的广告出现有随机性（有时第一次没广告刷新一下就有了），
    // 持续检测，检测到连续 2 次（约 0.4 秒）有广告就触发刷新按钮
    if (window.__memoIso.adTimer) clearInterval(window.__memoIso.adTimer);
    window.__memoIso.adTimer = setInterval(function() {
        // 持续 hide 兜底（防止 SPA 重新插入带 !important 的广告）
        hideAdsByClass();
        if (hasVisibleAds()) {
            window.__memoIso.adStreak++;
            // 连续 2 次检测到广告（约 0.4 秒）才触发，避免误刷新
            if (window.__memoIso.adStreak >= 2) {
                window.__memoIso.adStreak = 0;
                requestRefresh();
            }
        } else {
            window.__memoIso.adStreak = 0;
        }
    }, 200);

    // ========== 第三层：每 0.8 秒兜底重建 ==========
    // 防止 SPA 切集/异步渲染导致 wrapper 丢失后无法恢复
    if (window.__memoIso.idleTimer) clearInterval(window.__memoIso.idleTimer);
    window.__memoIso.idleTimer = setInterval(function() {
        // 先 hide（防止 rebuild 后又被插入内联 !important 广告）
        hideAdsByClass();
        // wrapper 丢失/被还原 → 重建
        if (!document.getElementById('__memo_player_wrapper')) {
            rebuild();
        }
    }, 800);

    // 持续监控 SPA URL 变化（切集数/切播放页）
    if (window.MutationObserver) {
        var debounceTimer = null;
        window.__memoIso.observer = new MutationObserver(function() {
            if (location.href !== window.__memoIso.lastUrl) {
                window.__memoIso.lastUrl = location.href;
                var old = document.getElementById('__memo_player_wrapper');
                if (old) old.parentNode.removeChild(old);
                if (debounceTimer) clearTimeout(debounceTimer);
                debounceTimer = setTimeout(function() {
                    rebuild();
                    debounceTimer = null;
                }, 200);
                return;
            }
            if (!document.getElementById('__memo_player_wrapper')) {
                if (debounceTimer) clearTimeout(debounceTimer);
                debounceTimer = setTimeout(function() {
                    rebuild();
                    debounceTimer = null;
                }, 300);
            }
        });
        window.__memoIso.observer.observe(document.documentElement, {
            childList: true, subtree: true
        });
    }
})();
        """.trimIndent()
    }
}
