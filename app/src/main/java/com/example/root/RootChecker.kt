// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.root

import android.os.Build
import com.example.ui.components.LogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RootChecker {

    private val _status = MutableStateFlow<RootVerificationStatus>(RootVerificationStatus.Idle)
    val status: StateFlow<RootVerificationStatus> = _status.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val terminalLogs: StateFlow<List<LogEntry>> = _terminalLogs.asStateFlow()

    private fun log(level: String, message: String) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val entry = LogEntry(
            timestamp = sdf.format(Date()),
            level = level,
            message = message
        )
        _terminalLogs.value = _terminalLogs.value + entry
    }

    fun clearLogs() {
        _terminalLogs.value = emptyList()
    }

    suspend fun verifyRoot(): RootVerificationStatus = withContext(Dispatchers.IO) {
        _status.value = RootVerificationStatus.Checking
        log("INFO", "Initiating Root privilege verification for DroidUtools...")
        log("INFO", "Target Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        // 1. Locate su binary
        log("EXEC", "Scanning filesystem for su executable binary...")
        val suPath = RootShell.findSuBinary()
        if (suPath == null) {
            log("ERROR", "No su binary detected in standard system or overlay locations.")
            log("WARN", "Magisk / KernelSU / APatch installation is required.")
            val result = RootVerificationStatus.Missing(
                "No 'su' executable was found on this device. DroidUtools requires systemless root (Magisk, KernelSU, or APatch) on Android 14+."
            )
            _status.value = result
            return@withContext result
        }

        log("INFO", "Found su binary at: $suPath")

        // 2. Request and verify root execution
        log("EXEC", "Invoking su -c 'id -u' to verify UID 0...")
        val idResult = RootShell.execute("id -u")
        if (!idResult.isSuccess || idResult.stdout.isEmpty()) {
            val err = idResult.stderr.joinToString(" ").ifEmpty { "Permission denied or prompt dismissed" }
            log("ERROR", "Failed to obtain root shell: $err")
            val result = RootVerificationStatus.Denied(
                reason = "Root request was denied or timed out by the superuser manager ($err).",
                suggestedFix = "Open Magisk / KernelSU / APatch app and grant superuser rights to DroidUtools."
            )
            _status.value = result
            return@withContext result
        }

        val uid = idResult.stdout.first().trim().toIntOrNull() ?: -1
        if (uid != 0) {
            log("ERROR", "UID verification returned $uid (expected 0 / root).")
            val result = RootVerificationStatus.Denied(
                reason = "Superuser shell returned non-root UID: $uid",
                suggestedFix = "Ensure root manager is providing full root namespace privileges."
            )
            _status.value = result
            return@withContext result
        }

        log("INFO", "Root UID confirmed: 0 (root).")

        // 3. Detect Superuser Manager
        log("EXEC", "Detecting Root Manager implementation...")
        var managerName = "Generic su"
        val magiskCheck = RootShell.execute("magisk -v 2>/dev/null")
        if (magiskCheck.isSuccess && magiskCheck.stdout.isNotEmpty()) {
            managerName = "Magisk v${magiskCheck.stdout.first().trim()}"
        } else {
            val ksuCheck = RootShell.execute("ksud -V 2>/dev/null || [ -d /data/adb/ksu ] && echo KSU")
            if (ksuCheck.isSuccess && ksuCheck.stdout.isNotEmpty()) {
                managerName = "KernelSU (${ksuCheck.stdout.first().trim()})"
            } else {
                val apCheck = RootShell.execute("[ -d /data/adb/ap ] && echo APatch")
                if (apCheck.isSuccess && apCheck.stdout.isNotEmpty()) {
                    managerName = "APatch"
                }
            }
        }
        log("INFO", "Root Manager identified: $managerName")

        // 4. SELinux & Kernel verification
        log("EXEC", "Checking SELinux enforcement status...")
        val selinuxMode = RootShell.getSELinuxStatus()
        log("INFO", "SELinux mode: $selinuxMode")

        val kernelRes = RootShell.execute("uname -r")
        val kernelVersion = if (kernelRes.isSuccess && kernelRes.stdout.isNotEmpty()) {
            kernelRes.stdout.first().trim()
        } else {
            "Linux"
        }
        log("INFO", "Kernel Version: $kernelVersion")

        // 5. Check and configure USB raw node access
        log("EXEC", "Setting permissions on /dev/bus/usb/ (chmod -R 0666)...")
        val usbPermGranted = RootShell.grantUsbNodePermissions()
        val usbDevExists = File("/dev/bus/usb").exists()
        log("INFO", "USB Host Nodes Accessible: $usbDevExists (chmod status: $usbPermGranted)")

        log("INFO", ">>> ROOT VERIFICATION SUCCESSFUL. SYSTEM ACCESS UNLOCKED. <<<")
        val grantedStatus = RootVerificationStatus.Granted(
            uid = uid,
            manager = managerName,
            suPath = suPath,
            selinuxMode = selinuxMode,
            kernelVersion = kernelVersion,
            usbDevNodesAccessible = usbDevExists
        )
        _status.value = grantedStatus
        grantedStatus
    }
}
