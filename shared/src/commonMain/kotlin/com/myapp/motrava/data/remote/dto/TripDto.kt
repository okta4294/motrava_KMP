package com.myapp.motrava.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable

data class StartTripRequest(
    @SerialName("vehicle_id")
    val vehicleId: String
)

@Serializable

data class StartTripResponse(
    val success: Boolean,
    val message: String,
    val data: TripData?
)

@Serializable

data class TripData(
    val id: String,
    val status: String,
    @SerialName("start_time")
    val startTime: String,
    @SerialName("vehicle_id")
    val vehicleId: String,
    @SerialName("user_id")
    val userId: String
)

@Serializable

data class PaginationMeta(
    @SerialName("total") val total: Int?,
    @SerialName("total_count") val totalCount: Int?,
    @SerialName("total_pages") val totalPages: Int?,
    @SerialName("current_page") val currentPage: Int?,
    @SerialName("page") val page: Int?,
    @SerialName("limit") val limit: Int?,
    @SerialName("per_page") val perPage: Int?
)

@Serializable

data class TripHistoryResponse(
    val success: Boolean,
    val message: String?,
    val data: List<TripHistoryData>?,
    @SerialName("pagination") val pagination: PaginationMeta? = null,
    @SerialName("meta") val meta: PaginationMeta? = null,
    @SerialName("total") val total: Int? = null,
    @SerialName("total_count") val totalCount: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("page") val page: Int? = null,
    @SerialName("limit") val limit: Int? = null,
    @SerialName("per_page") val perPage: Int? = null
) {
    fun resolveTotalPages(itemCount: Int, currentReqPage: Int, reqLimit: Int): Int {
        val tp = totalPages ?: pagination?.totalPages ?: meta?.totalPages
        if (tp != null && tp > 0) return tp
        
        val tot = total ?: totalCount ?: pagination?.total ?: pagination?.totalCount ?: meta?.total ?: meta?.totalCount
        if (tot != null && tot >= 0) {
            return (tot + reqLimit - 1) / reqLimit
        }
        
        return if (itemCount >= reqLimit) currentReqPage + 1 else currentReqPage
    }

    fun resolveTotalCount(fallbackCount: Int): Int {
        val tot = total ?: totalCount ?: pagination?.total ?: pagination?.totalCount ?: meta?.total ?: meta?.totalCount
        return tot ?: fallbackCount
    }
}

@Serializable

data class TripHistoryData(
    val id: String,
    @SerialName("user_id")
    val userId: String?,
    @SerialName("vehicle_id")
    val vehicleId: String?,
    @SerialName("vehicle_name")
    val vehicleName: String?,
    @SerialName("plate_number")
    val plateNumber: String?,
    @SerialName("start_time")
    val startTime: String?,
    @SerialName("end_time")
    val endTime: String?,
    @SerialName("total_distance")
    val totalDistance: Double?,
    val duration: Long?,
    @SerialName("moving_time")
    val movingTime: Long?,
    @SerialName("idle_time")
    val idleTime: Long?,
    @SerialName("average_speed")
    val averageSpeed: Double?,
    @SerialName("maximum_speed")
    val maximumSpeed: Double?,
    val status: String?,
    @SerialName("created_at")
    val createdAt: String?,
    @SerialName("updated_at")
    val updatedAt: String?
)

@Serializable

data class TripDetailResponse(
    val success: Boolean,
    val message: String?,
    val data: TripDetailData?
)

@Serializable
data class TripDetailData(
    val id: String,
    @SerialName("user_id")
    val userId: String?,
    @SerialName("vehicle_id")
    val vehicleId: String?,
    @SerialName("vehicle_name")
    val vehicleName: String?,
    @SerialName("plate_number")
    val plateNumber: String?,
    @SerialName("vehicle_type")
    val vehicleType: String?,
    @SerialName("start_time")
    val startTime: String?,
    @SerialName("end_time")
    val endTime: String?,
    @SerialName("start_latitude")
    val startLatitude: Double?,
    @SerialName("start_longitude")
    val startLongitude: Double?,
    @SerialName("end_latitude")
    val endLatitude: Double?,
    @SerialName("end_longitude")
    val endLongitude: Double?,
    @SerialName("total_distance")
    val totalDistance: Double?,
    val duration: Long?,
    @SerialName("moving_time")
    val movingTime: Long?,
    @SerialName("idle_time")
    val idleTime: Long?,
    @SerialName("average_speed")
    val averageSpeed: Double?,
    @SerialName("maximum_speed")
    val maximumSpeed: Double?,
    @SerialName("fuel_consumed")
    val fuelConsumed: Double?,
    val status: String?,
    @SerialName("route")
    val route: List<RoutePoint>?
)

@Serializable
data class RoutePoint(
    @SerialName("latitude")
    val latitude: Double,
    @SerialName("longitude")
    val longitude: Double,
    @SerialName("speed")
    val speed: Double?,
    @SerialName("heading")
    val heading: Double?,
    @SerialName("accuracy")
    val accuracy: Double?,
    @SerialName("altitude")
    val altitude: Double?,
    @SerialName("battery")
    val battery: Int?,
    @SerialName("recorded_at")
    val recordedAt: String?
)

@Serializable
data class WsLocationMessage(
    @SerialName("trip_id") val tripId: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("speed") val speed: Float,
    @SerialName("heading") val heading: Float,
    @SerialName("accuracy") val accuracy: Float,
    @SerialName("altitude") val altitude: Double,
    @SerialName("battery") val battery: Int,
    @SerialName("timestamp") val timestamp: String
)

@Serializable
data class WsEndTripMessage(
    @SerialName("trip_id") val tripId: String,
    @SerialName("total_distance") val totalDistance: Double? = null
)
