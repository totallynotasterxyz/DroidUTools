// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.DeviceEntity
import com.example.jit.DdiMountState
import com.example.jit.DebuggableApp
import com.example.jit.JitAttachmentState
import com.example.jit.JitManager
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun JitScreen(
    jitManager: JitManager,
    activeDevice: DeviceEntity? = null,
    onLogActivity: (actionType: String, description: String, status: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val apps by jitManager.debuggableApps.collectAsState()
    val attachmentState by jitManager.attachmentState.collectAsState()

    val primaryColor = MaterialTheme.colorScheme.primary
    val productVersion = activeDevice?.productVersion ?: "17.4.1"
    val buildVersion = activeDevice?.buildVersion ?: "21E236"
    val isDevMode = activeDevice?.isDeveloperModeEnabled ?: true

    var searchQuery by remember { mutableStateOf("") }
    var showDevModeHelp by remember { mutableStateOf(false) }
    var ddiStatusMessage by remember { mutableStateOf<String?>(null) }
    var isRemountingDdi by remember { mutableStateOf(false) }
    var liveAttachmentState by remember { mutableStateOf<JitAttachmentState>(JitAttachmentState.Idle) }
    val effectiveAttachmentState = if (liveAttachmentState !is JitAttachmentState.Idle) liveAttachmentState else attachmentState

    val filteredApps = apps.filter {
        searchQuery.isBlank() || it.appName.contains(searchQuery, ignoreCase = true) || it.bundleId.contains(searchQuery, ignoreCase = true)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Card: JIT Status & Personalized DDI
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = GreenPrimary
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(GreenPrimary.copy(alpha = 0.2f))
                                    .border(1.dp, GreenPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = GreenPrimary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "JIT Enabler & Tunnel",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "iOS 17.4+ CoreDevice RPPairing & Debugserver",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background((if (isDevMode) GreenPrimary else StatusWarning).copy(alpha = 0.15f))
                                .border(1.dp, (if (isDevMode) GreenPrimary else StatusWarning).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isDevMode) "DDI READY" else "DEV MODE OFF",
                                color = if (isDevMode) GreenPrimary else StatusWarning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // DDI Details Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF070B14))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Personalized DDI: iOS $productVersion ($buildVersion)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = ddiStatusMessage ?: "Debugserver service ready on port 1025-65535",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ddiStatusMessage != null) primaryColor else Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (!isRemountingDdi) {
                                    isRemountingDdi = true
                                    scope.launch {
                                        jitManager.ddiMounter.ensureDdiMounted(productVersion, buildVersion).collect { state ->
                                            when (state) {
                                                is DdiMountState.Checking -> ddiStatusMessage = "Checking mounted DDI signature..."
                                                is DdiMountState.DownloadingPersonalizedDdi -> ddiStatusMessage = "Fetching Personalized DDI ${(state.progress * 100).toInt()}%..."
                                                is DdiMountState.MountingImage -> ddiStatusMessage = "Mounting DDI image..."
                                                is DdiMountState.Mounted -> {
                                                    ddiStatusMessage = "${state.imageType} mounted (Build ${state.build})"
                                                    onLogActivity("DDI", "Mounted ${state.imageType} (Build ${state.build})", "SUCCESS")
                                                }
                                                is DdiMountState.Error -> {
                                                    ddiStatusMessage = "Mount error: ${state.message}"
                                                    onLogActivity("DDI", "DDI mount failed: ${state.message}", "FAILED")
                                                }
                                                else -> {}
                                            }
                                        }
                                        isRemountingDdi = false
                                    }
                                }
                            },
                            enabled = !isRemountingDdi,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("remount_ddi_button")
                        ) {
                            if (isRemountingDdi) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = primaryColor)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRemountingDdi) "Mounting..." else "Remount", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Active JIT Progress Card (when attaching)
        if (effectiveAttachmentState !is JitAttachmentState.Idle) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = when (effectiveAttachmentState) {
                        is JitAttachmentState.Active -> GreenPrimary
                        is JitAttachmentState.Failed -> StatusError
                        else -> primaryColor
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (effectiveAttachmentState) {
                                    is JitAttachmentState.Active -> {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                        Text("JIT Active & Unlocked!", fontWeight = FontWeight.Bold, color = GreenPrimary)
                                    }
                                    is JitAttachmentState.Failed -> {
                                        Icon(Icons.Default.Error, contentDescription = null, tint = StatusError, modifier = Modifier.size(20.dp))
                                        Text("JIT Attachment Error", fontWeight = FontWeight.Bold, color = StatusError)
                                    }
                                    else -> {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = primaryColor, strokeWidth = 2.dp)
                                        Text("Enabling JIT...", fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }

                            if (effectiveAttachmentState is JitAttachmentState.Active) {
                                val active = effectiveAttachmentState as JitAttachmentState.Active
                                OutlinedButton(
                                    onClick = {
                                        jitManager.detachJit(active.bundleId)
                                        liveAttachmentState = JitAttachmentState.Idle
                                        onLogActivity("JIT", "Detached JIT from ${active.appName}", "SUCCESS")
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Detach", fontSize = 11.sp)
                                }
                            }
                        }

                        when (val state = effectiveAttachmentState) {
                            is JitAttachmentState.CheckingDeveloperMode -> {
                                Text("Verifying Developer Mode status on iOS...", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is JitAttachmentState.MountingDdi -> {
                                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = primaryColor)
                                Text(state.status, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is JitAttachmentState.ConnectingDebugserver -> {
                                Text("Connecting to debugserver via ${state.tunnelPath}...", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is JitAttachmentState.UnlockingMemory -> {
                                Text("Sending LLDB remote packets (QStartNoAckMode, vAttach, c)...", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is JitAttachmentState.Active -> {
                                Text(
                                    text = "Process ${state.appName} (PID ${state.pid}) is running with CS_DEBUGGED memory permissions. Keep this app running in background.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                            is JitAttachmentState.Failed -> {
                                Text(state.error, color = StatusError, fontSize = 12.sp)
                                Text("Fix: ${state.suggestedFix}", color = StatusWarning, fontSize = 12.sp)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // Developer Mode Instruction Expander
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDevModeHelp = !showDevModeHelp },
                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.5f)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(18.dp))
                            Text("Developer Mode Requirement (iOS 16+)", style = MaterialTheme.typography.titleSmall, color = Color(0xFF64D2FF), fontWeight = FontWeight.SemiBold)
                        }
                        Text(if (showDevModeHelp) "Hide" else "Show", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }

                    AnimatedVisibility(visible = showDevModeHelp) {
                        Text(
                            text = "Apple requires Developer Mode to allow debugserver attach:\n" +
                                    "1. On iPhone, open Settings → Privacy & Security\n" +
                                    "2. Scroll to the bottom and tap 'Developer Mode'\n" +
                                    "3. Toggle it ON and tap 'Restart'\n" +
                                    "4. After reboot, unlock iPhone and tap 'Turn On' when prompted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search debuggable emulators & apps...", color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Section Title
        item {
            Text(
                text = "DEBUGGABLE APPLICATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = primaryColor,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Apps List
        items(filteredApps) { app ->
            DebugAppCard(
                app = app,
                accentColor = primaryColor,
                onTogglePin = { jitManager.togglePinApp(app.bundleId) },
                onToggleAutoJit = { jitManager.toggleAutoJit(app.bundleId) },
                onEnableJit = {
                    scope.launch {
                        jitManager.enableJit(
                            app = app,
                            productVersion = productVersion,
                            buildVersion = buildVersion,
                            isDeveloperModeEnabled = isDevMode
                        ).collect { st ->
                            liveAttachmentState = st
                            if (st is JitAttachmentState.Active) {
                                onLogActivity("JIT", "Enabled JIT for ${st.appName} (PID ${st.pid})", "SUCCESS")
                            } else if (st is JitAttachmentState.Failed) {
                                onLogActivity("JIT", "JIT failed for ${app.appName}: ${st.error}", "FAILED")
                            }
                        }
                    }
                },
                onDetachJit = {
                    jitManager.detachJit(app.bundleId)
                    liveAttachmentState = JitAttachmentState.Idle
                    onLogActivity("JIT", "Detached JIT from ${app.appName}", "SUCCESS")
                }
            )
        }
    }
}

@Composable
private fun DebugAppCard(
    app: DebuggableApp,
    accentColor: Color,
    onTogglePin: () -> Unit,
    onToggleAutoJit: () -> Unit,
    onEnableJit: () -> Unit,
    onDetachJit: () -> Unit
) {
    val isRunningJit = app.activePid != null

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isRunningJit) GreenPrimary.copy(alpha = 0.6f) else Color(0xFF1E293B),
        backgroundColor = if (isRunningJit) Color(0xFF091410) else Color(0xFF0F172A).copy(alpha = 0.6f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isRunningJit) GreenPrimary.copy(alpha = 0.2f) else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = if (isRunningJit) GreenPrimary else accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (isRunningJit) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GreenPrimary.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PID ${app.activePid}",
                                    color = GreenPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Text(
                        text = app.bundleId,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                IconButton(onClick = onTogglePin) {
                    Icon(
                        imageVector = if (app.isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Pin app",
                        tint = if (app.isPinned) Color(0xFFFFD60A) else Color(0xFF64748B)
                    )
                }
            }

            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = app.autoJitOnLaunch,
                        onCheckedChange = { onToggleAutoJit() },
                        colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.3f))
                    )
                    Text(
                        text = "JIT on Launch",
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }

                if (isRunningJit) {
                    Button(
                        onClick = onDetachJit,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Detach JIT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onEnableJit,
                        colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enable JIT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
