package com.example.memo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {

    @Query("SELECT * FROM ledgers ORDER BY timestamp DESC")
    fun getAllLedgers(): Flow<List<Ledger>>

    @Query("SELECT * FROM ledgers WHERE title = :title LIMIT 1")
    suspend fun getLedgerByTitle(title: String): Ledger?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedger(ledger: Ledger): Long

    @Delete
    suspend fun deleteLedger(ledger: Ledger): Int

    @Query("DELETE FROM ledgers WHERE id = :ledgerId")
    suspend fun deleteLedgerById(ledgerId: Long): Int
}
