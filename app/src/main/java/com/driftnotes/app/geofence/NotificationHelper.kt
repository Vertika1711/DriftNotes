package com.driftnotes.app.geofence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.driftnotes.app.MainActivity
import com.driftnotes.app.R
import com.driftnotes.app.data.Note

object NotificationHelper {

    private const val TAG = "DriftGeo"
    private const val CHANNEL_ID = "driftnotes_reminders"
    private const val GENERIC_NOTIFICATION_ID = 0

    fun createChannel(context: Context) {
        val appContext = context.applicationContext
        val notificationManager =
            appContext.getSystemService(NotificationManager::class.java)

        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.geofence_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description =
                appContext.getString(R.string.geofence_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showNoteNotification(context: Context, note: Note?) {
        val appContext = context.applicationContext
        createChannel(appContext)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Notification not posted: POST_NOTIFICATIONS permission is not granted")
            return
        }

        val notificationManager = NotificationManagerCompat.from(appContext)
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notification not posted: notifications are disabled for this app")
            return
        }

        val notificationId = notificationIdFor(note)
        val body = note?.text
            ?: appContext.getString(R.string.geofence_notification_generic_body)
        val contentIntent = Intent(appContext, MainActivity::class.java).apply {
            putExtra(OPEN_TAB_EXTRA, NOTES_TAB)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_map)
            .setContentTitle(appContext.getString(R.string.geofence_notification_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (exception: SecurityException) {
            Log.e(TAG, "Failed to post notification (notificationId=$notificationId)", exception)
        }
    }

    private fun notificationIdFor(note: Note?): Int {
        val noteId = note?.id ?: return GENERIC_NOTIFICATION_ID
        return if (noteId in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            noteId.toInt()
        } else {
            noteId.hashCode()
        }
    }

    private const val OPEN_TAB_EXTRA = "open_tab"
    private const val NOTES_TAB = "notes"
}
