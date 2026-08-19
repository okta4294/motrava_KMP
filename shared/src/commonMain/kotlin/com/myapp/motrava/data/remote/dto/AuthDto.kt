package com.myapp.motrava.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.myapp.motrava.domain.model.User

@Serializable

data class GoogleLoginRequest(
    val id_token: String
)

@Serializable

data class AuthResponse(
    val success: Boolean,
    val message: String?,
    val data: AuthData?
)

@Serializable

data class AuthData(
    @SerialName("access_token")
    val accessToken: String?,
    
    @SerialName("refresh_token")
    val refreshToken: String?,
    
    val user: UserDto?
)

@Serializable

data class UserResponse(
    val success: Boolean,
    val message: String?,
    val data: UserDto?
)

@Serializable

data class UserDto(
    val id: String?,
    val name: String?,
    val full_name: String?,
    val email: String?,
    val profile_pic: String?,
    val avatar_url: String?
) {
    fun toDomain(): User {
        return User(
            id = id ?: "",
            name = name ?: full_name ?: "Unknown",
            email = email ?: "",
            profilePic = profile_pic ?: avatar_url
        )
    }
}

@Serializable

data class RegisterRequest(
    val full_name: String,
    val email: String,
    val password: String
)

@Serializable

data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable

data class RefreshRequest(
    val refresh_token: String
)

