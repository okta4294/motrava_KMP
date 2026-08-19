package com.myapp.motrava.presentation.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myapp.motrava.data.remote.dto.TripDetailData
import com.myapp.motrava.presentation.theme.AccentPeach
import com.myapp.motrava.presentation.theme.GradientPurple
import com.myapp.motrava.utils.formatDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.*
import platform.UIKit.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun TripPosterEditorDialog(
    trip: TripDetailData,
    initialIsTransparentBg: Boolean,
    liveMapSnapshot: ImageBitmap?,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var stickerStyle by remember { mutableStateOf(0) } // 0: Float, 1: Card
    var stickerFormat by remember { mutableStateOf(0) } // 0: 3 Columns, 1: 2 Rows, 2: Stats, 3: Route, 4: Full Maps
    var isTransparentBg by remember { mutableStateOf(initialIsTransparentBg) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121318)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text(
                        text = if (isTransparentBg) "Export Transparent Sticker" else "Edit Trip Poster",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Button(
                        onClick = {
                            isSaving = true
                            val relX = if (previewSize.width > 0) offsetX / previewSize.width.toFloat() else 0f
                            val relY = if (previewSize.height > 0) offsetY / previewSize.height.toFloat() else 0f
                            coroutineScope.launch {
                                exportTripPosterIos(
                                    trip = trip,
                                    stickerStyle = stickerStyle,
                                    stickerFormat = stickerFormat,
                                    isTransparentBg = isTransparentBg,
                                    relX = relX,
                                    relY = relY,
                                    scale = scale
                                )
                                isSaving = false
                                onDismiss()
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = GradientPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // Interactive Preview Area (Center)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .aspectRatio(9f / 16f)
                            .fillMaxHeight(0.92f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (!isTransparentBg && stickerFormat == 4) Color(0xFFF0F2F5) else Color(0xFF1A1C24))
                            .onGloballyPositioned { previewSize = it.size }
                            .then(
                                if (stickerFormat != 4) Modifier.pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        offsetX += pan.x
                                        offsetY += pan.y
                                        scale = (scale * zoom).coerceIn(0.5f, 2.5f)
                                    }
                                } else Modifier
                            )
                    ) {
                        if (isTransparentBg) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0C0D12))
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(12.dp).align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "TRANSPARENT PNG",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = "Transparent Sticker Mode\nBackground will be 100% transparent when saved",
                                    textAlign = TextAlign.Center,
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                                )
                            }
                        } else if (stickerFormat == 4 && liveMapSnapshot != null) {
                            androidx.compose.foundation.Image(
                                bitmap = liveMapSnapshot,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                        startY = 400f
                                    )
                                )
                            )
                        } else {
                            Text(
                                text = "Touch & drag sticker to move\nPinch to zoom in/out",
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(24.dp)
                            )
                        }

                        // Draggable Sticker Overlay
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            IosTripStickerPreview(
                                trip = trip,
                                stickerStyle = stickerStyle,
                                stickerFormat = stickerFormat,
                                isLightMode = !isTransparentBg && stickerFormat == 4,
                                showManualRoute = stickerFormat != 4 || liveMapSnapshot == null,
                                modifier = Modifier
                                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                    .scale(scale)
                            )
                        }
                    }
                }

                // Bottom Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Sticker Format:", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = stickerFormat == 0,
                            onClick = { stickerFormat = 0 },
                            label = { Text("3 Columns + Route", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPeach, selectedLabelColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = stickerFormat == 1,
                            onClick = { stickerFormat = 1 },
                            label = { Text("2 Rows + Route", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPeach, selectedLabelColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = stickerFormat == 2,
                            onClick = { stickerFormat = 2 },
                            label = { Text("Stats Only", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPeach, selectedLabelColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = stickerFormat == 3,
                            onClick = { stickerFormat = 3 },
                            label = { Text("Route Only", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPeach, selectedLabelColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = stickerFormat == 4,
                            onClick = { stickerFormat = 4 },
                            label = { Text("Full Maps", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentPeach, selectedLabelColor = Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Box:", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                            FilterChip(
                                selected = stickerStyle == 0,
                                onClick = { stickerStyle = 0 },
                                label = { Text("Float", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GradientPurple, selectedLabelColor = Color.White)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = stickerStyle == 1,
                                onClick = { stickerStyle = 1 },
                                label = { Text("Card", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GradientPurple, selectedLabelColor = Color.White)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Background:", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(end = 4.dp))
                            FilterChip(
                                selected = !isTransparentBg,
                                onClick = { isTransparentBg = false },
                                label = { Text("Dark", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GradientPurple, selectedLabelColor = Color.White)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = isTransparentBg,
                                onClick = { isTransparentBg = true },
                                label = { Text("Transparent", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GradientPurple, selectedLabelColor = Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = { offsetX = 0f; offsetY = 0f; scale = 1f },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("Reset Sticker Position & Zoom", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun IosTripStickerPreview(
    trip: TripDetailData,
    stickerStyle: Int,
    stickerFormat: Int,
    isLightMode: Boolean = false,
    showManualRoute: Boolean = true,
    modifier: Modifier = Modifier
) {
    val distStr = trip.totalDistance?.let { "${(it / 1000.0).formatDecimal(2)} km" } ?: "0 km"
    val speedStr = trip.averageSpeed?.let { "${it.toDouble().formatDecimal(1)} km/h" } ?: "0 km/h"
    val durHour = (trip.duration ?: 0) / 3600
    val durMin = ((trip.duration ?: 0) % 3600) / 60
    val durSec = (trip.duration ?: 0) % 60
    val durStr = if (durHour > 0) "${durHour}h ${durMin}m ${durSec}s" else if (durMin > 0) "${durMin}m ${durSec}s" else "${durSec}s"

    val containerModifier = if (stickerStyle == 1) {
        modifier
            .width(270.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    } else {
        modifier
            .width(270.dp)
            .padding(8.dp)
    }

    if (stickerFormat == 4) {
        // Full Maps Style Preview
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(Color(0xFF1E212B))
                .clip(RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 400f
                    )
                )
            )
            Text(
                text = "M O T R A V A",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFF6D00),
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
            )
            val route = trip.route ?: emptyList()
            if (showManualRoute && route.size >= 2) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize().padding(bottom = 60.dp, top = 10.dp)
                ) {
                    val step = if (route.size > 300) route.size / 300 else 1
                    val simplified = route.filterIndexed { i, _ -> i % step == 0 || i == route.lastIndex }
                    var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
                    var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
                    for (p in simplified) {
                        if (p.latitude < minLat) minLat = p.latitude
                        if (p.latitude > maxLat) maxLat = p.latitude
                        if (p.longitude < minLon) minLon = p.longitude
                        if (p.longitude > maxLon) maxLon = p.longitude
                    }
                    val centerLat = (minLat + maxLat) / 2.0
                    val centerLon = (minLon + maxLon) / 2.0
                    val cosLat = kotlin.math.cos(centerLat * kotlin.math.PI / 180.0).toFloat()
                    val latSpan = (maxLat - minLat).toFloat()
                    val lonSpan = ((maxLon - minLon) * cosLat).toFloat()
                    val scaleFactor = if (latSpan == 0f || lonSpan == 0f) 1f else kotlin.math.min(size.width / lonSpan, size.height / latSpan) * 0.9f
                    val centerX = size.width / 2f; val centerY = size.height / 2f
                    
                    val path = androidx.compose.ui.graphics.Path()
                    simplified.forEachIndexed { idx, p ->
                        val x = centerX + ((p.longitude - centerLon).toFloat() * cosLat * scaleFactor)
                        val y = centerY - ((p.latitude - centerLat).toFloat() * scaleFactor)
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path = path, color = Color(0xFFFF6D00), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }

            // Full Maps Bottom Stats Overlay
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)
            ) {
                Text(text = (trip.vehicleName ?: "Outdoor activity").uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Column(modifier = Modifier.padding(end = 32.dp)) {
                        Text("Time", fontSize = 10.sp, color = Color(0xFFE0E5F5))
                        Text(durStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text("Avg Speed", fontSize = 10.sp, color = Color(0xFFE0E5F5))
                        Text(speedStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text("Distance", fontSize = 10.sp, color = Color(0xFFE0E5F5))
                    Text(distStr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    } else {
        Column(
            modifier = containerModifier,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (stickerFormat != 3) {
                Text(
                    text = "MOTRAVA ACTIVITY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE0E5F5),
                    letterSpacing = 1.sp
                )
                Text(
                    text = (trip.vehicleName ?: "MY RIDE").uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (stickerFormat == 1) {
                    // 2 Rows
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IosPreviewStatCol(label = "Distance", value = distStr)
                        IosPreviewStatCol(label = "Avg Speed", value = speedStr)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    IosPreviewStatCol(label = "Duration", value = durStr)
                } else {
                    // 3 Columns (Format 0 & 2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IosPreviewStatCol(label = "Distance", value = distStr)
                        IosPreviewStatCol(label = "Duration", value = durStr)
                        IosPreviewStatCol(label = "Avg Speed", value = speedStr)
                    }
                }
            }

            if (stickerFormat != 2) {
                if (stickerFormat != 3) Spacer(modifier = Modifier.height(10.dp))
                val route = trip.route ?: emptyList()
                if (showManualRoute && route.size >= 2) {
                    val polylineHeight = if (stickerFormat == 3) 140.dp else 85.dp
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(polylineHeight)
                    ) {
                        val step = if (route.size > 300) route.size / 300 else 1
                        val simplified = route.filterIndexed { i, _ -> i % step == 0 || i == route.lastIndex }
                        var minLat = Double.MAX_VALUE
                        var maxLat = -Double.MAX_VALUE
                        var minLon = Double.MAX_VALUE
                        var maxLon = -Double.MAX_VALUE
                        for (p in simplified) {
                            if (p.latitude < minLat) minLat = p.latitude
                            if (p.latitude > maxLat) maxLat = p.latitude
                            if (p.longitude < minLon) minLon = p.longitude
                            if (p.longitude > maxLon) maxLon = p.longitude
                        }
                        val centerLat = (minLat + maxLat) / 2.0
                        val centerLon = (minLon + maxLon) / 2.0
                        val cosLat = kotlin.math.cos(centerLat * kotlin.math.PI / 180.0).toFloat()
                        val latSpan = (maxLat - minLat).toFloat()
                        val lonSpan = ((maxLon - minLon) * cosLat).toFloat()
                        val scaleFactor = if (latSpan == 0f || lonSpan == 0f) 1f else {
                            kotlin.math.min(size.width / lonSpan, size.height / latSpan) * 0.85f
                        }
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f

                        val path = androidx.compose.ui.graphics.Path()
                        simplified.forEachIndexed { idx, p ->
                            val x = centerX + ((p.longitude - centerLon).toFloat() * cosLat * scaleFactor)
                            val y = centerY - ((p.latitude - centerLat).toFloat() * scaleFactor)
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = Color(0xFFFF6D00),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "M O T R A V A",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFFF6D00),
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun IosPreviewStatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = Color(0xFFE0E5F5))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun exportTripPosterIos(
    trip: TripDetailData,
    stickerStyle: Int,
    stickerFormat: Int,
    isTransparentBg: Boolean,
    relX: Float,
    relY: Float,
    scale: Float
) = withContext(Dispatchers.IO) {
    val width = 1080.0
    val height = 1920.0
    val size = CGSizeMake(width, height)
    
    platform.darwin.dispatch_sync(platform.darwin.dispatch_get_main_queue()) {
        UIGraphicsBeginImageContextWithOptions(size, !isTransparentBg, 1.0)
        val context = UIGraphicsGetCurrentContext()
        
        if (!isTransparentBg) {
            CGContextSetFillColorWithColor(context, UIColor(red = 0.08, green = 0.09, blue = 0.12, alpha = 1.0).CGColor)
            CGContextFillRect(context, CGRectMake(0.0, 0.0, width, height))
        }
        
        // Base center point adjusted for translation
        val centerX = (width / 2.0) + (relX * width)
        val centerY = (height / 2.0) + (relY * height)
        
        // Title
        val titleStr = "MOTRAVA TRIP" as platform.Foundation.NSString
        val titleAttrs = mapOf<Any?, Any?>(
            platform.UIKit.NSFontAttributeName to platform.UIKit.UIFont.boldSystemFontOfSize(72.0),
            platform.UIKit.NSForegroundColorAttributeName to platform.UIKit.UIColor.whiteColor
        )
        titleStr.drawAtPoint(CGPointMake(80.0, 160.0), titleAttrs)
        
        // Vehicle
        val vehicleStr = (trip.vehicleName?.uppercase() ?: "RIDE") as platform.Foundation.NSString
        val vehicleAttrs = mapOf<Any?, Any?>(
            platform.UIKit.NSFontAttributeName to platform.UIKit.UIFont.systemFontOfSize(56.0),
            platform.UIKit.NSForegroundColorAttributeName to platform.UIKit.UIColor(red = 1.0, green = 0.5, blue = 0.3, alpha = 1.0)
        )
        vehicleStr.drawAtPoint(CGPointMake(80.0, 260.0), vehicleAttrs)
        
        // Stats
        val dist = trip.totalDistance?.let { "${(it / 1000.0).formatDecimal(2)} km" } ?: "0 km"
        val spd = trip.averageSpeed?.let { "${it.toDouble().formatDecimal(1)} km/h" } ?: "0 km/h"
        
        val statStr = "Distance: $dist   Speed: $spd" as platform.Foundation.NSString
        val statAttrs = mapOf<Any?, Any?>(
            platform.UIKit.NSFontAttributeName to platform.UIKit.UIFont.systemFontOfSize(48.0),
            platform.UIKit.NSForegroundColorAttributeName to platform.UIKit.UIColor.lightGrayColor
        )
        statStr.drawAtPoint(CGPointMake(80.0, height - 200.0), statAttrs)
        
        // Draw Route
        val route = trip.route
        if (!route.isNullOrEmpty() && route.size > 1 && stickerFormat != 2) {
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            for (p in route) {
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
            
            val latRange = maxLat - minLat
            val lonRange = maxLon - minLon
            
            val pathBoxWidth = (width - 240.0) * scale.toDouble()
            val pathBoxHeight = (height - 900.0) * scale.toDouble()
            
            val cosLat = kotlin.math.cos(minLat * kotlin.math.PI / 180.0)
            val lonRangeAdjusted = lonRange * cosLat
            
            val scaleR = kotlin.math.min(pathBoxWidth / (lonRangeAdjusted + 0.0001), pathBoxHeight / (latRange + 0.0001))
            
            val xOffset = centerX - (lonRangeAdjusted * scaleR / 2.0)
            val yOffset = centerY - (latRange * scaleR / 2.0)
            
            CGContextSetStrokeColorWithColor(context, platform.UIKit.UIColor(red = 1.0, green = 0.43, blue = 0.0, alpha = 1.0).CGColor)
            CGContextSetLineWidth(context, (14.0 * scale).coerceIn(6.0, 32.0))
            CGContextSetLineCap(context, CGLineCap.kCGLineCapRound)
            CGContextSetLineJoin(context, CGLineJoin.kCGLineJoinRound)
            
            CGContextBeginPath(context)
            route.forEachIndexed { i, p ->
                val x = xOffset + ((p.longitude - minLon) * cosLat * scaleR)
                val y = yOffset + pathBoxHeight - ((p.latitude - minLat) * scaleR)
                if (i == 0) CGContextMoveToPoint(context, x, y)
                else CGContextAddLineToPoint(context, x, y)
            }
            CGContextStrokePath(context)
        }
        
        val resultImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        if (resultImage != null) {
            platform.UIKit.UIImageWriteToSavedPhotosAlbum(resultImage, null, null, null)
        }
    }
}
