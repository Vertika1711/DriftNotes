package com.driftnotes.app.geofence

import android.content.Context
import android.util.Log
import com.driftnotes.app.data.Note

class GeofenceManager(private val context: Context) {

    // TODO: Phase 3 - Member C

    fun register(note: Note) {
        Log.d("GeofenceManager", "register called for note id=${note.id}")
    }

    fun remove(noteId: Long) {
        Log.d("GeofenceManager", "remove called for noteId=$noteId")
    }

    fun registerAll(notes: List<Note>) {
        Log.d("GeofenceManager", "registerAll called with ${notes.size} notes")
    }
}
