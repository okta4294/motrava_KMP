package com.myapp.motrava.presentation.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.myapp.motrava.presentation.components.MapView
import androidx.compose.ui.graphics.ImageBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    viewModel: TripDetailViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var showPosterEditor by remember { mutableStateOf(false) }
    var initialTransparent by remember { mutableStateOf(false) }
    var cachedSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var isWaitingForSnapshot by remember { mutableStateOf(false) }

    val tripForShare = (state as? TripDetailViewModel.TripDetailState.Success)?.trip

    if (showPosterEditor && tripForShare != null) {
        TripPosterEditorDialog(
            trip = tripForShare,
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
                    CircularProgressIndicator(color = AccentPeach)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Preparing high-quality map...", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    LaunchedEffect(tripId) {
        viewModel.fetchTripDetail(tripId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Detail", fontWeight = FontWeight.Bold) },
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
                    Text(
                        text = (state as TripDetailViewModel.TripDetailState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is TripDetailViewModel.TripDetailState.Success -> {
                    val trip = (state as TripDetailViewModel.TripDetailState.Success).trip
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Map showing the route
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
                                modifier = Modifier.fillMaxSize(),
                                onSnapshotAvailable = { bmp ->
                                    cachedSnapshot = bmp
                                    if (isWaitingForSnapshot) {
                                        isWaitingForSnapshot = false
                                        showPosterEditor = true
                                    }
                                }
                            )
                        }
                        
                        // Trip Stats
                        TripStatsCard(
                            trip = trip,
                            onOpenPosterEditor = { isTransparent ->
                                initialTransparent = isTransparent
                                if (trip.route.isNullOrEmpty()) {
                                    showPosterEditor = true
                                } else if (cachedSnapshot == null) {
                                    isWaitingForSnapshot = true
                                } else {
                                    showPosterEditor = true
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
fun TripStatsCard(trip: TripDetailData, onOpenPosterEditor: (Boolean) -> Unit, onDeleteTrip: () -> Unit) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ponytail: simple confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Trip") },
            text = { Text("Are you sure you want to delete this trip history?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDeleteTrip()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Vehicle: ${trip.vehicleName ?: "Unknown"}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(title = "Distance", value = "${trip.totalDistance?.let { "%.2f km".format(it / 1000) } ?: "0 km"}")
                StatItem(title = "Avg Speed", value = "${trip.averageSpeed?.let { "%.1f km/h".format(it) } ?: "0 km/h"}")
                StatItem(title = "Duration", value = trip.duration?.let { dur ->
                    val h = dur / 3600
                    val m = (dur % 3600) / 60
                    val s = dur % 60
                    if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
                } ?: "0s")
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = { onOpenPosterEditor(false) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPeach, contentColor = Color.White),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Route", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Trip History", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = AccentPeach
        )
    }
}

