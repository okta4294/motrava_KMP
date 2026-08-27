package com.myapp.motrava.presentation.recap

import com.myapp.motrava.domain.model.TripRecap

/**
 * Export the animated recap as an MP4 video.
 * @param recap The recap data to render
 * @param onProgress Progress callback (0.0 to 1.0)
 * @return Absolute path to the saved video, or null on failure
 */
expect suspend fun exportRecapVideo(
    recap: TripRecap,
    onProgress: (Float) -> Unit
): String?
