// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.installer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class IpaMetadata(
    val bundleId: String,
    val appName: String,
    val version: String,
    val buildNumber: String,
    val minOsVersion: String,
    val appExecutable: String,
    val iconBitmap: Bitmap? = null,
    val hasMobileProvision: Boolean = false,
    val teamName: String? = null,
    val teamId: String? = null,
    val expirationDate: String? = null,
    val injectedDylibs: List<String> = emptyList(),
    val frameworks: List<String> = emptyList(),
    val fileSizeBytes: Long = 0L,
    val fileName: String = ""
)

object IpaPackageInspector {
    private const val TAG = "DroidUtools_IpaInspect"

    suspend fun inspectIpaFile(file: File): Result<IpaMetadata> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.name.endsWith(".ipa", ignoreCase = true)) {
                return@withContext Result.failure(IllegalArgumentException("Invalid IPA file: ${file.path}"))
            }

            var bundleId = "com.unknown.app"
            var appName = file.nameWithoutExtension
            var version = "1.0"
            var buildNumber = "1"
            var minOsVersion = "14.0"
            var appExecutable = "App"
            var iconBitmap: Bitmap? = null
            var hasMobileProvision = false
            var teamName: String? = null
            var teamId: String? = null
            var expirationDate: String? = null
            val frameworks = mutableListOf<String>()
            val injectedDylibs = mutableListOf<String>()

            ZipFile(file).use { zip ->
                val entries = zip.entries()
                var infoPlistBytes: ByteArray? = null

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    // Look for Payload/AppName.app/Info.plist (top level app only)
                    if (name.matches(Regex("Payload/[^/]+\\.app/Info\\.plist"))) {
                        zip.getInputStream(entry).use {
                            infoPlistBytes = it.readBytes()
                        }
                    }

                    // Look for embedded provisioning profile
                    if (name.matches(Regex("Payload/[^/]+\\.app/embedded\\.mobileprovision"))) {
                        hasMobileProvision = true
                        zip.getInputStream(entry).use { stream ->
                            val provText = String(stream.readBytes(), Charsets.ISO_8859_1)
                            teamName = extractXmlString(provText, "TeamName")
                            teamId = extractXmlString(provText, "TeamIdentifier")
                            expirationDate = extractXmlString(provText, "ExpirationDate")
                        }
                    }

                    // Look for app icon
                    if (iconBitmap == null && name.contains("AppIcon", ignoreCase = true) && name.endsWith(".png", ignoreCase = true)) {
                        try {
                            zip.getInputStream(entry).use { stream ->
                                iconBitmap = BitmapFactory.decodeStream(stream)
                            }
                        } catch (e: Exception) {
                            // Icon decode failure, ignore
                        }
                    }

                    // Frameworks & Dylibs
                    if (name.matches(Regex("Payload/[^/]+\\.app/Frameworks/[^/]+\\.framework.*"))) {
                        val fwName = name.substringAfter("Frameworks/").substringBefore("/")
                        if (!frameworks.contains(fwName)) frameworks.add(fwName)
                    } else if (name.matches(Regex("Payload/[^/]+\\.app/Frameworks/[^/]+\\.dylib"))) {
                        val dylibName = name.substringAfterLast("/")
                        if (!injectedDylibs.contains(dylibName)) injectedDylibs.add(dylibName)
                    }
                }

                // Parse Info.plist XML or text
                infoPlistBytes?.let { bytes ->
                    val content = String(bytes, Charsets.UTF_8)
                    bundleId = extractPlistString(content, "CFBundleIdentifier") ?: bundleId
                    appName = extractPlistString(content, "CFBundleDisplayName")
                        ?: extractPlistString(content, "CFBundleName")
                        ?: appName
                    version = extractPlistString(content, "CFBundleShortVersionString") ?: version
                    buildNumber = extractPlistString(content, "CFBundleVersion") ?: buildNumber
                    minOsVersion = extractPlistString(content, "MinimumOSVersion") ?: minOsVersion
                    appExecutable = extractPlistString(content, "CFBundleExecutable") ?: appExecutable
                }
            }

            Result.success(
                IpaMetadata(
                    bundleId = bundleId,
                    appName = appName,
                    version = version,
                    buildNumber = buildNumber,
                    minOsVersion = minOsVersion,
                    appExecutable = appExecutable,
                    iconBitmap = iconBitmap,
                    hasMobileProvision = hasMobileProvision,
                    teamName = teamName,
                    teamId = teamId,
                    expirationDate = expirationDate,
                    injectedDylibs = injectedDylibs,
                    frameworks = frameworks,
                    fileSizeBytes = file.length(),
                    fileName = file.name
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inspect IPA", e)
            Result.failure(e)
        }
    }

    private fun extractPlistString(content: String, key: String): String? {
        val regex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<string>([^<]+)</string>")
        return regex.find(content)?.groupValues?.get(1)?.trim()
    }

    private fun extractXmlString(content: String, key: String): String? {
        val regex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>[^<]*<string>([^<]+)</string>")
        return regex.find(content)?.groupValues?.get(1)?.trim()
    }
}
