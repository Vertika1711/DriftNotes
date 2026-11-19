package com.driftnotes.app.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // TODO: Phase 3 - Member C - handle geofence ENTER transition and show notification
        Log.d("GeofenceReceiver", "onReceive called")
    }
}
