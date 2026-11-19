package com.driftnotes.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoteRepository private constructor(context: Context) {

    private val dao = AppDatabase.get(context.applicationContext).noteDao()

    suspend fun add(note: Note): Long = withContext(Dispatchers.IO) { dao.insert(note) }

    suspend fun update(note: Note) = withContext(Dispatchers.IO) { dao.update(note) }

    suspend fun delete(note: Note) = withContext(Dispatchers.IO) { dao.delete(note) }

    suspend fun getById(id: Long): Note? = withContext(Dispatchers.IO) { dao.getById(id) }

    suspend fun getActive(): List<Note> = withContext(Dispatchers.IO) { dao.getActive() }

    fun observeAll(): Flow<List<Note>> = dao.observeAll()

    companion object {
        @Volatile private var INSTANCE: NoteRepository? = null

        fun get(context: Context): NoteRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: NoteRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
