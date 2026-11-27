package com.myapp.motrava.presentation.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.myapp.motrava.data.remote.dto.RoutePoint
import com.myapp.motrava.data.remote.dto.TripDetailData
import com.myapp.motrava.presentation.theme.*
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.myapp.motrava.presentation.components.MapView
import androidx.compose.ui.graphics.ImageBitmap
import com.myapp.motrava.presentation.recap.exportRecapVideo
import com.myapp.motrava.domain.model.TripRecap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    viewModel: TripDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showPosterEditor by remember { mutableStateOf(false) }
    var initialTransparent by remember { mutableStateOf(false) }
    var cachedSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var isWaitingForSnapshot by remember { mutableStateOf(false) }

    var isExportingVideo by remember { mutableStateOf(false) }
    var videoExportProgress by remember { mutableStateOf(0f) }
    val snackbarHostState = remember { SnackbarHostState() }

    val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f
    val tripForShare = (state as? TripDetailViewModel.TripDetailState.Success)?.trip

    if (showPosterEditor && tripForShare != null) {
        val distStr = tripForShare.totalDistance?.let { "%.2f km".format(it / 1000) } ?: "0 km"
        val speedStr = tripForShare.averageSpeed?.let { "%.1f km/h".format(it) } ?: "0 km/h"
        val durHour = (tripForShare.duration ?: 0) / 3600
        val durMin = ((tripForShare.duration ?: 0) % 3600) / 60
        val durSec = (tripForShare.duration ?: 0) % 60
        val durStr = if (durHour > 0) "${durHour}h ${durMin}m ${durSec}s" else if (durMin > 0) "${durMin}m ${durSec}s" else "${durSec}s"

        val posterData = PosterData(
            title = "MOTRAVA ACTIVITY",
            subtitle = tripForShare.vehicleName ?: "MY RIDE",
            stat1Label = "Distance",
            stat1Value = distStr,
            stat2Label = "Avg Speed",
            stat2Value = speedStr,
            stat3Label = "Duration",
            stat3Value = durStr,
            route = tripForShare.route
        )

        PosterEditorDialog(
            posterData = posterData,
            initialIsTransparentBg = initialTransparent,
            liveMapSnapshot = cachedSnapshot,
            onDismiss = { showPosterEditor = false }
        )
    }

    if (isWaitingForSnapshot) {
        Dialog(onDismissRequest = { isWaitingForSnapshot = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Preparing high-quality map...", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (isExportingVideo) {
        Dialog(onDismissRequest = { }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { videoExportProgress },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Exporting Video...", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(videoExportProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    LaunchedEffect(tripId) {
        viewModel.fetchTripDetail(tripId)
    }

    LaunchedEffect(isDarkTheme) {
        cachedSnapshot = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Detail", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (state) {
                is TripDetailViewModel.TripDetailState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is TripDetailViewModel.TripDetailState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = (state as TripDetailViewModel.TripDetailState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(
                            onClick = { viewModel.fetchTripDetail(tripId) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
                is TripDetailViewModel.TripDetailState.Success -> {
                    val trip = (state as TripDetailViewModel.TripDetailState.Success).trip
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Map showing the route (live view only, snapshot taken on-demand)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            val displayRoute = trip.route.takeIf { !it.isNullOrEmpty() } ?: run {
                                if (trip.startLatitude != null && trip.startLongitude != null && trip.startLatitude != 0.0 && trip.startLongitude != 0.0) {
                                    listOf(RoutePoint(trip.startLatitude, trip.startLongitude, 0.0, 0.0, 0.0, 0.0, 100, ""))
                                } else {
                                    // Fallback to Indonesia center instead of Africa 0,0
                                    listOf(RoutePoint(-0.789275, 113.921327, 0.0, 0.0, 0.0, 0.0, 100, ""))
                                }
                            }
                            MapView(
                                route = displayRoute,
                                modifier = Modifier.fillMaxSize()
                                // onSnapshotAvailable removed: snapshot is now taken on-demand
                                // via getMapSnapshot() when Export button is tapped, giving
                                // reliable high-res map tiles instead of live view screenshot.
                            )
                        }
                        
                        // Trip Stats
                        TripStatsCard(
                            trip = trip,
                            onOpenPosterEditor = { isTransparent ->
                                initialTransparent = isTransparent
                                if (trip.route.isNullOrEmpty()) {
                                    // No route data: open editor without map background
                                    showPosterEditor = true
                                } else if (cachedSnapshot != null) {
                                    // Already have a cached high-res snapshot
                                    showPosterEditor = true
                                } else {
                                    // Take high-res snapshot using MapLibre offline snapshotter
                                    isWaitingForSnapshot = true
                                    coroutineScope.launch {
                                        cachedSnapshot = getMapSnapshot(
                                            route = trip.route ?: emptyList(),
                                            width = 1080,
                                            height = 1920,
                                            isDarkTheme = isDarkTheme
                                        )
                                        isWaitingForSnapshot = false
                                        showPosterEditor = true
                                    }
                                }
                            },
                            onExportVideo = {
                                if (!isExportingVideo) {
                                    isExportingVideo = true
                                    videoExportProgress = 0f
                                    val singleTripRecap = TripRecap(
                                        periodName = trip.vehicleName ?: "Activity Video",
                                        totalDistance = trip.totalDistance ?: 0.0,
                                        totalDuration = trip.duration ?: 0L,
                                        totalTrips = 1,
                                        maxSpeed = trip.maximumSpeed ?: 0.0,
                                        averageSpeed = trip.averageSpeed ?: 0.0,
                                        routes = listOf(trip.route ?: emptyList())
                                    )
                                    coroutineScope.launch {
                                        val result = exportRecapVideo(singleTripRecap, isDarkTheme) { p ->
                                            videoExportProgress = p
                                        }
                                        isExportingVideo = false
                                        val msg = if (result != null) "Video saved to Gallery/Motrava" else "Export failed"
                                        snackbarHostState.showSnackbar(msg)
                                    }
                                }
                            },
                            onDeleteTrip = { viewModel.deleteTrip(trip.id, onSuccess = onNavigateBack) }
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun TripStatsCard(trip: TripDetailData, onOpenPosterEditor: (Boolean) -> Unit, onExportVideo: () -> Unit, onDeleteTrip: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    // Confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Trip") },
            text = { Text("Are you sure you want to delete this trip history?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteTrip()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    val cardColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = cardColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Vehicle & Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = trip.vehicleName ?: "Vehicle",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!trip.plateNumber.isNullOrEmpty()) {
                            Text(
                                text = trip.plateNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Status pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "Completed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtle divider
            HorizontalDivider(color = borderColor, thickness = 1.dp)

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Grid (Distance, Avg Speed, Duration)
            val distStr = trip.totalDistance?.let { "%.2f km".format(it / 1000) } ?: "0 km"
            val speedStr = trip.averageSpeed?.let { "%.1f km/h".format(it) } ?: "0 km/h"
            val durStr = trip.duration?.let { dur ->
                val h = dur / 3600
                val m = (dur % 3600) / 60
                val s = dur % 60
                if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
            } ?: "0s"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Route,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = "Distance",
                    value = distStr,
                    isDark = isDark
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Speed,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "Avg Speed",
                    value = speedStr,
                    isDark = isDark
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "Duration",
                    value = durStr,
                    isDark = isDark
                )
            }

            if (trip.maximumSpeed != null && trip.maximumSpeed > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Top Recorded Speed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "%.1f km/h".format(trip.maximumSpeed),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: Poster & Video
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onOpenPosterEditor(false) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Poster", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { onExportVideo() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Video", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Delete action
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f))
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delete Trip Record", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MetricTile(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    isDark: Boolean = true
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}
