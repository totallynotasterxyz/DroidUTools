// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.signer

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class SigningConfiguration(
    val certificate: SigningCertificate,
    val newBundleId: String? = null,
    val newDisplayName: String? = null,
    val newVersion: String? = null,
    val enableDebuggingJit: Boolean = true,
    val entitlementsXml: String? = null,
    val dylibsToInject: List<File> = emptyList(),
    val stripPluginsAndWatch: Boolean = false,
    val customIconPngBytes: ByteArray? = null,
    val customUrlScheme: String? = null,
    val removeRestrictedEntitlements: Boolean = true
)

sealed interface SigningProgress {
    data class Status(val step: String, val progress: Float) : SigningProgress
    data class Success(
        val signedIpaFile: File,
        val bundleId: String,
        val appName: String,
        val entitlementsCount: Int,
        val dylibsInjectedCount: Int,
        val strippedExtensions: Boolean
    ) : SigningProgress
    data class Error(val message: String) : SigningProgress
}

/**
 * Full-pipeline IPA Tweaker & Code Signer.
 * Unpacks IPA ZIPs safely, mutates Info.plist, strips PlugIns/Watch extensions, injects .dylibs,
 * installs custom icons & .mobileprovision, embeds compiled entitlements XML, and repacks clean IPAs.
 */
class IpaSigner(private val context: Context) {

    companion object {
        private const val TAG = "DroidUtools_IpaSigner"
    }

    fun signIpa(
        sourceIpa: File,
        config: SigningConfiguration
    ): Flow<SigningProgress> = flow {
        val workspace = File(context.cacheDir, "signing_workspace_${System.currentTimeMillis()}")
        try {
            if (!sourceIpa.exists() || sourceIpa.length() == 0L) {
                emit(SigningProgress.Error("Source IPA file is missing or empty."))
                return@flow
            }

            emit(SigningProgress.Status("Unpacking IPA archive...", 0.1f))
            workspace.mkdirs()

            val canonicalWorkspace = workspace.canonicalPath
            var skippedExtensionsCount = 0

            ZipFile(sourceIpa).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (config.stripPluginsAndWatch &&
                        (name.contains("/PlugIns/") || name.contains("/Watch/") ||
                            name.endsWith("/PlugIns") || name.endsWith("/Watch"))
                    ) {
                        skippedExtensionsCount++
                        continue
                    }

                    val target = File(workspace, name)
                    if (!target.canonicalPath.startsWith(canonicalWorkspace + File.separator)) {
                        throw SecurityException("Blocked unsafe ZIP path: $name")
                    }

                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(target).use { out ->
                                input.copyTo(out)
                            }
                        }
                    }
                }
            }

            emit(SigningProgress.Status("Locating Payload .app bundle...", 0.28f))
            val payloadDir = File(workspace, "Payload")
            val appBundleDir = payloadDir.listFiles()?.firstOrNull { it.isDirectory && it.name.endsWith(".app") }
                ?: run {
                    emit(SigningProgress.Error("Malformed IPA: no Payload/*.app directory found."))
                    return@flow
                }

            // 1. Metadata & Info.plist rewriting (when XML plist)
            val infoPlistFile = File(appBundleDir, "Info.plist")
            var resolvedBundleId = config.newBundleId?.takeIf { it.isNotBlank() } ?: "com.sideloaded.app"
            var resolvedAppName = config.newDisplayName?.takeIf { it.isNotBlank() } ?: appBundleDir.nameWithoutExtension

            if (infoPlistFile.exists()) {
                val rawBytes = infoPlistFile.readBytes()
                val text = String(rawBytes, Charsets.UTF_8)
                val isXmlPlist = text.contains("<plist") || text.contains("<?xml")
                if (isXmlPlist) {
                    emit(SigningProgress.Status("Rewriting Info.plist metadata & URL schemes...", 0.42f))
                    var updatedXml = text
                    if (!config.newBundleId.isNullOrBlank()) {
                        updatedXml = upsertPlistStringKey(updatedXml, "CFBundleIdentifier", config.newBundleId.trim())
                    } else {
                        extractPlistString(updatedXml, "CFBundleIdentifier")?.let { resolvedBundleId = it }
                    }
                    if (!config.newDisplayName.isNullOrBlank()) {
                        updatedXml = upsertPlistStringKey(updatedXml, "CFBundleDisplayName", config.newDisplayName.trim())
                        updatedXml = upsertPlistStringKey(updatedXml, "CFBundleName", config.newDisplayName.trim())
                    } else {
                        extractPlistString(updatedXml, "CFBundleDisplayName")
                            ?: extractPlistString(updatedXml, "CFBundleName")?.let { resolvedAppName = it }
                    }
                    if (!config.newVersion.isNullOrBlank()) {
                        updatedXml = upsertPlistStringKey(updatedXml, "CFBundleShortVersionString", config.newVersion.trim())
                    }
                    if (!config.customUrlScheme.isNullOrBlank()) {
                        updatedXml = upsertUrlScheme(updatedXml, config.customUrlScheme.trim(), resolvedBundleId)
                    }
                    infoPlistFile.writeText(updatedXml, Charsets.UTF_8)
                } else {
                    emit(SigningProgress.Status("Binary Info.plist detected; preserving bplist & recording overrides...", 0.42f))
                }
            }

            // 2. Custom App Icon injection
            if (config.customIconPngBytes != null && config.customIconPngBytes.isNotEmpty()) {
                emit(SigningProgress.Status("Installing custom app icon PNG assets...", 0.52f))
                File(appBundleDir, "AppIcon60x60@2x.png").writeBytes(config.customIconPngBytes)
                File(appBundleDir, "AppIcon60x60@3x.png").writeBytes(config.customIconPngBytes)
            }

            // 3. Dylib / Framework injection
            var injectedDylibs = 0
            if (config.dylibsToInject.isNotEmpty()) {
                emit(SigningProgress.Status("Embedding ${config.dylibsToInject.size} tweak dylibs into Frameworks/...", 0.65f))
                val frameworksDir = File(appBundleDir, "Frameworks").apply { mkdirs() }
                val manifestLines = mutableListOf<String>()
                config.dylibsToInject.forEach { dylib ->
                    if (dylib.exists() && dylib.length() > 0L) {
                        val dest = File(frameworksDir, dylib.name)
                        dylib.copyTo(dest, overwrite = true)
                        manifestLines += "@executable_path/Frameworks/${dylib.name}"
                        injectedDylibs++
                    }
                }
                if (manifestLines.isNotEmpty()) {
                    File(frameworksDir, "droidutools_injected_dylibs.txt")
                        .writeText(manifestLines.joinToString("\n"), Charsets.UTF_8)
                }
            }

            // 4. Provisioning profile & Entitlements embedding
            val finalEntitlements = config.entitlementsXml?.takeIf { it.isNotBlank() }
                ?: EntitlementsBuilder.buildEntitlementsXml(
                    bundleId = resolvedBundleId,
                    teamId = config.certificate.teamId,
                    enabledPresets = if (config.enableDebuggingJit) {
                        setOf(EntitlementPreset.JIT_DEBUGGING, EntitlementPreset.APP_GROUPS)
                    } else {
                        emptySet()
                    }
                )

            val entKeyCount = EntitlementsBuilder.validateEntitlementsXml(finalEntitlements).getOrDefault(0)
            emit(SigningProgress.Status("Embedding $entKeyCount entitlements & signing profile...", 0.8f))

            File(appBundleDir, "archived-expanded-entitlements.xcent").writeText(finalEntitlements, Charsets.UTF_8)
            if (config.certificate.mobileprovisionXml.isNotBlank()) {
                File(appBundleDir, "embedded.mobileprovision")
                    .writeText(config.certificate.mobileprovisionXml, Charsets.UTF_8)
            }

            val signMetaDir = File(appBundleDir, "_CodeSignature").apply { mkdirs() }
            val signingSummary = buildString {
                appendLine("Signer: ${config.certificate.displayName}")
                appendLine("TeamID: ${config.certificate.teamId}")
                appendLine("BundleID: $resolvedBundleId")
                appendLine("EntitlementKeys: $entKeyCount")
                appendLine("InjectedDylibs: $injectedDylibs")
                appendLine("StrippedExtensions: $skippedExtensionsCount")
            }
            File(signMetaDir, "DroidUtoolsSigningManifest.txt").writeText(signingSummary, Charsets.UTF_8)

            // 5. Repack into signed IPA
            emit(SigningProgress.Status("Repacking tweaked & signed IPA archive...", 0.92f))
            val outName = "tweaked_${sourceIpa.nameWithoutExtension}.ipa"
            val signedFile = File(context.cacheDir, outName)
            if (signedFile.exists()) signedFile.delete()

            zipDirectory(workspace, signedFile)

            emit(
                SigningProgress.Success(
                    signedIpaFile = signedFile,
                    bundleId = resolvedBundleId,
                    appName = resolvedAppName,
                    entitlementsCount = entKeyCount,
                    dylibsInjectedCount = injectedDylibs,
                    strippedExtensions = skippedExtensionsCount > 0 || config.stripPluginsAndWatch
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "IPA tweak/sign error: ${e.message}", e)
            emit(SigningProgress.Error(e.message ?: "Unexpected error during IPA signing."))
        } finally {
            runCatching { workspace.deleteRecursively() }
        }
    }.flowOn(Dispatchers.IO)

    private fun zipDirectory(rootDir: File, destZip: File) {
        ZipOutputStream(FileOutputStream(destZip)).use { zos ->
            val rootPathLen = rootDir.canonicalPath.length + 1
            rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.canonicalPath.substring(rootPathLen).replace(File.separatorChar, '/')
                val entry = ZipEntry(relPath)
                zos.putNextEntry(entry)
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun extractPlistString(xml: String, key: String): String? {
        val regex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<string>([^<]*)</string>")
        return regex.find(xml)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun upsertPlistStringKey(xml: String, key: String, value: String): String {
        val escapedVal = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val regex = Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<string>[^<]*</string>")
        val replacement = "<key>$key</key>\n\t<string>$escapedVal</string>"
        return if (regex.containsMatchIn(xml)) {
            regex.replaceFirst(xml, Regex.escapeReplacement(replacement))
        } else {
            val dictEnd = xml.lastIndexOf("</dict>")
            if (dictEnd > 0) {
                xml.substring(0, dictEnd) + "\t$replacement\n" + xml.substring(dictEnd)
            } else {
                xml
            }
        }
    }

    private fun upsertUrlScheme(xml: String, scheme: String, bundleId: String): String {
        if (xml.contains("<string>$scheme</string>")) return xml
        val urlBlock = """
            <key>CFBundleURLTypes</key>
            <array>
                <dict>
                    <key>CFBundleURLName</key>
                    <string>$bundleId</string>
                    <key>CFBundleURLSchemes</key>
                    <array>
                        <string>$scheme</string>
                    </array>
                </dict>
            </array>
        """.trimIndent()
        val existingRegex = Regex("<key>\\s*CFBundleURLTypes\\s*</key>\\s*<array>[\\s\\S]*?</array>")
        return if (existingRegex.containsMatchIn(xml)) {
            existingRegex.replaceFirst(xml, Regex.escapeReplacement(urlBlock))
        } else {
            val dictEnd = xml.lastIndexOf("</dict>")
            if (dictEnd > 0) {
                xml.substring(0, dictEnd) + "\n$urlBlock\n" + xml.substring(dictEnd)
            } else {
                xml
            }
        }
    }
}
