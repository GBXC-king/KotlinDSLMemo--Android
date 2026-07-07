package com.example.memo.repository

import com.example.memo.data.Note
import com.example.memo.data.NoteDao
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {

    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    suspend fun searchNotes(keyword: String): List<Note> = noteDao.searchNotes(keyword)

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)

    suspend fun deleteNote(note: Note): Int = noteDao.deleteNote(note)
}
