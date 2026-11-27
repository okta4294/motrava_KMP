package com.myapp.motrava.presentation.theme

import androidx.compose.ui.graphics.Color

// ── Brand Accent from DESIGN.md (Orange Telemetry) ──
val BrandPrimaryLight = Color(0xFFFE6E00)
val BrandPrimaryDark = Color(0xFFFF6B00)
val BrandPrimaryMuted = Color(0xFFF97015)

// ── Semantic Status Colors from DESIGN.md ──
val StatusSuccessLight = Color(0xFF00A84B)
val StatusSuccessDark = Color(0xFF00C758)
val StatusWarningLight = Color(0xFFC99700)
val StatusWarningDark = Color(0xFFEDB200)
val StatusDangerLight = Color(0xFFDC2626)
val StatusDangerDark = Color(0xFFFB2C36)

// ── Dark Theme Surfaces (Warm Espresso Charcoal) ──
val MainDark = Color(0xFF1A1715)
val CardDark = Color(0xFF24201D)
val CardDarkBorder = Color(0xFF35302B)
val InputDark = Color(0xFF2C2723)

// ── Light Theme Surfaces from DESIGN.md (Warm Off-White) ──
val LightBackground = Color(0xFFFCFAF7)
val LightSurface = Color(0xFFFFFFFF)
val LightCardSurface = Color(0xFFEDEBE9)
val LightBorder = Color(0xFFE3E0DD)

// ── Text Colors from DESIGN.md ──
val TextWhite = Color(0xFFFAFAF9)
val TextMuted = Color(0xFFB9B3AC)
val TextDark = Color(0xFF423D38)
val TextDarkMuted = Color(0xFF797067)

// ── Legacy Aliases (mapped to DESIGN.md tokens so all screens stay cohesive) ──
val GradientPurple = BrandPrimaryLight
val GradientPink = BrandPrimaryMuted
val AccentPeach = BrandPrimaryDark
val AccentYellow = StatusWarningDark
val AccentYellowBright = TextWhite
val AccentGreen = StatusSuccessDark
val ActivePink = StatusDangerDark
