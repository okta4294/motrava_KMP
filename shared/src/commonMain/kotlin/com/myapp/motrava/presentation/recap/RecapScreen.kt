package com.myapp.motrava.presentation.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.myapp.motrava.presentation.trip.PosterData
import com.myapp.motrava.presentation.trip.PosterEditorDialog
import com.myapp.motrava.presentation.trip.getMultiMapSnapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.window.Dialog
import com.myapp.motrava.presentation.theme.AccentPeach

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStory: (String, String, String) -> Unit, // periodName, startDate, endDate
    viewModel: RecapViewModel = koinViewModel<RecapViewModel>()
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    var showPosterEditor by remember { mutableStateOf(false) }
    var cachedSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var isWaitingForSnapshot by remember { mutableStateOf(false) }
    var isExportingVideo by remember { mutableStateOf(false) }
    var videoExportProgress by remember { mutableStateOf(0f) }
    var videoExportResult by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = androidx.compose.material3.MaterialTheme.colorScheme.background.red < 0.5f
    

    // Default to current month
    val currentMoment = Clock.System.now()
    val currentDateTime = currentMoment.toLocalDateTime(TimeZone.currentSystemDefault())
    
    // Simple state for UI selection
    var selectedMonth by remember { mutableStateOf(currentDateTime.monthNumber) }
    var selectedYear by remember { mutableStateOf(currentDateTime.year) }
    
    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsState()
    var expandedVehicleDropdown by remember { mutableStateOf(false) }
    
    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    
    // Mode state
    var isYearlyMode by remember { mutableStateOf(false) }

    LaunchedEffect(selectedMonth, selectedYear, selectedVehicleId, isYearlyMode) {
        val startDate: String
        val endDate: String
        val periodName: String
        
        if (isYearlyMode) {
            startDate = "$selectedYear-01-01T00:00:00Z"
            endDate = "${selectedYear + 1}-01-01T00:00:00Z"
            periodName = "$selectedYear"
        } else {
            startDate = "$selectedYear-${selectedMonth.toString().padStart(2, '0')}-01T00:00:00Z"
            val nextMonth = if (selectedMonth == 12) 1 else selectedMonth + 1
            val nextYear = if (selectedMonth == 12) selectedYear + 1 else selectedYear
            endDate = "$nextYear-${nextMonth.toString().padStart(2, '0')}-01T00:00:00Z"
            periodName = "${monthNames[selectedMonth - 1]} $selectedYear"
        }
        
        // Reset cached snapshot when period or vehicle changes
        cachedSnapshot = null
        viewModel.loadRecap(periodName, startDate, endDate)
    }
    LaunchedEffect(isDarkTheme) {
        cachedSnapshot = null
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

    if (isExportingVideo) {
        Dialog(onDismissRequest = { }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { videoExportProgress },
                        color = AccentPeach,
                        modifier = androidx.compose.ui.Modifier.padding(8.dp)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Recap") },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = !isYearlyMode,
                    onClick = { isYearlyMode = false },
                    label = { Text("Monthly") }
                )
                Spacer(modifier = Modifier.width(16.dp))
                FilterChip(
                    selected = isYearlyMode,
                    onClick = { isYearlyMode = true },
                    label = { Text("Yearly") }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        
            // Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // simple dropdowns or buttons to change month/year
                Button(onClick = { 
                    if (isYearlyMode) {
                        selectedYear--
                    } else {
                        if (selectedMonth > 1) selectedMonth-- 
                        else { selectedMonth = 12; selectedYear-- }
                    }
                }) {
                    Text("< Prev")
                }
                
                Text(
                    text = if (isYearlyMode) "$selectedYear" else "${monthNames[selectedMonth - 1]} $selectedYear",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                
                Button(onClick = { 
                    if (isYearlyMode) {
                        selectedYear++
                    } else {
                        if (selectedMonth < 12) selectedMonth++ 
                        else { selectedMonth = 1; selectedYear++ }
                    }
                }) {
                    Text("Next >")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Vehicle Selector
            Box(modifier = Modifier.fillMaxWidth()) {
                val selectedVehicleName = vehicles.find { it.id == selectedVehicleId }?.vehicleName ?: "All Vehicles"
                OutlinedButton(
                    onClick = { expandedVehicleDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedVehicleName)
                }
                DropdownMenu(
                    expanded = expandedVehicleDropdown,
                    onDismissRequest = { expandedVehicleDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = { Text("All Vehicles") },
                        onClick = { 
                            viewModel.selectVehicle(null)
                            expandedVehicleDropdown = false
                        }
                    )
                    vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = { Text(vehicle.vehicleName) },
                            onClick = { 
                                viewModel.selectVehicle(vehicle.id)
                                expandedVehicleDropdown = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            when (val state = uiState) {
                is RecapUiState.Loading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Gathering your trips...")
                }
                is RecapUiState.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
                is RecapUiState.Success -> {
                    val recap = state.recap
                    
                    // Stats Card (Selalu Tampil)
                    Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("You completed", style = MaterialTheme.typography.bodyLarge)
                                Text("${recap.totalTrips} Trips", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                                Text("covering", style = MaterialTheme.typography.bodyLarge)
                                Text("${"%.1f".format(recap.totalDistance / 1000)} km", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Max Speed", style = MaterialTheme.typography.labelMedium)
                                        Text("${"%.1f".format(recap.maxSpeed)} km/h", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Avg Speed", style = MaterialTheme.typography.labelMedium)
                                        Text("${"%.1f".format(recap.averageSpeed)} km/h", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Action Buttons
                    if (recap.totalTrips > 0) {
                        // Primary: Play Animated Story
                        Button(
                            onClick = { 
                                val startDate = "$selectedYear-${selectedMonth.toString().padStart(2, '0')}-01T00:00:00Z"
                                val nextMonth = if (selectedMonth == 12) 1 else selectedMonth + 1
                                val nextYear = if (selectedMonth == 12) selectedYear + 1 else selectedYear
                                val endDate = "$nextYear-${nextMonth.toString().padStart(2, '0')}-01T00:00:00Z"
                                onNavigateToStory(recap.periodName, startDate, endDate)
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Animated Story", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Secondary row: Export Poster | Export as Video (side by side)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    if (cachedSnapshot == null) {
                                        isWaitingForSnapshot = true
                                        coroutineScope.launch {
                                            val snapshot = getMultiMapSnapshot(recap.routes, 1080, 1920, isDarkTheme = isDarkTheme)
                                            cachedSnapshot = snapshot
                                            isWaitingForSnapshot = false
                                            showPosterEditor = true
                                        }
                                    } else {
                                        showPosterEditor = true
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Poster", style = MaterialTheme.typography.bodyMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (!isExportingVideo) {
                                        isExportingVideo = true
                                        videoExportProgress = 0f
                                        videoExportResult = null
                                        coroutineScope.launch {
                                            val result = exportRecapVideo(recap, isDarkTheme) { p ->
                                                videoExportProgress = p
                                            }
                                            isExportingVideo = false
                                            videoExportResult = result
                                            val msg = if (result != null) "✅ Video saved to Gallery/Motrava" else "❌ Export failed"
                                            snackbarHostState.showSnackbar(msg)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Video", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("No Trips Found", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (showPosterEditor) {
                        val posterData = PosterData(
                            title = "MOTRAVA RECAP",
                            subtitle = recap.periodName,
                            stat1Label = "Total Trips",
                            stat1Value = recap.totalTrips.toString(),
                            stat2Label = "Avg Speed",
                            stat2Value = "${"%.1f".format(recap.averageSpeed)} km/h",
                            stat3Label = "Total Distance",
                            stat3Value = "${"%.1f".format(recap.totalDistance / 1000)} km",
                            multiRoutes = recap.routes
                        )
                        PosterEditorDialog(
                            posterData = posterData,
                            initialIsTransparentBg = false,
                            liveMapSnapshot = cachedSnapshot,
                            onDismiss = { showPosterEditor = false }
                        )
                    }
                }
                else -> {}
            }
        }
    }
}
