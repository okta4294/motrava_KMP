package com.myapp.motrava.data.repository

import com.myapp.motrava.data.local.TokenManager
import com.myapp.motrava.data.remote.ApiService
import com.myapp.motrava.data.remote.dto.GoogleLoginRequest
import com.myapp.motrava.data.remote.dto.LoginRequest
import com.myapp.motrava.data.remote.dto.RegisterRequest
import com.myapp.motrava.domain.model.AuthResult
import com.myapp.motrava.domain.model.User

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    suspend fun googleLogin(idToken: String): AuthResult<User> {
        return try {
            val response = apiService.googleLogin(GoogleLoginRequest(idToken))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val data = body.data
                if (body.success && data?.accessToken != null) {
                    tokenManager.saveTokens(data.accessToken, data.refreshToken)
                    val user = data.user?.toDomain() ?: User("", "Unknown", "", null)
                    tokenManager.userName = user.name
                    AuthResult.Success(user)
                } else {
                    val msg = body.message ?: "No token returned"
                    AuthResult.Error("Login Failed: " + msg)
                }
            } else {
                val msg = when (response.code()) {
                    401, 403 -> "Google Sign-In failed. Please try again."
                    else -> "Google Sign-In failed (HTTP ${response.code()})."
                }
                AuthResult.Error(msg)
            }
        } catch (e: Exception) {
            AuthResult.Error("Unable to reach server. Please check your connection.")
        }
    }

    suspend fun loginWithEmail(email: String, pass: String): AuthResult<User> {
        return try {
            val response = apiService.login(LoginRequest(email, pass))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val data = body.data
                if (body.success && data?.accessToken != null) {
                    tokenManager.saveTokens(data.accessToken, data.refreshToken)
                    val user = data.user?.toDomain() ?: User("", "Unknown", "", null)
                    tokenManager.userName = user.name
                    AuthResult.Success(user)
                } else {
                    AuthResult.Error("Invalid email or password.")
                }
            } else {
                val msg = when (response.code()) {
                    400, 401, 403, 404 -> "Invalid email or password."
                    429 -> "Too many attempts. Please try again later."
                    else -> "Login failed (HTTP ${response.code()})."
                }
                AuthResult.Error(msg)
            }
        } catch (e: Exception) {
            AuthResult.Error("Unable to reach server. Please check your connection.")
        }
    }

    suspend fun registerWithEmail(name: String, email: String, pass: String): AuthResult<User> {
        return try {
            val response = apiService.register(RegisterRequest(name, email, pass))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val data = body.data
                if (body.success && data?.accessToken != null) {
                    tokenManager.saveTokens(data.accessToken, data.refreshToken)
                    val user = data.user?.toDomain() ?: User("", "Unknown", "", null)
                    tokenManager.userName = user.name
                    AuthResult.Success(user)
                } else {
                    AuthResult.Error("Registration failed. Please check your details.")
                }
            } else {
                val msg = when (response.code()) {
                    400 -> "Please check your registration details."
                    409 -> "Registration could not be completed. Please check your email."
                    429 -> "Too many attempts. Please try again later."
                    else -> "Registration failed (HTTP ${response.code()})."
                }
                AuthResult.Error(msg)
            }
        } catch (e: Exception) {
            AuthResult.Error("Unable to reach server. Please check your connection.")
        }
    }

    fun logout() {
        tokenManager.clearTokens()
    }

    suspend fun getMe(): Result<User> {
        return try {
            val response = apiService.getMe()
            if (response.isSuccessful && response.body()?.data != null) {
                val user = response.body()!!.data!!.toDomain()
                tokenManager.userName = user.name
                Result.success(user)
            } else {
                Result.failure(Exception("Failed to fetch profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerDevice(fcmToken: String): Result<Unit> {
        return try {
            val response = apiService.registerDevice(com.myapp.motrava.data.remote.dto.RegisterDeviceRequest(fcmToken))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to register device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
