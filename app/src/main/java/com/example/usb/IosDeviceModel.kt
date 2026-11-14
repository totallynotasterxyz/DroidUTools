// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.usb

enum class UsbMode(val label: String) {
    NORMAL("Normal (iOS Active)"),
    RECOVERY("Recovery Mode"),
    DFU("DFU Mode"),
    UNKNOWN("Unknown Mode")
}

data class AppleUsbDevice(
    val deviceName: String,
    val vendorId: Int, // 0x05ac (1452)
    val productId: Int,
    val serialNumber: String,
    val manufacturerName: String,
    val productName: String,
    val usbMode: UsbMode,
    val deviceId: Int = 1,
    val isLightning: Boolean = true
)

object IosDeviceModelDatabase {
    // Maps ProductType (e.g., iPhone15,2) to friendly commercial names
    private val modelMap = mapOf(
        "iPhone16,1" to "iPhone 15 Pro",
        "iPhone16,2" to "iPhone 15 Pro Max",
        "iPhone15,4" to "iPhone 15",
        "iPhone15,5" to "iPhone 15 Plus",
        "iPhone15,2" to "iPhone 14 Pro",
        "iPhone15,3" to "iPhone 14 Pro Max",
        "iPhone14,7" to "iPhone 14",
        "iPhone14,8" to "iPhone 14 Plus",
        "iPhone14,2" to "iPhone 13 Pro",
        "iPhone14,3" to "iPhone 13 Pro Max",
        "iPhone14,5" to "iPhone 13",
        "iPhone14,4" to "iPhone 13 mini",
        "iPhone13,2" to "iPhone 12",
        "iPhone13,3" to "iPhone 12 Pro",
        "iPhone13,4" to "iPhone 12 Pro Max",
        "iPhone13,1" to "iPhone 12 mini",
        "iPhone12,1" to "iPhone 11",
        "iPhone12,3" to "iPhone 11 Pro",
        "iPhone12,5" to "iPhone 11 Pro Max",
        "iPhone12,8" to "iPhone SE (2nd Gen)",
        "iPhone14,6" to "iPhone SE (3rd Gen)",
        "iPad13,16" to "iPad Air (5th Gen)",
        "iPad13,17" to "iPad Air (5th Gen)",
        "iPad14,3" to "iPad Pro 11-inch (4th Gen)",
        "iPad14,4" to "iPad Pro 11-inch (4th Gen)",
        "iPad14,5" to "iPad Pro 12.9-inch (6th Gen)",
        "iPad14,6" to "iPad Pro 12.9-inch (6th Gen)"
    )

    fun resolveFriendlyName(productType: String): String {
        return modelMap[productType] ?: if (productType.startsWith("iPhone")) {
            "iPhone ($productType)"
        } else if (productType.startsWith("iPad")) {
            "iPad ($productType)"
        } else {
            productType
        }
    }

    fun getUsbMode(productId: Int): UsbMode {
        return when (productId) {
            0x1281, 0x1282, 0x1283 -> UsbMode.RECOVERY
            0x1227, 0x1222 -> UsbMode.DFU
            in 0x1290..0x12af -> UsbMode.NORMAL
            else -> UsbMode.NORMAL
        }
    }
}
