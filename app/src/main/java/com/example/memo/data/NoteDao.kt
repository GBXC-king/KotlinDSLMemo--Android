package com.example.memo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 笔记数据访问对象（DAO - Data Access Object）
 *
 * 该接口定义了对 "notes" 表的所有数据库操作方法。
 * Room 会在编译时自动生成这些方法的实现代码，无需手写 SQL 执行逻辑。
 * 使用 @Dao 注解标记，Room 才能识别并处理该接口。
 */
@Dao
interface NoteDao {

    /**
     * 查询所有笔记，按时间戳降序排列（最新的笔记排在前面）
     *
     * 返回类型为 Flow<List<Note>>，这意味着当数据库中的笔记数据发生变化时，
     * Flow 会自动发出新的列表，UI 层通过 collectAsState 即可自动刷新。
     *
     * @return 笔记列表的 Flow 流
     */
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    /**
     * 按关键词搜索笔记（不区分大小写模糊匹配）
     * 同时搜索标题和内容
     *
     * @param keyword 搜索关键词
     * @return 匹配的笔记列表
     */
    @Query("SELECT * FROM notes WHERE LOWER(title) LIKE '%' || LOWER(:keyword) || '%' OR LOWER(content) LIKE '%' || LOWER(:keyword) || '%' ORDER BY timestamp DESC")
    suspend fun searchNotes(keyword: String): List<Note>

    /**
     * 根据ID查询单条笔记
     *
     * @param id 笔记ID
     * @return 对应的笔记，不存在则返回null
     */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    /**
     * 插入一条笔记
     *
     * 如果插入的笔记 id 与已有记录冲突（即 id 相同），则替换已有记录。
     * 这意味着该方法也可用于更新笔记——传入带有相同 id 的新 Note 对象即可。
     * 使用 suspend 修饰，表示这是一个协程挂起函数，需在协程中调用。
     *
     * @param note 要插入（或更新）的笔记对象
     * @return 插入记录的行 id
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    /**
     * 删除一条笔记
     *
     * 根据传入的 Note 对象的主键 id 删除对应的数据库记录。
     * 使用 suspend 修饰，需在协程中调用。
     *
     * @param note 要删除的笔记对象
     * @return 被删除的记录数（通常为 1，如果记录不存在则为 0）
     */
    @Delete
    suspend fun deleteNote(note: Note): Int

}
