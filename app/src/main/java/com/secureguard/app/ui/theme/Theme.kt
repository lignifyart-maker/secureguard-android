package com.secureguard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = GuardOcean,
    secondary = GuardMint,
    tertiary = GuardSand,
    background = GuardSurface,
    surface = GuardSurface
)

private val DarkColors = darkColorScheme(
    primary = GuardMint,
    secondary = GuardOcean,
    background = GuardNavy,
    surface = GuardNavy
)

@Composable
fun SecureGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
