package com.nofs.desk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DeskColorScheme = lightColorScheme(
    primary = DeskText,
    onPrimary = DeskCard,
    secondary = DeskMuted,
    onSecondary = DeskCard,
    background = DeskBg,
    onBackground = DeskText,
    surface = DeskCard,
    onSurface = DeskText,
    surfaceVariant = DeskBg,
    onSurfaceVariant = DeskMuted,
    outline = DeskHandle
)

@Composable
fun NOFSDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeskColorScheme,
        typography = DeskTypography,
        content = content
    )
}
