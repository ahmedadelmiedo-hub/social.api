package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = SpaceDark,
    secondary = NeonPurple,
    onSecondary = TextBright,
    tertiary = AccentTeal,
    onTertiary = SpaceDark,
    background = SpaceDark,
    onBackground = TextBright,
    surface = SpaceSurface,
    onSurface = TextBright,
    surfaceVariant = SpaceCard,
    onSurfaceVariant = TextBright
)

private val LightColorScheme = DarkColorScheme // Enforce immersive dark-theme for premium tech branding

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark mode for a sleek workspace environment
  dynamicColor: Boolean = false, // Set false to ensure our premium theme displays consistently
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
