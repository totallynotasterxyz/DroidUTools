// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Securely stores pairing records generated via Lockdownd or RPPairing (RemotePairing).
 */
@Entity(tableName = "pairing_records")
data class PairingRecordEntity(
    @PrimaryKey
    val udid: String,
    val hostId: String,
    val systemBuid: String,
    val hostCertificatePem: String,
    val hostPrivateKeyPem: String,
    val rootCertificatePem: String,
    val deviceCertificatePem: String,
    val rootPrivateKeyPem: String,
    val wifiMacAddress: String = "",
    val pairingType: String, // "LOCKDOWND", "RP_PAIRING"
    val rawPlistXml: String,
    val remotePairingSecret: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
