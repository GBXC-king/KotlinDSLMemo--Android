package com.example.memo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
//交易接口
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE ledgerId = :ledgerId ORDER BY timestamp DESC")
    fun getTransactionsByLedgerId(ledgerId: Long): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Delete
    suspend fun deleteTransaction(transaction: Transaction): Int

    @Query("SELECT * FROM transactions WHERE ledgerId = :ledgerId AND timestamp >= :startTime AND timestamp < :endTime ORDER BY timestamp DESC")
    fun getTransactionsByTimeRange(ledgerId: Long, startTime: Long, endTime: Long): Flow<List<Transaction>>
}
