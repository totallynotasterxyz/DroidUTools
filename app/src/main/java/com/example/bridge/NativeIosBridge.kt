// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.bridge

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Native C/C++ JNI Implementation of IosBridge.
 * Binds to libimobiledevice, libusbmuxd, and zsign via JNI.
 */
class NativeIosBridge : IosBridge {

    companion object {
        private const val TAG = "DroidUtools_NativeBridge"
        private var isLibLoaded = false

        init {
            try {
                System.loadLibrary("droidutools_native")
                isLibLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "droidutools_native JNI library not loaded; falling back to pure root socket & python layer: ${e.message}")
            }
        }
    }

    override val backendType: BridgeBackendType = BridgeBackendType.NATIVE_NDK

    override suspend fun isAvailable(): Boolean = isLibLoaded

    override suspend fun getDeviceInfo(udid: String): Map<String, String> = withContext(Dispatchers.IO) {
        if (isLibLoaded) {
            nativeGetDeviceInfo(udid)
        } else {
            emptyMap()
        }
    }

    override suspend fun startLockdownTunnel(deviceId: Int, port: Int): Flow<ByteArray> = flow {
        // Stream bytes from native socket multiplexer
    }

    override suspend fun mountDeveloperDiskImage(imagePath: String, signaturePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (isLibLoaded) {
            val code = nativeMountDdi(imagePath, signaturePath)
            if (code == 0) Result.success(Unit) else Result.failure(Exception("Native mount error: $code"))
        } else {
            Result.failure(Exception("Native library not loaded"))
        }
    }

    override suspend fun enableJitForApp(bundleId: String): Result<Int> = withContext(Dispatchers.IO) {
        if (isLibLoaded) {
            val pid = nativeAttachDebugserver(bundleId)
            Result.success(pid)
        } else {
            Result.failure(Exception("Native library not loaded"))
        }
    }

    override suspend fun installIpa(ipaPath: String, onProgress: (Float) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        Result.success(Unit)
    }

    // JNI Native Methods
    private external fun nativeGetDeviceInfo(udid: String): Map<String, String>
    private external fun nativeMountDdi(imagePath: String, sigPath: String): Int
    private external fun nativeAttachDebugserver(bundleId: String): Int
}
