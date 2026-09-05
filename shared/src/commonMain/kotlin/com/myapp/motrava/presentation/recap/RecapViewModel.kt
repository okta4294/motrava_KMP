package com.myapp.motrava.presentation.recap

import com.myapp.motrava.data.remote.dto.VehicleData
import com.myapp.motrava.data.repository.TripRepository
import com.myapp.motrava.data.repository.VehicleRepository
import com.myapp.motrava.domain.model.TripRecap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

sealed class RecapUiState {
    object Idle : RecapUiState()
    object Loading : RecapUiState()
    data class Success(val recap: TripRecap) : RecapUiState()
    data class Error(val message: String) : RecapUiState()
}

class RecapViewModel(
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<RecapUiState>(RecapUiState.Idle)
    val uiState: StateFlow<RecapUiState> = _uiState.asStateFlow()

    private val _vehicles = MutableStateFlow<List<VehicleData>>(emptyList())
    val vehicles: StateFlow<List<VehicleData>> = _vehicles.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    init {
        viewModelScope.launch {
            val result = vehicleRepository.getVehicles()
            if (result.isSuccess) {
                _vehicles.value = result.getOrNull() ?: emptyList()
            }
        }
    }

    fun selectVehicle(vehicleId: String?) {
        _selectedVehicleId.value = vehicleId
    }

    fun loadRecap(periodName: String, startDate: String, endDate: String) {
        viewModelScope.launch {
            _uiState.value = RecapUiState.Loading
            if (_vehicles.value.isEmpty()) {
                val result = vehicleRepository.getVehicles()
                if (result.isSuccess) {
                    _vehicles.value = result.getOrNull() ?: emptyList()
                }
            }
            val vehicleId = _selectedVehicleId.value
            val vehicleName = _vehicles.value.find { it.id == vehicleId }?.vehicleName
            
            val result = tripRepository.getTripRecap(periodName, startDate, endDate, vehicleId, vehicleName)
            if (result.isSuccess) {
                _uiState.value = RecapUiState.Success(result.getOrThrow())
            } else {
                _uiState.value = RecapUiState.Error(result.exceptionOrNull()?.message ?: "Failed to load recap")
            }
        }
    }
}
