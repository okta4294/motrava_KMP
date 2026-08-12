package com.myapp.motrava.presentation.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.myapp.motrava.data.remote.dto.RoutePoint
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView as AndroidMapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

@Composable
actual fun MapView(
    route: List<RoutePoint>,
    modifier: Modifier,
    onSnapshotAvailable: (ImageBitmap?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // For KMP, we can use a hardcoded dark theme check or pass it down, but for now we'll use a light theme map to keep it simple, or checking material3 theme
    val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f

    val density = context.resources.displayMetrics.density
    val mapView = remember {
        MapLibre.getInstance(context)
        val opts = org.maplibre.android.maps.MapLibreMapOptions
            .createFromAttributes(context)
            .pixelRatio(density)
        AndroidMapView(context, opts)
    }

    LaunchedEffect(route) {
        val scope = this
        if (route.isNotEmpty()) {
            mapView.getMapAsync { map ->
                map.getStyle { style ->
                    drawRoute(map, style, route)
                    
                    mapView.post {
                        val w = mapView.width
                        val h = mapView.height
                        if (w > 0 && h > 0) {
                            val boundsBuilder = LatLngBounds.Builder()
                            route.forEach { point ->
                                boundsBuilder.include(LatLng(point.latitude, point.longitude))
                            }
                            
                            val safePadX = kotlin.math.min((w * 0.35).toInt(), (w / 2) - 20)
                            val safePadY = kotlin.math.min(150, (h / 2) - 20)
                            
                            val cameraCallback = object : MapLibreMap.CancelableCallback {
                                override fun onCancel() { takeSnapshot() }
                                override fun onFinish() { takeSnapshot() }
                                
                                private fun takeSnapshot() {
                                    map.snapshot { snapshotBitmap ->
                                        onSnapshotAvailable(snapshotBitmap?.asImageBitmap())
                                    }
                                }
                            }
                            
                            if (route.size > 1) {
                                // Check if all points are identical
                                val first = route.first()
                                val hasVariation = route.any { it.latitude != first.latitude || it.longitude != first.longitude }
                                
                                try {
                                    if (hasVariation) {
                                        map.easeCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), safePadX, safePadY, safePadX, safePadY), 1000, cameraCallback)
                                    } else {
                                        map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15.0), 1000, cameraCallback)
                                    }
                                } catch (e: Exception) {
                                    map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 15.0), 1000, cameraCallback)
                                }
                            } else if (route.size == 1) {
                                map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(route[0].latitude, route[0].longitude), 15.0), 1000, cameraCallback)
                            }
                        }
                    }
                }
            }
        }
    }

    val mapStyleUrl = if (isDarkTheme) {
        "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    } else {
        "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    }
    
    LaunchedEffect(isDarkTheme) {
        mapView.getMapAsync { map ->
            map.setStyle(mapStyleUrl) { style ->
                if (route.isNotEmpty()) {
                    drawRoute(map, style, route)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

private fun drawRoute(map: MapLibreMap, style: Style, route: List<RoutePoint>) {
    val points = route.map { Point.fromLngLat(it.longitude, it.latitude) }
    if (points.isEmpty()) return

    val feature = if (points.size >= 2) {
        Feature.fromGeometry(LineString.fromLngLats(points))
    } else {
        Feature.fromGeometry(points.first())
    }
    
    val featureCollection = FeatureCollection.fromFeatures(arrayOf(feature))

    val sourceId = "route-source"
    val layerId = "route-layer"

    if (style.getSource(sourceId) == null) {
        style.addSource(GeoJsonSource(sourceId, featureCollection))
    } else {
        (style.getSource(sourceId) as GeoJsonSource).setGeoJson(featureCollection)
    }

    if (style.getLayer(layerId) == null) {
        if (points.size >= 2) {
            val lineLayer = LineLayer(layerId, sourceId).apply {
                setProperties(
                    PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF9800")),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            }
            style.addLayer(lineLayer)
        } else {
            val circleLayer = org.maplibre.android.style.layers.CircleLayer(layerId, sourceId).apply {
                setProperties(
                    PropertyFactory.circleColor(android.graphics.Color.parseColor("#FF9800")),
                    PropertyFactory.circleRadius(8f)
                )
            }
            style.addLayer(circleLayer)
        }
    }
}
