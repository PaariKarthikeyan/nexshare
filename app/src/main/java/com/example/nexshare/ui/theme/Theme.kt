package com.example.nexshare.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexShareColors = darkColorScheme(
    primary = PrimaryAccent,
    onPrimary = PrimaryText,
    primaryContainer = LightCard,
    onPrimaryContainer = PrimaryText,
    secondary = LightAccent,
    onSecondary = DarkPanel,
    background = OuterBackground,
    onBackground = DarkPanel,
    surface = MainPanel,
    onSurface = PrimaryText,
    surfaceVariant = Card,
    onSurfaceVariant = SecondaryText,
    error = Danger,
    onError = PrimaryText,
    outline = SecondaryText
)

/**
 * The app uses the burgundy palette in both modes so the dashboard
 * keeps the gaming-panel look. System dark mode is accepted but not
 * changed in Phase 1; a Theme mode setting is added with SettingsViewModel.
 */
@Composable
fun NexShareTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NexShareColors,
        typography = NexShareTypography,
        content = content
    )
}