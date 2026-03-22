package com.secureguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = GuardBlue,
    secondary = GuardMint,
    tertiary = GuardButter,
    background = GuardCream,
    surface = Color.White,
    primaryContainer = GuardLavender,
    secondaryContainer = Color(0xFFDFF7F0),
    tertiaryContainer = Color(0xFFFFF4D1),
    onBackground = GuardInk,
    onSurface = GuardInk,
    onSurfaceVariant = Color(0xFF5F6877)
)

private val DarkColors = darkColorScheme(
    primary = GuardMint,
    secondary = GuardBlue,
    tertiary = GuardPeach,
    background = GuardNight,
    surface = GuardNightSoft,
    primaryContainer = Color(0xFF2F415F),
    secondaryContainer = Color(0xFF204B49),
    tertiaryContainer = Color(0xFF5A4330)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun SecureGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}
