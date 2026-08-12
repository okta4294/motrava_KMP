package com.myapp.motrava.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TripSessionManager {
    private val _activeTripId = MutableStateFlow<String?>(null)
    val activeTripId: StateFlow<String?> = _activeTripId.asStateFlow()

    private val _activeVehicleId = MutableStateFlow<String?>(null)
    val activeVehicleId: StateFlow<String?> = _activeVehicleId.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0f)
    val distanceMeters: StateFlow<Float> = _distanceMeters.asStateFlow()

    private val _speedKmh = MutableStateFlow(0f)
    val speedKmh: StateFlow<Float> = _speedKmh.asStateFlow()

    private val _currentLatLng = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLatLng: StateFlow<Pair<Double, Double>?> = _currentLatLng.asStateFlow()

    private val _currentRoute = MutableStateFlow<List<Pair<Double, Double>>>(emptyList())
    val currentRoute: StateFlow<List<Pair<Double, Double>>> = _currentRoute.asStateFlow()

    fun setTripActive(tripId: String, vehicleId: String?) {
        _activeTripId.value = tripId
        _activeVehicleId.value = vehicleId
    }

    fun setTripInactive() {
        _activeTripId.value = null
        _activeVehicleId.value = null
        _distanceMeters.value = 0f
        _speedKmh.value = 0f
        _currentLatLng.value = null
        _currentRoute.value = emptyList()
    }

    fun updateStats(distance: Float, speed: Float, location: Pair<Double, Double>) {
        _distanceMeters.value = distance
        _speedKmh.value = speed
        _currentLatLng.value = location
        _currentRoute.value = _currentRoute.value + location
    }
}
