package com.myapp.motrava.presentation.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

@Composable
actual fun TrackingMapView(
    liveLatLng: Pair<Double, Double>?,
    currentRoute: List<Pair<Double, Double>>,
    centerTrigger: Int,
    hasLocationPermission: Boolean,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f
    
    var isPermissionGranted by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    // Check permission again when resuming
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isPermissionGranted = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                                      androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isPermissionGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true || 
                              permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    if (!isPermissionGranted) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }) {
                androidx.compose.material3.Text("Izinkan Akses Lokasi")
            }
        }
        return
    }

    val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
    val hasGyro = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR) != null || sm.getDefaultSensor(android.hardware.Sensor.TYPE_MAGNETIC_FIELD) != null
    val targetRenderMode = if (hasGyro) org.maplibre.android.location.modes.RenderMode.COMPASS else org.maplibre.android.location.modes.RenderMode.GPS

    val density = context.resources.displayMetrics.density
    val mapView = remember {
        org.maplibre.android.MapLibre.getInstance(context)
        val opts = org.maplibre.android.maps.MapLibreMapOptions
            .createFromAttributes(context)
            .pixelRatio(density)
        org.maplibre.android.maps.MapView(context, opts)
    }

    val mapStyleUrl = if (isDarkTheme) {
        "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
    } else {
        "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    }

    // 1. Lifecycle management FIRST - must happen before any map operations
    DisposableEffect(lifecycleOwner) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> mapView.onStart()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> mapView.onStop()
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
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

    // 2. Fetch user location immediately and move camera there
    LaunchedEffect(Unit) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val userLatLng = org.maplibre.android.geometry.LatLng(location.latitude, location.longitude)
                        mapView.getMapAsync { map ->
                            map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(userLatLng, 16.0))
                        }
                    }
                }
                .addOnFailureListener {
                    // Fallback
                    fusedClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            val userLatLng = org.maplibre.android.geometry.LatLng(loc.latitude, loc.longitude)
                            mapView.getMapAsync { map ->
                                map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(userLatLng, 16.0))
                            }
                        }
                    }
                }
        }
    }

    // 3. Load style and activate LocationComponent
    LaunchedEffect(isDarkTheme) {
        mapView.getMapAsync { map ->
            map.setStyle(mapStyleUrl) { style ->
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {

                    val locationComponent = map.locationComponent
                    val locationOptions = org.maplibre.android.location.LocationComponentOptions.builder(context)
                        .compassAnimationEnabled(true)
                        .accuracyAnimationEnabled(true)
                        .build()
                    val options = org.maplibre.android.location.LocationComponentActivationOptions.builder(context, style)
                        .locationComponentOptions(locationOptions)
                        .useDefaultLocationEngine(true)
                        .build()
                    locationComponent.activateLocationComponent(options)
                    
                    locationComponent.onStart()
                    locationComponent.isLocationComponentEnabled = true
                    locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING
                    locationComponent.zoomWhileTracking(16.0)
                    locationComponent.renderMode = targetRenderMode
                }
            }
        }
    }

    // 4. Follow live location updates from TripSessionManager
    val mapLocation = if (liveLatLng != null) {
        org.maplibre.android.geometry.LatLng(liveLatLng.first, liveLatLng.second)
    } else null

    LaunchedEffect(mapLocation) {
        if (mapLocation != null) {
            mapView.getMapAsync { map ->
                try {
                    map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLng(mapLocation), 1000)
                    if (map.locationComponent.isLocationComponentActivated && map.locationComponent.renderMode != targetRenderMode) {
                        map.locationComponent.renderMode = targetRenderMode
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // 5. Center on My Location button press
    LaunchedEffect(centerTrigger) {
        if (centerTrigger > 0) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            val targetLoc = org.maplibre.android.geometry.LatLng(loc.latitude, loc.longitude)
                            mapView.getMapAsync { map ->
                                map.easeCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(targetLoc, 16.0), 800)
                                try {
                                    map.locationComponent.cameraMode = org.maplibre.android.location.modes.CameraMode.TRACKING
                                    map.locationComponent.renderMode = targetRenderMode
                                } catch (_: Exception) {}
                            }
                        }
                    }
            }
        }
    }

    // 6. Draw route polyline
    LaunchedEffect(currentRoute) {
        mapView.getMapAsync { map ->
            map.style?.let { style ->
                val sourceId = "realtime-route-source"
                val layerId = "realtime-route-layer"

                if (currentRoute.size >= 2) {
                    val points = currentRoute.map { org.maplibre.geojson.Point.fromLngLat(it.second, it.first) }
                    val lineString = org.maplibre.geojson.LineString.fromLngLats(points)
                    val feature = org.maplibre.geojson.Feature.fromGeometry(lineString)
                    val featureCollection = org.maplibre.geojson.FeatureCollection.fromFeatures(arrayOf(feature))

                    if (style.getSource(sourceId) == null) {
                        style.addSource(org.maplibre.android.style.sources.GeoJsonSource(sourceId, featureCollection))
                    } else {
                        (style.getSource(sourceId) as org.maplibre.android.style.sources.GeoJsonSource).setGeoJson(featureCollection)
                    }

                    if (style.getLayer(layerId) == null) {
                        val lineLayer = org.maplibre.android.style.layers.LineLayer(layerId, sourceId).apply {
                            setProperties(
                                org.maplibre.android.style.layers.PropertyFactory.lineColor(android.graphics.Color.parseColor("#FF9800")),
                                org.maplibre.android.style.layers.PropertyFactory.lineWidth(4f),
                                org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND),
                                org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND)
                            )
                        }
                        if (style.getLayer("mapbox-location-background-layer") != null) {
                            style.addLayerBelow(lineLayer, "mapbox-location-background-layer")
                        } else {
                            style.addLayer(lineLayer)
                        }
                    }
                } else {
                    (style.getSource(sourceId) as? org.maplibre.android.style.sources.GeoJsonSource)?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyArray()))
                }
            }
        }
    }

    // 7. Render the map
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
