package com.example.memo.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memo.data.Memory
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.getMemoryRepository()

    val allMemories: Flow<List<Memory>> = repository.allMemories

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _isSearching.value = query.isNotBlank()
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _isSearching.value = false
    }

    fun addMemory(memory: Memory) {
        viewModelScope.launch {
            repository.insertMemory(memory)
        }
    }

    fun deleteMemory(memory: Memory) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    fun updateMemoryTags(memory: Memory, newTags: String) {
        viewModelScope.launch {
            repository.insertMemory(memory.copy(tags = newTags))
        }
    }

    fun searchMemories(memories: List<Memory>, query: String): List<Memory> {
        if (query.isBlank()) return memories

        val lowerQuery = query.lowercase()
        return memories.filter { memory ->
            memory.title.lowercase().contains(lowerQuery) ||
                    memory.content.lowercase().contains(lowerQuery) ||
                    memory.tags.lowercase().contains(lowerQuery)
        }
    }

    suspend fun searchMemoriesFromDb(keyword: String): List<Memory> {
        return repository.searchMemories(keyword)
    }

    suspend fun searchMemoriesByTagFromDb(tag: String): List<Memory> {
        return repository.searchMemoriesByTag(tag)
    }

    suspend fun getMemoryById(id: Long): Memory? {
        return repository.getMemoryById(id)
    }
}
