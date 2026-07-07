package com.example.memo.data.parser

import com.example.memo.data.AIAction

/**
 * 动作解析器接口
 * 支持多种解析策略：正则解析、Function Calling 解析等
 */
interface ActionParser {
    /**
     * 解析 AI 响应，提取动作列表
     * @param response AI 的文本响应
     * @param toolCalls Function Calling 返回的工具调用列表（可选）
     * @return 解析后的动作列表
     */
    fun parse(response: String, toolCalls: List<ToolCall>? = null): List<AIAction>
}

/**
 * Function Calling 工具调用数据类
 */
data class ToolCall(
    val id: String,
    val functionName: String,
    val arguments: String
)
