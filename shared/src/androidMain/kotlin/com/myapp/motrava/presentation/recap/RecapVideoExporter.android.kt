package com.myapp.motrava.presentation.recap

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RadialGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.myapp.motrava.data.remote.dto.RoutePoint
import com.myapp.motrava.domain.model.TripRecap
import org.koin.core.context.GlobalContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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

actual suspend fun exportRecapVideo(
    recap: TripRecap,
    isDarkTheme: Boolean,
    onProgress: (Float) -> Unit
): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
    try {
        val context = GlobalContext.get().get<Context>()
        val w = 1080
        val h = 1920
        val fps = 30

        // Frame budget (total 450 frames = 15s)
        val titleFrames = 30        // 1 detik
        val statsFrames = 60        // 2 detik
        val routeFrames = 360       // 12 detik

        onProgress(0.01f)

        // Capture map snapshot (single shot, reused for all frames)
        val allRoutes = recap.routes.filter { it.isNotEmpty() }
        val snapshotResult = captureMapSnapshot(context, allRoutes, w, h, isDarkTheme)
        onProgress(0.05f)

        // Output path
        val fileName = "motrava_recap_${recap.periodName.replace(" ", "_")}_${System.currentTimeMillis()}.mp4"
        val outputFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.cacheDir, fileName)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Motrava")
            dir.mkdirs()
            File(dir, fileName)
        }

        // Stream render and encode MP4 (single frame buffer, avoids 3.7GB OOM)
        renderAndEncodeToMp4(
            outputPath = outputFile.absolutePath,
            width = w,
            height = h,
            fps = fps,
            totalFrames = 450, // 15 detik @ 30fps
            recap = recap,
            allRoutes = allRoutes,
            snapshotResult = snapshotResult,
            isDarkTheme = isDarkTheme,
            onProgress = { p -> onProgress(0.05f + p * 0.93f) }  // 5–98%
        )

        // Save to MediaStore (Android Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Motrava")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    outputFile.inputStream().copyTo(out)
                }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
            outputFile.delete()
            onProgress(1f)
            return@withContext uri?.toString()
        }

        onProgress(1f)
        outputFile.absolutePath
    } catch (t: Throwable) {
        t.printStackTrace()
        null
    }
}

// Holds both the rendered bitmap and the exact geographic bounds used by MapSnapshotter
private data class SnapshotResult(
    val bitmap: Bitmap,
    val minLat: Double, val maxLat: Double,
    val minLon: Double, val maxLon: Double
)

private suspend fun captureMapSnapshot(
    context: Context,
    allRoutes: List<List<RoutePoint>>,
    w: Int, h: Int,
    isDarkTheme: Boolean
): SnapshotResult? {
    if (allRoutes.isEmpty() || allRoutes.all { it.isEmpty() }) return null
    return withTimeoutOrNull(15_000L) {
        suspendCancellableCoroutine { cont ->
            try {
                val boundsBuilder = LatLngBounds.Builder()
                allRoutes.forEach { route ->
                    if (route.isNotEmpty()) {
                        route.forEach { boundsBuilder.include(LatLng(it.latitude, it.longitude)) }
                    }
                }
                val rawBounds = boundsBuilder.build()
                val latSpan = kotlin.math.abs(rawBounds.latitudeNorth - rawBounds.latitudeSouth)
                val lonSpan = kotlin.math.abs(rawBounds.longitudeEast - rawBounds.longitudeWest)

                // 35% vertical, 25% horizontal padding to prevent edge clipping (matches TripSnapshotter)
                val padLat = if (latSpan == 0.0) 0.015 else kotlin.math.max(latSpan * 0.35, 0.005)
                val padLon = if (lonSpan == 0.0) 0.015 else kotlin.math.max(lonSpan * 0.25, 0.005)
                val centerShiftNorth = padLat * 0.15

                val snMinLat = (rawBounds.latitudeSouth - padLat + centerShiftNorth).coerceIn(-89.9, 89.9)
                val snMaxLat = (rawBounds.latitudeNorth + padLat + centerShiftNorth).coerceIn(-89.9, 89.9)
                val snMinLon = rawBounds.longitudeWest - padLon
                val snMaxLon = rawBounds.longitudeEast + padLon

                val paddedBounds = LatLngBounds.from(snMaxLat, snMaxLon, snMinLat, snMinLon)

                val styleUrl = if (isDarkTheme) {
                    "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
                } else {
                    "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"
                }

                val styleBuilder = Style.Builder().fromUri(styleUrl)

                val options = MapSnapshotter.Options(w, h)
                    .withRegion(paddedBounds)
                    .withStyleBuilder(styleBuilder)
                    .withPixelRatio(1f)
                    .withLogo(false)

                Handler(Looper.getMainLooper()).post {
                    try {
                        org.maplibre.android.MapLibre.getInstance(context)
                        val snapshotter = object : MapSnapshotter(context, options) {
                            override fun addOverlay(mapSnapshot: MapSnapshot) {
                                // No-op to bypass MapLibre createScaledLogo NPE bug
                            }
                        }
                        snapshotter.start(object : MapSnapshotter.SnapshotReadyCallback {
                            override fun onSnapshotReady(snapshot: MapSnapshot) {
                                if (cont.isActive) {
                                    val copied = snapshot.bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                                    // Extract EXACT geographic bounds from the rendered image
                                    val topLeft = snapshot.latLngForPixel(android.graphics.PointF(0f, 0f))
                                    val bottomRight = snapshot.latLngForPixel(android.graphics.PointF(w.toFloat(), h.toFloat()))
                                    
                                    val actualMinLat = bottomRight.latitude
                                    val actualMaxLat = topLeft.latitude
                                    val actualMinLon = topLeft.longitude
                                    val actualMaxLon = bottomRight.longitude
                                    
                                    cont.resume(SnapshotResult(copied, actualMinLat, actualMaxLat, actualMinLon, actualMaxLon))
                                }
                            }
                        }, object : MapSnapshotter.ErrorHandler {
                            override fun onError(error: String) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                        cont.invokeOnCancellation { snapshotter.cancel() }
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        if (cont.isActive) cont.resume(null)
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }
    }
}


private fun easeOutCubic(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    val inv = 1f - c
    return 1f - inv * inv * inv
}

private fun easeOutExpo(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return if (c >= 1f) 1f else 1f - Math.pow(2.0, -10.0 * c).toFloat()
}

private fun formatVideoDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0m"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

private class StoryOverlayPaints {
    val accentColor = Color.parseColor("#FE6E00")
    val mutedWarmColor = Color.parseColor("#B9B3AC")

    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        letterSpacing = 0.28f
        textAlign = Paint.Align.CENTER
    }
    val kickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        letterSpacing = 0.18f
        textAlign = Paint.Align.LEFT
    }
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 78f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        letterSpacing = -0.02f
        textAlign = Paint.Align.LEFT
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedWarmColor
        textSize = 34f
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }
    val distLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedWarmColor
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        letterSpacing = 0.16f
        textAlign = Paint.Align.LEFT
    }
    val distValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 114f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        letterSpacing = -0.03f
        textAlign = Paint.Align.LEFT
    }
    val distUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 44f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = mutedWarmColor
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        letterSpacing = 0.14f
        textAlign = Paint.Align.LEFT
    }
    val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 52f
        typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        letterSpacing = -0.01f
        textAlign = Paint.Align.LEFT
    }
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
    }
    val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    val progressTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(65, 255, 255, 255)
        style = Paint.Style.FILL
    }
}

private fun drawStoryOverlay(
    canvas: Canvas,
    recap: TripRecap,
    paints: StoryOverlayPaints,
    frameIdx: Int,
    totalFrames: Int,
    routeProgress: Float,
    w: Int,
    h: Int
) {
    val marginX = 84f
    val contentWidth = w - (marginX * 2f)

    // ── Top Timeline Progress Hairline (9:16 safe zone) ──
    val overallProgress = (frameIdx.toFloat() / (totalFrames - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    val barY = 96f
    val barH = 5f
    canvas.drawRoundRect(RectF(marginX, barY, w - marginX, barY + barH), 3f, 3f, paints.progressTrackPaint)
    paints.accentBarPaint.alpha = 255
    canvas.drawRoundRect(
        RectF(marginX, barY, marginX + contentWidth * overallProgress, barY + barH),
        3f, 3f, paints.accentBarPaint
    )

    // ── Brand Header (MOTRAVA + orange dot) ──
    val brandT = easeOutCubic(frameIdx / 24f)
    val brandAlpha = (brandT * 255).toInt().coerceIn(0, 255)
    val brandSlide = (1f - brandT) * 18f
    paints.brandPaint.alpha = brandAlpha
    canvas.drawText("MOTRAVA", w / 2f, 174f + brandSlide, paints.brandPaint)
    paints.accentBarPaint.alpha = brandAlpha
    canvas.drawRoundRect(
        RectF(w / 2f - 22f, 192f + brandSlide, w / 2f + 22f, 197f + brandSlide),
        3f, 3f, paints.accentBarPaint
    )

    // ── Bottom Editorial Telemetry Block (y = 1170..1710, clear of map center & Reels bottom UI) ──
    // Act 1: Period Kicker + Headline (frames 8..34)
    val step1T = easeOutExpo((frameIdx - 8f) / 26f)
    if (step1T > 0.01f) {
        val a1 = (step1T * 255).toInt().coerceIn(0, 255)
        val yOff1 = (1f - step1T) * 32f

        paints.accentBarPaint.alpha = a1
        canvas.drawRoundRect(
            RectF(marginX, 1188f + yOff1, marginX + 28f, 1194f + yOff1),
            3f, 3f, paints.accentBarPaint
        )
        paints.kickerPaint.alpha = a1
        canvas.drawText("RIDE RECAP", marginX + 42f, 1200f + yOff1, paints.kickerPaint)

        paints.titlePaint.alpha = a1
        canvas.drawText(recap.periodName, marginX, 1288f + yOff1, paints.titlePaint)

        paints.subPaint.alpha = (a1 * 0.88f).toInt()
        val subtitle = if (recap.totalTrips == 1) "1 recorded journey on the road" else "${recap.totalTrips} recorded journeys on the road"
        canvas.drawText(subtitle, marginX, 1340f + yOff1, paints.subPaint)
    }

    // Animated Hairline Divider (frames 24..50)
    val divT = easeOutCubic((frameIdx - 24f) / 26f)
    if (divT > 0.01f) {
        paints.dividerPaint.alpha = (divT * 52).toInt().coerceIn(0, 52)
        val divY = 1384f
        canvas.drawLine(marginX, divY, marginX + contentWidth * divT, divY, paints.dividerPaint)
    }

    // Act 2: Hero Distance with Live Count-Up (frames 30..62 entrance, count-up tracks route)
    val step2T = easeOutExpo((frameIdx - 30f) / 30f)
    if (step2T > 0.01f) {
        val a2 = (step2T * 255).toInt().coerceIn(0, 255)
        val yOff2 = (1f - step2T) * 30f

        paints.distLabelPaint.alpha = a2
        canvas.drawText("TOTAL DISTANCE", marginX, 1442f + yOff2, paints.distLabelPaint)

        val countProgress = easeOutCubic(((frameIdx - 30f) / 140f).coerceAtLeast(routeProgress))
        val animatedDistKm = (recap.totalDistance / 1000.0) * countProgress
        val distNumText = "%.1f".format(animatedDistKm)

        paints.distValPaint.alpha = a2
        canvas.drawText(distNumText, marginX, 1556f + yOff2, paints.distValPaint)

        val numWidth = paints.distValPaint.measureText(distNumText)
        paints.distUnitPaint.alpha = a2
        canvas.drawText("km", marginX + numWidth + 16f, 1556f + yOff2, paints.distUnitPaint)
    }

    // Act 3: 3-Column Secondary Metrics with Staggered Reveal (frames 54..96)
    val col1T = easeOutExpo((frameIdx - 54f) / 26f)
    val col2T = easeOutExpo((frameIdx - 62f) / 26f)
    val col3T = easeOutExpo((frameIdx - 70f) / 26f)

    val rowLabelY = 1638f
    val rowValY = 1698f
    val colWidth = contentWidth / 3f

    if (col1T > 0.01f) {
        val a = (col1T * 255).toInt().coerceIn(0, 255)
        val yOff = (1f - col1T) * 24f
        paints.labelPaint.alpha = a
        paints.valPaint.alpha = a
        val animatedTrips = kotlin.math.round(recap.totalTrips * easeOutCubic((frameIdx - 54f) / 36f)).toInt()
        canvas.drawText("TRIPS", marginX, rowLabelY + yOff, paints.labelPaint)
        canvas.drawText("$animatedTrips", marginX, rowValY + yOff, paints.valPaint)
    }

    if (col2T > 0.01f) {
        val a = (col2T * 255).toInt().coerceIn(0, 255)
        val yOff = (1f - col2T) * 24f
        val x2 = marginX + colWidth
        paints.dividerPaint.alpha = (col2T * 42).toInt().coerceIn(0, 42)
        canvas.drawLine(x2 - 24f, rowLabelY - 18f, x2 - 24f, rowValY + 6f, paints.dividerPaint)

        paints.labelPaint.alpha = a
        paints.valPaint.alpha = a
        val animatedSpeed = recap.maxSpeed * easeOutCubic((frameIdx - 62f) / 48f)
        canvas.drawText("TOP SPEED", x2, rowLabelY + yOff, paints.labelPaint)
        canvas.drawText("${"%.0f".format(animatedSpeed)} km/h", x2, rowValY + yOff, paints.valPaint)
    }

    if (col3T > 0.01f) {
        val a = (col3T * 255).toInt().coerceIn(0, 255)
        val yOff = (1f - col3T) * 24f
        val x3 = marginX + colWidth * 2f
        paints.dividerPaint.alpha = (col3T * 42).toInt().coerceIn(0, 42)
        canvas.drawLine(x3 - 24f, rowLabelY - 18f, x3 - 24f, rowValY + 6f, paints.dividerPaint)

        paints.labelPaint.alpha = a
        paints.valPaint.alpha = a
        canvas.drawText("DURATION", x3, rowLabelY + yOff, paints.labelPaint)
        canvas.drawText(formatVideoDuration(recap.totalDuration), x3, rowValY + yOff, paints.valPaint)
    }
}

private fun drawRoutes(
    canvas: Canvas,
    allRoutes: List<List<RoutePoint>>,
    drawnCounts: IntArray,
    glowPaint: Paint,
    linePaint: Paint,
    corePaint: Paint,
    headHaloPaint: Paint,
    headDotPaint: Paint,
    routePath: Path,
    lngToX: (Double) -> Float,
    latToY: (Double) -> Float
) {
    var headX = Float.NaN
    var headY = Float.NaN

    allRoutes.forEachIndexed { idx, r ->
        val count = drawnCounts.getOrNull(idx)?.coerceAtMost(r.size) ?: 0
        if (count < 2) return@forEachIndexed
        routePath.rewind()
        for (pi in 0 until count) {
            val p = r[pi]
            val x = lngToX(p.longitude)
            val y = latToY(p.latitude)
            if (pi == 0) routePath.moveTo(x, y) else routePath.lineTo(x, y)
            if (pi == count - 1) {
                headX = x
                headY = y
            }
        }
        canvas.drawPath(routePath, glowPaint)
        canvas.drawPath(routePath, linePaint)
        canvas.drawPath(routePath, corePaint)
    }

    if (!headX.isNaN() && !headY.isNaN()) {
        canvas.drawCircle(headX, headY, 16f, headHaloPaint)
        canvas.drawCircle(headX, headY, 7f, headDotPaint)
    }
}

// ─── Stream Render & Video Encoder (Single Reusable Bitmap Buffer) ────────────

private const val MIME_TYPE = "video/avc"
private const val BIT_RATE = 8_000_000 // 8 Mbps
private const val I_FRAME_INTERVAL = 1

private fun renderAndEncodeToMp4(
    outputPath: String,
    width: Int,
    height: Int,
    fps: Int,
    totalFrames: Int,
    recap: TripRecap,
    allRoutes: List<List<RoutePoint>>,
    snapshotResult: SnapshotResult?,
    isDarkTheme: Boolean,
    onProgress: (Float) -> Unit
) {
    val scaledMap = snapshotResult?.bitmap?.let {
        Bitmap.createScaledBitmap(it, width, height, true)
    }

    // ── Projection bounds: must match snapshot exactly so routes align with tiles ──
    val projMinLat: Double
    val projMaxLat: Double
    val projMinLon: Double
    val projMaxLon: Double
    if (snapshotResult != null) {
        projMinLat = snapshotResult.minLat
        projMaxLat = snapshotResult.maxLat
        projMinLon = snapshotResult.minLon
        projMaxLon = snapshotResult.maxLon
    } else {
        var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
        for (r in allRoutes) for (p in r) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        val latSpan = maxLat - minLat
        val lonSpan = maxLon - minLon
        val padLat = if (latSpan == 0.0) 0.03 else kotlin.math.max(latSpan * 0.70, 0.01)
        val padLon = if (lonSpan == 0.0) 0.03 else kotlin.math.max(lonSpan * 0.70, 0.01)
        projMinLat = minLat - padLat
        projMaxLat = maxLat + padLat
        projMinLon = minLon - padLon
        projMaxLon = maxLon + padLon
    }

    // ── Web Mercator helpers ──
    fun mercatorY(latDeg: Double): Double {
        val latRad = Math.toRadians(latDeg.coerceIn(-85.05, 85.05))
        return Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0))
    }
    val mTop    = mercatorY(projMaxLat)
    val mBottom = mercatorY(projMinLat)
    val mSpan   = mTop - mBottom
    val lonSpan = projMaxLon - projMinLon
    fun lngToX(lon: Double): Float = ((lon - projMinLon) / lonSpan * width).toFloat()
    fun latToY(lat: Double): Float  = ((mTop - mercatorY(lat)) / mSpan * height).toFloat()

    // ── Animation points state ──
    data class AnimPoint(val routeIdx: Int, val pointIdx: Int)
    val allPoints = mutableListOf<AnimPoint>()
    allRoutes.forEachIndexed { ri, route ->
        route.indices.forEach { pi -> allPoints.add(AnimPoint(ri, pi)) }
    }
    val drawnCounts = IntArray(allRoutes.size) { 0 }

    // ── Camera state ──
    val firstPt   = allRoutes.firstOrNull()?.firstOrNull()
    var camCenterX = if (firstPt != null) lngToX(firstPt.longitude) else width / 2f
    var camCenterY = if (firstPt != null) latToY(firstPt.latitude)  else height / 2f
    var camZoom   = if (allRoutes.isNotEmpty()) 3.5f else 1f

    // ── Pre-allocated drawing paints and cinematic vignette scrim ──
    val overlayPaints = StoryOverlayPaints()
    val bgPaint = Paint().apply { color = Color.parseColor(if (isDarkTheme) "#1A1715" else "#FCFAF7") }
    val vignettePaint = Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                Color.argb(145, 14, 12, 10),
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                Color.argb(190, 14, 12, 10),
                Color.argb(242, 14, 12, 10)
            ),
            floatArrayOf(0f, 0.20f, 0.54f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    val routeGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(72, 254, 110, 0)
        style = Paint.Style.STROKE
        strokeWidth = 22f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val routeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FE6E00")
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val routeCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 255, 210, 140)
        style = Paint.Style.STROKE
        strokeWidth = 3.2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val headHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 254, 110, 0)
        style = Paint.Style.FILL
    }
    val headDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    val reusableRoutePath = Path()

    // ── Single reusable bitmap buffer (allocates once, avoids 3.7GB OOM) ──
    val renderBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val renderCanvas = Canvas(renderBitmap)

    // ── MediaCodec & MediaMuxer setup ──
    val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
    }

    val encoder = MediaCodec.createEncoderByType(MIME_TYPE)
    encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val surface = encoder.createInputSurface()
    encoder.start()

    val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var trackIdx = -1
    var muxerStarted = false
    val intervalUs = (1_000_000L / fps)
    var presentationUs = 0L
    val bufferInfo = MediaCodec.BufferInfo()

    try {
        for (frameIdx in 0 until totalFrames) {
            // 1. Update drawn route progress (12s / 360 frames)
            val routeProgress = (frameIdx.toFloat() / 360f).coerceAtMost(1f)
            if (allPoints.isNotEmpty()) {
                val targetIdx = (routeProgress * (allPoints.size - 1)).toInt().coerceIn(0, allPoints.size - 1)
                for (i in 0..targetIdx) {
                    val ap = allPoints[i]
                    if (drawnCounts[ap.routeIdx] <= ap.pointIdx) drawnCounts[ap.routeIdx] = ap.pointIdx + 1
                }
            }

            // 2. Target camera bounding box
            var minPx = Float.MAX_VALUE; var maxPx = -Float.MAX_VALUE
            var minPy = Float.MAX_VALUE; var maxPy = -Float.MAX_VALUE
            var hasDrawn = false
            allRoutes.forEachIndexed { idx, route ->
                val count = drawnCounts[idx].coerceAtMost(route.size)
                for (pi in 0 until count) {
                    val p  = route[pi]
                    val px = lngToX(p.longitude)
                    val py = latToY(p.latitude)
                    if (px < minPx) minPx = px; if (px > maxPx) maxPx = px
                    if (py < minPy) minPy = py; if (py > maxPy) maxPy = py
                    hasDrawn = true
                }
            }

            val targetCamCenterX: Float
            val targetCamCenterY: Float
            val targetCamZoom: Float

            if (!hasDrawn) {
                targetCamCenterX = camCenterX
                targetCamCenterY = camCenterY
                targetCamZoom    = camZoom
            } else {
                targetCamCenterX = (minPx + maxPx) / 2f
                targetCamCenterY = (minPy + maxPy) / 2f
                val padX = width * 0.15f
                val padY = height * 0.15f
                val bboxW = (maxPx - minPx + padX * 2).coerceAtLeast(50f)
                val bboxH = (maxPy - minPy + padY * 2).coerceAtLeast(50f)
                targetCamZoom = min(width.toFloat() / bboxW, height.toFloat() / bboxH).coerceIn(0.9f, 5f)
            }

            // 3. Smooth camera lerp
            val lerpFactor = 0.06f
            camCenterX += (targetCamCenterX - camCenterX) * lerpFactor
            camCenterY += (targetCamCenterY - camCenterY) * lerpFactor
            camZoom    += (targetCamZoom    - camZoom)    * lerpFactor

            // 4. Render into single reusable bitmap (frame route in upper-center clear zone above bottom telemetry)
            renderCanvas.save()
            renderCanvas.translate(width / 2f, height * 0.38f)
            renderCanvas.scale(camZoom, camZoom)
            renderCanvas.translate(-camCenterX, -camCenterY)

            if (scaledMap != null) {
                renderCanvas.drawBitmap(scaledMap, 0f, 0f, null)
            } else {
                renderCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
            }

            drawRoutes(
                renderCanvas, allRoutes, drawnCounts,
                routeGlowPaint, routeLinePaint, routeCorePaint,
                headHaloPaint, headDotPaint,
                reusableRoutePath, ::lngToX, ::latToY
            )
            renderCanvas.restore()

            // 5. Cinematic top + bottom vignette scrim (keeps center route crisp)
            renderCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), vignettePaint)

            // 6. Editorial choreographed text overlays
            drawStoryOverlay(
                canvas = renderCanvas,
                recap = recap,
                paints = overlayPaints,
                frameIdx = frameIdx,
                totalFrames = totalFrames,
                routeProgress = routeProgress,
                w = width,
                h = height
            )

            // 7. Write rendered frame directly to encoder surface
            val surfaceCanvas = surface.lockCanvas(null)
            surfaceCanvas.drawBitmap(renderBitmap, 0f, 0f, null)
            surface.unlockCanvasAndPost(surfaceCanvas)

            // Drain encoder
            drainEncoder(encoder, muxer, bufferInfo, { ti ->
                trackIdx = ti
                muxerStarted = true
            }, muxerStarted, trackIdx, presentationUs, false)

            presentationUs += intervalUs
            onProgress((frameIdx + 1).toFloat() / totalFrames)
        }

        // Signal end of stream
        encoder.signalEndOfInputStream()
        drainEncoder(encoder, muxer, bufferInfo, { }, muxerStarted, trackIdx, presentationUs, true)
    } finally {
        renderBitmap.recycle()
        scaledMap?.recycle()
        encoder.stop()
        encoder.release()
        surface.release()
        if (muxerStarted) muxer.stop()
        muxer.release()
    }
}

private fun drainEncoder(
    encoder: MediaCodec,
    muxer: MediaMuxer,
    bufferInfo: MediaCodec.BufferInfo,
    onTrackAdded: (Int) -> Unit,
    muxerStarted: Boolean,
    trackIdx: Int,
    presentationUs: Long,
    endOfStream: Boolean
): Unit {
    var started = muxerStarted
    var track = trackIdx
    val timeoutUs = if (endOfStream) 10_000L else 0L

    loop@ while (true) {
        val outputIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        when {
            outputIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                if (!endOfStream) break@loop
            }
            outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                track = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                started = true
                onTrackAdded(track)
            }
            outputIdx >= 0 -> {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size != 0 && started) {
                    val encodedData: ByteBuffer = encoder.getOutputBuffer(outputIdx)!!
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    bufferInfo.presentationTimeUs = presentationUs
                    muxer.writeSampleData(track, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(outputIdx, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
            }
        }
    }
}
