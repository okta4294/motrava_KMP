package com.myapp.motrava.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable

data class CreateReminderRequest(
    @SerialName("service_name")
    val serviceName: String,
    @SerialName("interval_km")
    val intervalKm: Int
)

@Serializable

data class AddManualDistanceRequest(
    @SerialName("distance_km")
    val distanceKm: Double,
    val note: String? = null
)

@Serializable

data class ServiceReminderData(
    val id: String = "",
    @SerialName("vehicle_id")
    val vehicleId: String = "",
    @SerialName("service_name")
    val serviceName: String = "",
    @SerialName("interval_km")
    val intervalKm: Int = 0,
    @SerialName("last_service_km")
    val lastServiceKm: Double = 0.0,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable

data class ServiceReminderResponse(
    val success: Boolean,
    val message: String,
    val data: ServiceReminderData?
)

@Serializable

data class ServiceReminderListResponse(
    val success: Boolean,
    val message: String,
    val data: List<ServiceReminderData>?
)

@Serializable

data class ServiceReminderProgressData(
    val id: String,
    @SerialName("vehicle_id")
    val vehicleId: String,
    @SerialName("service_name")
    val serviceName: String,
    @SerialName("interval_km")
    val intervalKm: Int,
    @SerialName("accumulated_km")
    val accumulatedKm: Double,
    @SerialName("progress_percent")
    val progressPercent: Double,
    @SerialName("needs_service")
    val needsService: Boolean,
    @SerialName("last_service_at")
    val lastServiceAt: String?,
    @SerialName("is_active")
    val isActive: Boolean,
    @SerialName("created_at")
    val createdAt: String?,
    @SerialName("updated_at")
    val updatedAt: String?
)

@Serializable

data class ServiceReminderProgressResponse(
    val success: Boolean,
    val message: String,
    val data: ServiceReminderProgressData?
)

@Serializable

data class BaseResponse(
    val success: Boolean,
    val message: String
)

