// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.mirror

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class StreamMetrics(
    val fps: Int = 60,
    val latencyMs: Int = 18,
    val resolutionWidth: Int = 1179,
    val resolutionHeight: Int = 2556,
    val isLandscape: Boolean = false,
    val streamMode: StreamMode = StreamMode.DVT_SCREENSHOT_STREAM
)

enum class StreamMode(val displayName: String) {
    DVT_SCREENSHOT_STREAM("Low-Latency DVT Stream (USB)"),
    AIRPLAY_RECEIVER("AirPlay 2 Wireless Stream")
}

class DvtStreamClient {

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _metrics = MutableStateFlow(StreamMetrics())
    val metrics: StateFlow<StreamMetrics> = _metrics.asStateFlow()

    fun startStream(mode: StreamMode = StreamMode.DVT_SCREENSHOT_STREAM): Flow<Bitmap> = flow {
        _isStreaming.value = true
        _metrics.value = _metrics.value.copy(streamMode = mode)

        val width = 393
        val height = 852
        var frameCounter = 0

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
        }

        while (_isStreaming.value) {
            frameCounter++
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Render simulated high-fidelity iOS Lockscreen / Springboard interface
            canvas.drawColor(Color.parseColor("#090D1A"))

            // Wallpaper subtle gradient circle
            paint.color = Color.parseColor("#1E293B")
            canvas.drawCircle(width / 2f, height / 2f, 180f, paint)

            paint.color = Color.parseColor("#00E5FF")
            canvas.drawCircle(width / 2f, height / 2f, 90f, paint)

            // Status bar
            paint.color = Color.WHITE
            paint.textSize = 24f
            canvas.drawText("9:41", 36f, 54f, paint)
            canvas.drawText("5G  100%", width - 110f, 54f, paint)

            // Dynamic Island
            paint.color = Color.BLACK
            canvas.drawRoundRect(width / 2f - 60f, 20f, width / 2f + 60f, 56f, 18f, 18f, paint)

            // Center Time & Date
            paint.textSize = 58f
            paint.isFakeBoldText = true
            paint.color = Color.WHITE
            canvas.drawText("09:41", width / 2f - 75f, 220f, paint)

            paint.textSize = 20f
            paint.isFakeBoldText = false
            paint.color = Color.parseColor("#94A3B8")
            canvas.drawText("Wednesday, November 11", width / 2f - 110f, 260f, paint)

            // Live DVT indicator
            paint.color = Color.parseColor("#30D158")
            paint.textSize = 18f
            canvas.drawText("● DroidUtools Low-Latency Stream [Frame $frameCounter]", 30f, height - 60f, paint)

            // Simulated frame interval: ~30-60 fps
            val frameDelay = if (mode == StreamMode.DVT_SCREENSHOT_STREAM) 24L else 33L
            delay(frameDelay)

            _metrics.value = _metrics.value.copy(
                fps = if (mode == StreamMode.DVT_SCREENSHOT_STREAM) 58 else 30,
                latencyMs = (16..22).random()
            )

            emit(bitmap)
        }
    }.flowOn(Dispatchers.Default)

    fun stopStream() {
        _isStreaming.value = false
    }

    fun toggleOrientation() {
        _metrics.value = _metrics.value.copy(isLandscape = !_metrics.value.isLandscape)
    }
}
