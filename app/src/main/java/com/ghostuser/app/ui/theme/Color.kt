package com.ghostuser.app.ui.theme

import androidx.compose.ui.graphics.Color

// Ported from the FileExplorer (Sift) Material3 DayNight palette: a blue primary
// with full light and dark color sets that track the system theme.

// --- Light ---
val LightPrimary = Color(0xFF2D6BFF)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFDBE4FF)
val LightOnPrimaryContainer = Color(0xFF001A41)
val LightSecondary = Color(0xFF565E71)
val LightBackground = Color(0xFFF6F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE6E9F0)
val LightSurfaceContainer = Color(0xFFEEF1F7)
val LightOnSurface = Color(0xFF1A1B20)
val LightOnSurfaceVariant = Color(0xFF44474F)
val LightOutline = Color(0xFFC4C6D0)

// --- Dark ---
val DarkPrimary = Color(0xFFAEC6FF)
val DarkOnPrimary = Color(0xFF002E69)
val DarkPrimaryContainer = Color(0xFF274777)
val DarkOnPrimaryContainer = Color(0xFFD8E2FF)
val DarkSecondary = Color(0xFFBEC6DC)
val DarkBackground = Color(0xFF0E1014)
val DarkSurface = Color(0xFF16181D)
val DarkSurfaceVariant = Color(0xFF262A33)
val DarkSurfaceContainer = Color(0xFF1D2026)
val DarkOnSurface = Color(0xFFE3E5EA)
val DarkOnSurfaceVariant = Color(0xFFC4C6D0)
val DarkOutline = Color(0xFF3A3E46)

// --- Semantic ---
val ErrorRed = Color(0xFFEF4444)
val ErrorRedDark = Color(0xFF7F1D1D)
val SuccessGreen = Color(0xFF22C55E)

// Accent used by the floating overlay panel (always drawn on a dark translucent
// pill, so it uses the brighter dark-mode blue for contrast on any wallpaper).
// Hex strings so OverlayController can parse them for classic View drawables.
const val OVERLAY_ACCENT_HEX = "#4C8DFF"
const val OVERLAY_SURFACE_HEX = "#E6141519"
const val OVERLAY_DANGER_HEX = "#FF5A5A"
