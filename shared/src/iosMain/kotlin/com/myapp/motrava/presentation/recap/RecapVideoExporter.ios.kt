package com.myapp.motrava.presentation.recap

import com.myapp.motrava.domain.model.TripRecap

actual suspend fun exportRecapVideo(
    recap: TripRecap,
    isDarkTheme: Boolean,
    onProgress: (Float) -> Unit
): String? {
    // iOS implementation: requires AVFoundation / AVAssetWriter
    // Stub for now — returns null
    return null
}
