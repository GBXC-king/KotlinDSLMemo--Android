package com.example.memo.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memo.data.Ledger
import com.example.memo.data.Transaction
import com.example.memo.repository.RepositoryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class LedgerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RepositoryProvider.getLedgerRepository()

    val allLedgers: Flow<List<Ledger>> = repository.allLedgers

    fun getTransactionsByLedgerId(ledgerId: Long): Flow<List<Transaction>> {
        return repository.getTransactionsByLedgerId(ledgerId)
    }

    fun addLedger(ledger: Ledger) {
        viewModelScope.launch {
            repository.insertLedger(ledger)
        }
    }

    suspend fun addLedgerSync(ledger: Ledger): Long {
        return repository.insertLedger(ledger)
    }

    suspend fun findLedgerByTitle(title: String): Ledger? {
        return repository.getLedgerByTitle(title)
    }

    fun deleteLedger(ledger: Ledger) {
        viewModelScope.launch {
            repository.deleteLedger(ledger)
        }
    }

    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    suspend fun getMonthTotal(ledgerId: Long, month: Int, year: Int, type: Int): Double {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endTime = calendar.timeInMillis

        return repository.getTransactionsByTimeRange(ledgerId, startTime, endTime)
            .first()
            .filter { it.type == type }
            .sumOf { it.amount }
    }

    fun getCurrentMonthStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getCurrentMonthEnd(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.MONTH, 1)
        return calendar.timeInMillis
    }

    fun getLastMonthStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.MONTH, -1)
        return calendar.timeInMillis
    }

    fun getLastMonthEnd(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getCurrentYearStart(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, Calendar.JANUARY)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    fun getCurrentYearEnd(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.MONTH, Calendar.DECEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 31)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }

    fun calculateTotal(transactions: List<Transaction>, type: Int): Double {
        return transactions
            .filter { it.type == type }
            .sumOf { it.amount }
    }

    fun filterTransactionsByTimeRange(
        transactions: List<Transaction>,
        startTime: Long,
        endTime: Long
    ): List<Transaction> {
        return transactions.filter { it.timestamp >= startTime && it.timestamp < endTime }
    }
}
