package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.*
import platform.Foundation.NSURL
import platform.CoreLocation.CLLocationManager
import cocoapods.Mapbox_iOS_SDK.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TrackingMapView(
    liveLatLng: Pair<Double, Double>?,
    currentRoute: List<Pair<Double, Double>>,
    centerTrigger: Int,
    hasLocationPermission: Boolean,
    modifier: Modifier
) {
    val locationManager = remember {
        CLLocationManager().apply {
            requestWhenInUseAuthorization()
        }
    }

    val mapView = remember {
        val view = MGLMapView()
        NSURL.URLWithString("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")?.let {
            view.styleURL = it
        }
        view.userTrackingMode = 1uL // MGLUserTrackingModeFollow
        view
    }

    LaunchedEffect(centerTrigger) {
        if (centerTrigger > 0) {
            mapView.setUserTrackingMode(1uL, animated = true)
        }
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.showsUserLocation = hasLocationPermission
            
            view.annotations?.let { view.removeAnnotations(it) }
            
            if (currentRoute.size >= 2) {
                memScoped {
                    val coords = allocArray<CLLocationCoordinate2D>(currentRoute.size)
                    currentRoute.forEachIndexed { index, point ->
                        coords[index].latitude = point.first
                        coords[index].longitude = point.second
                    }
                    val polyline = MGLPolyline.polylineWithCoordinates(coords, currentRoute.size.toULong())
                    view.addAnnotation(polyline)
                }
            }
            
            // On iOS, userTrackingMode handles following the live location automatically.
            // We only manually center if there is a route but no live location tracking (e.g. paused/recovered state without user tracking mode active)
            if (liveLatLng == null && currentRoute.isNotEmpty() && view.userTrackingMode == 0uL) {
                val last = currentRoute.last()
                val coord = cValue<CLLocationCoordinate2D> {
                    latitude = last.first
                    longitude = last.second
                }
                view.setCenterCoordinate(coord, 17.0, true)
            }
        }
    )
}
