package com.example.memo.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memo.data.Note
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.getNoteRepository()

    val allNotes: Flow<List<Note>> = repository.allNotes

    // ==================== 搜索相关状态 ====================

    /**
     * 搜索关键词（可变状态流）
     * 使用 MutableStateFlow 作为可变数据源，对外暴露只读的 StateFlow
     */
    private val _searchQuery = MutableStateFlow("")

    /**
     * 搜索关键词（只读状态流）
     * UI 层通过该属性观察搜索关键词的变化
     */
    val searchQuery: StateFlow<String> = _searchQuery

    /**
     * 是否正在搜索（可变状态流）
     * 当搜索关键词非空时为 true，用于控制搜索相关的 UI 显示
     */
    private val _isSearching = MutableStateFlow(false)

    /**
     * 是否正在搜索（只读状态流）
     */
    val isSearching: StateFlow<Boolean> = _isSearching

    /**
     * 设置搜索关键词
     *
     * 当关键词非空时自动进入搜索状态（isSearching = true），
     * 当关键词为空时退出搜索状态（isSearching = false）
     *
     * @param query 新的搜索关键词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _isSearching.value = query.isNotBlank()
    }

    /**
     * 清除搜索状态
     * 重置搜索关键词和搜索状态标记
     */
    fun clearSearch() {
        _searchQuery.value = ""
        _isSearching.value = false
    }

    // ==================== 笔记增删操作 ====================

    /**
     * 添加（或更新）一条笔记
     *
     * 在 viewModelScope 协程中执行数据库插入操作。
     * 如果传入的 Note 对象 id 为 0，则插入新记录；
     * 如果传入的 Note 对象 id 与已有记录相同，则替换（更新）该记录。
     *
     * @param note 要添加或更新的笔记对象
     */
    fun addNote(note: Note) {
        viewModelScope.launch {
            repository.insertNote(note)
        }
    }

    /**
     * 删除一条笔记
     *
     * 在 viewModelScope 协程中执行数据库删除操作。
     *
     * @param note 要删除的笔记对象
     */
    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    /**
     * 搜索笔记（在内存中过滤）
     *
     * 该方法在内存中对已加载的笔记列表进行过滤，而非发起数据库查询。
     * 搜索逻辑为不区分大小写的模糊匹配，同时搜索标题和内容。
     *
     * @param notes 当前所有笔记列表
     * @param query 搜索关键词
     * @return 匹配的笔记列表
     */
    fun searchNotes(notes: List<Note>, query: String): List<Note> {
        if (query.isBlank()) return notes

        val lowerQuery = query.lowercase()
        return notes.filter { note ->
            note.title.lowercase().contains(lowerQuery) ||
                    note.content.lowercase().contains(lowerQuery)
        }
    }

    /**
     * 在数据库中搜索笔记
     *
     * @param keyword 搜索关键词
     * @return 匹配的笔记列表
     */
    suspend fun searchNotesFromDb(keyword: String): List<Note> {
        return repository.searchNotes(keyword)
    }

    /**
     * 根据ID获取笔记
     *
     * @param id 笔记ID
     * @return 对应的笔记，不存在则返回null
     */
    suspend fun getNoteById(id: Long): Note? {
        return repository.getNoteById(id)
    }
}
