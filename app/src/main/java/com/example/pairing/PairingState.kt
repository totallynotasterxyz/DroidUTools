// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.pairing

import com.example.data.entities.PairingRecordEntity

enum class PairingMethod(val title: String, val badge: String) {
    AUTO("Auto-Detection", "SMART"),
    RP_PAIRING("RPPairing (RemotePairing)", "iOS 17+"),
    LOCKDOWND("Lockdownd (Classic)", "COMPAT")
}

sealed interface PairingState {
    object Idle : PairingState
    data class Connecting(val method: PairingMethod, val details: String) : PairingState
    data class WaitingForTrust(val promptMessage: String, val method: PairingMethod) : PairingState
    data class ExchangingKeys(val method: PairingMethod, val step: String) : PairingState
    data class Paired(val record: PairingRecordEntity, val method: PairingMethod) : PairingState
    data class Failed(
        val error: String,
        val recoverableFix: String,
        val method: PairingMethod
    ) : PairingState
}
