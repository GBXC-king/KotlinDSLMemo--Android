package com.example.memo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库类（Room Database）
 *
 * 该类是 Room 持久化库的入口，负责管理数据库的创建和版本控制。
 * 使用单例模式确保整个应用只有一个数据库实例，避免重复创建造成的资源浪费。
 *
 * @Database 注解参数说明：
 * - entities = [Note::class]：声明该数据库包含的实体表（即 Note 表）
 * - version = 2：数据库版本号，当表结构发生变化时需要递增
 * - exportSchema = false：不导出数据库 schema 文件（简化构建配置）
 */
@Database(entities = [Note::class, Ledger::class, Transaction::class, Memory::class, Alarm::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao

    abstract fun ledgerDao(): LedgerDao

    abstract fun transactionDao(): TransactionDao

    abstract fun memoryDao(): MemoryDao

    abstract fun alarmDao(): AlarmDao

    companion object {
        /**
         * 数据库单例实例
         * 使用 @Volatile 确保多线程环境下对该变量的修改对所有线程立即可见，
         * 防止多线程同时创建多个数据库实例
         */
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 版本 1 → 2：添加账本表和交易表
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `ledgers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `unit` TEXT NOT NULL DEFAULT '元',
                        `color` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `ledgerId` INTEGER NOT NULL,
                        `type` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `note` TEXT NOT NULL DEFAULT '',
                        `timestamp` INTEGER NOT NULL
                    )"""
                )
            }
        }

        // 版本 2 → 3：预留迁移（如 Note 表结构调整）
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 预留：若无结构变更可为空
            }
        }

        // 版本 3 → 4：添加记忆表
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `memories` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `tags` TEXT NOT NULL DEFAULT '',
                        `timestamp` INTEGER NOT NULL
                    )"""
                )
            }
        }

        // 版本 4 → 5：添加索引以优化查询性能
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 为 notes 表的 timestamp 添加索引
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_timestamp` ON `notes` (`timestamp`)")
                // 为 ledgers 表的 title 添加索引
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_ledgers_title` ON `ledgers` (`title`)")
                // 为 transactions 表的 ledgerId、timestamp、type 添加索引
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_ledgerId` ON `transactions` (`ledgerId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)")
                // 为 memories 表的 timestamp 添加索引
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_memories_timestamp` ON `memories` (`timestamp`)")
            }
        }

        // 版本 5 → 6：添加闹钟表
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `alarms` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `repeatDays` INTEGER NOT NULL,
                        `ringtoneType` INTEGER NOT NULL,
                        `ringtoneUri` TEXT NOT NULL DEFAULT '',
                        `vibrate` INTEGER NOT NULL,
                        `deleteAfterDismiss` INTEGER NOT NULL,
                        `snoozeEnabled` INTEGER NOT NULL,
                        `snoozeInterval` INTEGER NOT NULL,
                        `snoozeCount` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )"""
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_alarms_createdAt` ON `alarms` (`createdAt`)")
            }
        }

        /**
         * 获取数据库实例（双重检查锁定单例模式）
         *
         * 如果实例已存在则直接返回，否则在 synchronized 同步块中创建新实例。
         * 双重检查锁定（Double-Check Locking）既保证了线程安全，又避免了每次调用都加锁的性能开销。
         *
         * @param context 应用上下文，使用 applicationContext 避免内存泄漏
         * @return AppDatabase 数据库单例实例
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,  // 使用应用级 Context，防止 Activity 泄漏
                    AppDatabase::class.java,
                    "memo_database"              // 数据库文件名
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration() // 遇到未定义的迁移版本时销毁重建，防止崩溃
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
