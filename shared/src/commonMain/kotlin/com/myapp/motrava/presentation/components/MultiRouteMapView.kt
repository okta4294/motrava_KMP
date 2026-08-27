package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myapp.motrava.data.remote.dto.RoutePoint

@Composable
expect fun MultiRouteMapView(
    routes: List<List<RoutePoint>>,
    modifier: Modifier = Modifier
)
