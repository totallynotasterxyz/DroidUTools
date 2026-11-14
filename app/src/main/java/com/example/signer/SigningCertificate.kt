// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.signer

data class SigningCertificate(
    val id: String,
    val displayName: String,
    val teamName: String,
    val teamId: String,
    val isFreeAppleId: Boolean = true,
    val appleIdEmail: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000), // 7 days for free accounts
    val certificateType: CertificateType = CertificateType.APPLE_ID,
    val p12Base64: String = "",
    val p12Password: String = "",
    val mobileprovisionXml: String = ""
) {
    val remainingDays: Long
        get() = ((expiresAt - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).coerceAtLeast(0)

    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAt
}

enum class CertificateType {
    P12_CERTIFICATE,
    APPLE_ID
}
