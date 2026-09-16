package com.myapp.motrava.presentation.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.myapp.motrava.presentation.theme.*
import com.myapp.motrava.presentation.trip.PosterData
import com.myapp.motrava.presentation.trip.PosterEditorDialog
import com.myapp.motrava.presentation.trip.getMultiMapSnapshot
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

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
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f

    // Default to current month
    val currentMoment = Clock.System.now()
    val currentDateTime = currentMoment.toLocalDateTime(TimeZone.currentSystemDefault())

    var selectedMonth by remember { mutableStateOf(currentDateTime.monthNumber) }
    var selectedYear by remember { mutableStateOf(currentDateTime.year) }

    val vehicles by viewModel.vehicles.collectAsState()
    val selectedVehicleId by viewModel.selectedVehicleId.collectAsState()
    var expandedVehicleDropdown by remember { mutableStateOf(false) }

    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Mode state: Monthly vs Yearly
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = GradientPurple, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        "Rendering Map Snapshot...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Preparing multi-route HD snapshot for your poster",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    if (isExportingVideo) {
        Dialog(onDismissRequest = { }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { videoExportProgress },
                        color = GradientPink,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        "Exporting Video...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(videoExportProgress * 100).toInt()}% rendered",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    val cardBg = if (isDarkTheme) CardDark else MaterialTheme.colorScheme.surface
    val subtleBorderColor = if (isDarkTheme) Color(0xFF383D4E) else Color(0xFFE2E8F0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Trip Recap",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ── 1. Modern Segmented Pill Toggle (Monthly vs Yearly) ──
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDarkTheme) CardDark.copy(alpha = 0.65f) else LightCardSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, subtleBorderColor, RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Monthly Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(13.dp))
                            .then(
                                if (!isYearlyMode) {
                                    Modifier.background(
                                        Brush.horizontalGradient(listOf(GradientPurple, GradientPink))
                                    )
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isYearlyMode = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Monthly Recap",
                            fontSize = 13.sp,
                            fontWeight = if (!isYearlyMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isYearlyMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Yearly Tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(13.dp))
                            .then(
                                if (isYearlyMode) {
                                    Modifier.background(
                                        Brush.horizontalGradient(listOf(GradientPurple, GradientPink))
                                    )
                                } else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isYearlyMode = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Yearly Recap",
                            fontSize = 13.sp,
                            fontWeight = if (isYearlyMode) FontWeight.Bold else FontWeight.Medium,
                            color = if (isYearlyMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── 2. Sleek Month & Year Navigator Bar ──
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                shadowElevation = if (isDarkTheme) 2.dp else 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, subtleBorderColor, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Prev Button
                    IconButton(
                        onClick = {
                            if (isYearlyMode) {
                                selectedYear--
                            } else {
                                if (selectedMonth > 1) selectedMonth--
                                else {
                                    selectedMonth = 12
                                    selectedYear--
                                }
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF222633) else Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Period",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Center Date Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(GradientPurple.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = GradientPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isYearlyMode) "$selectedYear" else "${monthNames[selectedMonth - 1]} $selectedYear",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Next Button
                    IconButton(
                        onClick = {
                            if (isYearlyMode) {
                                selectedYear++
                            } else {
                                if (selectedMonth < 12) selectedMonth++
                                else {
                                    selectedMonth = 1
                                    selectedYear++
                                }
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF222633) else Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Period",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── 3. Modern Vehicle Selector Card ──
            val selectedVehicle = vehicles.find { it.id == selectedVehicleId }
            val selectedVehicleName = selectedVehicle?.vehicleName ?: "All Vehicles"
            val vehiclePlate = selectedVehicle?.plateNumber

            Box(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, subtleBorderColor, RoundedCornerShape(16.dp))
                        .clickable { expandedVehicleDropdown = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Vehicle Icon Badge
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GradientPurple.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedVehicle?.vehicleType?.contains("car", ignoreCase = true) == true) {
                                    Icons.Default.DirectionsCar
                                } else {
                                    Icons.Default.TwoWheeler
                                },
                                contentDescription = null,
                                tint = GradientPurple,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "VEHICLE FILTER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.6.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = selectedVehicleName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!vehiclePlate.isNullOrBlank()) {
                                    Text(
                                        text = " • $vehiclePlate",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = expandedVehicleDropdown,
                    onDismissRequest = { expandedVehicleDropdown = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("All Vehicles (Combined)")
                            }
                        },
                        onClick = {
                            viewModel.selectVehicle(null)
                            expandedVehicleDropdown = false
                        }
                    )
                    vehicles.forEach { vehicle ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (vehicle.vehicleType.contains("car", ignoreCase = true)) {
                                            Icons.Default.DirectionsCar
                                        } else {
                                            Icons.Default.TwoWheeler
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(vehicle.vehicleName, fontWeight = FontWeight.Medium)
                                        if (vehicle.plateNumber.isNotBlank()) {
                                            Text(vehicle.plateNumber, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            },
                            onClick = {
                                viewModel.selectVehicle(vehicle.id)
                                expandedVehicleDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── 4. Main Content Area ──
            when (val state = uiState) {
                is RecapUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GradientPurple, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Gathering your journey highlights...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is RecapUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "Failed to load recap: ${state.message}",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                is RecapUiState.Success -> {
                    val recap = state.recap

                    if (recap.totalTrips > 0) {
                        // ── 4A. Hero Showcase Card (Spotify Wrapped / Strava Style) ──
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = cardBg,
                            shadowElevation = if (isDarkTheme) 4.dp else 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.2.dp, subtleBorderColor, RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp)
                            ) {
                                // Top Spotlight Badge
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                Brush.horizontalGradient(listOf(GradientPurple, GradientPink))
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✨ TRIP SPOTLIGHT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            letterSpacing = 0.8.sp
                                        )
                                    }

                                    Text(
                                        text = recap.periodName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                // Dual Hero Numbers: Distance & Completed Trips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Distance Block
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "TOTAL DISTANCE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.6.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = "%.1f".format(recap.totalDistance / 1000),
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.Black,
                                                color = GradientPurple,
                                                lineHeight = 38.sp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "KM",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                        }
                                    }

                                    // Vertical Divider
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(44.dp)
                                            .background(subtleBorderColor)
                                    )

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // Trips Block
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "COMPLETED TRIPS",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            letterSpacing = 0.6.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text(
                                                text = "${recap.totalTrips}",
                                                fontSize = 36.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (isDarkTheme) Color.White else TextDark,
                                                lineHeight = 38.sp
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (recap.totalTrips == 1) "TRIP" else "TRIPS",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(18.dp))
                                HorizontalDivider(color = subtleBorderColor.copy(alpha = 0.6f))
                                Spacer(modifier = Modifier.height(16.dp))

                                // 2x2 Automotive Stat Grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StatTile(
                                        icon = Icons.Default.Timer,
                                        iconTint = AccentPeach,
                                        label = "Driving Time",
                                        value = formatDuration(recap.totalDuration),
                                        modifier = Modifier.weight(1f),
                                        isDark = isDarkTheme
                                    )
                                    StatTile(
                                        icon = Icons.Default.Speed,
                                        iconTint = AccentYellow,
                                        label = "Average Speed",
                                        value = "${"%.1f".format(recap.averageSpeed)} km/h",
                                        modifier = Modifier.weight(1f),
                                        isDark = isDarkTheme
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StatTile(
                                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                                        iconTint = ActivePink,
                                        label = "Top Speed",
                                        value = "${"%.1f".format(recap.maxSpeed)} km/h",
                                        modifier = Modifier.weight(1f),
                                        isDark = isDarkTheme
                                    )
                                    StatTile(
                                        icon = Icons.Default.Route,
                                        iconTint = AccentGreen,
                                        label = "Routes Mapped",
                                        value = "${recap.routes.size} Routes",
                                        modifier = Modifier.weight(1f),
                                        isDark = isDarkTheme
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // ── 4B. Primary Hero Action: Play Animated Story ──
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    val startDate = "$selectedYear-${selectedMonth.toString().padStart(2, '0')}-01T00:00:00Z"
                                    val nextMonth = if (selectedMonth == 12) 1 else selectedMonth + 1
                                    val nextYear = if (selectedMonth == 12) selectedYear + 1 else selectedYear
                                    val endDate = "$nextYear-${nextMonth.toString().padStart(2, '0')}-01T00:00:00Z"
                                    onNavigateToStory(recap.periodName, startDate, endDate)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(listOf(GradientPurple, GradientPink))
                                    )
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Glowing Play Circle
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Play Animated Story",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Watch your monthly journey reel",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // ── 4C. Secondary Actions: Poster & Video Cards ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Poster Card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = cardBg,
                                shadowElevation = if (isDarkTheme) 2.dp else 1.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .border(1.dp, subtleBorderColor, RoundedCornerShape(18.dp))
                                    .clickable {
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
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(AccentPeach.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = AccentPeach,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Trip Poster",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Story image",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Video Card
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = cardBg,
                                shadowElevation = if (isDarkTheme) 2.dp else 1.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(72.dp)
                                    .border(1.dp, subtleBorderColor, RoundedCornerShape(18.dp))
                                    .clickable {
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
                                                val msg = if (result != null) "✅ Video saved to Gallery/Movies/Motrava" else "❌ Export failed"
                                                snackbarHostState.showSnackbar(msg)
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(AccentGreen.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = AccentGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Export Video",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "15s MP4 reel",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // ── 4D. Empty State: No Trips Found ──
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = cardBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, subtleBorderColor, RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(GradientPurple.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Route,
                                        contentDescription = null,
                                        tint = GradientPurple,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No Trips in ${recap.periodName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "You haven't recorded any trips during this period. Track trips on the Tracking tab or switch to another month to view your highlights!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
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

            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

/**
 * Modern Stat Tile with Icon Badge, Label, and Value
 */
@Composable
private fun StatTile(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    val tileBg = if (isDark) Color(0xFF222634) else Color(0xFFF8FAFC)
    val tileBorder = if (isDark) Color(0xFF333849) else Color(0xFFE2E8F0)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = tileBg,
        modifier = modifier.border(1.dp, tileBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Format raw seconds to human-readable driving duration (e.g., "1h 45m" or "32m")
 */
private fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0 min"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
