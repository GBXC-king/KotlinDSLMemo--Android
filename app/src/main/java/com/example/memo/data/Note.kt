package com.example.memo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 笔记数据实体类
 *
 * 该类对应 Room 数据库中的 "notes" 表，用于存储备忘录的每一条记录。
 * 使用 @Entity 注解标记为数据库表，@PrimaryKey 标记主键字段。
 *
 * @property id    笔记的唯一标识，由数据库自动生成（autoGenerate = true）
 * @property title 笔记标题
 * @property content 笔记正文内容
 * @property timestamp 笔记的创建/修改时间戳（毫秒），默认取当前系统时间
 */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["timestamp"])]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
