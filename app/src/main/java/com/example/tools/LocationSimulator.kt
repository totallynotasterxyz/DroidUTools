// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class SimulatedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

class LocationSimulator {

    companion object {
        val PRESETS = listOf(
            SimulatedLocation("Apple Park (Cupertino, CA)", 37.334643, -122.008972),
            SimulatedLocation("Tokyo Tower (Tokyo, Japan)", 35.658581, 139.745438),
            SimulatedLocation("Big Ben (London, UK)", 51.500729, -0.124625),
            SimulatedLocation("Eiffel Tower (Paris, France)", 48.858370, 2.294481),
            SimulatedLocation("Central Park (New York, NY)", 40.785091, -73.968285)
        )
    }

    private val _currentLocation = MutableStateFlow<SimulatedLocation?>(null)
    val currentLocation: StateFlow<SimulatedLocation?> = _currentLocation.asStateFlow()

    suspend fun setLocation(location: SimulatedLocation): Result<Unit> = withContext(Dispatchers.IO) {
        delay(400)
        _currentLocation.value = location
        Result.success(Unit)
    }

    suspend fun clearLocation(): Result<Unit> = withContext(Dispatchers.IO) {
        delay(200)
        _currentLocation.value = null
        Result.success(Unit)
    }
}
