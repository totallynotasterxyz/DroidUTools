// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.activity.compose.BackHandler
import com.example.legal.LegalDocType
import com.example.legal.LegalDocumentReader
import com.example.pairing.PairingState
import com.example.root.RootVerificationStatus
import com.example.service.UsbMuxdService
import com.example.ui.components.DeviceSwitcherBar
import com.example.ui.components.DevicesDrawerSheet
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.ConsentGateScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DocumentViewerScreen
import com.example.ui.screens.JitScreen
import com.example.ui.screens.LegalSettingsScreen
import com.example.ui.screens.MirrorScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.QuickActionTarget
import com.example.ui.screens.RootGateScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.UpdateDialogScreen
import com.example.ui.screens.WhatsNewSheet
import com.example.ui.theme.DroidUtoolsTheme
import com.example.ui.theme.OledBlack
import com.example.updater.AppInstaller
import com.example.updater.UpdateState
import com.example.wireless.WirelessReachability
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DroidUtoolsApp

        setContent {
            val isOled by app.preferencesManager.isOledBlackFlow.collectAsState(initial = true)
            val accentIndex by app.preferencesManager.accentColorIndexFlow.collectAsState(initial = 0)
            val isOnboardingCompleted by app.preferencesManager.isOnboardingCompletedFlow.collectAsState(initial = false)
            val pairingPreference by app.preferencesManager.pairingPreferenceFlow.collectAsState(initial = "AUTO")

            val isConsentAccepted by app.consentManager.isConsentAccepted.collectAsState()
            val needsReconsent by app.consentManager.needsReconsent.collectAsState()
            var viewingDocType by remember { mutableStateOf<LegalDocType?>(null) }
            var isViewingLegalSettings by remember { mutableStateOf(false) }

            val updateState by app.updateManager.updateState.collectAsState()
            val hasUpdateBadge by app.updateManager.hasUpdateBadge.collectAsState()
            var showWhatsNew by remember { mutableStateOf(false) }
            var showUpdateDialog by remember { mutableStateOf(false) }

            val rootStatus by app.rootChecker.status.collectAsState()
            val terminalLogs by app.rootChecker.terminalLogs.collectAsState()
            val connectedUsbDevices by app.usbDeviceWatcher.connectedAppleDevices.collectAsState()
            val usbDevice by app.usbDeviceWatcher.connectedAppleDevice.collectAsState()
            val wirelessDevices by app.wirelessDeviceWatcher.wirelessDevices.collectAsState()
            val isScanningWifi by app.wirelessDeviceWatcher.isScanning.collectAsState()
            val pairingState by app.pairingManager.pairingState.collectAsState()
            val pairingBadge by app.pairingManager.activePairingMethodBadge.collectAsState()

            val savedDevices by app.deviceRepository.allDevices.collectAsState()
            val activeDevice by app.deviceRepository.activeDevice.collectAsState()
            val recentActivityLogs by app.deviceRepository.recentActivityLogs.collectAsState()
            val daemonStatus by app.usbMuxdDaemonManager.daemonStatus.collectAsState()
            val socketType by app.usbMuxdDaemonManager.activeSocketType.collectAsState()
            val bridgeBackend by app.iosBridgeRouter.activeBackendLabel.collectAsState()

            val scope = rememberCoroutineScope()
            var selectedTab by remember { mutableIntStateOf(0) }
            var appsSubTab by remember { mutableIntStateOf(0) }
            var toolsSubTab by remember { mutableIntStateOf(0) }
            var showDevicesDrawer by remember { mutableStateOf(false) }

            val activeUsbDevice = remember(activeDevice, connectedUsbDevices) {
                if (activeDevice != null) {
                    connectedUsbDevices.firstOrNull { usb ->
                        usb.serialNumber.equals(activeDevice?.udid, ignoreCase = true) ||
                            usb.serialNumber.equals(activeDevice?.serialNumber, ignoreCase = true) ||
                            "USB-${usb.deviceId}" == activeDevice?.udid
                    } ?: connectedUsbDevices.firstOrNull()
                } else {
                    connectedUsbDevices.firstOrNull()
                }
            }

            val activeWirelessEndpoint = remember(activeDevice, wirelessDevices) {
                app.deviceRepository.resolveWirelessEndpoint(activeDevice, wirelessDevices)
            }

            // Handle back button for legal viewer and settings
            BackHandler(enabled = viewingDocType != null || isViewingLegalSettings) {
                if (viewingDocType != null) {
                    viewingDocType = null
                } else {
                    isViewingLegalSettings = false
                }
            }

            // Trigger root verification, wireless LAN mDNS discovery, update cleanup, What's New, and auto-update check ONLY AFTER legal consent
            LaunchedEffect(isConsentAccepted) {
                if (isConsentAccepted) {
                    app.rootChecker.verifyRoot()
                    app.wirelessDeviceWatcher.startScanning()
                    AppInstaller.cleanUpdateCache(this@MainActivity)
                    if (app.updateManager.checkShouldShowWhatsNew()) {
                        showWhatsNew = true
                    }
                    app.updateManager.checkForUpdates(manual = false)
                } else {
                    app.wirelessDeviceWatcher.clearAll()
                }
            }

            // Sync discovered or manually added wireless LAN iOS endpoints into the multi-device repository
            LaunchedEffect(wirelessDevices, isConsentAccepted) {
                if (isConsentAccepted && wirelessDevices.isNotEmpty()) {
                    app.deviceRepository.syncWirelessDevices(wirelessDevices)
                }
            }

            // Automatically present the Update Dialog when a new release or active download/install state enters
            LaunchedEffect(updateState) {
                when (updateState) {
                    is UpdateState.UpdateAvailable,
                    is UpdateState.Downloading,
                    is UpdateState.ReadyToInstall,
                    is UpdateState.Installing -> {
                        showUpdateDialog = true
                    }
                    else -> {}
                }
            }

            // Start USB service once root is granted AND consent is accepted
            LaunchedEffect(rootStatus, isConsentAccepted) {
                if (isConsentAccepted && rootStatus is RootVerificationStatus.Granted) {
                    UsbMuxdService.startService(this@MainActivity)
                }
            }

            // Automatic multi-device USB hub sync & pairing trigger when USB devices are detected AND consent is accepted
            LaunchedEffect(connectedUsbDevices, rootStatus, isConsentAccepted) {
                if (isConsentAccepted && rootStatus is RootVerificationStatus.Granted && connectedUsbDevices.isNotEmpty()) {
                    val syncedList = app.deviceRepository.syncConnectedUsbDevices(connectedUsbDevices, pairingBadge)
                    val primaryUsb = connectedUsbDevices.first()
                    val primarySynced = syncedList.firstOrNull()
                    if (primarySynced != null) {
                        app.pairingManager.initiatePairing(
                            deviceId = primaryUsb.deviceId,
                            udid = primarySynced.udid,
                            productVersion = primarySynced.productVersion
                        )
                    }
                }
            }

            // Persist paired device telemetry and log pairing activity when PairingState enters Paired
            LaunchedEffect(pairingState) {
                val st = pairingState
                if (st is PairingState.Paired) {
                    app.deviceRepository.recordPairedDevice(
                        udid = st.record.udid,
                        usbDevice = activeUsbDevice ?: usbDevice,
                        pairingMethod = st.method.name,
                        wifiMac = st.record.wifiMacAddress.ifBlank { "Unknown" }
                    )
                }
            }

            DroidUtoolsTheme(
                darkTheme = true,
                isOled = isOled,
                accentIndex = accentIndex
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isOled) OledBlack else Color(0xFF080C14))
                ) {
                    val activeDoc = viewingDocType
                    if (!isConsentAccepted) {
                        // MANDATORY FIRST-RUN CONSENT GATE
                        // Shown before anything else, including root check, onboarding, and background services.
                        if (activeDoc != null) {
                            DocumentViewerScreen(
                                docType = activeDoc,
                                documentContent = LegalDocumentReader.loadDocument(this@MainActivity, activeDoc),
                                onBack = { viewingDocType = null }
                            )
                        } else {
                            ConsentGateScreen(
                                isReconsentNotice = needsReconsent,
                                onAccept = {
                                    scope.launch {
                                        app.consentManager.acceptConsent(app.updateManager.installedVersionName)
                                    }
                                },
                                onOpenDoc = { doc ->
                                    viewingDocType = doc
                                }
                            )
                        }
                    } else if (isViewingLegalSettings) {
                        // SETTINGS > LEGAL SCREEN
                        if (activeDoc != null) {
                            DocumentViewerScreen(
                                docType = activeDoc,
                                documentContent = LegalDocumentReader.loadDocument(this@MainActivity, activeDoc),
                                onBack = { viewingDocType = null }
                            )
                        } else {
                            LegalSettingsScreen(
                                consentManager = app.consentManager,
                                updateManager = app.updateManager,
                                preferencesManager = app.preferencesManager,
                                daemonManager = app.usbMuxdDaemonManager,
                                deviceRepository = app.deviceRepository,
                                onBack = { isViewingLegalSettings = false },
                                onOpenDoc = { doc -> viewingDocType = doc }
                            )
                        }
                    } else {
                        // MAIN APPLICATION FLOW
                        when (val status = rootStatus) {
                            is RootVerificationStatus.Idle, is RootVerificationStatus.Checking,
                            is RootVerificationStatus.Denied, is RootVerificationStatus.Missing -> {
                                // ROOT GATE: Strictly blocking screen. No degraded mode.
                                RootGateScreen(
                                    status = status,
                                    terminalLogs = terminalLogs,
                                    onRetryCheck = {
                                        scope.launch { app.rootChecker.verifyRoot() }
                                    },
                                    onClearLogs = { app.rootChecker.clearLogs() },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .statusBarsPadding()
                                        .navigationBarsPadding()
                                )
                            }

                            is RootVerificationStatus.Granted -> {
                                if (!isOnboardingCompleted) {
                                    // FIRST-RUN ONBOARDING WIZARD
                                    OnboardingScreen(
                                        rootStatus = status,
                                        initialPairingPreference = pairingPreference,
                                        onPairingPreferenceChanged = { pref ->
                                            scope.launch { app.preferencesManager.setPairingPreference(pref) }
                                        },
                                        onCompleteOnboarding = {
                                            scope.launch { app.preferencesManager.setOnboardingCompleted(true) }
                                        },
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .statusBarsPadding()
                                            .navigationBarsPadding()
                                    )
                                } else {
                                    // MAIN APPLICATION WITH TOP DEVICE SWITCHER & BOTTOM NAVIGATION
                                    Scaffold(
                                        modifier = Modifier.fillMaxSize(),
                                        containerColor = if (isOled) OledBlack else Color(0xFF080C14),
                                        topBar = {
                                            DeviceSwitcherBar(
                                                activeDevice = activeDevice,
                                                connectedUsbDevices = connectedUsbDevices,
                                                totalSavedCount = savedDevices.size,
                                                onOpenDevicesDrawer = { showDevicesDrawer = true },
                                                activeWirelessEndpoint = activeWirelessEndpoint,
                                                connectedWifiCount = wirelessDevices.count {
                                                    it.reachability == WirelessReachability.REACHABLE
                                                }
                                            )
                                        },
                                        bottomBar = {
                                            BottomNavBar(
                                                selectedTab = selectedTab,
                                                hasUpdateBadge = hasUpdateBadge,
                                                onTabSelected = { selectedTab = it }
                                            )
                                        }
                                    ) { innerPadding ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(innerPadding)
                                        ) {
                                            val currentUdid = activeDevice?.udid
                                                ?: activeUsbDevice?.serialNumber?.takeIf { it.isNotBlank() && it != "UnknownSerial" }
                                                ?: "UnpairedDevice"
                                            val currentDeviceId = activeUsbDevice?.deviceId ?: 1

                                            when (selectedTab) {
                                                0 -> {
                                                    DashboardScreen(
                                                        usbDevice = activeUsbDevice,
                                                        pairedDevice = activeDevice,
                                                        pairingState = pairingState,
                                                        pairingBadge = pairingBadge,
                                                        terminalLogs = terminalLogs,
                                                        pairingManager = app.pairingManager,
                                                        daemonStatus = daemonStatus,
                                                        socketType = socketType,
                                                        bridgeBackend = bridgeBackend,
                                                        recentActivity = recentActivityLogs,
                                                        onInitiatePairing = {
                                                            app.pairingManager.initiatePairing(
                                                                deviceId = currentDeviceId,
                                                                udid = currentUdid,
                                                                productVersion = activeDevice?.productVersion ?: "17.4.1"
                                                            )
                                                        },
                                                        onUnpair = {
                                                            scope.launch {
                                                                app.pairingManager.unpair(currentUdid)
                                                                app.deviceRepository.logActivity(
                                                                    udid = currentUdid,
                                                                    actionType = "PAIRING",
                                                                    description = "Unpaired device & removed trust record",
                                                                    status = "SUCCESS"
                                                                )
                                                            }
                                                        },
                                                        onClearLogs = { app.rootChecker.clearLogs() },
                                                        onScanUsb = {
                                                            val detectedAll = app.usbDeviceWatcher.scanAllDevices()
                                                            if (detectedAll.isNotEmpty()) {
                                                                scope.launch {
                                                                    app.deviceRepository.syncConnectedUsbDevices(detectedAll, pairingBadge)
                                                                }
                                                            }
                                                        },
                                                        onQuickAction = { target ->
                                                            when (target) {
                                                                QuickActionTarget.INSTALL_IPA -> {
                                                                    appsSubTab = 0
                                                                    selectedTab = 1
                                                                }
                                                                QuickActionTarget.SIDESTORE_1TAP -> {
                                                                    appsSubTab = 1
                                                                    selectedTab = 1
                                                                }
                                                                QuickActionTarget.ENABLE_JIT -> {
                                                                    selectedTab = 2
                                                                }
                                                                QuickActionTarget.SCREEN_MIRROR -> {
                                                                    selectedTab = 3
                                                                }
                                                                QuickActionTarget.AFC_FILES -> {
                                                                    toolsSubTab = 1
                                                                    selectedTab = 4
                                                                }
                                                                QuickActionTarget.DEVICE_TOOLS -> {
                                                                    toolsSubTab = 0
                                                                    selectedTab = 4
                                                                }
                                                            }
                                                        },
                                                        hasUpdateBadge = hasUpdateBadge,
                                                        onOpenLegalSettings = { isViewingLegalSettings = true },
                                                        activeWirelessEndpoint = activeWirelessEndpoint,
                                                        onToggleWifiSync = { enabled ->
                                                            scope.launch {
                                                                app.deviceRepository.enableWifiSyncForDevice(
                                                                    udid = currentUdid,
                                                                    deviceId = currentDeviceId,
                                                                    enabled = enabled,
                                                                    wirelessHost = activeWirelessEndpoint?.hostIp
                                                                )
                                                            }
                                                        },
                                                        onOpenDevicesDrawer = { showDevicesDrawer = true }
                                                    )
                                                }
                                                1 -> {
                                                    AppsScreen(
                                                        udid = currentUdid,
                                                        deviceId = currentDeviceId,
                                                        initialSubTab = appsSubTab,
                                                        pairingManager = app.pairingManager,
                                                        installProxy = app.installationProxyClient,
                                                        certificateVault = app.certificateVault,
                                                        ipaSigner = app.ipaSigner,
                                                        onLogActivity = { actionType, desc, statusText ->
                                                            app.deviceRepository.logActivity(
                                                                udid = currentUdid,
                                                                actionType = actionType,
                                                                description = desc,
                                                                status = statusText
                                                            )
                                                        }
                                                    )
                                                }
                                                2 -> {
                                                    JitScreen(
                                                        jitManager = app.jitManager,
                                                        activeDevice = activeDevice,
                                                        onLogActivity = { actionType, desc, statusText ->
                                                            app.deviceRepository.logActivity(
                                                                udid = currentUdid,
                                                                actionType = actionType,
                                                                description = desc,
                                                                status = statusText
                                                            )
                                                        }
                                                    )
                                                }
                                                3 -> {
                                                    MirrorScreen(
                                                        dvtStreamClient = app.dvtStreamClient,
                                                        wdaClient = app.wdaClient
                                                    )
                                                }
                                                4 -> {
                                                    ToolsScreen(
                                                        udid = currentUdid,
                                                        deviceId = currentDeviceId,
                                                        initialSubTab = toolsSubTab,
                                                        activeDevice = activeDevice,
                                                        appVersion = app.updateManager.installedVersionName,
                                                        daemonStatus = daemonStatus,
                                                        socketType = socketType,
                                                        hasUpdateBadge = hasUpdateBadge,
                                                        afcManagerOverride = app.afcFileManager,
                                                        tunnelManager = app.iproxyTunnelManager,
                                                        backupManager = app.appBackupManager,
                                                        wirelessHost = activeWirelessEndpoint?.hostIp,
                                                        onOpenLegalSettings = { isViewingLegalSettings = true },
                                                        onLogActivity = { actionType, desc, statusText ->
                                                            app.deviceRepository.logActivity(
                                                                udid = currentUdid,
                                                                actionType = actionType,
                                                                description = desc,
                                                                status = statusText
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (showDevicesDrawer) {
                                        DevicesDrawerSheet(
                                            connectedUsbDevices = connectedUsbDevices,
                                            savedDevices = savedDevices,
                                            activeUdid = activeDevice?.udid,
                                            onSelectDevice = { udid ->
                                                app.deviceRepository.selectActiveDevice(udid)
                                            },
                                            onRescanUsb = {
                                                val found = app.usbDeviceWatcher.scanAllDevices()
                                                if (found.isNotEmpty()) {
                                                    scope.launch {
                                                        app.deviceRepository.syncConnectedUsbDevices(found, pairingBadge)
                                                    }
                                                }
                                            },
                                            onPairUsbDevice = { usb ->
                                                scope.launch {
                                                    val synced = app.deviceRepository.syncConnectedUsbDevice(usb, pairingBadge)
                                                    app.deviceRepository.selectActiveDevice(synced.udid)
                                                    app.pairingManager.initiatePairing(
                                                        deviceId = usb.deviceId,
                                                        udid = synced.udid,
                                                        productVersion = synced.productVersion
                                                    )
                                                }
                                                showDevicesDrawer = false
                                            },
                                            onUpdateNickname = { udid, label ->
                                                scope.launch { app.deviceRepository.updateDeviceNickname(udid, label) }
                                            },
                                            onTogglePin = { udid ->
                                                scope.launch { app.deviceRepository.togglePinDevice(udid) }
                                            },
                                            onForgetDevice = { udid ->
                                                scope.launch { app.deviceRepository.forgetDevice(udid) }
                                            },
                                            onDismiss = { showDevicesDrawer = false },
                                            wirelessDevices = wirelessDevices,
                                            isScanningWifi = isScanningWifi,
                                            onRescanWifi = { app.wirelessDeviceWatcher.rescan() },
                                            onAddManualWifiIp = { host, port, label ->
                                                val res = app.wirelessDeviceWatcher.addManualEndpoint(
                                                    hostInput = host,
                                                    port = port,
                                                    udidHint = activeDevice?.udid ?: "",
                                                    label = label
                                                )
                                                res.onSuccess {
                                                    app.deviceRepository.syncWirelessDevices(app.wirelessDeviceWatcher.wirelessDevices.value)
                                                }
                                                res
                                            },
                                            onEnableWifiSync = { dev ->
                                                scope.launch {
                                                    val matchedUsb = connectedUsbDevices.firstOrNull { usb ->
                                                        usb.serialNumber.equals(dev.udid, ignoreCase = true) ||
                                                            usb.serialNumber.equals(dev.serialNumber, ignoreCase = true) ||
                                                            "USB-${usb.deviceId}" == dev.udid
                                                    }
                                                    app.deviceRepository.enableWifiSyncForDevice(
                                                        udid = dev.udid,
                                                        deviceId = matchedUsb?.deviceId ?: activeUsbDevice?.deviceId ?: 1,
                                                        enabled = true,
                                                        wirelessHost = activeWirelessEndpoint?.hostIp
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Global In-App Updater Modals (Only after consent accepted)
                    if (isConsentAccepted && showWhatsNew) {
                        WhatsNewSheet(
                            versionName = app.updateManager.installedVersionName,
                            onDismiss = { showWhatsNew = false }
                        )
                    }

                    if (isConsentAccepted && showUpdateDialog) {
                        UpdateDialogScreen(
                            updateState = updateState,
                            currentVersionName = app.updateManager.installedVersionName,
                            onDownloadAndInstall = { release ->
                                app.updateManager.startDownload(release)
                            },
                            onInstallVerified = { release, apkFile ->
                                app.updateManager.installUpdate(release, apkFile)
                            },
                            onCancelDownload = {
                                app.updateManager.cancelDownload()
                            },
                            onSkipVersion = { versionCode ->
                                showUpdateDialog = false
                                scope.launch { app.updateManager.skipVersion(versionCode) }
                            },
                            onRemindMeLater = {
                                showUpdateDialog = false
                                app.updateManager.dismissUpdate()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    selectedTab: Int,
    hasUpdateBadge: Boolean = false,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        NavItem("Device", Icons.Default.Devices),
        NavItem("Apps", Icons.Default.InstallMobile),
        NavItem("JIT", Icons.Default.FlashOn),
        NavItem("Mirror", Icons.Default.ScreenShare),
        NavItem("Tools", Icons.Default.Build)
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    NavigationBar(
        modifier = Modifier
            .navigationBarsPadding()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = primaryColor
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = selectedTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(index) },
                icon = {
                    if (index == 4 && hasUpdateBadge) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = Color(0xFFEF4444))
                            }
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                label = {
                    Text(
                        text = item.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = primaryColor,
                    indicatorColor = primaryColor,
                    unselectedIconColor = Color(0xFF64748B),
                    unselectedTextColor = Color(0xFF64748B)
                )
            )
        }
    }
}

private data class NavItem(val title: String, val icon: ImageVector)
