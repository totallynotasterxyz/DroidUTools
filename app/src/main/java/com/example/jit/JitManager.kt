// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.jit

import android.content.Context
import android.util.Log
import com.example.service.JitKeepAliveService
import com.example.usb.UsbMuxdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

class JitManager(
    private val context: Context,
    private val usbMuxdClient: UsbMuxdClient,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DroidUtools_JitManager"
    }

    val ddiMounter = DdiMounter(context, usbMuxdClient)
    val debugServerClient = DebugServerClient(usbMuxdClient)

    private val _attachmentState = MutableStateFlow<JitAttachmentState>(JitAttachmentState.Idle)
    val attachmentState: StateFlow<JitAttachmentState> = _attachmentState.asStateFlow()

    private val _debuggableApps = MutableStateFlow<List<DebuggableApp>>(
        listOf(
            DebuggableApp(
                bundleId = "org.dolphin-emu.dolphin",
                appName = "Dolphin iOS",
                executableName = "Dolphin",
                version = "4.0.0",
                isPinned = true,
                autoJitOnLaunch = true
            ),
            DebuggableApp(
                bundleId = "com.flyinghead.Flycast",
                appName = "Flycast",
                executableName = "Flycast",
                version = "2.3",
                isPinned = true,
                autoJitOnLaunch = false
            ),
            DebuggableApp(
                bundleId = "net.kdt.pojavlauncher",
                appName = "PojavLauncher",
                executableName = "PojavLauncher",
                version = "3.3.1",
                isPinned = false,
                autoJitOnLaunch = true
            ),
            DebuggableApp(
                bundleId = "com.utmapp.UTM-SE",
                appName = "UTM SE",
                executableName = "UTM",
                version = "4.5.3",
                isPinned = false,
                autoJitOnLaunch = false
            ),
            DebuggableApp(
                bundleId = "com.stik.debug",
                appName = "StikDebug",
                executableName = "StikDebug",
                version = "1.2.0",
                isPinned = false,
                autoJitOnLaunch = false
            )
        )
    )
    val debuggableApps: StateFlow<List<DebuggableApp>> = _debuggableApps.asStateFlow()

    fun togglePinApp(bundleId: String) {
        _debuggableApps.value = _debuggableApps.value.map {
            if (it.bundleId == bundleId) it.copy(isPinned = !it.isPinned) else it
        }
    }

    fun toggleAutoJit(bundleId: String) {
        _debuggableApps.value = _debuggableApps.value.map {
            if (it.bundleId == bundleId) it.copy(autoJitOnLaunch = !it.autoJitOnLaunch) else it
        }
    }

    /**
     * Executes the JIT enabling pipeline:
     * 1. Check Developer Mode status
     * 2. Mount DDI / Personalized DDI if not mounted
     * 3. Route through RPPairing tunnel (iOS 17.4+) or usbmuxd port forwarding
     * 4. Attach debugserver via GDB/LLDB remote protocol
     * 5. Start JitKeepAliveService foreground notification
     */
    fun enableJit(
        app: DebuggableApp,
        productVersion: String = "17.4.1",
        buildVersion: String = "21E236",
        isDeveloperModeEnabled: Boolean = true
    ): Flow<JitAttachmentState> = flow {
        emit(JitAttachmentState.CheckingDeveloperMode(app.bundleId))
        delay(300)

        if (!isDeveloperModeEnabled) {
            emit(
                JitAttachmentState.Failed(
                    bundleId = app.bundleId,
                    error = "Developer Mode is disabled on this iOS device.",
                    suggestedFix = "On your iPhone, go to Settings → Privacy & Security → Developer Mode. Turn it ON, reboot your iPhone, and tap Turn On."
                )
            )
            return@flow
        }

        // Mount DDI if needed
        ddiMounter.ensureDdiMounted(productVersion, buildVersion).collect { ddiState ->
            when (ddiState) {
                is DdiMountState.DownloadingPersonalizedDdi -> {
                    emit(
                        JitAttachmentState.MountingDdi(
                            app.bundleId,
                            ddiState.progress,
                            "Downloading Personalized DDI for iOS $productVersion ($buildVersion)..."
                        )
                    )
                }
                is DdiMountState.MountingImage -> {
                    emit(
                        JitAttachmentState.MountingDdi(
                            app.bundleId,
                            0.9f,
                            "Mounting Developer Disk Image via mobile_image_mounter..."
                        )
                    )
                }
                is DdiMountState.Error -> {
                    emit(
                        JitAttachmentState.Failed(
                            bundleId = app.bundleId,
                            error = ddiState.message,
                            suggestedFix = ddiState.fix
                        )
                    )
                    return@collect
                }
                else -> {}
            }
        }

        // Route debugserver over tunnel
        val isIos17Plus = productVersion.split(".").firstOrNull()?.toIntOrNull() ?: 17 >= 17
        val tunnelName = if (isIos17Plus) "RPPairing Tunnel (iOS 17+)" else "Direct Usbmuxd (Classic)"

        emit(JitAttachmentState.ConnectingDebugserver(app.bundleId, tunnelName))
        delay(400)

        emit(JitAttachmentState.UnlockingMemory(app.bundleId, 0))

        val attachResult = debugServerClient.attachAndEnableJit(
            deviceId = 1,
            bundleId = app.bundleId,
            targetPid = null,
            isIos17Plus = isIos17Plus
        )

        if (attachResult.isSuccess) {
            val pid = attachResult.getOrNull() ?: 1420
            // Start Foreground Keep-Alive Service
            JitKeepAliveService.startKeepAlive(context, app.appName, pid)

            // Update app active state
            _debuggableApps.value = _debuggableApps.value.map {
                if (it.bundleId == app.bundleId) {
                    it.copy(activePid = pid, lastJitTimestamp = System.currentTimeMillis())
                } else {
                    it
                }
            }

            val activeState = JitAttachmentState.Active(
                bundleId = app.bundleId,
                appName = app.appName,
                pid = pid,
                tunnelType = tunnelName
            )
            _attachmentState.value = activeState
            emit(activeState)
        } else {
            val errState = JitAttachmentState.Failed(
                bundleId = app.bundleId,
                error = attachResult.exceptionOrNull()?.message ?: "Failed to connect to debugserver",
                suggestedFix = "Ensure the app is launched or running in the foreground on your iOS device."
            )
            _attachmentState.value = errState
            emit(errState)
        }
    }.flowOn(Dispatchers.IO)

    fun detachJit(bundleId: String) {
        JitKeepAliveService.stopKeepAlive(context)
        _debuggableApps.value = _debuggableApps.value.map {
            if (it.bundleId == bundleId) it.copy(activePid = null) else it
        }
        _attachmentState.value = JitAttachmentState.Idle
    }
}
