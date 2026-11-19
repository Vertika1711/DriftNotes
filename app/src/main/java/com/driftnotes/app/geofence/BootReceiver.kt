package com.driftnotes.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.driftnotes.app.data.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed; restoring geofences")
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notes = NoteRepository.get(appContext).getActive()
                Log.i(TAG, "Retrieved ${notes.size} active notes")
                Log.i(TAG, "Starting geofence restoration")
                GeofenceManager(appContext).registerAll(notes)
            } catch (exception: Exception) {
                Log.e(TAG, "Geofence restoration failed after boot completed", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DriftGeo"
    }
}
