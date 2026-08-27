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

actual suspend fun exportRecapVideo(
    recap: TripRecap,
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

        // === Generate all frames ===
        val allRoutes = recap.routes.filter { it.isNotEmpty() }
        val frames = generateFrames(
            recap = recap,
            allRoutes = allRoutes,
            w = w, h = h,
            titleFrames = titleFrames,
            routeFrames = routeFrames,
            statsFrames = statsFrames,
            onProgress = { p -> onProgress(p * 0.7f) }  // 0–70% = render
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
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// ─── Frame Generator ─────────────────────────────────────────────────────────

private fun generateFrames(
    recap: TripRecap,
    allRoutes: List<List<RoutePoint>>,
    w: Int, h: Int,
    titleFrames: Int,
    routeFrames: Int,
    statsFrames: Int,
    onProgress: (Float) -> Unit
): List<Bitmap> {
    val frames = mutableListOf<Bitmap>()
    val total = (titleFrames + routeFrames + statsFrames).toFloat()
    var rendered = 0

    // Precompute map bounds for projection
    val bounds = computeBounds(allRoutes)

    // ── Phase 1: Title fade-in ──
    for (i in 0 until titleFrames) {
        val alpha = (i + 1) / titleFrames.toFloat()
        val bmp = createFrame(w, h)
        val canvas = Canvas(bmp)
        drawBackground(canvas, w, h)
        drawTitle(canvas, recap.periodName, alpha, w, h)
        frames.add(bmp)
        rendered++
        onProgress(rendered / total)
    }

    // ── Phase 2: Animated route drawing ──
    // Flatten all points with route index for smooth animation
    data class AnimPoint(val routeIdx: Int, val pointIdx: Int)
    val allPoints = mutableListOf<AnimPoint>()
    allRoutes.forEachIndexed { ri, route ->
        route.indices.forEach { pi -> allPoints.add(AnimPoint(ri, pi)) }
    }

    // Sub-sample: if too many points, pick evenly distributed subset
    val step = max(1, allPoints.size / routeFrames)
    val drawnCounts = IntArray(allRoutes.size) { 0 }

    for (frameIdx in 0 until routeFrames) {
        // Advance drawn points
        val targetIdx = min(allPoints.size - 1, frameIdx * step)
        for (i in (frameIdx - 1).coerceAtLeast(0) * step until targetIdx + 1) {
            val ap = allPoints.getOrNull(i) ?: break
            if (drawnCounts[ap.routeIdx] <= ap.pointIdx) {
                drawnCounts[ap.routeIdx] = ap.pointIdx + 1
            }
        }

        val bmp = createFrame(w, h)
        val canvas = Canvas(bmp)
        drawBackground(canvas, w, h)
        drawTitle(canvas, recap.periodName, 1f, w, h)
        drawRoutes(canvas, allRoutes, drawnCounts, bounds, w, h, frameIdx.toFloat() / routeFrames)
        frames.add(bmp)
        rendered++
        onProgress(rendered / total)
    }

    // Full routes for stats phase
    val fullCounts = IntArray(allRoutes.size) { idx -> allRoutes[idx].size }

    // ── Phase 3: Stats fade-in ──
    for (i in 0 until statsFrames) {
        val alpha = (i + 1) / statsFrames.toFloat()
        val bmp = createFrame(w, h)
        val canvas = Canvas(bmp)
        drawBackground(canvas, w, h)
        drawTitle(canvas, recap.periodName, 1f, w, h)
        drawRoutes(canvas, allRoutes, fullCounts, bounds, w, h, 1f)
        drawStats(canvas, recap, alpha, w, h)
        frames.add(bmp)
        rendered++
        onProgress(rendered / total)
    }

    return frames
}

// ─── Canvas Drawing Primitives ────────────────────────────────────────────────

private fun createFrame(w: Int, h: Int): Bitmap =
    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

private fun drawBackground(canvas: Canvas, w: Int, h: Int) {
    val bgPaint = Paint().apply { color = Color.parseColor("#0A0A12") }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
    // Subtle radial glow at center
    val gradPaint = Paint().apply {
        shader = RadialGradient(
            w / 2f, h / 2f, h * 0.55f,
            intArrayOf(Color.parseColor("#1A1A3A"), Color.parseColor("#0A0A12")),
            null, Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), gradPaint)
}

private fun drawTitle(canvas: Canvas, periodName: String, alpha: Float, w: Int, h: Int) {
    if (alpha <= 0f) return
    val alphaByte = (alpha * 255).toInt().coerceIn(0, 255)
    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00"); this.alpha = alphaByte
        textSize = 52f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.LEFT; letterSpacing = 0.15f
    }
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E5F5"); this.alpha = alphaByte
        textSize = 40f; typeface = Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }
    val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; this.alpha = alphaByte
        textSize = 72f; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B8D0"); this.alpha = alphaByte
        textSize = 38f; typeface = Typeface.DEFAULT
        textAlign = Paint.Align.LEFT
    }

    val x = 80f
    val top = h * 0.10f
    canvas.drawText("MOTRAVA", x, top + 60f, brandPaint)
    canvas.drawText(periodName, x, top + 145f, periodPaint)
    canvas.drawText("Your journey in numbers.", x, top + 205f, tagPaint)
}




private data class MapBounds(
    val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double
)

private fun computeBounds(allRoutes: List<List<RoutePoint>>): MapBounds {
    var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
    var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
    for (r in allRoutes) for (p in r) {
        if (p.latitude < minLat) minLat = p.latitude
        if (p.latitude > maxLat) maxLat = p.latitude
        if (p.longitude < minLon) minLon = p.longitude
        if (p.longitude > maxLon) maxLon = p.longitude
    }
    return MapBounds(minLat, maxLat, minLon, maxLon)
}

private fun drawRoutes(
    canvas: Canvas,
    allRoutes: List<List<RoutePoint>>,
    drawnCounts: IntArray,
    bounds: MapBounds,
    w: Int, h: Int,
    routeProgress: Float
) {
    if (bounds.minLat == Double.MAX_VALUE) return
    val pad = 0.12f
    val boxL = bounds.minLon - pad; val boxR = bounds.maxLon + pad
    val boxT = bounds.maxLat + pad; val boxB = bounds.minLat - pad
    val latSpan = boxT - boxB; val lonSpan = boxR - boxL
    val scale = if (latSpan == 0.0 || lonSpan == 0.0) 1f else min(
        (w * 0.78f) / lonSpan, (h * 0.85f) / latSpan
    ).toFloat()
    val cLat = (bounds.minLat + bounds.maxLat) / 2.0
    val cLon = (bounds.minLon + bounds.maxLon) / 2.0
    fun lngToX(lon: Double) = (w / 2f) + ((lon - cLon) * scale).toFloat()
    fun latToY(lat: Double) = (h / 2f) - ((lat - cLat) * scale).toFloat()

    // Ghost: all routes dim
    val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#30FF6D00"); style = Paint.Style.STROKE
        strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    for (r in allRoutes) {
        if (r.size < 2) continue
        val path = Path()
        r.forEachIndexed { idx, p ->
            val x = lngToX(p.longitude); val y = latToY(p.latitude)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, ghostPaint)
    }

    // Active: drawn portion
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00"); style = Paint.Style.STROKE
        strokeWidth = 9f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
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
        // Moving head dot
        if (routeProgress < 1f) {
            val last = r[count - 1]
            val lx = lngToX(last.longitude); val ly = latToY(last.latitude)
            canvas.drawCircle(lx, ly, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF6D00"); style = Paint.Style.FILL })
            canvas.drawCircle(lx, ly, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL })
        }
    }
}

private fun drawStats(canvas: Canvas, recap: TripRecap, alpha: Float, w: Int, h: Int) {
    val alphaByte = (alpha * 255).toInt().coerceIn(0, 255)
    // Semi-transparent card
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000"); this.alpha = ((alphaByte * 0.85f).toInt())
    }
    val cardLeft = 60f; val cardTop = h * 0.80f
    val cardRight = w - 60f; val cardBottom = h * 0.97f
    canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 32f, 32f, cardPaint)

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0B8D0"); this.alpha = alphaByte
        textSize = 34f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT
    }
    val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; this.alpha = alphaByte
        textSize = 56f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }
    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6D00"); this.alpha = alphaByte
        textSize = 56f; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    }

    val cy1 = cardTop + (cardBottom - cardTop) * 0.32f
    val cy2 = cardTop + (cardBottom - cardTop) * 0.82f

    val col1 = w * 0.20f; val col2 = w * 0.50f; val col3 = w * 0.80f

    canvas.drawText("TRIPS", col1, cy1, labelPaint)
    canvas.drawText("${recap.totalTrips}", col1, cy2, valuePaint)
    canvas.drawText("DISTANCE", col2, cy1, labelPaint)
    canvas.drawText("${"%.1f".format(recap.totalDistance)} km", col2, cy2, accentPaint)
    canvas.drawText("MAX SPEED", col3, cy1, labelPaint)
    canvas.drawText("${"%.0f".format(recap.maxSpeed)} km/h", col3, cy2, valuePaint)
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
