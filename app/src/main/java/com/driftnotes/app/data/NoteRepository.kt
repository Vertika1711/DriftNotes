package com.driftnotes.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class NoteRepository private constructor(context: Context) {

    private val dao = AppDatabase.get(context).noteDao()

    // TODO: Phase 2 — replace stubs with real Dispatchers.IO implementations
    suspend fun add(note: Note): Long = 0L
    suspend fun update(note: Note) {}
    suspend fun delete(note: Note) {}
    suspend fun getById(id: Long): Note? = null
    suspend fun getActive(): List<Note> = emptyList()
    fun observeAll(): Flow<List<Note>> = flowOf(emptyList())

    companion object {
        @Volatile private var INSTANCE: NoteRepository? = null

        fun get(context: Context): NoteRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: NoteRepository(context.applicationContext).also { INSTANCE = it }
        }
    }
}
