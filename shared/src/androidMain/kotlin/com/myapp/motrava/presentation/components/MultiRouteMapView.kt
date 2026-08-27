package com.myapp.motrava.presentation.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
actual fun MultiRouteMapView(
    routes: List<List<RoutePoint>>,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f

    val density = context.resources.displayMetrics.density
    val mapView = remember {
        MapLibre.getInstance(context)
        val opts = org.maplibre.android.maps.MapLibreMapOptions
            .createFromAttributes(context)
            .pixelRatio(density)
        AndroidMapView(context, opts)
    }

    LaunchedEffect(routes) {
        if (routes.isNotEmpty()) {
            mapView.getMapAsync { map ->
                map.getStyle { style ->
                    drawMultiRoutes(map, style, routes)
                    
                    val boundsBuilder = LatLngBounds.Builder()
                    var hasPoints = false
                    routes.forEach { trip ->
                        trip.forEach { point ->
                            boundsBuilder.include(LatLng(point.latitude, point.longitude))
                            hasPoints = true
                        }
                    }
                    
                    if (hasPoints) {
                        mapView.post {
                            val w = mapView.width
                            val h = mapView.height
                            if (w > 0 && h > 0) {
                                val safePadX = kotlin.math.min((w * 0.35).toInt(), (w / 2) - 20)
                                val safePadY = kotlin.math.min(150, (h / 2) - 20)
                                
                                try {
                                    val bounds = boundsBuilder.build()
                                    val allPts = routes.flatten()
                                    val hasVariation = allPts.any { it.latitude != allPts.first().latitude || it.longitude != allPts.first().longitude }
                                    if (hasVariation) {
                                        map.easeCamera(CameraUpdateFactory.newLatLngBounds(bounds, safePadX, safePadY, safePadX, safePadY), 300)
                                    } else if (allPts.isNotEmpty()) {
                                        map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(allPts.first().latitude, allPts.first().longitude), 15.0), 300)
                                    }
                                } catch (e: Exception) {
                                    // ignore bounds builder error if points are too close
                                }
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
                if (routes.isNotEmpty()) {
                    drawMultiRoutes(map, style, routes)
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

private fun drawMultiRoutes(map: MapLibreMap, style: Style, routes: List<List<RoutePoint>>) {
    val features = mutableListOf<Feature>()
    
    routes.forEach { route ->
        val points = route.map { Point.fromLngLat(it.longitude, it.latitude) }
        if (points.isNotEmpty()) {
            if (points.size >= 2) {
                features.add(Feature.fromGeometry(LineString.fromLngLats(points)))
            } else {
                features.add(Feature.fromGeometry(points.first()))
            }
        }
    }

    val featureCollection = FeatureCollection.fromFeatures(features)
    val sourceId = "multi-route-source"
    val layerId = "multi-route-layer"

    if (style.getSource(sourceId) == null) {
        style.addSource(GeoJsonSource(sourceId, featureCollection))
    } else {
        (style.getSource(sourceId) as GeoJsonSource).setGeoJson(featureCollection)
    }

    if (style.getLayer(layerId) == null) {
        val lineLayer = LineLayer(layerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF9800")),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(lineLayer)
    }
}
