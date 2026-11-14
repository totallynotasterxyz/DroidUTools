// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entities.DeviceEntity
import com.example.tools.CrashLogAnalyzer
import com.example.tools.CrashSeverity
import com.example.tools.ParsedCrashReport
import com.example.tools.ThermalStateLabel
import com.example.ui.components.GlassCard
import com.example.ui.components.GlowGlassCard
import com.example.ui.theme.GreenPrimary
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusTunnel
import com.example.ui.theme.StatusWarning

/**
 * Crash Log Analyzer (.ips JSON & .crash text backtraces) combined with a live USB Host
 * & iOS Thermal Pressure Telemetry Monitor.
 */
@Composable
fun CrashThermalSubScreen(
    activeDevice: DeviceEntity?,
    accent: Color,
    onLogActivity: (actionType: String, description: String, status: String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var thermal by remember(activeDevice) {
        mutableStateOf(CrashLogAnalyzer.sampleThermalTelemetry(context, activeDevice))
    }
    var reports by remember { mutableStateOf(CrashLogAnalyzer.getBuiltInDiagnosticReports()) }
    var selectedSeverity by remember { mutableStateOf<CrashSeverity?>(null) }
    var expandedReportId by remember { mutableStateOf<String?>(null) }

    val crashFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            if (!content.isNullOrBlank()) {
                val parsed = CrashLogAnalyzer.parseCrashContent(content, "Imported.ips")
                reports = listOf(parsed) + reports
                expandedReportId = parsed.id
                onLogActivity("CRASH", "Parsed crash log for ${parsed.processName} (${parsed.exceptionType})", "INFO")
            }
        }
    }

    val thermalColor = when (thermal.thermalState) {
        ThermalStateLabel.NOMINAL -> GreenPrimary
        ThermalStateLabel.FAIR -> accent
        ThermalStateLabel.SERIOUS -> StatusWarning
        ThermalStateLabel.CRITICAL -> StatusError
    }

    val filteredReports = reports.filter { selectedSeverity == null || it.severity == selectedSeverity }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thermal Telemetry Card
        item {
            GlowGlassCard(
                modifier = Modifier.fillMaxWidth(),
                glowColor = thermalColor
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Thermostat, contentDescription = null, tint = thermalColor, modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    text = "Live Thermal & USB Power Monitor",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = thermal.usbPowerDrawNote,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = { thermal = CrashLogAnalyzer.sampleThermalTelemetry(context, activeDevice) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh thermals", tint = accent)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Text("HOST TEMP", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", thermal.hostBatteryTempC)} °C", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Text("iOS EST. SOC", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("${String.format("%.1f", thermal.iosEstimatedTempC)} °C", color = thermalColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .padding(10.dp)
                        ) {
                            Text("PRESSURE", color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(thermal.thermalState.title, color = thermalColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    LinearProgressIndicator(
                        progress = { ((thermal.iosEstimatedTempC - 20f) / 30f).coerceIn(0.05f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = thermalColor,
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }
        }

        // Crash Analyzer Header + Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Text(
                        text = "iOS CRASH LOG ANALYZER (.IPS / .CRASH)",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { crashFilePicker.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("import_crash_log_button")
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import .ips", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    null to "All (${reports.size})",
                    CrashSeverity.FATAL to "FATAL",
                    CrashSeverity.WATCHDOG to "WATCHDOG",
                    CrashSeverity.MEMORY to "MEMORY"
                ).forEach { (sev, label) ->
                    val isSelected = selectedSeverity == sev
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) accent else Color(0xFF1E293B))
                            .clickable { selectedSeverity = sev }
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
        }

        items(filteredReports, key = { it.id }) { report ->
            val isExpanded = expandedReportId == report.id
            CrashReportCard(
                report = report,
                expanded = isExpanded,
                accent = accent,
                onToggleExpand = {
                    expandedReportId = if (isExpanded) null else report.id
                },
                onCopy = {
                    clipboard.setText(AnnotatedString(formatReportSummary(report)))
                },
                onShare = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, formatReportSummary(report))
                    }
                    context.startActivity(Intent.createChooser(share, "Share iOS Crash Trace"))
                }
            )
        }
    }
}

@Composable
private fun CrashReportCard(
    report: ParsedCrashReport,
    expanded: Boolean,
    accent: Color,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    val sevColor = when (report.severity) {
        CrashSeverity.FATAL -> StatusError
        CrashSeverity.WATCHDOG -> StatusWarning
        CrashSeverity.MEMORY -> StatusTunnel
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        borderColor = sevColor.copy(alpha = 0.45f)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(report.processName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${report.bundleId} • ${report.osVersion}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(sevColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(report.severity.label, color = sevColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "${report.exceptionType} (${report.signal}) — ${report.terminationReason}",
                color = Color(0xFFE2E8F0),
                fontSize = 12.sp
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF02040A))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "FAULTING THREAD #${report.crashingThreadIndex} BACKTRACE",
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        report.stackFrames.forEach { frame ->
                            Text(
                                text = frame,
                                color = Color(0xFFCBD5E1),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCopy,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Trace", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onShare,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Report", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatReportSummary(r: ParsedCrashReport): String = buildString {
    appendLine("Process: ${r.processName} (${r.bundleId})")
    appendLine("OS: ${r.osVersion} | Timestamp: ${r.timestampLabel}")
    appendLine("Exception: ${r.exceptionType} (${r.signal})")
    appendLine("Termination: ${r.terminationReason}")
    appendLine("--- Faulting Thread #${r.crashingThreadIndex} ---")
    r.stackFrames.forEach { appendLine(it) }
}
