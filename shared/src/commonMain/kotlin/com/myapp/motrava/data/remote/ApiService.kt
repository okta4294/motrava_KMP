package com.myapp.motrava.data.remote

import com.myapp.motrava.data.local.TokenManager
import com.myapp.motrava.data.remote.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.sync.withLock

class ErrorBodyWrapper(private val content: String) {
    fun string(): String = content
}

data class Response<T>(
    val code: Int,
    val body: T?,
    val errorBody: String? = null
) {
    val isSuccessful: Boolean get() = code in 200..299
    fun body(): T? = body
    fun code(): Int = code
    fun message(): String = errorBody ?: ""
    fun errorBody(): ErrorBodyWrapper? = errorBody?.let { ErrorBodyWrapper(it) }
}

class ApiService(
    private val client: HttpClient,
    private val tokenManager: TokenManager
) {

    private suspend inline fun <reified T> safeRequest(
        block: () -> HttpResponse
    ): Response<T> {
        return try {
            val response = block()
            if (response.status.isSuccess()) {
                Response(response.status.value, response.body<T>())
            } else if (response.status.value == 401) {
                // Try refresh token, then retry once
                val refreshed = tryRefreshToken()
                if (refreshed) {
                    val retryResponse = block()
                    if (retryResponse.status.isSuccess()) {
                        Response(retryResponse.status.value, retryResponse.body<T>())
                    } else {
                        Response(retryResponse.status.value, null, retryResponse.bodyAsText())
                    }
                } else {
                    Response(response.status.value, null, response.bodyAsText())
                }
            } else {
                Response(response.status.value, null, response.bodyAsText())
            }
        } catch (e: Exception) {
            println("API_ERROR: ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            Response(0, null, "${e::class.simpleName}: ${e.message}")
        }
    }

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    private suspend fun tryRefreshToken(): Boolean {
        refreshMutex.withLock {
            val currentRefreshToken = tokenManager.refreshToken
            if (currentRefreshToken.isNullOrEmpty()) {
                tokenManager.clearTokens()
                return false
            }
            return try {
                val response = client.post("api/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(currentRefreshToken))
                }
                if (response.status.isSuccess()) {
                    val authResponse = response.body<AuthResponse>()
                    val data = authResponse.data
                    val newAccess = data?.accessToken
                    if (!newAccess.isNullOrEmpty()) {
                        tokenManager.saveTokens(newAccess, data.refreshToken ?: currentRefreshToken)
                        true
                    } else {
                        tokenManager.clearTokens()
                        false
                    }
                } else {
                    if (response.status.value == 401 || response.status.value == 403 || response.status.value == 400) {
                        tokenManager.clearTokens()
                    }
                    false
                }
            } catch (e: Exception) {
                // Network error (timeout, no connection, etc) - DO NOT clear tokens
                false
            }
        }
    }

    suspend fun googleLogin(request: GoogleLoginRequest): Response<AuthResponse> = safeRequest {
        client.post("api/auth/google/mobile") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun registerDevice(request: RegisterDeviceRequest): Response<BaseResponse> = safeRequest {
        client.post("api/devices/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun register(request: RegisterRequest): Response<AuthResponse> = safeRequest {
        client.post("api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun login(request: LoginRequest): Response<AuthResponse> = safeRequest {
        client.post("api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getMe(): Response<UserResponse> = safeRequest {
        client.get("api/auth/me")
    }

    suspend fun refresh(request: RefreshRequest): Response<AuthResponse> = safeRequest {
        client.post("api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun refreshSync(request: RefreshRequest): Response<AuthResponse> = refresh(request)

    suspend fun startTrip(request: StartTripRequest): Response<StartTripResponse> = safeRequest {
        client.post("api/trips/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun endTrip(tripId: String, totalDistance: Double? = null): Response<String> = safeRequest {
        client.post("api/trips/$tripId/end") {
            if (totalDistance != null) {
                contentType(ContentType.Application.Json)
                setBody(WsEndTripMessage(tripId = tripId, totalDistance = totalDistance))
            }
        }
    }

    suspend fun getTripHistory(page: Int = 1, limit: Int = 10): Response<TripHistoryResponse> = safeRequest {
        client.get("api/trips") {
            parameter("page", page)
            parameter("limit", limit)
        }
    }

    suspend fun getTripDetail(tripId: String): Response<TripDetailResponse> = safeRequest {
        client.get("api/trips/$tripId")
    }

    suspend fun deleteTrip(tripId: String): Response<String> = safeRequest {
        client.delete("api/trips/$tripId")
    }

    suspend fun batchUploadLocations(
        tripId: String,
        locations: List<WsLocationMessage>
    ): Response<String> = safeRequest {
        client.post("api/trips/$tripId/locations/batch") {
            contentType(ContentType.Application.Json)
            setBody(locations)
        }
    }

    suspend fun getVehicles(): Response<VehicleListResponse> = safeRequest {
        client.get("api/vehicles")
    }

    suspend fun createVehicle(request: CreateVehicleRequest): Response<VehicleResponse> = safeRequest {
        client.post("api/vehicles") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateVehicle(id: String, request: UpdateVehicleRequest): Response<VehicleResponse> = safeRequest {
        client.put("api/vehicles/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteVehicle(id: String): Response<BaseResponse> = safeRequest {
        client.delete("api/vehicles/$id")
    }

    suspend fun getServiceReminders(vehicleId: String): Response<ServiceReminderListResponse> = safeRequest {
        client.get("api/vehicles/$vehicleId/service-reminders")
    }

    suspend fun createServiceReminder(vehicleId: String, request: CreateReminderRequest): Response<ServiceReminderResponse> = safeRequest {
        client.post("api/vehicles/$vehicleId/service-reminders") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun updateServiceReminder(vehicleId: String, reminderId: String, request: CreateReminderRequest): Response<ServiceReminderResponse> = safeRequest {
        client.put("api/vehicles/$vehicleId/service-reminders/$reminderId") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteServiceReminder(vehicleId: String, reminderId: String): Response<BaseResponse> = safeRequest {
        client.delete("api/vehicles/$vehicleId/service-reminders/$reminderId")
    }

    suspend fun getServiceReminderProgress(vehicleId: String, reminderId: String): Response<ServiceReminderProgressResponse> = safeRequest {
        client.get("api/vehicles/$vehicleId/service-reminders/$reminderId/progress")
    }

    suspend fun resetServiceReminder(vehicleId: String, reminderId: String): Response<BaseResponse> = safeRequest {
        client.post("api/vehicles/$vehicleId/service-reminders/$reminderId/reset")
    }

    suspend fun addManualDistance(vehicleId: String, reminderId: String, request: AddManualDistanceRequest): Response<BaseResponse> = safeRequest {
        client.post("api/vehicles/$vehicleId/service-reminders/$reminderId/manual-distance") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
