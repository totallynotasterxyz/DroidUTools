// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.root

sealed interface RootVerificationStatus {
    object Idle : RootVerificationStatus
    object Checking : RootVerificationStatus
    data class Granted(
        val uid: Int,
        val manager: String,
        val suPath: String,
        val selinuxMode: String,
        val kernelVersion: String,
        val usbDevNodesAccessible: Boolean
    ) : RootVerificationStatus
    data class Denied(val reason: String, val suggestedFix: String) : RootVerificationStatus
    data class Missing(val reason: String) : RootVerificationStatus
}

data class RootCommandResult(
    val exitCode: Int,
    val stdout: List<String>,
    val stderr: List<String>,
    val executionTimeMs: Long
) {
    val isSuccess: Boolean get() = exitCode == 0
    val outputText: String get() = stdout.joinToString("\n")
    val errorText: String get() = stderr.joinToString("\n")
}
