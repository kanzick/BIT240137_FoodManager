package com.example.bit240137_foodmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = androidx.compose.ui.graphics.Color(0xFF171A17),
    surface = androidx.compose.ui.graphics.Color(0xFF20251F),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE9E8E0)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = androidx.compose.ui.graphics.Color(0xFFF7F5EF),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFEFA),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEDEAE1),
    onPrimary = androidx.compose.ui.graphics.Color.White
)

@Composable
fun BIT240137_FoodManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}