// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.pairing

enum class PairingExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String,
    val description: String
) {
    LOCKDOWN_PLIST(
        extension = "plist",
        mimeType = "application/xml",
        displayName = "Classic Lockdown Plist",
        description = "Standard Apple lockdown pairing record for libimobiledevice and 3uTools"
    ),
    REMOTE_PAIRING(
        extension = "mobiledevicepairing",
        mimeType = "application/octet-stream",
        displayName = "RemotePairing File (.mobiledevicepairing)",
        description = "CoreDevice RemoteXPC pairing file for iOS 17+ Mac/Xcode emulation"
    ),
    SIDESTORE_PAIRING(
        extension = "plist",
        mimeType = "application/xml",
        displayName = "SideStore Pairing File",
        description = "Ready-to-import pairing file for SideStore sideloading without a computer"
    ),
    STIKDEBUG_FORMAT(
        extension = "json",
        mimeType = "application/json",
        displayName = "StikDebug JIT Pairing Record",
        description = "JSON pairing format for StikDebug, JITStreamer, and AltJIT"
    )
}
