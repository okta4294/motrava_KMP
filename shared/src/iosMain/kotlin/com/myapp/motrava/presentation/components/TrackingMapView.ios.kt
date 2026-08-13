package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.*
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSURL
import cocoapods.Mapbox.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun TrackingMapView(
    liveLatLng: Pair<Double, Double>?,
    currentRoute: List<Pair<Double, Double>>,
    centerTrigger: Int,
    hasLocationPermission: Boolean,
    modifier: Modifier
) {
    val mapView = remember {
        val view = MGLMapView()
        view.styleURL = NSURL.URLWithString("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")
        view
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            view.showsUserLocation = hasLocationPermission
            
            view.annotations?.let { view.removeAnnotations(it) }
            
            if (currentRoute.isNotEmpty()) {
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
            
            if (liveLatLng != null) {
                view.setCenterCoordinate(CLLocationCoordinate2DMake(liveLatLng.first, liveLatLng.second), 17.0, true)
            } else if (currentRoute.isNotEmpty()) {
                val last = currentRoute.last()
                view.setCenterCoordinate(CLLocationCoordinate2DMake(last.first, last.second), 17.0, true)
            }
        }
    )
}
