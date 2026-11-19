package com.driftnotes.app.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.driftnotes.app.R
import com.driftnotes.app.data.Note
import com.driftnotes.app.data.NoteRepository
import com.driftnotes.app.databinding.DialogAddNoteBinding
import com.driftnotes.app.databinding.FragmentMapBinding
import com.driftnotes.app.geofence.GeofenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var locationOverlay: MyLocationNewOverlay? = null
    private var temporaryMarker: Marker? = null
    private var radiusPolygon: Polygon? = null
    private val savedNoteMarkers = mutableMapOf<Long, Marker>()
    private var radiusMeters = DEFAULT_RADIUS_METERS.toDouble()

    private val defaultMapCenter = GeoPoint(51.5074, -0.1278)

    private companion object {
        const val TAG = "DriftNotesMap"
        const val GEOFENCE_TAG = "DriftGeo"
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 500
        const val RADIUS_STEP_METERS = 50
        const val DEFAULT_RADIUS_METERS = 150
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().userAgentValue = requireContext().packageName
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(17.0)
            controller.setCenter(defaultMapCenter)
        }

        binding.mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    placeOrMoveMarker(p)
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean = false
            })
        )

        binding.myLocationButton.setOnClickListener {
            locationOverlay?.myLocation?.let(binding.mapView.controller::animateTo)
        }

        binding.addNoteButton.setOnClickListener {
            if (temporaryMarker != null) showAddNoteDialog()
        }

        val noteRepository = NoteRepository.get(requireContext().applicationContext)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                noteRepository.observeAll().collect(::updateSavedNoteMarkers)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapView?.onResume()
        updateLocationOverlay()
    }

    private fun updateLocationOverlay() {
        val mapView = _binding?.mapView ?: return
        val hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasFineLocation && !hasCoarseLocation) {
            locationOverlay?.let {
                it.disableMyLocation()
                mapView.overlays.remove(it)
            }
            locationOverlay = null
            mapView.invalidate()
            return
        }

        val overlay = locationOverlay ?: run {
            val provider = GpsMyLocationProvider(requireContext()).apply {
                clearLocationSources()
                if (hasFineLocation) addLocationSource(LocationManager.GPS_PROVIDER)
                addLocationSource(LocationManager.NETWORK_PROVIDER)
                setLocationUpdateMinTime(1_000L)
                setLocationUpdateMinDistance(5f)
            }
            MyLocationNewOverlay(provider, mapView).also {
                locationOverlay = it
                mapView.overlays.add(it)
            }
        }
        if (!overlay.enableMyLocation()) {
            Log.w(TAG, "Unable to enable current-location updates")
            return
        }
        overlay.runOnFirstFix {
            val firstFix = overlay.myLocation
            if (
                firstFix != null &&
                firstFix.latitude.isFinite() &&
                firstFix.longitude.isFinite() &&
                firstFix.latitude in -90.0..90.0 &&
                firstFix.longitude in -180.0..180.0
            ) {
                mapView.post {
                    if (locationOverlay === overlay) {
                        mapView.controller.animateTo(firstFix)
                    }
                }
            }
        }
        mapView.invalidate()
    }

    private fun placeOrMoveMarker(position: GeoPoint) {
        val mapView = _binding?.mapView ?: return
        val marker = temporaryMarker

        if (marker == null) {
            val circle = createRadiusPolygon(mapView, position)
            val newMarker = Marker(mapView).apply {
                setPosition(position)
                setDraggable(true)
                setOnMarkerClickListener { _, _ -> false }
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(marker: Marker) {
                        updateRadiusPolygon(marker.position)
                    }

                    override fun onMarkerDragEnd(marker: Marker) {
                        updateRadiusPolygon(marker.position)
                    }

                    override fun onMarkerDragStart(marker: Marker) = Unit
                })
            }

            radiusPolygon = circle
            temporaryMarker = newMarker
            mapView.overlays.add(circle)
            mapView.overlays.add(newMarker)
            binding.addNoteButton.isEnabled = true
        } else {
            marker.setPosition(position)
            updateRadiusPolygon(position)
        }

        mapView.invalidate()
    }

    private fun createRadiusPolygon(mapView: MapView, center: GeoPoint): Polygon =
        Polygon(mapView).apply {
            setPoints(Polygon.pointsAsCircle(center, radiusMeters))
            fillPaint.color = Color.argb(55, 33, 150, 243)
            outlinePaint.color = Color.argb(190, 33, 150, 243)
            outlinePaint.strokeWidth = 3f
            setOnClickListener { _, _, _ -> false }
        }

    private fun updateRadiusPolygon(center: GeoPoint) {
        val mapView = _binding?.mapView ?: return
        radiusPolygon?.setPoints(Polygon.pointsAsCircle(center, radiusMeters))
        mapView.invalidate()
    }

    private fun updateSavedNoteMarkers(notes: List<Note>) {
        val mapView = _binding?.mapView ?: return
        val activeNotes = notes.filter { it.isActive }
        val activeNoteIds = activeNotes.mapTo(mutableSetOf()) { it.id }

        savedNoteMarkers.keys.toList()
            .filterNot(activeNoteIds::contains)
            .forEach { noteId ->
                savedNoteMarkers.remove(noteId)?.let(mapView.overlays::remove)
            }

        activeNotes.forEach { note ->
            val marker = savedNoteMarkers[note.id] ?: Marker(mapView).also {
                savedNoteMarkers[note.id] = it
                mapView.overlays.add(it)
            }
            marker.setPosition(GeoPoint(note.lat, note.lng))
            marker.setTitle(note.text)
        }
        mapView.invalidate()
    }

    private fun showAddNoteDialog() {
        val marker = temporaryMarker ?: return
        val initialRadius = radiusMeters
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)

        dialogBinding.radiusSeekBar.apply {
            max = (MAX_RADIUS_METERS - MIN_RADIUS_METERS) / RADIUS_STEP_METERS
            progress = ((initialRadius.toInt() - MIN_RADIUS_METERS) / RADIUS_STEP_METERS)
        }
        dialogBinding.radiusValue.text = getString(
            R.string.map_radius_format,
            radiusMeters.toInt()
        )
        dialogBinding.radiusSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    radiusMeters =
                        (MIN_RADIUS_METERS + progress * RADIUS_STEP_METERS).toDouble()
                    dialogBinding.radiusValue.text =
                        getString(R.string.map_radius_format, radiusMeters.toInt())
                    updateRadiusPolygon(marker.position)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.map_add_note_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.map_save, null)
            .setNegativeButton(R.string.map_cancel) { _, _ ->
                setRadius(initialRadius, marker)
            }
            .create()

        dialog.setOnCancelListener {
            setRadius(initialRadius, marker)
        }
        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            saveButton.setOnClickListener {
                val noteText = dialogBinding.noteText.text?.toString()?.trim().orEmpty()
                if (noteText.isEmpty()) {
                    dialogBinding.noteInputLayout.error =
                        getString(R.string.map_note_empty_error)
                    dialogBinding.noteText.requestFocus()
                    return@setOnClickListener
                }

                dialogBinding.noteInputLayout.error = null
                val position = marker.position
                val selectedRadiusMeters =
                    MIN_RADIUS_METERS + dialogBinding.radiusSeekBar.progress * RADIUS_STEP_METERS
                radiusMeters = selectedRadiusMeters.toDouble()
                updateRadiusPolygon(position)
                val note = Note(
                    text = noteText,
                    lat = position.latitude,
                    lng = position.longitude,
                    radiusM = selectedRadiusMeters.toFloat()
                )
                val appContext = requireContext().applicationContext

                saveButton.isEnabled = false
                cancelButton.isEnabled = false
                dialogBinding.noteText.isEnabled = false
                dialogBinding.radiusSeekBar.isEnabled = false
                dialog.setCancelable(false)

                viewLifecycleOwner.lifecycleScope.launch {
                    val id = try {
                        NoteRepository.get(appContext).add(note)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.e(TAG, "Failed to save note", exception)
                        Toast.makeText(
                            appContext,
                            R.string.map_note_save_failure,
                            Toast.LENGTH_SHORT
                        ).show()
                        saveButton.isEnabled = true
                        cancelButton.isEnabled = true
                        dialogBinding.noteText.isEnabled = true
                        dialogBinding.radiusSeekBar.isEnabled = true
                        dialog.setCancelable(true)
                        dialog.setCanceledOnTouchOutside(true)
                        return@launch
                    }

                    var messageId = R.string.map_note_save_success
                    try {
                        val hasBackgroundLocation =
                            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                                ContextCompat.checkSelfPermission(
                                    appContext,
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                        if (!hasBackgroundLocation) {
                            Log.w(
                                GEOFENCE_TAG,
                                "Background location permission is not granted " +
                                    "(noteId=$id); reminders may not trigger while the app is closed"
                            )
                        }
                        val registered = GeofenceManager(appContext)
                            .registerAndAwait(note.copy(id = id))
                        messageId = when {
                            !registered -> R.string.map_note_save_geofence_failure
                            !hasBackgroundLocation ->
                                R.string.map_note_save_background_location_warning
                            else -> R.string.map_note_save_success
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.e(
                            GEOFENCE_TAG,
                            "Failed to register geofence for saved note id=$id",
                            exception
                        )
                        messageId = R.string.map_note_save_geofence_failure
                    }

                    clearTemporaryPin()
                    Toast.makeText(appContext, messageId, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun setRadius(radius: Double, marker: Marker) {
        radiusMeters = radius
        updateRadiusPolygon(marker.position)
    }

    private fun clearTemporaryPin() {
        val mapView = _binding?.mapView ?: return
        temporaryMarker?.let { mapView.overlays.remove(it) }
        radiusPolygon?.let { mapView.overlays.remove(it) }
        temporaryMarker = null
        radiusPolygon = null
        radiusMeters = DEFAULT_RADIUS_METERS.toDouble()
        binding.addNoteButton.isEnabled = false
        mapView.invalidate()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

    override fun onPause() {
        locationOverlay?.disableMyLocation()
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        locationOverlay?.disableMyLocation()
        _binding?.mapView?.onDetach()
        locationOverlay = null
        temporaryMarker = null
        radiusPolygon = null
        savedNoteMarkers.clear()
        radiusMeters = DEFAULT_RADIUS_METERS.toDouble()
        _binding = null
        super.onDestroyView()
    }
}
