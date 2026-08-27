package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import com.myapp.motrava.data.remote.dto.RoutePoint
import kotlinx.cinterop.*
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import cocoapods.Mapbox.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MultiRouteMapView(
    routes: List<List<RoutePoint>>,
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
            if (routes.isNotEmpty()) {
                view.annotations?.let { view.removeAnnotations(it) }
                
                var firstPoint: RoutePoint? = null
                
                routes.forEach { route ->
                    if (route.size >= 2) {
                        memScoped {
                            val coords = allocArray<CLLocationCoordinate2D>(route.size)
                            route.forEachIndexed { index, point ->
                                coords[index].latitude = point.latitude
                                coords[index].longitude = point.longitude
                                if (firstPoint == null) firstPoint = point
                            }
                            val polyline = MGLPolyline.polylineWithCoordinates(coords, route.size.toULong())
                            view.addAnnotation(polyline)
                        }
                    }
                }
                
                if (firstPoint != null) {
                    // Zoom out a bit to fit multiple routes better since bounds computation in iOS requires more code
                    view.setCenterCoordinate(CLLocationCoordinate2DMake(firstPoint!!.latitude, firstPoint!!.longitude), 13.0, true)
                }
            }
        }
    )
}
