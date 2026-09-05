package com.myapp.motrava.presentation.trip

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.os.Handler
import android.os.Looper
import com.myapp.motrava.data.remote.dto.RoutePoint

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun PosterEditorDialog(
    posterData: PosterData,
    initialIsTransparentBg: Boolean,
    liveMapSnapshot: ImageBitmap?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var stickerStyle by remember { mutableStateOf(0) } // 0: Transparan (Float), 1: Kartu Kapsul
    var stickerFormat by remember { mutableStateOf(0) } // 0: 3 Kolom + Rute, 1: 2 Baris + Rute, 2: Statistik Saja, 3: Rute Saja
    var isTransparentBg by remember { mutableStateOf(initialIsTransparentBg) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(1f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var mapSnapshotBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Map snapshot for Full Maps preview - disabled in preview to avoid crash
    // The snapshot is only used during actual export (Save button)

    var isSaving by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            isTransparentBg = false
        }
    }

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
                                exportEditedTripPoster(
                                    context = context,
                                    posterData = posterData,
                                    imageUri = selectedImageUri,
                                    stickerStyle = stickerStyle,
                                    stickerFormat = stickerFormat,
                                    isTransparentBg = isTransparentBg,
                                    liveMapSnapshot = liveMapSnapshot?.asAndroidBitmap(),
                                    relX = relX,
                                    relY = relY,
                                    scale = scale,
                                    onComplete = {
                                        isSaving = false
                                        onDismiss()
                                    }
                                )
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
                            .background(if (selectedImageUri == null && !isTransparentBg && stickerFormat == 4) Color(0xFFF0F2F5) else Color(0xFF1A1C24))
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
                        val bitmap by produceState<Bitmap?>(initialValue = null, selectedImageUri) {
                            value = if (selectedImageUri != null) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, selectedImageUri!!)) { decoder, _, _ ->
                                                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                                            }
                                        } else {
                                            @Suppress("DEPRECATION")
                                            MediaStore.Images.Media.getBitmap(context.contentResolver, selectedImageUri!!)
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bmp?.config == Bitmap.Config.HARDWARE) {
                                            bmp.copy(Bitmap.Config.ARGB_8888, true) ?: bmp
                                        } else bmp
                                    } catch (t: Throwable) { null }
                                }
                            } else null
                        }

                
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
                        } else if (bitmap != null) {
                            Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))

                        } else if (stickerFormat == 4 && liveMapSnapshot != null) {
                            Image(
                                bitmap = liveMapSnapshot,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                        startY = 700f
                                    )
                                )
                            )
                        } else {
                            Text(
                                text = "Touch & drag sticker to move\nPinch to zoom in/out\n\nSelect gallery photo below for background",
                                textAlign = TextAlign.Center,
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(24.dp)
                            )
                        }

                        // Draggable Sticker Overlay
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            TripStickerPreview(
                                posterData = posterData,
                                stickerStyle = stickerStyle,
                                stickerFormat = stickerFormat,
                                isLightMode = selectedImageUri == null && !isTransparentBg && stickerFormat == 4,
                                showManualRoute = true,
                                liveMapSnapshot = if (stickerFormat == 4) liveMapSnapshot?.asAndroidBitmap() else null,
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
                                label = { Text("Photo", fontSize = 10.sp) },
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

                    if (!isTransparentBg && stickerFormat != 4) {
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPeach, contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (selectedImageUri == null) "Select Photo from Gallery" else "Change Background Photo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

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
fun TripStickerPreview(
    posterData: PosterData,
    stickerStyle: Int,
    stickerFormat: Int,
    isLightMode: Boolean = false,
    showManualRoute: Boolean = true,
    liveMapSnapshot: android.graphics.Bitmap? = null,
    modifier: Modifier = Modifier
) {

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
        // Full Maps Style Preview - full 9:16 ratio
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(Color(0xFFF0F2F5))
                .clip(RoundedCornerShape(12.dp))
        ) {
            liveMapSnapshot?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Map Background",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Always draw gradient so white text is readable (even if snapshot fails/is loading)
            Box(
                modifier = Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 700f
                    )
                )
            )
            // MOTRAVA label - orange, top right
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
            
            // (Route is now drawn natively by MapLibre inside liveMapSnapshot, so we don't need manual Canvas drawing here)

            // Full Maps Bottom Stats Overlay
            Column(
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)
            ) {
                Text(text = posterData.subtitle.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    Column(modifier = Modifier.padding(end = 32.dp)) {
                        Text(posterData.stat3Label, fontSize = 10.sp, color = Color(0xFFE0E5F5))
                        Text(posterData.stat3Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column {
                        Text(posterData.stat2Label, fontSize = 10.sp, color = Color(0xFFE0E5F5))
                        Text(posterData.stat2Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Text(posterData.stat1Label, fontSize = 10.sp, color = Color(0xFFE0E5F5))
                    Text(posterData.stat1Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    text = posterData.title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE0E5F5),
                    letterSpacing = 1.sp
                )
                Text(
                    text = posterData.subtitle.uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (stickerFormat == 1) {
                    // 2 Baris / Piramida
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PreviewStatCol(isLightMode = isLightMode, label = posterData.stat1Label, value = posterData.stat1Value)
                        PreviewStatCol(isLightMode = isLightMode, label = posterData.stat2Label, value = posterData.stat2Value)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    PreviewStatCol(isLightMode = isLightMode, label = posterData.stat3Label, value = posterData.stat3Value)
                } else {
                    // 3 Kolom / Sejajar (Format 0 & 2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PreviewStatCol(isLightMode = isLightMode, label = posterData.stat1Label, value = posterData.stat1Value)
                        PreviewStatCol(isLightMode = isLightMode, label = posterData.stat3Label, value = posterData.stat3Value)
                        PreviewStatCol(isLightMode = isLightMode, label = posterData.stat2Label, value = posterData.stat2Value)
                    }
                }
            }

            if (stickerFormat != 2) {
                if (stickerFormat != 3) Spacer(modifier = Modifier.height(10.dp))
                if (showManualRoute) {
                    val polylineHeight = if (stickerFormat == 3) 140.dp else 85.dp
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(polylineHeight)
                    ) {
                        var minLat = Double.MAX_VALUE
                        var maxLat = -Double.MAX_VALUE
                        var minLon = Double.MAX_VALUE
                        var maxLon = -Double.MAX_VALUE
                        
                        val allRoutes = posterData.multiRoutes ?: (posterData.route?.let { listOf(it) } ?: emptyList())
                        
                        if (allRoutes.isNotEmpty()) {
                            for (r in allRoutes) {
                                for (p in r) {
                                    if (p.latitude < minLat) minLat = p.latitude
                                    if (p.latitude > maxLat) maxLat = p.latitude
                                    if (p.longitude < minLon) minLon = p.longitude
                                    if (p.longitude > maxLon) maxLon = p.longitude
                                }
                            }
                            
                            val latSpan = maxLat - minLat
                            val lonSpan = maxLon - minLon
                            val scaleFactor = if (latSpan == 0.0 || lonSpan == 0.0) 1f else {
                                kotlin.math.min(size.width / lonSpan, size.height / latSpan).toFloat() * 0.85f
                            }
                            val centerLat = (minLat + maxLat) / 2.0
                            val centerLon = (minLon + maxLon) / 2.0
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f

                            for (r in allRoutes) {
                                if (r.size >= 2) {
                                    val path = androidx.compose.ui.graphics.Path()
                                    r.forEachIndexed { idx, p ->
                                        val x = centerX + ((p.longitude - centerLon) * scaleFactor).toFloat()
                                        val y = centerY - ((p.latitude - centerLat) * scaleFactor).toFloat()
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
private fun PreviewStatCol(label: String, value: String, isLightMode: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 10.sp, color = if (isLightMode) Color(0xFF5A6278) else Color(0xFFE0E5F5))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isLightMode) Color(0xFF1A1C24) else Color.White)
    }
}

private suspend fun exportEditedTripPoster(
    context: Context,
    posterData: PosterData,
    imageUri: Uri?,
    stickerStyle: Int,
    stickerFormat: Int,
    isTransparentBg: Boolean,
    liveMapSnapshot: android.graphics.Bitmap?,
    relX: Float,
    relY: Float,
    scale: Float,
    onComplete: () -> Unit
) = withContext(Dispatchers.IO) {
    val isLightMode = imageUri == null && !isTransparentBg && stickerFormat == 4
    try {
        // ponytail: Full HD resolution (1080x1920) for standard file size, keeping vector rendering crisp
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)


        if (isTransparentBg) {
            canvas.drawColor(android.graphics.Color.TRANSPARENT)
        } else if (imageUri != null) {
            try {
                val srcBmpRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, imageUri)) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                }
                val srcBmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && srcBmpRaw?.config == Bitmap.Config.HARDWARE) {
                    srcBmpRaw.copy(Bitmap.Config.ARGB_8888, true) ?: srcBmpRaw
                } else {
                    srcBmpRaw
                }
                if (srcBmp != null) {
                    val srcRatio = srcBmp.width.toFloat() / srcBmp.height.toFloat()
                    val dstRatio = 1080f / 1920f
                    val scaleFactor = if (srcRatio > dstRatio) 1920f / srcBmp.height.toFloat() else 1080f / srcBmp.width.toFloat()
                    val scaledW = (srcBmp.width * scaleFactor).roundToInt()
                    val scaledH = (srcBmp.height * scaleFactor).roundToInt()
                    val scaledBmp = Bitmap.createScaledBitmap(srcBmp, scaledW, scaledH, true)
                    val left = (1080 - scaledW) / 2f
                    val top = (1920 - scaledH) / 2f
                    canvas.drawBitmap(scaledBmp, left, top, null)
                } else {
                    canvas.drawColor(if (isLightMode) android.graphics.Color.parseColor("#F0F2F5") else android.graphics.Color.parseColor("#1A1C24"))
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                canvas.drawColor(if (isLightMode) android.graphics.Color.parseColor("#F0F2F5") else android.graphics.Color.parseColor("#1A1C24"))
            }
        } else if (liveMapSnapshot != null && stickerFormat == 4 && imageUri == null && !isTransparentBg) {
            // Full Maps: draw real map as background with ContentScale.Crop logic (to prevent stretching)
            val srcRatio = liveMapSnapshot.width.toFloat() / liveMapSnapshot.height.toFloat()
            val dstRatio = 1080f / 1920f
            val scaleFactor = if (srcRatio > dstRatio) 1920f / liveMapSnapshot.height.toFloat() else 1080f / liveMapSnapshot.width.toFloat()
            val scaledW = kotlin.math.round(liveMapSnapshot.width * scaleFactor).toInt()
            val scaledH = kotlin.math.round(liveMapSnapshot.height * scaleFactor).toInt()
            val scaledBmp = android.graphics.Bitmap.createScaledBitmap(liveMapSnapshot, scaledW, scaledH, true)
            val left = (1080 - scaledW) / 2f
            val top = (1920 - scaledH) / 2f
            canvas.drawBitmap(scaledBmp, left, top, null)
            
            // Add dark gradient at the bottom so white text is readable on light maps
            val gradientPaint = Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 1920f * 0.65f, 0f, 1920f,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.parseColor("#D9000000"), // 85% black
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, 1080f, 1920f, gradientPaint)
        } else {
            canvas.drawColor(if (isLightMode) android.graphics.Color.parseColor("#F0F2F5") else android.graphics.Color.parseColor("#1A1C24"))
            if (stickerFormat == 4 && !isTransparentBg) {
                // Add gradient for fallback background as well
                val gradientPaint = Paint().apply {
                    shader = android.graphics.LinearGradient(0f, 1920f * 0.65f, 0f, 1920f, android.graphics.Color.TRANSPARENT, android.graphics.Color.parseColor("#D9000000"), android.graphics.Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, 1080f, 1920f, gradientPaint)
            }
        }

        if (!isTransparentBg && !isLightMode) {
            canvas.drawColor(android.graphics.Color.parseColor("#33000000"))
        }

        // 2. Draw Sticker at Custom Position & Scale (Standard 1x scale for Full HD canvas)
        val centerX = 540f + (relX * 1080f)
        val centerY = 960f + (relY * 1920f)
        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)
        val isFullMapBackground = liveMapSnapshot != null && stickerFormat == 4 && imageUri == null && !isTransparentBg
        drawTripStickerOnCanvas(canvas, posterData, stickerStyle, stickerFormat, isLightMode, true)
        canvas.restore()

        // 3. Save to MediaStore
        val filename = "motrava_poster_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Motrava")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Image successfully saved to Gallery (Pictures/Motrava)", Toast.LENGTH_LONG).show()
                onComplete()
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                onComplete()
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "An error occurred: ${t.message}", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }
}

private fun drawTripStickerOnCanvas(canvas: Canvas, posterData: PosterData, stickerStyle: Int, stickerFormat: Int, isLightMode: Boolean = false, showManualRoute: Boolean = true) {
    val semiBoldTypeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 600, false)
    } else {
        android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
    }

    if (stickerStyle == 1) {
        val cardRect = if (stickerFormat == 3) RectF(-360f, -360f, 360f, 360f) else RectF(-480f, -440f, 480f, 440f)
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#B3000000")
        }
        canvas.drawRoundRect(cardRect, 60f, 60f, cardPaint)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#40FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawRoundRect(cardRect, 60f, 60f, borderPaint)
    }

    // ponytail: removed all setShadowLayer calls for crisp vector typography without background blur/shadows
    val motravaHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E0E5F5")
        textSize = 34f
        typeface = semiBoldTypeface
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.05f
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 54f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E0E5F5")
        textSize = 30f
        typeface = android.graphics.Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 44f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#FF6D00")
        textSize = 40f
        typeface = semiBoldTypeface
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.15f
    }

    // (Moved to posterData fields)

    if (stickerFormat == 4) {
        // Strava Style Export
        // Note: The route is already baked into the liveMapSnapshot natively by MapLibre/Mapbox.
        // We only draw the route manually if we are NOT using the Full Maps format, or if for some reason the map background is absent.
        // But since Full Maps requires the map background to be drawn in the container above, the route is already there.

        val leftAlign = -460f
        val titleLeftPaint = Paint(titlePaint).apply { textAlign = Paint.Align.LEFT }
        val labelLeftPaint = Paint(labelPaint).apply { textAlign = Paint.Align.LEFT }
        val valueLeftPaint = Paint(valuePaint).apply { textAlign = Paint.Align.LEFT }
        val brandRightPaint = Paint(valuePaint).apply { textAlign = Paint.Align.RIGHT; color = android.graphics.Color.parseColor("#FF6D00"); typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD); letterSpacing = 0.2f }

        // MOTRAVA at top-right with orange color
        canvas.drawText("MOTRAVA", 460f, -860f, brandRightPaint)
        
        // Stats at bottom-left
        canvas.drawText(posterData.subtitle.uppercase(), leftAlign, 560f, titleLeftPaint)
        
        canvas.drawText(posterData.stat3Label, leftAlign, 660f, labelLeftPaint)
        canvas.drawText(posterData.stat3Value, leftAlign, 720f, valueLeftPaint)
        
        canvas.drawText(posterData.stat2Label, -100f, 660f, labelLeftPaint)
        canvas.drawText(posterData.stat2Value, -100f, 720f, valueLeftPaint)
        
        canvas.drawText(posterData.stat1Label, leftAlign, 820f, labelLeftPaint)
        canvas.drawText(posterData.stat1Value, leftAlign, 880f, valueLeftPaint)
    } else {
        if (stickerFormat != 3) {
            val headerY = if (stickerFormat == 2) -140f else -330f
            val titleY = if (stickerFormat == 2) -65f else -255f
            canvas.drawText(posterData.title, 0f, headerY, motravaHeaderPaint)
            canvas.drawText(posterData.subtitle.uppercase(), 0f, titleY, titlePaint)

            if (stickerFormat == 1) {
                // 2 Baris / Piramida
                canvas.drawText(posterData.stat1Label, -260f, -140f, labelPaint)
                canvas.drawText(posterData.stat1Value, -260f, -75f, valuePaint)
                canvas.drawText(posterData.stat2Label, 260f, -140f, labelPaint)
                canvas.drawText(posterData.stat2Value, 260f, -75f, valuePaint)
                canvas.drawText(posterData.stat3Label, 0f, -10f, labelPaint)
                canvas.drawText(posterData.stat3Value, 0f, 55f, valuePaint)
            } else {
                // 3 Kolom / Sejajar (Format 0 & 2)
                val statLabelY = if (stickerFormat == 2) 50f else -140f
                val statValueY = if (stickerFormat == 2) 115f else -75f
                canvas.drawText(posterData.stat1Label, -350f, statLabelY, labelPaint)
                canvas.drawText(posterData.stat1Value, -350f, statValueY, valuePaint)
                canvas.drawText(posterData.stat3Label, 0f, statLabelY, labelPaint)
                canvas.drawText(posterData.stat3Value, 0f, statValueY, valuePaint)
                canvas.drawText(posterData.stat2Label, 350f, statLabelY, labelPaint)
                canvas.drawText(posterData.stat2Value, 350f, statValueY, valuePaint)
            }
        }

        if (stickerFormat != 2) {
            val allRoutes = posterData.multiRoutes ?: (posterData.route?.let { listOf(it) } ?: emptyList())
            if (showManualRoute && allRoutes.isNotEmpty()) {
                var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
                var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
                for (r in allRoutes) {
                    for (p in r) {
                        if (p.latitude < minLat) minLat = p.latitude
                        if (p.latitude > maxLat) maxLat = p.latitude
                        if (p.longitude < minLon) minLon = p.longitude
                        if (p.longitude > maxLon) maxLon = p.longitude
                    }
                }

                val boxLeft = -380f
                val boxRight = 380f
                val boxTop = when (stickerFormat) {
                    3 -> -260f // Rute Saja
                    1 -> 95f   // 2 Baris + Rute
                    else -> 15f // 3 Kolom + Rute
                }
                val boxBottom = when (stickerFormat) {
                    3 -> 260f
                    1 -> 310f
                    else -> 275f
                }
                val boxW = boxRight - boxLeft
                val boxH = boxBottom - boxTop

                val latSpan = maxLat - minLat
                val lonSpan = maxLon - minLon
                val scaleFactor = if (latSpan == 0.0 || lonSpan == 0.0) 1f else {
                    kotlin.math.min(boxW / lonSpan, boxH / latSpan).toFloat() * 0.85f
                }

                val centerLat = (minLat + maxLat) / 2.0
                val centerLon = (minLon + maxLon) / 2.0
                val boxCenterX = (boxLeft + boxRight) / 2f
                val boxCenterY = (boxTop + boxBottom) / 2f

                // ponytail: clean solid vector line without blurry glow or shadow for maximum HD sharpness
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.parseColor("#FF6D00")
                    style = Paint.Style.STROKE
                    strokeWidth = 8f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                
                for (r in allRoutes) {
                    if (r.size >= 2) {
                        val path = Path()
                        r.forEachIndexed { idx, p ->
                            val x = boxCenterX + ((p.longitude - centerLon) * scaleFactor).toFloat()
                            val y = boxCenterY - ((p.latitude - centerLat) * scaleFactor).toFloat()
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        canvas.drawPath(path, linePaint)
                    }
                }
            }
        }

        val footerY = if (stickerFormat == 2) 230f else 360f
        canvas.drawText("M O T R A V A", 0f, footerY, brandPaint)
    }

}
