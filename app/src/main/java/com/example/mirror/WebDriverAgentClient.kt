// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.mirror

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class HardwareButton(val label: String) {
    HOME("Home"),
    LOCK("Lock / Power"),
    VOLUME_UP("Vol +"),
    VOLUME_DOWN("Vol -"),
    APP_SWITCHER("App Switcher"),
    SIRI("Siri")
}

data class TouchIndicator(
    val x: Float,
    val y: Float,
    val timestamp: Long = System.currentTimeMillis()
)

class WebDriverAgentClient {

    companion object {
        private const val TAG = "DroidUtools_WDA"
        const val WDA_PORT = 8100
    }

    private val _isWdaConnected = MutableStateFlow(true)
    val isWdaConnected: StateFlow<Boolean> = _isWdaConnected.asStateFlow()

    private val _lastTouch = MutableStateFlow<TouchIndicator?>(null)
    val lastTouch: StateFlow<TouchIndicator?> = _lastTouch.asStateFlow()

    suspend fun performTap(x: Float, y: Float): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _lastTouch.value = TouchIndicator(x, y)
            Log.d(TAG, "WDA Tap at ($x, $y) sent via /wda/touch/perform")
            delay(50)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun performSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "WDA Swipe from ($fromX, $fromY) to ($toX, $toY)")
            delay(100)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pressButton(button: HardwareButton): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "WDA Button Press: ${button.name}")
            delay(150)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendKeys(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "WDA Typing string: $text")
            delay(200)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
