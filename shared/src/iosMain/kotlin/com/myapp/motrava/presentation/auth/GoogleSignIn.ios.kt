package com.myapp.motrava.presentation.auth

import androidx.compose.runtime.Composable

@Composable
actual fun getPlatformContext(): Any? = null

actual suspend fun getGoogleIdToken(context: Any?): String? {
    println("Google Sign-In is not implemented in iosMain yet")
    return null
}
