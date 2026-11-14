// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DroidUtoolsApp
import com.example.installer.AppType
import com.example.installer.DownloadProgress
import com.example.installer.InstallStage
import com.example.installer.InstallationProxyClient
import com.example.installer.InstalledApp
import com.example.installer.IpaDownloader
import com.example.installer.IpaMetadata
import com.example.installer.IpaPackageInspector
import com.example.pairing.PairingManager
import com.example.sidestore.SideStoreBundleOption
import com.example.sidestore.SideStoreInjector
import com.example.sidestore.SideStoreInstallStage
import com.example.sidestore.SideloadAppCatalog
import com.example.sidestore.SideloadAppItem
import com.example.signer.AnisetteClient
import com.example.signer.CertificateVault
import com.example.signer.ExpiryTracker
import com.example.signer.IpaSigner
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusTunnel
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun AppsScreen(
    udid: String,
    deviceId: Int = 1,
    initialSubTab: Int = 0,
    pairingManager: PairingManager,
    installProxy: InstallationProxyClient,
    certificateVault: CertificateVault? = null,
    ipaSigner: IpaSigner? = null,
    onLogActivity: (actionType: String, description: String, status: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    var selectedSubTab by remember { mutableIntStateOf(initialSubTab.coerceIn(0, 4)) }

    LaunchedEffect(initialSubTab) {
        selectedSubTab = initialSubTab.coerceIn(0, 4)
    }

    val ipaDownloader = remember { IpaDownloader(context) }
    val sideStoreInjector = remember { SideStoreInjector(context, pairingManager) }
    val expiryTracker = remember { ExpiryTracker() }
    val anisetteClient = remember { AnisetteClient() }
    val resolvedSigner = remember(ipaSigner) { ipaSigner ?: IpaSigner(context) }
    val resolvedVault = remember(certificateVault) { certificateVault ?: CertificateVault(context) }

    var installedApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var showSideStoreGuide by remember { mutableStateOf(false) }
    var guideBundleOption by remember { mutableStateOf(SideStoreBundleOption.SIDESTORE_AND_LIVECONTAINER) }

    // Load installed apps
    LaunchedEffect(deviceId) {
        isLoadingApps = true
        val result = installProxy.listInstalledApps(deviceId)
        if (result.isSuccess) {
            installedApps = result.getOrNull() ?: emptyList()
        }
        isLoadingApps = false
    }

    if (showSideStoreGuide) {
        SideStoreGuideDialog(
            bundleOption = guideBundleOption,
            onDismiss = { showSideStoreGuide = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // Sub-navigation tab row
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
            listOf("Sideload Store", "IPA Installer", "Device Apps", "Vault & Certs", "Tweak & Sign").forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedSubTab) {
                0 -> SideloadStoreSubScreen(
                    udid = udid,
                    accent = accent,
                    sideStoreInjector = sideStoreInjector,
                    ipaDownloader = ipaDownloader,
                    installProxy = installProxy,
                    deviceId = deviceId,
                    onLogActivity = onLogActivity,
                    onLaunchGuide = { opt ->
                        guideBundleOption = opt
                        showSideStoreGuide = true
                    }
                )
                1 -> IpaInstallerSubScreen(
                    deviceId = deviceId,
                    accent = accent,
                    ipaDownloader = ipaDownloader,
                    installProxy = installProxy,
                    onLogActivity = onLogActivity
                )
                2 -> DeviceAppsSubScreen(
                    apps = installedApps,
                    isLoading = isLoadingApps,
                    accent = accent,
                    onRefresh = {
                        scope.launch {
                            isLoadingApps = true
                            installedApps = installProxy.listInstalledApps(deviceId).getOrNull() ?: emptyList()
                            isLoadingApps = false
                        }
                    },
                    onUninstall = { bundleId ->
                        scope.launch {
                            val res = installProxy.uninstallApp(deviceId, bundleId)
                            if (res.isSuccess) {
                                installedApps = installedApps.filter { it.bundleId != bundleId }
                                onLogActivity("UNINSTALL", "Uninstalled $bundleId", "SUCCESS")
                            }
                        }
                    }
                )
                3 -> CertificateVaultSubScreen(
                    udid = udid,
                    accent = accent,
                    certificateVault = resolvedVault,
                    expiryTracker = expiryTracker,
                    anisetteClient = anisetteClient,
                    onLogActivity = onLogActivity
                )
                4 -> SignAndTweakSubScreen(
                    deviceId = deviceId,
                    accent = accent,
                    certificateVault = resolvedVault,
                    ipaSigner = resolvedSigner,
                    installProxy = installProxy,
                    onJumpToVault = { selectedSubTab = 3 },
                    onLogActivity = onLogActivity
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 1. Sideload Store SubScreen (1-Tap SideStore & Community)
// -------------------------------------------------------------
@Composable
private fun SideloadStoreSubScreen(
    udid: String,
    accent: Color,
    sideStoreInjector: SideStoreInjector,
    ipaDownloader: IpaDownloader,
    installProxy: InstallationProxyClient,
    deviceId: Int,
    onLogActivity: (String, String, String) -> Unit,
    onLaunchGuide: (SideStoreBundleOption) -> Unit
) {
    val scope = rememberCoroutineScope()
    var installStatusMessage by remember { mutableStateOf<String?>(null) }
    var isInstalling by remember { mutableStateOf(false) }
    var selectedOption by remember {
        mutableStateOf(SideStoreBundleOption.SIDESTORE_AND_LIVECONTAINER)
    }
    var repairMessage by remember { mutableStateOf<String?>(null) }

    // Per-community-app live download/install status
    val communityAppStatuses = remember { mutableStateMapOf<String, String>() }
    val communityAppProgress = remember { mutableStateMapOf<String, Float?>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Card: SideStore + LiveContainer 1-Tap Installer
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = StatusTunnel
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "1-Tap Sideload Engine",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(StatusTunnel.copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = selectedOption.badge,
                                        color = StatusTunnel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Zero-computer iOS sideloading with automated pairing injection",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Text(
                        text = "INSTALLATION CHOICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SideStoreBundleOption.entries.forEach { option ->
                            val isSelected = selectedOption == option
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF070B14))
                                    .border(
                                        1.dp,
                                        if (isSelected) StatusTunnel else Color(0xFF1E293B),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedOption = option }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) StatusTunnel else Color(0xFF334155)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = option.title,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 13.sp
                                        )
                                        if (option == SideStoreBundleOption.SIDESTORE_AND_LIVECONTAINER) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(GreenPrimary.copy(alpha = 0.2f))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "NO 3-APP LIMIT",
                                                    color = GreenPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = option.description,
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    if (isInstalling || installStatusMessage != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (isInstalling) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = StatusTunnel
                                )
                            }
                            Text(
                                text = installStatusMessage ?: "Installing...",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (installStatusMessage?.startsWith("Failed") == true) StatusError else Color(0xFFCBD5E1)
                            )
                        }
                    }

                    if (repairMessage != null) {
                        Text(
                            text = repairMessage!!,
                            color = if (repairMessage!!.startsWith("Error")) StatusError else GreenPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isInstalling = true
                                    repairMessage = null
                                    sideStoreInjector.installBundleOption(
                                        udid = udid,
                                        option = selectedOption,
                                        appleId = "user@icloud.com"
                                    ).collect { stage ->
                                        when (stage) {
                                            is SideStoreInstallStage.Fetching ->
                                                installStatusMessage = stage.status
                                            is SideStoreInstallStage.InjectingPairing ->
                                                installStatusMessage = "Injecting device pairing records into ${stage.appName}..."
                                            is SideStoreInstallStage.Signing ->
                                                installStatusMessage = "Signing ${stage.appName} with developer certificate..."
                                            is SideStoreInstallStage.Installing ->
                                                installStatusMessage = "Installing ${stage.appName} via InstallationProxy (${(stage.progress * 100).toInt()}%)..."
                                            is SideStoreInstallStage.ReadyForIosSetup -> {
                                                isInstalling = false
                                                installStatusMessage = null
                                                onLogActivity("SIDESTORE", "Installed ${selectedOption.title}", "SUCCESS")
                                                onLaunchGuide(stage.bundleOption)
                                            }
                                            is SideStoreInstallStage.Failed -> {
                                                isInstalling = false
                                                installStatusMessage = "Failed: ${stage.error}"
                                                onLogActivity("SIDESTORE", "Failed ${selectedOption.title}: ${stage.error}", "ERROR")
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isInstalling,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusTunnel,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("1-Tap Install & Pair", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val res = sideStoreInjector.repairSideStorePairing(udid)
                                    repairMessage = if (res.isSuccess) {
                                        onLogActivity("PAIRING", "Repaired SideStore pairing", "SUCCESS")
                                        "✓ Pairing file re-injected into SideStore & LiveContainer!"
                                    } else {
                                        "Error repairing pairing: ${res.exceptionOrNull()?.message ?: "Not paired"}"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF64D2FF)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Repair Pairing")
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "COMMUNITY SIDELOADING APPS",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // Community Apps List (fully wired!)
        items(SideloadAppCatalog.communityApps.filter { it.id != "sidestore" }) { app ->
            val statusText = communityAppStatuses[app.id]
            val progressVal = communityAppProgress[app.id]

            CommunityAppCard(
                app = app,
                accent = accent,
                statusText = statusText,
                progress = progressVal,
                onInstall = {
                    if (progressVal != null) return@CommunityAppCard
                    scope.launch {
                        communityAppProgress[app.id] = 0f
                        communityAppStatuses[app.id] = "Fetching ${app.name} IPA..."
                        var downloadedFile: File? = null
                        ipaDownloader.downloadIpa(app.downloadUrl, "${app.id}.ipa").collect { dl ->
                            when (dl) {
                                is DownloadProgress.Progress -> {
                                    communityAppProgress[app.id] = dl.progress.coerceIn(0f, 1f)
                                    communityAppStatuses[app.id] = "Downloading (${dl.speedKbps} KB/s)..."
                                }
                                is DownloadProgress.Success -> {
                                    downloadedFile = dl.downloadedFile
                                    communityAppStatuses[app.id] = "Inspecting & installing ${app.name}..."
                                }
                                is DownloadProgress.Error -> {
                                    communityAppProgress.remove(app.id)
                                    communityAppStatuses[app.id] = "Error: ${dl.message}"
                                    onLogActivity("SIDELOAD", "Failed downloading ${app.name}: ${dl.message}", "ERROR")
                                }
                            }
                        }

                        val ipaFile = downloadedFile ?: return@launch
                        val meta = IpaPackageInspector.inspectIpaFile(ipaFile).getOrNull()
                            ?: com.example.installer.IpaMetadata(
                                bundleId = app.bundleId,
                                appName = app.name,
                                version = app.version,
                                buildNumber = "1",
                                minOsVersion = "15.0",
                                appExecutable = app.name,
                                fileSizeBytes = ipaFile.length(),
                                fileName = ipaFile.name
                            )

                        installProxy.installIpaPackage(deviceId, ipaFile, meta).collect { stage ->
                            when (stage) {
                                is InstallStage.UploadingToStaging -> {
                                    communityAppProgress[app.id] = stage.progress
                                    communityAppStatuses[app.id] = "Uploading via AFC (${(stage.progress * 100).toInt()}%)..."
                                }
                                is InstallStage.InstallingViaProxy -> {
                                    communityAppProgress[app.id] = stage.percent / 100f
                                    communityAppStatuses[app.id] = "${stage.statusText} (${stage.percent}%)"
                                }
                                is InstallStage.Complete -> {
                                    communityAppProgress.remove(app.id)
                                    communityAppStatuses[app.id] = "✓ ${app.name} installed"
                                    onLogActivity("INSTALL", "Installed ${app.name} (${meta.bundleId})", "SUCCESS")
                                }
                                is InstallStage.Failed -> {
                                    communityAppProgress.remove(app.id)
                                    communityAppStatuses[app.id] = "Failed: ${stage.error}"
                                }
                                else -> {}
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CommunityAppCard(
    app: SideloadAppItem,
    accent: Color,
    statusText: String?,
    progress: Float?,
    onInstall: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (app.category) {
                            "Emulation" -> Icons.Default.FlashOn
                            "JIT & Debug" -> Icons.Default.Build
                            else -> Icons.Default.InstallMobile
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = app.version,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Text(
                        text = app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        maxLines = 2
                    )
                }

                Button(
                    onClick = onInstall,
                    enabled = progress == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = accent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("community_install_${app.id}")
                ) {
                    Text(if (progress != null) "Working..." else "Install", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = accent,
                    trackColor = Color(0xFF1E293B)
                )
            }

            statusText?.let { status ->
                Text(
                    text = status,
                    fontSize = 11.sp,
                    color = when {
                        status.startsWith("✓") -> GreenPrimary
                        status.startsWith("Error") || status.startsWith("Failed") -> StatusError
                        else -> accent
                    }
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 2. IPA Installer SubScreen (URL download & File Picker)
// -------------------------------------------------------------
@Composable
private fun IpaInstallerSubScreen(
    deviceId: Int,
    accent: Color,
    ipaDownloader: IpaDownloader,
    installProxy: InstallationProxyClient,
    onLogActivity: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var urlInput by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var inspectedMetadata by remember { mutableStateOf<IpaMetadata?>(null) }
    var inspectError by remember { mutableStateOf<String?>(null) }
    var installStage by remember { mutableStateOf<InstallStage?>(null) }
    var downloadStage by remember { mutableStateOf<DownloadProgress?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                inspectError = null
                val tempFile = File(context.cacheDir, "picked_${System.currentTimeMillis()}.ipa")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                selectedFile = tempFile
                val meta = IpaPackageInspector.inspectIpaFile(tempFile)
                if (meta.isSuccess) {
                    inspectedMetadata = meta.getOrNull()
                } else {
                    inspectError = meta.exceptionOrNull()?.message ?: "Could not read Info.plist from selected archive."
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "INSTALL IPA FROM URL OR LOCAL STORAGE",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // URL Input Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Download IPA from Direct URL",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("https://example.com/app.ipa", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                inspectError = null
                                scope.launch {
                                    ipaDownloader.downloadIpa(urlInput).collect { dl ->
                                        downloadStage = dl
                                        if (dl is DownloadProgress.Success) {
                                            selectedFile = dl.downloadedFile
                                            val metaRes = IpaPackageInspector.inspectIpaFile(dl.downloadedFile)
                                            inspectedMetadata = metaRes.getOrNull()
                                            if (metaRes.isFailure) {
                                                inspectError = metaRes.exceptionOrNull()?.message
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = urlInput.isNotBlank() && downloadStage !is DownloadProgress.Progress,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch & Download IPA", fontWeight = FontWeight.Bold)
                    }

                    // Live Download Progress / Error UI (previously dropped!)
                    when (val dl = downloadStage) {
                        is DownloadProgress.Progress -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { dl.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = accent
                                )
                                val mbDone = String.format("%.1f", dl.downloadedBytes / (1024.0 * 1024.0))
                                val mbTotal = if (dl.totalBytes > 0) String.format("%.1f MB", dl.totalBytes / (1024.0 * 1024.0)) else "--"
                                Text(
                                    text = "Downloaded $mbDone MB / $mbTotal • ${dl.speedKbps} KB/s",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                        is DownloadProgress.Error -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                Text(dl.message, color = StatusError, fontSize = 12.sp)
                            }
                        }
                        is DownloadProgress.Success -> {
                            Text(
                                text = "✓ Downloaded ${dl.downloadedFile.name}",
                                color = GreenPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        null -> {}
                    }
                }
            }
        }

        // Local File Picker Card
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filePicker.launch("*/*") }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedFile?.name ?: "Select Local .IPA File",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (selectedFile != null) "${(selectedFile!!.length() / (1024.0 * 1024.0)).let { String.format("%.1f", it) }} MB • Ready to Inspect & Install" else "Tap to browse internal storage or downloads",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        inspectError?.let { err ->
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = StatusError.copy(alpha = 0.5f)
                ) {
                    Text("IPA Inspection Error: $err", color = StatusError, fontSize = 12.sp)
                }
            }
        }

        // Inspected Metadata preview
        inspectedMetadata?.let { meta ->
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF0F172A)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                meta.iconBitmap?.let { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = meta.appName,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                }
                                Column {
                                    Text(
                                        text = meta.appName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${meta.bundleId} (v${meta.version})",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = accent
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    selectedFile?.let { file ->
                                        scope.launch {
                                            installProxy.installIpaPackage(deviceId, file, meta).collect { stage ->
                                                installStage = stage
                                                if (stage is InstallStage.Complete) {
                                                    onLogActivity("INSTALL", "Installed ${meta.appName} (${meta.bundleId})", "SUCCESS")
                                                } else if (stage is InstallStage.Failed) {
                                                    onLogActivity("INSTALL", "Install failed for ${meta.appName}: ${stage.error}", "ERROR")
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Install Now", fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            text = "Min iOS: ${meta.minOsVersion} • Team: ${meta.teamName ?: "Ad-hoc / Unsigned"} • Frameworks: ${meta.frameworks.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // Live Install Stage Card
        installStage?.let { stage ->
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = when (stage) {
                        is InstallStage.Complete -> GreenPrimary
                        is InstallStage.Failed -> StatusError
                        else -> accent
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Installation Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        when (stage) {
                            is InstallStage.Extracting -> {
                                LinearProgressIndicator(progress = { stage.progress }, modifier = Modifier.fillMaxWidth(), color = accent)
                                Text("Extracting IPA archive...", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is InstallStage.UploadingToStaging -> {
                                LinearProgressIndicator(progress = { stage.progress }, modifier = Modifier.fillMaxWidth(), color = accent)
                                Text("Uploading to /PublicStaging via AFC (${(stage.progress * 100).toInt()}%)...", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is InstallStage.VerifyingSignature -> {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = accent)
                                Text(stage.details, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is InstallStage.InstallingViaProxy -> {
                                LinearProgressIndicator(progress = { stage.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = GreenPrimary)
                                Text("Installing: ${stage.statusText} (${stage.percent}%)", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                            }
                            is InstallStage.Complete -> {
                                Text("✓ ${stage.appName} installed successfully on your iOS device!", color = GreenPrimary, fontWeight = FontWeight.Bold)
                            }
                            is InstallStage.Failed -> {
                                Text("Error: ${stage.error}", color = StatusError)
                                Text("Fix: ${stage.recoverableSuggestion}", color = StatusWarning, fontSize = 12.sp)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. Device Apps SubScreen (List & Uninstall with Confirmation)
// -------------------------------------------------------------
@Composable
private fun DeviceAppsSubScreen(
    apps: List<InstalledApp>,
    isLoading: Boolean,
    accent: Color,
    onRefresh: () -> Unit,
    onUninstall: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<AppType?>(null) }
    var pendingUninstallApp by remember { mutableStateOf<InstalledApp?>(null) }

    val filteredApps = apps.filter { app ->
        (filterType == null || app.appType == filterType) &&
                (searchQuery.isBlank() || app.appName.contains(searchQuery, ignoreCase = true) || app.bundleId.contains(searchQuery, ignoreCase = true))
    }

    pendingUninstallApp?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingUninstallApp = null },
            title = { Text("Uninstall ${target.appName}?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes ${target.bundleId} and its sandbox data from the connected iOS device.") },
            confirmButton = {
                Button(
                    onClick = {
                        onUninstall(target.bundleId)
                        pendingUninstallApp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Uninstall", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstallApp = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...", color = Color(0xFF64748B)) },
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
                onClick = onRefresh,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh apps", tint = accent)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                null to "All (${apps.size})",
                AppType.DEVELOPER to "Sideloaded",
                AppType.USER to "User",
                AppType.SYSTEM to "System"
            ).forEach { (type, label) ->
                val isSelected = filterType == type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) accent else Color(0xFF1E293B))
                        .clickable { filterType = type }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent)
            }
        } else if (filteredApps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(36.dp))
                    Text("No matching applications found", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps) { app ->
                    InstalledAppCard(
                        app = app,
                        accent = accent,
                        onUninstall = { pendingUninstallApp = app }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledAppCard(
    app: InstalledApp,
    accent: Color,
    onUninstall: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when (app.appType) {
                            AppType.DEVELOPER -> StatusTunnel.copy(alpha = 0.2f)
                            AppType.USER -> accent.copy(alpha = 0.2f)
                            AppType.SYSTEM -> Color(0xFF334155)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.InstallMobile,
                    contentDescription = null,
                    tint = when (app.appType) {
                        AppType.DEVELOPER -> StatusTunnel
                        AppType.USER -> accent
                        AppType.SYSTEM -> Color(0xFF94A3B8)
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "v${app.version}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Text(
                    text = app.bundleId,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Text(
                    text = "${app.appType.label} • ${app.diskUsageBytes / (1024 * 1024)} MB",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            if (app.appType != AppType.SYSTEM) {
                IconButton(
                    onClick = onUninstall,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Uninstall app",
                        tint = StatusError,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
