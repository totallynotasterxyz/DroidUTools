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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.DeviceEntity
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusWarning
import com.example.usb.AppleUsbDevice
import com.example.wireless.WirelessDiscoveredDevice
import com.example.wireless.WirelessReachability

/**
 * Persistent top multi-device switcher bar rendered across all primary screens.
 * Displays the currently selected iOS target, live Wi-Fi & USB hub counts, and opens the Devices Drawer.
 */
@Composable
fun DeviceSwitcherBar(
    activeDevice: DeviceEntity?,
    connectedUsbDevices: List<AppleUsbDevice>,
    totalSavedCount: Int,
    onOpenDevicesDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    activeWirelessEndpoint: WirelessDiscoveredDevice? = null,
    connectedWifiCount: Int = 0
) {
    val accent = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    val isWifiOnline = activeWirelessEndpoint?.reachability == WirelessReachability.REACHABLE
    val isUsbOnline = activeDevice != null && connectedUsbDevices.any { usb ->
        usb.serialNumber.equals(activeDevice.udid, ignoreCase = true) ||
            usb.serialNumber.equals(activeDevice.serialNumber, ignoreCase = true) ||
            "USB-${usb.deviceId}" == activeDevice.udid
    } || (activeDevice == null && connectedUsbDevices.isNotEmpty())

    val statusDotColor = when {
        isWifiOnline || isUsbOnline -> GreenPrimary
        activeDevice != null -> StatusWarning
        else -> Color(0xFF64748B)
    }

    val displayName = when {
        activeDevice != null && activeDevice.nickname.isNotBlank() ->
            "${activeDevice.nickname} (${activeDevice.deviceName})"
        activeDevice != null -> activeDevice.deviceName
        activeWirelessEndpoint != null -> activeWirelessEndpoint.serviceName
        connectedUsbDevices.isNotEmpty() -> connectedUsbDevices.first().productName
        else -> "No iOS Device Selected"
    }

    val subtitle = when {
        isWifiOnline && activeDevice != null ->
            "Wi-Fi ${activeWirelessEndpoint!!.hostIp}:${activeWirelessEndpoint.port} (${activeWirelessEndpoint.latencyMs}ms) • iOS ${activeDevice.productVersion}"
        activeDevice != null ->
            "iOS ${activeDevice.productVersion} • ${activeDevice.productType} • ${activeDevice.udid.take(10)}…"
        connectedWifiCount > 0 ->
            "$connectedWifiCount wireless iOS endpoint(s) reachable on LAN"
        connectedUsbDevices.isNotEmpty() ->
            "${connectedUsbDevices.size} Apple USB endpoint ready to pair"
        else ->
            "Plug in USB OTG, scan Wi-Fi, or pick from $totalSavedCount saved"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenDevicesDrawer()
            }
            .minimumInteractiveComponentSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("device_switcher_bar"),
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
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DevicesOther,
                    contentDescription = "Active device switcher",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusDotColor)
                    )
                    Text(
                        text = displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = subtitle,
                    color = if (isWifiOnline) GreenPrimary else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (connectedWifiCount > 0) GreenPrimary.copy(alpha = 0.16f)
                        else Color(0xFF1E293B)
                    )
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = if (connectedWifiCount > 0) GreenPrimary else Color(0xFF64748B),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "$connectedWifiCount Wi-Fi",
                    color = if (connectedWifiCount > 0) GreenPrimary else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (connectedUsbDevices.isNotEmpty()) GreenPrimary.copy(alpha = 0.16f)
                        else Color(0xFF1E293B)
                    )
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Usb,
                    contentDescription = null,
                    tint = if (connectedUsbDevices.isNotEmpty()) GreenPrimary else Color(0xFF64748B),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${connectedUsbDevices.size} USB",
                    color = if (connectedUsbDevices.isNotEmpty()) GreenPrimary else Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Open devices drawer",
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
