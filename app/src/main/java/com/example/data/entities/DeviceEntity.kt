// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an iOS device discovered or paired via DroidUtools.
 */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey
    val udid: String,
    val deviceName: String,
    val productType: String,
    val modelNumber: String,
    val productVersion: String,
    val buildVersion: String,
    val serialNumber: String,
    val ecid: String,
    val imei: String,
    val wifiAddress: String,
    val bluetoothAddress: String,
    val activePairingMethod: String, // "RP_PAIRING" or "LOCKDOWND"
    val connectionType: String,      // "USB" or "NETWORK"
    val isDeveloperModeEnabled: Boolean,
    val isActivated: Boolean,
    val batteryLevel: Int,
    val batteryHealth: Int,
    val batteryCycles: Int,
    val totalDiskCapacityBytes: Long,
    val freeDiskSpaceBytes: Long,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val nickname: String = "",
    val isPinned: Boolean = false,
    val lastKnownIp: String = "",
    val wirelessPort: Int = 62078,
    val isWifiSyncEnabled: Boolean = false,
    val lastWifiSeenTimestamp: Long = 0L
)
