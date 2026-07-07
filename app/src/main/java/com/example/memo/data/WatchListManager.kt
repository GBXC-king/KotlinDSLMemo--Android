package com.example.memo.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 待看/已看影视列表管理器
 *
 * 数据存储在应用内部目录的 watch_list.json，分为电影和电视剧两类。
 * 写入时自动去重。返回给 AI 的信息为逗号隔开的纯名字文本，最大化节省 token。
 *
 * 已看列表每条记录带 watchedAt 时间戳（最近观看时间）：
 * - 在 AI 助手跳浏览器、观影记录弹窗卡片跳浏览器时更新为当时本机时间
 * - 看过的影片弹窗按时间倒序排序（最近看过的在最前）
 */
object WatchListManager {

    private const val FILE_NAME = "watch_list.json"

    enum class WatchType(val key: String) {
        MOVIE("movies"),
        TV("tv"),
        ALL("all");

        companion object {
            fun fromString(s: String?): WatchType {
                return when (s?.trim()) {
                    "电影" -> MOVIE
                    "电视剧" -> TV
                    else -> ALL
                }
            }
        }
    }

    /**
     * 已看记录项：包含片名和最近观看时间戳
     */
    data class WatchedEntry(
        val title: String,
        var watchedAt: Long
    )

    private data class WatchData(
        // 已看：保留 WatchedEntry（带时间戳）
        var moviesWatched: MutableList<WatchedEntry> = mutableListOf(),
        var tvWatched: MutableList<WatchedEntry> = mutableListOf(),
        // 待看：保留纯字符串（无需时间戳）
        var moviesPending: MutableList<String> = mutableListOf(),
        var tvPending: MutableList<String> = mutableListOf()
    )

    private var cached: WatchData? = null
    private var cachedContext: Context? = null

    @Synchronized
    private fun load(context: Context): WatchData {
        if (cached != null && cachedContext == context) return cached!!
        val file = File(context.filesDir, FILE_NAME)
        val data = if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                WatchData(
                    moviesWatched = jsonToWatchedEntryList(json, "movies_watched"),
                    tvWatched = jsonToWatchedEntryList(json, "tv_watched"),
                    moviesPending = jsonToStringList(json, "movies_pending"),
                    tvPending = jsonToStringList(json, "tv_pending")
                )
            } catch (e: Exception) {
                WatchData()
            }
        } else {
            WatchData()
        }
        cached = data
        cachedContext = context.applicationContext
        return data
    }

    @Synchronized
    private fun save(context: Context, data: WatchData) {
        try {
            val json = JSONObject()
            json.put("movies_watched", watchedEntriesToJsonArray(data.moviesWatched))
            json.put("tv_watched", watchedEntriesToJsonArray(data.tvWatched))
            json.put("movies_pending", listToJsonArray(data.moviesPending))
            json.put("tv_pending", listToJsonArray(data.tvPending))
            File(context.filesDir, FILE_NAME).writeText(json.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 解析已看列表。兼容两种格式：
     * 1. 新格式：[{"title":"...","watchedAt":1234567890}, ...]
     * 2. 旧格式：["...", "..."]  → 自动赋默认时间戳（保证不丢数据）
     */
    private fun jsonToWatchedEntryList(json: JSONObject, key: String): MutableList<WatchedEntry> {
        val arr = json.optJSONArray(key) ?: return mutableListOf()
        val result = mutableListOf<WatchedEntry>()
        val defaultTime = System.currentTimeMillis() // 旧格式统一赋一个时间（不重要，反正会被新点击覆盖）
        for (i in 0 until arr.length()) {
            val item = arr.get(i)
            when (item) {
                is JSONObject -> {
                    val title = item.optString("title", "").trim()
                    val watchedAt = item.optLong("watchedAt", defaultTime)
                    if (title.isNotEmpty()) result.add(WatchedEntry(title, watchedAt))
                }
                is String -> {
                    val title = item.trim()
                    if (title.isNotEmpty()) result.add(WatchedEntry(title, defaultTime))
                }
            }
        }
        return result
    }

    private fun watchedEntriesToJsonArray(list: List<WatchedEntry>): org.json.JSONArray {
        val arr = org.json.JSONArray()
        list.forEach { entry ->
            val obj = JSONObject()
            obj.put("title", entry.title)
            obj.put("watchedAt", entry.watchedAt)
            arr.put(obj)
        }
        return arr
    }

    private fun jsonToStringList(json: JSONObject, key: String): MutableList<String> {
        val arr = json.optJSONArray(key) ?: return mutableListOf()
        return (0 until arr.length()).map { arr.getString(it) }.toMutableList()
    }

    private fun listToJsonArray(list: List<String>): org.json.JSONArray {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        return arr
    }

    /**
     * 加入已看列表（去重，自动用当前时间戳）。
     * 如果该名字在待看列表中，则从待看移除。
     * 如果该名字已在已看列表中，则更新其时间戳为当前时间。
     *
     * @param title 影视名称
     * @param type 类型（电影/电视剧）
     */
    @Synchronized
    fun addToWatched(context: Context, title: String, type: WatchType) {
        updateWatchedTimestamp(context, title, type, System.currentTimeMillis())
    }

    /**
     * 更新已看记录的时间戳为指定时间（不存在的会新建）。
     * 用于：AI 助手跳浏览器、观影记录弹窗卡片跳浏览器时调用。
     *
     * @param context Context
     * @param title 影视名称
     * @param type 类型
     * @param timestamp 新的时间戳（毫秒）
     */
    @Synchronized
    fun updateWatchedTimestamp(
        context: Context,
        title: String,
        type: WatchType,
        timestamp: Long
    ) {
        val data = load(context)
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        when (type) {
            WatchType.MOVIE -> updateEntry(data.moviesWatched, cleanTitle, timestamp)
            WatchType.TV -> updateEntry(data.tvWatched, cleanTitle, timestamp)
            WatchType.ALL -> {
                // ALL：电影和电视剧两边都更新
                updateEntry(data.moviesWatched, cleanTitle, timestamp)
                updateEntry(data.tvWatched, cleanTitle, timestamp)
            }
        }
        // 同时从待看移除（已看了就不再是待看）
        data.moviesPending.remove(cleanTitle)
        data.tvPending.remove(cleanTitle)
        save(context, data)
    }

    /**
     * 在已看列表中查找/添加条目并更新时间戳
     */
    private fun updateEntry(list: MutableList<WatchedEntry>, title: String, timestamp: Long) {
        val existing = list.firstOrNull { it.title == title }
        if (existing != null) {
            existing.watchedAt = timestamp
        } else {
            list.add(WatchedEntry(title, timestamp))
        }
    }

    /**
     * 批量加入待看列表（去重）。
     */
    @Synchronized
    fun addAllToPending(context: Context, titles: List<String>, type: WatchType) {
        val data = load(context)
        when (type) {
            WatchType.MOVIE -> {
                titles.forEach { t ->
                    val clean = t.trim()
                    if (clean.isNotEmpty() && !data.moviesPending.contains(clean)
                        && data.moviesWatched.none { it.title == clean }) {
                        data.moviesPending.add(clean)
                    }
                }
            }
            WatchType.TV -> {
                titles.forEach { t ->
                    val clean = t.trim()
                    if (clean.isNotEmpty() && !data.tvPending.contains(clean)
                        && data.tvWatched.none { it.title == clean }) {
                        data.tvPending.add(clean)
                    }
                }
            }
            WatchType.ALL -> {
                titles.forEach { t ->
                    val clean = t.trim()
                    if (clean.isNotEmpty()) {
                        if (!data.moviesPending.contains(clean) && data.moviesWatched.none { it.title == clean }) {
                            data.moviesPending.add(clean)
                        }
                        if (!data.tvPending.contains(clean) && data.tvWatched.none { it.title == clean }) {
                            data.tvPending.add(clean)
                        }
                    }
                }
            }
        }
        save(context, data)
    }

    /**
     * 从待看列表移除（用户点击观看后调用）。
     */
    @Synchronized
    fun removeFromPending(context: Context, title: String, type: WatchType) {
        val data = load(context)
        val cleanTitle = title.trim()
        when (type) {
            WatchType.MOVIE -> data.moviesPending.remove(cleanTitle)
            WatchType.TV -> data.tvPending.remove(cleanTitle)
            WatchType.ALL -> {
                data.moviesPending.remove(cleanTitle)
                data.tvPending.remove(cleanTitle)
            }
        }
        save(context, data)
    }

    /**
     * 获取待看列表。
     */
    @Synchronized
    fun getPendingList(context: Context, type: WatchType): List<String> {
        val data = load(context)
        return when (type) {
            WatchType.MOVIE -> data.moviesPending.toList()
            WatchType.TV -> data.tvPending.toList()
            WatchType.ALL -> (data.moviesPending + data.tvPending).distinct()
        }
    }

    /**
     * 获取待看数量。
     */
    @Synchronized
    fun getPendingCount(context: Context, type: WatchType): Int {
        return getPendingList(context, type).size
    }

    /**
     * 获取已看列表（按 watchedAt 倒序：最近看过的在最前）
     */
    @Synchronized
    fun getWatchedList(context: Context, type: WatchType): List<String> {
        return getWatchedEntries(context, type).map { it.title }
    }

    /**
     * 获取已看条目（含时间戳），按 watchedAt 倒序排序。
     * 用于在弹窗中显示完整信息。
     */
    @Synchronized
    fun getWatchedEntries(context: Context, type: WatchType): List<WatchedEntry> {
        val data = load(context)
        val combined: List<WatchedEntry> = when (type) {
            WatchType.MOVIE -> data.moviesWatched.toList()
            WatchType.TV -> data.tvWatched.toList()
            WatchType.ALL -> {
                // 合并电影+电视剧（去重：同名字以最新的时间为准）
                val map = mutableMapOf<String, WatchedEntry>()
                (data.moviesWatched + data.tvWatched).forEach { entry ->
                    val existing = map[entry.title]
                    if (existing == null || entry.watchedAt > existing.watchedAt) {
                        map[entry.title] = entry
                    }
                }
                map.values.toList()
            }
        }
        // 按时间戳倒序
        return combined.sortedByDescending { it.watchedAt }
    }

    /**
     * 获取最近 N 条已看记录（按时间倒序）。
     * 不足 N 条则返回全部。
     */
    @Synchronized
    fun getRecentWatched(context: Context, count: Int, type: WatchType): List<String> {
        val list = getWatchedList(context, type)
        return if (list.size <= count) list else list.take(count)
    }

    /**
     * 将已看列表转为逗号隔开的纯名字文本，供发送给 AI（最大化节省 token）。
     * @param limit 最多返回的数量，0 表示全部
     */
    @Synchronized
    fun getWatchedNamesAsText(context: Context, type: WatchType, limit: Int = 0): String {
        val list = getWatchedList(context, type)
        val src = if (limit > 0 && list.size > limit) list.take(limit) else list
        return src.joinToString(",")
    }

    /**
     * 将待看列表转为逗号隔开的纯名字文本。
     */
    @Synchronized
    fun getPendingNamesAsText(context: Context, type: WatchType): String {
        return getPendingList(context, type).joinToString(",")
    }

    /**
     * 获取分批展示数据（循环补齐）。
     *
     * 例：list 有 16 项，offset=15，batchSize=5 → 返回 [list[15], list[0], list[1], list[2], list[3]]
     * 若 list 为空，返回空列表。
     *
     * @param list 数据源
     * @param offset 起始偏移
     * @param batchSize 每批数量（默认 5）
     * @param padToBatchSize 是否循环补齐到 batchSize。
     *        true（默认）：不足 batchSize 时从头部循环补齐，用于 AI 推荐弹窗的循环补齐算法
     *        false：取多少返回多少，不足时返回 list 末尾剩余的元素。用于观影记录弹窗，避免首位相接
     * @return 这一批的名字列表
     */
    fun getBatch(
        list: List<String>,
        offset: Int,
        batchSize: Int = 5,
        padToBatchSize: Boolean = true
    ): List<String> {
        if (list.isEmpty()) return emptyList()
        if (padToBatchSize) {
            // 循环补齐（AI 推荐弹窗用）
            val result = mutableListOf<String>()
            for (i in 0 until batchSize) {
                result.add(list[(offset + i) % list.size])
            }
            return result
        } else {
            // 不补齐：截取 [offset, offset+batchSize) 区间，越界自动截断
            val end = (offset + batchSize).coerceAtMost(list.size)
            val start = offset.coerceAtLeast(0).coerceAtMost(end)
            return list.subList(start, end)
        }
    }

    /**
     * 获取待看数量统计文本（给 AI 看的精简格式）。
     * 例："电影待看3,电视剧待看5"
     */
    @Synchronized
    fun getPendingCountText(context: Context): String {
        val data = load(context)
        return "电影待看${data.moviesPending.size},电视剧待看${data.tvPending.size}"
    }

    /**
     * 本地兜底校验：判断标题是否"像"影视剧名。
     *
     * 用于在 AI 误判调用 play_in_browser 时拦截，避免非影视内容（如"今日金价查询"）
     * 被写入观影记录或在影视站搜索。
     *
     * 策略：保守判断，仅在标题明显是查询/实时信息时返回 false。
     * 正常影视剧名（如《今日说法》《天气之子》）不会被误伤。
     *
     * @param title 待校验的标题
     * @return true 表示可能是影视剧名（允许处理）；false 表示明显不是影视内容
     */
    fun isLikelyMovieTitle(title: String): Boolean {
        val t = title.trim()
        if (t.isEmpty()) return false

        // 明显的非影视内容短语（精确匹配，避免误伤《天气之子》《今日说法》等正常影视名）
        val nonMovieKeywords = listOf(
            // 实时行情
            "金价", "银价", "黄金价格", "股价", "股票行情", "基金净值", "汇率", "利率",
            // 天气环境（带后缀，避免误伤《天气之子》）
            "天气预报", "天气查询", "今天天气", "明天天气",
            // 新闻资讯
            "新闻头条", "今日新闻", "最近新闻", "热搜",
            // 赛事彩票
            "比分", "赛果", "彩票", "开奖",
            // 生活服务
            "快递查询", "物流查询", "航班查询", "高铁查询", "车次查询",
            // 知识问答
            "是什么", "什么意思", "百科"
        )
        // 明显的查询/疑问后缀（命中即判定为非影视）
        val querySuffixes = listOf("查询", "是多少", "怎么样", "多少钱", "多少元", "多少度")

        if (nonMovieKeywords.any { t.contains(it) }) return false
        if (querySuffixes.any { t.contains(it) }) return false

        return true
    }
}
