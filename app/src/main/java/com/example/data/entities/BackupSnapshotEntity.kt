// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local snapshot archive for iTunes-style app data backups pulled over AFC/HouseArrest.
 */
@Entity(tableName = "backup_snapshots")
data class BackupSnapshotEntity(
    @PrimaryKey
    val id: String,
    val udid: String,
    val bundleId: String,
    val appName: String,
    val archivePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val includesDocuments: Boolean = true,
    val includesPreferences: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
