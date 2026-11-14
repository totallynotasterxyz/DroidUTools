// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.updater.DownloadStatus
import com.example.updater.ReleaseInfo
import com.example.updater.UpdateState
import java.io.File

@Composable
fun UpdateDialogScreen(
    updateState: UpdateState,
    currentVersionName: String,
    onDownloadAndInstall: (ReleaseInfo) -> Unit,
    onInstallVerified: (ReleaseInfo, File) -> Unit,
    onCancelDownload: () -> Unit,
    onSkipVersion: (Long) -> Unit,
    onRemindMeLater: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val release: ReleaseInfo? = when (updateState) {
        is UpdateState.UpdateAvailable -> updateState.release
        is UpdateState.Downloading -> updateState.release
        is UpdateState.ReadyToInstall -> updateState.release
        is UpdateState.Installing -> updateState.release
        else -> null
    }

    if (release == null && updateState !is UpdateState.Error) return

    Dialog(
        onDismissRequest = onRemindMeLater,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(20.dp)),
            color = Color(0xFF090E17)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header: Title & Channel Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isErrorOnly = release == null && updateState is UpdateState.Error
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background((if (isErrorOnly) StatusError else primaryColor).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isErrorOnly) Icons.Default.ErrorOutline else Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = if (isErrorOnly) StatusError else primaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (isErrorOnly) "Update Failed" else "Update Available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (release != null) {
                                    "DroidUtools v${release.versionName}"
                                } else {
                                    "Installed: v$currentVersionName"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isErrorOnly) StatusError else primaryColor,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (release != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B)
                        ) {
                            Text(
                                text = release.channel.displayName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (release != null) {
                    // Version Comparison & File Size Card
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Current Version", fontSize = 11.sp, color = Color(0xFF64748B))
                                Text(
                                    text = "v$currentVersionName",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )

                            Column {
                                Text("New Version", fontSize = 11.sp, color = Color(0xFF64748B))
                                Text(
                                    text = "v${release.versionName}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = GreenPrimary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Size", fontSize = 11.sp, color = Color(0xFF64748B))
                                val sizeMb = release.apkAsset.sizeBytes / (1024.0 * 1024.0)
                                Text(
                                    text = if (sizeMb > 0) String.format("%.1f MB", sizeMb) else "--",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Release Notes (Markdown body)
                    Text(
                        text = "Release Notes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF05080E)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 180.dp)
                    ) {
                        val notesScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(notesScrollState)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val body = release.releaseNotes.ifBlank { "• Regular bug fixes and performance enhancements." }
                            body.lines().forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.startsWith("### ") || trimmed.startsWith("## ")) {
                                    Text(
                                        text = trimmed.removePrefix("### ").removePrefix("## "),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = primaryColor,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else if (trimmed.startsWith("* ") || trimmed.startsWith("- ")) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("•", color = primaryColor, fontSize = 12.sp)
                                        Text(trimmed.substring(2), fontSize = 12.sp, color = Color(0xFFCBD5E1), lineHeight = 16.sp)
                                    }
                                } else if (trimmed.isNotBlank()) {
                                    Text(trimmed, fontSize = 12.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }

                // Active Download Progress Section
                if (updateState is UpdateState.Downloading) {
                    val progress = updateState.progress
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (progress.status == DownloadStatus.VERIFYING) "Verifying signature & SHA-256..." else "Downloading update...",
                                fontSize = 12.sp,
                                color = primaryColor,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = progress.sizeFormatted,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        LinearProgressIndicator(
                            progress = { progress.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = primaryColor,
                            trackColor = Color(0xFF1E293B)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(progress.speedFormatted, fontSize = 11.sp, color = Color(0xFF64748B))
                            Text(progress.etaFormatted, fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }
                }

                // Error Message Banner
                if (updateState is UpdateState.Error) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StatusError.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = StatusError, modifier = Modifier.size(20.dp))
                            Text(updateState.message, color = StatusError, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                }

                // Action Buttons
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (updateState) {
                        is UpdateState.UpdateAvailable -> {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDownloadAndInstall(release!!)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("update_download_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = primaryColor,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download and Install", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        is UpdateState.Downloading -> {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onCancelDownload()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel Download", color = Color(0xFFCBD5E1))
                            }
                        }

                        is UpdateState.ReadyToInstall -> {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onInstallVerified(release!!, updateState.apkFile)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("update_install_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verify & Install Update", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        is UpdateState.Installing -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Installing...", color = Color.White)
                            }
                        }

                        else -> {}
                    }

                    // Secondary Options: Remind me later & Skip version
                    if (updateState !is UpdateState.Downloading && updateState !is UpdateState.Installing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onRemindMeLater,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Remind Me Later", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }

                            if (release != null) {
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSkipVersion(release.versionCode)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Skip Version", color = Color(0xFF64748B), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
