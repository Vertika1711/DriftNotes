package com.driftnotes.app.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.driftnotes.app.databinding.FragmentMapBinding
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var locationOverlay: MyLocationNewOverlay? = null

    private val defaultMapCenter = GeoPoint(51.5074, -0.1278)

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

        binding.myLocationButton.setOnClickListener {
            locationOverlay?.myLocation?.let(binding.mapView.controller::animateTo)
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
        _binding = null
        super.onDestroyView()
    }
}
