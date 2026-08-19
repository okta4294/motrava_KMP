package com.myapp.motrava.presentation.trip

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.context.GlobalContext
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

actual suspend fun getMapSnapshot(route: List<RoutePoint>, width: Int, height: Int): ImageBitmap? = suspendCancellableCoroutine { cont ->
    try {
        val context = GlobalContext.get().get<Context>()
        
        if (route.isEmpty()) {
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val points = route.map { Point.fromLngLat(it.longitude, it.latitude) }
        val boundsBuilder = LatLngBounds.Builder()
        route.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
        
        val lineString = LineString.fromLngLats(points)
        val feature = Feature.fromGeometry(lineString)
        val featureCollection = FeatureCollection.fromFeatures(arrayOf(feature))
        
        val styleBuilder = Style.Builder()
            .fromUri("https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json")
            .withSource(GeoJsonSource("route-source", featureCollection))
            .withLayer(LineLayer("route-layer", "route-source").withProperties(
                PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF6D00")),
                PropertyFactory.lineWidth(8f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            ))
            
        val density = context.resources.displayMetrics.density
        val options = MapSnapshotter.Options(width, height)
            .withRegion(boundsBuilder.build())
            .withStyleBuilder(styleBuilder)
            .withPixelRatio(density)
            
        Handler(Looper.getMainLooper()).post {
            val snapshotter = MapSnapshotter(context, options)
            snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                override fun onSnapshotReady(snapshot: MapSnapshot) {
                    if (cont.isActive) cont.resume(snapshot.bitmap.asImageBitmap())
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
