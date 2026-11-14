// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.pairing

import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.UUID

object PairingCertificateUtil {

    fun generateHostId(): String {
        return UUID.randomUUID().toString().uppercase()
    }

    fun generateSystemBuid(): String {
        return UUID.randomUUID().toString().uppercase()
    }

    /**
     * Generates a standard 2048-bit RSA keypair for host and root certificates.
     */
    fun generateRsaKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048, SecureRandom())
        return keyGen.generateKeyPair()
    }

    fun keyToPem(keyBytes: ByteArray, type: String): String {
        val base64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        val chunks = base64.chunked(64).joinToString("\n")
        return "-----BEGIN $type-----\n$chunks\n-----END $type-----"
    }

    /**
     * Creates a standard lockdown pairing XML string conforming to Apple's lockdown schema.
     */
    fun createLockdownPlistXml(
        hostId: String,
        systemBuid: String,
        rootCertBase64: String,
        hostCertBase64: String,
        deviceCertBase64: String,
        hostPrivateKeyBase64: String,
        wifiMac: String = ""
    ): String {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>DeviceCertificate</key>
                <data>$deviceCertBase64</data>
                <key>HostCertificate</key>
                <data>$hostCertBase64</data>
                <key>HostID</key>
                <string>$hostId</string>
                <key>HostPrivateKey</key>
                <data>$hostPrivateKeyBase64</data>
                <key>RootCertificate</key>
                <data>$rootCertBase64</data>
                <key>SystemBUID</key>
                <string>$systemBuid</string>
                ${if (wifiMac.isNotEmpty()) "<key>WiFiMACAddress</key><string>$wifiMac</string>" else ""}
            </dict>
            </plist>
        """.trimIndent()
    }

    /**
     * Formats SideStore pairing file: SideStore requires exact keys:
     * DeviceCertificate, HostCertificate, HostID, HostPrivateKey, RootCertificate, SystemBUID, and WiFiMACAddress.
     */
    fun createSideStorePairingFile(
        hostId: String,
        systemBuid: String,
        rootCertBase64: String,
        hostCertBase64: String,
        deviceCertBase64: String,
        hostPrivateKeyBase64: String,
        wifiMac: String
    ): String {
        val safeWifiMac = wifiMac.ifEmpty { "00:00:00:00:00:00" }
        return createLockdownPlistXml(
            hostId = hostId,
            systemBuid = systemBuid,
            rootCertBase64 = rootCertBase64,
            hostCertBase64 = hostCertBase64,
            deviceCertBase64 = deviceCertBase64,
            hostPrivateKeyBase64 = hostPrivateKeyBase64,
            wifiMac = safeWifiMac
        )
    }

    /**
     * Formats JSON pairing record for StikDebug / JITStreamer.
     */
    fun createStikDebugJson(
        udid: String,
        hostId: String,
        systemBuid: String,
        rootCertPem: String,
        hostCertPem: String,
        deviceCertPem: String,
        hostPrivateKeyPem: String,
        wifiMac: String
    ): String {
        fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """
            {
                "udid": "$udid",
                "host_id": "$hostId",
                "system_buid": "$systemBuid",
                "root_certificate": "${escapeJson(rootCertPem)}",
                "host_certificate": "${escapeJson(hostCertPem)}",
                "device_certificate": "${escapeJson(deviceCertPem)}",
                "host_private_key": "${escapeJson(hostPrivateKeyPem)}",
                "wifi_mac": "$wifiMac",
                "version": 1
            }
        """.trimIndent()
    }
}
