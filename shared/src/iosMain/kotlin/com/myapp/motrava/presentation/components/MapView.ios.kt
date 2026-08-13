package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.interop.UIKitView
import com.myapp.motrava.data.remote.dto.RoutePoint
import kotlinx.cinterop.*
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import cocoapods.Mapbox.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MapView(
    route: List<RoutePoint>,
    modifier: Modifier,
    onSnapshotAvailable: (ImageBitmap?) -> Unit
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
            if (route.isNotEmpty()) {
                view.annotations?.let { view.removeAnnotations(it) }
                
                memScoped {
                    val coords = allocArray<CLLocationCoordinate2D>(route.size)
                    route.forEachIndexed { index, point ->
                        coords[index].latitude = point.latitude
                        coords[index].longitude = point.longitude
                    }
                    val polyline = MGLPolyline.polylineWithCoordinates(coords, route.size.toULong())
                    view.addAnnotation(polyline)
                }
                
                val first = route.first()
                view.setCenterCoordinate(CLLocationCoordinate2DMake(first.latitude, first.longitude), 15.0, true)
            }
        }
    )
}
