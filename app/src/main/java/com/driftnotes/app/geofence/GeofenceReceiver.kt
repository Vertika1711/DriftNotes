package com.driftnotes.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.driftnotes.app.data.NoteRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent)
        if (event == null) {
            Log.e(TAG, "Received a null geofencing event")
            return
        }

        if (event.hasError()) {
            val errorCode = event.errorCode
            Log.e(
                TAG,
                "Geofencing event failed (errorCode=$errorCode, " +
                    "${GeofenceStatusCodes.getStatusCodeString(errorCode)})"
            )
            return
        }

        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        val triggeredGeofences = event.triggeringGeofences.orEmpty()
        if (triggeredGeofences.isEmpty()) {
            Log.w(TAG, "ENTER event contained no triggered geofences")
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = NoteRepository.get(appContext)
                for (geofence in triggeredGeofences) {
                    val requestId = geofence.requestId
                    val noteId = requestId.toLongOrNull()
                    if (noteId == null) {
                        Log.e(TAG, "Ignoring invalid geofence request ID: $requestId")
                        continue
                    }

                    try {
                        val note = repository.getById(noteId)
                        if (note == null) {
                            Log.w(TAG, "No note found for geofence request ID: $requestId")
                        } else if (note.isActive) {
                            NotificationHelper.showNoteNotification(appContext, note)
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.e(
                            TAG,
                            "Failed to process note for geofence request ID: $requestId",
                            exception
                        )
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(TAG, "Failed to process geofencing event", exception)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "DriftGeo"
    }
}
