package com.myapp.motrava.presentation.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.motrava.data.remote.dto.CreateReminderRequest
import com.myapp.motrava.data.remote.dto.ServiceReminderData
import com.myapp.motrava.data.remote.dto.VehicleData
import com.myapp.motrava.data.repository.VehicleRepository
import com.myapp.motrava.data.repository.ServiceReminderRepository
import com.myapp.motrava.data.remote.dto.AddManualDistanceRequest
import com.myapp.motrava.data.local.TokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServiceReminderUiState(
    val isLoading: Boolean = false,
    val vehicles: List<VehicleData> = emptyList(),
    val selectedVehicle: VehicleData? = null,
    val reminders: List<ServiceReminderData> = emptyList(),
    val error: String? = null
)

class ServiceReminderViewModel(
    private val vehicleRepository: VehicleRepository,
    private val serviceReminderRepository: ServiceReminderRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServiceReminderUiState())
    val uiState: StateFlow<ServiceReminderUiState> = _uiState.asStateFlow()

    init {
        fetchVehicles()
    }

    private fun fetchVehicles() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = vehicleRepository.getVehicles()
            result.onSuccess { vehicles ->
                val lastId = tokenManager.lastSelectedVehicleId
                val selected = vehicles.find { it.id == lastId } ?: vehicles.firstOrNull { it.isDefault } ?: vehicles.firstOrNull()
                _uiState.update { 
                    it.copy(
                        vehicles = vehicles,
                        selectedVehicle = selected,
                        isLoading = false
                    ) 
                }
                if (selected != null) {
                    fetchReminders(selected.id)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun selectVehicle(vehicle: VehicleData) {
        tokenManager.lastSelectedVehicleId = vehicle.id
        _uiState.update { it.copy(selectedVehicle = vehicle) }
        fetchReminders(vehicle.id)
    }

    fun fetchReminders(vehicleId: String? = _uiState.value.selectedVehicle?.id) {
        if (vehicleId == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = serviceReminderRepository.getServiceReminders(vehicleId)
            result.onSuccess { reminders ->
                _uiState.update { it.copy(reminders = reminders, isLoading = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun addReminder(serviceName: String, intervalKm: Int) {
        val vehicleId = _uiState.value.selectedVehicle?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val request = CreateReminderRequest(serviceName, intervalKm)
            val result = serviceReminderRepository.createServiceReminder(vehicleId, request)
            result.onSuccess {
                fetchReminders(vehicleId) // Refresh list
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun deleteReminder(reminderId: String) {
        val vehicleId = _uiState.value.selectedVehicle?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = serviceReminderRepository.deleteServiceReminder(vehicleId, reminderId)
            result.onSuccess {
                fetchReminders(vehicleId)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun resetReminder(reminderId: String) {
        val vehicleId = _uiState.value.selectedVehicle?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = serviceReminderRepository.resetServiceReminder(vehicleId, reminderId)
            result.onSuccess {
                fetchReminders(vehicleId)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun updateReminder(reminderId: String, serviceName: String, intervalKm: Int) {
        val vehicleId = _uiState.value.selectedVehicle?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val request = CreateReminderRequest(serviceName, intervalKm)
            val result = serviceReminderRepository.updateServiceReminder(vehicleId, reminderId, request)
            result.onSuccess {
                fetchReminders(vehicleId)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }

    fun addManualDistance(reminderId: String, distanceKm: Double, note: String) {
        val vehicleId = _uiState.value.selectedVehicle?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val request = AddManualDistanceRequest(distanceKm, note)
            val result = serviceReminderRepository.addManualDistance(vehicleId, reminderId, request)
            result.onSuccess {
                fetchReminders(vehicleId)
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        }
    }
}
