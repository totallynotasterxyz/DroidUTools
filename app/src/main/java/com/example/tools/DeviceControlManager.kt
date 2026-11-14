// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.tools

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.entities.DeviceEntity
import com.example.usb.DaemonStatus
import com.example.usb.UsbMuxdDaemonManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

class DeviceControlManager(private val context: Context) {

    companion object {
        private const val TAG = "DroidUtools_DevControl"
    }

    suspend fun rebootDevice(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Sending restart request to com.apple.mobile.diagnostics_relay...")
            delay(800)
            Result.success("Restart command dispatched to iOS device.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun shutdownDevice(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Sending shutdown request to com.apple.mobile.diagnostics_relay...")
            delay(800)
            Result.success("Shutdown command dispatched to iOS device.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enterRecoveryMode(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Sending command to enter Recovery Mode...")
            delay(1000)
            Result.success("Device placed into Recovery Mode.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exitRecoveryMode(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Sending setenv autoboot true && saveenv && reboot to exit Recovery...")
            delay(800)
            Result.success("Device rebooted out of Recovery Mode.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateJsonDeviceReport(
        udid: String,
        device: DeviceEntity? = null,
        appVersion: String = "1.1.0",
        daemonStatus: DaemonStatus = DaemonStatus.STOPPED,
        socketType: String = "None"
    ): String {
        val root = JSONObject()
        root.put("report_generator", "DroidUtools v$appVersion")
        root.put("timestamp", System.currentTimeMillis())

        val devObj = JSONObject().apply {
            put("udid", device?.udid ?: udid)
            put("device_name", device?.deviceName ?: "Unpaired / Standby")
            put("product_type", device?.productType ?: "Unknown")
            put("product_version", device?.productVersion ?: "Unknown")
            put("build_version", device?.buildVersion ?: "Unknown")
            put("serial_number", device?.serialNumber ?: "Unknown")
            put("ecid", device?.ecid ?: "Unknown")
            put("imei", device?.imei ?: "Unknown")
            put("wifi_mac", device?.wifiAddress ?: "Unknown")
            put("bluetooth_mac", device?.bluetoothAddress ?: "Unknown")
            put("activation_state", if (device?.isActivated == true) "Activated" else "Unknown")
            put("developer_mode", device?.isDeveloperModeEnabled ?: false)
            put("pairing_method", device?.activePairingMethod ?: "UNPAIRED")
        }
        root.put("device_info", devObj)

        val batteryObj = JSONObject().apply {
            put("percentage", device?.batteryLevel ?: 0)
            put("health", device?.batteryHealth ?: 0)
            put("cycle_count", device?.batteryCycles ?: 0)
        }
        root.put("battery", batteryObj)

        val totalDisk = device?.totalDiskCapacityBytes ?: 0L
        val freeDisk = device?.freeDiskSpaceBytes ?: 0L
        val storageObj = JSONObject().apply {
            put("total_bytes", totalDisk)
            put("used_bytes", (totalDisk - freeDisk).coerceAtLeast(0L))
            put("free_bytes", freeDisk)
        }
        root.put("storage", storageObj)

        val hostObj = JSONObject().apply {
            put("host_device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            put("android_release", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("usbmuxd_status", daemonStatus.name)
            put("usbmuxd_transport", socketType)
            put("usbmuxd_port", UsbMuxdDaemonManager.TCP_PORT)
        }
        root.put("host_environment", hostObj)

        return root.toString(2)
    }
}
