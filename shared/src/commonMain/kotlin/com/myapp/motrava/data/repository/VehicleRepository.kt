package com.myapp.motrava.data.repository

import com.myapp.motrava.data.remote.ApiService
import com.myapp.motrava.data.remote.dto.CreateVehicleRequest
import com.myapp.motrava.data.remote.dto.UpdateVehicleRequest
import com.myapp.motrava.data.remote.dto.VehicleData
class VehicleRepository(
    private val apiService: ApiService
) {

    suspend fun getVehicles(): Result<List<VehicleData>> {
        return try {
            val response = apiService.getVehicles()
            if (response.isSuccessful) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty vehicles list body"))
            } else {
                Result.failure(Exception("Error fetching vehicles: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createVehicle(request: CreateVehicleRequest): Result<VehicleData> {
        return try {
            val response = apiService.createVehicle(request)
            if (response.isSuccessful) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty create vehicle body"))
            } else {
                Result.failure(Exception("Error creating vehicle: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateVehicle(id: String, request: UpdateVehicleRequest): Result<VehicleData> {
        return try {
            val response = apiService.updateVehicle(id, request)
            if (response.isSuccessful) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty update vehicle body"))
            } else {
                Result.failure(Exception("Error updating vehicle: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVehicle(id: String): Result<Unit> {
        return try {
            val response = apiService.deleteVehicle(id)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Error deleting vehicle: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
