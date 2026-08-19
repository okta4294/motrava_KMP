package com.myapp.motrava.presentation.splash

import motravakmp.shared.generated.resources.Res
import motravakmp.shared.generated.resources.logo

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import com.myapp.motrava.data.local.TokenManager
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon

@Composable
fun SplashScreen(
    onSplashFinished: (isLoggedIn: Boolean) -> Unit
) {
    val tokenManager: TokenManager = koinInject()

    LaunchedEffect(Unit) {
        // Wait for 1.5 seconds to show the logo
        delay(1500L)
        val isLoggedIn = tokenManager.accessToken != null
        onSplashFinished(isLoggedIn)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = org.jetbrains.compose.resources.painterResource(motravakmp.shared.generated.resources.Res.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(120.dp)
        )
    }
}
