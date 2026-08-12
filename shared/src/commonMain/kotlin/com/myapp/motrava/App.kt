package com.myapp.motrava

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import com.myapp.motrava.presentation.navigation.MotravaApp
import com.myapp.motrava.presentation.theme.MotravaTheme
import org.koin.compose.KoinContext

@Composable
fun App() {
    val systemDarkTheme = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(systemDarkTheme) }

    KoinContext {
        MotravaTheme(darkTheme = isDarkMode) {
            MotravaApp(
                isDarkMode = isDarkMode,
                onThemeToggle = { isDarkMode = !isDarkMode }
            )
        }
    }
}