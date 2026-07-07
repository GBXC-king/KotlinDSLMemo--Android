package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import com.example.memo.data.Memory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 创建记忆工具
 */
class CreateMemoryTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "create_memory"
    override val description = "创建一条新的长期记忆。如果发现相似记忆，应先调用find_similar_memories确认是否需要更新后再创建。"
    override val parameters = listOf(
        ParamSpec("title", ParamType.STRING, "记忆标题，由AI总结"),
        ParamSpec("content", ParamType.STRING, "记忆内容"),
        ParamSpec("tags", ParamType.STRING, "标签列表，用空格分隔的字符串，每个标签以#开头，例如：#大门 #密码 #家庭", required = false)
    )
    
    override val promptFragment = """
        ### 记忆管理规则
        **创建记忆前**：
        - 必须先调用 find_similar_memories 检查是否有相似记忆
        - 如果返回"[无匹配记忆]"，调用 create_memory 创建新记忆
        - 如果返回相似记忆列表，判断是否是同一条信息的更新：
          - 主题相同但值不同（如密码从123456改成654321）→ 调用 update_memory 更新
          - 主题完全不同 → 调用 create_memory 创建
        
        **记忆标签规范**：
        - tags 格式：#标签1 #标签2 #标签3（每个标签以#开头，空格分隔）
        - 示例：大门密码 → #大门 #密码 #家庭；公司地址 → #地址 #公司 #工作
        
        **查询记忆**：
        - 使用 search_memory，可用 keyword（关键词）或 tag（标签，不需要#前缀）
        - 如果有多条记忆，时间戳最新的是当前有效信息
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val title = params["title"] ?: "无标题"
        val content = params["content"] ?: ""
        val tags = params["tags"] ?: ""
        val memory = Memory(
            title = title,
            content = content,
            tags = tags,
            timestamp = System.currentTimeMillis()
        )
        deps.memoryViewModel.addMemory(memory)
        return ToolResult.success("已创建记忆：$title\n标签：$tags\n内容：$content")
    }
}

/**
 * 搜索记忆工具
 */
class SearchMemoryTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "search_memory"
    override val description = "搜索模型记忆中的内容，支持按关键词或标签搜索"
    override val parameters = listOf(
        ParamSpec("keyword", ParamType.STRING, "搜索关键词（与tag二选一）", required = false),
        ParamSpec("tag", ParamType.STRING, "按标签搜索，不需要#前缀（与keyword二选一）", required = false)
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val keyword = params["keyword"]
        val tag = params["tag"]

        val memories = when {
            !tag.isNullOrBlank() -> {
                withContext(Dispatchers.IO) {
                    deps.memoryViewModel.searchMemoriesByTagFromDb(tag)
                }
            }
            !keyword.isNullOrBlank() -> {
                withContext(Dispatchers.IO) {
                    searchMemoriesSmart(keyword)
                }
            }
            else -> {
                return ToolResult.failure("请提供 keyword 或 tag 参数")
            }
        }

        if (memories.isEmpty()) {
            return ToolResult.failure("未找到相关记忆")
        }

        val sb = StringBuilder("找到 ${memories.size} 条记忆：\n")
        memories.take(10).forEachIndexed { index, memory ->
            sb.append("【记忆${memory.id}】${memory.title}\n")
            sb.append("  内容：${memory.content}\n")
            if (memory.tags.isNotBlank()) {
                sb.append("  标签：${memory.tags}\n")
            }
            if (index < memories.size - 1) sb.append("\n")
        }
        if (memories.size > 10) {
            sb.append("...还有${memories.size - 10}条记忆")
        }
        return ToolResult.success(sb.toString().trim())
    }

    private suspend fun searchMemoriesSmart(keyword: String): List<Memory> {
        val exactResults = deps.memoryViewModel.searchMemoriesFromDb(keyword)
        if (exactResults.isNotEmpty()) {
            return exactResults
        }

        val tagResults = deps.memoryViewModel.searchMemoriesByTagFromDb(keyword)
        if (tagResults.isNotEmpty()) {
            return tagResults
        }

        val allMemories = deps.memoryViewModel.allMemories.first()
        val keywordLower = keyword.lowercase()

        val matched = allMemories.filter { memory ->
            val title = memory.title.lowercase()
            val content = memory.content.lowercase()
            val tags = memory.tags.lowercase()

            if (title.contains(keywordLower) || content.contains(keywordLower) || tags.contains(keywordLower)) {
                return@filter true
            }

            val cleanTags = tags.replace("#", "").split(Regex("\\s+")).filter { it.isNotBlank() }
            var matchCount = 0
            for (tag in cleanTags) {
                if (keywordLower.contains(tag) || tag.contains(keywordLower)) {
                    matchCount++
                }
            }
            if (matchCount > 0) {
                return@filter true
            }

            var charMatch = 0
            for (char in keywordLower) {
                if (title.contains(char) || content.contains(char) || tags.contains(char)) {
                    charMatch++
                }
            }
            charMatch > keywordLower.length * 0.5
        }

        return matched.sortedByDescending { it.timestamp }.take(5)
    }
}

/**
 * 查找相似记忆工具
 */
class FindSimilarMemoriesTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "find_similar_memories"
    override val description = "查找与给定信息相似的记忆，用于判断是否需要更新现有记忆而非创建新记忆。返回匹配度最高的记忆列表。"
    override val parameters = listOf(
        ParamSpec("keywords", ParamType.STRING, "关键词列表，用空格分隔，用于匹配记忆的标题和标签"),
        ParamSpec("content_preview", ParamType.STRING, "要存储的记忆内容预览，用于辅助判断匹配度", required = false)
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val keywordsStr = params["keywords"] ?: return ToolResult.failure("缺少关键词参数")
        val contentPreview = params["content_preview"] ?: ""

        val keywords = keywordsStr.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (keywords.isEmpty()) {
            return ToolResult.failure("关键词不能为空")
        }

        val allMemories = withContext(Dispatchers.IO) {
            deps.memoryViewModel.allMemories.first()
        }

        if (allMemories.isEmpty()) {
            return ToolResult.success("[无现有记忆] 当前记忆库为空，可以直接创建新记忆")
        }

        val scoredMemories = allMemories.map { memory ->
            val score = calculateMatchScore(memory, keywords, contentPreview)
            Pair(memory, score)
        }.filter { it.second > 0 }
         .sortedByDescending { it.second }

        if (scoredMemories.isEmpty()) {
            return ToolResult.success("[无匹配记忆] 未找到相似记忆，可以直接创建新记忆")
        }

        val topMatches = scoredMemories.take(3)
        val sb = StringBuilder("[找到相似记忆] 找到 ${topMatches.size} 条可能相关的记忆：\n\n")

        topMatches.forEachIndexed { index, (memory, score) ->
            val matchPercent = (score * 100).toInt()
            sb.append("【候选${index + 1}】(匹配度:$matchPercent%)\n")
            sb.append("  ID：${memory.id}\n")
            sb.append("  标题：${memory.title}\n")
            sb.append("  内容：${memory.content}\n")
            if (memory.tags.isNotBlank()) {
                sb.append("  标签：${memory.tags}\n")
            }
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(memory.timestamp))
            sb.append("  时间：$date\n")
            if (index < topMatches.size - 1) sb.append("\n")
        }

        sb.append("\n💡 如果这是同一条信息的更新，请调用 update_memory 工具更新该记忆；")
        sb.append("\n   如果这是全新的信息，请调用 create_memory 创建新记忆。")

        return ToolResult.success(sb.toString())
    }

    private fun calculateMatchScore(memory: Memory, keywords: List<String>, contentPreview: String): Double {
        var score = 0.0
        val titleLower = memory.title.lowercase()
        val tagsLower = memory.tags.lowercase()
        val contentLower = memory.content.lowercase()

        for (keyword in keywords) {
            // 标签匹配权重最高
            if (tagsLower.contains(keyword)) {
                score += 0.4
            }
            // 标题匹配
            if (titleLower.contains(keyword)) {
                score += 0.3
            }
            // 内容匹配
            if (contentLower.contains(keyword)) {
                score += 0.2
            }
            // 关键词被内容包含（反向匹配）
            if (keyword.length >= 2) {
                for (word in memory.title.lowercase().split(Regex("[\\s#]+")).filter { it.length >= 2 }) {
                    if (keyword.contains(word) || word.contains(keyword)) {
                        score += 0.1
                        break
                    }
                }
            }
        }

        // 如果有内容预览，检查是否涉及相似概念
        if (contentPreview.isNotBlank()) {
            val previewLower = contentPreview.lowercase()
            // 检查内容预览中的关键信息是否与现有记忆重复
            val existingValues = extractKeyValues(memory.content)
            val previewValues = extractKeyValues(contentPreview)

            for (existing in existingValues) {
                for (preview in previewValues) {
                    if (existing.isNotBlank() && preview.isNotBlank() &&
                        existing != preview &&
                        (existing.length >= 3 || preview.length >= 3)) {
                        // 值不同但概念相同，可能是更新
                        if (existing.length > 2 && preview.length > 2) {
                            score += 0.1
                        }
                    }
                }
            }
        }

        // 限制分数最高为1.0
        return minOf(score, 1.0)
    }

    private fun extractKeyValues(content: String): List<String> {
        // 提取内容中的关键值（如密码、号码等）
        val values = mutableListOf<String>()
        val patterns = listOf(
            Regex("密码[是为：:]*([\\w@#$%^&*]+)"),
            Regex("(?:是|为|：|:)([\\w@#$%^&*]{4,})"),
            Regex("(\\d{4,})") // 4位以上数字
        )
        for (pattern in patterns) {
            val matches = pattern.findAll(content)
            for (match in matches) {
                match.groupValues.getOrNull(1)?.let { values.add(it) }
            }
        }
        return values
    }
}

/**
 * 更新记忆工具
 */
class UpdateMemoryTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "update_memory"
    override val description = "更新指定ID的记忆内容，用于信息变更时修改现有记忆而非创建重复记录"
    override val parameters = listOf(
        ParamSpec("id", ParamType.LONG, "要更新的记忆ID（必须）"),
        ParamSpec("title", ParamType.STRING, "新的标题（可选，不提供则保留原标题）", required = false),
        ParamSpec("content", ParamType.STRING, "新的内容（必须）"),
        ParamSpec("tags", ParamType.STRING, "新的标签（可选，不提供则保留原标签）", required = false)
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val idStr = params["id"] ?: return ToolResult.failure("缺少记忆ID（必须参数）")
        val id = idStr.toLongOrNull() ?: return ToolResult.failure("记忆ID格式不正确")

        val existingMemory = withContext(Dispatchers.IO) {
            deps.memoryViewModel.getMemoryById(id)
        }

        if (existingMemory == null) {
            return ToolResult.failure("未找到ID为 $id 的记忆，更新失败")
        }

        val newTitle = if (params.containsKey("title") && params["title"]?.isNotBlank() == true) {
            params["title"]!!
        } else {
            existingMemory.title
        }

        val newContent = params["content"] ?: return ToolResult.failure("缺少内容参数（必须）")

        val newTags = if (params.containsKey("tags") && params["tags"]?.isNotBlank() == true) {
            params["tags"]!!
        } else {
            existingMemory.tags
        }

        val updatedMemory = existingMemory.copy(
            title = newTitle,
            content = newContent,
            tags = newTags,
            timestamp = System.currentTimeMillis()
        )
        deps.memoryViewModel.addMemory(updatedMemory)

        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(updatedMemory.timestamp))
        return ToolResult.success("已更新记忆【${updatedMemory.title}】\n新的内容：$newContent\n更新后标签：$newTags\n更新时间：$date")
    }
}
