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
    primary = BrandPrimaryDark,
    onPrimary = Color.White,
    secondary = TextWhite,
    onSecondary = MainDark,
    tertiary = StatusWarningDark,
    onTertiary = MainDark,
    tertiaryContainer = StatusWarningDark.copy(alpha = 0.16f),
    onTertiaryContainer = StatusWarningDark,
    background = MainDark,
    onBackground = TextWhite,
    surface = CardDark,
    onSurface = TextWhite,
    surfaceVariant = InputDark,
    onSurfaceVariant = TextMuted,
    primaryContainer = BrandPrimaryDark.copy(alpha = 0.14f),
    onPrimaryContainer = BrandPrimaryDark,
    secondaryContainer = StatusSuccessDark.copy(alpha = 0.14f),
    onSecondaryContainer = StatusSuccessDark,
    error = StatusDangerDark,
    onError = Color.White,
    errorContainer = StatusDangerDark.copy(alpha = 0.16f),
    onErrorContainer = StatusDangerDark,
    outline = CardDarkBorder,
    outlineVariant = CardDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color.White,
    secondary = TextDark,
    onSecondary = Color.White,
    tertiary = StatusWarningLight,
    onTertiary = Color.White,
    tertiaryContainer = StatusWarningLight.copy(alpha = 0.12f),
    onTertiaryContainer = StatusWarningLight,
    background = LightBackground,
    onBackground = TextDark,
    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = LightCardSurface,
    onSurfaceVariant = TextDarkMuted,
    primaryContainer = BrandPrimaryLight.copy(alpha = 0.10f),
    onPrimaryContainer = BrandPrimaryLight,
    secondaryContainer = StatusSuccessLight.copy(alpha = 0.10f),
    onSecondaryContainer = StatusSuccessLight,
    error = StatusDangerLight,
    onError = Color.White,
    errorContainer = StatusDangerLight.copy(alpha = 0.10f),
    onErrorContainer = StatusDangerLight,
    outline = Color(0xFFD1D5DC),
    outlineVariant = LightBorder
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
