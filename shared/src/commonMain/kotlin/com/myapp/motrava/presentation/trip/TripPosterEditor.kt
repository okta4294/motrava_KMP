package com.myapp.motrava.presentation.trip

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.myapp.motrava.data.remote.dto.TripDetailData

@Composable
expect fun TripPosterEditorDialog(
    trip: TripDetailData,
    initialIsTransparentBg: Boolean = false,
    liveMapSnapshot: ImageBitmap? = null,
    onDismiss: () -> Unit
)
