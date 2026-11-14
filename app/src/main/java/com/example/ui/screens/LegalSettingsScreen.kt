// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DeviceRepository
import com.example.data.PreferencesManager
import com.example.legal.ConsentManager
import com.example.legal.LegalDocType
import com.example.legal.LegalDocumentReader
import com.example.legal.ThirdPartyTermItem
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.PurplePrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.updater.UpdateChannel
import com.example.updater.UpdateConfig
import com.example.updater.UpdateManager
import com.example.updater.UpdateState
import com.example.usb.DaemonStatus
import com.example.usb.UsbMuxdDaemonManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalSettingsScreen(
    consentManager: ConsentManager,
    updateManager: UpdateManager,
    preferencesManager: PreferencesManager,
    daemonManager: UsbMuxdDaemonManager,
    deviceRepository: DeviceRepository,
    onBack: () -> Unit,
    onOpenDoc: (LegalDocType) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val docVersion by consentManager.acceptedDocVersion.collectAsState()
    val consentTimestamp by consentManager.acceptedTimestampUtc.collectAsState()
    val appVersion by consentManager.acceptedAppVersion.collectAsState()

    val isOledBlack by preferencesManager.isOledBlackFlow.collectAsState(initial = true)
    val accentIndex by preferencesManager.accentColorIndexFlow.collectAsState(initial = 0)
    val pairingPreference by preferencesManager.pairingPreferenceFlow.collectAsState(initial = "AUTO")
    val autoMountDdi by preferencesManager.autoMountDdiFlow.collectAsState(initial = true)

    val daemonStatus by daemonManager.daemonStatus.collectAsState()
    val socketType by daemonManager.activeSocketType.collectAsState()
    var isRestartingDaemon by remember { mutableStateOf(false) }
    var historyFeedback by remember { mutableStateOf<String?>(null) }

    val autoCheckEnabled by updateManager.autoCheckEnabled.collectAsState()
    val currentChannel by updateManager.currentChannel.collectAsState()
    val lastCheckTimestamp by updateManager.lastCheckTimestamp.collectAsState()
    val updateState by updateManager.updateState.collectAsState()

    var showWipeConfirmDialog by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var selectedThirdPartyTab by remember { mutableStateOf(false) }

    val thirdPartyItems = remember { LegalDocumentReader.loadThirdPartyTerms(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedThirdPartyTab) "Third-Party Terms" else "Settings & Legal",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedThirdPartyTab) {
                                selectedThirdPartyTab = false
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("legal_settings_back")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (selectedThirdPartyTab) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Data Processing & Third-Party Services",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Below is an overview of every external dependency and server protocol DroidUtools interacts with, along with what happens to your data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }

                items(thirdPartyItems) { item ->
                    ThirdPartyCard(item = item)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Appearance & Theme Card
                item {
                    Text(
                        text = "Appearance & Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AMOLED Pure Black",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Uses #000000 background for zero-power Super AMOLED pixels",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                Switch(
                                    checked = isOledBlack,
                                    onCheckedChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch { preferencesManager.setOledBlack(enabled) }
                                    },
                                    modifier = Modifier.testTag("oled_black_switch")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Text(
                                text = "Accent Palette",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val accentOptions = listOf(
                                Triple(0, "Cyan", CyanPrimary),
                                Triple(1, "Blue", BluePrimary),
                                Triple(2, "Emerald", GreenPrimary),
                                Triple(3, "Purple", PurplePrimary)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                accentOptions.forEach { (idx, label, swatchColor) ->
                                    val selected = accentIndex == idx
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (selected) swatchColor.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                            .border(
                                                width = if (selected) 1.5.dp else 1.dp,
                                                color = if (selected) swatchColor else MaterialTheme.colorScheme.outlineVariant,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                coroutineScope.launch { preferencesManager.setAccentColorIndex(idx) }
                                            }
                                            .padding(horizontal = 6.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) swatchColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // USB Host & Pairing Card
                item {
                    Text(
                        text = "USB Host & Pairing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Preferred Pairing Engine",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val pairingModes = listOf(
                                "AUTO" to "Auto",
                                "RP_PAIRING" to "RPPairing",
                                "LOCKDOWND" to "Lockdownd"
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pairingModes.forEach { (key, label) ->
                                    val isSelected = pairingPreference == key
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch { preferencesManager.setPairingPreference(key) }
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Mount Developer Disk Image",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Automatically mount Personalized DDI when pairing finishes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                Switch(
                                    checked = autoMountDdi,
                                    onCheckedChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch { preferencesManager.setAutoMountDdi(enabled) }
                                    },
                                    modifier = Modifier.testTag("auto_mount_ddi_switch")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val statusColor = when (daemonStatus) {
                                        DaemonStatus.RUNNING -> GreenPrimary
                                        DaemonStatus.STARTING -> StatusWarning
                                        else -> StatusError
                                    }
                                    Text(
                                        text = "usbmuxd Daemon: ${daemonStatus.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = statusColor
                                    )
                                    Text(
                                        text = "Socket: $socketType",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (!isRestartingDaemon) {
                                            isRestartingDaemon = true
                                            coroutineScope.launch {
                                                daemonManager.restartDaemon()
                                                isRestartingDaemon = false
                                            }
                                        }
                                    },
                                    enabled = !isRestartingDaemon,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("restart_usbmuxd_button")
                                ) {
                                    if (isRestartingDaemon) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isRestartingDaemon) "Restarting" else "Restart",
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Device Activity History",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = historyFeedback ?: "Clear local Room activity logs without unpairing devices",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (historyFeedback != null) GreenPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }

                                OutlinedButton(
                                    onClick = { showClearHistoryDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("clear_activity_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Clear", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Active Consent Record Card
                item {
                    Text(
                        text = "Consent & Document Record",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RecordRow(label = "Document Version", value = docVersion ?: ConsentManager.CURRENT_LEGAL_DOC_VERSION)
                            RecordRow(label = "Consent Timestamp (UTC)", value = consentTimestamp ?: "Pending")
                            RecordRow(label = "Application Version", value = appVersion ?: "1.0")
                            RecordRow(label = "Storage Status", value = "Stored locally in private DataStore")
                        }
                    }
                }

                // Documents List
                item {
                    Text(
                        text = "Legal Documents",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column {
                            LegalDocRow(
                                title = "Terms of Service",
                                subtitle = "User responsibilities, AS IS warranty, and liability limits",
                                onClick = { onOpenDoc(LegalDocType.TERMS_OF_SERVICE) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            LegalDocRow(
                                title = "Privacy Policy",
                                subtitle = "100% on-device processing and zero developer data collection",
                                onClick = { onOpenDoc(LegalDocType.PRIVACY_POLICY) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            LegalDocRow(
                                title = "Disclaimers & Technical Notices",
                                subtitle = "Root privileges, Apple ID limits, and hardware risk warnings",
                                onClick = { onOpenDoc(LegalDocType.DISCLAIMERS) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            LegalDocRow(
                                title = "Open-Source Licenses (GPL-3.0)",
                                subtitle = "GNU General Public License v3.0 text and component notices",
                                onClick = { onOpenDoc(LegalDocType.OPEN_SOURCE_LICENSES) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            LegalDocRow(
                                title = "Third-Party Terms & Services",
                                subtitle = "Detailed data-flow breakdown for Apple, Anisette, SideStore, and tools",
                                onClick = { selectedThirdPartyTab = true }
                            )
                        }
                    }
                }

                // Software Updates & Channels
                item {
                    Text(
                        text = "Software Updates",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Automatic Update Checks",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Checks GitHub Releases at most once every 12 hours on launch",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }
                                Switch(
                                    checked = autoCheckEnabled,
                                    onCheckedChange = { enabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch {
                                            updateManager.setAutoCheckEnabled(enabled)
                                        }
                                    },
                                    modifier = Modifier.testTag("auto_update_switch")
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Text(
                                text = "Release Channel",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                UpdateChannel.entries.forEach { channel ->
                                    val isSelected = currentChannel == channel
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            coroutineScope.launch {
                                                updateManager.setUpdateChannel(channel)
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = channel.displayName,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Text(
                                text = currentChannel.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                // About & Manual Update Check
                item {
                    Text(
                        text = "About DroidUtools",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DroidUtools v${updateManager.installedVersionName}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Version Code: ${updateManager.installedVersionCode}",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        updateManager.checkForUpdates(manual = true)
                                    },
                                    enabled = updateState !is UpdateState.Checking,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("check_updates_button")
                                ) {
                                    if (updateState is UpdateState.Checking) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.Black,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Checking...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.SystemUpdate,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Check for updates", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Status feedback row
                            val formattedLastCheck = remember(lastCheckTimestamp) {
                                if (lastCheckTimestamp > 0L) {
                                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastCheckTimestamp))
                                } else {
                                    "Never"
                                }
                            }
                            Text(
                                text = "Last checked: $formattedLastCheck",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )

                            when (val st = updateState) {
                                is UpdateState.UpToDate -> {
                                    Text(
                                        text = "✓ You are running the latest version on ${currentChannel.displayName}.",
                                        fontSize = 12.sp,
                                        color = GreenPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                is UpdateState.Error -> {
                                    Text(
                                        text = st.message,
                                        fontSize = 12.sp,
                                        color = StatusError,
                                        lineHeight = 16.sp
                                    )
                                }
                                else -> {}
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )

                            Text(
                                text = "DroidUtools is licensed under GNU General Public License v3.0 or later (GPL-3.0-or-later). Source code and release APKs are published at:\n${UpdateConfig.REPO_WEB_URL}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                lineHeight = 18.sp
                            )
                            Text(
                                text = "Developer: Aster\nContact: spaminhaler1276@gmail.com",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Withdraw Consent and Wipe Data Button
                item {
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showWipeConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("withdraw_consent_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Withdraw Consent and Wipe Data",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showWipeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Withdraw Consent & Wipe Data?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will immediately:\n\n" +
                           "1. Erase all stored pairing records and certificates.\n" +
                           "2. Wipe the local Room database and history.\n" +
                           "3. Clear all user preferences and signing credentials.\n" +
                           "4. Revoke legal consent and terminate the application.\n\n" +
                           "This action cannot be undone."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirmDialog = false
                        coroutineScope.launch {
                            consentManager.withdrawConsentAndWipeData()
                            (context as? Activity)?.finishAffinity()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Wipe and Exit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = {
                Text(
                    text = "Clear Device Activity History?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("This clears all logged pairing, installation, JIT, and tool actions from the local Room history table. Saved pairing records are preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        coroutineScope.launch {
                            deviceRepository.clearActivityHistory()
                            historyFeedback = "Activity history cleared"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Clear History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LegalDocRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                lineHeight = 16.sp
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ThirdPartyCard(item: ThirdPartyTermItem) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = item.license,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "Category: ${item.category}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Text(
                text = item.dataSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            if (item.officialUrl.isNotBlank()) {
                TextButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.officialUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open site",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Official Site",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
