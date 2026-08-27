package com.myapp.motrava.presentation.trip

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

@Composable
expect fun PosterEditorDialog(
    posterData: PosterData,
    initialIsTransparentBg: Boolean = false,
    liveMapSnapshot: ImageBitmap? = null,
    onDismiss: () -> Unit
)
