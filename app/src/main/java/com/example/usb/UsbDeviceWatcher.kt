// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.example.root.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UsbDeviceWatcher(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DroidUtools_UsbWatcher"
        const val APPLE_VENDOR_ID = 0x05ac // 1452
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedAppleDevices = MutableStateFlow<List<AppleUsbDevice>>(emptyList())
    val connectedAppleDevices: StateFlow<List<AppleUsbDevice>> = _connectedAppleDevices.asStateFlow()

    private val _connectedAppleDevice = MutableStateFlow<AppleUsbDevice?>(null)
    val connectedAppleDevice: StateFlow<AppleUsbDevice?> = _connectedAppleDevice.asStateFlow()

    private val _usbAttachedEvent = MutableStateFlow<Boolean>(false)
    val usbAttachedEvent: StateFlow<Boolean> = _usbAttachedEvent.asStateFlow()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    Log.d(TAG, "USB Device Attached: ${device?.deviceName}, VID: ${device?.vendorId}, PID: ${device?.productId}")
                    scanDevices()
                    _usbAttachedEvent.value = true
                    scope.launch(Dispatchers.IO) {
                        RootShell.grantUsbNodePermissions()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    Log.d(TAG, "USB Device Detached: ${device?.deviceName}")
                    val remaining = scanAllDevices()
                    _usbAttachedEvent.value = remaining.isNotEmpty()
                }
            }
        }
    }

    fun startWatching() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, filter)
        }
        scanDevices()
    }

    fun stopWatching() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        }
    }

    fun scanAllDevices(): List<AppleUsbDevice> {
        val deviceList = usbManager.deviceList
        val found = mutableListOf<AppleUsbDevice>()
        for ((_, device) in deviceList) {
            if (device.vendorId == APPLE_VENDOR_ID) {
                val mode = IosDeviceModelDatabase.getUsbMode(device.productId)
                found += AppleUsbDevice(
                    deviceName = device.deviceName,
                    vendorId = device.vendorId,
                    productId = device.productId,
                    serialNumber = device.serialNumber ?: "UnknownSerial",
                    manufacturerName = device.manufacturerName ?: "Apple Inc.",
                    productName = device.productName ?: "Apple Mobile Device",
                    usbMode = mode,
                    deviceId = device.deviceId
                )
            }
        }
        _connectedAppleDevices.value = found
        _connectedAppleDevice.value = found.firstOrNull()
        return found
    }

    fun scanDevices(): AppleUsbDevice? {
        return scanAllDevices().firstOrNull()
    }
}
