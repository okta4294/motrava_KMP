package com.myapp.motrava.presentation.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.motrava.data.remote.dto.TripDetailData
import com.myapp.motrava.data.repository.TripRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripDetailViewModel(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _state = MutableStateFlow<TripDetailState>(TripDetailState.Loading)
    val state: StateFlow<TripDetailState> = _state

    fun fetchTripDetail(tripId: String) {
        viewModelScope.launch {
            _state.value = TripDetailState.Loading
            val result = tripRepository.getTripDetail(tripId)
            result.onSuccess { data ->
                _state.value = TripDetailState.Success(data)
            }.onFailure { error ->
                _state.value = TripDetailState.Error(error.message ?: "Unknown error")
            }
        }
    }

    fun deleteTrip(tripId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId).onSuccess {
                onSuccess()
            }.onFailure { error ->
                _state.value = TripDetailState.Error(error.message ?: "Failed to delete trip")
            }
        }
    }

    sealed class TripDetailState {
        object Loading : TripDetailState()
        data class Success(val trip: TripDetailData) : TripDetailState()
        data class Error(val message: String) : TripDetailState()
    }
}
