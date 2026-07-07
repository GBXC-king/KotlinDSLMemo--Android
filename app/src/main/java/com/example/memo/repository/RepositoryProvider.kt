package com.example.memo.repository

import android.content.Context
import com.example.memo.data.AppDatabase

/**
 * Repository 单例提供者
 * 在 Application 中初始化，全局共享 Repository 实例
 */
object RepositoryProvider {
    
    private lateinit var noteRepository: NoteRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var ledgerRepository: LedgerRepository
    private lateinit var alarmRepository: AlarmRepository

    /**
     * 初始化所有 Repository 实例
     * 应在 Application.onCreate() 中调用
     */
    fun init(context: Context) {
        val database = AppDatabase.getDatabase(context)
        noteRepository = NoteRepository(database.noteDao())
        memoryRepository = MemoryRepository(database.memoryDao())
        ledgerRepository = LedgerRepository(database.ledgerDao(), database.transactionDao())
        alarmRepository = AlarmRepository(database.alarmDao())
    }
    
    /**
     * 获取 NoteRepository 单例
     */
    fun getNoteRepository(): NoteRepository {
        check(::noteRepository.isInitialized) { "RepositoryProvider 未初始化，请在 Application.onCreate() 中调用 init()" }
        return noteRepository
    }
    
    /**
     * 获取 MemoryRepository 单例
     */
    fun getMemoryRepository(): MemoryRepository {
        check(::memoryRepository.isInitialized) { "RepositoryProvider 未初始化，请在 Application.onCreate() 中调用 init()" }
        return memoryRepository
    }
    
    /**
     * 获取 LedgerRepository 单例
     */
    fun getLedgerRepository(): LedgerRepository {
        check(::ledgerRepository.isInitialized) { "RepositoryProvider 未初始化，请在 Application.onCreate() 中调用 init()" }
        return ledgerRepository
    }

    /**
     * 获取 AlarmRepository 单例
     */
    fun getAlarmRepository(): AlarmRepository {
        check(::alarmRepository.isInitialized) { "RepositoryProvider 未初始化，请在 Application.onCreate() 中调用 init()" }
        return alarmRepository
    }
}
