// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.entities.BackupSnapshotEntity
import com.example.tools.AppBackupManager
import com.example.tools.BackupProgress
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iTunes-Style App Backup & Restore SubScreen producing SHA-256 verified .droidbackup archives.
 */
@Composable
fun BackupsSubScreen(
    udid: String,
    accent: Color,
    backupManager: AppBackupManager,
    onLogActivity: (actionType: String, description: String, status: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allSnapshots by backupManager.allSnapshots.collectAsState()

    var targetBundleId by remember { mutableStateOf("com.sidestore.SideStore") }
    var targetAppName by remember { mutableStateOf("SideStore") }
    var includeDocs by remember { mutableStateOf(true) }
    var includePrefs by remember { mutableStateOf(true) }
    var backupProgress by remember { mutableStateOf<BackupProgress?>(null) }
    var confirmingDelete by remember { mutableStateOf<BackupSnapshotEntity?>(null) }

    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }

    confirmingDelete?.let { snap ->
        AlertDialog(
            onDismissRequest = { confirmingDelete = null },
            title = { Text("Delete Backup Snapshot?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Remove ${snap.appName} (${snap.bundleId}) archive from device storage?", color = Color(0xFFCBD5E1)) },
            confirmButton = {
                Button(
                    onClick = {
                        val target = snap
                        confirmingDelete = null
                        scope.launch {
                            backupManager.deleteBackup(target)
                            onLogActivity("BACKUP", "Deleted backup for ${target.bundleId}", "INFO")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = accent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Text(
                            text = "App Container Backup & Restore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = targetAppName,
                            onValueChange = { targetAppName = it },
                            label = { Text("App Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = targetBundleId,
                            onValueChange = { targetBundleId = it },
                            label = { Text("Bundle Identifier") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includeDocs, onCheckedChange = { includeDocs = it })
                            Text("Documents/", color = Color.White, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = includePrefs, onCheckedChange = { includePrefs = it })
                            Text("Preferences .plist", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                backupManager.createAppBackup(
                                    udid = udid,
                                    bundleId = targetBundleId.trim(),
                                    appName = targetAppName.trim().ifBlank { targetBundleId.trim() },
                                    includesDocuments = includeDocs,
                                    includesPreferences = includePrefs
                                ).collect { prog ->
                                    backupProgress = prog
                                    if (prog is BackupProgress.Completed) {
                                        onLogActivity("BACKUP", prog.message, "SUCCESS")
                                    }
                                }
                            }
                        },
                        enabled = targetBundleId.isNotBlank() && backupProgress !is BackupProgress.Running,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("create_backup_snapshot_button")
                    ) {
                        Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create .droidbackup Snapshot", fontWeight = FontWeight.Bold)
                    }

                    when (val prog = backupProgress) {
                        is BackupProgress.Running -> {
                            LinearProgressIndicator(progress = { prog.progress }, modifier = Modifier.fillMaxWidth(), color = accent)
                            Text(prog.step, color = Color(0xFFCBD5E1), fontSize = 11.sp)
                        }
                        is BackupProgress.Completed -> {
                            Text("✓ ${prog.message}", color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        is BackupProgress.Error -> {
                            Text("Error: ${prog.message}", color = StatusError, fontSize = 12.sp)
                        }
                        null -> {}
                    }
                }
            }
        }

        item {
            Text(
                text = "SAVED ARCHIVE SNAPSHOTS (${allSnapshots.size})",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        if (allSnapshots.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No .droidbackup snapshots created yet. Pick an app above to archive its Documents and Preferences before upgrading or switching devices.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(allSnapshots, key = { it.id }) { snap ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(snap.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(snap.bundleId, color = accent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                            Text(
                                text = dateFormatter.format(Date(snap.createdAt)),
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = "Size: ${snap.sizeBytes} B • SHA-256: ${snap.sha256.take(14)}…",
                            color = Color(0xFF64748B),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        backupManager.restoreBackup(snap).collect { prog ->
                                            backupProgress = prog
                                            if (prog is BackupProgress.Completed) {
                                                onLogActivity("RESTORE", prog.message, "SUCCESS")
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val archiveFile = File(snap.archivePath)
                                    if (archiveFile.exists()) {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            archiveFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/octet-stream"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Export .droidbackup"))
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export", fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            IconButton(
                                onClick = { confirmingDelete = snap },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete backup", tint = StatusError)
                            }
                        }
                    }
                }
            }
        }
    }
}
