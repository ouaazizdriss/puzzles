package com.driss.puzzles.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A55C8),
    secondary = Color(0xFFB35C00),
    tertiary = Color(0xFF00897B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    secondary = Color(0xFFFFB68A),
    tertiary = Color(0xFF80CBC4),
)

/** Colors of the table the puzzle is assembled on. */
val TableColors = listOf(
    Color(0xFF2B2D31),
    Color(0xFF2F4A3A),
    Color(0xFF5A4232),
    Color(0xFF34405C),
    Color(0xFFE6E1D8),
)

@Composable
fun PuzzlesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
