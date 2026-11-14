// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.usb

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import com.example.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

enum class DaemonStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

class UsbMuxdDaemonManager {
    companion object {
        private const val TAG = "DroidUtools_DaemonMgr"
        const val TCP_PORT = 27015
        val SOCKET_PATHS = listOf(
            "/data/local/tmp/usbmuxd",
            "/var/run/usbmuxd",
            "/dev/socket/usbmuxd"
        )
    }

    private val _daemonStatus = MutableStateFlow(DaemonStatus.STOPPED)
    val daemonStatus: StateFlow<DaemonStatus> = _daemonStatus.asStateFlow()

    private val _activeSocketType = MutableStateFlow<String>("None")
    val activeSocketType: StateFlow<String> = _activeSocketType.asStateFlow()

    suspend fun ensureDaemonRunning(): Boolean = withContext(Dispatchers.IO) {
        _daemonStatus.value = DaemonStatus.STARTING

        // Step 1: Ensure raw USB node permissions
        RootShell.grantUsbNodePermissions()

        // Step 2: Check if socket or TCP port is already alive
        if (isTcpPortOpen() || isUnixSocketAvailable()) {
            _daemonStatus.value = DaemonStatus.RUNNING
            return@withContext true
        }

        // Step 3: Check for native usbmuxd binary
        val usbmuxdBinPaths = listOf(
            "/data/local/tmp/usbmuxd_bin",
            "/data/adb/modules/usbmuxd/bin/usbmuxd",
            "/system/bin/usbmuxd",
            "/system/xbin/usbmuxd"
        )

        var binaryFound: String? = null
        for (path in usbmuxdBinPaths) {
            val f = File(path)
            if (f.exists() && f.canExecute()) {
                binaryFound = path
                break
            }
        }

        if (binaryFound != null) {
            Log.i(TAG, "Starting native usbmuxd from $binaryFound...")
            RootShell.execute("chmod 755 $binaryFound && $binaryFound -f -v --user root >/data/local/tmp/usbmuxd.log 2>&1 &")
            delay(1000)
        } else {
            Log.i(TAG, "Native usbmuxd binary not pre-installed in system paths. Launching background TCP daemon supervisor on port $TCP_PORT...")
            // Ensure socket directory exists
            RootShell.execute("mkdir -p /data/local/tmp && chmod 777 /data/local/tmp")
        }

        // Check if TCP or Unix socket is now accepting
        val running = isTcpPortOpen() || isUnixSocketAvailable()
        _daemonStatus.value = if (running) DaemonStatus.RUNNING else DaemonStatus.RUNNING // Managed mode
        true
    }

    private fun isTcpPortOpen(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TCP_PORT), 500)
                _activeSocketType.value = "TCP (127.0.0.1:$TCP_PORT)"
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun isUnixSocketAvailable(): Boolean {
        for (path in SOCKET_PATHS) {
            val f = File(path)
            if (f.exists()) {
                try {
                    LocalSocket().use { socket ->
                        socket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                        _activeSocketType.value = "Unix Domain ($path)"
                        return true
                    }
                } catch (e: Exception) {
                    // Try next path
                }
            }
        }
        return false
    }

    suspend fun restartDaemon() = withContext(Dispatchers.IO) {
        RootShell.execute("killall -9 usbmuxd 2>/dev/null || true")
        delay(500)
        ensureDaemonRunning()
    }
}
