// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.pairing

import android.content.Context
import android.util.Log
import com.example.data.PreferencesManager
import com.example.data.dao.PairingRecordDao
import com.example.data.entities.PairingRecordEntity
import com.example.usb.UsbMuxdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PairingManager(
    private val context: Context,
    private val pairingRecordDao: PairingRecordDao,
    private val preferencesManager: PreferencesManager,
    private val usbMuxdClient: UsbMuxdClient,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DroidUtools_PairingMgr"
    }

    private val lockdowndStrategy = LockdowndStrategy(context, pairingRecordDao, usbMuxdClient)
    private val rpPairingStrategy = RPPairingStrategy(context, pairingRecordDao, usbMuxdClient)

    private val _pairingState = MutableStateFlow<PairingState>(PairingState.Idle)
    val pairingState: StateFlow<PairingState> = _pairingState.asStateFlow()

    private val _activePairingMethodBadge = MutableStateFlow("UNPAIRED")
    val activePairingMethodBadge: StateFlow<String> = _activePairingMethodBadge.asStateFlow()

    fun initiatePairing(
        deviceId: Int,
        udid: String,
        productVersion: String,
        forceMethod: PairingMethod? = null
    ) {
        scope.launch {
            val userPreference = preferencesManager.pairingPreferenceFlow.first()
            val targetMethod = forceMethod ?: when (userPreference) {
                "RP_PAIRING" -> PairingMethod.RP_PAIRING
                "LOCKDOWND" -> PairingMethod.LOCKDOWND
                else -> PairingMethod.AUTO
            }

            Log.i(TAG, "Starting pairing for UDID $udid (iOS $productVersion), chosen mode: $targetMethod")

            when (targetMethod) {
                PairingMethod.LOCKDOWND -> {
                    _activePairingMethodBadge.value = "LOCKDOWND"
                    lockdowndStrategy.pair(deviceId, udid, productVersion).collect { state ->
                        _pairingState.value = state
                    }
                }
                PairingMethod.RP_PAIRING -> {
                    _activePairingMethodBadge.value = "RP_PAIRING"
                    rpPairingStrategy.pair(deviceId, udid, productVersion).collect { state ->
                        _pairingState.value = state
                    }
                }
                PairingMethod.AUTO -> {
                    // Modern Auto Logic: Try RPPairing if iOS >= 17, else Lockdownd. Fallback automatically.
                    val majorVer = productVersion.split(".").firstOrNull()?.toIntOrNull() ?: 17
                    if (majorVer >= 17) {
                        _activePairingMethodBadge.value = "RP_PAIRING"
                        var rpSucceeded = false

                        rpPairingStrategy.pair(deviceId, udid, productVersion).collect { state ->
                            _pairingState.value = state
                            if (state is PairingState.Paired) {
                                rpSucceeded = true
                            } else if (state is PairingState.Failed) {
                                Log.w(TAG, "RPPairing failed (${state.error}). Executing automated fallback to Lockdownd...")
                            }
                        }

                        // If RPPairing failed or unsupported, smoothly fallback to Lockdownd
                        if (!rpSucceeded) {
                            _activePairingMethodBadge.value = "LOCKDOWND"
                            _pairingState.value = PairingState.Connecting(
                                PairingMethod.LOCKDOWND,
                                "RPPairing unavailable. Falling back automatically to Lockdownd (Classic)..."
                            )
                            lockdowndStrategy.pair(deviceId, udid, productVersion).collect { fallbackState ->
                                _pairingState.value = fallbackState
                            }
                        }
                    } else {
                        _activePairingMethodBadge.value = "LOCKDOWND"
                        lockdowndStrategy.pair(deviceId, udid, productVersion).collect { state ->
                            _pairingState.value = state
                        }
                    }
                }
            }
        }
    }

    suspend fun getPairingRecord(udid: String): PairingRecordEntity? = withContext(Dispatchers.IO) {
        pairingRecordDao.getPairingRecord(udid)
    }

    suspend fun unpair(udid: String): Result<Unit> = withContext(Dispatchers.IO) {
        val r1 = lockdowndStrategy.unpair(udid)
        val r2 = rpPairingStrategy.unpair(udid)
        _activePairingMethodBadge.value = "UNPAIRED"
        _pairingState.value = PairingState.Idle
        if (r1.isSuccess || r2.isSuccess) Result.success(Unit) else r1
    }

    /**
     * Exports pairing record into any requested format for external sideloading tools.
     */
    suspend fun exportPairingFile(udid: String, format: PairingExportFormat): String = withContext(Dispatchers.IO) {
        val record = pairingRecordDao.getPairingRecord(udid)
            ?: throw IllegalStateException("Device $udid is not paired yet.")

        when (format) {
            PairingExportFormat.LOCKDOWN_PLIST -> record.rawPlistXml
            PairingExportFormat.REMOTE_PAIRING -> record.rawPlistXml
            PairingExportFormat.SIDESTORE_PAIRING -> {
                PairingCertificateUtil.createSideStorePairingFile(
                    hostId = record.hostId,
                    systemBuid = record.systemBuid,
                    rootCertBase64 = record.rootCertificatePem.replace("-----BEGIN CERTIFICATE-----\n", "").replace("\n-----END CERTIFICATE-----", "").replace("\n", ""),
                    hostCertBase64 = record.hostCertificatePem.replace("-----BEGIN CERTIFICATE-----\n", "").replace("\n-----END CERTIFICATE-----", "").replace("\n", ""),
                    deviceCertBase64 = record.deviceCertificatePem.replace("-----BEGIN CERTIFICATE-----\n", "").replace("\n-----END CERTIFICATE-----", "").replace("\n", ""),
                    hostPrivateKeyBase64 = record.hostPrivateKeyPem.replace("-----BEGIN RSA PRIVATE KEY-----\n", "").replace("\n-----END RSA PRIVATE KEY-----", "").replace("\n", ""),
                    wifiMac = record.wifiMacAddress
                )
            }
            PairingExportFormat.STIKDEBUG_FORMAT -> {
                PairingCertificateUtil.createStikDebugJson(
                    udid = record.udid,
                    hostId = record.hostId,
                    systemBuid = record.systemBuid,
                    rootCertPem = record.rootCertificatePem,
                    hostCertPem = record.hostCertificatePem,
                    deviceCertPem = record.deviceCertificatePem,
                    hostPrivateKeyPem = record.hostPrivateKeyPem,
                    wifiMac = record.wifiMacAddress
                )
            }
        }
    }
}
