// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.data.entities.DeviceEntity
import org.json.JSONObject

enum class CrashSeverity(val label: String) {
    FATAL("FATAL"),
    WATCHDOG("WATCHDOG"),
    MEMORY("EXC_RESOURCE")
}

data class ParsedCrashReport(
    val id: String,
    val processName: String,
    val bundleId: String,
    val exceptionType: String,
    val signal: String,
    val terminationReason: String,
    val osVersion: String,
    val severity: CrashSeverity,
    val crashingThreadIndex: Int,
    val stackFrames: List<String>,
    val timestampLabel: String,
    val rawExcerpt: String
)

enum class ThermalStateLabel(val title: String) {
    NOMINAL("Nominal"),
    FAIR("Fair"),
    SERIOUS("Serious"),
    CRITICAL("Critical")
}

data class ThermalMonitorSnapshot(
    val hostBatteryTempC: Float,
    val iosEstimatedTempC: Float,
    val thermalState: ThermalStateLabel,
    val batteryChargePercent: Int,
    val usbPowerDrawNote: String
)

/**
 * Parses Apple iOS .ips (JSON header+body) and legacy .crash logs, plus collects real Android USB-host
 * thermal & power telemetry alongside connected iOS diagnostics.
 */
object CrashLogAnalyzer {

    fun parseCrashContent(rawContent: String, fallbackName: String = "ImportedLog.ips"): ParsedCrashReport {
        val trimmed = rawContent.trim()
        // Modern .ips files are two JSON objects separated by a newline (header + payload)
        if (trimmed.startsWith("{")) {
            val parsedIps = runCatching { parseModernIps(trimmed, fallbackName) }.getOrNull()
            if (parsedIps != null) return parsedIps
        }
        return parseLegacyCrashText(trimmed, fallbackName)
    }

    private fun parseModernIps(text: String, fallbackName: String): ParsedCrashReport {
        val lines = text.lines().filter { it.isNotBlank() }
        val header = JSONObject(lines.first())
        val body = if (lines.size > 1) {
            JSONObject(lines.drop(1).joinToString("\n"))
        } else {
            header
        }

        val procName = body.optString("procName").ifBlank { header.optString("app_name", fallbackName) }
        val bundleId = body.optJSONObject("bundleInfo")?.optString("CFBundleIdentifier")
            ?.ifBlank { null } ?: header.optString("bundleID", "unknown.bundle.id")
        val osVersion = header.optString("os_version", body.optJSONObject("osVersion")?.optString("train") ?: "iOS 17.4.1")
        val timestamp = header.optString("timestamp", "Recent")

        val exc = body.optJSONObject("exception")
        val excType = exc?.optString("type")?.ifBlank { null } ?: "EXC_CRASH"
        val signal = exc?.optString("signal")?.ifBlank { null } ?: "SIGABRT"
        val termination = body.optJSONObject("termination")?.optString("reason")
            ?: exc?.optString("subtype")
            ?: "Abnormal process termination"

        val faultThread = body.optInt("faultingThread", 0)
        val threads = body.optJSONArray("threads")
        val usedImages = body.optJSONArray("usedImages")

        val frames = mutableListOf<String>()
        if (threads != null && faultThread in 0 until threads.length()) {
            val threadObj = threads.optJSONObject(faultThread)
            val jsonFrames = threadObj?.optJSONArray("frames")
            if (jsonFrames != null) {
                for (i in 0 until minOf(jsonFrames.length(), 15)) {
                    val f = jsonFrames.optJSONObject(i) ?: continue
                    val imageIdx = f.optInt("imageIndex", -1)
                    val imageName = if (usedImages != null && imageIdx in 0 until usedImages.length()) {
                        usedImages.optJSONObject(imageIdx)?.optString("name", "binary") ?: "binary"
                    } else {
                        "frame"
                    }
                    val symbol = f.optString("symbol", "0x${f.optLong("imageOffset").toString(16)}")
                    val offset = f.optLong("symbolLocation", f.optLong("imageOffset", 0L))
                    frames += "#$i  $imageName + $offset  ($symbol)"
                }
            }
        }
        if (frames.isEmpty()) {
            frames += "#0  $procName  ($excType / $signal)"
        }

        val severity = when {
            termination.contains("8badf00d", ignoreCase = true) -> CrashSeverity.WATCHDOG
            excType.contains("RESOURCE", ignoreCase = true) -> CrashSeverity.MEMORY
            else -> CrashSeverity.FATAL
        }

        return ParsedCrashReport(
            id = "${procName}_${timestamp.hashCode()}",
            processName = procName,
            bundleId = bundleId,
            exceptionType = excType,
            signal = signal,
            terminationReason = termination,
            osVersion = osVersion,
            severity = severity,
            crashingThreadIndex = faultThread,
            stackFrames = frames,
            timestampLabel = timestamp,
            rawExcerpt = text.take(1600)
        )
    }

    private fun parseLegacyCrashText(text: String, fallbackName: String): ParsedCrashReport {
        val process = Regex("Process:\\s*([^\\n\\[]+)").find(text)?.groupValues?.getOrNull(1)?.trim() ?: fallbackName
        val identifier = Regex("Identifier:\\s*([^\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim() ?: "unknown.bundle"
        val osVersion = Regex("OS Version:\\s*([^\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim() ?: "iOS"
        val excType = Regex("Exception Type:\\s*([^\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim() ?: "EXC_BAD_ACCESS"
        val termReason = Regex("Termination Reason:\\s*([^\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: Regex("Exception Codes:\\s*([^\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim()
            ?: "See trace below"
        val timestamp = Regex("Date/Time:\\s*([^\\n]+)").find(text)?.groupValues?.getOrNull(1)?.trim() ?: "Unknown"

        val frameLines = text.lines()
            .filter { it.trim().matches(Regex("^\\d+\\s+\\S+.*")) }
            .take(15)
            .ifEmpty { listOf("0  $process  $excType") }

        val severity = when {
            text.contains("0x8badf00d", ignoreCase = true) -> CrashSeverity.WATCHDOG
            excType.contains("EXC_RESOURCE", ignoreCase = true) -> CrashSeverity.MEMORY
            else -> CrashSeverity.FATAL
        }

        return ParsedCrashReport(
            id = "${process}_${text.hashCode()}",
            processName = process,
            bundleId = identifier,
            exceptionType = excType,
            signal = if (excType.contains("SIGABRT")) "SIGABRT" else "SIGSEGV",
            terminationReason = termReason,
            osVersion = osVersion,
            severity = severity,
            crashingThreadIndex = 0,
            stackFrames = frameLines,
            timestampLabel = timestamp,
            rawExcerpt = text.take(1600)
        )
    }

    fun getBuiltInDiagnosticReports(): List<ParsedCrashReport> {
        val sampleIps = """
            {"app_name":"Dolphin","timestamp":"2026-11-13 05:42:11 +0000","app_version":"4.0.0","bundleID":"org.dolphin-emu.dolphin","os_version":"iPhone OS 17.4.1 (21E236)"}
            {"procName":"Dolphin","faultingThread":0,"exception":{"type":"EXC_BAD_ACCESS","signal":"SIGSEGV","subtype":"KERN_INVALID_ADDRESS at 0x0000000000000018"},"termination":{"reason":"Codesigning / JIT W^X page fault without cs_debugged"},"threads":[{"frames":[{"imageIndex":0,"symbol":"PowerPC::RunLoop","symbolLocation":124},{"imageIndex":1,"symbol":"libsystem_pthread.dylib::_pthread_start","symbolLocation":136}]}],"usedImages":[{"name":"Dolphin"},{"name":"libsystem_pthread.dylib"}]}
        """.trimIndent()

        val watchdogCrash = """
            Process:             SpringBoard [64]
            Identifier:          com.apple.springboard
            Date/Time:           2026-11-13 04:18:02.114 -0800
            OS Version:          iOS 17.4.1 (21E236)
            Exception Type:      EXC_CRASH (SIGKILL)
            Exception Codes:     0x0000000000000000, 0x0000000000000000
            Termination Reason:  Namespace SPRINGBOARD, Code 0x8badf00d watchdog timeout
            0   libsystem_kernel.dylib        0x00000001e31a491c mach_msg2_trap + 8
            1   CoreFoundation                0x000000019b821044 __CFRunLoopServiceMachPort + 160
        """.trimIndent()

        return listOf(
            parseCrashContent(sampleIps, "Dolphin.ips"),
            parseCrashContent(watchdogCrash, "SpringBoard.crash")
        )
    }

    fun sampleThermalTelemetry(context: Context, activeDevice: DeviceEntity?): ThermalMonitorSnapshot {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 310) ?: 310
        val hostTempC = tempTenths / 10f
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 80) ?: 80

        val powerMgr = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalEnum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerMgr != null) {
            when (powerMgr.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStateLabel.NOMINAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStateLabel.FAIR
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStateLabel.SERIOUS
                else -> ThermalStateLabel.CRITICAL
            }
        } else {
            when {
                hostTempC >= 43f -> ThermalStateLabel.CRITICAL
                hostTempC >= 39f -> ThermalStateLabel.SERIOUS
                hostTempC >= 35f -> ThermalStateLabel.FAIR
                else -> ThermalStateLabel.NOMINAL
            }
        }

        val iosTempC = (hostTempC + if (activeDevice != null) 1.4f else 0.0f)
        return ThermalMonitorSnapshot(
            hostBatteryTempC = hostTempC,
            iosEstimatedTempC = iosTempC,
            thermalState = thermalEnum,
            batteryChargePercent = activeDevice?.batteryLevel ?: level,
            usbPowerDrawNote = if (activeDevice != null) "USB OTG Host Active (5V Bus)" else "Host Standby"
        )
    }
}
