package com.myapp.motrava.presentation.auth

import androidx.compose.runtime.Composable

@Composable
expect fun getPlatformContext(): Any?

expect suspend fun getGoogleIdToken(context: Any? = null): String?
