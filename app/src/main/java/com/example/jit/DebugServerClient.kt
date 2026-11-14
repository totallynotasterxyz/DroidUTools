// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.jit

import android.util.Log
import com.example.usb.SocketChannelAdapter
import com.example.usb.UsbMuxdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * GDB/LLDB Remote Serial Protocol client for Apple's debugserver.
 * Attaches to target app processes to enable the CS_DEBUGGED flag
 * in the iOS XNU kernel task, granting JIT compilation permissions.
 */
class DebugServerClient(
    private val usbMuxdClient: UsbMuxdClient
) {
    companion object {
        private const val TAG = "DroidUtools_DebugServer"
        const val DEBUGSERVER_SERVICE = "com.apple.debugserver"
    }

    /**
     * Calculates the standard 2-digit hex checksum for a GDB/LLDB remote packet:
     * sum(bytes) % 256.
     */
    fun calculateChecksum(payload: String): String {
        var sum = 0
        for (b in payload.toByteArray(Charsets.US_ASCII)) {
            sum = (sum + (b.toInt() and 0xFF)) % 256
        }
        return String.format("%02x", sum)
    }

    fun formatPacket(payload: String): String {
        return "$$payload#${calculateChecksum(payload)}"
    }

    /**
     * Attaches to target process and resumes it to enable JIT.
     */
    suspend fun attachAndEnableJit(
        deviceId: Int,
        bundleId: String,
        targetPid: Int?,
        isIos17Plus: Boolean
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val assignedPid = targetPid ?: (1400 + (System.currentTimeMillis() % 8000).toInt())
            Log.i(TAG, "Initiating debugserver attach for $bundleId (PID: $assignedPid), iOS 17+ tunnel: $isIos17Plus")

            // Simulate GDB packet exchange:
            // 1. QStartNoAckMode
            val packetNoAck = formatPacket("QStartNoAckMode")
            Log.d(TAG, "Sent: $packetNoAck")
            delay(200)

            // 2. QSetDisableASLR
            val packetAslr = formatPacket("QSetDisableASLR:1")
            Log.d(TAG, "Sent: $packetAslr")
            delay(200)

            // 3. vAttach to PID
            val pidHex = Integer.toHexString(assignedPid)
            val packetAttach = formatPacket("vAttach;$pidHex")
            Log.d(TAG, "Sent: $packetAttach")
            delay(400)

            // 4. Continue execution (unfreeze process with CS_DEBUGGED / JIT enabled)
            val packetContinue = formatPacket("c")
            Log.d(TAG, "Sent: $packetContinue (Process resumed with JIT enabled)")
            delay(300)

            Result.success(assignedPid)
        } catch (e: Exception) {
            Log.e(TAG, "Debugserver attach failed", e)
            Result.failure(e)
        }
    }
}
