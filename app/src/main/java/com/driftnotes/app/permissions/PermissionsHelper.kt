package com.driftnotes.app.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.driftnotes.app.R
import com.driftnotes.app.data.NoteRepository
import com.driftnotes.app.geofence.GeofenceManager
import kotlinx.coroutines.launch

class PermissionsHelper(private val activity: AppCompatActivity) {

    private var requestInProgress = false
    private var bannerDismissed = false
    private var locationDialogShown = false

    private val foregroundPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasForegroundLocation()) {
                requestNextPermission()
            } else {
                finishPermissionFlow()
            }
        }

    private val backgroundPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (hasBackgroundLocation()) {
                requestNextPermission()
            } else {
                finishPermissionFlow()
            }
        }

    private val backgroundSettingsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasBackgroundLocation()) {
                requestNextPermission()
            } else {
                finishPermissionFlow()
            }
        }

    private val notificationPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (hasNotificationPermission()) {
                requestNextPermission()
            } else {
                finishPermissionFlow()
            }
        }

    fun requestAll() {
        if (requestInProgress) return
        requestInProgress = true
        requestNextPermission()
    }

    fun onResume() {
        updatePermissionUi()
        if (hasAllRequiredPermissions()) {
            registerActiveNotes()
        }
        checkLocationServices()
    }

    private fun requestNextPermission() {
        when {
            !hasForegroundLocation() -> showExplanation(
                R.string.permission_foreground_explanation
            ) {
                foregroundPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation() -> {
                val explanation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    R.string.permission_background_android_11_explanation
                } else {
                    R.string.permission_background_explanation
                }
                showExplanation(explanation) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        backgroundSettingsLauncher.launch(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                    } else {
                        backgroundPermissionLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    }
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission() -> showExplanation(
                R.string.permission_notifications_explanation
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> finishPermissionFlow()
        }
    }

    private fun showExplanation(messageResId: Int, onContinue: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(messageResId)
            .setPositiveButton(R.string.permission_continue) { _, _ -> onContinue() }
            .setNegativeButton(R.string.permission_not_now) { _, _ -> finishPermissionFlow() }
            .setOnCancelListener { finishPermissionFlow() }
            .show()
    }

    private fun finishPermissionFlow() {
        requestInProgress = false
        updatePermissionUi()
        if (hasAllRequiredPermissions()) {
            registerActiveNotes()
        }
        checkLocationServices()
    }

    private fun hasForegroundLocation(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasBackgroundLocation(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(Manifest.permission.POST_NOTIFICATIONS)

    private fun hasAllRequiredPermissions(): Boolean =
        hasForegroundLocation() && hasBackgroundLocation() && hasNotificationPermission()

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun updatePermissionUi() {
        val banner = activity.findViewById<View>(R.id.permission_banner) ?: return
        val allGranted = hasAllRequiredPermissions()
        if (allGranted) bannerDismissed = false
        banner.visibility =
            if (!allGranted && !bannerDismissed) View.VISIBLE else View.GONE

        activity.findViewById<View>(R.id.permission_settings_button)?.setOnClickListener {
            activity.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                )
            )
        }
        activity.findViewById<View>(R.id.permission_dismiss_button)?.setOnClickListener {
            bannerDismissed = true
            banner.visibility = View.GONE
        }
    }

    private fun checkLocationServices() {
        val locationManager =
            activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (LocationManagerCompat.isLocationEnabled(locationManager)) {
            locationDialogShown = false
            return
        }
        if (locationDialogShown || activity.isFinishing) return

        locationDialogShown = true
        AlertDialog.Builder(activity)
            .setMessage(R.string.location_services_disabled)
            .setPositiveButton(R.string.location_services_settings) { _, _ ->
                locationDialogShown = false
                activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton(R.string.permission_not_now) { _, _ ->
                locationDialogShown = false
            }
            .setOnCancelListener { locationDialogShown = false }
            .show()
    }

    private fun registerActiveNotes() {
        activity.lifecycleScope.launch {
            GeofenceManager(activity).registerAll(NoteRepository.get(activity).getActive())
        }
    }
}
