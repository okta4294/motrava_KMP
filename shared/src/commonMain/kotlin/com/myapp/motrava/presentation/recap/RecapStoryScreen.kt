package com.myapp.motrava.presentation.recap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.motrava.data.remote.dto.RoutePoint
import com.myapp.motrava.presentation.components.MultiRouteMapView
import kotlinx.coroutines.delay

@Composable
fun RecapStoryScreen(
    periodName: String,
    startDate: String,
    endDate: String,
    vehicleId: String?,
    viewModel: RecapViewModel,
    onClose: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Animations states
    var showStep1 by remember { mutableStateOf(false) }
    var showStep2 by remember { mutableStateOf(false) }
    var showStep3 by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    
    var animatedRoutes by remember { mutableStateOf<List<List<RoutePoint>>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (vehicleId != null) {
            viewModel.selectVehicle(vehicleId)
        }
        viewModel.loadRecap(periodName, startDate, endDate)
    }

    LaunchedEffect(uiState) {
        if (uiState is RecapUiState.Success) {
            val recap = (uiState as RecapUiState.Success).recap
            val allRoutes = recap.routes
            
            // Start Story Animation Steps
            showStep1 = true
            delay(1000)
            showStep2 = true
            delay(1000)
            showStep3 = true
            
            // Route Animation Loop
            val currentAnim = mutableListOf<List<RoutePoint>>()
            
            // Flatten animation somewhat: iterate each trip and add points
            for (trip in allRoutes) {
                if (trip.isEmpty()) continue
                
                val currentTripList = mutableListOf<RoutePoint>()
                currentAnim.add(currentTripList)
                
                // Animate points one by one
                for (point in trip) {
                    while (isPaused) {
                        delay(100)
                    }
                    currentTripList.add(point)
                    // Trigger recomposition with a new list instance
                    animatedRoutes = currentAnim.map { it.toList() }
                    
                    // Delay based on route length to make it fit within a reasonable time (~10-15 seconds total)
                    val frameDelay = maxOf(5L, 10000L / allRoutes.sumOf { it.size }.coerceAtLeast(1))
                    delay(frameDelay)
                }
            }
        } else if (uiState !is RecapUiState.Loading) {
            viewModel.loadRecap(periodName, startDate, endDate)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { isPaused = !isPaused }
    ) {
        if (uiState is RecapUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        } else if (uiState is RecapUiState.Success) {
            val recap = (uiState as RecapUiState.Success).recap
            
            // Map Background - Animated MultiRouteMapView
            MultiRouteMapView(
                routes = animatedRoutes,
                modifier = Modifier.fillMaxSize()
            )
            
            // Dark Gradient overlay
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

            // Foreground Elements
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = showStep1,
                    enter = fadeIn(tween(1000)) + slideInVertically(tween(1000), initialOffsetY = { 50 })
                ) {
                    Column {
                        Text(recap.periodName, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("You were unstoppable.", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                AnimatedVisibility(
                    visible = showStep2,
                    enter = fadeIn(tween(1000)) + slideInVertically(tween(1000), initialOffsetY = { 50 })
                ) {
                    Column {
                        Text("DISTANCE", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF6D00))
                        Text("${"%.1f".format(recap.totalDistance / 1000)} km", style = MaterialTheme.typography.displayMedium, color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                AnimatedVisibility(
                    visible = showStep3,
                    enter = fadeIn(tween(1000)) + slideInVertically(tween(1000), initialOffsetY = { 50 })
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("TRIPS", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Text("${recap.totalTrips}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        }
                        Column {
                            Text("MAX SPEED", style = MaterialTheme.typography.labelLarge, color = Color.Gray)
                            Text("${"%.0f".format(recap.maxSpeed)} km/h", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        }
                    }
                }
            }

            // Top Instructions and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPaused) "⏸ Paused" else "",
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelMedium
                )
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

