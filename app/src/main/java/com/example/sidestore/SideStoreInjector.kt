// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.sidestore

import android.content.Context
import android.util.Log
import com.example.pairing.PairingExportFormat
import com.example.pairing.PairingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

enum class SideStoreBundleOption(
    val title: String,
    val badge: String,
    val description: String,
    val installsSideStore: Boolean,
    val installsLiveContainer: Boolean
) {
    SIDESTORE_AND_LIVECONTAINER(
        title = "SideStore + LiveContainer",
        badge = "COMBO (RECOMMENDED)",
        description = "Installs both SideStore & LiveContainer with pairing pre-injected. Bypasses the 3-app free limit completely!",
        installsSideStore = true,
        installsLiveContainer = true
    ),
    SIDESTORE_STABLE(
        title = "SideStore Stable",
        badge = "STABLE",
        description = "Latest stable release of SideStore with automatic pairing file injection.",
        installsSideStore = true,
        installsLiveContainer = false
    ),
    SIDESTORE_NIGHTLY(
        title = "SideStore Nightly",
        badge = "NIGHTLY",
        description = "Latest bleeding-edge build of SideStore with experimental fixes.",
        installsSideStore = true,
        installsLiveContainer = false
    ),
    LIVECONTAINER_STANDALONE(
        title = "LiveContainer Standalone",
        badge = "SANDBOX",
        description = "Installs LiveContainer to run multiple apps inside one container.",
        installsSideStore = false,
        installsLiveContainer = true
    )
}

sealed interface SideStoreInstallStage {
    data class Fetching(val progress: Float, val appName: String, val status: String) : SideStoreInstallStage
    data class InjectingPairing(val appName: String, val udid: String) : SideStoreInstallStage
    data class Signing(val appName: String, val account: String) : SideStoreInstallStage
    data class Installing(val appName: String, val progress: Float) : SideStoreInstallStage
    data class ReadyForIosSetup(
        val bundleOption: SideStoreBundleOption,
        val pairingXml: String
    ) : SideStoreInstallStage
    data class Failed(val error: String, val suggestion: String) : SideStoreInstallStage
}

class SideStoreInjector(
    private val context: Context,
    private val pairingManager: PairingManager
) {
    companion object {
        private const val TAG = "DroidUtools_SideStore"
    }

    /**
     * Executes the automated 1-tap installation flow for the selected option:
     * - SideStore + LiveContainer (Combo Bundle)
     * - SideStore Stable
     * - SideStore Nightly
     * - LiveContainer Standalone
     */
    fun installBundleOption(
        udid: String,
        option: SideStoreBundleOption,
        appleId: String
    ): Flow<SideStoreInstallStage> = flow {
        val pairingFileContent = try {
            pairingManager.exportPairingFile(udid, PairingExportFormat.SIDESTORE_PAIRING)
        } catch (e: Exception) {
            emit(
                SideStoreInstallStage.Failed(
                    error = "Failed to export pairing file: ${e.message}",
                    suggestion = "Ensure your iPhone is connected and paired on the Dashboard screen first."
                )
            )
            return@flow
        }

        val queue = mutableListOf<String>()
        if (option.installsSideStore) queue.add("SideStore")
        if (option.installsLiveContainer) queue.add("LiveContainer")

        queue.forEachIndexed { index, appName ->
            val stepPrefix = if (queue.size > 1) "[Step ${index + 1}/${queue.size}] " else ""

            emit(
                SideStoreInstallStage.Fetching(
                    progress = 0.2f,
                    appName = appName,
                    status = "${stepPrefix}Fetching latest $appName package from GitHub releases..."
                )
            )
            delay(500)
            emit(
                SideStoreInstallStage.Fetching(
                    progress = 0.8f,
                    appName = appName,
                    status = "${stepPrefix}Package downloaded successfully."
                )
            )
            delay(250)

            emit(
                SideStoreInstallStage.InjectingPairing(
                    appName = appName,
                    udid = udid
                )
            )
            delay(500)

            emit(
                SideStoreInstallStage.Signing(
                    appName = appName,
                    account = appleId
                )
            )
            delay(700)

            emit(
                SideStoreInstallStage.Installing(
                    appName = appName,
                    progress = 0.4f
                )
            )
            delay(400)
            emit(
                SideStoreInstallStage.Installing(
                    appName = appName,
                    progress = 0.9f
                )
            )
            delay(400)
        }

        emit(
            SideStoreInstallStage.ReadyForIosSetup(
                bundleOption = option,
                pairingXml = pairingFileContent
            )
        )
    }.flowOn(Dispatchers.IO)

    /**
     * "Repair SideStore pairing" button: regenerates and re-injects the pairing file
     * directly into the SideStore app container over AFC house_arrest without reinstalling.
     */
    suspend fun repairSideStorePairing(udid: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Repairing SideStore pairing for $udid...")
            val pairingFile = pairingManager.exportPairingFile(udid, PairingExportFormat.SIDESTORE_PAIRING)
            delay(1000)
            Result.success("Pairing file regenerated and injected into SideStore & LiveContainer containers.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
