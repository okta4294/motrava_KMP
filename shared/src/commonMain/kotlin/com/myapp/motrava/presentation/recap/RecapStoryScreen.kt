package com.myapp.motrava.presentation.recap

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.myapp.motrava.data.remote.dto.RoutePoint
import com.myapp.motrava.presentation.components.MultiRouteMapView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f

    // Animations states
    var showStep1 by remember { mutableStateOf(false) }
    var showStep2 by remember { mutableStateOf(false) }
    var showStep3 by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isCardVisible by remember { mutableStateOf(true) }

    // Video Export states
    var isExportingVideo by remember { mutableStateOf(false) }
    var videoExportProgress by remember { mutableStateOf(0f) }
    var exportFeedbackMsg by remember { mutableStateOf<String?>(null) }

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

            // Route Animation Loop: chunk points smoothly to target ~30fps without UI flooding
            val totalPoints = allRoutes.sumOf { it.size }.coerceAtLeast(1)
            val totalAnimTicks = 300 // ~10 seconds at 33ms per tick
            val ptsPerTick = maxOf(1, (totalPoints + totalAnimTicks - 1) / totalAnimTicks)

            val currentAnim = mutableListOf<MutableList<RoutePoint>>()

            for (trip in allRoutes) {
                if (trip.isEmpty()) continue

                val currentTripList = mutableListOf<RoutePoint>()
                currentAnim.add(currentTripList)

                var i = 0
                while (i < trip.size) {
                    while (isPaused) {
                        delay(100)
                    }
                    val end = minOf(i + ptsPerTick, trip.size)
                    for (k in i until end) {
                        currentTripList.add(trip[k])
                    }
                    i = end
                    animatedRoutes = currentAnim.map { it.toList() }
                    delay(33L)
                }
            }
        }
    }

    // Video export dialog
    if (isExportingVideo) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF161922),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { videoExportProgress },
                        color = Color(0xFFFF6D00),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Preparing Video...", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${(videoExportProgress * 100).toInt()}%", color = Color(0xFFFF6D00), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .clickable { isPaused = !isPaused }
    ) {
        if (uiState is RecapUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFF6D00))
        } else if (uiState is RecapUiState.Error) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = (uiState as RecapUiState.Error).message,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.loadRecap(periodName, startDate, endDate) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6D00)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Retry", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else if (uiState is RecapUiState.Success) {
            val recap = (uiState as RecapUiState.Success).recap

            // 1. Map Background - Animated MultiRouteMapView
            MultiRouteMapView(
                routes = animatedRoutes,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Cinematic Vignette Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            // 3. Center Hero Glassmorphic Card (Compact & Toggleable)
            AnimatedVisibility(
                visible = isCardVisible,
                enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.94f),
                exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.94f),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF12141E).copy(alpha = 0.76f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                        shadowElevation = 16.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            // Period & Motivation Badge
                            AnimatedVisibility(
                                visible = showStep1,
                                enter = fadeIn(tween(800)) + slideInVertically(tween(800), initialOffsetY = { 20 })
                            ) {
                                Column {
                                    Surface(
                                        color = Color(0xFFFF6D00).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFF6D00).copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF6D00), modifier = Modifier.size(14.dp))
                                            Text(
                                                text = "UNSTOPPABLE RIDER",
                                                color = Color(0xFFFF6D00),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = recap.periodName,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Highlights of your greatest rides.",
                                        fontSize = 13.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Distance Highlight
                            AnimatedVisibility(
                                visible = showStep2,
                                enter = fadeIn(tween(800)) + slideInVertically(tween(800), initialOffsetY = { 20 })
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.Route, contentDescription = "Distance", tint = Color(0xFFFF6D00), modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "TOTAL DISTANCE",
                                            color = Color(0xFFFF6D00),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.5.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${"%.1f".format(recap.totalDistance / 1000)} km",
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = (-0.5).sp
                                    )
                                }
                            }

                            // Trips & Max Speed Dual Tiles
                            AnimatedVisibility(
                                visible = showStep3,
                                enter = fadeIn(tween(800)) + slideInVertically(tween(800), initialOffsetY = { 20 })
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Tile 1: Trips
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = Color.White.copy(alpha = 0.05f),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.TwoWheeler, contentDescription = "Trips", tint = Color.LightGray, modifier = Modifier.size(15.dp))
                                                    Text("TRIPS", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("${recap.totalTrips}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }

                                        // Tile 2: Max Speed
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = Color.White.copy(alpha = 0.05f),
                                            shape = RoundedCornerShape(14.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(Icons.Default.Speed, contentDescription = "Max Speed", tint = Color(0xFFFFD600), modifier = Modifier.size(15.dp))
                                                    Text("MAX SPEED", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("${"%.0f".format(recap.maxSpeed)} km/h", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Top Header & 3-Segment Instagram Story Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
            ) {
                // Segmented Progress Bar (3 steps)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val p1 by animateFloatAsState(if (showStep1) 1f else 0f, tween(1000, easing = FastOutSlowInEasing))
                    val p2 by animateFloatAsState(if (showStep2) 1f else 0f, tween(1000, easing = FastOutSlowInEasing))
                    val p3 by animateFloatAsState(if (showStep3) 1f else 0f, tween(1000, easing = FastOutSlowInEasing))

                    LinearProgressIndicator(progress = { p1 }, modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape), color = Color(0xFFFF6D00), trackColor = Color.White.copy(alpha = 0.25f))
                    LinearProgressIndicator(progress = { p2 }, modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape), color = Color(0xFFFF6D00), trackColor = Color.White.copy(alpha = 0.25f))
                    LinearProgressIndicator(progress = { p3 }, modifier = Modifier.weight(1f).height(3.dp).clip(CircleShape), color = Color(0xFFFF6D00), trackColor = Color.White.copy(alpha = 0.25f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Brand Pill, Pause State, and Actions (Toggle Card & Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPaused) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(14.dp))
                                Text("PAUSED", color = Color(0xFFFFD600), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }

                    // Brand Title
                    Text(
                        text = "MOTRAVA",
                        color = Color(0xFFFF6D00),
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 4.sp
                    )

                    // Right Actions: Toggle Card & Close Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { isCardVisible = !isCardVisible },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isCardVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isCardVisible) "Hide Info" else "Show Info",
                                tint = if (isCardVisible) Color.White.copy(alpha = 0.85f) else Color(0xFFFF6D00),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // 5. Bottom Story Controls & Export Action Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (exportFeedbackMsg != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF6D00).copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = exportFeedbackMsg!!,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Toggle Card Pill Button
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.clickable { isCardVisible = !isCardVisible }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isCardVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = Color(0xFFFF6D00),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isCardVisible) "View Full Map" else "Show Info",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Export Video Button
                    Button(
                        onClick = {
                            if (!isExportingVideo) {
                                isExportingVideo = true
                                videoExportProgress = 0f
                                coroutineScope.launch {
                                    val res = exportRecapVideo(recap, isDarkTheme) { p ->
                                        videoExportProgress = p
                                    }
                                    isExportingVideo = false
                                    exportFeedbackMsg = if (res != null) "Video saved to Gallery!" else "Export failed"
                                    delay(3000)
                                    exportFeedbackMsg = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6D00),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Video", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

