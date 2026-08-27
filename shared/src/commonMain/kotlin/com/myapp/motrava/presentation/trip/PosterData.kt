package com.myapp.motrava.presentation.trip

import com.myapp.motrava.data.remote.dto.RoutePoint

data class PosterData(
    val title: String,
    val subtitle: String,
    val stat1Label: String,
    val stat1Value: String,
    val stat2Label: String,
    val stat2Value: String,
    val stat3Label: String,
    val stat3Value: String,
    val route: List<RoutePoint>? = null,
    val multiRoutes: List<List<RoutePoint>>? = null
)
