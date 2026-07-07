package com.example.memo.agent

import com.example.memo.data.ChatMessage
import com.example.memo.data.DeepSeekHelper
import com.example.memo.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AgentStepRecord(
    val stepIndex: Int,
    val thought: String?,
    val action: String?,
    val actionInput: Map<String, String>?,
    val observation: String?,
    val finalAnswer: String?
)

class AgentEngine(
    private val toolRegistry: AgentToolRegistry,
    private val maxSteps: Int = 10
) {
    private val agentMessages = mutableListOf<ChatMessage>()
    val stepRecords = mutableListOf<AgentStepRecord>()

    @Volatile
    private var isCancelled = false

    interface AgentCallback {
        fun onStepUpdate(step: AgentStepRecord)
        fun onFinalAnswer(answer: String)
        fun onError(error: String)
        fun onStopped(reason: String) {}
    }

    fun cancel() {
        isCancelled = true
    }

    fun isRunning(): Boolean = stepRecords.isNotEmpty() && !isCancelled

    suspend fun run(userInput: String, callback: AgentCallback) {
        stepRecords.clear()
        agentMessages.clear()

        val systemPrompt = AgentSystemPrompt.getSystemPrompt(toolRegistry.getAllTools())
        agentMessages.add(ChatMessage(content = userInput, role = MessageRole.USER))

        try {
            executeStepLoop(0, systemPrompt, callback)
        } catch (e: Exception) {
            callback.onError("Agent执行出错: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private suspend fun executeStepLoop(
        startStep: Int,
        systemPrompt: String,
        callback: AgentCallback
    ) {
        var stepIndex = startStep
        val actionHistory = mutableListOf<Pair<String, Map<String, String>?>>()

        while (stepIndex < maxSteps) {
            if (isCancelled) {
                callback.onStopped("操作已被用户终止")
                return
            }

            val response = callAI(systemPrompt)
            val step = ReActParser.parse(response)

            agentMessages.add(ChatMessage(content = response, role = MessageRole.ASSISTANT))

            val record = AgentStepRecord(
                stepIndex = stepIndex + 1,
                thought = step.thought,
                action = step.action,
                actionInput = step.actionInput,
                observation = null,
                finalAnswer = step.finalAnswer
            )
            stepRecords.add(record)
            callback.onStepUpdate(record)

            when {
                step.hasAction -> {
                    val currentAction = step.action ?: ""
                    val currentInput = step.actionInput

                    val duplicateCount = actionHistory.count { it.first == currentAction && it.second == currentInput }
                    if (duplicateCount >= 2) {
                        val finalMsg = "检测到重复操作，已停止执行。已完成的操作请查看上方步骤记录。"
                        callback.onFinalAnswer(finalMsg)
                        return
                    }
                    actionHistory.add(currentAction to currentInput)

                    val observation = executeTool(step)
                    val updatedRecord = record.copy(observation = observation)
                    stepRecords[stepRecords.size - 1] = updatedRecord
                    callback.onStepUpdate(updatedRecord)

                    // 当工具返回"任务已交给用户选择"时，说明已经弹出选择框等待用户操作
                    // 此时Agent应立即停止，不再继续循环
                    if (observation.contains("[任务已交给用户选择]")) {
                        callback.onFinalAnswer("已为您找到匹配的应用，请从弹出的选择框中选择要打开的应用。")
                        return
                    }

                    agentMessages.add(ChatMessage(content = "观察: $observation", role = MessageRole.USER))
                    stepIndex++

                    // 如果AI在同一响应中同时给出了最终答案，说明它误以为操作已完成
                    // 实际操作刚刚执行，需要继续循环让AI看到观察结果后正确判断
                    if (step.hasFinalAnswer) {
                        // 操作已执行，但AI提前给出了"最终答案"，忽略它并继续循环
                        // 让AI基于真实的观察结果决定下一步
                    }
                }
                step.hasFinalAnswer -> {
                    callback.onFinalAnswer(step.finalAnswer ?: "任务完成")
                    return
                }
                else -> {
                    callback.onFinalAnswer(response)
                    return
                }
            }
        }

        callback.onFinalAnswer("已达到最大执行步数($maxSteps)，任务未完成。")
    }

    private suspend fun callAI(systemPrompt: String): String {
        return withContext(Dispatchers.IO) {
            var result: String? = null
            var error: String? = null

            DeepSeekHelper.sendChatMessage(
                messages = agentMessages,
                onResponse = { response ->
                    result = response
                },
                onError = { err ->
                    error = err
                },
                customSystemPrompt = systemPrompt,
                enableFunctionCalling = false
            )

            result ?: throw Exception(error ?: "未知错误")
        }
    }

    private suspend fun executeTool(step: ReActStep): String {
        val toolName = step.action ?: return "错误：未指定工具"
        val toolInput = step.actionInput ?: emptyMap()

        val tool = toolRegistry.getTool(toolName)
            ?: return "工具 \"$toolName\" 不存在"

        return try {
            val result = tool.execute(toolInput)
            // 记录工具调用
            com.example.memo.util.OperationLogger.logToolCall(toolName, toolInput, result.toObservation())
            result.toObservation()
        } catch (e: Exception) {
            val errorMsg = "工具执行出错: ${e.message ?: e.javaClass.simpleName}"
            // 记录工具调用错误
            com.example.memo.util.OperationLogger.logToolCall(toolName, toolInput, errorMsg)
            ToolResult.failure(errorMsg).toObservation()
        }
    }
}
