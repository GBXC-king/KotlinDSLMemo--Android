package com.example.memo.repository

import com.example.memo.data.Ledger
import com.example.memo.data.LedgerDao
import com.example.memo.data.Transaction
import com.example.memo.data.TransactionDao
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val ledgerDao: LedgerDao,
    private val transactionDao: TransactionDao
) {

    val allLedgers: Flow<List<Ledger>> = ledgerDao.getAllLedgers()

    fun getTransactionsByLedgerId(ledgerId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByLedgerId(ledgerId)

    fun getTransactionsByTimeRange(ledgerId: Long, startTime: Long, endTime: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByTimeRange(ledgerId, startTime, endTime)

    suspend fun insertLedger(ledger: Ledger): Long = ledgerDao.insertLedger(ledger)

    suspend fun getLedgerByTitle(title: String): Ledger? = ledgerDao.getLedgerByTitle(title)

    suspend fun deleteLedger(ledger: Ledger): Int = ledgerDao.deleteLedger(ledger)

    suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insertTransaction(transaction)

    suspend fun deleteTransaction(transaction: Transaction): Int =
        transactionDao.deleteTransaction(transaction)
}
