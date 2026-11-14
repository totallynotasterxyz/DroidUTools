// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.bridge

import kotlinx.coroutines.flow.Flow

/**
 * Unified iOS Bridge interface abstracting Native C/C++ (libimobiledevice/usbmuxd/zsign)
 * and Python (pymobiledevice3 / RemoteXPC / CoreDevice) implementations.
 */
interface IosBridge {
    val backendType: BridgeBackendType

    suspend fun isAvailable(): Boolean

    suspend fun getDeviceInfo(udid: String): Map<String, String>

    suspend fun startLockdownTunnel(deviceId: Int, port: Int): Flow<ByteArray>

    suspend fun mountDeveloperDiskImage(imagePath: String, signaturePath: String): Result<Unit>

    suspend fun enableJitForApp(bundleId: String): Result<Int> // returns debugserver PID

    suspend fun installIpa(ipaPath: String, onProgress: (Float) -> Unit): Result<Unit>
}

enum class BridgeBackendType(val displayName: String) {
    NATIVE_NDK("Native NDK (libimobiledevice / usbmuxd)"),
    PYTHON_CHAQUOPY("Python Chaquopy (pymobiledevice3 / RemoteXPC)")
}
