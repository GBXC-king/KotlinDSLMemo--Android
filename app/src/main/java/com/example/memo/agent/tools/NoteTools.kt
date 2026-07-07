package com.example.memo.agent.tools

import com.example.memo.agent.AgentTool
import com.example.memo.agent.ParamSpec
import com.example.memo.agent.ParamType
import com.example.memo.agent.ToolResult
import com.example.memo.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 创建笔记工具
 */
class CreateNoteTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "create_note"
    override val description = "创建一条新的笔记备忘录"
    override val parameters = listOf(
        ParamSpec("title", ParamType.STRING, "笔记标题", required = false, defaultValue = "无标题"),
        ParamSpec("content", ParamType.STRING, "笔记内容", required = false, defaultValue = "")
    )
    
    override val promptFragment = """
        ### 自动创建笔记规则
        当用户提出以下请求时，在给出最终答案前先调用 create_note 创建笔记：
        - 解答题目（数学题、物理题、编程题等）
        - 写文章、作文、文案、诗歌等
        - 总结内容、整理资料、翻译较长内容
        - 提供方案、建议、教程等
        
        笔记标题要简洁概括，内容要包含用户问题和完整解答。创建后在最终答案中告知用户已创建笔记。
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val title = params["title"] ?: "无标题"
        val content = params["content"] ?: ""
        val note = Note(title = title, content = content, timestamp = System.currentTimeMillis())
        deps.noteViewModel.addNote(note)
        return ToolResult.success("已创建笔记：$title")
    }
}

/**
 * 搜索笔记工具
 */
class SearchNoteTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "search_note"
    override val description = "搜索包含关键词的笔记，返回笔记列表供后续操作"
    override val parameters = listOf(
        ParamSpec("keyword", ParamType.STRING, "搜索关键词")
    )
    
    override val promptFragment = """
        ### 笔记修改/删除规则
        - 修改或删除笔记前，必须先调用 search_note 获取笔记ID
        - 修改笔记时，content 参数必须传入修改后的完整内容，不能只传要修改的部分
    """.trimIndent()

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val keyword = params["keyword"] ?: return ToolResult.failure("缺少搜索关键词")

        val notes = withContext(Dispatchers.IO) {
            searchNotesSmart(keyword)
        }

        if (notes.isEmpty()) {
            return ToolResult.failure("未找到包含\"$keyword\"的笔记")
        }

        val sb = StringBuilder("找到 ${notes.size} 条笔记：\n")
        notes.take(10).forEachIndexed { index, note ->
            sb.append("【笔记${note.id}】${note.title}\n")
            sb.append("  内容：${note.content}\n")
        }
        if (notes.size > 10) {
            sb.append("...还有${notes.size - 10}条笔记")
        }
        sb.append("\n\n💡 如需修改或删除，请告诉我笔记ID")
        return ToolResult.success(sb.toString())
    }

    private suspend fun searchNotesSmart(keyword: String): List<Note> {
        val exactResults = deps.noteViewModel.searchNotesFromDb(keyword)
        if (exactResults.isNotEmpty()) {
            return exactResults
        }

        val keywords = extractKeywords(keyword)
        if (keywords.size <= 1) {
            return emptyList()
        }

        val matchedNotes = mutableListOf<Note>()
        val allNotes = withContext(Dispatchers.IO) {
            deps.noteViewModel.allNotes.first()
        }

        for (note in allNotes) {
            val titleLower = note.title.lowercase()
            val contentLower = note.content.lowercase()

            val matchedKeywords = keywords.filter { kw ->
                titleLower.contains(kw) || contentLower.contains(kw)
            }

            if (matchedKeywords.size >= keywords.size) {
                matchedNotes.add(note)
            } else if (matchedKeywords.size >= 2 && matchedKeywords.size >= keywords.size * 0.5) {
                matchedNotes.add(note)
            }
        }

        return matchedNotes.sortedByDescending { it.timestamp }
    }

    private fun extractKeywords(keyword: String): List<String> {
        val cleaned = keyword
            .replace(Regex("[，。、；：！？,.!?;:()（）【】\\[\\]]"), " ")
            .trim()

        val words = cleaned.split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .map { it.lowercase() }
            .distinct()

        if (words.size <= 1) {
            val nonSpaceChars = cleaned.filter { !it.isWhitespace() }
            val result = mutableListOf<String>()
            for (i in 0 until nonSpaceChars.length - 1) {
                result.add(nonSpaceChars.substring(i, i + 2))
            }
            return result.distinct()
        }

        return words
    }
}

/**
 * 更新笔记工具
 */
class UpdateNoteTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "update_note"
    override val description = "修改指定ID的笔记内容"
    override val parameters = listOf(
        ParamSpec("id", ParamType.LONG, "笔记ID（必须）"),
        ParamSpec("title", ParamType.STRING, "新的标题（可选）", required = false),
        ParamSpec("content", ParamType.STRING, "新的内容（可选）", required = false)
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val idStr = params["id"] ?: return ToolResult.failure("缺少笔记ID（必须参数）")
        val id = idStr.toLongOrNull() ?: return ToolResult.failure("笔记ID格式不正确")

        val existingNote = withContext(Dispatchers.IO) {
            deps.noteViewModel.getNoteById(id)
        }

        if (existingNote == null) {
            return ToolResult.failure("未找到ID为 $id 的笔记")
        }

        val newTitle = if (params.containsKey("title") && params["title"]?.isNotBlank() == true) {
            params["title"]!!
        } else {
            existingNote.title
        }
        val newContent = if (params.containsKey("content") && params["content"]?.isNotBlank() == true) {
            params["content"]!!
        } else {
            existingNote.content
        }

        val updatedNote = existingNote.copy(
            title = newTitle,
            content = newContent,
            timestamp = System.currentTimeMillis()
        )
        deps.noteViewModel.addNote(updatedNote)

        return ToolResult.success("已修改笔记【${updatedNote.title}】\n新的标题：$newTitle\n新的内容：$newContent")
    }
}

/**
 * 删除笔记工具
 */
class DeleteNoteTool(private val deps: ToolDependencies) : AgentTool {
    override val name = "delete_note"
    override val description = "删除指定ID的笔记"
    override val parameters = listOf(
        ParamSpec("id", ParamType.LONG, "要删除的笔记ID（必须）")
    )

    override suspend fun execute(params: Map<String, String>): ToolResult {
        val idStr = params["id"] ?: return ToolResult.failure("缺少笔记ID（必须参数）")
        val id = idStr.toLongOrNull() ?: return ToolResult.failure("笔记ID格式不正确")

        val existingNote = withContext(Dispatchers.IO) {
            deps.noteViewModel.getNoteById(id)
        }

        if (existingNote == null) {
            return ToolResult.failure("未找到ID为 $id 的笔记，删除失败")
        }

        deps.noteViewModel.deleteNote(existingNote)
        return ToolResult.success("已删除笔记【${existingNote.title}】")
    }
}
