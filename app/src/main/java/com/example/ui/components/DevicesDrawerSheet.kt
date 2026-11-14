// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.DeviceEntity
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusTunnel
import com.example.usb.AppleUsbDevice
import com.example.wireless.WirelessDiscoveredDevice
import com.example.wireless.WirelessReachability
import kotlinx.coroutines.launch

/**
 * Multi-Device Management Drawer Sheet listing live USB hub targets, Wireless LAN iOS endpoints,
 * and the persisted iOS device fleet with switching, pinning, custom nicknames, quick-pair, and forget controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesDrawerSheet(
    connectedUsbDevices: List<AppleUsbDevice>,
    savedDevices: List<DeviceEntity>,
    activeUdid: String?,
    onSelectDevice: (String) -> Unit,
    onRescanUsb: () -> Unit,
    onPairUsbDevice: (AppleUsbDevice) -> Unit,
    onUpdateNickname: (udid: String, nickname: String) -> Unit,
    onTogglePin: (udid: String) -> Unit,
    onForgetDevice: (udid: String) -> Unit,
    onDismiss: () -> Unit,
    wirelessDevices: List<WirelessDiscoveredDevice> = emptyList(),
    isScanningWifi: Boolean = false,
    onRescanWifi: () -> Unit = {},
    onAddManualWifiIp: suspend (host: String, port: Int, label: String) -> Result<WirelessDiscoveredDevice> = { host, port, _ ->
        Result.success(WirelessDiscoveredDevice(serviceName = host, hostIp = host, port = port))
    },
    onEnableWifiSync: (DeviceEntity) -> Unit = {}
) {
    val accent = MaterialTheme.colorScheme.primary
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var editingNicknameFor by remember { mutableStateOf<DeviceEntity?>(null) }
    var nicknameDraft by remember { mutableStateOf("") }
    var confirmingForgetDevice by remember { mutableStateOf<DeviceEntity?>(null) }

    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("62078") }
    var manualLabel by remember { mutableStateOf("") }
    var manualIpError by remember { mutableStateOf<String?>(null) }
    var isProbingManualIp by remember { mutableStateOf(false) }

    editingNicknameFor?.let { target ->
        AlertDialog(
            onDismissRequest = { editingNicknameFor = null },
            title = { Text("Device Nickname", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Assign an easy label for ${target.deviceName} (${target.udid.take(10)}…)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = nicknameDraft,
                        onValueChange = { nicknameDraft = it },
                        placeholder = { Text("e.g. Daily 15 Pro / TestBench SE") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateNickname(target.udid, nicknameDraft)
                        editingNicknameFor = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Save Label", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNicknameFor = null }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    confirmingForgetDevice?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmingForgetDevice = null },
            title = { Text("Forget Device?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    text = "Remove ${target.nickname.ifBlank { target.deviceName }} and its local pairing trust keys from DroidUtools?",
                    color = Color(0xFFCBD5E1)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onForgetDevice(target.udid)
                        confirmingForgetDevice = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Forget", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingForgetDevice = null }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isProbingManualIp) showManualIpDialog = false
            },
            title = { Text("Connect Wireless iOS by IP", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the iPhone/iPad Wi-Fi LAN IP address and lockdownd TCP port (62078 default).",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = manualHost,
                        onValueChange = {
                            manualHost = it
                            manualIpError = null
                        },
                        label = { Text("LAN IP or Hostname") },
                        placeholder = { Text("e.g. 192.168.1.42") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_wifi_host_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = {
                                manualPort = it
                                manualIpError = null
                            },
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(0.45f)
                                .testTag("manual_wifi_port_input")
                        )
                        OutlinedTextField(
                            value = manualLabel,
                            onValueChange = { manualLabel = it },
                            label = { Text("Label (Optional)") },
                            placeholder = { Text("Studio 15 Pro") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(0.55f)
                        )
                    }
                    manualIpError?.let { err ->
                        Text(err, color = StatusError, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hostTrimmed = manualHost.trim()
                        val parsedPort = manualPort.trim().toIntOrNull()
                        if (hostTrimmed.isBlank() || hostTrimmed.contains(" ")) {
                            manualIpError = "Enter a valid LAN IPv4/IPv6 address or hostname."
                        } else if (parsedPort == null || parsedPort !in 1..65535) {
                            manualIpError = "Port must be between 1 and 65535."
                        } else {
                            scope.launch {
                                isProbingManualIp = true
                                val res = onAddManualWifiIp(hostTrimmed, parsedPort, manualLabel.trim())
                                isProbingManualIp = false
                                res.onSuccess {
                                    showManualIpDialog = false
                                    manualHost = ""
                                    manualLabel = ""
                                    manualIpError = null
                                }.onFailure { err ->
                                    manualIpError = err.message ?: "Unable to save wireless endpoint."
                                }
                            }
                        }
                    },
                    enabled = !isProbingManualIp,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    modifier = Modifier.testTag("manual_wifi_probe_confirm")
                ) {
                    if (isProbingManualIp) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Probing…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Probe & Add", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualIpDialog = false },
                    enabled = !isProbingManualIp
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    val sortedSaved = remember(savedDevices) {
        savedDevices.sortedWith(
            compareByDescending<DeviceEntity> { it.isPinned }
                .thenByDescending { it.lastSeenTimestamp }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0B111E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Multi-Device Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val reachableWifiCount = wirelessDevices.count { it.reachability == WirelessReachability.REACHABLE }
                    Text(
                        text = "$reachableWifiCount Wi-Fi • ${connectedUsbDevices.size} USB hub • ${sortedSaved.size} saved",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }

                OutlinedButton(
                    onClick = onRescanUsb,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("drawer_rescan_usb_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan USB", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rescan Hubs", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "LIVE USB HUBS & OTG ENDPOINTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (connectedUsbDevices.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No Apple USB devices detected on OTG or powered hubs right now. Connect via Lightning/USB-C and tap Rescan Hubs.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(connectedUsbDevices, key = { "usb_${it.deviceId}_${it.serialNumber}" }) { usb ->
                        val resolvedUdid = usb.serialNumber.takeIf { it.isNotBlank() && it != "UnknownSerial" }
                            ?: "USB-${usb.deviceId}"
                        val isCurrent = activeUdid.equals(resolvedUdid, ignoreCase = true)

                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectDevice(resolvedUdid)
                                    onDismiss()
                                },
                            borderColor = if (isCurrent) GreenPrimary else Color(0xFF1E293B)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(GreenPrimary.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Usb, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(
                                            text = usb.productName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "ID #${usb.deviceId} • ${usb.usbMode.name} • ${resolvedUdid.take(14)}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { onPairUsbDevice(usb) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pair", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE WIRELESS LAN (mDNS & MANUAL IP)",
                            style = MaterialTheme.typography.labelSmall,
                            color = GreenPrimary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onRescanWifi,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("drawer_rescan_wifi_button")
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = "Scan Wi-Fi mDNS", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isScanningWifi) "Scanning…" else "Scan Wi-Fi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = { showManualIpDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("drawer_add_wifi_ip_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add IP", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Add IP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                if (wirelessDevices.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No wireless lockdownd endpoints found on LAN yet. Enable Wi-Fi Sync over USB once or tap Add IP to probe port 62078 directly.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(wirelessDevices, key = { "wifi_${it.hostIp}_${it.port}" }) { wifi ->
                        val isReachable = wifi.reachability == WirelessReachability.REACHABLE
                        val targetSelectUdid = savedDevices.firstOrNull { dev ->
                            (wifi.udidHint.isNotBlank() && wifi.udidHint.equals(dev.udid, ignoreCase = true)) ||
                                (wifi.wifiMacHint.isNotBlank() && wifi.wifiMacHint.equals(dev.wifiAddress, ignoreCase = true)) ||
                                dev.lastKnownIp.equals(wifi.hostIp, ignoreCase = true)
                        }?.udid ?: wifi.udidHint.ifBlank { "WIFI-${wifi.hostIp}" }

                        val isSelectedWifi = activeUdid.equals(targetSelectUdid, ignoreCase = true)

                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectDevice(targetSelectUdid)
                                    onDismiss()
                                }
                                .testTag("drawer_wifi_${wifi.hostIp}"),
                            borderColor = when {
                                isSelectedWifi -> accent
                                isReachable -> GreenPrimary.copy(alpha = 0.6f)
                                else -> Color(0xFF1E293B)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isReachable) GreenPrimary.copy(alpha = 0.18f)
                                                else Color(0xFF1E293B)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = if (isReachable) GreenPrimary else Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = wifi.serviceName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        val rttText = if (isReachable && wifi.latencyMs > 0) {
                                            "REACHABLE • ${wifi.latencyMs}ms"
                                        } else {
                                            wifi.reachability.name
                                        }
                                        Text(
                                            text = "${wifi.hostIp}:${wifi.port} • $rttText",
                                            color = if (isReachable) GreenPrimary else Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                if (wifi.isManual) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(accent.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text("MANUAL", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(StatusTunnel.copy(alpha = 0.16f))
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text("mDNS", color = StatusTunnel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SAVED FLEET & RECENT DEVICES",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                if (sortedSaved.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Saved devices will appear here automatically once detected or paired.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    items(sortedSaved, key = { it.udid }) { dev ->
                        val isSelected = dev.udid == activeUdid
                        val isUsbConnected = connectedUsbDevices.any { usb ->
                            usb.serialNumber.equals(dev.udid, ignoreCase = true) ||
                                usb.serialNumber.equals(dev.serialNumber, ignoreCase = true) ||
                                "USB-${usb.deviceId}" == dev.udid
                        }
                        val isWifiOnline = wirelessDevices.any { wifi ->
                            wifi.reachability == WirelessReachability.REACHABLE && (
                                (wifi.udidHint.isNotBlank() && wifi.udidHint.equals(dev.udid, ignoreCase = true)) ||
                                    (wifi.wifiMacHint.isNotBlank() && wifi.wifiMacHint.equals(dev.wifiAddress, ignoreCase = true)) ||
                                    (dev.lastKnownIp.isNotBlank() && wifi.hostIp.equals(dev.lastKnownIp, ignoreCase = true))
                                )
                        }

                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectDevice(dev.udid)
                                    onDismiss()
                                }
                                .testTag("drawer_device_${dev.udid}"),
                            borderColor = if (isSelected) accent else Color(0xFF1E293B)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isWifiOnline || isUsbConnected) GreenPrimary
                                                        else Color(0xFF64748B)
                                                    )
                                            )
                                            Text(
                                                text = dev.nickname.ifBlank { dev.deviceName },
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Active device",
                                                    tint = accent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        if (dev.nickname.isNotBlank()) {
                                            Text(
                                                text = dev.deviceName,
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Text(
                                            text = "iOS ${dev.productVersion} • ${dev.productType} • ${dev.udid.take(16)}",
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        if (dev.lastKnownIp.isNotBlank() || dev.isWifiSyncEnabled) {
                                            val wifiLabel = buildString {
                                                if (dev.lastKnownIp.isNotBlank()) append("LAN ${dev.lastKnownIp}:${dev.wirelessPort} • ")
                                                append(if (dev.isWifiSyncEnabled) "Wi-Fi Sync Armed" else "USB Only")
                                            }
                                            Text(
                                                text = wifiLabel,
                                                color = if (isWifiOnline) GreenPrimary else Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { onTogglePin(dev.udid) },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = if (dev.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                                contentDescription = if (dev.isPinned) "Unpin device" else "Pin device",
                                                tint = if (dev.isPinned) StatusTunnel else Color(0xFF64748B)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                nicknameDraft = dev.nickname
                                                editingNicknameFor = dev
                                            },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename device",
                                                tint = accent
                                            )
                                        }
                                        IconButton(
                                            onClick = { confirmingForgetDevice = dev },
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Forget device",
                                                tint = StatusError
                                            )
                                        }
                                    }
                                }

                                if (isUsbConnected && !dev.isWifiSyncEnabled) {
                                    OutlinedButton(
                                        onClick = { onEnableWifiSync(dev) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("drawer_enable_wifi_${dev.udid}")
                                    ) {
                                        Icon(Icons.Default.Wifi, contentDescription = null, tint = GreenPrimary, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Enable 1-Tap Wi-Fi Sync Over USB", color = GreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
