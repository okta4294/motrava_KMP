package com.myapp.motrava.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.tooling.preview.Preview
import com.myapp.motrava.data.remote.dto.TripHistoryData
import com.myapp.motrava.data.remote.dto.VehicleData
import com.myapp.motrava.presentation.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onNavigateToTripDetail: (String) -> Unit,
    onNavigateToRecap: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel()
) {
    val dashboardState by viewModel.dashboardState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val urgentReminders by viewModel.urgentReminders.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.fetchDashboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.fetchDashboard()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (dashboardState) {
                is DashboardViewModel.DashboardState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GradientPurple)
                    }
                }
                is DashboardViewModel.DashboardState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = (dashboardState as DashboardViewModel.DashboardState.Error).message,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.fetchDashboard() },
                                colors = ButtonDefaults.buttonColors(containerColor = GradientPurple)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is DashboardViewModel.DashboardState.Success -> {
                    val state = dashboardState as DashboardViewModel.DashboardState.Success
                    val currentPage by viewModel.currentPage.collectAsState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshDashboard() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        DashboardContent(
                            state = state,
                            currentPage = currentPage,
                            onPageChange = { viewModel.onPageChanged(it) },
                            onNavigateToTripDetail = onNavigateToTripDetail,
                            onNavigateToRecap = onNavigateToRecap,
                            onServiceDone = { vehicleId, reminderId ->
                                viewModel.resetReminder(vehicleId, reminderId)
                            }
                        )
                    }
                }
            }

            // In-app urgent reminder banners (shown at top as overlay)
            if (urgentReminders.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    urgentReminders.forEach { reminder ->
                        ServiceReminderBanner(
                            reminder = reminder,
                            onDismiss = { viewModel.dismissUrgentReminder(reminder.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    state: DashboardViewModel.DashboardState.Success,
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    onNavigateToTripDetail: (String) -> Unit,
    onNavigateToRecap: () -> Unit,
    onServiceDone: (vehicleId: String, reminderId: String) -> Unit
) {
    val totalPages = state.totalPages

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Welcome Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Welcome Back!",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.userName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentPeach
                    )
                }
                
                IconButton(
                    onClick = onNavigateToRecap,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Monthly Recap",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Main Trip Summary Card with Gradient
        item {
            TripSummaryCard(completedTrips = state.completedTripsCount, totalDistanceKm = state.totalDistanceKm)
        }

        // Stats Grid
        item {
            StatsGrid(
                totalDistanceKm = state.totalDistanceKm,
                avgBbm = state.avgBbm
            )
        }

        // Service Reminders
        if (state.reminderProgressList.isNotEmpty()) {
            item {
                Text(
                    text = "Service Reminders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            items(state.reminderProgressList.size) { index ->
                ServiceProgressCard(
                    progressData = state.reminderProgressList[index],
                    onServiceDone = {
                        onServiceDone(
                            state.reminderProgressList[index].vehicleId,
                            state.reminderProgressList[index].id
                        )
                    }
                )
            }
        }

        // Recent Activity
        if (state.trips.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(state.trips.size) { index ->
                ActivityItem(
                    trip = state.trips[index],
                    vehicleMap = state.vehicleMap,
                    onClick = { onNavigateToTripDetail(state.trips[index].id) }
                )
            }

            if (state.trips.isNotEmpty()) {
                item {
                    // ponytail: pill-shaped pagination with ellipsis matching user design
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 4.dp),
                        color = Color.Transparent,
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onPageChange(currentPage - 1) },
                                enabled = currentPage > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowLeft,
                                    contentDescription = "Previous Page",
                                    tint = if (currentPage > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val pageTokens = remember(currentPage, totalPages) {
                                    val tokens = mutableListOf<Int>()
                                    if (totalPages <= 7) {
                                        for (i in 0 until totalPages) tokens.add(i)
                                    } else {
                                        tokens.add(0)
                                        if (currentPage <= 3) {
                                            for (i in 1..4) tokens.add(i)
                                            tokens.add(-1)
                                            tokens.add(totalPages - 1)
                                        } else if (currentPage >= totalPages - 4) {
                                            tokens.add(-1)
                                            for (i in (totalPages - 5) until totalPages) tokens.add(i)
                                        } else {
                                            tokens.add(-1)
                                            for (i in (currentPage - 1)..(currentPage + 1)) tokens.add(i)
                                            tokens.add(-1)
                                            tokens.add(totalPages - 1)
                                        }
                                    }
                                    tokens
                                }

                                for (token in pageTokens) {
                                    if (token == -1) {
                                        Text(
                                            text = "...",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                    } else {
                                        val isSelected = token == currentPage
                                        Surface(
                                            onClick = { onPageChange(token) },
                                            shape = CircleShape,
                                            color = if (isSelected) GradientPurple else Color.Transparent,
                                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${token + 1}",
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { onPageChange(currentPage + 1) },
                                enabled = currentPage < totalPages - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = "Next Page",
                                    tint = if (currentPage < totalPages - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No trips yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start a trip from the Tracking tab!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TripSummaryCard(completedTrips: Int, totalDistanceKm: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(GradientPurple, GradientPink)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            // Decorative map icon
            Icon(
                Icons.Default.Map,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )

            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Trip count
                Text(
                    text = "$completedTrips",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "completed trips",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Distance footer with separator
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "%.1f km".format(totalDistanceKm),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "total distance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun StatsGrid(totalDistanceKm: Double, avgBbm: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avg Fuel Card â€” vibrant peach
        VibrantStatCard(
            modifier = Modifier.weight(1f),
            title = "Avg Fuel",
            value = "%.1f".format(avgBbm),
            unit = "km/L",
            icon = Icons.Default.LocalGasStation,
            backgroundColor = AccentPeach,
            contentColor = Color(0xFF1A1A2E)
        )
        // Distance Card â€” vibrant peach
        VibrantStatCard(
            modifier = Modifier.weight(1f),
            title = "Distance",
            value = "%.1f".format(totalDistanceKm),
            unit = "km",
            icon = Icons.Default.Route,
            backgroundColor = AccentPeach,
            contentColor = Color(0xFF1A1A2E)
        )
    }
}

@Composable
fun VibrantStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .height(100.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ActivityItem(trip: TripHistoryData, vehicleMap: Map<String, VehicleData>, onClick: () -> Unit) {
    val distanceText = if (trip.totalDistance != null) {
        "%.1f km".format(trip.totalDistance / 1000.0)
    } else "—"
    
    val durationText = if (trip.duration != null) {
        val h = trip.duration / 3600
        val m = (trip.duration % 3600) / 60
        val s = trip.duration % 60
        if (h > 0) "${h}h ${m}m ${s}s" else if (m > 0) "${m}m ${s}s" else "${s}s"
    } else "—"
    
    val vehicle = trip.vehicleId?.let { vehicleMap[it] }
        ?: vehicleMap.values.find { it.vehicleName.equals(trip.vehicleName, ignoreCase = true) }
        
    val displayName = trip.vehicleName ?: vehicle?.vehicleName ?: "Unknown Vehicle"
    
    val fuelConsumedLiters = if (trip.totalDistance != null && vehicle?.avgBbm != null && vehicle.avgBbm > 0) {
        (trip.totalDistance / 1000.0) / vehicle.avgBbm
    } else null
    
    val formattedTime = trip.startTime?.let { 
        if (it.length >= 16) it.substring(0, 16).replace("T", " ") else it 
    } ?: "Unknown Time"
    
    val statusColor = when (trip.status) {
        "COMPLETED" -> ActivePink
        "ONGOING" -> AccentYellow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vehicle icon in dark circle
            Surface(
                color = AccentPeach.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (vehicle?.vehicleType?.uppercase()) {
                            "MOTORCYCLE" -> Icons.Default.DirectionsBike
                            "CAR" -> Icons.Default.DirectionsCar
                            else -> Icons.Default.DirectionsCar
                        },
                        contentDescription = null,
                        tint = AccentPeach,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Route,
                            contentDescription = "Distance",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = distanceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Duration",
                            modifier = Modifier.size(10.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    
                    if (fuelConsumedLiters != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = "Fuel",
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "%.1f L".format(fuelConsumedLiters),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Status icon
            Icon(
                imageVector = if (trip.status == "ONGOING") Icons.Default.HourglassBottom else Icons.Default.CheckCircle,
                contentDescription = trip.status,
                modifier = Modifier.size(22.dp),
                tint = statusColor
            )
        }
    }
}

@Composable
fun ServiceProgressCard(
    progressData: com.myapp.motrava.data.remote.dto.ServiceReminderProgressData,
    onServiceDone: () -> Unit
) {
    val progressRatio = (progressData.progressPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val isCritical = progressData.needsService || progressRatio >= 0.9f
    val isWarning = progressRatio in 0.7f..0.89f
    val remainingKm = (progressData.intervalKm - progressData.accumulatedKm).coerceAtLeast(0.0)
    
    val progressColor = when {
        isCritical -> MaterialTheme.colorScheme.error
        isWarning -> AccentPeach
        else -> GradientPurple
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = progressColor.copy(alpha = 0.15f),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = progressColor,
                            modifier = Modifier.padding(10.dp).size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = progressData.serviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = "${progressData.progressPercent.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = progressColor
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LinearProgressIndicator(
                progress = { progressRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (progressData.needsService) 
                               "Overdue by %.1f km".format(progressData.accumulatedKm - progressData.intervalKm)
                           else 
                               "%.1f km remaining".format(remainingKm),
                    style = MaterialTheme.typography.labelMedium,
                    color = progressColor,
                    fontWeight = if (isCritical) FontWeight.Bold else FontWeight.Normal
                )
                
                Text(
                    text = "Interval: ${progressData.intervalKm} km",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progressData.needsService) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onServiceDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GradientPurple
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Service Done?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardPreview() {
    MotravaTheme {
        DashboardScreen(onNavigateToTripDetail = {})
    }
}

@Composable
fun ServiceReminderBanner(
    reminder: com.myapp.motrava.data.remote.dto.ServiceReminderProgressData,
    onDismiss: () -> Unit
) {
    val isOverdue = reminder.needsService
    val bgColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer
                  else AccentPeach.copy(alpha = 0.15f)
    val contentColor = if (isOverdue) MaterialTheme.colorScheme.onErrorContainer
                       else AccentPeach
    val icon = if (isOverdue) Icons.Default.Build else Icons.Default.Schedule

    androidx.compose.animation.AnimatedVisibility(
        visible = true,
        enter = androidx.compose.animation.slideInVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutVertically() + androidx.compose.animation.fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = bgColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOverdue) "🚨 Service Overdue!" else "⚠️ Service Due Soon!",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = "${reminder.serviceName} • ${reminder.progressPercent.toInt()}% of ${reminder.intervalKm} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

