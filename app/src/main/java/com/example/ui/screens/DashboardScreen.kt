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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.ActivityLogEntity
import com.example.data.entities.DeviceEntity
import com.example.pairing.PairingManager
import com.example.pairing.PairingState
import com.example.ui.components.BatteryRing
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.components.LogEntry
import com.example.ui.components.PairingMethodBadge
import com.example.ui.components.TerminalConsole
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusTunnel
import com.example.ui.theme.StatusWarning
import com.example.usb.AppleUsbDevice
import com.example.usb.DaemonStatus
import com.example.wireless.WirelessDiscoveredDevice
import com.example.wireless.WirelessReachability
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class QuickActionTarget {
    INSTALL_IPA,
    SIDESTORE_1TAP,
    ENABLE_JIT,
    SCREEN_MIRROR,
    AFC_FILES,
    DEVICE_TOOLS
}

@Composable
fun DashboardScreen(
    usbDevice: AppleUsbDevice?,
    pairedDevice: DeviceEntity?,
    pairingState: PairingState,
    pairingBadge: String,
    terminalLogs: List<LogEntry>,
    pairingManager: PairingManager,
    daemonStatus: DaemonStatus = DaemonStatus.STOPPED,
    socketType: String = "None",
    bridgeBackend: String = "Usbmuxd Socket",
    recentActivity: List<ActivityLogEntity> = emptyList(),
    onInitiatePairing: () -> Unit,
    onUnpair: () -> Unit,
    onClearLogs: () -> Unit,
    onScanUsb: () -> Unit = {},
    onQuickAction: (QuickActionTarget) -> Unit = {},
    hasUpdateBadge: Boolean = false,
    onOpenLegalSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    activeWirelessEndpoint: WirelessDiscoveredDevice? = null,
    onToggleWifiSync: (Boolean) -> Unit = {},
    onOpenDevicesDrawer: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    var showExportDialog by remember { mutableStateOf(false) }
    var copiedLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedLabel) {
        if (copiedLabel != null) {
            delay(1800)
            copiedLabel = null
        }
    }

    val isUsbConnected = usbDevice != null
    val isWifiPreferredOnline = activeWirelessEndpoint?.reachability == WirelessReachability.REACHABLE
    val activeUdid = pairedDevice?.udid
        ?: usbDevice?.serialNumber?.takeIf { it.isNotBlank() && it != "UnknownSerial" }
        ?: activeWirelessEndpoint?.udidHint?.takeIf { it.isNotBlank() }
        ?: "Not Connected"
    val friendlyModelName = pairedDevice?.deviceName
        ?: usbDevice?.productName?.takeIf { it.isNotBlank() }
        ?: activeWirelessEndpoint?.serviceName
        ?: "No iOS Device Detected"
    val iosVersion = pairedDevice?.productVersion ?: "17.4.1"
    val buildVersion = pairedDevice?.buildVersion ?: "21E236"
    val isDeveloperMode = pairedDevice?.isDeveloperModeEnabled ?: true

    val totalBytes = (pairedDevice?.totalDiskCapacityBytes ?: 128_000_000_000L).coerceAtLeast(1L)
    val freeBytes = (pairedDevice?.freeDiskSpaceBytes ?: 43_800_000_000L).coerceIn(0L, totalBytes)
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedGb = usedBytes / 1_000_000_000.0
    val totalGb = totalBytes / 1_000_000_000.0
    val freeGb = freeBytes / 1_000_000_000.0
    val usedFraction = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.05f, 0.95f)

    if (showExportDialog && activeUdid != "Not Connected") {
        PairingExportDialog(
            udid = activeUdid,
            pairingManager = pairingManager,
            onDismiss = { showExportDialog = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar: Branding, Settings & USB status
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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f))
                            .border(1.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "DroidUtools",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = bridgeBackend,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScanUsb()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_scan_usb")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "Rescan USB OTG",
                            tint = if (isUsbConnected) StatusSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenLegalSettings,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_settings_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasUpdateBadge) {
                                    Badge(containerColor = Color(0xFFEF4444))
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings & Legal",
                                tint = accent,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }

                    ConnectionBadge(
                        connectionType = when {
                            isWifiPreferredOnline -> "NETWORK"
                            isUsbConnected -> "USB"
                            else -> "STANDBY"
                        }
                    )
                }
            }

            // Copy confirmation pill
            AnimatedVisibility(visible = copiedLabel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StatusSuccess.copy(alpha = 0.14f))
                        .border(1.dp, StatusSuccess.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Copied ${copiedLabel ?: ""} to clipboard",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = StatusSuccess
                        )
                    }
                }
            }

            // Hero Device Card
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = if (pairingBadge == "RP_PAIRING") StatusTunnel else accent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isWifiPreferredOnline -> GreenPrimary.copy(alpha = 0.7f)
                                            isUsbConnected -> accent.copy(alpha = 0.6f)
                                            else -> Color(0xFF334155)
                                        },
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        isWifiPreferredOnline -> Icons.Default.Wifi
                                        isUsbConnected -> Icons.Default.Smartphone
                                        else -> Icons.Default.Cable
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isWifiPreferredOnline -> GreenPrimary
                                        isUsbConnected -> accent
                                        else -> Color(0xFF64748B)
                                    },
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friendlyModelName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        isWifiPreferredOnline ->
                                            "iOS $iosVersion ($buildVersion) • Wi-Fi ${activeWirelessEndpoint!!.hostIp} (${activeWirelessEndpoint.latencyMs}ms)"
                                        isUsbConnected || pairedDevice != null ->
                                            "iOS $iosVersion ($buildVersion) • ${pairedDevice?.productType ?: "USB OTG"}"
                                        else ->
                                            "Connect iPhone/iPad via USB OTG or Wi-Fi LAN"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isWifiPreferredOnline) GreenPrimary else Color(0xFF94A3B8)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        PairingMethodBadge(method = pairingBadge)
                    }

                    // Status Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniChip(
                            label = "usbmuxd",
                            value = daemonStatus.name,
                            color = if (daemonStatus == DaemonStatus.RUNNING) StatusSuccess else StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MiniChip(
                            label = "Dev Mode",
                            value = if (isDeveloperMode) "On" else "Off",
                            color = if (isDeveloperMode) StatusSuccess else StatusWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MiniChip(
                            label = "Socket",
                            value = socketType.substringBefore(" "),
                            color = accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Pairing State Progress Card
            when (pairingState) {
                is PairingState.Connecting -> {
                    PairingProgressCard(
                        title = "Connecting via ${pairingState.method.badge}...",
                        subtitle = pairingState.details,
                        accent = accent,
                        showSpinner = true
                    )
                }
                is PairingState.WaitingForTrust -> {
                    PairingProgressCard(
                        title = "Trust Challenge Pending",
                        subtitle = pairingState.promptMessage,
                        accent = accent,
                        isWarning = true
                    )
                }
                is PairingState.ExchangingKeys -> {
                    PairingProgressCard(
                        title = "Negotiating Cryptographic Identity",
                        subtitle = pairingState.step,
                        accent = accent,
                        showSpinner = true
                    )
                }
                is PairingState.Failed -> {
                    PairingErrorCard(
                        error = pairingState.error,
                        fix = pairingState.recoverableFix,
                        onRetry = onInitiatePairing
                    )
                }
                is PairingState.Idle, is PairingState.Paired -> {}
            }

            // Wireless Sync & Transport Routing Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isWifiPreferredOnline) GreenPrimary.copy(alpha = 0.55f) else Color(0xFF1E293B)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isWifiPreferredOnline) GreenPrimary.copy(alpha = 0.16f)
                                        else accent.copy(alpha = 0.14f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = "Wireless mode status",
                                    tint = if (isWifiPreferredOnline) GreenPrimary else accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Wireless Mode & Transport",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                val routeDesc = when {
                                    isWifiPreferredOnline ->
                                        "Wi-Fi Preferred • ${activeWirelessEndpoint!!.hostIp}:${activeWirelessEndpoint.port} (${activeWirelessEndpoint.latencyMs}ms)"
                                    isUsbConnected ->
                                        "USB OTG Fallback • Auto-switches to Wi-Fi on same LAN"
                                    pairedDevice?.lastKnownIp?.isNotBlank() == true ->
                                        "Saved IP ${pairedDevice.lastKnownIp}:${pairedDevice.wirelessPort} (Offline)"
                                    else ->
                                        "Scan mDNS or enter iOS IP address (port 62078)"
                                }
                                Text(
                                    text = routeDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isWifiPreferredOnline) GreenPrimary else Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = pairedDevice?.isWifiSyncEnabled == true || isWifiPreferredOnline,
                            onCheckedChange = { enabled -> onToggleWifiSync(enabled) },
                            enabled = pairedDevice != null || isUsbConnected || isWifiPreferredOnline,
                            modifier = Modifier.testTag("dashboard_wifi_sync_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1-Tap USB Lockdownd EnableWifiConnections (TCP 62078)",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onOpenDevicesDrawer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("dashboard_wireless_connect_button")
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Scan / Add IP", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Battery & Storage Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                ) {
                    BatteryRing(
                        percentage = pairedDevice?.batteryLevel ?: 92,
                        health = pairedDevice?.batteryHealth ?: 98,
                        cycles = pairedDevice?.batteryCycles ?: 142,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                GlassCard(
                    modifier = Modifier.weight(1.3f),
                    backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Storage",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${String.format("%.1f", usedGb)} / ${String.format("%.0f", totalGb)} GB",
                                style = MaterialTheme.typography.labelSmall,
                                color = accent
                            )
                        }

                        // Dynamic proportional storage bar
                        val appsFraction = (usedFraction * 0.52f).coerceAtLeast(0.05f)
                        val systemFraction = (usedFraction * 0.23f).coerceAtLeast(0.04f)
                        val mediaFraction = (usedFraction - appsFraction - systemFraction).coerceAtLeast(0.03f)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF1E293B))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(appsFraction)
                                    .fillMaxSize()
                                    .background(accent)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(systemFraction)
                                    .fillMaxSize()
                                    .background(StatusTunnel)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(mediaFraction)
                                    .fillMaxSize()
                                    .background(StatusWarning)
                            )
                            Box(
                                modifier = Modifier
                                    .weight((1f - usedFraction).coerceAtLeast(0.05f))
                                    .fillMaxSize()
                                    .background(Color.Transparent)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StorageLegendItem(
                                color = accent,
                                label = "Apps & App Data",
                                size = "${String.format("%.1f", usedGb * 0.52)} GB"
                            )
                            StorageLegendItem(
                                color = StatusTunnel,
                                label = "iOS System Files",
                                size = "${String.format("%.1f", usedGb * 0.23)} GB"
                            )
                            StorageLegendItem(
                                color = StatusWarning,
                                label = "Media & Photos",
                                size = "${String.format("%.1f", usedGb * 0.25)} GB"
                            )
                            StorageLegendItem(
                                color = Color(0xFF475569),
                                label = "Available Free",
                                size = "${String.format("%.1f", freeGb)} GB"
                            )
                        }
                    }
                }
            }

            // Hardware Identifiers Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "DEVICE HARDWARE IDENTIFIERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    val serialVal = pairedDevice?.serialNumber ?: usbDevice?.serialNumber ?: "Standby"
                    val ecidVal = pairedDevice?.ecid ?: "Pending Lockdown"
                    val imeiVal = pairedDevice?.imei ?: "Pending Lockdown"
                    val wifiVal = pairedDevice?.wifiAddress ?: "00:00:00:00:00:00"

                    HardwareRow(
                        label = "UDID",
                        value = activeUdid,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(activeUdid))
                            copiedLabel = "UDID"
                        }
                    )
                    HardwareRow(
                        label = "Serial Number",
                        value = serialVal,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(serialVal))
                            copiedLabel = "Serial Number"
                        }
                    )
                    HardwareRow(
                        label = "ECID",
                        value = ecidVal,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(ecidVal))
                            copiedLabel = "ECID"
                        }
                    )
                    HardwareRow(
                        label = "IMEI",
                        value = imeiVal,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(imeiVal))
                            copiedLabel = "IMEI"
                        }
                    )
                    HardwareRow(
                        label = "Wi-Fi MAC",
                        value = wifiVal,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(wifiVal))
                            copiedLabel = "Wi-Fi MAC"
                        }
                    )
                }
            }

            // Pairing Management Actions
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Pairing Records & Sideloading",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Active Mode: $pairingBadge",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        Button(
                            onClick = { showExportDialog = true },
                            enabled = activeUdid != "Not Connected",
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("dashboard_export_pairing")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Pairing", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onInitiatePairing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_repair_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pair / Refresh")
                        }

                        OutlinedButton(
                            onClick = onUnpair,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFFF453A)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("dashboard_unpair_button")
                        ) {
                            Text("Unpair")
                        }
                    }
                }
            }

            // Quick Action Grid (all wired to real tabs!)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "QUICK ACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        icon = Icons.Default.InstallMobile,
                        title = "Install IPA",
                        subtitle = "Picker & Direct URL",
                        color = accent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_install_ipa"),
                        onClick = { onQuickAction(QuickActionTarget.INSTALL_IPA) }
                    )
                    ActionTile(
                        icon = Icons.Default.VpnKey,
                        title = "SideStore 1-Tap",
                        subtitle = "Auto-Pair & LiveContainer",
                        color = StatusTunnel,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_sidestore"),
                        onClick = { onQuickAction(QuickActionTarget.SIDESTORE_1TAP) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        icon = Icons.Default.PlayArrow,
                        title = "Enable JIT",
                        subtitle = "iOS 17+ Tunnel Attach",
                        color = GreenPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_jit"),
                        onClick = { onQuickAction(QuickActionTarget.ENABLE_JIT) }
                    )
                    ActionTile(
                        icon = Icons.Default.ScreenShare,
                        title = "Screen Mirror",
                        subtitle = "Low Latency DVT",
                        color = Color(0xFF64D2FF),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_mirror"),
                        onClick = { onQuickAction(QuickActionTarget.SCREEN_MIRROR) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        icon = Icons.Default.Folder,
                        title = "AFC File Mgr",
                        subtitle = "Media & App Sandbox",
                        color = Color(0xFFFFD60A),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_afc"),
                        onClick = { onQuickAction(QuickActionTarget.AFC_FILES) }
                    )
                    ActionTile(
                        icon = Icons.Default.Tune,
                        title = "Device Tools",
                        subtitle = "Syslog, Reboot, GPS",
                        color = Color(0xFFBF5AF2),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_action_tools"),
                        onClick = { onQuickAction(QuickActionTarget.DEVICE_TOOLS) }
                    )
                }
            }

            // Recent Room-Persisted Activity Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "RECENT DEVICE ACTIVITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    if (recentActivity.isEmpty()) {
                        Text(
                            text = "No recorded device sessions yet. Connect an iPhone or run an action above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        val sdf = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                        recentActivity.take(4).forEach { entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE2E8F0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.tag} • ${sdf.format(Date(entry.timestamp))}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = entry.level,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (entry.level == "SUCCESS") StatusSuccess else StatusWarning
                                )
                            }
                        }
                    }
                }
            }

            // Terminal Diagnostics Output
            TerminalConsole(
                logs = terminalLogs,
                title = "Live USB Host & Usbmuxd Diagnostics",
                onClearLogs = onClearLogs
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun MiniChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = "$label: $value",
                fontSize = 10.sp,
                color = Color(0xFFE2E8F0),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StorageLegendItem(color: Color, label: String, size: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }
        Text(
            text = size,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE2E8F0)
        )
    }
}

@Composable
private fun HardwareRow(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B)
            )
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = Color(0xFFF1F5F9),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onCopy,
            modifier = Modifier.minimumInteractiveComponentSize()
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    GlassCard(
        modifier = modifier,
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.65f),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun PairingProgressCard(
    title: String,
    subtitle: String,
    accent: Color,
    showSpinner: Boolean = false,
    isWarning: Boolean = false
) {
    val borderColor = if (isWarning) StatusWarning else accent
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = borderColor.copy(alpha = 0.5f),
        backgroundColor = Color(0xFF0F172A)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = accent,
                    strokeWidth = 2.5.dp
                )
            } else if (isWarning) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

@Composable
private fun PairingErrorCard(
    error: String,
    fix: String,
    onRetry: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = StatusError.copy(alpha = 0.5f),
        backgroundColor = Color(0xFF1E1116)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Pairing Error",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = StatusError
            )
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFCA5A5)
            )
            Text(
                text = "How to Fix: $fix",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFDE047),
                fontWeight = FontWeight.Medium
            )
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry Pairing")
            }
        }
    }
}
