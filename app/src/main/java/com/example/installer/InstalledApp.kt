// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.installer

data class InstalledApp(
    val bundleId: String,
    val appName: String,
    val version: String,
    val appType: AppType, // USER, SYSTEM, DEVELOPER
    val signerIdentity: String? = null,
    val isDebuggable: Boolean = false,
    val diskUsageBytes: Long = 0L,
    val containerPath: String? = null
)

enum class AppType(val label: String) {
    USER("User App"),
    SYSTEM("System App"),
    DEVELOPER("Sideloaded / Dev")
}
