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

        // === Frame budget (total 450 frame = 15 detik) ===
        val titleFrames = 30        // 1 detik
        val statsFrames = 60        // 2 detik
        val routeFrames = 360       // 12 detik

        onProgress(0.01f)

        // === Capture map snapshot (single shot, reused for all frames) ===
        val allRoutes = recap.routes.filter { it.isNotEmpty() }
        val snapshotResult = captureMapSnapshot(context, allRoutes, w, h, isDarkTheme)
        onProgress(0.05f)

        // === Generate all frames ===
        val frames = generateFrames(
            recap = recap,
            allRoutes = allRoutes,
            snapshotResult = snapshotResult,
            w = w, h = h,
            isDarkTheme = isDarkTheme,
            totalFrames = 450, // 15 detik @ 30fps
            onProgress = { p -> onProgress(0.05f + p * 0.65f) }  // 5–70% = render
        )

        // === Output path ===
        val fileName = "motrava_recap_${recap.periodName.replace(" ", "_")}_${System.currentTimeMillis()}.mp4"
        val outputFile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.cacheDir, fileName)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Motrava")
            dir.mkdirs()
            File(dir, fileName)
        }

        // === Encode MP4 ===
        encodeFramesToMp4(
            frames = frames,
            outputPath = outputFile.absolutePath,
            width = w,
            height = h,
            fps = fps,
            onProgress = { p -> onProgress(0.7f + p * 0.28f) }  // 70–98%
        )

        // Free bitmaps
        frames.forEach { it.recycle() }

        // === Save to MediaStore (Android Q+) ===
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


private fun generateFrames(
    recap: TripRecap,
    allRoutes: List<List<RoutePoint>>,
    snapshotResult: SnapshotResult?,
    w: Int, h: Int,
    isDarkTheme: Boolean,
    totalFrames: Int = 450, // 15 detik @ 30fps
    onProgress: (Float) -> Unit
): List<Bitmap> {
    val frames = mutableListOf<Bitmap>()

    // Scale map snapshot to fit the frame
    val scaledMap = snapshotResult?.bitmap?.let {
        Bitmap.createScaledBitmap(it, w, h, true)
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
        // Inline fallback bounds computation (computeBounds removed, inline instead)
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

    // ── Web Mercator helpers (shared by camera + route drawing) ──
    fun mercatorY(latDeg: Double): Double {
        val latRad = Math.toRadians(latDeg.coerceIn(-85.05, 85.05))
        return Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0))
    }
    val mTop    = mercatorY(projMaxLat)
    val mBottom = mercatorY(projMinLat)
    val mSpan   = mTop - mBottom
    val lonSpan = projMaxLon - projMinLon
    fun lngToX(lon: Double): Float = ((lon - projMinLon) / lonSpan * w).toFloat()
    fun latToY(lat: Double): Float  = ((mTop - mercatorY(lat)) / mSpan * h).toFloat()

    // ── Animation state ──
    data class AnimPoint(val routeIdx: Int, val pointIdx: Int)
    val allPoints = mutableListOf<AnimPoint>()
    allRoutes.forEachIndexed { ri, route ->
        route.indices.forEach { pi -> allPoints.add(AnimPoint(ri, pi)) }
    }
    val drawnCounts = IntArray(allRoutes.size) { 0 }

    // ── Camera state (starts on the first point, zoomed in) ──
    val firstPt   = allRoutes.firstOrNull()?.firstOrNull()
    var camCenterX = if (firstPt != null) lngToX(firstPt.longitude) else w / 2f
    var camCenterY = if (firstPt != null) latToY(firstPt.latitude)  else h / 2f
    var camZoom   = if (allRoutes.isNotEmpty()) 3.5f else 1f

    for (frameIdx in 0 until totalFrames) {
        val bmp    = createFrame(w, h)
        val canvas = Canvas(bmp)

        // 1. Update drawn route progress (12s / 360 frames)
        val routeProgress = (frameIdx.toFloat() / 360f).coerceAtMost(1f)
        if (allPoints.isNotEmpty()) {
            val targetIdx = (routeProgress * (allPoints.size - 1)).toInt().coerceIn(0, allPoints.size - 1)
            for (i in 0..targetIdx) {
                val ap = allPoints[i]
                if (drawnCounts[ap.routeIdx] <= ap.pointIdx) drawnCounts[ap.routeIdx] = ap.pointIdx + 1
            }
        }

        // 2. Compute target camera from bounding box of currently drawn pixels
        //    — mirrors MapLibre easeCamera(newLatLngBounds(currentBounds, padding))
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
            // Initialise camera at route start, zoomed in tight
            targetCamCenterX = camCenterX
            targetCamCenterY = camCenterY
            targetCamZoom    = camZoom
        } else {
            // Fit camera to drawn route bounds + 15% padding (like MapLibre)
            targetCamCenterX = (minPx + maxPx) / 2f
            targetCamCenterY = (minPy + maxPy) / 2f
            val padX = w * 0.15f
            val padY = h * 0.15f
            val bboxW = (maxPx - minPx + padX * 2).coerceAtLeast(50f)
            val bboxH = (maxPy - minPy + padY * 2).coerceAtLeast(50f)
            targetCamZoom = min(w.toFloat() / bboxW, h.toFloat() / bboxH).coerceIn(0.9f, 5f)
        }

        // 3. Smooth camera lerp (factor 0.06 ≈ 300ms settle at 30fps, matching easeCamera)
        val lerpFactor = 0.06f
        camCenterX += (targetCamCenterX - camCenterX) * lerpFactor
        camCenterY += (targetCamCenterY - camCenterY) * lerpFactor
        camZoom    += (targetCamZoom    - camZoom)    * lerpFactor

        // 4. Apply camera transform:
        //    Translate so that camCenter lands at screen centre, then scale.
        //    Transform: screen_xy = (map_xy - camCenter) * zoom + (w/2, h/2)
        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.scale(camZoom, camZoom)
        canvas.translate(-camCenterX, -camCenterY)

        // Draw map bitmap (in camera space)
        if (scaledMap != null) canvas.drawBitmap(scaledMap, 0f, 0f, null)
        else {
            val bgPaint = Paint().apply { color = Color.parseColor(if (isDarkTheme) "#0A0A12" else "#F5F5F5") }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        }

        // Draw animated route (in camera space — same coordinate system)
        drawRoutes(canvas, allRoutes, drawnCounts,
            projMinLat, projMaxLat, projMinLon, projMaxLon, w, h)

        canvas.restore() // back to screen space

        // 5. Full-screen dark overlay (always covers entire screen, outside camera)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(),
            Paint().apply { color = Color.parseColor("#80000000") })

        // 6. Draw text overlays (screen-space, not affected by camera)
        val step1Alpha = (frameIdx / 30f).coerceIn(0f, 1f)
        val step1OffsetY = (1f - step1Alpha) * 40f
        val step2Alpha = ((frameIdx - 30) / 30f).coerceIn(0f, 1f)
        val step2OffsetY = (1f - step2Alpha) * 40f
        val step3Alpha = ((frameIdx - 60) / 30f).coerceIn(0f, 1f)
        val step3OffsetY = (1f - step3Alpha) * 40f

        drawStoryOverlay(
            canvas = canvas, recap = recap,
            step1Alpha = step1Alpha, step1OffsetY = step1OffsetY,
            step2Alpha = step2Alpha, step2OffsetY = step2OffsetY,
            step3Alpha = step3Alpha, step3OffsetY = step3OffsetY,
            w = w, h = h, isDarkTheme = isDarkTheme
        )

        frames.add(bmp)
        onProgress((frameIdx + 1) / totalFrames.toFloat())
    }

    return frames
}


// ─── Canvas Drawing Primitives ────────────────────────────────────────────────

private fun createFrame(w: Int, h: Int): Bitmap =
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

private fun drawBackground(canvas: Canvas, w: Int, h: Int, mapSnapshot: Bitmap?, isDarkTheme: Boolean) {
    if (mapSnapshot != null) {
        canvas.drawBitmap(mapSnapshot, 0f, 0f, null)
    } else {
        val bgPaint = Paint().apply { color = Color.parseColor(if (isDarkTheme) "#0A0A12" else "#F5F5F5") }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
    }
}

private fun drawStoryOverlay(
    canvas: Canvas,
    recap: TripRecap,
    step1Alpha: Float, step1OffsetY: Float,
    step2Alpha: Float, step2OffsetY: Float,
    step3Alpha: Float, step3OffsetY: Float,
    w: Int, h: Int, isDarkTheme: Boolean
) {
    val startX = 90f

    // Step 1: Period Title & "You were unstoppable."
    if (step1Alpha > 0f) {
        val a1 = (step1Alpha * 255).toInt().coerceIn(0, 255)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.alpha = a1
            textSize = 76f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.alpha = (a1 * 0.9f).toInt()
            textSize = 40f; typeface = Typeface.DEFAULT
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText(recap.periodName, startX, 680f + step1OffsetY, titlePaint)
        canvas.drawText("You were unstoppable.", startX, 745f + step1OffsetY, subPaint)
    }

    // Step 2: DISTANCE & value
    if (step2Alpha > 0f) {
        val a2 = (step2Alpha * 255).toInt().coerceIn(0, 255)
        val distLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6D00"); this.alpha = a2
            textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            letterSpacing = 0.1f; textAlign = Paint.Align.LEFT
        }
        val distValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.alpha = a2
            textSize = 96f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        canvas.drawText("DISTANCE", startX, 900f + step2OffsetY, distLabelPaint)
        canvas.drawText("${"%.1f".format(recap.totalDistance / 1000)} km", startX, 1010f + step2OffsetY, distValPaint)
    }

    // Step 3: TRIPS & MAX SPEED
    if (step3Alpha > 0f) {
        val a3 = (step3Alpha * 255).toInt().coerceIn(0, 255)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (isDarkTheme) "#808080" else "#B3B3B3"); this.alpha = a3
            textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            letterSpacing = 0.1f; textAlign = Paint.Align.LEFT
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; this.alpha = a3
            textSize = 68f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }

        // Column 1: TRIPS
        canvas.drawText("TRIPS", startX, 1160f + step3OffsetY, labelPaint)
        canvas.drawText("${recap.totalTrips}", startX, 1245f + step3OffsetY, valPaint)

        // Column 2: MAX SPEED
        val col2X = w * 0.52f
        canvas.drawText("MAX SPEED", col2X, 1160f + step3OffsetY, labelPaint)
        canvas.drawText("${"%.0f".format(recap.maxSpeed)} km/h", col2X, 1245f + step3OffsetY, valPaint)
    }
}

private fun drawRoutes(
    canvas: Canvas,
    allRoutes: List<List<RoutePoint>>,
    drawnCounts: IntArray,
    minLat: Double, maxLat: Double,
    minLon: Double, maxLon: Double,
    w: Int, h: Int
) {
    if (minLat == Double.MAX_VALUE) return

    // ── Web Mercator projection (matches MapLibre / Carto tiles exactly) ──────
    // MapLibre uses EPSG:3857 (Web Mercator). To overlay routes on the snapshot
    // we must use the same projection, otherwise latitude lines shift non-linearly.
    fun mercatorY(latDeg: Double): Double {
        val latRad = Math.toRadians(latDeg.coerceIn(-85.05, 85.05))
        return Math.log(Math.tan(Math.PI / 4.0 + latRad / 2.0))
    }

    val mTop    = mercatorY(maxLat)
    val mBottom = mercatorY(minLat)
    val mSpan   = mTop - mBottom
    val lonSpan = maxLon - minLon

    // Map geographic coordinates to pixel space
    fun lngToX(lon: Double): Float = ((lon - minLon) / lonSpan * w).toFloat()
    fun latToY(lat: Double): Float  = ((mTop - mercatorY(lat)) / mSpan * h).toFloat()

    // Active: draw animated portion of each route
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00"); style = Paint.Style.STROKE
        strokeWidth = 10f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    allRoutes.forEachIndexed { idx, r ->
        val count = drawnCounts.getOrNull(idx)?.coerceAtMost(r.size) ?: 0
        if (count < 2) return@forEachIndexed
        val path = Path()
        for (pi in 0 until count) {
            val p = r[pi]
            val x = lngToX(p.longitude); val y = latToY(p.latitude)
            if (pi == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}

// ─── Video Encoder ────────────────────────────────────────────────────────────

private const val MIME_TYPE = "video/avc"
private const val BIT_RATE = 8_000_000 // 8 Mbps
private const val I_FRAME_INTERVAL = 1

private fun encodeFramesToMp4(
    frames: List<Bitmap>,
    outputPath: String,
    width: Int,
    height: Int,
    fps: Int,
    onProgress: (Float) -> Unit
) {
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
        frames.forEachIndexed { frameIdx, bitmap ->
            // Draw bitmap onto the encoder surface
            val canvas = surface.lockCanvas(null)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            surface.unlockCanvasAndPost(canvas)

            // Drain encoder
            drainEncoder(encoder, muxer, bufferInfo, { ti ->
                trackIdx = ti
                muxerStarted = true
            }, muxerStarted, trackIdx, presentationUs, false)

            presentationUs += intervalUs
            onProgress((frameIdx + 1).toFloat() / frames.size)
        }

        // Signal end of stream
        encoder.signalEndOfInputStream()
        drainEncoder(encoder, muxer, bufferInfo, { }, muxerStarted, trackIdx, presentationUs, true)
    } finally {
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
