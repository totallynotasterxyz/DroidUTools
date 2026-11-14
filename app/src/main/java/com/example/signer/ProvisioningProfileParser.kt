// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.signer

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class ProvisioningProfileInfo(
    val uuid: String,
    val name: String,
    val teamName: String,
    val teamIdentifier: String,
    val appIdName: String,
    val applicationIdentifierPrefix: String,
    val creationDateEpoch: Long,
    val expirationDateEpoch: Long,
    val provisionedDevices: List<String>,
    val provisionsAllDevices: Boolean,
    val entitlementsXml: String,
    val rawXmlPlist: String
) {
    fun isDeviceProvisioned(udid: String): Boolean {
        if (provisionsAllDevices) return true
        if (udid.isBlank()) return false
        return provisionedDevices.any { it.equals(udid, ignoreCase = true) }
    }

    val isExpired: Boolean
        get() = expirationDateEpoch > 0L && System.currentTimeMillis() >= expirationDateEpoch

    val remainingDays: Long
        get() = if (expirationDateEpoch <= 0L) 0L else {
            ((expirationDateEpoch - System.currentTimeMillis()) / (24L * 60 * 60 * 1000)).coerceAtLeast(0L)
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("uuid", uuid)
        put("name", name)
        put("teamName", teamName)
        put("teamIdentifier", teamIdentifier)
        put("appIdName", appIdName)
        put("applicationIdentifierPrefix", applicationIdentifierPrefix)
        put("creationDateEpoch", creationDateEpoch)
        put("expirationDateEpoch", expirationDateEpoch)
        put("provisionsAllDevices", provisionsAllDevices)
        put("entitlementsXml", entitlementsXml)
        put("rawXmlPlist", rawXmlPlist)
        put("provisionedDevices", JSONArray(provisionedDevices))
    }

    companion object {
        fun fromJson(obj: JSONObject): ProvisioningProfileInfo {
            val devs = mutableListOf<String>()
            val arr = obj.optJSONArray("provisionedDevices")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    devs += arr.optString(i)
                }
            }
            return ProvisioningProfileInfo(
                uuid = obj.optString("uuid"),
                name = obj.optString("name"),
                teamName = obj.optString("teamName"),
                teamIdentifier = obj.optString("teamIdentifier"),
                appIdName = obj.optString("appIdName"),
                applicationIdentifierPrefix = obj.optString("applicationIdentifierPrefix"),
                creationDateEpoch = obj.optLong("creationDateEpoch"),
                expirationDateEpoch = obj.optLong("expirationDateEpoch"),
                provisionedDevices = devs,
                provisionsAllDevices = obj.optBoolean("provisionsAllDevices"),
                entitlementsXml = obj.optString("entitlementsXml"),
                rawXmlPlist = obj.optString("rawXmlPlist")
            )
        }
    }
}

/**
 * Extracts and parses Apple .mobileprovision CMS/PKCS#7 envelopes and raw XML plists.
 */
object ProvisioningProfileParser {

    fun parse(rawBytes: ByteArray): Result<ProvisioningProfileInfo> {
        return try {
            val content = String(rawBytes, Charsets.UTF_8)
            val startIdx = content.indexOf("<?xml")
                .takeIf { it >= 0 } ?: content.indexOf("<plist")
            val endTag = "</plist>"
            val endIdx = content.indexOf(endTag)
            if (startIdx < 0 || endIdx < 0 || endIdx <= startIdx) {
                return Result.failure(IllegalArgumentException("No valid XML plist payload found inside provisioning profile."))
            }

            val plistXml = content.substring(startIdx, endIdx + endTag.length)

            val uuid = extractStringValue(plistXml, "UUID") ?: "unknown-uuid"
            val name = extractStringValue(plistXml, "Name") ?: "Imported Profile"
            val teamName = extractStringValue(plistXml, "TeamName") ?: "Unknown Team"
            val appIdName = extractStringValue(plistXml, "AppIDName") ?: name
            val teamId = extractArrayFirstString(plistXml, "TeamIdentifier") ?: ""
            val appIdPrefix = extractArrayFirstString(plistXml, "ApplicationIdentifierPrefix") ?: teamId

            val createdEpoch = extractDateEpoch(plistXml, "CreationDate")
            val expiryEpoch = extractDateEpoch(plistXml, "ExpirationDate")

            val devices = extractArrayStrings(plistXml, "ProvisionedDevices")
            val allDevices = Regex("<key>\\s*ProvisionsAllDevices\\s*</key>\\s*<true\\s*/>")
                .containsMatchIn(plistXml)

            val entitlementsDict = extractDictBlock(plistXml, "Entitlements")
            val entitlementsXml = if (entitlementsDict != null) {
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                $entitlementsDict
                </plist>
                """.trimIndent()
            } else {
                ""
            }

            Result.success(
                ProvisioningProfileInfo(
                    uuid = uuid,
                    name = name,
                    teamName = teamName,
                    teamIdentifier = teamId,
                    appIdName = appIdName,
                    applicationIdentifierPrefix = appIdPrefix,
                    creationDateEpoch = createdEpoch,
                    expirationDateEpoch = expiryEpoch,
                    provisionedDevices = devices,
                    provisionsAllDevices = allDevices,
                    entitlementsXml = entitlementsXml,
                    rawXmlPlist = plistXml
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractStringValue(xml: String, key: String): String? {
        val regex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<string>([^<]*)</string>")
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractArrayFirstString(xml: String, key: String): String? {
        return extractArrayStrings(xml, key).firstOrNull()
    }

    private fun extractArrayStrings(xml: String, key: String): List<String> {
        val arrayRegex = Regex(
            "<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<array>([\\s\\S]*?)</array>"
        )
        val body = arrayRegex.find(xml)?.groupValues?.getOrNull(1) ?: return emptyList()
        return Regex("<string>([^<]+)</string>")
            .findAll(body)
            .map { it.groupValues[1].trim() }
            .toList()
    }

    private fun extractDateEpoch(xml: String, key: String): Long {
        val regex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<date>([^<]+)</date>")
        val iso = regex.find(xml)?.groupValues?.getOrNull(1)?.trim() ?: return 0L
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            fmt.parse(iso)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractDictBlock(xml: String, key: String): String? {
        val keyRegex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<dict>")
        val match = keyRegex.find(xml) ?: return null
        val dictStart = xml.indexOf("<dict>", match.range.first)
        if (dictStart < 0) return null

        var depth = 0
        var idx = dictStart
        while (idx < xml.length) {
            if (xml.startsWith("<dict>", idx)) {
                depth++
                idx += 6
            } else if (xml.startsWith("</dict>", idx)) {
                depth--
                idx += 7
                if (depth == 0) {
                    return xml.substring(dictStart, idx)
                }
            } else {
                idx++
            }
        }
        return null
    }
}
