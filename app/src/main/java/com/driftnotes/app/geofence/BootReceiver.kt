package com.driftnotes.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // TODO: Phase 3 - Member C - re-register all active notes via GeofenceManager.registerAll()
        Log.d("BootReceiver", "onReceive called")
    }
}
