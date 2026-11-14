// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.legal.LegalDocType

@Composable
fun ConsentGateScreen(
    isReconsentNotice: Boolean = false,
    onAccept: () -> Unit,
    onOpenDoc: (LegalDocType) -> Unit
) {
    val context = LocalContext.current
    var isChecked by remember { mutableStateOf(false) }
    var showWhatChangedDialog by remember { mutableStateOf(isReconsentNotice) }

    LaunchedEffect(isReconsentNotice) {
        if (isReconsentNotice) {
            showWhatChangedDialog = true
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        enabled = isChecked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("consent_accept_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "Accept and Continue",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isChecked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            (context as? Activity)?.finishAffinity()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("consent_decline_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Decline and Exit",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = "Legal Terms",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = "DroidUtools",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Terms & Consent",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Text(
                text = "Before you connect devices, manage iOS subsystems, or sideload applications, please review and accept these core terms:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )

            // Four plain-language points
            LegalPointCard(
                icon = Icons.Default.Warning,
                iconTint = Color(0xFFFFB74D), // Amber
                title = "1. Use at Your Own Risk",
                description = "If you break your device, lose data, or get your Apple account restricted, that is solely on you, not the Developer. Advanced USB host features and signing tools carry inherent technical risks."
            )

            LegalPointCard(
                icon = Icons.Default.Security,
                iconTint = MaterialTheme.colorScheme.primary,
                title = "2. Authorized Devices Only",
                description = "Only use DroidUtools on iOS/iPadOS devices that you personally own or have explicit legal authorization to manage and configure."
            )

            LegalPointCard(
                icon = Icons.Default.Lock,
                iconTint = Color(0xFF81C784), // Soft green
                title = "3. 100% Local Device Processing",
                description = "All data is processed strictly on your phone. The Developer operates no tracking servers and collects zero data, analytics, or personal identifiers."
            )

            LegalPointCard(
                icon = Icons.Default.Info,
                iconTint = Color(0xFF64B5F6), // Light blue
                title = "4. Third-Party Services & Updates",
                description = "DroidUtools relies on third-party services and tools (Apple, GitHub for updates, anisette servers, SideStore, open-source libraries). If update checks are on, the app contacts GitHub to look for new releases. GitHub's terms and privacy policy apply. You're responsible for reading and following them. The Developer doesn't control them and isn't responsible for them."
            )

            // Direct Links Bar
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Read Bundled Documents (Offline):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenDoc(LegalDocType.TERMS_OF_SERVICE) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Terms of Service", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onOpenDoc(LegalDocType.PRIVACY_POLICY) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Privacy Policy", fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenDoc(LegalDocType.DISCLAIMERS) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Disclaimers", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { onOpenDoc(LegalDocType.OPEN_SOURCE_LICENSES) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("GPL & Licenses", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Checkbox and Agreement Text
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isChecked = !isChecked }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { isChecked = it },
                    modifier = Modifier.testTag("consent_checkbox")
                )

                Text(
                    text = buildAnnotatedString {
                        append("I've read and agree to the ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("Terms of Service")
                        }
                        append(" and ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("Privacy Policy")
                        }
                        append(", and I am 18 years of age or older.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showWhatChangedDialog) {
        AlertDialog(
            onDismissRequest = { showWhatChangedDialog = false },
            title = {
                Text("Legal Document Updates")
            },
            text = {
                Text(
                    "The Terms of Service and Privacy Policy have been updated to version 1.1.0.\n\n" +
                    "Summary of updates:\n" +
                    "• Added in-app updater disclosure: If update checks are on, the app contacts GitHub to look for new releases. GitHub's terms and privacy policy apply.\n" +
                    "• Added GitHub repository link where source code is maintained alongside release APKs.\n" +
                    "• Updated third-party terms breakdown.\n\n" +
                    "Please review the updated terms and accept to continue using DroidUtools."
                )
            },
            confirmButton = {
                TextButton(onClick = { showWhatChangedDialog = false }) {
                    Text("Got It")
                }
            }
        )
    }
}

@Composable
private fun LegalPointCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
