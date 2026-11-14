// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun getDarkColorScheme(accentIndex: Int, isOled: Boolean): ColorScheme {
    val (primary, secondary) = when (accentIndex) {
        1 -> BluePrimary to BlueSecondary
        2 -> GreenPrimary to GreenSecondary
        3 -> PurplePrimary to PurpleSecondary
        else -> CyanPrimary to CyanSecondary
    }

    val bg = if (isOled) OledBlack else DarkBackground
    val surface = if (isOled) Color(0xFF05070B) else DarkSurface

    return darkColorScheme(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = primary.copy(alpha = 0.2f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color.Black,
        secondaryContainer = secondary.copy(alpha = 0.2f),
        onSecondaryContainer = secondary,
        tertiary = StatusTunnel,
        onTertiary = Color.White,
        background = bg,
        onBackground = TextPrimary,
        surface = surface,
        onSurface = TextPrimary,
        surfaceVariant = DarkSurfaceVariant,
        onSurfaceVariant = TextSecondary,
        outline = DarkOutline,
        outlineVariant = DarkOutlineVariant,
        error = StatusError,
        onError = Color.White
    )
}

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = BlueSecondary,
    secondary = CyanSecondary,
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = StatusError,
    onError = Color.White
)

@Composable
fun DroidUtoolsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isOled: Boolean = true,
    accentIndex: Int = 0,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        getDarkColorScheme(accentIndex, isOled)
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
