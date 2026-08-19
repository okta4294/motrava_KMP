package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.myapp.motrava.data.remote.dto.RoutePoint

@Composable
expect fun MapView(
    route: List<RoutePoint>,
    modifier: Modifier = Modifier,
    onSnapshotAvailable: (ImageBitmap?) -> Unit = {}
)
