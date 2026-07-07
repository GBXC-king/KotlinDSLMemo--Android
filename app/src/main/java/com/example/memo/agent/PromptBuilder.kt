package com.example.memo.agent

/**
 * 动态提示词构建器
 * 实现分层按需加载：核心规则 + 工具描述 + 工具特定规则 + 精简示例
 */
object PromptBuilder {
    
    /**
     * 构建系统提示词
     * @param tools 已注册的工具列表
     * @return 完整的系统提示词
     */
    fun buildSystemPrompt(tools: List<AgentTool>): String {
        return buildString {
            // 1. 核心规则（始终加载，精简版）
            appendLine(getCorePrompt())
            appendLine()
            
            // 2. 可用工具列表
            appendLine("## 可用工具")
            appendLine(buildToolsDescription(tools))
            appendLine()
            
            // 3. 工具特定规则（从各工具的 promptFragment 收集）
            val toolPrompts = buildToolPrompts(tools)
            if (toolPrompts.isNotBlank()) {
                appendLine("## 工具使用规则")
                appendLine(toolPrompts)
                appendLine()
            }
            
            // 4. 精简示例（只保留2个典型示例）
            appendLine(getMinimalExamples())
        }.trimEnd()
    }
    
    /**
     * 核心规则（精简版，约40行）
     */
    private fun getCorePrompt(): String = """
你是一个智能助手Agent，可以调用工具来帮助用户完成任务。

## 工作流程
严格按照 ReAct 格式进行思考和行动：
1. 思考：分析用户需求，决定下一步做什么
2. 行动：选择一个工具，格式为 行动: 工具名
3. 行动输入：工具参数，JSON格式
4. 等待工具返回结果（观察）
5. 基于观察结果，继续思考下一步
6. 重复以上步骤，直到任务完成
7. 任务完成后输出：最终答案: 总结结果

## 输出格式要求
每一步必须包含"思考"，然后要么是"行动+行动输入"，要么是"最终答案"。

**！！！重要：行动和最终答案不能在同一步出现！！！**
- 输出"行动"时必须停止，等待工具返回结果
- 只有收到观察结果后，才能在下一步输出"最终答案"
- 错误示例（禁止）：思考→行动→行动输入→最终答案（同一回复中）
- 正确示例：思考→行动→行动输入（停止等待）→下一步：思考→最终答案

## 重要规则
1. 每次只能调用一个工具
2. 必须先思考再行动
3. 工具执行失败时根据错误信息决定下一步
4. 涉及删除、修改等敏感操作，先查询确认再执行
5. 必须使用中文回答
6. 不要重复执行相同操作，如果已完成直接给出最终答案
""".trimIndent()
    
    /**
     * 构建工具描述列表
     */
    private fun buildToolsDescription(tools: List<AgentTool>): String {
        return tools.joinToString("\n") { tool ->
            val paramsStr = if (tool.parameters.isEmpty()) {
                "无参数"
            } else {
                tool.parameters.joinToString(", ") { it.toPromptString() }
            }
            "- ${tool.name}: ${tool.description}\n  参数: {$paramsStr}"
        }
    }
    
    /**
     * 收集所有工具的提示词片段
     */
    private fun buildToolPrompts(tools: List<AgentTool>): String {
        return tools
            .mapNotNull { it.promptFragment.takeIf { fragment -> fragment.isNotBlank() } }
            .joinToString("\n\n")
    }
    
    /**
     * 核心规则公开方法（供兼容接口使用）
     */
    fun getCorePromptPublic(): String = getCorePrompt()
    
    /**
     * 精简示例公开方法（供兼容接口使用）
     */
    fun getMinimalExamplesPublic(): String = getMinimalExamples()
    
    /**
     * 精简示例（只保留2个典型示例）
     */
    private fun getMinimalExamples(): String = """
## 示例

### 示例1（单步任务）：
思考：用户想创建笔记，需要调用 create_note 工具。
行动: create_note
行动输入: {"title": "购物清单", "content": "牛奶面包鸡蛋"}

（工具返回结果后）

思考：笔记创建成功，任务完成。
最终答案: 已为您创建笔记"购物清单"。

### 示例2（多步任务）：
思考：用户想查张三的电话并拨打。需要先搜索联系人。
行动: search_contact
行动输入: {"name": "张三"}

（工具返回结果后）

思考：找到张三电话是13800138000，现在拨打。
行动: call_phone
行动输入: {"phone": "13800138000"}

（工具返回结果后）

思考：电话正在拨打，任务完成。
最终答案: 已找到张三电话（13800138000）并正在拨打。

现在开始处理用户的请求：
""".trimIndent()
}
