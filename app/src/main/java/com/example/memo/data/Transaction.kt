package com.example.memo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
//交易实体类
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["ledgerId"]),
        Index(value = ["timestamp"]),
        Index(value = ["type"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ledgerId: Long,
    val type: Int,
    val amount: Double,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_EXPENSE = 0
        const val TYPE_INCOME = 1
    }
}
