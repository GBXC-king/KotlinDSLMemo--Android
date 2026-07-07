package com.example.memo.agent

interface AgentTool {
    val name: String
    val description: String
    val parameters: List<ParamSpec>
    
    /**
     * 工具自带的提示词片段，包含该工具的规则和示例
     * 用于动态拼装系统提示词，实现按需加载
     */
    val promptFragment: String
        get() = "" // 默认空实现，子类可选覆盖

    suspend fun execute(params: Map<String, String>): ToolResult
}
