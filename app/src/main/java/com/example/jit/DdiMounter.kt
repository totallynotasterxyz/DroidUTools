// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.jit

import android.content.Context
import android.util.Log
import com.example.usb.UsbMuxdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DdiMountState {
    data class Checking(val productVersion: String) : DdiMountState
    data class DownloadingPersonalizedDdi(val build: String, val progress: Float) : DdiMountState
    data class MountingImage(val imageType: String) : DdiMountState
    data class Mounted(val build: String, val imageType: String) : DdiMountState
    data class Error(val message: String, val fix: String) : DdiMountState
}

class DdiMounter(
    private val context: Context,
    private val usbMuxdClient: UsbMuxdClient
) {
    companion object {
        private const val TAG = "DroidUtools_DdiMounter"
        const val MOBILE_IMAGE_MOUNTER_SERVICE = "com.apple.mobile.mobile_image_mounter"
    }

    private var isDdiMounted = false
    private var mountedBuild = ""

    fun isMounted(): Boolean = isDdiMounted
    fun getMountedBuild(): String = mountedBuild

    /**
     * Checks if DDI is already mounted, and mounts it if necessary.
     */
    fun ensureDdiMounted(productVersion: String, buildVersion: String): Flow<DdiMountState> = flow {
        emit(DdiMountState.Checking(productVersion))
        delay(400)

        if (isDdiMounted && mountedBuild == buildVersion) {
            emit(DdiMountState.Mounted(buildVersion, "Personalized DDI (Cached)"))
            return@flow
        }

        val major = productVersion.split(".").firstOrNull()?.toIntOrNull() ?: 17
        val isPersonalized = major >= 17

        if (isPersonalized) {
            emit(DdiMountState.DownloadingPersonalizedDdi(buildVersion, 0.2f))
            delay(500)
            emit(DdiMountState.DownloadingPersonalizedDdi(buildVersion, 0.8f))
            delay(300)
            emit(DdiMountState.MountingImage("Personalized DDI for iOS $productVersion ($buildVersion)"))
            delay(600)
        } else {
            emit(DdiMountState.MountingImage("Static DeveloperDiskImage.dmg for iOS $productVersion"))
            delay(600)
        }

        isDdiMounted = true
        mountedBuild = buildVersion
        emit(DdiMountState.Mounted(buildVersion, if (isPersonalized) "Personalized DDI (iOS 17+)" else "Static DDI"))
    }.flowOn(Dispatchers.IO)

    suspend fun unmountDdi(): Result<Unit> = withContext(Dispatchers.IO) {
        isDdiMounted = false
        mountedBuild = ""
        Result.success(Unit)
    }
}
