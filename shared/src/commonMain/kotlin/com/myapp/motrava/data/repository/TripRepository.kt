package com.myapp.motrava.data.repository

import com.myapp.motrava.data.remote.ApiService
import com.myapp.motrava.data.remote.dto.StartTripRequest
import com.myapp.motrava.data.remote.dto.StartTripResponse
import com.myapp.motrava.data.remote.dto.TripHistoryData
import com.myapp.motrava.data.remote.dto.TripHistoryResponse
import com.myapp.motrava.data.remote.dto.TripDetailData
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

    suspend fun getTripHistory(page: Int = 1, limit: Int = 10): Result<TripHistoryResponse> {
        return try {
            val response = apiService.getTripHistory(page, limit)
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
}
