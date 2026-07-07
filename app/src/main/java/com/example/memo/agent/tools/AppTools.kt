package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.PendingAppSelection
import com.example.memo.agent.ToolResult
import com.example.memo.data.AppHelper
import com.example.memo.data.AppListCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 打开应用工具
 */
class OpenAppTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "open_app"
    override val description = "打开指定名称的应用"
    override val parameters = listOf(
        ParamSpec("app_name", ParamType.STRING, "应用名称")
    )
    
    override val promptFragment = """
        ### 打开应用规则
        根据用户表述判断使用哪个工具：
        - 用户**明确指定**应用名（如"打开QQ"、"打开微信"）→ 使用 open_app
        - 用户表达**模糊需求**（如"玩游戏"、"听音乐"）→ 使用 recommend_app，category 为需求类别
        - 影视相关需求（我想看电影/电视剧、推荐电影、上次看了什么）→ 使用影视相关工具（play_in_browser / query_pending_count / show_recent_watched），不要使用 recommend_app
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val appName = params["app_name"] ?: return ToolResult.failure("缺少应用名称")
        val apps = AppListCache.getCachedApps(deps.context)
        val installedApps = AppHelper.searchInstalledAppsWithList(apps, appName)

        if (installedApps.isEmpty()) {
            return ToolResult.failure("未找到应用 \"$appName\"，请确认应用是否已安装")
        }

        // 如果只找到一个应用，直接打开
        if (installedApps.size == 1) {
            val app = installedApps[0]
            val success = withContext(Dispatchers.Main) {
                AppHelper.openApp(deps.context, app.packageName)
            }
            return if (success) {
                ToolResult.success("已打开应用：${app.name}")
            } else {
                ToolResult.failure("打开应用 ${app.name} 失败")
            }
        }

        // 多个应用时，弹出选择框
        val appNames = installedApps.map { it.name }
        val packageNames = installedApps.map { it.packageName }
        val descriptions = installedApps.map { it.description }

        val selection = PendingAppSelection(
            category = appName,
            apps = appNames,
            packageNames = packageNames,
            descriptions = descriptions
        )

        withContext(Dispatchers.Main) {
            deps.onAppSelectionNeeded(selection)
        }

        val appList = installedApps.joinToString("\n") {
            if (it.description.isNotBlank()) "${it.name}（${it.description}）" else it.name
        }
        return ToolResult.success("[任务已交给用户选择] 已弹出应用选择框，请用户从弹窗中选择要打开的应用。任务到此结束，不要再执行任何后续操作。\n匹配的应用列表：\n$appList")
    }
}

/**
 * 获取已安装应用列表工具
 */
class GetInstalledAppsTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "get_installed_apps"
    override val description = "获取设备上已安装的应用列表"
    override val parameters = listOf(
        ParamSpec("category", ParamType.STRING, "按类别过滤（如游戏、社交、工具）", required = false)
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val apps = AppListCache.getCachedApps(deps.context)
        if (apps.isEmpty()) {
            return ToolResult.failure("未能获取应用列表，请检查应用列表权限")
        }
        val sb = StringBuilder("设备上已安装 ${apps.size} 个应用：\n")
        apps.forEach { app ->
            if (app.description.isNotBlank()) {
                sb.append("• ${app.name}（${app.description}）\n")
            } else {
                sb.append("• ${app.name}\n")
            }
        }
        return ToolResult.success(sb.toString().trim())
    }
}

/**
 * 推荐应用工具
 */
class RecommendAppTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "recommend_app"
    override val description = "根据用户需求从已安装应用中推荐并打开应用，会弹出选择框让用户选择"
    override val parameters = listOf(
        ParamSpec("category", ParamType.STRING, "应用类别或用户需求描述，如：游戏、音乐、视频、社交、工具、购物等")
    )
    
    override val promptFragment = """
        ### recommend_app 使用规则
        当用户表达模糊需求但没有指定具体应用时使用。category 参数示例：
        - 游戏类：游戏、休闲游戏
        - 音乐类：音乐、音乐播放
        - 视频类：视频、短视频、影视
        - 社交类：社交、聊天
        - 购物类：购物、电商
        - 出行类：出行、打车、导航
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val category = params["category"] ?: return ToolResult.failure("缺少应用类别参数")
        val apps = AppListCache.getCachedApps(deps.context)

        if (apps.isEmpty()) {
            return ToolResult.failure("未能获取应用列表，请检查应用列表权限")
        }

        val matchedApps = matchAppsByCategory(apps, category)

        if (matchedApps.isEmpty()) {
            val appListText = apps.joinToString("\n") {
                if (it.description.isNotBlank()) "${it.name}（${it.description}）" else it.name
            }
            return ToolResult.failure("未找到符合\"$category\"的应用。\n\n您已安装的全部应用：\n$appListText\n\n请从以上应用中选择，或直接说具体的应用名称。")
        }

        val appNames = matchedApps.map { it.name }
        val packageNames = matchedApps.map { it.packageName }
        val descriptions = matchedApps.map { it.description }

        val selection = PendingAppSelection(
            category = category,
            apps = appNames,
            packageNames = packageNames,
            descriptions = descriptions
        )

        withContext(Dispatchers.Main) {
            deps.onAppSelectionNeeded(selection)
        }

        val appList = matchedApps.joinToString("\n") {
            if (it.description.isNotBlank()) "${it.name}（${it.description}）" else it.name
        }
        return ToolResult.success("[任务已交给用户选择] 已弹出应用选择框，请用户从弹窗中选择要打开的应用。任务到此结束，不要再执行任何后续操作。\n匹配的应用列表：\n$appList")
    }

    private fun matchAppsByCategory(apps: List<AppHelper.AppInfo>, category: String): List<AppHelper.AppInfo> {
        val lowerCategory = category.lowercase().trim()
        
        val scoredApps = apps.map { app ->
            val score = calculateAppMatchScore(app, lowerCategory)
            Pair(app, score)
        }.filter { it.second > 0 }
         .sortedByDescending { it.second }
         .map { it.first }

        return scoredApps.take(10)
    }

    private fun calculateAppMatchScore(app: AppHelper.AppInfo, category: String): Double {
        val appNameLower = app.name.lowercase()
        val descLower = app.description.lowercase()
        val pkgLower = app.packageName.lowercase()

        var score = 0.0

        if (appNameLower.contains(category) || category.contains(appNameLower)) {
            score += 1.0
        }

        if (descLower.contains(category)) {
            score += 0.8
        }

        val categoryKeywords = getCategoryKeywords(category)
        var keywordMatchCount = 0
        for (kw in categoryKeywords) {
            val kwLower = kw.lowercase()
            if (descLower.contains(kwLower) || appNameLower.contains(kwLower) || pkgLower.contains(kwLower)) {
                keywordMatchCount++
            }
        }
        if (categoryKeywords.isNotEmpty()) {
            score += keywordMatchCount.toDouble() / categoryKeywords.size * 0.5
        }

        val gameKeywords = listOf("游戏", "game", "手游", "王者", "荣耀", "和平精英", "原神", 
            "我的世界", "mc", "mincraft", "pubg", "崩坏", "lol", "英雄联盟", 
            "阴阳师", "明日方舟", "光遇", "蛋仔", "消消乐", "农场", "跑酷",
            "tencent", "netease", "mihoyo")
        
        if (category.contains("游戏") || category.contains("玩")) {
            if (descLower.contains("游戏")) {
                score += 0.9
            }
            for (kw in gameKeywords) {
                if (appNameLower.contains(kw.lowercase()) || pkgLower.contains(kw.lowercase())) {
                    score += 0.6
                    break
                }
            }
        }

        return score
    }

    private fun getCategoryKeywords(category: String): List<String> {
        return when {
            category.contains("游戏") || category.contains("玩游戏") -> listOf("游戏", "玩")
            category.contains("音乐") || category.contains("听歌") -> listOf("音乐", "听")
            category.contains("短视频") || category.contains("刷视频") || category.contains("抖音")
                || category.contains("快手") -> listOf("短视频", "刷", "视频")
            category.contains("社交") || category.contains("聊天") -> listOf("社交", "聊天")
            category.contains("购物") || category.contains("买东西") || category.contains("电商") 
                -> listOf("购物", "买")
            category.contains("外卖") || category.contains("吃饭") || category.contains("点餐") 
                -> listOf("外卖", "吃")
            category.contains("新闻") || category.contains("阅读") -> listOf("新闻", "阅读")
            category.contains("工具") || category.contains("效率") -> listOf("工具", "效率")
            category.contains("地图") || category.contains("出行") || category.contains("导航") 
                || category.contains("打车") -> listOf("地图", "出行", "导航")
            category.contains("相机") || category.contains("拍照") || category.contains("照相") 
                -> listOf("相机", "拍照")
            category.contains("相册") || category.contains("图片") || category.contains("照片") 
                -> listOf("相册", "图片", "照片")
            category.contains("浏览器") || category.contains("上网") -> listOf("浏览器", "上网")
            category.contains("办公") || category.contains("文档") || category.contains("wps") 
                -> listOf("办公", "文档")
            category.contains("支付") || category.contains("付钱") || category.contains("钱") 
                -> listOf("支付", "理财")
            category.contains("旅行") || category.contains("旅游") || category.contains("订票") 
                -> listOf("旅行", "订票")
            else -> listOf(category)
        }
    }
}
