package com.example.memo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: Long): Memory?

    @Query("SELECT * FROM memories WHERE LOWER(title) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(content) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(tags) LIKE '%' || LOWER(:keyword) || '%' ORDER BY timestamp DESC")
    suspend fun searchMemories(keyword: String): List<Memory>

    @Query("SELECT * FROM memories WHERE LOWER(tags) LIKE '%' || LOWER(:tag) || '%' ORDER BY timestamp DESC")
    suspend fun searchMemoriesByTag(tag: String): List<Memory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: Memory): Long

    @Delete
    suspend fun deleteMemory(memory: Memory): Int

}
