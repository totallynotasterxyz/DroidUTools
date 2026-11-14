// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.pairing

import kotlinx.coroutines.flow.Flow

interface PairingStrategy {
    val method: PairingMethod
    fun pair(deviceId: Int, udid: String, productVersion: String): Flow<PairingState>
    fun validatePair(udid: String): Flow<PairingState>
    suspend fun unpair(udid: String): Result<Unit>
}
