package com.example.memo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 闹钟数据访问对象（DAO）
 *
 * 定义对 "alarms" 表的所有数据库操作方法。
 */
@Dao
interface AlarmDao {

    /**
     * 查询所有闹钟，按创建时间降序排列
     */
    @Query("SELECT * FROM alarms ORDER BY createdAt DESC")
    fun getAllAlarms(): Flow<List<Alarm>>

    /**
     * 根据 ID 查询单个闹钟
     */
    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarmById(id: Long): Alarm?

    /**
     * 插入或更新闹钟
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: Alarm): Long

    /**
     * 删除单个闹钟
     */
    @Delete
    suspend fun deleteAlarm(alarm: Alarm): Int

    /**
     * 根据 ID 删除闹钟
     */
    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarmById(id: Long): Int

    /**
     * 更新闹钟启用状态
     */
    @Query("UPDATE alarms SET isEnabled = :enabled WHERE id = :id")
    suspend fun updateAlarmEnabled(id: Long, enabled: Boolean): Int
}
