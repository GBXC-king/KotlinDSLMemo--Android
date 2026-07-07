package com.example.memo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ledgers",
    indices = [Index(value = ["title"])]
)
data class Ledger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val unit: String = "元",
    val color: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
