package com.myapp.motrava.data.repository

import com.myapp.motrava.data.remote.ApiService
import com.myapp.motrava.data.remote.dto.StartTripRequest
import com.myapp.motrava.data.remote.dto.StartTripResponse
import com.myapp.motrava.data.remote.dto.TripHistoryData
import com.myapp.motrava.data.remote.dto.TripHistoryResponse
import com.myapp.motrava.data.remote.dto.TripDetailData
import com.myapp.motrava.domain.model.TripRecap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class TripRepository(
    private val apiService: ApiService
) {
    suspend fun startTrip(vehicleId: String): Result<StartTripResponse> {
        return try {
            var response = apiService.startTrip(StartTripRequest(vehicleId))
            
            // Auto-resolve 409 Conflict (active trip exists)
            if (response.code() == 409) {
                val historyResp = apiService.getTripHistory()
                if (historyResp.isSuccessful) {
                    val activeTrip = historyResp.body()?.data?.find { it.status == "ONGOING" }
                    if (activeTrip != null) {
                        apiService.endTrip(activeTrip.id)
                        // Retry start
                        response = apiService.startTrip(StartTripRequest(vehicleId))
                    }
                }
            }

            if (response.isSuccessful) {
                response.body()?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty body"))
            } else {
                Result.failure(Exception("Error starting trip: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTripHistory(page: Int = 1, limit: Int = 10, startDate: String? = null, endDate: String? = null): Result<TripHistoryResponse> {
        return try {
            val response = apiService.getTripHistory(page, limit, startDate, endDate)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error fetching trips: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getTripDetail(tripId: String): Result<TripDetailData> {
        return try {
            val response = apiService.getTripDetail(tripId)
            if (response.isSuccessful) {
                val data = response.body()?.data
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(Exception("Empty trip detail data"))
                }
            } else {
                Result.failure(Exception("Failed to get trip details: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ponytail: simple delete wrapper
    suspend fun deleteTrip(tripId: String): Result<Unit> {
        return try {
            val response = apiService.deleteTrip(tripId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Error deleting trip: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTripRecap(periodName: String, startDate: String, endDate: String, vehicleId: String? = null, vehicleName: String? = null): Result<TripRecap> = coroutineScope {
        try {
            // 1. Fetch all trips in this period (assuming limit 100 is enough for a month)
            val historyResult = getTripHistory(page = 1, limit = 100, startDate = startDate, endDate = endDate)
            if (historyResult.isFailure) {
                return@coroutineScope Result.failure(historyResult.exceptionOrNull() ?: Exception("Failed to fetch history for recap"))
            }

            var trips = historyResult.getOrNull()?.data ?: emptyList()
            
            // Filter by date locally in case backend ignores start_date and end_date
            try {
                val startInstant = kotlinx.datetime.Instant.parse(startDate)
                val endInstant = kotlinx.datetime.Instant.parse(endDate)
                trips = trips.filter { trip ->
                    val timeStr = trip.startTime ?: trip.createdAt
                    if (timeStr != null) {
                        try {
                            var normalizedTimeStr = timeStr.replace(" ", "T")
                            if (!normalizedTimeStr.endsWith("Z") && !normalizedTimeStr.contains("+") && !normalizedTimeStr.contains(Regex("""-\d\d:\d\d"""))) {
                                normalizedTimeStr += "Z"
                            }
                            val tripInstant = kotlinx.datetime.Instant.parse(normalizedTimeStr)
                            tripInstant >= startInstant && tripInstant < endInstant
                        } catch (e: Exception) {
                            false
                        }
                    } else false
                }
            } catch (e: Exception) {
                // Ignore if boundary parsing fails
            }
            
            if (vehicleId != null) {
                val cleanVehicleId = vehicleId.trim()
                val cleanVehicleName = vehicleName?.trim()
                trips = trips.filter { 
                    (it.vehicleId != null && it.vehicleId.trim() == cleanVehicleId) || 
                    (cleanVehicleName != null && it.vehicleName?.trim().equals(cleanVehicleName, ignoreCase = true))
                }
            }
            
            if (trips.isEmpty()) {
                return@coroutineScope Result.success(
                    TripRecap(
                        periodName = periodName,
                        totalDistance = 0.0,
                        totalDuration = 0L,
                        totalTrips = 0,
                        maxSpeed = 0.0,
                        averageSpeed = 0.0,
                        routes = emptyList()
                    )
                )
            }

            var totalDistance = 0.0
            var totalDuration = 0L
            var maxSpeed = 0.0
            var sumAverageSpeed = 0.0

            trips.forEach { trip ->
                totalDistance += trip.totalDistance ?: 0.0
                totalDuration += trip.duration ?: 0L
                val tripMax = trip.maximumSpeed ?: 0.0
                if (tripMax > maxSpeed) maxSpeed = tripMax
                sumAverageSpeed += trip.averageSpeed ?: 0.0
            }

            val avgSpeed = if (trips.isNotEmpty()) sumAverageSpeed / trips.size else 0.0

            // 2. Fetch routes concurrently
            val routesDeferred = trips.map { trip ->
                async {
                    val detail = getTripDetail(trip.id).getOrNull()
                    detail?.route ?: emptyList()
                }
            }
            val routes = routesDeferred.awaitAll().filter { it.isNotEmpty() }

            Result.success(
                TripRecap(
                    periodName = periodName,
                    totalDistance = totalDistance,
                    totalDuration = totalDuration,
                    totalTrips = trips.size,
                    maxSpeed = maxSpeed,
                    averageSpeed = avgSpeed,
                    routes = routes
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
