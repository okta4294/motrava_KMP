package com.myapp.motrava.presentation.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.motrava.domain.manager.TripSessionManager
import com.myapp.motrava.data.repository.TripRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripViewModel(
    private val tripRepository: TripRepository,
    val tripSessionManager: TripSessionManager,
    private val settings: Settings
) : ViewModel() {

    private val _tripState = MutableStateFlow<TripState>(TripState.Idle)
    val tripState: StateFlow<TripState> = _tripState

    init {
        // Restore active trip from prefs in case the app was killed and reopened
        if (tripSessionManager.activeTripId.value == null) {
            val savedTrip = settings.getStringOrNull("active_trip_id")
            val savedVehicle = settings.getStringOrNull("active_vehicle_id")
            if (savedTrip != null) {
                tripSessionManager.setTripActive(savedTrip, savedVehicle)
            }
        }

        viewModelScope.launch {
            tripSessionManager.activeTripId.collect { activeId ->
                if (activeId != null) {
                    _tripState.value = TripState.Ongoing(activeId)
                } else if (_tripState.value is TripState.Ongoing) {
                    _tripState.value = TripState.Idle
                }
            }
        }
    }

    fun startTrip(vehicleId: String) {
        viewModelScope.launch {
            _tripState.value = TripState.Starting
            val result = tripRepository.startTrip(vehicleId)
            result.onSuccess { response ->
                if (response.success && response.data != null) {
                    val tripId = response.data.id
                    tripSessionManager.setTripActive(tripId, vehicleId)
                    _tripState.value = TripState.Ongoing(tripId)
                    
                    settings.putString("active_trip_id", tripId)
                    settings.putString("active_vehicle_id", vehicleId)
                    
                    // Start Foreground Service (KMP abstracted)
                    startTripService(tripId, vehicleId)
                } else {
                    _tripState.value = TripState.Error("Failed to start trip: ${response.message}")
                }
            }.onFailure {
                _tripState.value = TripState.Error(it.message ?: "Unknown error")
            }
        }
    }

    fun endTrip() {
        viewModelScope.launch {
            try {
                tripSessionManager.setTripInactive()
                _tripState.value = TripState.Idle
                settings.remove("active_trip_id")
                settings.remove("active_vehicle_id")
                stopTripService()
            } catch (e: Exception) {
                println("endTrip error: ${e.message}")
                _tripState.value = TripState.Idle
            }
        }
    }


    sealed class TripState {
        object Idle : TripState()
        object Starting : TripState()
        data class Ongoing(val tripId: String) : TripState()
        data class Error(val message: String) : TripState()
    }
}
