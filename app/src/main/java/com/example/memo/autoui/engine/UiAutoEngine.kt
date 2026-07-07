package com.example.memo.autoui.engine

import android.content.Context
import com.example.memo.agent.AgentEngine
import com.example.memo.agent.AgentToolRegistry
import com.example.memo.viewModel.LedgerViewModel
import com.example.memo.viewModel.MemoryViewModel
import com.example.memo.viewModel.NoteViewModel
import com.example.memo.agent.PendingAppSelection

class UiAutoEngine(
    private val context: Context,
    private val noteViewModel: NoteViewModel,
    private val ledgerViewModel: LedgerViewModel,
    private val memoryViewModel: MemoryViewModel,
    private val maxSteps: Int = 20
) {
    private var agentEngine: AgentEngine? = null
    private var toolRegistry: AgentToolRegistry? = null

    interface UiAutoCallback {
        fun onStepUpdate(step: Int, thought: String?, action: String?, observation: String?)
        fun onComplete(result: String)
        fun onError(error: String)
        fun onAppSelectionNeeded(selection: PendingAppSelection)
    }

    suspend fun executeTask(task: String, callback: UiAutoCallback) {
        val registry = AgentToolRegistry(
            context = context,
            ledgerViewModel = ledgerViewModel,
            noteViewModel = noteViewModel,
            memoryViewModel = memoryViewModel,
            onCreateNote = { _, _ -> },
            onAppSelectionNeeded = { selection ->
                callback.onAppSelectionNeeded(selection)
            }
        )
        toolRegistry = registry

        val engine = AgentEngine(registry, maxSteps)
        agentEngine = engine

        engine.run(task, object : AgentEngine.AgentCallback {
            override fun onStepUpdate(step: com.example.memo.agent.AgentStepRecord) {
                callback.onStepUpdate(
                    step.stepIndex,
                    step.thought,
                    step.action,
                    step.observation
                )
            }

            override fun onFinalAnswer(answer: String) {
                callback.onComplete(answer)
            }

            override fun onError(error: String) {
                callback.onError(error)
            }
        })
    }

    fun stop() {
        agentEngine = null
        toolRegistry = null
    }
}
