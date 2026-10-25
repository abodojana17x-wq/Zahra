package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neutral light palette
val GptLightBg = Color(0xFFFFFFFF)
val GptLightSurface = Color(0xFFFFFFFF)
val GptLightInputCapsule = Color(0xFFF4F4F6)
val GptLightBorder = Color(0xFFE5E5EA)
val GptLightDivider = Color(0xFFEEEEF0)
val GptLightTextPrimary = Color(0xFF0D0D0D)
val GptLightTextSecondary = Color(0xFF71717A)
val GptLightTextMuted = Color(0xFFA1A1AA)

// Neutral dark palette
val GptDarkBg = Color(0xFF171717)
val GptDarkSurface = Color(0xFF212121)
val GptDarkInputCapsule = Color(0xFF2F2F2F)
val GptDarkBorder = Color(0xFF383838)
val GptDarkDivider = Color(0xFF2C2C2E)
val GptDarkTextPrimary = Color(0xFFF4F4F5)
val GptDarkTextSecondary = Color(0xFFA1A1AA)
val GptDarkTextMuted = Color(0xFF71717A)

// Selectable accents. The emerald default matches the new Zahra emblem.
val ZahraAccentBlue = Color(0xFF007AFF)
val ZahraBrandEmerald = Color(0xFF0B6B4F)
val ZahraAccentViolet = Color(0xFF8B5CF6)
val ZahraAccentOrange = Color(0xFFF59E0B)

/**
 * Legacy design-token names now resolve through MaterialTheme.
 * This makes the selected accent and dark mode apply everywhere instead of only in Settings.
 */
val GptAccentBlue: Color
  @Composable get() = MaterialTheme.colorScheme.primary

val ZahraBackground: Color
  @Composable get() = MaterialTheme.colorScheme.background

val ZahraSurface: Color
  @Composable get() = MaterialTheme.colorScheme.surface

val ZahraSurfaceGlass: Color
  @Composable get() = MaterialTheme.colorScheme.surfaceVariant

val ZahraGlassBorder: Color
  @Composable get() = MaterialTheme.colorScheme.outlineVariant

val ZahraGlassCard: Color
  @Composable get() = MaterialTheme.colorScheme.surface

val ZahraViolet: Color
  @Composable get() = MaterialTheme.colorScheme.primary

val ZahraCyan: Color
  @Composable get() = MaterialTheme.colorScheme.primary

val ZahraPink = Color(0xFFA855F7)
val ZahraEmerald = Color(0xFF10A37F)
val ZahraAmber = Color(0xFFF59E0B)

val ZahraTextPrimary: Color
  @Composable get() = MaterialTheme.colorScheme.onSurface

val ZahraTextSecondary: Color
  @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

val ZahraTextTertiary: Color
  @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

val ZahraPrimary: Color
  @Composable get() = MaterialTheme.colorScheme.primary

val ZahraSecondary: Color
  @Composable get() = MaterialTheme.colorScheme.secondary

val ZahraTertiary: Color
  @Composable get() = MaterialTheme.colorScheme.primary
