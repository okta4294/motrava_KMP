package com.myapp.motrava.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GradientPurple,
    onPrimary = TextWhite,
    secondary = AccentPeach,
    onSecondary = TextDark,
    tertiary = GradientPink,
    background = MainDark,
    onBackground = TextWhite,
    surface = CardDark,
    onSurface = TextWhite,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextMuted,
    primaryContainer = GradientPurple.copy(alpha = 0.2f),
    onPrimaryContainer = GradientPurple,
    secondaryContainer = AccentGreen.copy(alpha = 0.15f),
    onSecondaryContainer = AccentGreen,
    error = Color(0xFFFF6B6B),
    onError = TextWhite,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = CardDarkBorder,
    outlineVariant = CardDarkBorder.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = GradientPurple,
    onPrimary = Color.White,
    secondary = AccentPeach,
    onSecondary = Color.White,
    tertiary = GradientPink,
    background = LightBackground,
    onBackground = TextDark,
    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = LightCardSurface,
    onSurfaceVariant = TextDarkMuted,
    primaryContainer = GradientPurple.copy(alpha = 0.1f),
    onPrimaryContainer = GradientPurple,
    secondaryContainer = AccentGreen.copy(alpha = 0.1f),
    onSecondaryContainer = AccentGreen,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB)
)

@Composable
fun MotravaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor to false to enforce our custom modern brand colors on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
