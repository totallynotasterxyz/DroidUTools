// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.root.RootVerificationStatus
import com.example.ui.components.GlassCard
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusTunnel

@Composable
fun OnboardingScreen(
    rootStatus: RootVerificationStatus.Granted,
    initialPairingPreference: String,
    onPairingPreferenceChanged: (String) -> Unit,
    onCompleteOnboarding: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedPairing by remember { mutableStateOf(initialPairingPreference) }

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Step Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                (0..3).forEach { index ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (step == index) 24.dp else 8.dp, 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (step == index) primaryColor
                                else if (step > index) StatusSuccess
                                else Color(0xFF334155)
                            )
                    )
                }
            }

            // Step Content
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step"
            ) { currentStep ->
                when (currentStep) {
                    0 -> StepRootVerified(rootStatus)
                    1 -> StepUsbSetup()
                    2 -> StepPairingStrategy(
                        selected = selectedPairing,
                        onSelect = {
                            selectedPairing = it
                            onPairingPreferenceChanged(it)
                        }
                    )
                    3 -> StepReady()
                }
            }

            // Bottom Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (step > 0) {
                    Button(
                        onClick = { step-- },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text("Back", fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            onCompleteOnboarding()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(if (step > 0) 2f else 1f)
                        .height(52.dp)
                ) {
                    Text(
                        text = if (step == 3) "Enter DroidUtools" else "Continue",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (step == 3) Icons.Default.Check else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRootVerified(rootStatus: RootVerificationStatus.Granted) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(StatusSuccess.copy(alpha = 0.2f))
                .border(2.dp, StatusSuccess, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = StatusSuccess,
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = "Root Access Granted!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Your device has been verified with full superuser permissions. DroidUtools can now bind raw USB hardware nodes.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFF0F172A)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow("Root Manager", rootStatus.manager)
                DetailRow("Superuser UID", "${rootStatus.uid} (Root)")
                DetailRow("SELinux Mode", rootStatus.selinuxMode)
                DetailRow("Kernel", rootStatus.kernelVersion)
                DetailRow("Raw USB Access", if (rootStatus.usbDevNodesAccessible) "Unlocked (/dev/bus/usb)" else "Configuring")
            }
        }
    }
}

@Composable
private fun StepUsbSetup() {
    val primaryColor = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.2f))
                .border(2.dp, primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cable,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = "USB Host OTG Connection",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Connect your iPhone or iPad to your phone's USB-C port using an OTG adapter or a USB-C to Lightning/USB-C cable.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Hardware Requirements:",
                    style = MaterialTheme.typography.titleSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "• Samsung A54 acts as USB Host (power source + controller)\n" +
                            "• iPhone will prompt 'Trust This Computer?'\n" +
                            "• Always keep your iPhone unlocked on first connect\n" +
                            "• If phone prompts for USB permission, select 'Always allow'",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun StepPairingStrategy(
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(StatusTunnel.copy(alpha = 0.2f))
                .border(2.dp, StatusTunnel, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.VpnKey,
                contentDescription = null,
                tint = StatusTunnel,
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = "Pairing Strategy",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Choose your pairing engine. DroidUtools defaults to Auto-Detection, trying modern RPPairing first and falling back to Lockdownd.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StrategyOption(
                title = "Auto-Detect (Recommended)",
                badge = "SMART",
                description = "Tries RPPairing tunnel for iOS 17+. Falls back to classic Lockdownd if < iOS 17 or unsupported.",
                isSelected = selected == "AUTO",
                onClick = { onSelect("AUTO") }
            )

            StrategyOption(
                title = "Force RPPairing (RemotePairing)",
                badge = "iOS 17+",
                description = "Modern CoreDevice tunnel over TCP/QUIC. Required for JIT on iOS 17.4+ and remote developer services.",
                isSelected = selected == "RP_PAIRING",
                onClick = { onSelect("RP_PAIRING") }
            )

            StrategyOption(
                title = "Force Lockdownd (Classic)",
                badge = "COMPAT",
                description = "Classic lockdown SSL handshake. Reliable for all iOS versions, SideStore, and standard app installs.",
                isSelected = selected == "LOCKDOWND",
                onClick = { onSelect("LOCKDOWND") }
            )
        }
    }
}

@Composable
private fun StrategyOption(
    title: String,
    badge: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (isSelected) primaryColor else Color(0xFF1E293B),
        backgroundColor = if (isSelected) Color(0xFF0F172A) else Color(0xFF050811),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) primaryColor else Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(primaryColor.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badge,
                            color = primaryColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun StepReady() {
    val primaryColor = MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.2f))
                .border(2.dp, primaryColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneIphone,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = "Ready to Connect!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Plug in your iOS device. The usbmuxd daemon will automatically supervise the USB bus and initiate pairing.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8),
            textAlign = TextAlign.Center
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Checklist:",
                    style = MaterialTheme.typography.titleSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "✓ Root verified with UID 0\n" +
                            "✓ USB Host permissions granted\n" +
                            "✓ usbmuxd daemon ready\n" +
                            "✓ Dual pairing strategy (RPPairing + Lockdownd) armed",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF1F5F9),
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFF1F5F9)
        )
    }
}
