package com.example.memo.agent

import android.content.Context
import com.example.memo.agent.tools.*
import com.example.memo.data.AppListCache
import com.example.memo.data.WatchListManager
import com.example.memo.viewModel.LedgerViewModel
import com.example.memo.viewModel.MemoryViewModel
import com.example.memo.viewModel.NoteViewModel

/**
 * Agent 工具注册器（轻量级）
 *
 * 职责：
 * 1. 接收 ToolDependencies 依赖注入
 * 2. 注册所有可用的 Agent 工具
 * 3. 提供工具查询和描述生成功能
 * 4. 管理应用缓存（AppListCache）
 */
class AgentToolRegistry(
    private val context: Context,
    private val ledgerViewModel: LedgerViewModel,
    private val noteViewModel: NoteViewModel,
    private val memoryViewModel: MemoryViewModel,
    private val onCreateNote: (title: String, content: String) -> Unit,
    private val onAppSelectionNeeded: (PendingAppSelection) -> Unit,
    private val autoControlEnabled: Boolean = false,
    private val onPlayInBrowser: (String) -> Unit = {},
    private val onShowMovieRecommendations: (List<String>, WatchListManager.WatchType) -> Unit = { _, _ -> },
    private val onShowPendingList: (WatchListManager.WatchType) -> Unit = {},
    private val onShowRecentWatched: () -> Unit = {}
) {
    private val tools = mutableMapOf<String, AgentTool>()

    // 依赖注入容器，传递给所有需要依赖的工具
    private val deps = ToolDependencies(
        context = context,
        ledgerViewModel = ledgerViewModel,
        noteViewModel = noteViewModel,
        memoryViewModel = memoryViewModel,
        onCreateNote = onCreateNote,
        onAppSelectionNeeded = onAppSelectionNeeded,
        onPlayInBrowser = onPlayInBrowser,
        onShowMovieRecommendations = onShowMovieRecommendations,
        onShowPendingList = onShowPendingList,
        onShowRecentWatched = onShowRecentWatched
    )

    init {
        // 注册笔记工具
        registerTool(CreateNoteTool(deps))
        registerTool(SearchNoteTool(deps))
        registerTool(UpdateNoteTool(deps))
        registerTool(DeleteNoteTool(deps))

        // 注册联系人工具
        registerTool(CreateContactTool(deps))
        registerTool(SearchContactTool(deps))
        registerTool(DeleteContactTool(deps))
        registerTool(SearchPhoneTool(deps))
        registerTool(CallPhoneTool(deps))

        // 注册系统工具
        registerTool(CreateEventTool(deps))
        registerTool(FlashlightTool(deps))
        registerTool(CurrentTimeTool())

        // 注册闹钟工具
        registerTool(CreateAlarmTool(deps))
        registerTool(UpdateAlarmTool(deps))
        registerTool(DeleteAlarmTool(deps))
        registerTool(QueryAlarmTool(deps))

        // 注册账本工具
        registerTool(CreateLedgerTool(deps))
        registerTool(QueryLedgerTool(deps))
        registerTool(CreateTransactionTool(deps))

        // 注册应用工具
        registerTool(OpenAppTool(deps))
        registerTool(GetInstalledAppsTool(deps))
        registerTool(RecommendAppTool(deps))

        // 注册影视工具（内置浏览器方案）
        registerTool(PlayInBrowserTool(deps))
        registerTool(QueryPendingCountTool(deps))
        registerTool(ShowPendingListTool(deps))
        registerTool(ShowMovieRecommendationsTool(deps))
        registerTool(ShowRecentWatchedTool(deps))

        // 注册记忆工具
        registerTool(CreateMemoryTool(deps))
        registerTool(SearchMemoryTool(deps))
        registerTool(FindSimilarMemoriesTool(deps))
        registerTool(UpdateMemoryTool(deps))

        // 注册屏幕控制工具（仅在自动控制启用时）
        if (autoControlEnabled) {
            registerTool(GetScreenUiTool())
            registerTool(ClickElementTool())
            registerTool(InputTextTool())
            registerTool(SwipeScreenTool())
            registerTool(PressBackTool())
            registerTool(PressHomeTool())
        }
    }

    private fun registerTool(tool: AgentTool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): AgentTool? = tools[name]

    fun getAllTools(): List<AgentTool> = tools.values.toList()

    suspend fun updateAppCache() {
        AppListCache.refreshCache(context)
    }

    suspend fun checkAndUpdateCacheIfNeeded() {
        AppListCache.updateCacheIfNeeded(context)
    }

    fun getAppCacheInfo(): String {
        return AppListCache.getCacheInfo(context)
    }

    fun getToolsDescription(): String {
        return tools.values.joinToString("\n") { tool ->
            val paramsStr = if (tool.parameters.isEmpty()) {
                "无参数"
            } else {
                tool.parameters.joinToString(", ") { it.toPromptString() }
            }
            "- ${tool.name}: ${tool.description}\n  参数: {${paramsStr}}"
        }
    }
}
