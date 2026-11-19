package com.driftnotes.app.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.driftnotes.app.R
import com.driftnotes.app.databinding.DialogAddNoteBinding
import com.driftnotes.app.databinding.FragmentMapBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var radiusMeters = DEFAULT_RADIUS_METERS.toDouble()

    private val defaultMapCenter = GeoPoint(51.5074, -0.1278)

    private companion object {
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

        updateLocationOverlay()
    }

    override fun onResume() {
        super.onResume()
        updateLocationOverlay()
        _binding?.mapView?.onResume()
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

        if (locationOverlay != null) return

        val provider = GpsMyLocationProvider(requireContext()).apply {
            clearLocationSources()
            if (hasFineLocation) addLocationSource(LocationManager.GPS_PROVIDER)
            addLocationSource(LocationManager.NETWORK_PROVIDER)
            setLocationUpdateMinTime(1_000L)
            setLocationUpdateMinDistance(5f)
        }
        val overlay = MyLocationNewOverlay(provider, mapView)
        locationOverlay = overlay
        mapView.overlays.add(overlay)
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
            .setPositiveButton(R.string.map_save) { _, _ ->
                Toast.makeText(
                    requireContext(),
                    R.string.map_note_saving_unavailable,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.map_cancel) { _, _ ->
                setRadius(initialRadius, marker)
            }
            .create()

        dialog.setOnCancelListener {
            setRadius(initialRadius, marker)
        }
        dialog.show()
    }

    private fun setRadius(radius: Double, marker: Marker) {
        radiusMeters = radius
        updateRadiusPolygon(marker.position)
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED

    override fun onPause() {
        _binding?.mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        _binding?.mapView?.onDetach()
        locationOverlay = null
        temporaryMarker = null
        radiusPolygon = null
        radiusMeters = DEFAULT_RADIUS_METERS.toDouble()
        _binding = null
        super.onDestroyView()
    }
}
