package com.myapp.motrava.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable

data class RegisterDeviceRequest(
    @SerialName("device_token")
    val deviceToken: String,
    val platform: String = "android"
)

