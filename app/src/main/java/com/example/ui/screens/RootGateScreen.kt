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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.root.RootVerificationStatus
import com.example.ui.components.GlassCard
import com.example.ui.components.LogEntry
import com.example.ui.components.TerminalConsole
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning

@Composable
fun RootGateScreen(
    status: RootVerificationStatus,
    terminalLogs: List<LogEntry>,
    onRetryCheck: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showHelpDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val isChecking = status is RootVerificationStatus.Checking

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Glowing Shield Icon
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                StatusError.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(2.dp, StatusError.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = StatusError,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Headline
            Text(
                text = "Root Privileges Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = "DroidUtools requires systemless root (Magisk, KernelSU, or APatch) to turn your Android phone into an iOS USB host daemon. There is no degraded mode.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Status Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF0F172A).copy(alpha = 0.8f),
                borderColor = when (status) {
                    is RootVerificationStatus.Denied -> StatusError.copy(alpha = 0.5f)
                    is RootVerificationStatus.Missing -> StatusWarning.copy(alpha = 0.5f)
                    else -> Color(0xFF334155)
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = if (status is RootVerificationStatus.Denied) StatusError else StatusWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when (status) {
                                is RootVerificationStatus.Checking -> "Probing system for root permissions..."
                                is RootVerificationStatus.Denied -> "Root Request Denied"
                                is RootVerificationStatus.Missing -> "su Binary Not Found"
                                else -> "Waiting for user action"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9)
                        )
                    }

                    val details = when (status) {
                        is RootVerificationStatus.Denied -> "${status.reason}\n\nTip: ${status.suggestedFix}"
                        is RootVerificationStatus.Missing -> status.reason
                        else -> "Tap the button below to grant superuser access."
                    }

                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            // Why Root is Mandatory section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "WHY ROOT IS MANDATORY FOR IOS HOSTING",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                RequirementItem(
                    icon = Icons.Default.Cable,
                    title = "Raw USB Host Nodes (/dev/bus/usb)",
                    description = "Bypasses Android SELinux and user-space limits to send raw bulk/control USB transfers to Apple devices."
                )

                RequirementItem(
                    icon = Icons.Default.Memory,
                    title = "usbmuxd Daemon Supervisor",
                    description = "Runs the native usbmuxd multiplexer daemon as UID 0 to route Lockdown, AFC, and DebugServer sockets."
                )

                RequirementItem(
                    icon = Icons.Default.VpnLock,
                    title = "QUIC & RemoteXPC Tunnels (iOS 17+)",
                    description = "Creates virtual interfaces and tunnels for modern iOS 17+ CoreDevice and JIT debugging without a Mac."
                )
            }

            // Retry Button
            Button(
                onClick = onRetryCheck,
                enabled = !isChecking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Verifying Root...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Grant Root & Retry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Help expander toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showHelpDialog = !showHelpDialog }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFF64D2FF),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (showHelpDialog) "Hide Root Setup Guide" else "Show Root Setup Guide (Magisk / KSU / APatch)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF64D2FF)
                )
            }

            // Help Guide Tabs
            AnimatedVisibility(visible = showHelpDialog) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF030712)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = Color.Transparent,
                            contentColor = primaryColor,
                            indicator = { tabPositions ->
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = primaryColor
                                )
                            }
                        ) {
                            listOf("Magisk", "KernelSU", "APatch").forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTab == index,
                                    onClick = { selectedTab = index },
                                    text = {
                                        Text(
                                            text = title,
                                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                )
                            }
                        }

                        when (selectedTab) {
                            0 -> {
                                Text(
                                    text = "1. Open Magisk app on your Samsung A54.\n" +
                                            "2. Go to Superuser tab.\n" +
                                            "3. Find 'DroidUtools' and toggle Superuser Access to ON.\n" +
                                            "4. Ensure MagiskHide/Zygisk is not denying DroidUtools.\n" +
                                            "5. Return here and tap 'Grant Root & Retry'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            1 -> {
                                Text(
                                    text = "1. Open KernelSU Manager app.\n" +
                                            "2. Tap the Superuser tab at the bottom.\n" +
                                            "3. Locate 'DroidUtools' in the installed app list.\n" +
                                            "4. Enable 'Superuser' switch for DroidUtools.\n" +
                                            "5. Return here and tap 'Grant Root & Retry'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                            2 -> {
                                Text(
                                    text = "1. Open APatch manager application.\n" +
                                            "2. Switch to Superuser management screen.\n" +
                                            "3. Authorize DroidUtools with root privileges.\n" +
                                            "4. Return here and tap 'Grant Root & Retry'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }

            // Diagnostic Terminal Logs Drawer
            TerminalConsole(
                logs = terminalLogs,
                title = "Root Diagnostic Shell Output",
                isInitiallyExpanded = true,
                onClearLogs = onClearLogs
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RequirementItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0F172A).copy(alpha = 0.5f))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFFF1F5F9),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
