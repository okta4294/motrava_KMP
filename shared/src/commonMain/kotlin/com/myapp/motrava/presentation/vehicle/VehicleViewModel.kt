package com.myapp.motrava.presentation.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.motrava.data.remote.dto.CreateVehicleRequest
import com.myapp.motrava.data.remote.dto.UpdateVehicleRequest
import com.myapp.motrava.data.remote.dto.VehicleData
import com.myapp.motrava.data.repository.VehicleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class VehicleViewModel(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _vehiclesState = MutableStateFlow<VehiclesState>(VehiclesState.Idle)
    val vehiclesState: StateFlow<VehiclesState> = _vehiclesState

    private val _createVehicleState = MutableStateFlow<CreateVehicleState>(CreateVehicleState.Idle)
    val createVehicleState: StateFlow<CreateVehicleState> = _createVehicleState

    fun fetchVehicles() {
        viewModelScope.launch {
            _vehiclesState.value = VehiclesState.Loading
            val vehiclesResult = vehicleRepository.getVehicles()

            vehiclesResult.onSuccess { vehicles ->
                val distances = mutableMapOf<String, Double>()
                vehicles.forEach { vehicle ->
                    distances[vehicle.id] = vehicle.lastRecordedOdometerKm ?: vehicle.initialKm ?: 0.0
                }
                _vehiclesState.value = VehiclesState.Success(vehicles, distances)
            }.onFailure {
                _vehiclesState.value = VehiclesState.Error(it.message ?: "Failed to fetch vehicles")
            }
        }
    }

    fun createVehicle(
        name: String, plate: String, brand: String, model: String,
        type: String, color: String, year: Int, avgBbm: Double?, initialKm: Double?
    ) {
        viewModelScope.launch {
            _createVehicleState.value = CreateVehicleState.Loading
            val request = CreateVehicleRequest(
                vehicleName = name,
                plateNumber = plate,
                brand = brand,
                model = model,
                vehicleType = type,
                color = color,
                year = year,
                avgBbm = avgBbm,
                initialKm = initialKm
            )
            val result = vehicleRepository.createVehicle(request)
            result.onSuccess {
                _createVehicleState.value = CreateVehicleState.Success
                fetchVehicles()
            }.onFailure {
                _createVehicleState.value = CreateVehicleState.Error(it.message ?: "Failed to create vehicle")
            }
        }
    }

    fun updateVehicle(
        id: String, name: String, plate: String, brand: String, model: String,
        type: String, color: String, year: Int, avgBbm: Double?, initialKm: Double?
    ) {
        viewModelScope.launch {
            _createVehicleState.value = CreateVehicleState.Loading
            val request = UpdateVehicleRequest(
                vehicleName = name,
                plateNumber = plate,
                brand = brand,
                model = model,
                vehicleType = type,
                color = color,
                year = year,
                avgBbm = avgBbm,
                initialKm = initialKm
            )
            val result = vehicleRepository.updateVehicle(id, request)
            result.onSuccess {
                _createVehicleState.value = CreateVehicleState.Success
                fetchVehicles()
            }.onFailure {
                _createVehicleState.value = CreateVehicleState.Error(it.message ?: "Failed to update vehicle")
            }
        }
    }

    fun deleteVehicle(id: String) {
        viewModelScope.launch {
            vehicleRepository.deleteVehicle(id)
            fetchVehicles()
        }
    }

    fun resetCreateState() {
        _createVehicleState.value = CreateVehicleState.Idle
    }

    sealed class VehiclesState {
        object Idle : VehiclesState()
        object Loading : VehiclesState()
        data class Success(val vehicles: List<VehicleData>, val distances: Map<String, Double> = emptyMap()) : VehiclesState()
        data class Error(val message: String) : VehiclesState()
    }

    sealed class CreateVehicleState {
        object Idle : CreateVehicleState()
        object Loading : CreateVehicleState()
        object Success : CreateVehicleState()
        data class Error(val message: String) : CreateVehicleState()
    }
}
