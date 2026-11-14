// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.sidestore

data class SideloadAppItem(
    val id: String,
    val name: String,
    val bundleId: String,
    val version: String,
    val author: String,
    val description: String,
    val category: String, // "Store & Sideload", "JIT & Debug", "Emulation", "Utilities"
    val downloadUrl: String,
    val isNightlyAvailable: Boolean = false,
    val nightlyDownloadUrl: String? = null,
    val requiresPairingInjection: Boolean = false,
    val requiresJit: Boolean = false
)

object SideloadAppCatalog {

    val communityApps: List<SideloadAppItem> = listOf(
        SideloadAppItem(
            id = "sidestore",
            name = "SideStore",
            bundleId = "com.SideStore.SideStore",
            version = "0.5.8 (Stable)",
            author = "SideStore Team",
            description = "Sideload apps directly on your iPhone without a Mac or PC using a loopback WireGuard VPN.",
            category = "Store & Sideload",
            downloadUrl = "https://github.com/SideStore/SideStore/releases/latest/download/SideStore.ipa",
            isNightlyAvailable = true,
            nightlyDownloadUrl = "https://github.com/SideStore/SideStore/releases/download/nightly/SideStore-nightly.ipa",
            requiresPairingInjection = true
        ),
        SideloadAppItem(
            id = "livecontainer",
            name = "LiveContainer",
            bundleId = "com.kdt.livecontainer",
            version = "2.1.0",
            author = "Khang (khanhduytran)",
            description = "Run unlimited iOS apps inside one shared sandbox container, completely bypassing the free 3-app sideload limit.",
            category = "Store & Sideload",
            downloadUrl = "https://github.com/khanhduytran0/LiveContainer/releases/latest/download/LiveContainer.ipa",
            requiresPairingInjection = true
        ),
        SideloadAppItem(
            id = "stikdebug",
            name = "StikDebug",
            bundleId = "com.stik.debug",
            version = "1.2.0",
            author = "Stik",
            description = "Wireless JIT debugger client for enabling Just-In-Time compilation on iOS 17+ emulators.",
            category = "JIT & Debug",
            downloadUrl = "https://github.com/stikdebug/stikdebug/releases/latest/download/StikDebug.ipa",
            requiresPairingInjection = true,
            requiresJit = true
        ),
        SideloadAppItem(
            id = "dolphin",
            name = "Dolphin iOS",
            bundleId = "org.dolphin-emu.dolphin",
            version = "4.0.0-beta",
            author = "OatmealDome",
            description = "Nintendo GameCube and Wii emulator for iOS with high-resolution rendering and controller support. Requires JIT.",
            category = "Emulation",
            downloadUrl = "https://dolphinios.oatmealdome.me/download/latest.ipa",
            requiresJit = true
        ),
        SideloadAppItem(
            id = "flycast",
            name = "Flycast",
            bundleId = "com.flyinghead.Flycast",
            version = "2.3",
            author = "flyinghead",
            description = "Multiplatform Sega Dreamcast, Naomi, and Atomiswave arcade emulator with full JIT support.",
            category = "Emulation",
            downloadUrl = "https://github.com/flyinghead/flycast/releases/latest/download/flycast-ios.ipa",
            requiresJit = true
        ),
        SideloadAppItem(
            id = "utm_se",
            name = "UTM SE",
            bundleId = "com.utmapp.UTM-SE",
            version = "4.5.3",
            author = "UTM Team",
            description = "Run Windows, Linux, and classic Mac OS operating systems directly on your iPhone or iPad.",
            category = "Emulation",
            downloadUrl = "https://github.com/utmapp/UTM/releases/latest/download/UTM-SE.ipa"
        ),
        SideloadAppItem(
            id = "trollstore",
            name = "TrollStore Helper",
            bundleId = "com.opa334.TrollStoreHelper",
            version = "2.0.15",
            author = "opa334",
            description = "Permanent app installer without certificate expiration for supported iOS versions (up to iOS 17.0).",
            category = "Utilities",
            downloadUrl = "https://github.com/opa334/TrollStore/releases/latest/download/TrollStore.ipa"
        )
    )
}
