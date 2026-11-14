// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Audit and activity log entity for DroidUtools operations.
 */
@Entity(tableName = "activity_logs")
data class ActivityLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val udid: String = "",
    val tag: String,
    val message: String,
    val level: String = "INFO" // "INFO", "WARN", "ERROR", "DEBUG"
)
