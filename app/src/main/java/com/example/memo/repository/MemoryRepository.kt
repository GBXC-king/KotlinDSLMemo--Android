package com.example.memo.repository

import com.example.memo.data.Memory
import com.example.memo.data.MemoryDao
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {

    val allMemories: Flow<List<Memory>> = memoryDao.getAllMemories()

    suspend fun getMemoryById(id: Long): Memory? = memoryDao.getMemoryById(id)

    suspend fun searchMemories(keyword: String): List<Memory> = memoryDao.searchMemories(keyword)

    suspend fun searchMemoriesByTag(tag: String): List<Memory> = memoryDao.searchMemoriesByTag(tag)

    suspend fun insertMemory(memory: Memory): Long = memoryDao.insertMemory(memory)

    suspend fun deleteMemory(memory: Memory): Int = memoryDao.deleteMemory(memory)
}
