package com.myapp.motrava.presentation.trip

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import com.myapp.motrava.data.remote.dto.RoutePoint
import kotlin.coroutines.resume

suspend fun getMapSnapshotTest(context: Context, route: List<RoutePoint>, width: Int, height: Int): Bitmap? = suspendCancellableCoroutine { cont ->
    try {
        val points = route.map { Point.fromLngLat(it.longitude, it.latitude) }
        val boundsBuilder = LatLngBounds.Builder()
        val first = route.firstOrNull()
        var hasVariation = false
        if (first != null) {
            route.forEach { 
                boundsBuilder.include(LatLng(it.latitude, it.longitude)) 
                if (it.latitude != first.latitude || it.longitude != first.longitude) {
                    hasVariation = true
                }
            }
        }
        
        val feature = if (points.size >= 2) {
            Feature.fromGeometry(LineString.fromLngLats(points))
        } else if (points.isNotEmpty()) {
            Feature.fromGeometry(points.first())
        } else {
            null
        }
        
        val featureCollection = feature?.let { FeatureCollection.fromFeatures(arrayOf(it)) } ?: FeatureCollection.fromFeatures(emptyArray())
        
        val styleBuilder = Style.Builder()
            .fromUri("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")
            .withSource(GeoJsonSource("route-source", featureCollection))
            
        if (points.size >= 2) {
            styleBuilder.withLayer(LineLayer("route-layer", "route-source").withProperties(
                PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF6D00")),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            ))
        } else if (points.isNotEmpty()) {
            styleBuilder.withLayer(org.maplibre.android.style.layers.CircleLayer("route-layer", "route-source").withProperties(
                PropertyFactory.circleColor(android.graphics.Color.parseColor("#FF6D00")),
                PropertyFactory.circleRadius(12f)
            ))
        }
            
        val density = context.resources.displayMetrics.density
        val options = MapSnapshotter.Options(width, height)
            .withStyleBuilder(styleBuilder)
            .withPixelRatio(density)
            
        if (hasVariation) {
            options.withRegion(boundsBuilder.build())
        } else if (first != null) {
            options.withCameraPosition(
                org.maplibre.android.camera.CameraPosition.Builder()
                    .target(LatLng(first.latitude, first.longitude))
                    .zoom(15.0)
                    .build()
            )
        }
            
        Handler(Looper.getMainLooper()).post {
            val snapshotter = MapSnapshotter(context, options)
            snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                override fun onSnapshotReady(snapshot: MapSnapshot) {
                    if (cont.isActive) cont.resume(snapshot.bitmap)
                }
            }, object : MapSnapshotter.ErrorHandler {
                override fun onError(error: String) {
                    if (cont.isActive) cont.resume(null)
                }
            })
            cont.invokeOnCancellation {
                snapshotter.cancel()
            }
        }
    } catch (e: Exception) {
        if (cont.isActive) cont.resume(null)
    }
}
