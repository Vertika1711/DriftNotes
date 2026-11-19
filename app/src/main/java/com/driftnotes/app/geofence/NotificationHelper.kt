package com.driftnotes.app.geofence

import android.content.Context
import android.util.Log
import com.driftnotes.app.data.Note

object NotificationHelper {

    // TODO: Phase 3 - Member C

    fun createChannel(context: Context) {
        Log.d("NotificationHelper", "createChannel called")
    }

    fun showNoteNotification(context: Context, note: Note?) {
        Log.d("NotificationHelper", "showNoteNotification called for note id=${note?.id}")
    }
}
