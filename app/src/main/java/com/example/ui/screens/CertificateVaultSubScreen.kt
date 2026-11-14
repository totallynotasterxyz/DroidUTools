// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.DroidUtoolsApp
import com.example.signer.AnisetteAuthResult
import com.example.signer.AnisetteClient
import com.example.signer.CertificateVault
import com.example.signer.ExpiryTracker
import com.example.signer.OcspStatus
import com.example.signer.VaultEntry
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusTunnel
import com.example.ui.theme.StatusWarning
import kotlinx.coroutines.launch

/**
 * Certificate & Provisioning Vault SubScreen backed by AndroidKeyStore AES-256-GCM encryption,
 * live OCSP revocation checking, device UDID allowlist verification, and Anisette Apple ID slots.
 */
@Composable
fun CertificateVaultSubScreen(
    udid: String,
    accent: Color,
    certificateVault: CertificateVault,
    expiryTracker: ExpiryTracker,
    anisetteClient: AnisetteClient,
    onLogActivity: (actionType: String, description: String, status: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as DroidUtoolsApp

    val entries by certificateVault.entries.collectAsState()
    val trackedApps by expiryTracker.trackedApps.collectAsState()

    var pendingP12Bytes by remember { mutableStateOf<ByteArray?>(null) }
    var p12PasswordInput by remember { mutableStateOf("") }
    var p12DisplayLabel by remember { mutableStateOf("") }
    var p12PasswordVisible by remember { mutableStateOf(false) }
    var vaultFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var confirmingDeleteEntry by remember { mutableStateOf<VaultEntry?>(null) }

    // Apple ID / Anisette state
    var appleIdInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var twoFactorCode by remember { mutableStateOf("") }
    var anisetteUrl by remember { mutableStateOf(anisetteClient.getServerUrl()) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var twoFactorRequired by remember { mutableStateOf(false) }
    var authSuccessMessage by remember { mutableStateOf<String?>(null) }

    val isAppleIdNoticeShown by app.consentManager.isAppleIdNoticeShown.collectAsState()
    val isAnisetteNoticeShown by app.consentManager.isAnisetteNoticeShown.collectAsState()
    var showAppleIdNoticeDialog by remember { mutableStateOf(false) }
    var showAnisetteNoticeDialog by remember { mutableStateOf(false) }

    val p12Picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    pendingP12Bytes = bytes
                    p12PasswordInput = ""
                    p12DisplayLabel = ""
                } else {
                    vaultFeedback = false to "Selected .p12 file could not be read or was empty."
                }
            }
        }
    }

    val provisionPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val res = certificateVault.importMobileProvision(bytes)
                    if (res.isSuccess) {
                        val imported = res.getOrThrow()
                        vaultFeedback = true to "Imported profile '${imported.profileInfo?.name ?: imported.certificate.displayName}'."
                        onLogActivity("VAULT", "Imported .mobileprovision profile", "SUCCESS")
                    } else {
                        vaultFeedback = false to (res.exceptionOrNull()?.message ?: "Invalid .mobileprovision file.")
                    }
                }
            }
        }
    }

    // P12 Password Dialog
    pendingP12Bytes?.let { rawBytes ->
        AlertDialog(
            onDismissRequest = { pendingP12Bytes = null },
            title = { Text("Unlock .p12 Certificate", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the PKCS#12 passphrase to extract your Apple signing identity and seal it inside the AES-256-GCM KeyStore vault.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = p12PasswordInput,
                        onValueChange = { p12PasswordInput = it },
                        label = { Text("P12 Password (leave blank if none)") },
                        visualTransformation = if (p12PasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { p12PasswordVisible = !p12PasswordVisible }) {
                                Icon(
                                    imageVector = if (p12PasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = p12DisplayLabel,
                        onValueChange = { p12DisplayLabel = it },
                        label = { Text("Optional Label Override") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingP12Bytes = null
                        scope.launch {
                            val res = certificateVault.importP12Certificate(
                                p12Bytes = rawBytes,
                                password = p12PasswordInput,
                                displayNameOverride = p12DisplayLabel.ifBlank { null }
                            )
                            if (res.isSuccess) {
                                val entry = res.getOrThrow()
                                vaultFeedback = true to "Sealed '${entry.certificate.displayName}' in hardware vault."
                                onLogActivity("VAULT", "Imported P12 certificate: ${entry.certificate.teamId}", "SUCCESS")
                            } else {
                                vaultFeedback = false to (res.exceptionOrNull()?.message ?: "Failed to unlock .p12 (wrong password?).")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Import & Seal", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingP12Bytes = null }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    confirmingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmingDeleteEntry = null },
            title = { Text("Remove Vault Certificate?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Delete '${entry.certificate.displayName}' and its encrypted key material from DroidUtools?", color = Color(0xFFCBD5E1)) },
            confirmButton = {
                Button(
                    onClick = {
                        val target = entry
                        confirmingDeleteEntry = null
                        scope.launch {
                            certificateVault.deleteEntry(target.id)
                            vaultFeedback = true to "Removed '${target.certificate.displayName}' from vault."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDeleteEntry = null }) {
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
        // Vault Actions Header Card
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = accent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Encrypted Certificate Vault",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "AES-256-GCM KeyStore storage for .p12 identities, .mobileprovision profiles & live OCSP verification",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { p12Picker.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("vault_import_p12_button")
                        ) {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import .P12", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = { provisionPicker.launch(arrayOf("*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("vault_import_profile_button")
                        ) {
                            Icon(Icons.Default.AddModerator, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(".mobileprovision", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    vaultFeedback?.let { (ok, msg) ->
                        Text(
                            text = msg,
                            color = if (ok) GreenPrimary else StatusError,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Stored Vault Entries
        item {
            Text(
                text = "STORED SIGNING IDENTITIES (${entries.size})",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        if (entries.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Vault is empty. Import a .p12 + .mobileprovision pair above, or authenticate a free 7-day Apple ID below.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                VaultEntryCard(
                    entry = entry,
                    activeDeviceUdid = udid,
                    accent = accent,
                    onCheckOcsp = {
                        scope.launch {
                            val res = certificateVault.checkOcspStatus(entry.id)
                            vaultFeedback = (res.status == OcspStatus.GOOD) to "OCSP (${entry.certificate.displayName}): ${res.status.label} — ${res.detail}"
                            onLogActivity("OCSP", "Checked OCSP for ${entry.certificate.displayName}: ${res.status.label}", "INFO")
                        }
                    },
                    onSetActive = {
                        scope.launch { certificateVault.setActiveEntry(entry.id) }
                    },
                    onDelete = { confirmingDeleteEntry = entry }
                )
            }
        }

        // 7-Day Expiry Tracker Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "7-Day Sideload Expiry Tracker",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Free Apple ID apps expire after 7 days",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        OutlinedButton(
                            onClick = { expiryTracker.refreshAllApps() },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Refresh", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    trackedApps.forEach { appItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(appItem.appName, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 12.sp)
                                Text(appItem.bundleId, color = Color(0xFF64748B), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                text = "${appItem.remainingDays}d left",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (appItem.isExpiringSoon) StatusError else GreenPrimary
                            )
                        }
                    }
                }
            }
        }

        // Apple ID & Anisette Signer Account Card (Legal notices preserved)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Apple ID & Anisette Signer Account",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    OutlinedTextField(
                        value = appleIdInput,
                        onValueChange = {
                            if (!isAppleIdNoticeShown && it.isNotBlank()) {
                                showAppleIdNoticeDialog = true
                            }
                            appleIdInput = it
                        },
                        placeholder = { Text("Apple ID Email (e.g. user@icloud.com)", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        placeholder = { Text("Apple ID Password or App-Specific Password", color = Color(0xFF64748B)) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (twoFactorRequired) {
                        OutlinedTextField(
                            value = twoFactorCode,
                            onValueChange = { twoFactorCode = it },
                            placeholder = { Text("Enter 6-digit 2FA Code", color = Color(0xFF64748B)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = anisetteUrl,
                        onValueChange = {
                            if (!isAnisetteNoticeShown && it != anisetteClient.getServerUrl()) {
                                showAnisetteNoticeDialog = true
                            }
                            anisetteUrl = it
                            anisetteClient.setServerUrl(it)
                        },
                        placeholder = { Text("Anisette Server URL", color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    authSuccessMessage?.let { msg ->
                        Text(
                            text = msg,
                            color = if (msg.startsWith("Error")) StatusError else GreenPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isAuthenticating = true
                                if (!twoFactorRequired) {
                                    when (val result = anisetteClient.authenticateAppleId(appleIdInput, passwordInput)) {
                                        is AnisetteAuthResult.TwoFactorRequired -> twoFactorRequired = true
                                        is AnisetteAuthResult.Success -> {
                                            certificateVault.recordAppleIdIdentity(appleIdInput.ifBlank { result.accountName })
                                            authSuccessMessage = "✓ Logged in & armed in Vault as ${result.accountName}"
                                            twoFactorRequired = false
                                        }
                                        is AnisetteAuthResult.Error -> authSuccessMessage = "Error: ${result.message}"
                                    }
                                } else {
                                    when (val result = anisetteClient.submitTwoFactorCode("session", twoFactorCode, appleIdInput)) {
                                        is AnisetteAuthResult.Success -> {
                                            certificateVault.recordAppleIdIdentity(appleIdInput.ifBlank { result.accountName })
                                            authSuccessMessage = "✓ 2FA Verified! Armed in Certificate Vault."
                                            twoFactorRequired = false
                                        }
                                        is AnisetteAuthResult.Error -> authSuccessMessage = "Error: ${result.message}"
                                        else -> {}
                                    }
                                }
                                isAuthenticating = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        if (isAuthenticating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                        } else {
                            Text(if (twoFactorRequired) "Verify 2FA Code" else "Authenticate & Add to Vault", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAppleIdNoticeDialog) {
        AlertDialog(
            onDismissRequest = {
                showAppleIdNoticeDialog = false
                scope.launch { app.consentManager.markAppleIdNoticeShown() }
            },
            title = { Text("Apple ID Notice", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    text = "Credentials are sent directly to Apple (and any configured anisette server), stored encrypted on the device, and never received by the Developer.",
                    color = Color(0xFFCBD5E1),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAppleIdNoticeDialog = false
                        scope.launch { app.consentManager.markAppleIdNoticeShown() }
                    }
                ) {
                    Text("I Understand", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }

    if (showAnisetteNoticeDialog) {
        AlertDialog(
            onDismissRequest = {
                showAnisetteNoticeDialog = false
                scope.launch { app.consentManager.markAnisetteNoticeShown() }
            },
            title = { Text("Third-Party Server Notice", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    text = "This server is run by a third party. Your Apple ID login data passes through it. Only use servers you trust and have read the terms for.",
                    color = Color(0xFFCBD5E1),
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAnisetteNoticeDialog = false
                        scope.launch { app.consentManager.markAnisetteNoticeShown() }
                    }
                ) {
                    Text("I Understand", color = accent, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
private fun VaultEntryCard(
    entry: VaultEntry,
    activeDeviceUdid: String,
    accent: Color,
    onCheckOcsp: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
    val cert = entry.certificate
    val ocspColor = when (entry.ocspStatus) {
        OcspStatus.GOOD -> GreenPrimary
        OcspStatus.REVOKED -> StatusError
        OcspStatus.CHECKING -> StatusTunnel
        OcspStatus.OFFLINE -> StatusWarning
        else -> Color(0xFF94A3B8)
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (entry.isActive) accent else Color(0xFF1E293B)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = cert.displayName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (entry.isActive) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE", color = accent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "${cert.teamName} • Team ${cert.teamId} • ${if (cert.isExpired) "EXPIRED" else "${cert.remainingDays}d remaining"}",
                        color = if (cert.isExpired) StatusError else Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ocspColor.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "OCSP: ${entry.ocspStatus.label}",
                        color = ocspColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (entry.sha1Fingerprint.isNotBlank()) {
                Text(
                    text = "SHA-1: ${entry.sha1Fingerprint}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            entry.profileInfo?.let { prof ->
                val matchesDevice = prof.isDeviceProvisioned(activeDeviceUdid)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Profile: ${prof.name}", color = Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (prof.provisionsAllDevices) "Universal / Distribution" else "${prof.provisionedDevices.size} UDID(s) • ${prof.remainingDays}d left",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = if (matchesDevice) "UDID Match ✓" else "UDID Missing ✗",
                        color = if (matchesDevice) GreenPrimary else StatusWarning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = entry.ocspDetail,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCheckOcsp,
                    enabled = entry.ocspStatus != OcspStatus.CHECKING,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    if (entry.ocspStatus == OcspStatus.CHECKING) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(15.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Check OCSP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (!entry.isActive) {
                    OutlinedButton(
                        onClick = onSetActive,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Set Active", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete certificate", tint = StatusError)
                }
            }
        }
    }
}
