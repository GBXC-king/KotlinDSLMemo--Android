package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import com.example.memo.data.WatchListManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 影视相关 Agent 工具集（内置浏览器方案）
 *
 * 流程说明：
 * - 具体剧名 → play_in_browser（直接跳浏览器搜索）
 * - 模糊需求 → query_pending_count（查待看数量+已看历史）
 *   - 待看≥15 → show_pending_list（弹待看列表）
 *   - 待看<15 → 基于已看历史搜索15个 → show_movie_recommendations（弹推荐窗）
 * - 查询上次看了什么 → show_recent_watched（弹最近3条已看）
 */

/**
 * 内置浏览器播放工具
 * 用户说"我想看 + 具体剧名"时调用，直接用内置浏览器搜索该内容。
 */
class PlayInBrowserTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "play_in_browser"
    override val description = "用内置浏览器在影视站搜索并播放指定的电影/电视剧（仅用于明确的具体剧名/电影名）"
    override val parameters = listOf(
        ParamSpec("title", ParamType.STRING, "电影或电视剧的名称")
    )

    override val promptFragment = """
        ### play_in_browser 使用规则
        **仅当**用户明确说出**具体的影视剧名/电影名**（如"我想看庆余年"、"我想看流浪地球2"、"播放哈利波特"）时使用。
        工具会用内置浏览器在**影视站**搜索该内容（不是通用搜索引擎，无法搜索其他内容）。

        ### 严禁使用的场景
        以下情况**绝对禁止**调用本工具，应改用其他工具或直接回答：
        - 实时行情查询：金价、银价、股价、汇率、利率、基金净值
        - 天气环境查询：天气、气温、降雨、空气质量
        - 新闻资讯查询：新闻、头条、热搜、赛事比分、彩票开奖
        - 生活服务查询：快递、物流、航班、高铁、车次、菜谱
        - 知识问答：百科、定义、"是什么"、"什么意思"
        - 任何**非"看影视剧"**的需求

        例如"今天的金价是多少"、"明天天气怎么样"、"比特币现在多少钱"等**绝对不能**调用本工具。
        如果用户需求不是看影视剧，请直接回答或说明无法处理，不要调用本工具。
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val title = params["title"]?.trim() ?: return ToolResult.failure("缺少影视名称")
        withContext(Dispatchers.Main) {
            deps.onPlayInBrowser(title)
        }
        return ToolResult.success("[已跳转内置浏览器] 正在用内置浏览器搜索\"$title\"。任务到此结束，不要再执行任何后续操作。")
    }
}

/**
 * 查询待看数量工具
 * 用户表达模糊观影需求时调用。返回待看数量和已看历史（已看历史用于后续推荐参考）。
 */
class QueryPendingCountTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "query_pending_count"
    override val description = "查询待看列表数量和已看历史。用户模糊表达观影需求（如我想看电视剧、推荐点电影）时先调用此工具"
    override val parameters = listOf(
        ParamSpec("type", ParamType.STRING, "类型：电影、电视剧或影视（两者都查）", required = false)
    )

    override val promptFragment = """
        ### query_pending_count 使用规则
        当用户表达模糊的观影需求（如"我想看电视剧"、"推荐点电影"、"想看好看的剧"）时先调用此工具。
        工具会返回待看数量和已看历史。根据返回结果决定下一步：
        - 如果待看数量 ≥ 15 → 调用 show_pending_list 展示待看列表，任务结束
        - 如果待看数量 < 15 → 基于已看历史搜索15个新的，调用 show_movie_recommendations 展示推荐
        type 参数：用户说"电影"传"电影"，说"电视剧"传"电视剧"，说"影视/看剧"等传"影视"
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val type = WatchListManager.WatchType.fromString(params["type"])
        val pendingCount = WatchListManager.getPendingCount(deps.context, type)
        val watchedText = WatchListManager.getWatchedNamesAsText(deps.context, type, limit = 20)
        val pendingText = WatchListManager.getPendingNamesAsText(deps.context, type)

        return ToolResult.success("待看数量:$pendingCount,已看历史:$watchedText,待看列表:$pendingText")
    }
}

/**
 * 展示待看列表工具
 * 待看数量 ≥ 15 时调用，弹出待看列表供用户选择。
 */
class ShowPendingListTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "show_pending_list"
    override val description = "展示待看列表弹窗（当待看数量≥15时调用，无需AI再推荐）"
    override val parameters = listOf(
        ParamSpec("type", ParamType.STRING, "类型：电影、电视剧或影视", required = false)
    )

    override val promptFragment = """
        ### show_pending_list 使用规则
        当 query_pending_count 返回的待看数量 ≥ 15 时调用此工具，直接展示待看列表。
        调用后任务结束，输出最终答案告知用户已展示待看列表。
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val type = WatchListManager.WatchType.fromString(params["type"])
        withContext(Dispatchers.Main) {
            deps.onShowPendingList(type)
        }
        return ToolResult.success("[已展示待看列表] 已弹出待看列表供用户选择。任务到此结束，不要再执行任何后续操作。")
    }
}

/**
 * 展示推荐结果工具
 * AI 搜索15个热门影视后调用，弹出推荐窗供用户选择。
 */
class ShowMovieRecommendationsTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "show_movie_recommendations"
    override val description = "展示AI推荐的电影/电视剧弹窗（传入15个名字，弹窗一次展示5个，用户可刷新换批）"
    override val parameters = listOf(
        ParamSpec("type", ParamType.STRING, "类型：电影或电视剧"),
        ParamSpec("titles", ParamType.STRING, "15个影视名称，用英文逗号隔开，如：庆余年,琅琊榜,甄嬛传")
    )

    override val promptFragment = """
        ### show_movie_recommendations 使用规则
        当 query_pending_count 返回的待看数量 < 15 时，基于已看历史搜索15个热门影视，然后调用此工具。
        要求：
        - titles 必须是15个名字，用英文逗号隔开
        - 不要带书名号或其他符号
        - 尽量推荐与已看历史不同类型或风格多样的话题作品
        - type 与用户需求一致（电影或电视剧）
        调用后任务结束，输出最终答案告知用户已展示推荐列表。
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val typeStr = params["type"] ?: return ToolResult.failure("缺少类型参数")
        val titlesStr = params["titles"] ?: return ToolResult.failure("缺少影视名称列表")
        val type = WatchListManager.WatchType.fromString(typeStr)
        val titles = titlesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        if (titles.isEmpty()) return ToolResult.failure("影视名称列表为空")

        withContext(Dispatchers.Main) {
            deps.onShowMovieRecommendations(titles, type)
        }
        return ToolResult.success("[已展示推荐列表] 已弹出推荐窗，共${titles.size}个影视供用户选择。任务到此结束，不要再执行任何后续操作。")
    }
}

/**
 * 展示最近已看工具
 * 用户问"上次看了什么"时调用，弹出最近3条已看记录。
 */
class ShowRecentWatchedTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "show_recent_watched"
    override val description = "展示最近观看记录弹窗（用户问上次看了什么、最近看了什么时调用）"
    override val parameters = listOf(
        ParamSpec("type", ParamType.STRING, "类型：电影、电视剧或影视（用户没指定则传影视）", required = false)
    )

    override val promptFragment = """
        ### show_recent_watched 使用规则
        当用户问"上次看了什么"、"最近看了什么电影/电视剧"等查询已看历史时调用。
        弹窗会展示最近3条已看记录。调用后任务结束。
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val type = WatchListManager.WatchType.fromString(params["type"])
        withContext(Dispatchers.Main) {
            deps.onShowRecentWatched()
        }
        return ToolResult.success("[已展示最近观看记录] 已弹出最近观看记录供用户查看。任务到此结束，不要再执行任何后续操作。")
    }
}
