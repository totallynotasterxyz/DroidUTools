// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.BuildCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.installer.InstallStage
import com.example.installer.InstallationProxyClient
import com.example.installer.IpaMetadata
import com.example.installer.IpaPackageInspector
import com.example.signer.CertificateVault
import com.example.signer.EntitlementPreset
import com.example.signer.EntitlementsBuilder
import com.example.signer.IpaSigner
import com.example.signer.SigningCertificate
import com.example.signer.SigningConfiguration
import com.example.signer.SigningProgress
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusTunnel
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Full IPA Tweaker & Entitlements Studio:
 * Allows modifying Bundle ID, Display Name, Version, URL schemes, Custom App Icon (via Android Photo Picker),
 * stripping PlugIns/Watch extensions, injecting .dylib tweaks, toggling Entitlement Presets synced with
 * a Raw XML Plist Editor, signing via IpaSigner, and 1-tap installing onto the active iOS device.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignAndTweakSubScreen(
    deviceId: Int,
    accent: Color,
    certificateVault: CertificateVault,
    ipaSigner: IpaSigner,
    installProxy: InstallationProxyClient,
    onJumpToVault: () -> Unit,
    onLogActivity: (actionType: String, description: String, status: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vaultEntries by certificateVault.entries.collectAsState()
    val activeVaultEntry = vaultEntries.firstOrNull { it.isActive } ?: vaultEntries.firstOrNull()

    var sourceIpaFile by remember { mutableStateOf<File?>(null) }
    var inspectedMeta by remember { mutableStateOf<IpaMetadata?>(null) }
    var bundleIdInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var versionInput by remember { mutableStateOf("") }
    var urlSchemeInput by remember { mutableStateOf("") }
    var stripPluginsAndWatch by remember { mutableStateOf(true) }
    var customIconBytes by remember { mutableStateOf<ByteArray?>(null) }
    var customIconPreview by remember { mutableStateOf<Bitmap?>(null) }
    var dylibFiles by remember { mutableStateOf<List<File>>(emptyList()) }

    var enabledPresets by remember {
        mutableStateOf(setOf(EntitlementPreset.JIT_DEBUGGING, EntitlementPreset.APP_GROUPS))
    }
    var isRawEntitlementsMode by remember { mutableStateOf(false) }
    var rawEntitlementsXml by remember {
        mutableStateOf(
            EntitlementsBuilder.buildEntitlementsXml(
                bundleId = "com.sideloaded.tweaked",
                teamId = activeVaultEntry?.certificate?.teamId ?: "TEAMID0000",
                enabledPresets = setOf(EntitlementPreset.JIT_DEBUGGING, EntitlementPreset.APP_GROUPS)
            )
        )
    }

    var signingProgress by remember { mutableStateOf<SigningProgress?>(null) }
    var installStage by remember { mutableStateOf<InstallStage?>(null) }

    val ipaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val target = File(context.cacheDir, "tweak_src_${System.currentTimeMillis()}.ipa")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
                sourceIpaFile = target
                signingProgress = null
                installStage = null
                val meta = IpaPackageInspector.inspectIpaFile(target).getOrNull()
                inspectedMeta = meta
                if (meta != null) {
                    bundleIdInput = meta.bundleId
                    displayNameInput = meta.appName
                    versionInput = meta.version
                    rawEntitlementsXml = EntitlementsBuilder.buildEntitlementsXml(
                        bundleId = meta.bundleId,
                        teamId = activeVaultEntry?.certificate?.teamId ?: "TEAMID0000",
                        enabledPresets = enabledPresets
                    )
                }
            }
        }
    }

    val dylibPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val rawName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.endsWith(".dylib") }
                    ?: "tweak_${System.currentTimeMillis()}.dylib"
                val dest = File(context.cacheDir, rawName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
                dylibFiles = (dylibFiles + dest).distinctBy { it.name }
            }
        }
    }

    val entitlementsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            if (!content.isNullOrBlank()) {
                rawEntitlementsXml = content
                enabledPresets = EntitlementsBuilder.detectPresetsFromXml(content)
                isRawEntitlementsMode = true
            }
        }
    }

    // Zero-permission Android Photo Picker for custom PNG app icon
    val iconPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        val scaled = Bitmap.createScaledBitmap(decoded, 180, 180, true)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                        customIconBytes = out.toByteArray()
                        customIconPreview = scaled
                    }
                }
            }
        }
    }

    val entitlementValidation = remember(rawEntitlementsXml) {
        EntitlementsBuilder.validateEntitlementsXml(rawEntitlementsXml)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Signer Identity Status Banner
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (activeVaultEntry == null) StatusWarning else GreenPrimary.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeVaultEntry?.certificate?.displayName ?: "No Vault Certificate Selected",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (activeVaultEntry != null) {
                                "Team ${activeVaultEntry.certificate.teamId} • OCSP ${activeVaultEntry.ocspStatus.label}"
                            } else {
                                "Import a .p12 or authenticate Apple ID in Vault, or sign ad-hoc"
                            },
                            color = if (activeVaultEntry != null) Color(0xFF94A3B8) else StatusWarning,
                            fontSize = 11.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onJumpToVault,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Open Vault", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Source IPA Picker
        item {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { ipaPicker.launch(arrayOf("*/*")) }
                    .testTag("tweak_pick_ipa_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = accent)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sourceIpaFile?.name ?: "Select Target .IPA to Tweak & Sign",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = inspectedMeta?.let { "${it.appName} (${it.bundleId}) • v${it.version}" }
                                ?: "Tap to choose any .ipa archive",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // IPA Tweaker Controls Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.BuildCircle, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                        Text(
                            text = "IPA TWEAKER & DYLIB INJECTOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    OutlinedTextField(
                        value = bundleIdInput,
                        onValueChange = { bundleIdInput = it },
                        label = { Text("Bundle Identifier Override") },
                        placeholder = { Text("com.example.clonedapp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = { displayNameInput = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = versionInput,
                            onValueChange = { versionInput = it },
                            label = { Text("Version") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                    }

                    OutlinedTextField(
                        value = urlSchemeInput,
                        onValueChange = { urlSchemeInput = it },
                        label = { Text("Custom URL Scheme (optional)") },
                        placeholder = { Text("e.g. mytweakedapp") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Icon Picker & PlugIn Strip Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                iconPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (customIconPreview != null) "Change Icon" else "Custom Icon", fontSize = 12.sp)
                        }

                        customIconPreview?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Custom app icon preview",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, accent, RoundedCornerShape(10.dp))
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        OutlinedButton(
                            onClick = { dylibPicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ .dylib", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (dylibFiles.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dylibFiles.forEach { dylib ->
                                FilterChip(
                                    selected = true,
                                    onClick = { dylibFiles = dylibFiles.filter { it.name != dylib.name } },
                                    label = { Text(dylib.name, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, contentDescription = "Remove ${dylib.name}", modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Strip PlugIns/ & Watch/ Extensions",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Removes app extensions so free accounts don't waste the 3-app slot limit",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = stripPluginsAndWatch,
                            onCheckedChange = { stripPluginsAndWatch = it }
                        )
                    }
                }
            }
        }

        // Entitlements Studio (Presets + Raw XML Plist Editor)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = StatusTunnel, modifier = Modifier.size(18.dp))
                            Text(
                                text = "ENTITLEMENTS STUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusTunnel,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { entitlementsPicker.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Import .xcent", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { isRawEntitlementsMode = !isRawEntitlementsMode },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("toggle_raw_entitlements_button")
                            ) {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isRawEntitlementsMode) "Presets" else "Raw XML", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (!isRawEntitlementsMode) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            EntitlementPreset.entries.forEach { preset ->
                                val selected = preset in enabledPresets
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        val next = if (selected) enabledPresets - preset else enabledPresets + preset
                                        enabledPresets = next
                                        rawEntitlementsXml = EntitlementsBuilder.buildEntitlementsXml(
                                            bundleId = bundleIdInput.ifBlank { "com.sideloaded.tweaked" },
                                            teamId = activeVaultEntry?.certificate?.teamId ?: "TEAMID0000",
                                            enabledPresets = next
                                        )
                                    },
                                    leadingIcon = if (selected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                                    } else null,
                                    label = {
                                        Text(
                                            text = if (preset.requiresPaidProfile) "${preset.title} (Paid)" else preset.title,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = StatusTunnel.copy(alpha = 0.22f),
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = rawEntitlementsXml,
                            onValueChange = { updated ->
                                rawEntitlementsXml = updated
                                enabledPresets = EntitlementsBuilder.detectPresetsFromXml(updated)
                            },
                            label = { Text("Raw Entitlements Plist XML") },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0)
                            ),
                            minLines = 8,
                            maxLines = 16,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("raw_entitlements_editor")
                        )
                    }

                    val validationText = if (entitlementValidation.isSuccess) {
                        "✓ Valid Plist XML • ${entitlementValidation.getOrDefault(0)} entitlement key(s) armed"
                    } else {
                        "✗ ${entitlementValidation.exceptionOrNull()?.message}"
                    }
                    Text(
                        text = validationText,
                        color = if (entitlementValidation.isSuccess) GreenPrimary else StatusError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Primary Execute Tweak & Sign Action
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = accent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val ipa = sourceIpaFile ?: return@Button
                            val cert = activeVaultEntry?.certificate ?: SigningCertificate(
                                id = "adhoc",
                                displayName = "Ad-Hoc / Local Identity",
                                teamName = "Local Team",
                                teamId = "TEAMID0000"
                            )
                            val cfg = SigningConfiguration(
                                certificate = cert,
                                newBundleId = bundleIdInput.ifBlank { null },
                                newDisplayName = displayNameInput.ifBlank { null },
                                newVersion = versionInput.ifBlank { null },
                                enableDebuggingJit = EntitlementPreset.JIT_DEBUGGING in enabledPresets,
                                entitlementsXml = rawEntitlementsXml,
                                dylibsToInject = dylibFiles,
                                stripPluginsAndWatch = stripPluginsAndWatch,
                                customIconPngBytes = customIconBytes,
                                customUrlScheme = urlSchemeInput.ifBlank { null }
                            )
                            scope.launch {
                                installStage = null
                                ipaSigner.signIpa(ipa, cfg).collect { prog ->
                                    signingProgress = prog
                                    if (prog is SigningProgress.Success) {
                                        onLogActivity(
                                            "TWEAK_SIGN",
                                            "Tweaked & signed ${prog.appName} (${prog.entitlementsCount} entitlements, ${prog.dylibsInjectedCount} dylibs)",
                                            "SUCCESS"
                                        )
                                    } else if (prog is SigningProgress.Error) {
                                        onLogActivity("TWEAK_SIGN", "Signing failed: ${prog.message}", "ERROR")
                                    }
                                }
                            }
                        },
                        enabled = sourceIpaFile != null &&
                            entitlementValidation.isSuccess &&
                            signingProgress !is SigningProgress.Status,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("sign_tweak_execute_button")
                    ) {
                        Text("Repack, Inject & Sign IPA", fontWeight = FontWeight.Bold)
                    }

                    when (val prog = signingProgress) {
                        is SigningProgress.Status -> {
                            LinearProgressIndicator(
                                progress = { prog.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = accent
                            )
                            Text(prog.step, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        }
                        is SigningProgress.Error -> {
                            Text("Error: ${prog.message}", color = StatusError, fontSize = 12.sp)
                        }
                        is SigningProgress.Success -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "✓ Ready: ${prog.signedIpaFile.name} (${prog.bundleId}) • ${prog.entitlementsCount} entitlements, ${prog.dylibsInjectedCount} dylib(s)",
                                    color = GreenPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val meta = IpaMetadata(
                                                bundleId = prog.bundleId,
                                                appName = prog.appName,
                                                version = versionInput.ifBlank { "1.0" },
                                                buildNumber = "1",
                                                minOsVersion = "15.0",
                                                appExecutable = prog.appName,
                                                fileSizeBytes = prog.signedIpaFile.length(),
                                                fileName = prog.signedIpaFile.name
                                            )
                                            installProxy.installIpaPackage(deviceId, prog.signedIpaFile, meta)
                                                .collect { stage ->
                                                    installStage = stage
                                                    if (stage is InstallStage.Complete) {
                                                        onLogActivity("INSTALL", "Installed tweaked ${prog.appName}", "SUCCESS")
                                                    }
                                                }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GreenPrimary, contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                ) {
                                    Icon(Icons.Default.InstallMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install Signed IPA to Device", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        null -> {}
                    }

                    installStage?.let { stage ->
                        when (stage) {
                            is InstallStage.UploadingToStaging -> {
                                LinearProgressIndicator(progress = { stage.progress }, modifier = Modifier.fillMaxWidth(), color = GreenPrimary)
                                Text("Uploading via AFC (${(stage.progress * 100).toInt()}%)...", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                            is InstallStage.InstallingViaProxy -> {
                                LinearProgressIndicator(progress = { stage.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = GreenPrimary)
                                Text("Installing: ${stage.statusText} (${stage.percent}%)", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                            }
                            is InstallStage.Complete -> {
                                Text("✓ Installed ${stage.appName} onto active iOS device!", color = GreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            is InstallStage.Failed -> {
                                Text("Install error: ${stage.error}", color = StatusError, fontSize = 12.sp)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
