package com.example.memo.ui.browser

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.util.concurrent.atomic.AtomicReference

/**
 * 广告/隐私规则引擎
 *
 * 设计目标：
 * - 解析 EasyList 风格的规则文件（assets/adblock 下 .txt）
 * - 启动时一次性加载到内存，匹配走 O(1) HashMap 查表
 * - 区分"网络规则"（拦截 URL 请求）和"美化规则"（注入 CSS 隐藏 DOM）
 * - 支持例外（@@）、选项（$third-party, $script, $image）
 * - 线程安全：内部用 AtomicReference 持有快照，加载完成前返回 false 不拦截
 *
 * 规则语法（精简版）：
 *   ! 或 [Adblock] 开头为注释
 *   ||domain^            匹配该域名及子域
 *   ||domain^$third-party 仅拦截跨站请求
 *   /regex/              正则匹配 URL
 *   @@||domain^          例外（白名单）
 *   ##.selector          美化规则：全站隐藏该 CSS 选择器
 *   domain##.selector    美化规则：仅在该域名隐藏
 *   ~domain##.selector   美化规则例外
 */
object RuleEngine {

    private const val TAG = "RuleEngine"

    // ===== 文件名常量 =====
    private const val FILE_NETWORK = "adblock/network.txt"
    private const val FILE_COSMETIC = "adblock/cosmetic.txt"
    private const val FILE_PRIVACY = "adblock/privacy.txt"

    // ===== 数据快照（线程安全） =====
    private val snapshotRef = AtomicReference<RulesSnapshot?>(null)

    /**
     * 规则快照：解析后的所有规则
     */
    data class RulesSnapshot(
        /** 域名网络规则：key = 域名（不含 ^），value = 规则（含选项） */
        val networkByDomain: Map<String, List<NetworkRule>>,
        /** 例外规则：key = 域名，value = 例外规则 */
        val exceptionsByDomain: Map<String, List<NetworkRule>>,
        /** 正则网络规则 */
        val regexRules: List<NetworkRule>,
        /** 正则例外规则 */
        val regexExceptions: List<NetworkRule>,
        /** 通用美化 CSS（无域名限定） */
        val globalCosmeticCss: String,
        /** 按域名分组的私有美化 CSS：key = 域名，value = CSS */
        val domainCosmeticCss: Map<String, String>
    )

    /**
     * 网络规则
     * @param pattern 域名（不含 || 和 ^）或正则字符串（已剥 /.../）
     * @param isRegex 是否是正则规则
     * @param thirdParty 是否仅拦截第三方
     * @param options 选项列表（如 script/image/object 等）
     */
    data class NetworkRule(
        val pattern: String,
        val isRegex: Boolean,
        val thirdParty: Boolean,
        val options: Set<String>,
        val raw: String
    )

    /**
     * 加载所有规则。失败时返回 false，但不会抛出（保证浏览器仍能正常浏览）。
     * 首次调用前必须先调用 load()，否则所有 shouldBlock 返回 false（不拦截）。
     */
    fun load(context: Context) {
        try {
            val network = parseNetworkFile(readAsset(context, FILE_NETWORK))
            val cosmetic = parseCosmeticFile(readAsset(context, FILE_COSMETIC))
            val privacy = parseNetworkFile(readAsset(context, FILE_PRIVACY))
            // privacy 规则附加到 network 规则中，共享同一组匹配器
            val merged = mergeRules(network, privacy)
            snapshotRef.set(merged.copy(
                globalCosmeticCss = (merged.globalCosmeticCss + "\n" + cosmetic.first).trim(),
                domainCosmeticCss = merged.domainCosmeticCss + cosmetic.second
            ))
            Log.d(TAG, "Rules loaded: network=${network.first.size + network.second.size + network.third.size + network.fourth.size}, cosmetic=global=${merged.globalCosmeticCss.length}c domain=${merged.domainCosmeticCss.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load rules", e)
            snapshotRef.set(null)
        }
    }

    /**
     * 同步加载（已加载则直接返回，未加载则同步读）
     * 用于 WebViewClient 回调链中保证规则已就绪
     */
    fun ensureLoaded(context: Context) {
        if (snapshotRef.get() == null) {
            load(context)
        }
    }

    /**
     * 释放规则（页面退出时调用，便于测试）
     */
    fun clear() {
        snapshotRef.set(null)
    }

    /**
     * 判断 URL 是否应被网络层拦截
     * @param url 请求的完整 URL
     * @param pageUrl 当前页面 URL（用于判断 third-party）
     * @return true = 拦截
     */
    fun shouldBlock(url: String, pageUrl: String? = null): Boolean {
        val snap = snapshotRef.get() ?: return false
        if (url.isBlank()) return false
        val lower = url.lowercase()
        val host = hostOf(lower) ?: return false
        val pageHost = pageUrl?.let { hostOf(it.lowercase()) }

        // 1. 例外优先（最具体优先）
        if (matchesException(snap, host, lower, pageHost)) return false

        // 2. 正则规则
        snap.regexRules.forEach { rule ->
            if (ruleMatches(rule, host, lower, pageHost)) return true
        }

        // 3. 域名规则
        snap.networkByDomain[host]?.forEach { rule ->
            if (ruleMatches(rule, host, lower, pageHost)) return true
        }
        // 子域匹配：example.com 规则命中 sub.example.com
        snap.networkByDomain.forEach { (ruleDomain, rules) ->
            if (host != ruleDomain && host.endsWith(".$ruleDomain")) {
                rules.forEach { rule ->
                    if (ruleMatches(rule, host, lower, pageHost)) return true
                }
            }
        }
        return false
    }

    /**
     * 获取用于注入到页面的全局美化 CSS（含通用规则 + 按当前域名的私有规则）
     * @param pageUrl 当前页面 URL
     * @return CSS 字符串（多条规则以换行分隔，可直接拼入 <style>）
     */
    fun getCosmeticCss(pageUrl: String?): String {
        val snap = snapshotRef.get() ?: return ""
        val sb = StringBuilder()
        if (snap.globalCosmeticCss.isNotBlank()) sb.append(snap.globalCosmeticCss).append('\n')
        val host = pageUrl?.let { hostOf(it.lowercase()) } ?: return sb.toString()
        // 精确匹配 + 父域匹配（sub.example.com 套用 example.com 的规则）
        snap.domainCosmeticCss[host]?.let { sb.append(it).append('\n') }
        for ((ruleDomain, css) in snap.domainCosmeticCss) {
            if (ruleDomain != host && host.endsWith(".$ruleDomain")) {
                sb.append(css).append('\n')
            }
        }
        return sb.toString().trim()
    }

    /**
     * 生成用于在页面上移除广告 DOM 的 JS 脚本
     * - 注入全局 CSS（display:none）
     * - 持续 MutationObserver 兜底（应对动态加载）
     */
    fun getCosmeticJs(pageUrl: String?): String {
        val css = getCosmeticCss(pageUrl)
        if (css.isBlank()) return ""
        val cssLiteral = jsStringLiteral(css)
        return """
            (function() {
                try {
                    var css = $cssLiteral;
                    if (!css) return;
                    var styleId = '__memo_rule_css';
                    var style = document.getElementById(styleId);
                    if (!style) {
                        style = document.createElement('style');
                        style.id = styleId;
                        style.type = 'text/css';
                        (document.head || document.documentElement).appendChild(style);
                    }
                    style.textContent = (style.textContent || '') + '\n' + css;
                } catch(e) {}
            })();
        """.trimIndent()
    }

    // ========== 内部实现 ==========

    private fun readAsset(context: Context, path: String): String {
        return context.assets.open(path).bufferedReader().use(BufferedReader::readText)
    }

    /**
     * 解析网络规则文件
     * @return Quadruple(域名规则Map, 例外Map, 正则规则List, 正则例外List)
     */
    private fun parseNetworkFile(content: String): Quadruple<Map<String, List<NetworkRule>>, Map<String, List<NetworkRule>>, List<NetworkRule>, List<NetworkRule>> {
        val network = mutableMapOf<String, MutableList<NetworkRule>>()
        val exceptions = mutableMapOf<String, MutableList<NetworkRule>>()
        val regexRules = mutableListOf<NetworkRule>()
        val regexExceptions = mutableListOf<NetworkRule>()

        content.lines().forEach rawLineIter@{ rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@rawLineIter
            if (line.startsWith("!") || line.startsWith("[")) return@rawLineIter
            // 跳过纯 cosmetic 规则（包含 ##）
            if (line.contains("##")) return@rawLineIter

            val isException = line.startsWith("@@")
            val ruleText = if (isException) line.substring(2) else line

            val (patternPart, optionsPart) = splitOptions(ruleText)
            val options = parseOptions(optionsPart)
            val thirdParty = "third-party" in options || "3p" in options

            // 域名规则：||domain^ 或 ||domain
            if (patternPart.startsWith("||")) {
                val domain = patternPart.substring(2).removeSuffix("^").lowercase()
                if (domain.isBlank()) return@rawLineIter
                val rule = NetworkRule(domain, false, thirdParty, options, line)
                if (isException) {
                    exceptions.getOrPut(domain) { mutableListOf() }.add(rule)
                } else {
                    network.getOrPut(domain) { mutableListOf() }.add(rule)
                }
                return@rawLineIter
            }
            // 正则规则：/regex/
            if (patternPart.startsWith("/") && patternPart.endsWith("/") && patternPart.length > 2) {
                val regex = patternPart.substring(1, patternPart.length - 1)
                val rule = NetworkRule(regex, true, thirdParty, options, line)
                if (isException) regexExceptions.add(rule) else regexRules.add(rule)
                return@rawLineIter
            }
        }
        return Quadruple(network, exceptions, regexRules, regexExceptions)
    }

    /**
     * 解析 cosmetic 规则文件
     * @return Pair(全局CSS字符串, 域名->CSS Map)
     */
    private fun parseCosmeticFile(content: String): Pair<String, Map<String, String>> {
        val global = StringBuilder()
        val byDomain = mutableMapOf<String, StringBuilder>()
        val exceptionsByDomain = mutableMapOf<String, List<String>>()

        // 第一遍：先收集例外
        content.lines().forEach { line ->
            val t = line.trim()
            if (t.startsWith("~") && t.contains("##")) {
                val domain = t.substring(0, t.indexOf("##")).removePrefix("~").lowercase()
                val selector = t.substring(t.indexOf("##") + 2)
                exceptionsByDomain.getOrPut(domain) { emptyList() }.let { _ ->
                    // 简化处理：只记录带例外的域名
                }
                // 真正处理在第二遍：直接跳过该域名的所有规则
            }
        }

        val exceptionDomains = exceptionsByDomain.keys.toSet()
        content.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[")) return@forEach
            if (!line.contains("##")) return@forEach
            val sepIdx = line.indexOf("##")
            val left = line.substring(0, sepIdx)
            val selector = line.substring(sepIdx + 2)
            if (selector.isBlank()) return@forEach

            // 域名前缀 ~ 表示例外：直接跳过
            if (left.startsWith("~")) return@forEach

            val cssRule = "$selector { display: none !important; }\n"
            if (left.isBlank()) {
                // 全局规则
                global.append(cssRule)
            } else {
                // 域名规则
                val domain = left.lowercase()
                if (domain in exceptionDomains) return@forEach
                byDomain.getOrPut(domain) { StringBuilder() }.append(cssRule)
            }
        }
        return global.toString() to byDomain.mapValues { it.value.toString() }
    }

    /**
     * 合并多个 network 解析结果
     */
    private fun mergeRules(
        a: Quadruple<Map<String, List<NetworkRule>>, Map<String, List<NetworkRule>>, List<NetworkRule>, List<NetworkRule>>,
        b: Quadruple<Map<String, List<NetworkRule>>, Map<String, List<NetworkRule>>, List<NetworkRule>, List<NetworkRule>>
    ): RulesSnapshot {
        val network = mutableMapOf<String, MutableList<NetworkRule>>()
        val exceptions = mutableMapOf<String, MutableList<NetworkRule>>()
        val regex = mutableListOf<NetworkRule>()
        val regexExc = mutableListOf<NetworkRule>()

        listOf(a, b).forEach { (n, e, r, re) ->
            n.forEach { (k, v) -> network.getOrPut(k) { mutableListOf() }.addAll(v) }
            e.forEach { (k, v) -> exceptions.getOrPut(k) { mutableListOf() }.addAll(v) }
            regex.addAll(r)
            regexExc.addAll(re)
        }

        return RulesSnapshot(
            networkByDomain = network.mapValues { it.value.toList() },
            exceptionsByDomain = exceptions.mapValues { it.value.toList() },
            regexRules = regex,
            regexExceptions = regexExc,
            globalCosmeticCss = "",
            domainCosmeticCss = emptyMap()
        )
    }

    private fun matchesException(snap: RulesSnapshot, host: String, url: String, pageHost: String?): Boolean {
        // 精确匹配 + 子域匹配
        snap.exceptionsByDomain[host]?.let { rules ->
            rules.forEach { if (ruleMatches(it, host, url, pageHost)) return true }
        }
        snap.exceptionsByDomain.forEach { (ruleDomain, rules) ->
            if (host != ruleDomain && host.endsWith(".$ruleDomain")) {
                rules.forEach { if (ruleMatches(it, host, url, pageHost)) return true }
            }
        }
        // 正则例外
        snap.regexExceptions.forEach {
            if (ruleMatches(it, host, url, pageHost)) return true
        }
        return false
    }

    private fun ruleMatches(rule: NetworkRule, host: String, url: String, pageHost: String?): Boolean {
        // third-party 判断
        if (rule.thirdParty) {
            if (pageHost == null) return false  // 无法判断则放行
            if (host == pageHost || host.endsWith(".$pageHost") || pageHost.endsWith(".$host")) {
                return false
            }
        }
        return if (rule.isRegex) {
            try {
                Regex(rule.pattern).containsMatchIn(url)
            } catch (e: Exception) {
                false
            }
        } else {
            // 域名规则：host 完全等于 pattern 或 host 以 ".pattern" 结尾
            host == rule.pattern || host.endsWith(".${rule.pattern}")
        }
    }

    private fun splitOptions(ruleText: String): Pair<String, String?> {
        val idx = ruleText.indexOf('$')
        return if (idx > 0) {
            ruleText.substring(0, idx) to ruleText.substring(idx + 1)
        } else {
            ruleText to null
        }
    }

    private fun parseOptions(optionsPart: String?): Set<String> {
        if (optionsPart.isNullOrBlank()) return emptySet()
        return optionsPart.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    }

    private fun hostOf(url: String): String? {
        return try {
            val u = if (url.contains("://")) url else "http://$url"
            java.net.URI(u).host?.lowercase()
        } catch (e: Exception) {
            // 退化：手剥
            val noProto = url.substringAfter("://").substringBefore("/")
            noProto.substringAfter("@").lowercase().takeIf { it.isNotBlank() }
        }
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

    /**
     * 4 元组（Kotlin 标准库无 Pair/Triple 之外的元组）
     */
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
