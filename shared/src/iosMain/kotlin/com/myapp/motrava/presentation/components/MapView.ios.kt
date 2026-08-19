package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.interop.UIKitView
import com.myapp.motrava.data.remote.dto.RoutePoint
import kotlinx.cinterop.*
import platform.Foundation.NSURL
import cocoapods.Mapbox_iOS_SDK.*

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MapView(
    route: List<RoutePoint>,
    modifier: Modifier,
    onSnapshotAvailable: (ImageBitmap?) -> Unit
) {
    val mapView = remember {
        val view = MGLMapView()
        NSURL.URLWithString("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")?.let {
            view.styleURL = it
        }
        view
    }

    LaunchedEffect(route) {
        if (route.isNotEmpty()) {
            // iOS currently doesn't generate a snapshot directly here, unblock immediately
            onSnapshotAvailable(null)
        }
    }

    UIKitView(
        factory = { mapView },
        modifier = modifier,
        update = { view ->
            if (route.isNotEmpty()) {
                view.annotations?.let { view.removeAnnotations(it) }
                
                if (route.size >= 2) {
                    // Simplify route for iOS to prevent memScoped stack overflow and rendering issues
                    val step = if (route.size > 300) route.size / 300 else 1
                    val simplified = route.filterIndexed { i, _ -> i % step == 0 || i == route.lastIndex }
                    
                    memScoped {
                        val coords = allocArray<CLLocationCoordinate2D>(simplified.size)
                        simplified.forEachIndexed { index, point ->
                            coords[index].latitude = point.latitude
                            coords[index].longitude = point.longitude
                        }
                        val polyline = MGLPolyline.polylineWithCoordinates(coords, simplified.size.toULong())
                        view.addAnnotation(polyline)
                        
                        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
                        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
                        for (p in simplified) {
                            if (p.latitude < minLat) minLat = p.latitude
                            if (p.latitude > maxLat) maxLat = p.latitude
                            if (p.longitude < minLon) minLon = p.longitude
                            if (p.longitude > maxLon) maxLon = p.longitude
                        }
                        
                        if (minLat == maxLat || minLon == maxLon) {
                            val center = cValue<CLLocationCoordinate2D> { latitude = minLat; longitude = minLon }
                            view.setCenterCoordinate(center, 15.0, true)
                        } else {
                            val bounds = cValue<MGLCoordinateBounds> {
                                sw.latitude = minLat
                                sw.longitude = minLon
                                ne.latitude = maxLat
                                ne.longitude = maxLon
                            }
                            platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
                                val insets = platform.UIKit.UIEdgeInsetsMake(50.0, 50.0, 50.0, 50.0)
                                view.setVisibleCoordinateBounds(bounds, edgePadding = insets, animated = true)
                            }
                        }
                    }
                } else {
                    val first = route.first()
                    val coord = cValue<CLLocationCoordinate2D> {
                        latitude = first.latitude
                        longitude = first.longitude
                    }
                    view.setCenterCoordinate(coord, 15.0, true)
                }
            }
        }
    )
}
