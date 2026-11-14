// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyslogEntry(
    val timestamp: String,
    val process: String,
    val pid: Int,
    val level: String, // "Notice", "Info", "Warning", "Error", "Fault"
    val message: String
)

class SyslogClient {

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    fun streamSyslog(): Flow<SyslogEntry> = flow {
        _isStreaming.value = true
        val sdf = SimpleDateFormat("MMM dd HH:mm:ss", Locale.US)

        val processes = listOf(
            "kernel" to 0,
            "SpringBoard" to 54,
            "backboardd" to 58,
            "mDNSResponder" to 92,
            "launchd" to 1,
            "debugserver" to 1420,
            "SideStore" to 1642,
            "locationd" to 77,
            "securityd" to 84,
            "rapportd" to 112
        )

        val sampleMessages = listOf(
            Triple("kernel", "xnu-10063.101.17~1/RELEASE_ARM64_T8120: sandbox active", "Notice"),
            Triple("SpringBoard", "Received display state change notification: DISPLAY_ON", "Info"),
            Triple("debugserver", "GDB remote listener socket opened on port 1025", "Notice"),
            Triple("debugserver", "Attached to task org.dolphin-emu.dolphin (PID 1420)", "Notice"),
            Triple("mDNSResponder", "Discovered RemoteXPC CoreDevice service _remotepairing._tcp", "Info"),
            Triple("securityd", "Validating mobileprovision trust chain for developer certificate", "Info"),
            Triple("locationd", "Location simulated override active: Lat 37.3346, Lon -122.0090", "Notice"),
            Triple("rapportd", "RPPairing session key exchange validated over usbmuxd tunnel", "Notice"),
            Triple("SideStore", "Loopback WireGuard interface LocalDevVPN connected (10.0.0.1)", "Info"),
            Triple("kernel", "AppleARMPMU: Battery state updated (92% - Normal)", "Info"),
            Triple("SpringBoard", "App launched in foreground: org.dolphin-emu.dolphin", "Info")
        )

        var idx = 0
        while (_isStreaming.value) {
            val (proc, msg, lvl) = sampleMessages[idx % sampleMessages.size]
            idx++

            val entry = SyslogEntry(
                timestamp = sdf.format(Date()),
                process = proc,
                pid = processes.find { it.first == proc }?.second ?: 100,
                level = lvl,
                message = msg
            )
            emit(entry)
            delay(1200)
        }
    }.flowOn(Dispatchers.Default)

    fun stopStream() {
        _isStreaming.value = false
    }
}
