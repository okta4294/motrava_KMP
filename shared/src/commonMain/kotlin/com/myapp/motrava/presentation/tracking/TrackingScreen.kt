package com.myapp.motrava.presentation.tracking


import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.myapp.motrava.presentation.theme.*
import com.myapp.motrava.presentation.trip.TripViewModel
import com.myapp.motrava.presentation.vehicle.VehicleViewModel
import org.koin.compose.viewmodel.koinViewModel
import com.myapp.motrava.presentation.components.TrackingMapView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    onNavigateToAddVehicle: () -> Unit,
    tripViewModel: TripViewModel = koinViewModel(),
    vehicleViewModel: VehicleViewModel = koinViewModel()
) {
    val tripState by tripViewModel.tripState.collectAsState()
    val vehiclesState by vehicleViewModel.vehiclesState.collectAsState()
    val speedKmh by tripViewModel.tripSessionManager.speedKmh.collectAsState()
    val distanceMeters by tripViewModel.tripSessionManager.distanceMeters.collectAsState()

    var hasLocationPermission by remember { mutableStateOf(true) } // Assume true for now in commonMain
    var showTapWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Fetch vehicles when screen is opened
        vehicleViewModel.fetchVehicles()
    }

    val activeVehicleId by tripViewModel.tripSessionManager.activeVehicleId.collectAsState()
    val isTracking = tripState is TripViewModel.TripState.Ongoing || tripState is TripViewModel.TripState.Starting

    var selectedVehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Observe live GPS location from TripSessionManager
    val liveLatLng by tripViewModel.tripSessionManager.currentLatLng.collectAsState()
    val currentRoute by tripViewModel.tripSessionManager.currentRoute.collectAsState()
    
    var centerTrigger by remember { mutableStateOf(0) }
    
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f
    val highlightColor = if (isDarkTheme) AccentYellowBright else GradientPurple

    Box(modifier = Modifier.fillMaxSize()) {
        // MapLibre View
        Box(modifier = Modifier.fillMaxSize()) {
            TrackingMapView(
                liveLatLng = liveLatLng,
                currentRoute = currentRoute,
                centerTrigger = centerTrigger,
                hasLocationPermission = hasLocationPermission,
                modifier = Modifier.fillMaxSize()
            )
        }

        // My Location Button
        SmallFloatingActionButton(
            onClick = { centerTrigger++ },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            contentColor = highlightColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "My Location",
                modifier = Modifier.size(20.dp)
            )
        }

        // Top-Left Unified HUD Card (Speedometer + Distance + Live pulse)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live recording pulsing dot if tracking
                if (isTracking) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse_rec")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse_alpha"
                    )
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(ActivePink.copy(alpha = pulseAlpha))
                    )
                }

                // Speed Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Speed",
                        tint = highlightColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "%.0f".format(speedKmh),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = highlightColor
                        )
                        Text(
                            text = "km/h",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = highlightColor.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                }

                // Distance Section (divider + distance)
                if (isTracking) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = "Distance",
                            tint = GradientPurple,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "%.2f km".format(distanceMeters / 1000f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Bottom Floating Controls Panel (Stitch design)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 120.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
        ) {
            if (tripState is TripViewModel.TripState.Starting) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentPeach)
                    }
                }
            } else if (tripState is TripViewModel.TripState.Error) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Text(
                        text = (tripState as TripViewModel.TripState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (isTracking) {
                // While tracking: vehicle name badge + hold-to-stop button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val vehicles = (vehiclesState as? VehicleViewModel.VehiclesState.Success)?.vehicles
                    val targetVehicleId = selectedVehicleId ?: activeVehicleId
                    val vehicleName = vehicles?.find { it.id == targetVehicleId }?.vehicleName
                    if (vehicleName != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .border(1.dp, AccentGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TwoWheeler,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Driving: $vehicleName",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    HoldToStopButton(
                        onStop = { tripViewModel.endTrip() },
                        onTap = {
                            showTapWarning = true
                        }
                    )
                }
            } else {
                // Not tracking: unified control panel
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vehicle Selector
                        Box(modifier = Modifier.weight(1f)) {
                            when (vehiclesState) {
                                is VehicleViewModel.VehiclesState.Loading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp).padding(8.dp),
                                        color = AccentPeach,
                                        strokeWidth = 2.dp
                                    )
                                }
                                is VehicleViewModel.VehiclesState.Success -> {
                                    val vehicles = (vehiclesState as VehicleViewModel.VehiclesState.Success).vehicles
                                    if (vehicles.isEmpty()) {
                                        TextButton(onClick = onNavigateToAddVehicle) {
                                            Text("Add Vehicle", color = AccentPeach, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        if (selectedVehicleId == null) {
                                            selectedVehicleId = vehicles.firstOrNull { it.isDefault }?.id ?: vehicles.first().id
                                        }
                                        val selectedVehicle = vehicles.find { it.id == selectedVehicleId }

                                        ExposedDropdownMenuBox(
                                            expanded = expanded,
                                            onExpandedChange = { expanded = !expanded }
                                        ) {
                                            // Styled vehicle selector button with vehicle icon badge
                                            Surface(
                                                onClick = { expanded = true },
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .menuAnchor()
                                                    .fillMaxWidth()
                                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = highlightColor.copy(alpha = 0.15f),
                                                        modifier = Modifier.size(36.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                imageVector = Icons.Default.TwoWheeler,
                                                                contentDescription = null,
                                                                tint = highlightColor,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            "VEHICLE",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            letterSpacing = 1.sp
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = selectedVehicle?.vehicleName ?: "Select",
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 15.sp,
                                                            maxLines = 1
                                                        )
                                                    }
                                                    Icon(
                                                        Icons.Default.KeyboardArrowDown,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            ExposedDropdownMenu(
                                                expanded = expanded,
                                                onDismissRequest = { expanded = false }
                                            ) {
                                                vehicles.forEach { vehicle ->
                                                    DropdownMenuItem(
                                                        leadingIcon = {
                                                            Icon(
                                                                imageVector = Icons.Default.TwoWheeler,
                                                                contentDescription = null,
                                                                tint = highlightColor,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                        },
                                                        text = {
                                                            Column {
                                                                Text(vehicle.vehicleName, fontWeight = FontWeight.SemiBold)
                                                                if (!vehicle.plateNumber.isNullOrEmpty()) {
                                                                    Text(
                                                                        vehicle.plateNumber,
                                                                        style = MaterialTheme.typography.bodySmall,
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        onClick = {
                                                            selectedVehicleId = vehicle.id
                                                            expanded = false
                                                        }
                                                    )
                                                }
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    text = { Text("Add New Vehicle...", fontWeight = FontWeight.Medium) },
                                                    onClick = {
                                                        expanded = false
                                                        onNavigateToAddVehicle()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                                is VehicleViewModel.VehiclesState.Error -> {
                                    Text("Failed", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                                }
                                else -> {}
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Start button (circular peach)
                        val hasVehicles = (vehiclesState as? VehicleViewModel.VehiclesState.Success)?.vehicles?.isNotEmpty() == true
                        val targetId = selectedVehicleId ?: activeVehicleId
                        FloatingActionButton(
                            onClick = {
                                if (hasLocationPermission && targetId != null) {
                                    tripViewModel.startTrip(targetId)
                                }
                            },
                            containerColor = AccentPeach,
                            contentColor = Color.White,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp),
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start",
                                modifier = Modifier.size(30.dp),
                                tint = if (!hasVehicles || !hasLocationPermission) Color.White.copy(alpha = 0.4f) else Color.White
                            )
                        }
                    }
                }

                if (!hasLocationPermission) {
                    Text(
                        text = "Location Permission Required",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
        
        // Animated Warning Toast
        androidx.compose.animation.AnimatedVisibility(
            visible = showTapWarning,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { 50 }),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { 50 }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Press and hold the red button to stop",
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            
            LaunchedEffect(showTapWarning) {
                if (showTapWarning) {
                    kotlinx.coroutines.delay(2000)
                    showTapWarning = false
                }
            }
        }
    }
}

@Composable
fun HoldToStopButton(
    onStop: () -> Unit,
    onTap: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val progress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(durationMillis = if (isPressed) 1500 else 300, easing = LinearEasing),
        label = "stop_progress"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(1500)
            onStop()
            isPressed = false
        }
    }

    Box(
        modifier = Modifier.size(112.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            color = ActivePink,
            strokeWidth = 6.dp
        )
        Surface(
            shape = CircleShape,
            color = ActivePink,
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        },
                        onTap = {
                            onTap()
                        }
                    )
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

