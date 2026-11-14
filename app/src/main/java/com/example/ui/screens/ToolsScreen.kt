// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DroidUtoolsApp
import com.example.data.entities.DeviceEntity
import com.example.tools.AfcFileItem
import com.example.tools.AfcFileManager
import com.example.tools.AppBackupManager
import com.example.tools.DeviceControlManager
import com.example.tools.IproxyTunnelManager
import com.example.tools.LocationSimulator
import com.example.tools.SimulatedLocation
import com.example.tools.SyslogClient
import com.example.tools.SyslogEntry
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.usb.DaemonStatus
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun ToolsScreen(
    udid: String,
    deviceId: Int = 1,
    initialSubTab: Int = 0,
    activeDevice: DeviceEntity? = null,
    appVersion: String = "1.1.0",
    daemonStatus: DaemonStatus = DaemonStatus.STOPPED,
    socketType: String = "None",
    hasUpdateBadge: Boolean = false,
    afcManagerOverride: AfcFileManager? = null,
    tunnelManager: IproxyTunnelManager? = null,
    backupManager: AppBackupManager? = null,
    wirelessHost: String? = null,
    onOpenLegalSettings: () -> Unit = {},
    onLogActivity: (actionType: String, description: String, status: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as DroidUtoolsApp }
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    var selectedSubTab by remember { mutableIntStateOf(initialSubTab.coerceIn(0, 6)) }

    LaunchedEffect(initialSubTab) {
        selectedSubTab = initialSubTab.coerceIn(0, 6)
    }

    val devControl = remember { DeviceControlManager(context) }
    val afcManager = remember(afcManagerOverride) { afcManagerOverride ?: app.afcFileManager }
    val resolvedTunnelMgr = remember(tunnelManager) { tunnelManager ?: app.iproxyTunnelManager }
    val resolvedBackupMgr = remember(backupManager) { backupManager ?: app.appBackupManager }
    val syslogClient = remember { SyslogClient() }
    val locationSimulator = remember { LocationSimulator() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = accent,
            edgePadding = 8.dp,
            indicator = { tabPositions ->
                if (selectedSubTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                        color = accent
                    )
                }
            }
        ) {
            listOf(
                "Power & Stats",
                "AFC Files",
                "Tunnels",
                "Backups",
                "Crashes",
                "Live Syslog",
                "GPS Spoof",
                "Settings"
            ).forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = {
                        if (index == 7) {
                            onOpenLegalSettings()
                        } else {
                            selectedSubTab = index
                        }
                    },
                    text = {
                        if (index == 7 && hasUpdateBadge) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = Color(0xFFEF4444))
                                }
                            ) {
                                Text(
                                    text = title,
                                    fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        } else {
                            Text(
                                text = title,
                                fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        }
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedSubTab) {
                0 -> DeviceControlsSubScreen(
                    udid = udid,
                    accent = accent,
                    activeDevice = activeDevice,
                    appVersion = appVersion,
                    daemonStatus = daemonStatus,
                    socketType = socketType,
                    devControl = devControl,
                    onLogActivity = onLogActivity
                )
                1 -> AfcFilesSubScreen(
                    accent = accent,
                    afcManager = afcManager,
                    onLogActivity = onLogActivity
                )
                2 -> TunnelsSubScreen(
                    udid = udid,
                    deviceId = deviceId,
                    accent = accent,
                    tunnelManager = resolvedTunnelMgr,
                    wirelessHost = wirelessHost,
                    onLogActivity = onLogActivity
                )
                3 -> BackupsSubScreen(
                    udid = udid,
                    accent = accent,
                    backupManager = resolvedBackupMgr,
                    onLogActivity = onLogActivity
                )
                4 -> CrashThermalSubScreen(
                    activeDevice = activeDevice,
                    accent = accent,
                    onLogActivity = onLogActivity
                )
                5 -> LiveSyslogSubScreen(
                    accent = accent,
                    syslogClient = syslogClient
                )
                6 -> LocationSimulatorSubScreen(
                    accent = accent,
                    locationSimulator = locationSimulator,
                    onLogActivity = onLogActivity
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Device Controls & Diagnostics SubScreen
// -------------------------------------------------------------
@Composable
private fun DeviceControlsSubScreen(
    udid: String,
    accent: Color,
    activeDevice: DeviceEntity?,
    appVersion: String,
    daemonStatus: DaemonStatus,
    socketType: String,
    devControl: DeviceControlManager,
    onLogActivity: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }
    var pendingConfirmAction by remember { mutableStateOf<Pair<String, suspend () -> Result<String>>?>(null) }

    pendingConfirmAction?.let { (label, actionBlock) ->
        AlertDialog(
            onDismissRequest = { pendingConfirmAction = null },
            title = { Text("Confirm $label", fontWeight = FontWeight.Bold) },
            text = { Text("Send '$label' command to the connected iOS device via Diagnostics Relay?") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingConfirmAction = null
                        scope.launch {
                            isOperating = true
                            val res = actionBlock()
                            statusMessage = res.getOrNull() ?: res.exceptionOrNull()?.message
                            onLogActivity("POWER", label, if (res.isSuccess) "SUCCESS" else "ERROR")
                            isOperating = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Confirm", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirmAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "DEVICE POWER & RECOVERY CONTROLS",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        statusMessage?.let { msg ->
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = GreenPrimary.copy(alpha = 0.5f)
                ) {
                    Text(msg, color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Diagnostics Relay Commands",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    val res = devControl.rebootDevice()
                                    statusMessage = res.getOrNull()
                                    onLogActivity("POWER", "Reboot iOS requested", "SUCCESS")
                                    isOperating = false
                                }
                            },
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reboot iOS")
                        }

                        Button(
                            onClick = {
                                pendingConfirmAction = "Shutdown iOS" to { devControl.shutdownDevice() }
                            },
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shutdown")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pendingConfirmAction = "Enter Recovery Mode" to { devControl.enterRecoveryMode() }
                            },
                            enabled = !isOperating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enter Recovery")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isOperating = true
                                    val res = devControl.exitRecoveryMode()
                                    statusMessage = res.getOrNull()
                                    onLogActivity("POWER", "Exit Recovery Mode requested", "SUCCESS")
                                    isOperating = false
                                }
                            },
                            enabled = !isOperating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Exit Recovery")
                        }
                    }
                }
            }
        }

        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = accent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export Full Device Report",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Generates live JSON audit with hardware IDs, daemon & host metadata",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val report = devControl.generateJsonDeviceReport(
                                udid = udid,
                                device = activeDevice,
                                appVersion = appVersion,
                                daemonStatus = daemonStatus,
                                socketType = socketType
                            )
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, report)
                                type = "application/json"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export iOS Device Report"))
                            onLogActivity("EXPORT", "Shared JSON device diagnostic report", "SUCCESS")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export JSON", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. AFC File Manager SubScreen
// -------------------------------------------------------------
@Composable
private fun AfcFilesSubScreen(
    accent: Color,
    afcManager: AfcFileManager,
    onLogActivity: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf(afcManager.getCurrentPath()) }
    var fileList by remember { mutableStateOf<List<AfcFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    fun refreshDir(path: String) {
        scope.launch {
            isLoading = true
            currentPath = path
            fileList = afcManager.listDirectory(path).getOrNull() ?: emptyList()
            isLoading = false
        }
    }

    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val temp = File(context.cacheDir, "afc_upload_${System.currentTimeMillis()}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                }
                val res = afcManager.uploadFile(temp, currentPath)
                temp.delete()
                if (res.isSuccess) {
                    actionFeedback = "Uploaded file to $currentPath"
                    onLogActivity("AFC", "Uploaded file to $currentPath", "SUCCESS")
                    refreshDir(currentPath)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshDir("/Media")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Path Header & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (currentPath != "/" && currentPath != "/Media") {
                    IconButton(
                        onClick = {
                            val parent = currentPath.substringBeforeLast("/").ifEmpty { "/" }
                            refreshDir(parent)
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go up", tint = accent)
                    }
                }
                Text(
                    text = currentPath,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Row {
                IconButton(
                    onClick = { uploadPicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Upload file", tint = accent)
                }
                IconButton(
                    onClick = { refreshDir(currentPath) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh directory", tint = accent)
                }
            }
        }

        actionFeedback?.let { msg ->
            Text(text = msg, color = GreenPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
        } else if (fileList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Directory is empty", color = Color(0xFF64748B), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fileList) { item ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isDirectory) {
                                    refreshDir(item.path)
                                }
                            },
                        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = if (item.isDirectory) accent else Color(0xFF94A3B8),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(item.formattedSize, color = Color(0xFF64748B), fontSize = 10.sp)
                                }
                            }

                            if (!item.isDirectory) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            afcManager.deleteFile(item.path)
                                            fileList = fileList.filter { it.path != item.path }
                                            actionFeedback = "Deleted ${item.name}"
                                        }
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusError, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. Live Syslog SubScreen
// -------------------------------------------------------------
@Composable
private fun LiveSyslogSubScreen(
    accent: Color,
    syslogClient: SyslogClient
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var logs by remember { mutableStateOf<List<SyslogEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    val isStreaming by syslogClient.isStreaming.collectAsState()

    LaunchedEffect(Unit) {
        syslogClient.streamSyslog().collect { entry ->
            logs = (logs + entry).takeLast(250)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            syslogClient.stopStream()
        }
    }

    val filteredLogs = logs.filter { entry ->
        (selectedLevel == null || entry.level.equals(selectedLevel, ignoreCase = true)) &&
                (searchQuery.isBlank() || entry.message.contains(searchQuery, ignoreCase = true) || entry.process.contains(searchQuery, ignoreCase = true))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter live syslog...", color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    val dump = filteredLogs.joinToString("\n") {
                        "${it.timestamp} [${it.process}:${it.pid}] [${it.level}] ${it.message}"
                    }
                    clipboard.setText(AnnotatedString(dump))
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy syslog", tint = Color(0xFF94A3B8))
            }

            IconButton(
                onClick = { logs = emptyList() },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear syslog", tint = Color(0xFF94A3B8))
            }

            Button(
                onClick = {
                    if (isStreaming) {
                        syslogClient.stopStream()
                    } else {
                        scope.launch {
                            syslogClient.streamSyslog().collect { entry ->
                                logs = (logs + entry).takeLast(250)
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isStreaming) Color(0xFF7F1D1D) else GreenPrimary,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text(if (isStreaming) "Pause" else "Stream", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(null to "All", "Notice" to "Notice", "Info" to "Info", "Warning" to "Warn", "Error" to "Error").forEach { (lvl, label) ->
                val isSelected = selectedLevel == lvl
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) accent else Color(0xFF1E293B))
                        .clickable { selectedLevel = lvl }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(label, color = if (isSelected) Color.Black else Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF02040A))
                .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(12.dp))
                .padding(8.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Text(
                    text = "No syslog entries matching current filter.",
                    color = Color(0xFF475569),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredLogs) { entry ->
                        val color = when (entry.level) {
                            "Error" -> StatusError
                            "Warning" -> StatusWarning
                            "Notice" -> accent
                            else -> Color(0xFF94A3B8)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(entry.timestamp, color = Color(0xFF475569), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("[${entry.process}:${entry.pid}]", color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(entry.message, color = Color(0xFFE2E8F0), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. Location Simulator SubScreen (Now includes Custom Coordinates!)
// -------------------------------------------------------------
@Composable
private fun LocationSimulatorSubScreen(
    accent: Color,
    locationSimulator: LocationSimulator,
    onLogActivity: (String, String, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val activeLoc by locationSimulator.currentLocation.collectAsState()
    var customLat by remember { mutableStateOf("37.3346") }
    var customLon by remember { mutableStateOf("-122.0090") }
    var coordError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = if (activeLoc != null) GreenPrimary else Color(0xFF64748B)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeLoc != null) "Spoofed: ${activeLoc!!.name}" else "Real Device GPS Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (activeLoc != null) "Lat: ${activeLoc!!.latitude}, Lon: ${activeLoc!!.longitude}" else "No location override active",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    if (activeLoc != null) {
                        Button(
                            onClick = {
                                scope.launch {
                                    locationSimulator.clearLocation()
                                    onLogActivity("GPS", "Reset simulated GPS to real hardware", "SUCCESS")
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7F1D1D), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Custom Coordinates Entry Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "CUSTOM COORDINATES",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customLat,
                            onValueChange = {
                                customLat = it
                                coordError = null
                            },
                            label = { Text("Latitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customLon,
                            onValueChange = {
                                customLon = it
                                coordError = null
                            },
                            label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    coordError?.let { err ->
                        Text(err, color = StatusError, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val lat = customLat.trim().toDoubleOrNull()
                            val lon = customLon.trim().toDoubleOrNull()
                            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                                coordError = "Enter valid Latitude (-90..90) and Longitude (-180..180)."
                            } else {
                                scope.launch {
                                    val customLoc = SimulatedLocation("Custom ($lat, $lon)", lat, lon)
                                    locationSimulator.setLocation(customLoc)
                                    onLogActivity("GPS", "Applied custom coordinates $lat, $lon", "SUCCESS")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("gps_apply_custom_button")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Custom Coordinates", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text(
                text = "POPULAR PRESETS",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        items(LocationSimulator.PRESETS) { preset ->
            val isSelected = activeLoc?.name == preset.name
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            locationSimulator.setLocation(preset)
                            onLogActivity("GPS", "Simulated GPS: ${preset.name}", "SUCCESS")
                        }
                    },
                borderColor = if (isSelected) GreenPrimary else Color(0xFF1E293B)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                        Column {
                            Text(preset.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text("${preset.latitude}, ${preset.longitude}", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(GreenPrimary.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("ACTIVE", color = GreenPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
