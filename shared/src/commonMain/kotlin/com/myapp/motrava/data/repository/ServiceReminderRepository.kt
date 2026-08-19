package com.myapp.motrava.data.repository

import com.myapp.motrava.data.remote.ApiService
import com.myapp.motrava.data.remote.dto.*
class ServiceReminderRepository(
    private val apiService: ApiService
) {

    suspend fun getServiceReminders(vehicleId: String): Result<List<ServiceReminderData>> {
        return try {
            val response = apiService.getServiceReminders(vehicleId)
            if (response.isSuccessful) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty service reminders list body"))
            } else {
                Result.failure(Exception("Error fetching service reminders: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createServiceReminder(
        vehicleId: String,
        request: CreateReminderRequest
    ): Result<ServiceReminderData> {
        return try {
            val response = apiService.createServiceReminder(vehicleId, request)
            if (response.isSuccessful) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty create reminder body"))
            } else {
                Result.failure(Exception("Error creating reminder: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateServiceReminder(
        vehicleId: String,
        reminderId: String,
        request: CreateReminderRequest
    ): Result<ServiceReminderData> {
        return try {
            val response = apiService.updateServiceReminder(vehicleId, reminderId, request)
            if (response.isSuccessful) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty update reminder body"))
            } else {
                Result.failure(Exception("Error updating reminder: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteServiceReminder(
        vehicleId: String,
        reminderId: String
    ): Result<Unit> {
        return try {
            val response = apiService.deleteServiceReminder(vehicleId, reminderId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error deleting reminder: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getServiceReminderProgress(
        vehicleId: String,
        reminderId: String
    ): Result<ServiceReminderProgressData> {
        return try {
            val response = apiService.getServiceReminderProgress(vehicleId, reminderId)
            if (response.isSuccessful) {
                response.body()?.data?.let {
                    Result.success(it)
                } ?: Result.failure(Exception("Empty progress body"))
            } else {
                Result.failure(Exception("Error fetching progress: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetServiceReminder(
        vehicleId: String,
        reminderId: String
    ): Result<Unit> {
        return try {
            val response = apiService.resetServiceReminder(vehicleId, reminderId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Error resetting reminder: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addManualDistance(
        vehicleId: String,
        reminderId: String,
        request: AddManualDistanceRequest
    ): Result<Unit> {
        return try {
            val response = apiService.addManualDistance(vehicleId, reminderId, request)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Result.failure(Exception(errorBody))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
