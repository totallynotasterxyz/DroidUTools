// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.signer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrackedSignedApp(
    val bundleId: String,
    val appName: String,
    val signedWithAccount: String,
    val signedAtTimestamp: Long = System.currentTimeMillis(),
    val expiresAtTimestamp: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
) {
    val remainingDays: Long
        get() = ((expiresAtTimestamp - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)

    val remainingHours: Long
        get() = ((expiresAtTimestamp - System.currentTimeMillis()) / (60 * 60 * 1000)).coerceAtLeast(0)

    val isExpiringSoon: Boolean
        get() = remainingDays <= 2

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAtTimestamp
}

class ExpiryTracker {

    private val _trackedApps = MutableStateFlow<List<TrackedSignedApp>>(
        listOf(
            TrackedSignedApp(
                bundleId = "com.SideStore.SideStore",
                appName = "SideStore",
                signedWithAccount = "user@icloud.com",
                signedAtTimestamp = System.currentTimeMillis() - (3L * 24 * 60 * 60 * 1000),
                expiresAtTimestamp = System.currentTimeMillis() + (4L * 24 * 60 * 60 * 1000)
            ),
            TrackedSignedApp(
                bundleId = "org.dolphin-emu.dolphin",
                appName = "Dolphin iOS",
                signedWithAccount = "user@icloud.com",
                signedAtTimestamp = System.currentTimeMillis() - (5L * 24 * 60 * 60 * 1000),
                expiresAtTimestamp = System.currentTimeMillis() + (2L * 24 * 60 * 60 * 1000)
            )
        )
    )
    val trackedApps: StateFlow<List<TrackedSignedApp>> = _trackedApps.asStateFlow()

    fun addTrackedApp(app: TrackedSignedApp) {
        _trackedApps.value = _trackedApps.value.filter { it.bundleId != app.bundleId } + app
    }

    fun removeTrackedApp(bundleId: String) {
        _trackedApps.value = _trackedApps.value.filter { it.bundleId != bundleId }
    }

    fun refreshAllApps() {
        val now = System.currentTimeMillis()
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        _trackedApps.value = _trackedApps.value.map {
            it.copy(
                signedAtTimestamp = now,
                expiresAtTimestamp = now + sevenDays
            )
        }
    }
}
