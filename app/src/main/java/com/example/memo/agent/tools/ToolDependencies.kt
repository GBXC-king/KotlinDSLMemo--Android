package com.example.memo.agent.tools

import android.content.Context
import com.example.memo.agent.PendingAppSelection
import com.example.memo.data.WatchListManager
import com.example.memo.viewModel.LedgerViewModel
import com.example.memo.viewModel.MemoryViewModel
import com.example.memo.viewModel.NoteViewModel

/**
 * 工具依赖注入容器
 * 包含所有 Agent 工具所需的共享依赖
 */
data class ToolDependencies(
    val context: Context,
    val ledgerViewModel: LedgerViewModel,
    val noteViewModel: NoteViewModel,
    val memoryViewModel: MemoryViewModel,
    val onCreateNote: (title: String, content: String) -> Unit,
    val onAppSelectionNeeded: (PendingAppSelection) -> Unit,
    // 影视相关回调
    val onPlayInBrowser: (title: String) -> Unit = {},
    val onShowMovieRecommendations: (titles: List<String>, type: WatchListManager.WatchType) -> Unit = { _, _ -> },
    val onShowPendingList: (type: WatchListManager.WatchType) -> Unit = {},
    val onShowRecentWatched: () -> Unit = {}
)
