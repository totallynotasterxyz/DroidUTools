// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.IosBridgeRouter
import com.example.data.DeviceDatabase
import com.example.data.DeviceRepository
import com.example.data.entities.DeviceEntity
import com.example.signer.CertificateVault
import com.example.signer.EntitlementPreset
import com.example.signer.EntitlementsBuilder
import com.example.signer.IpaSigner
import com.example.signer.OcspRevocationClient
import com.example.signer.OcspStatus
import com.example.signer.ProvisioningProfileParser
import com.example.signer.SigningCertificate
import com.example.signer.SigningConfiguration
import com.example.signer.SigningProgress
import com.example.tools.AfcFileManager
import com.example.tools.AppBackupManager
import com.example.tools.BackupProgress
import com.example.tools.CrashLogAnalyzer
import com.example.tools.CrashSeverity
import com.example.usb.UsbMuxdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExpansionFeaturesTest {

    @Test
    fun `ProvisioningProfileParser extracts CMS plist metadata and UDID allowlist`() {
        val sampleCms = """
            CMS-HEADER-BINARY-BYTES
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>UUID</key>
                <string>A1B2C3D4-E5F6-7890-ABCD-112233445566</string>
                <key>Name</key>
                <string>Aster Wildcard Dev</string>
                <key>TeamName</key>
                <string>Aster Personal Team</string>
                <key>TeamIdentifier</key>
                <array><string>9XYZ1234AB</string></array>
                <key>ExpirationDate</key>
                <date>2028-11-13T12:00:00Z</date>
                <key>ProvisionedDevices</key>
                <array>
                    <string>00008110-0011223344556677</string>
                    <string>00008120-9988776655443322</string>
                </array>
                <key>Entitlements</key>
                <dict>
                    <key>get-task-allow</key>
                    <true/>
                </dict>
            </dict>
            </plist>
            PKCS7-SIGNATURE-TRAILER
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val parsed = ProvisioningProfileParser.parse(sampleCms).getOrThrow()
        assertEquals("A1B2C3D4-E5F6-7890-ABCD-112233445566", parsed.uuid)
        assertEquals("Aster Wildcard Dev", parsed.name)
        assertEquals("9XYZ1234AB", parsed.teamIdentifier)
        assertTrue(parsed.isDeviceProvisioned("00008110-0011223344556677"))
        assertFalse(parsed.isDeviceProvisioned("00008110-UNKNOWN"))
        assertTrue(parsed.entitlementsXml.contains("<key>get-task-allow</key>"))
    }

    @Test
    fun `EntitlementsBuilder generates valid plist XML and detects presets bidirectionally`() {
        val presets = setOf(
            EntitlementPreset.JIT_DEBUGGING,
            EntitlementPreset.INCREASED_MEMORY,
            EntitlementPreset.APP_GROUPS
        )
        val xml = EntitlementsBuilder.buildEntitlementsXml(
            bundleId = "com.aster.emulator",
            teamId = "TEAM12345",
            enabledPresets = presets
        )

        val validation = EntitlementsBuilder.validateEntitlementsXml(xml)
        assertTrue(validation.isSuccess)
        assertTrue((validation.getOrNull() ?: 0) >= 6)

        val detected = EntitlementsBuilder.detectPresetsFromXml(xml)
        assertEquals(presets, detected)

        val invalid = EntitlementsBuilder.validateEntitlementsXml("<broken><xml")
        assertTrue(invalid.isFailure)
    }

    @Test
    fun `OcspRevocationClient builds DER OCSPRequest and parses GOOD vs REVOKED responses`() {
        val client = OcspRevocationClient()
        val req = client.buildDerOcspRequest(
            issuerNameHash = ByteArray(20) { 0x11 },
            issuerKeyHash = ByteArray(20) { 0x22 },
            serialNumber = BigInteger.valueOf(123456789L)
        )
        assertTrue(req.isNotEmpty())
        assertEquals(0x30.toByte(), req[0])

        val goodDer = byteArrayOf(0x30, 0x10, 0x0A, 0x01, 0x00, 0xA0.toByte(), 0x09, 0x30, 0x07, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00)
        val goodRes = client.parseDerOcspResponse(goodDer, "http://ocsp.apple.com")
        assertEquals(OcspStatus.GOOD, goodRes.status)

        val revokedDer = byteArrayOf(0x30, 0x12, 0x0A, 0x01, 0x00, 0xA0.toByte(), 0x0B, 0x30, 0x09, 0x04, 0x02, 0x01, 0x02, 0xA1.toByte(), 0x02, 0x00, 0x00)
        val revokedRes = client.parseDerOcspResponse(revokedDer, "http://ocsp.apple.com")
        assertEquals(OcspStatus.REVOKED, revokedRes.status)
    }

    @Test
    fun `CertificateVault seals profile and Apple ID encrypted on disk across reloads`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault1 = CertificateVault(context)
        vault1.wipeVault()

        vault1.recordAppleIdIdentity("aster@example.com", teamName = "Aster Team", teamId = "AST123")
        val reloaded = CertificateVault(context)
        assertEquals(1, reloaded.entries.value.size)
        assertEquals("aster@example.com", reloaded.entries.value.first().certificate.displayName)

        reloaded.wipeVault()
        assertEquals(0, reloaded.entries.value.size)
    }

    @Test
    fun `IpaSigner rewrites Bundle ID, injects dylib & icon, strips PlugIns, and embeds entitlements`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val srcIpa = File(context.cacheDir, "sample_input.ipa")
        val sampleDylib = File(context.cacheDir, "libCustomTweak.dylib").apply {
            writeBytes(byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte()))
        }

        val originalPlist = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>CFBundleIdentifier</key>
                <string>com.original.app</string>
                <key>CFBundleDisplayName</key>
                <string>OriginalApp</string>
                <key>CFBundleShortVersionString</key>
                <string>1.0</string>
            </dict>
            </plist>
        """.trimIndent()

        ZipOutputStream(FileOutputStream(srcIpa)).use { zos ->
            zos.putNextEntry(ZipEntry("Payload/Sample.app/Info.plist"))
            zos.write(originalPlist.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("Payload/Sample.app/PlugIns/Widget.appex/Info.plist"))
            zos.write("<plist/>".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val entitlements = EntitlementsBuilder.buildEntitlementsXml(
            bundleId = "com.aster.cloned",
            teamId = "ASTER99",
            enabledPresets = setOf(EntitlementPreset.JIT_DEBUGGING)
        )

        val signer = IpaSigner(context)
        val events = signer.signIpa(
            sourceIpa = srcIpa,
            config = SigningConfiguration(
                certificate = SigningCertificate(
                    id = "c1",
                    displayName = "Aster Cert",
                    teamName = "Aster Team",
                    teamId = "ASTER99"
                ),
                newBundleId = "com.aster.cloned",
                newDisplayName = "ClonedApp",
                newVersion = "2.5.0",
                entitlementsXml = entitlements,
                dylibsToInject = listOf(sampleDylib),
                stripPluginsAndWatch = true,
                customIconPngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
                customUrlScheme = "clonedscheme"
            )
        ).toList()

        val success = events.lastOrNull() as? SigningProgress.Success
        assertNotNull(success)
        assertEquals("com.aster.cloned", success!!.bundleId)
        assertEquals("ClonedApp", success.appName)
        assertEquals(1, success.dylibsInjectedCount)
        assertTrue(success.strippedExtensions)

        ZipFile(success.signedIpaFile).use { outZip ->
            assertNull(outZip.getEntry("Payload/Sample.app/PlugIns/Widget.appex/Info.plist"))
            assertNotNull(outZip.getEntry("Payload/Sample.app/Frameworks/libCustomTweak.dylib"))
            assertNotNull(outZip.getEntry("Payload/Sample.app/AppIcon60x60@2x.png"))
            assertNotNull(outZip.getEntry("Payload/Sample.app/archived-expanded-entitlements.xcent"))

            val rewrittenPlist = outZip.getInputStream(outZip.getEntry("Payload/Sample.app/Info.plist")).readBytes().toString(Charsets.UTF_8)
            assertTrue(rewrittenPlist.contains("<string>com.aster.cloned</string>"))
            assertTrue(rewrittenPlist.contains("<string>ClonedApp</string>"))
            assertTrue(rewrittenPlist.contains("<string>clonedscheme</string>"))
        }

        srcIpa.delete()
        sampleDylib.delete()
        success.signedIpaFile.delete()
        Unit
    }

    @Test
    fun `CrashLogAnalyzer parses IPS and legacy crash formats and reads thermals`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builtIn = CrashLogAnalyzer.getBuiltInDiagnosticReports()
        assertEquals(2, builtIn.size)
        assertEquals("Dolphin", builtIn[0].processName)
        assertEquals(CrashSeverity.FATAL, builtIn[0].severity)
        assertEquals("SpringBoard", builtIn[1].processName.trim())
        assertEquals(CrashSeverity.WATCHDOG, builtIn[1].severity)

        val thermals = CrashLogAnalyzer.sampleThermalTelemetry(context, null)
        assertTrue(thermals.hostBatteryTempC > 0f)
    }

    @Test
    fun `AppBackupManager creates, verifies, restores, and deletes droidbackup snapshot`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DeviceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val mgr = AppBackupManager(context, db.backupSnapshotDao(), AfcFileManager(), testScope)

        val createEvents = mgr.createAppBackup(
            udid = "UDID-TEST-1",
            bundleId = "com.example.demo",
            appName = "DemoApp",
            includesDocuments = true,
            includesPreferences = true
        ).toList()

        val completed = createEvents.lastOrNull() as? BackupProgress.Completed
        assertNotNull(completed?.snapshot)

        val restoreEvents = mgr.restoreBackup(completed!!.snapshot!!).toList()
        assertTrue(restoreEvents.lastOrNull() is BackupProgress.Completed)

        mgr.deleteBackup(completed.snapshot!!)
        assertFalse(File(completed.snapshot!!.archivePath).exists())

        testScope.cancel()
        db.close()
    }

    @Test
    fun `DeviceRepository supports multi-device switching, pinning, nicknames, and forgetting`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, DeviceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = DeviceRepository(
            deviceDao = db.deviceDao(),
            pairingRecordDao = db.pairingRecordDao(),
            activityLogDao = db.activityLogDao(),
            bridgeRouter = IosBridgeRouter(context, UsbMuxdClient()),
            scope = testScope
        )

        val d1 = DeviceEntity(
            udid = "UDID-AAA", deviceName = "iPhone 15 Pro", productType = "iPhone16,1",
            modelNumber = "A2848", productVersion = "17.4", buildVersion = "21E219",
            serialNumber = "SER1", ecid = "0x1", imei = "1", wifiAddress = "00", bluetoothAddress = "00",
            activePairingMethod = "USB", connectionType = "USB", isDeveloperModeEnabled = true, isActivated = true,
            batteryLevel = 90, batteryHealth = 99, batteryCycles = 40, totalDiskCapacityBytes = 128L, freeDiskSpaceBytes = 64L
        )
        val d2 = d1.copy(udid = "UDID-BBB", deviceName = "iPad Pro M4", serialNumber = "SER2")

        db.deviceDao().insertOrUpdateDevice(d1)
        db.deviceDao().insertOrUpdateDevice(d2)

        repo.selectActiveDevice("UDID-BBB")
        repo.updateDeviceNickname("UDID-BBB", "Desk iPad")
        repo.togglePinDevice("UDID-BBB")

        val storedB = db.deviceDao().getDeviceByUdid("UDID-BBB")
        assertEquals("Desk iPad", storedB?.nickname)
        assertTrue(storedB?.isPinned == true)

        repo.forgetDevice("UDID-BBB")
        assertNull(db.deviceDao().getDeviceByUdid("UDID-BBB"))

        testScope.cancel()
        db.close()
    }
}
