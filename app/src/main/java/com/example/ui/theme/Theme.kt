package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.zahra.storage.ZahraPreferences

val LocalZahraGlassmorphism = staticCompositionLocalOf { false }

@Composable
fun ZahraTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  accentColorName: String = ZahraPreferences.DEFAULT_ACCENT,
  glassmorphismEnabled: Boolean = false,
  content: @Composable () -> Unit
) {
  val accent = when (accentColorName) {
    "أزرق" -> ZahraAccentBlue
    "بنفسجي" -> ZahraAccentViolet
    "برتقالي" -> ZahraAccentOrange
    else -> ZahraBrandEmerald
  }
  val onAccent = if (accent == ZahraAccentOrange) Color(0xFF1F2937) else Color.White

  val colors = if (darkTheme) {
    darkColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accent.copy(alpha = 0.28f),
      onPrimaryContainer = Color.White,
      secondary = accent,
      onSecondary = onAccent,
      background = GptDarkBg,
      onBackground = GptDarkTextPrimary,
      surface = GptDarkSurface,
      onSurface = GptDarkTextPrimary,
      surfaceVariant = GptDarkInputCapsule.copy(alpha = if (glassmorphismEnabled) 0.72f else 1f),
      onSurfaceVariant = GptDarkTextSecondary,
      outline = GptDarkBorder,
      outlineVariant = GptDarkDivider
    )
  } else {
    lightColorScheme(
      primary = accent,
      onPrimary = onAccent,
      primaryContainer = accent.copy(alpha = 0.12f),
      onPrimaryContainer = accent,
      secondary = accent,
      onSecondary = onAccent,
      background = GptLightBg,
      onBackground = GptLightTextPrimary,
      surface = GptLightSurface,
      onSurface = GptLightTextPrimary,
      surfaceVariant = GptLightInputCapsule.copy(alpha = if (glassmorphismEnabled) 0.68f else 1f),
      onSurfaceVariant = GptLightTextSecondary,
      outline = GptLightBorder,
      outlineVariant = GptLightDivider
    )
  }

  CompositionLocalProvider(LocalZahraGlassmorphism provides glassmorphismEnabled) {
    MaterialTheme(
      colorScheme = colors,
      typography = Typography,
      content = content
    )
  }
}
