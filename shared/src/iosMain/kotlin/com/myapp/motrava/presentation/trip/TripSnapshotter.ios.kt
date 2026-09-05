package com.myapp.motrava.presentation.trip

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.myapp.motrava.data.remote.dto.RoutePoint
import kotlinx.cinterop.*
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Image
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSURL
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy
import cocoapods.Mapbox.*
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getMapSnapshot(route: List<RoutePoint>, width: Int, height: Int, isDarkTheme: Boolean): ImageBitmap? = suspendCancellableCoroutine { cont ->
    if (route.isEmpty()) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    val first = route.first()
    val camera = MGLMapCamera.cameraLookingAtCenterCoordinate(
        platform.CoreLocation.CLLocationCoordinate2DMake(first.latitude, first.longitude),
        fromDistance = 5000.0,
        pitch = 0.0,
        heading = 0.0
    )
    
    val styleUri = if (isDarkTheme) "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json" else "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    val options = MGLMapSnapshotOptions(
        styleURL = NSURL.URLWithString(styleUri),
        camera = camera,
        size = CGSizeMake(width.toDouble(), height.toDouble())
    )
    
    val snapshotter = MGLMapSnapshotter(options)
    snapshotter.startWithCompletionHandler { snapshot, error ->
        if (error != null || snapshot == null) {
            cont.resume(null)
        } else {
            val uiImage = snapshot.image
            val data = UIImagePNGRepresentation(uiImage)
            val bytes = data?.let { nsData ->
                ByteArray(nsData.length.toInt()).apply {
                    usePinned { pinned ->
                        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                    }
                }
            }
            val imageBitmap = bytes?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
            cont.resume(imageBitmap)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun getMultiMapSnapshot(routes: List<List<RoutePoint>>, width: Int, height: Int, isDarkTheme: Boolean): ImageBitmap? = suspendCancellableCoroutine { cont ->
    if (routes.isEmpty() || routes.all { it.isEmpty() }) {
        cont.resume(null)
        return@suspendCancellableCoroutine
    }

    val first = routes.first { it.isNotEmpty() }.first()
    val camera = MGLMapCamera.cameraLookingAtCenterCoordinate(
        platform.CoreLocation.CLLocationCoordinate2DMake(first.latitude, first.longitude),
        fromDistance = 15000.0,
        pitch = 0.0,
        heading = 0.0
    )
    
    val styleUri = if (isDarkTheme) "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json" else "https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json"
    val options = MGLMapSnapshotOptions(
        styleURL = NSURL.URLWithString(styleUri),
        camera = camera,
        size = CGSizeMake(width.toDouble(), height.toDouble())
    )
    
    val snapshotter = MGLMapSnapshotter(options)
    snapshotter.startWithCompletionHandler { snapshot, error ->
        if (error != null || snapshot == null) {
            cont.resume(null)
        } else {
            val uiImage = snapshot.image
            val data = UIImagePNGRepresentation(uiImage)
            val bytes = data?.let { nsData ->
                ByteArray(nsData.length.toInt()).apply {
                    usePinned { pinned ->
                        memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
                    }
                }
            }
            val imageBitmap = bytes?.let { Image.makeFromEncoded(it).toComposeImageBitmap() }
            cont.resume(imageBitmap)
        }
    }
}
