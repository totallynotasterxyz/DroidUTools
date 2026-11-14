// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Base Dark & OLED Colors
val OledBlack = Color(0xFF000000)
val DarkBackground = Color(0xFF080C14)
val DarkSurface = Color(0xFF0F172A)
val DarkSurfaceVariant = Color(0xFF1E293B)
val DarkSurfaceElevated = Color(0xFF162032)
val DarkOutline = Color(0xFF334155)
val DarkOutlineVariant = Color(0xFF1E293B)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Accent Palette 0: Electric Cyan (Default DroidUtools)
val CyanPrimary = Color(0xFF00E5FF)
val CyanSecondary = Color(0xFF00B4D8)
val CyanGlow = Color(0x3300E5FF)

// Accent Palette 1: Apple System Blue
val BluePrimary = Color(0xFF0A84FF)
val BlueSecondary = Color(0xFF0066CC)
val BlueGlow = Color(0x330A84FF)

// Accent Palette 2: Emerald Green (iOS Battery / Active)
val GreenPrimary = Color(0xFF30D158)
val GreenSecondary = Color(0xFF248A3D)
val GreenGlow = Color(0x3330D158)

// Accent Palette 3: Neon Purple (DVT / Developer Mode)
val PurplePrimary = Color(0xFFBF5AF2)
val PurpleSecondary = Color(0xFFAF52DE)
val PurpleGlow = Color(0x33BF5AF2)

// System & Diagnostic Badges
val StatusSuccess = Color(0xFF30D158)
val StatusWarning = Color(0xFFFFD60A)
val StatusError = Color(0xFFFF453A)
val StatusInfo = Color(0xFF64D2FF)
val StatusTunnel = Color(0xFF5E5CE6) // RPPairing badge

// Gradients
val CyanGradient = Brush.horizontalGradient(listOf(CyanPrimary, CyanSecondary))
val BlueGradient = Brush.horizontalGradient(listOf(BluePrimary, BlueSecondary))
val GreenGradient = Brush.horizontalGradient(listOf(GreenPrimary, GreenSecondary))
val PurpleGradient = Brush.horizontalGradient(listOf(PurplePrimary, PurpleSecondary))

// Glass card border gradient
val GlassBorderBrush = Brush.linearGradient(
    listOf(
        Color(0x40FFFFFF),
        Color(0x10FFFFFF),
        Color(0x2000E5FF)
    )
)
