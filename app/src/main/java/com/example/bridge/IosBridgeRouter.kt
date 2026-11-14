// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.bridge

import android.content.Context
import com.example.usb.UsbMuxdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Coordinates the active IosBridge backend across Native NDK (libimobiledevice/usbmuxd/zsign),
 * Python Chaquopy (pymobiledevice3), and pure-Kotlin UsbMuxd socket transport.
 */
class IosBridgeRouter(
    context: Context,
    private val usbMuxdClient: UsbMuxdClient
) {
    private val nativeBridge = NativeIosBridge()
    private val pythonBridge = PythonIosBridge(context)

    private val _activeBackendLabel = MutableStateFlow("Usbmuxd Root Socket")
    val activeBackendLabel: StateFlow<String> = _activeBackendLabel.asStateFlow()

    suspend fun selectPreferredBridge(): IosBridge = withContext(Dispatchers.IO) {
        when {
            nativeBridge.isAvailable() -> {
                _activeBackendLabel.value = nativeBridge.backendType.displayName
                nativeBridge
            }
            pythonBridge.isAvailable() -> {
                _activeBackendLabel.value = pythonBridge.backendType.displayName
                pythonBridge
            }
            else -> {
                _activeBackendLabel.value = "Usbmuxd Root Socket (Kotlin Transport)"
                nativeBridge
            }
        }
    }

    suspend fun queryDeviceProperties(
        deviceId: Int,
        udid: String,
        wirelessHost: String? = null,
        wirelessPort: Int = UsbMuxdClient.LOCKDOWN_PORT
    ): Map<String, String> = withContext(Dispatchers.IO) {
        if (!wirelessHost.isNullOrBlank()) {
            val wifiInfo = usbMuxdClient.queryLockdownBasicInfo(deviceId, wirelessHost, wirelessPort)
            if (wifiInfo.isNotEmpty()) {
                _activeBackendLabel.value = "Wi-Fi LAN Lockdownd Socket"
                return@withContext wifiInfo
            }
        }

        if (nativeBridge.isAvailable()) {
            val nativeInfo = nativeBridge.getDeviceInfo(udid)
            if (nativeInfo.isNotEmpty()) {
                _activeBackendLabel.value = nativeBridge.backendType.displayName
                return@withContext nativeInfo
            }
        }

        val lockdownInfo = usbMuxdClient.queryLockdownBasicInfo(deviceId)
        if (lockdownInfo.isNotEmpty()) {
            _activeBackendLabel.value = "Usbmuxd Lockdownd Socket"
            return@withContext lockdownInfo
        }

        if (pythonBridge.isAvailable()) {
            _activeBackendLabel.value = pythonBridge.backendType.displayName
            return@withContext pythonBridge.getDeviceInfo(udid)
        }

        _activeBackendLabel.value = if (!wirelessHost.isNullOrBlank()) {
            "Wi-Fi LAN Standby ($wirelessHost)"
        } else {
            "Usbmuxd Root Socket (Standby)"
        }
        emptyMap()
    }

    suspend fun setWifiSyncEnabled(
        deviceId: Int,
        enabled: Boolean,
        wirelessHost: String? = null,
        wirelessPort: Int = UsbMuxdClient.LOCKDOWN_PORT
    ): Result<Boolean> = usbMuxdClient.setWifiSyncEnabled(deviceId, enabled, wirelessHost, wirelessPort)
}
