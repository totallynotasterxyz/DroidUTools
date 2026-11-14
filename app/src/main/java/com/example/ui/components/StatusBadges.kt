// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusTunnel
import com.example.ui.theme.StatusWarning

@Composable
fun StatusBadge(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    showPulseDot: Boolean = false
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(100.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showPulseDot) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
fun PairingMethodBadge(
    method: String, // "RP_PAIRING" or "LOCKDOWND" or "UNPAIRED"
    modifier: Modifier = Modifier
) {
    when (method) {
        "RP_PAIRING" -> {
            StatusBadge(
                text = "RPPairing (iOS 17+)",
                icon = Icons.Default.VpnKey,
                color = StatusTunnel,
                modifier = modifier,
                showPulseDot = true
            )
        }
        "LOCKDOWND" -> {
            StatusBadge(
                text = "Lockdownd (Classic)",
                icon = Icons.Default.Lock,
                color = CyanPrimary,
                modifier = modifier,
                showPulseDot = true
            )
        }
        else -> {
            StatusBadge(
                text = "Not Paired",
                icon = Icons.Default.Error,
                color = StatusWarning,
                modifier = modifier
            )
        }
    }
}

@Composable
fun ConnectionBadge(
    connectionType: String, // "USB", "NETWORK", or "STANDBY"
    modifier: Modifier = Modifier
) {
    when (connectionType) {
        "USB" -> {
            StatusBadge(
                text = "USB Host OTG",
                icon = Icons.Default.Cable,
                color = CyanPrimary,
                modifier = modifier
            )
        }
        "NETWORK" -> {
            StatusBadge(
                text = "Wi-Fi Tunnel",
                icon = Icons.Default.Wifi,
                color = StatusSuccess,
                modifier = modifier
            )
        }
        else -> {
            StatusBadge(
                text = "Standby",
                icon = Icons.Default.Wifi,
                color = Color(0xFF94A3B8),
                modifier = modifier
            )
        }
    }
}

@Composable
fun RootBadge(
    isRootGranted: Boolean,
    rootManager: String = "Magisk / KSU",
    modifier: Modifier = Modifier
) {
    if (isRootGranted) {
        StatusBadge(
            text = "Root: $rootManager",
            icon = Icons.Default.Shield,
            color = StatusSuccess,
            modifier = modifier
        )
    } else {
        StatusBadge(
            text = "Root Missing",
            icon = Icons.Default.Error,
            color = StatusError,
            modifier = modifier
        )
    }
}
