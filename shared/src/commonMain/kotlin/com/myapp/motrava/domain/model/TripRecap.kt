package com.myapp.motrava.domain.model

import com.myapp.motrava.data.remote.dto.RoutePoint

data class TripRecap(
    val periodName: String, // e.g., "August 2026" or "2026"
    val totalDistance: Double,
    val totalDuration: Long,
    val totalTrips: Int,
    val maxSpeed: Double,
    val averageSpeed: Double,
    val routes: List<List<RoutePoint>> // Multiple routes for the map
)
