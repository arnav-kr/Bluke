package dev.arnv.bluke.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF0A3D91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF08265a),
    onPrimaryContainer = Color(0xFFd4e3ff),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF2B2930), // subtle card color
    onSurfaceVariant = Color(0xFFCBC4D0),
    outline = Color(0xFF938F99)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF0A3D91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFd4e3ff),
    onPrimaryContainer = Color(0xFF001c3b),
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val sharedPrefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
  
  val isDynamicColorDefault = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  var dynamicColor by androidx.compose.runtime.remember { 
      androidx.compose.runtime.mutableStateOf(sharedPrefs.getBoolean("dynamic_color", isDynamicColorDefault)) 
  }
  var accentColorIndex by androidx.compose.runtime.remember {
      androidx.compose.runtime.mutableStateOf(sharedPrefs.getInt("accent_color_index", 0))
  }
  var themeMode by androidx.compose.runtime.remember {
      androidx.compose.runtime.mutableStateOf(sharedPrefs.getInt("theme_mode", 0))
  }
  var highContrastMode by androidx.compose.runtime.remember {
      androidx.compose.runtime.mutableStateOf(sharedPrefs.getBoolean("high_contrast_mode", false))
  }
  var paletteStyle by androidx.compose.runtime.remember {
      androidx.compose.runtime.mutableStateOf(sharedPrefs.getString("palette_style", "Tonal Spot") ?: "Tonal Spot")
  }
  
  // Listen for changes
  androidx.compose.runtime.DisposableEffect(context) {
      val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
          when (key) {
              "dynamic_color" -> dynamicColor = prefs.getBoolean("dynamic_color", isDynamicColorDefault)
              "accent_color_index" -> accentColorIndex = prefs.getInt("accent_color_index", 0)
              "theme_mode" -> themeMode = prefs.getInt("theme_mode", 0)
              "high_contrast_mode" -> highContrastMode = prefs.getBoolean("high_contrast_mode", false)
              "palette_style" -> paletteStyle = prefs.getString("palette_style", "Tonal Spot") ?: "Tonal Spot"
          }
      }
      sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
      onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
  }

  val isMonochrome = paletteStyle == "Monochrome"
  val isVibrant = paletteStyle == "Vibrant"
  val isExpressive = paletteStyle == "Expressive"

  val baseColor = if (isMonochrome) {
      if (themeMode == 2 || (themeMode == 0 && darkTheme)) Color(0xFF6E6E6E) else Color(0xFF424242)
  } else {
      AccentColors.getOrElse(accentColorIndex) { AccentColors.first() }
  }

  val useDarkTheme = when (themeMode) {
      1 -> false // Off
      2 -> true  // On
      else -> darkTheme // System
  }

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val baseScheme = if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (useDarkTheme && highContrastMode) {
            baseScheme.copy(background = androidx.compose.ui.graphics.Color.Black, surface = androidx.compose.ui.graphics.Color.Black, surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1C1C1E))
        } else baseScheme
      }
      useDarkTheme -> DarkColorScheme.copy(
          primary = baseColor,
          primaryContainer = baseColor.copy(alpha = 0.3f),
          onPrimaryContainer = baseColor.copy(alpha = 0.9f),
          secondary = if (isVibrant) Color(0xFFE8DEF8) else if (isMonochrome) Color.LightGray else DarkColorScheme.secondary,
          background = if (highContrastMode) androidx.compose.ui.graphics.Color.Black else DarkColorScheme.background,
          surface = if (highContrastMode) androidx.compose.ui.graphics.Color.Black else DarkColorScheme.surface,
          surfaceVariant = if (highContrastMode) androidx.compose.ui.graphics.Color(0xFF1C1C1E) else DarkColorScheme.surfaceVariant
      )
      else -> LightColorScheme.copy(
          primary = baseColor,
          primaryContainer = baseColor.copy(alpha = 0.2f),
          onPrimaryContainer = baseColor.copy(alpha = 0.9f),
          secondary = if (isVibrant) Color(0xFF625B71) else if (isMonochrome) Color.DarkGray else LightColorScheme.secondary
      )
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
