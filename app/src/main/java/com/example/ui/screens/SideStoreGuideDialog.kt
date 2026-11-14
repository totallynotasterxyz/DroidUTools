// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.sidestore.SideStoreBundleOption
import com.example.ui.components.GlassCard
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusTunnel

@Composable
fun SideStoreGuideDialog(
    bundleOption: SideStoreBundleOption = SideStoreBundleOption.SIDESTORE_AND_LIVECONTAINER,
    onDismiss: () -> Unit
) {
    val isCombo = bundleOption == SideStoreBundleOption.SIDESTORE_AND_LIVECONTAINER

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp)),
            color = Color(0xFF090D1A)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
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
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(GreenPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GreenPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = GreenPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isCombo) "SideStore + LiveContainer Ready!" else "${bundleOption.title} Installed!",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Pairing file successfully injected into iOS containers",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Text(
                    text = if (isCombo)
                        "Complete setup on your iPhone to unlock UNLIMITED sideloading without 3-app limits:"
                    else
                        "Complete setup on your iPhone in quick steps:",
                    style = MaterialTheme.typography.titleSmall,
                    color = CyanPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                // Step 1: Developer Mode
                GuideStepCard(
                    stepNumber = "1",
                    icon = Icons.Default.DeveloperMode,
                    title = "Enable Developer Mode (iOS 16+)",
                    instructions = "On iPhone, go to Settings → Privacy & Security → Developer Mode. Turn it ON, reboot your iPhone, and confirm 'Turn On'."
                )

                // Step 2: Trust Profile
                GuideStepCard(
                    stepNumber = "2",
                    icon = Icons.Default.Security,
                    title = "Trust Apple ID Profile",
                    instructions = "Go to Settings → General → VPN & Device Management. Tap your developer profile and select 'Trust'."
                )

                if (bundleOption.installsSideStore) {
                    // Step 3: WireGuard VPN in SideStore
                    GuideStepCard(
                        stepNumber = "3",
                        icon = Icons.Default.VpnKey,
                        title = "Install WireGuard Loopback VPN",
                        instructions = "Launch SideStore on your iPhone. Follow the prompt to install the 'SideStore VPN' profile. Toggle the VPN switch ON."
                    )
                }

                if (isCombo) {
                    // Step 4: Unlimited Apps inside LiveContainer
                    GuideStepCard(
                        stepNumber = "4",
                        icon = Icons.Default.AllInclusive,
                        title = "Bypass 3-App Limit with LiveContainer",
                        instructions = "Open LiveContainer. Tap (+) to import unlimited IPAs! They will run securely inside LiveContainer while SideStore keeps your certificates refreshed."
                    )
                } else if (bundleOption.installsSideStore) {
                    GuideStepCard(
                        stepNumber = "4",
                        icon = Icons.Default.Refresh,
                        title = "Refresh Apps in SideStore",
                        instructions = "In SideStore, tap 'My Apps' → 'Refresh All'. Your apps will refresh on-device without a Mac or PC!"
                    )
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPrimary,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "Got it! Start Sideloading",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    instructions: String
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFF0F172A).copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(StatusTunnel.copy(alpha = 0.25f))
                    .border(1.dp, StatusTunnel, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    color = StatusTunnel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
