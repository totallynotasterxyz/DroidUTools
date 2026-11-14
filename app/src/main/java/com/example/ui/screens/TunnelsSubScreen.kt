// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tools.IproxyTunnelManager
import com.example.tools.TunnelRule
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusTunnel

/**
 * SSH & Multi-Port Forwarder (pure-Kotlin iproxy) managing localhost TCP listeners
 * bridged to active iOS usbmuxd service ports.
 */
@Composable
fun TunnelsSubScreen(
    udid: String,
    deviceId: Int,
    accent: Color,
    tunnelManager: IproxyTunnelManager,
    wirelessHost: String? = null,
    onLogActivity: (actionType: String, description: String, status: String) -> Unit
) {
    val tunnels by tunnelManager.tunnels.collectAsState()
    val clipboard = LocalClipboardManager.current
    var showAddDialog by remember { mutableStateOf(false) }
    var newLabel by remember { mutableStateOf("") }
    var newLocalPort by remember { mutableStateOf("2223") }
    var newDevicePort by remember { mutableStateOf("22") }
    var addError by remember { mutableStateOf<String?>(null) }
    var copiedNotice by remember { mutableStateOf<String?>(null) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add iproxy Tunnel Rule", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newLabel,
                        onValueChange = { newLabel = it },
                        label = { Text("Rule Label") },
                        placeholder = { Text("e.g. Frida Server / SFTP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newLocalPort,
                            onValueChange = {
                                newLocalPort = it
                                addError = null
                            },
                            label = { Text("Local TCP Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newDevicePort,
                            onValueChange = {
                                newDevicePort = it
                                addError = null
                            },
                            label = { Text("iOS Device Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    addError?.let { err ->
                        Text(err, color = StatusError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lp = newLocalPort.trim().toIntOrNull()
                        val dp = newDevicePort.trim().toIntOrNull()
                        if (lp == null || dp == null || lp !in 1024..65535 || dp !in 1..65535) {
                            addError = "Use valid unprivileged local port (1024..65535) and device port (1..65535)."
                        } else {
                            tunnelManager.addTunnelRule(newLabel, lp, dp, deviceId, udid, wirelessHost)
                            onLogActivity(
                                "TUNNEL",
                                "Added iproxy rule 127.0.0.1:$lp -> iOS:$dp via ${wirelessHost ?: "USB"}",
                                "SUCCESS"
                            )
                            showAddDialog = false
                            newLabel = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Create Rule", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    val activeCount = tunnels.count { it.isActive }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = if (activeCount > 0) StatusTunnel else accent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Lan, contentDescription = null, tint = StatusTunnel, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "SSH & Port Forwarder (iproxy)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            val routeLabel = if (!wirelessHost.isNullOrBlank()) {
                                "Wi-Fi Direct ($wirelessHost)"
                            } else {
                                "USB usbmuxd (#$deviceId)"
                            }
                            Text(
                                text = "$activeCount active relay(s) • Route: $routeLabel • ${udid.take(10)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!wirelessHost.isNullOrBlank()) GreenPrimary else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("tunnel_add_rule_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Port Rule", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        if (activeCount > 0) {
                            OutlinedButton(
                                onClick = {
                                    tunnelManager.stopAll()
                                    onLogActivity("TUNNEL", "Stopped all active iproxy tunnels", "INFO")
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Default.StopCircle, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop All", color = StatusError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    copiedNotice?.let { note ->
                        Text(note, color = GreenPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        items(tunnels, key = { it.id }) { rule ->
            TunnelRuleCard(
                rule = rule,
                accent = accent,
                sshCommand = tunnelManager.buildSshCommand(rule.localPort),
                onToggle = { enable ->
                    if (enable) {
                        tunnelManager.startTunnel(rule.id, deviceId, udid, wirelessHost)
                        onLogActivity(
                            "TUNNEL",
                            "Started tunnel ${rule.localPort} -> ${rule.devicePort} (${wirelessHost ?: "USB"})",
                            "SUCCESS"
                        )
                    } else {
                        tunnelManager.stopTunnel(rule.id)
                        onLogActivity("TUNNEL", "Stopped tunnel ${rule.localPort} -> ${rule.devicePort}", "INFO")
                    }
                },
                onCopySsh = { cmd ->
                    clipboard.setText(AnnotatedString(cmd))
                    copiedNotice = "✓ Copied: $cmd"
                },
                onDelete = { tunnelManager.removeTunnelRule(rule.id) }
            )
        }
    }
}

@Composable
private fun TunnelRuleCard(
    rule: TunnelRule,
    accent: Color,
    sshCommand: String,
    onToggle: (Boolean) -> Unit,
    onCopySsh: (String) -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (rule.isActive) GreenPrimary else Color(0xFF1E293B)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (rule.isActive) GreenPrimary else Color(0xFF475569))
                    )
                    Column {
                        Text(
                            text = rule.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "127.0.0.1:${rule.localPort}  ⇄  iOS:${rule.devicePort}",
                            color = accent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = rule.isActive,
                    onCheckedChange = onToggle
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val kb = rule.bytesTransferred / 1024.0
                Text(
                    text = "Conns: ${rule.activeConnections} • Relayed: ${String.format("%.1f", kb)} KB",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rule.devicePort == 22) {
                        OutlinedButton(
                            onClick = { onCopySsh(sshCommand) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy SSH command", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy SSH", fontSize = 11.sp)
                        }
                    }

                    if (!rule.id.endsWith("_default")) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete rule", tint = StatusError)
                        }
                    }
                }
            }

            rule.lastError?.let { err ->
                Text(err, color = StatusError, fontSize = 11.sp)
            }
        }
    }
}
