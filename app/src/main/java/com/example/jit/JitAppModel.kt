// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.jit

data class DebuggableApp(
    val bundleId: String,
    val appName: String,
    val executableName: String,
    val version: String,
    val isDebuggable: Boolean = true,
    val isPinned: Boolean = false,
    val autoJitOnLaunch: Boolean = false,
    val activePid: Int? = null,
    val lastJitTimestamp: Long? = null
)

sealed interface JitAttachmentState {
    object Idle : JitAttachmentState
    data class CheckingDeveloperMode(val bundleId: String) : JitAttachmentState
    data class MountingDdi(val bundleId: String, val progress: Float, val status: String) : JitAttachmentState
    data class ConnectingDebugserver(val bundleId: String, val tunnelPath: String) : JitAttachmentState
    data class UnlockingMemory(val bundleId: String, val pid: Int) : JitAttachmentState
    data class Active(
        val bundleId: String,
        val appName: String,
        val pid: Int,
        val tunnelType: String // "RPPairing Tunnel (iOS 17+)" or "Direct Usbmuxd (Classic)"
    ) : JitAttachmentState
    data class Failed(
        val bundleId: String,
        val error: String,
        val suggestedFix: String
    ) : JitAttachmentState
}
