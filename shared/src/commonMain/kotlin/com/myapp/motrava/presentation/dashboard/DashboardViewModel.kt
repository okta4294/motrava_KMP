package com.myapp.motrava.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.motrava.presentation.dashboard.sendServiceReminderNotification
import com.myapp.motrava.data.remote.dto.TripHistoryData
import com.myapp.motrava.data.remote.dto.VehicleData
import com.myapp.motrava.data.repository.AuthRepository
import com.myapp.motrava.data.repository.TripRepository
import com.myapp.motrava.data.repository.VehicleRepository
import com.myapp.motrava.data.repository.ServiceReminderRepository
import com.myapp.motrava.data.local.TokenManager
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.myapp.motrava.data.remote.dto.ServiceReminderProgressData


class DashboardViewModel(
    private val tripRepository: TripRepository,
    private val vehicleRepository: VehicleRepository,
    private val serviceReminderRepository: ServiceReminderRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private suspend fun fetchUserName(): String {
        try {
            val res = authRepository.getMe()
            if (res.isSuccess) {
                val name = res.getOrNull()?.name
                if (!name.isNullOrBlank()) {
                    tokenManager.userName = name
                    return name
                }
            }
        } catch (_: Exception) {}
        return tokenManager.userName ?: "Motrava Rider"
    }

    private val _dashboardState = MutableStateFlow<DashboardState>(DashboardState.Loading)
    val dashboardState: StateFlow<DashboardState> = _dashboardState

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    val currentPage = MutableStateFlow(0)

    // Reminders that need in-app alert (>= 90% or overdue), dismissed by user
    private val _urgentReminders = MutableStateFlow<List<com.myapp.motrava.data.remote.dto.ServiceReminderProgressData>>(emptyList())
    val urgentReminders: StateFlow<List<com.myapp.motrava.data.remote.dto.ServiceReminderProgressData>> = _urgentReminders

    fun dismissUrgentReminder(reminderId: String) {
        _urgentReminders.value = _urgentReminders.value.filter { it.id != reminderId }
    }

    // ponytail: fetch only requested trip page without resetting state to Loading to prevent scroll position reset
    fun onPageChanged(newPage: Int) {
        if (newPage == currentPage.value || newPage < 0) return
        val currentSuccess = _dashboardState.value as? DashboardState.Success ?: run {
            currentPage.value = newPage
            fetchDashboard()
            return
        }
        currentPage.value = newPage
        viewModelScope.launch {
            val tripsResult = tripRepository.getTripHistory(page = currentPage.value + 1, limit = 10)
            if (tripsResult.isSuccess) {
                val tripsResp = tripsResult.getOrNull()
                val allFetchedTrips = tripsResp?.data ?: emptyList()
                val trips = allFetchedTrips.filter { it.status != "ONGOING" }
                if (currentPage.value > 0 && trips.isEmpty() && allFetchedTrips.isEmpty()) {
                    currentPage.value = 0
                    onPageChanged(0)
                    return@launch
                }
                val totalPages = tripsResp?.resolveTotalPages(allFetchedTrips.size, currentPage.value + 1, 10) ?: currentSuccess.totalPages
                _dashboardState.value = currentSuccess.copy(
                    trips = trips,
                    totalPages = totalPages
                )
            }
        }
    }

    init {
        fetchDashboard()
    }

    fun fetchDashboard() {
        viewModelScope.launch {
            _dashboardState.value = DashboardState.Loading
            val tripsDeferred = async { tripRepository.getTripHistory(page = currentPage.value + 1, limit = 10) }
            val vehiclesDeferred = async { vehicleRepository.getVehicles() }
            val userDeferred = async { fetchUserName() }

            val tripsResult = tripsDeferred.await()
            val vehiclesResult = vehiclesDeferred.await()
            val userName = userDeferred.await()

            if (tripsResult.isSuccess && vehiclesResult.isSuccess) {
                val tripsResp = tripsResult.getOrNull()
                val allFetchedTrips = tripsResp?.data ?: emptyList()
                val trips = allFetchedTrips.filter { it.status != "ONGOING" }
                val vehicles = vehiclesResult.getOrNull() ?: emptyList()
                val vehicleMap = vehicles.associateBy { it.id }

                if (currentPage.value > 0 && trips.isEmpty() && allFetchedTrips.isEmpty()) {
                    currentPage.value = 0
                    fetchDashboard()
                    return@launch
                }

                val vehicleTotalDistKm = vehicles.sumOf { it.totalDistanceKm ?: 0.0 }
                val tripTotalDistKm = allFetchedTrips.sumOf { (it.totalDistance ?: 0.0) / 1000.0 }
                val totalDistanceKm = if (vehicleTotalDistKm > 0.0) vehicleTotalDistKm else tripTotalDistKm

                val avgBbm = if (trips.isNotEmpty()) {
                    val usedVehicles = trips.mapNotNull { trip ->
                        trip.vehicleId?.let { vehicleMap[it] } 
                            ?: vehicleMap.values.find { it.vehicleName.equals(trip.vehicleName, ignoreCase = true) }
                    }.distinct()
                    val bbmList = usedVehicles.mapNotNull { it.avgBbm }
                    if (bbmList.isNotEmpty()) bbmList.average() else 0.0
                } else 0.0
                val maxSpeed = trips.mapNotNull { it.maximumSpeed }.maxOrNull() ?: 0.0
                val completedTrips = trips.filter { it.status == "COMPLETED" }

                val totalTripsFromMeta = tripsResp?.resolveTotalCount(completedTrips.size) ?: completedTrips.size
                val hasOngoing = allFetchedTrips.any { it.status == "ONGOING" }
                val completedTripsCount = if (tripsResp?.total != null || tripsResp?.totalCount != null || tripsResp?.pagination?.total != null || tripsResp?.meta?.total != null) {
                    maxOf(0, totalTripsFromMeta - (if (hasOngoing) 1 else 0))
                } else {
                    completedTrips.size
                }

                val totalPages = tripsResp?.resolveTotalPages(allFetchedTrips.size, currentPage.value + 1, 10) ?: 1

                // Fetch service progress for the selected vehicle
                val reminderProgressList = mutableListOf<ServiceReminderProgressData>()
                val selectedVehicleId = tokenManager.lastSelectedVehicleId ?: vehicles.firstOrNull { it.isDefault }?.id ?: vehicles.firstOrNull()?.id
                
                if (selectedVehicleId != null) {
                    val remindersResult = serviceReminderRepository.getServiceReminders(selectedVehicleId)
                    if (remindersResult.isSuccess) {
                        val reminders = remindersResult.getOrNull() ?: emptyList()
                        val progressDeferred = reminders.map { reminder ->
                            async { serviceReminderRepository.getServiceReminderProgress(selectedVehicleId, reminder.id) }
                        }
                        val progressResults = progressDeferred.awaitAll()
                        reminderProgressList.addAll(progressResults.mapNotNull { it.getOrNull() })
                    }
                }

                _dashboardState.value = DashboardState.Success(
                    trips = trips,
                    vehicleMap = vehicleMap,
                    totalDistanceKm = totalDistanceKm,
                    avgBbm = avgBbm,
                    maxSpeedKmh = maxSpeed,
                    completedTripsCount = completedTripsCount,
                    reminderProgressList = reminderProgressList,
                    totalPages = totalPages,
                    userName = userName
                )

                // Trigger local notification + in-app alert for reminders >= 90%
                val urgent = reminderProgressList.filter { it.progressPercent >= 90.0 }
                _urgentReminders.value = urgent
                urgent.forEach { progress ->
                    sendServiceReminderNotification(
                        serviceName = progress.serviceName,
                        progressPercent = progress.progressPercent.toInt(),
                        isOverdue = progress.needsService
                    )
                }
            } else {
                val errorMsg = tripsResult.exceptionOrNull()?.message ?: vehiclesResult.exceptionOrNull()?.message ?: "Unknown error"
                _dashboardState.value = DashboardState.Error(errorMsg)
            }
        }
    }

    fun resetReminder(vehicleId: String, reminderId: String) {
        viewModelScope.launch {
            serviceReminderRepository.resetServiceReminder(vehicleId, reminderId)
            fetchDashboard()
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _isRefreshing.value = true
            currentPage.value = 0
            val tripsDeferred = async { tripRepository.getTripHistory(page = 1, limit = 10) }
            val vehiclesDeferred = async { vehicleRepository.getVehicles() }
            val userDeferred = async { fetchUserName() }

            val tripsResult = tripsDeferred.await()
            val vehiclesResult = vehiclesDeferred.await()
            val userName = userDeferred.await()

            if (tripsResult.isSuccess && vehiclesResult.isSuccess) {
                val tripsResp = tripsResult.getOrNull()
                val allFetchedTrips = tripsResp?.data ?: emptyList()
                val trips = allFetchedTrips.filter { it.status != "ONGOING" }
                val vehicles = vehiclesResult.getOrNull() ?: emptyList()
                val vehicleMap = vehicles.associateBy { it.id }

                val vehicleTotalDistKm = vehicles.sumOf { it.totalDistanceKm ?: 0.0 }
                val tripTotalDistKm = allFetchedTrips.sumOf { (it.totalDistance ?: 0.0) / 1000.0 }
                val totalDistanceKm = if (vehicleTotalDistKm > 0.0) vehicleTotalDistKm else tripTotalDistKm

                val avgBbm = if (trips.isNotEmpty()) {
                    val usedVehicles = trips.mapNotNull { trip ->
                        trip.vehicleId?.let { vehicleMap[it] } 
                            ?: vehicleMap.values.find { it.vehicleName.equals(trip.vehicleName, ignoreCase = true) }
                    }.distinct()
                    val bbmList = usedVehicles.mapNotNull { it.avgBbm }
                    if (bbmList.isNotEmpty()) bbmList.average() else 0.0
                } else 0.0
                val maxSpeed = trips.mapNotNull { it.maximumSpeed }.maxOrNull() ?: 0.0
                val completedTrips = trips.filter { it.status == "COMPLETED" }

                val totalTripsFromMeta = tripsResp?.resolveTotalCount(completedTrips.size) ?: completedTrips.size
                val hasOngoing = allFetchedTrips.any { it.status == "ONGOING" }
                val completedTripsCount = if (tripsResp?.total != null || tripsResp?.totalCount != null || tripsResp?.pagination?.total != null || tripsResp?.meta?.total != null) {
                    maxOf(0, totalTripsFromMeta - (if (hasOngoing) 1 else 0))
                } else {
                    completedTrips.size
                }

                val totalPages = tripsResp?.resolveTotalPages(allFetchedTrips.size, 1, 10) ?: 1

                // Fetch service progress for the selected vehicle
                val reminderProgressList = mutableListOf<ServiceReminderProgressData>()
                val selectedVehicleId = tokenManager.lastSelectedVehicleId ?: vehicles.firstOrNull { it.isDefault }?.id ?: vehicles.firstOrNull()?.id
                
                if (selectedVehicleId != null) {
                    val remindersResult = serviceReminderRepository.getServiceReminders(selectedVehicleId)
                    if (remindersResult.isSuccess) {
                        val reminders = remindersResult.getOrNull() ?: emptyList()
                        val progressDeferred = reminders.map { reminder ->
                            async { serviceReminderRepository.getServiceReminderProgress(selectedVehicleId, reminder.id) }
                        }
                        val progressResults = progressDeferred.awaitAll()
                        reminderProgressList.addAll(progressResults.mapNotNull { it.getOrNull() })
                    }
                }

                _dashboardState.value = DashboardState.Success(
                    trips = trips,
                    vehicleMap = vehicleMap,
                    totalDistanceKm = totalDistanceKm,
                    avgBbm = avgBbm,
                    maxSpeedKmh = maxSpeed,
                    completedTripsCount = completedTripsCount,
                    reminderProgressList = reminderProgressList,
                    totalPages = totalPages,
                    userName = userName
                )
            } else {
                val errorMsg = tripsResult.exceptionOrNull()?.message ?: vehiclesResult.exceptionOrNull()?.message ?: "Unknown error"
                _dashboardState.value = DashboardState.Error(errorMsg)
            }
            _isRefreshing.value = false
        }
    }

    sealed class DashboardState {
        object Loading : DashboardState()
        data class Success(
            val trips: List<TripHistoryData>,
            val vehicleMap: Map<String, VehicleData>,
            val totalDistanceKm: Double,
            val avgBbm: Double,
            val maxSpeedKmh: Double,
            val completedTripsCount: Int,
            val reminderProgressList: List<ServiceReminderProgressData> = emptyList(),
            val totalPages: Int = 1,
            val userName: String = "Motrava Rider"
        ) : DashboardState()
        data class Error(val message: String) : DashboardState()
    }
}

