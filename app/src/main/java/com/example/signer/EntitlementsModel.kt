// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.signer

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

enum class EntitlementPreset(
    val title: String,
    val subtitle: String,
    val entitlementKey: String,
    val requiresPaidProfile: Boolean = false
) {
    JIT_DEBUGGING(
        title = "JIT / Debuggable (get-task-allow)",
        subtitle = "Enables debugserver & JIT memory unlocking",
        entitlementKey = "get-task-allow",
        requiresPaidProfile = false
    ),
    INCREASED_MEMORY(
        title = "Increased Memory Limit",
        subtitle = "Unlocks higher RAM budget for Dolphin, UTM & Pojav",
        entitlementKey = "com.apple.developer.kernel.increased-memory-limit",
        requiresPaidProfile = true
    ),
    EXTENDED_VIRTUAL_ADDRESSING(
        title = "Extended Virtual Addressing",
        subtitle = "Allows large 64-bit address space mapping",
        entitlementKey = "com.apple.developer.kernel.extended-virtual-addressing",
        requiresPaidProfile = true
    ),
    APP_GROUPS(
        title = "App Groups Shared Container",
        subtitle = "Shares sandbox files across SideStore / LiveContainer",
        entitlementKey = "com.apple.security.application-groups",
        requiresPaidProfile = false
    ),
    KEYCHAIN_SHARING(
        title = "Keychain Access Groups",
        subtitle = "Shares login tokens inside your signing team",
        entitlementKey = "keychain-access-groups",
        requiresPaidProfile = false
    ),
    USB_ACCESSORY(
        title = "MFi / External Accessory",
        subtitle = "Permits external USB/Lightning hardware sessions",
        entitlementKey = "com.apple.developer.external-accessory",
        requiresPaidProfile = true
    ),
    CARPLAY_AUDIO(
        title = "CarPlay Audio Mode",
        subtitle = "Exposes audio controls on Apple CarPlay heads-up UI",
        entitlementKey = "com.apple.developer.carplay-audio",
        requiresPaidProfile = true
    ),
    ASSOCIATED_DOMAINS(
        title = "Associated Domains",
        subtitle = "Enables universal links / applinks associations",
        entitlementKey = "com.apple.developer.associated-domains",
        requiresPaidProfile = false
    )
}

object EntitlementsBuilder {

    fun buildEntitlementsXml(
        bundleId: String,
        teamId: String = "TEAMID0000",
        enabledPresets: Set<EntitlementPreset>,
        customAppGroup: String = "group.com.droidutools.shared"
    ): String {
        val safeTeam = teamId.ifBlank { "TEAMID0000" }
        val safeBundle = bundleId.ifBlank { "com.droidutools.sideloaded" }
        val safeGroup = customAppGroup.ifBlank { "group.$safeBundle" }

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">""")
        sb.appendLine("""<plist version="1.0">""")
        sb.appendLine("""<dict>""")
        sb.appendLine("""    <key>application-identifier</key>""")
        sb.appendLine("""    <string>${escapeXml(safeTeam)}.${escapeXml(safeBundle)}</string>""")
        sb.appendLine("""    <key>com.apple.developer.team-identifier</key>""")
        sb.appendLine("""    <string>${escapeXml(safeTeam)}</string>""")
        sb.appendLine("""    <key>aps-environment</key>""")
        sb.appendLine("""    <string>development</string>""")

        if (EntitlementPreset.JIT_DEBUGGING in enabledPresets) {
            sb.appendLine("""    <key>get-task-allow</key>""")
            sb.appendLine("""    <true/>""")
        }
        if (EntitlementPreset.INCREASED_MEMORY in enabledPresets) {
            sb.appendLine("""    <key>com.apple.developer.kernel.increased-memory-limit</key>""")
            sb.appendLine("""    <true/>""")
        }
        if (EntitlementPreset.EXTENDED_VIRTUAL_ADDRESSING in enabledPresets) {
            sb.appendLine("""    <key>com.apple.developer.kernel.extended-virtual-addressing</key>""")
            sb.appendLine("""    <true/>""")
        }
        if (EntitlementPreset.APP_GROUPS in enabledPresets) {
            sb.appendLine("""    <key>com.apple.security.application-groups</key>""")
            sb.appendLine("""    <array>""")
            sb.appendLine("""        <string>${escapeXml(safeGroup)}</string>""")
            sb.appendLine("""    </array>""")
        }
        if (EntitlementPreset.KEYCHAIN_SHARING in enabledPresets) {
            sb.appendLine("""    <key>keychain-access-groups</key>""")
            sb.appendLine("""    <array>""")
            sb.appendLine("""        <string>${escapeXml(safeTeam)}.${escapeXml(safeBundle)}</string>""")
            sb.appendLine("""    </array>""")
        }
        if (EntitlementPreset.USB_ACCESSORY in enabledPresets) {
            sb.appendLine("""    <key>com.apple.external-accessory.wireless-configuration</key>""")
            sb.appendLine("""    <true/>""")
        }
        if (EntitlementPreset.CARPLAY_AUDIO in enabledPresets) {
            sb.appendLine("""    <key>com.apple.developer.carplay-audio</key>""")
            sb.appendLine("""    <true/>""")
        }
        if (EntitlementPreset.ASSOCIATED_DOMAINS in enabledPresets) {
            sb.appendLine("""    <key>com.apple.developer.associated-domains</key>""")
            sb.appendLine("""    <array>""")
            sb.appendLine("""        <string>applinks:${escapeXml(safeBundle)}</string>""")
            sb.appendLine("""    </array>""")
        }

        sb.appendLine("""</dict>""")
        sb.append("""</plist>""")
        return sb.toString()
    }

    fun detectPresetsFromXml(xml: String): Set<EntitlementPreset> {
        return EntitlementPreset.entries.filter { preset ->
            xml.contains("<key>${preset.entitlementKey}</key>") ||
                (preset == EntitlementPreset.USB_ACCESSORY && xml.contains("external-accessory"))
        }.toSet()
    }

    fun validateEntitlementsXml(xml: String): Result<Int> {
        return try {
            if (!xml.contains("<plist") || !xml.contains("<dict")) {
                return Result.failure(IllegalArgumentException("Entitlements XML must contain <plist> and <dict> tags."))
            }
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))
            var keyCount = 0
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "key") {
                    keyCount++
                }
                event = parser.next()
            }
            Result.success(keyCount)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid XML syntax: ${e.message}"))
        }
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
