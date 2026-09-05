package com.myapp.motrava.presentation.trip

import androidx.compose.ui.graphics.ImageBitmap
import com.myapp.motrava.data.remote.dto.RoutePoint

expect suspend fun getMapSnapshot(route: List<RoutePoint>, width: Int, height: Int, isDarkTheme: Boolean = false): ImageBitmap?
expect suspend fun getMultiMapSnapshot(routes: List<List<RoutePoint>>, width: Int, height: Int, isDarkTheme: Boolean = false): ImageBitmap?
