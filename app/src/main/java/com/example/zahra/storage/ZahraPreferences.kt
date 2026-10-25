package com.example.zahra.storage

import android.content.Context

/** Small, non-sensitive UI preferences kept separately from the encrypted user archive. */
class ZahraPreferences(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  var accentColorName: String
    get() = prefs.getString(KEY_ACCENT, DEFAULT_ACCENT)
      ?.takeIf { it in SUPPORTED_ACCENTS }
      ?: DEFAULT_ACCENT
    set(value) {
      if (value in SUPPORTED_ACCENTS) {
        prefs.edit().putString(KEY_ACCENT, value).apply()
      }
    }

  var isGlassmorphismEnabled: Boolean
    get() = prefs.getBoolean(KEY_GLASSMORPHISM, false)
    set(value) = prefs.edit().putBoolean(KEY_GLASSMORPHISM, value).apply()

  var isDeepThinkingEnabled: Boolean
    get() = prefs.getBoolean(KEY_DEEP_THINKING, false)
    set(value) = prefs.edit().putBoolean(KEY_DEEP_THINKING, value).apply()

  companion object {
    const val DEFAULT_ACCENT = "أخضر زمردي"
    val SUPPORTED_ACCENTS = setOf("أزرق", "أخضر زمردي", "بنفسجي", "برتقالي")

    private const val PREFS_NAME = "zahra_ui_preferences"
    private const val KEY_ACCENT = "accent_color"
    private const val KEY_GLASSMORPHISM = "glassmorphism_enabled"
    private const val KEY_DEEP_THINKING = "deep_thinking_enabled"
  }
}
