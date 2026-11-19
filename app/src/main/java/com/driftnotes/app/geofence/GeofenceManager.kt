package com.driftnotes.app.geofence

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.driftnotes.app.data.Note
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class GeofenceManager(context: Context) {

    private val appContext = context.applicationContext
    private val geofencingClient = LocationServices.getGeofencingClient(appContext)
    private val geofencePendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            appContext,
            GEOFENCE_PENDING_INTENT_REQUEST_CODE,
            Intent(appContext, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    fun register(note: Note) {
        val requestId = note.id.toString()
        val task = registrationTask(note) ?: return
        task.addOnSuccessListener {
            Log.i(TAG, "Geofence registered (requestId=$requestId)")
        }.addOnFailureListener { exception ->
            logFailure("register", requestId, exception)
        }
    }

    suspend fun registerAndAwait(note: Note): Boolean {
        val requestId = note.id.toString()
        val task = registrationTask(note) ?: return false
        return try {
            task.await()
            Log.i(TAG, "Geofence registered (requestId=$requestId)")
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logFailure("register", requestId, exception)
            false
        }
    }

    private fun registrationTask(note: Note): Task<Void>? {
        val requestId = note.id.toString()
        if (!hasFineLocationPermission("register", requestId)) return null
        try {
            val geofence = Geofence.Builder()
                .setRequestId(requestId)
                .setCircularRegion(note.lat, note.lng, note.radiusM)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofence(geofence)
                .build()

            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(
                    TAG,
                    "ACCESS_BACKGROUND_LOCATION is not granted (requestId=$requestId); " +
                        "geofence reminders may not trigger while the app is closed"
                )
            }
            Log.i(
                TAG,
                "Submitting geofence request (requestId=$requestId, radiusM=${note.radiusM}, " +
                    "transition=ENTER, initialTrigger=ENTER, expiration=NEVER_EXPIRE)"
            )
            Log.d(
                TAG,
                "Geofence PendingIntent target=${GeofenceReceiver::class.java.name}, " +
                    "requestCode=$GEOFENCE_PENDING_INTENT_REQUEST_CODE, " +
                    "flags=FLAG_UPDATE_CURRENT|FLAG_MUTABLE"
            )
            return geofencingClient.addGeofences(request, geofencePendingIntent)
        } catch (exception: SecurityException) {
            Log.e(
                TAG,
                "Geofence registration rejected (requestId=$requestId): " +
                    "permission denied; verify ACCESS_FINE_LOCATION and background-location access",
                exception
            )
            return null
        } catch (exception: IllegalArgumentException) {
            Log.e(
                TAG,
                "Invalid geofence registration request (requestId=$requestId, " +
                    "latitudeValid=${note.lat.isFinite() && note.lat in -90.0..90.0}, " +
                    "longitudeValid=${note.lng.isFinite() && note.lng in -180.0..180.0}, " +
                    "radiusM=${note.radiusM})",
                exception
            )
            return null
        }
    }

    fun remove(noteId: Long) {
        val requestId = noteId.toString()
        if (!hasFineLocationPermission("remove", requestId)) return

        try {
            geofencingClient.removeGeofences(listOf(requestId))
                .addOnSuccessListener {
                    Log.i(TAG, "Geofence removed (requestId=$requestId)")
                }
                .addOnFailureListener { exception ->
                    logFailure("remove", requestId, exception)
                }
        } catch (exception: SecurityException) {
            Log.e(
                TAG,
                "Geofence removal failed (requestId=$requestId, statusCode=unavailable): permission denied",
                exception
            )
        }
    }

    fun registerAll(notes: List<Note>) {
        notes.forEach(::register)
    }

    private fun hasFineLocationPermission(operation: String, requestId: String): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            Log.e(
                TAG,
                "Geofence $operation not attempted (requestId=$requestId): " +
                    "ACCESS_FINE_LOCATION is not granted"
            )
        } else {
            Log.d(TAG, "ACCESS_FINE_LOCATION granted for geofence $operation (requestId=$requestId)")
        }
        return granted
    }

    private fun logFailure(operation: String, requestId: String, exception: Exception) {
        val apiException = exception as? ApiException
        val status = apiException?.let {
            "statusCode=${it.statusCode}, " +
                "status=${GeofenceStatusCodes.getStatusCodeString(it.statusCode)}"
        } ?: "statusCode=unavailable"
        Log.e(
            TAG,
            "Geofence $operation failed (requestId=$requestId, $status)",
            exception
        )
    }

    private companion object {
        const val TAG = "DriftGeo"
        const val GEOFENCE_PENDING_INTENT_REQUEST_CODE = 0
    }
}
