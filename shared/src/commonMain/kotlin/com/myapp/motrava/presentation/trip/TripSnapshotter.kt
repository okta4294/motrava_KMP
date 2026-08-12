package com.myapp.motrava.presentation.trip

import androidx.compose.ui.graphics.ImageBitmap
import com.myapp.motrava.data.remote.dto.RoutePoint

expect suspend fun getMapSnapshot(route: List<RoutePoint>, width: Int, height: Int): ImageBitmap?
