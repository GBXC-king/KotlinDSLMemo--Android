package com.example.memo.agent

object AgentSystemPrompt {

    /**
     * 动态构建系统提示词（分层按需加载）
     * 核心规则 + 工具描述 + 工具特定规则（从各工具 promptFragment 收集）+ 精简示例
     *
     * @param tools 当前已注册的工具列表
     */
    fun getSystemPrompt(tools: List<AgentTool>): String {
        return PromptBuilder.buildSystemPrompt(tools)
    }

    /**
     * 兼容旧调用方式（仅传工具描述字符串）
     * @deprecated 使用 getSystemPrompt(tools: List<AgentTool>) 代替
     */
    @Deprecated("使用 getSystemPrompt(tools: List<AgentTool>) 代替", ReplaceWith("getSystemPrompt(emptyList())"))
    fun getSystemPrompt(toolsDescription: String): String {
        // 向后兼容：如果没有传入工具列表，只返回核心规则 + 工具描述 + 精简示例
        return buildString {
            appendLine(PromptBuilder.getCorePromptPublic())
            appendLine()
            appendLine("## 可用工具")
            appendLine(toolsDescription)
            appendLine()
            appendLine(PromptBuilder.getMinimalExamplesPublic())
        }.trimEnd()
    }
}
