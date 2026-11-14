// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mirror.DvtStreamClient
import com.example.mirror.HardwareButton
import com.example.mirror.StreamMode
import com.example.mirror.WebDriverAgentClient
import com.example.ui.components.GlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusTunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun MirrorScreen(
    dvtStreamClient: DvtStreamClient,
    wdaClient: WebDriverAgentClient,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryColor = MaterialTheme.colorScheme.primary
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val metrics by dvtStreamClient.metrics.collectAsState()

    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var screenshotFeedback by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var showKeyboardField by remember { mutableStateOf(false) }

    // Start stream automatically when opening screen
    LaunchedEffect(Unit) {
        dvtStreamClient.startStream(StreamMode.DVT_SCREENSHOT_STREAM).collect { frame ->
            currentBitmap = frame
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            dvtStreamClient.stopStream()
        }
    }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingSeconds++
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(if (isFullscreen) 8.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tv, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                Text(
                    text = "Screen Mirror & Remote Control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { dvtStreamClient.toggleOrientation() }) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color(0xFFCBD5E1))
                }
                IconButton(onClick = { isFullscreen = !isFullscreen }) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Fullscreen",
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }
        }

        // Stream Canvas with HUD Overlay & Touch Input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF030712))
                .border(1.5.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            currentBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "iOS Screen Mirror",
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                scope.launch {
                                    wdaClient.performTap(offset.x, offset.y)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            var startX = 0f
                            var startY = 0f
                            detectDragGestures(
                                onDragStart = { startX = it.x; startY = it.y },
                                onDragEnd = {}
                            ) { change, dragAmount ->
                                scope.launch {
                                    wdaClient.performSwipe(startX, startY, change.position.x, change.position.y)
                                }
                            }
                        }
                )
            } ?: run {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Connecting DVT Stream...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }

            // Real-time Performance HUD Overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(GreenPrimary)
                )
                Text("${metrics.fps} FPS", color = GreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text("•", color = Color(0xFF64748B))
                Text("${metrics.latencyMs} ms", color = Color(0xFF64D2FF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Text("•", color = Color(0xFF64748B))
                Text(if (metrics.streamMode == StreamMode.DVT_SCREENSHOT_STREAM) "DVT USB" else "AirPlay", color = Color.White, fontSize = 11.sp)
            }

            // Recording indicator
            if (isRecording) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StatusError.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Text(
                        text = "REC ${String.format("%02d:%02d", recordingSeconds / 60, recordingSeconds % 60)}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Screenshot notification
            screenshotFeedback?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, GreenPrimary, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(msg, color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // On-screen Keyboard text input bar
        AnimatedVisibility(visible = showKeyboardField && !isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Type text to send to iPhone...", color = Color(0xFF64748B)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = primaryColor
                    ),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        if (textInput.isNotEmpty()) {
                            scope.launch {
                                wdaClient.sendKeys(textInput)
                                textInput = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send text")
                }
            }
        }

        // Remote Hardware Controls Dock (collapsed in Fullscreen mode)
        AnimatedVisibility(visible = !isFullscreen) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.8f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Primary Control Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DockButton(icon = Icons.Default.Home, label = "Home", tintColor = primaryColor, onClick = {
                            scope.launch { wdaClient.pressButton(HardwareButton.HOME) }
                        })
                        DockButton(icon = Icons.Default.Widgets, label = "Switcher", tintColor = primaryColor, onClick = {
                            scope.launch { wdaClient.pressButton(HardwareButton.APP_SWITCHER) }
                        })
                        DockButton(icon = Icons.Default.Lock, label = "Lock", tintColor = primaryColor, onClick = {
                            scope.launch { wdaClient.pressButton(HardwareButton.LOCK) }
                        })
                        DockButton(icon = Icons.Default.VolumeUp, label = "Vol +", tintColor = primaryColor, onClick = {
                            scope.launch { wdaClient.pressButton(HardwareButton.VOLUME_UP) }
                        })
                        DockButton(icon = Icons.Default.VolumeDown, label = "Vol -", tintColor = primaryColor, onClick = {
                            scope.launch { wdaClient.pressButton(HardwareButton.VOLUME_DOWN) }
                        })
                        DockButton(icon = Icons.Default.Keyboard, label = "Type", tintColor = primaryColor, onClick = {
                            showKeyboardField = !showKeyboardField
                        })
                    }

                    // Capture Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val frame = currentBitmap
                                scope.launch {
                                    if (frame == null) {
                                        screenshotFeedback = "No video frame received yet"
                                    } else {
                                        val savedPath = withContext(Dispatchers.IO) {
                                            val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                                                ?: File(context.filesDir, "screenshots").apply { mkdirs() }
                                            val file = File(dir, "ios_mirror_${System.currentTimeMillis()}.png")
                                            FileOutputStream(file).use { out ->
                                                frame.compress(Bitmap.CompressFormat.PNG, 100, out)
                                            }
                                            file.name
                                        }
                                        screenshotFeedback = "Saved $savedPath"
                                    }
                                    kotlinx.coroutines.delay(2200)
                                    screenshotFeedback = null
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Screenshot", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { isRecording = !isRecording },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRecording) Color(0xFF7F1D1D) else StatusTunnel,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRecording) "Stop Rec" else "Record Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tintColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tintColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color(0xFFCBD5E1)
        )
    }
}
