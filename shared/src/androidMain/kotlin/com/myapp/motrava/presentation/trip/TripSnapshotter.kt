package com.myapp.motrava.presentation.trip

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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

// Timeout for MapSnapshotter: if tiles fail to load from CDN, we don't want to hang forever.
private const val SNAPSHOT_TIMEOUT_MS = 20_000L

actual suspend fun getMapSnapshot(route: List<RoutePoint>, width: Int, height: Int, isDarkTheme: Boolean): ImageBitmap? {
    if (route.isEmpty()) return null
    return withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            try {
                val context = GlobalContext.get().get<Context>()

                val points = route.map { Point.fromLngLat(it.longitude, it.latitude) }
                val boundsBuilder = LatLngBounds.Builder()
                route.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }

                val rawBounds = boundsBuilder.build()
                val latSpan = kotlin.math.abs(rawBounds.latitudeNorth - rawBounds.latitudeSouth)
                val lonSpan = kotlin.math.abs(rawBounds.longitudeEast - rawBounds.longitudeWest)

                // 25% horizontal padding, 35% vertical padding to ensure no edge clipping
                val padLat = if (latSpan == 0.0) 0.015 else kotlin.math.max(latSpan * 0.35, 0.005)
                val padLon = if (lonSpan == 0.0) 0.015 else kotlin.math.max(lonSpan * 0.25, 0.005)

                // Shift slightly north so the route sits comfortably above the bottom stats card
                val centerShiftNorth = padLat * 0.15

                val paddedBounds = LatLngBounds.from(
                    rawBounds.latitudeNorth + padLat + centerShiftNorth,
                    rawBounds.longitudeEast + padLon,
                    rawBounds.latitudeSouth - padLat + centerShiftNorth,
                    rawBounds.longitudeWest - padLon
                )

                val styleUrl = if (isDarkTheme) {
                    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                } else {
                    "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
                }
                val styleBuilder = Style.Builder().fromUri(styleUrl)

                val density = context.resources.displayMetrics.density

                // Bake the route directly into the MapLibre style using GeoJsonSource
                if (points.size >= 2) {
                    val feature = Feature.fromGeometry(LineString.fromLngLats(points))
                    val geoJsonSource = GeoJsonSource("route-source", FeatureCollection.fromFeatures(arrayOf(feature)))
                    val innerLayer = LineLayer("route-inner", "route-source")
                        .withProperties(
                            PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF6D00")),
                            PropertyFactory.lineWidth(2.5f * density),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                        )
                    styleBuilder.withSource(geoJsonSource).withLayer(innerLayer)
                }

                val options = MapSnapshotter.Options(width, height)
                    .withRegion(paddedBounds)
                    .withStyleBuilder(styleBuilder)
                    .withPixelRatio(1f) // Fix: Use 1f instead of density to prevent OOM/Texture too large on 1080x1920 exports
                    .withLogo(false)

                Handler(Looper.getMainLooper()).post {
                    try {
                        org.maplibre.android.MapLibre.getInstance(context)
                        val snapshotter = object : MapSnapshotter(context, options) {
                            override fun addOverlay(mapSnapshot: MapSnapshot) {
                                // No-op to bypass MapLibre createScaledLogo NPE bug
                            }
                        }
                        snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                            override fun onSnapshotReady(snapshot: MapSnapshot) {
                                if (cont.isActive) {
                                    val copied = snapshot.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                    cont.resume(copied.asImageBitmap())
                                }
                            }
                        }, object : MapSnapshotter.ErrorHandler {
                            override fun onError(error: String) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                        cont.invokeOnCancellation {
                            snapshotter.cancel()
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}

actual suspend fun getMultiMapSnapshot(routes: List<List<RoutePoint>>, width: Int, height: Int, isDarkTheme: Boolean): ImageBitmap? {
    if (routes.isEmpty() || routes.all { it.isEmpty() }) return null
    return withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) {
        suspendCancellableCoroutine { cont ->
            try {
                val context = GlobalContext.get().get<Context>()

                val boundsBuilder = LatLngBounds.Builder()
                routes.forEach { route ->
                    if (route.isNotEmpty()) {
                        route.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                    }
                }

                val rawBounds = boundsBuilder.build()
                val latSpan = kotlin.math.abs(rawBounds.latitudeNorth - rawBounds.latitudeSouth)
                val lonSpan = kotlin.math.abs(rawBounds.longitudeEast - rawBounds.longitudeWest)

                // 25% horizontal padding, 35% vertical padding to ensure no edge clipping
                val padLat = if (latSpan == 0.0) 0.015 else kotlin.math.max(latSpan * 0.35, 0.005)
                val padLon = if (lonSpan == 0.0) 0.015 else kotlin.math.max(lonSpan * 0.25, 0.005)

                // Shift slightly north so the routes sit comfortably above the bottom stats card
                val centerShiftNorth = padLat * 0.15

                val paddedBounds = LatLngBounds.from(
                    rawBounds.latitudeNorth + padLat + centerShiftNorth,
                    rawBounds.longitudeEast + padLon,
                    rawBounds.latitudeSouth - padLat + centerShiftNorth,
                    rawBounds.longitudeWest - padLon
                )

                val styleUrl = if (isDarkTheme) {
                    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                } else {
                    "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
                }
                val styleBuilder = Style.Builder().fromUri(styleUrl)

                val density = context.resources.displayMetrics.density

                val features = mutableListOf<Feature>()
                routes.forEach { r ->
                    if (r.size >= 2) {
                        val points = r.map { Point.fromLngLat(it.longitude, it.latitude) }
                        features.add(Feature.fromGeometry(LineString.fromLngLats(points)))
                    }
                }
                if (features.isNotEmpty()) {
                    val geoJsonSource = GeoJsonSource("multi-route-source", FeatureCollection.fromFeatures(features))
                    val innerLayer = LineLayer("multi-route-inner", "multi-route-source")
                        .withProperties(
                            PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF6D00")),
                            PropertyFactory.lineWidth(2.5f * density),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                        )
                    styleBuilder.withSource(geoJsonSource).withLayer(innerLayer)
                }

                val options = MapSnapshotter.Options(width, height)
                    .withRegion(paddedBounds)
                    .withStyleBuilder(styleBuilder)
                    .withPixelRatio(1f) // Use 1f instead of density to prevent OOM/Texture too large
                    .withLogo(false)

                Handler(Looper.getMainLooper()).post {
                    try {
                        org.maplibre.android.MapLibre.getInstance(context)
                        val snapshotter = object : MapSnapshotter(context, options) {
                            override fun addOverlay(mapSnapshot: MapSnapshot) {
                                // No-op to bypass MapLibre createScaledLogo NPE bug
                            }
                        }
                        snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                            override fun onSnapshotReady(snapshot: MapSnapshot) {
                                if (cont.isActive) {
                                    val copied = snapshot.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                    cont.resume(copied.asImageBitmap())
                                }
                            }
                        }, object : MapSnapshotter.ErrorHandler {
                            override fun onError(error: String) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                        cont.invokeOnCancellation {
                            snapshotter.cancel()
                        }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}
