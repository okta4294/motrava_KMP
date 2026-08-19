package com.myapp.motrava.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable

data class CreateVehicleRequest(
    @SerialName("vehicle_name")
    val vehicleName: String,
    @SerialName("plate_number")
    val plateNumber: String,
    val brand: String,
    val model: String,
    @SerialName("vehicle_type")
    val vehicleType: String,
    val color: String,
    val year: Int,
    @SerialName("fuel_efficiency_km_per_liter")
    val avgBbm: Double?,
    @SerialName("initial_km")
    val initialKm: Double? = null,
    val photo: String? = null
)

@Serializable

data class UpdateVehicleRequest(
    @SerialName("vehicle_name")
    val vehicleName: String? = null,
    @SerialName("plate_number")
    val plateNumber: String? = null,
    val brand: String? = null,
    val model: String? = null,
    @SerialName("vehicle_type")
    val vehicleType: String? = null,
    val color: String? = null,
    val year: Int? = null,
    @SerialName("fuel_efficiency_km_per_liter")
    val avgBbm: Double? = null,
    @SerialName("initial_km")
    val initialKm: Double? = null,
    val photo: String? = null
)

@Serializable

data class VehicleResponse(
    val success: Boolean,
    val message: String,
    val data: VehicleData?
)

@Serializable

data class VehicleListResponse(
    val success: Boolean,
    val message: String,
    val data: List<VehicleData>?
)

@Serializable

data class VehicleData(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("vehicle_name")
    val vehicleName: String,
    @SerialName("plate_number")
    val plateNumber: String,
    val brand: String,
    val model: String,
    @SerialName("vehicle_type")
    val vehicleType: String,
    val color: String,
    val year: Int,
    val photo: String?,
    @SerialName("fuel_efficiency_km_per_liter")
    val avgBbm: Double?,
    @SerialName("initial_km")
    val initialKm: Double? = null,
    @SerialName("total_distance_km")
    val totalDistanceKm: Double? = null,
    @SerialName("last_recorded_odometer_km")
    val lastRecordedOdometerKm: Double? = null,
    @SerialName("is_default")
    val isDefault: Boolean,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

