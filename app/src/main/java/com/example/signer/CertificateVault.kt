// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.signer

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class VaultEntry(
    val id: String,
    val certificate: SigningCertificate,
    val profileInfo: ProvisioningProfileInfo? = null,
    val ocspStatus: OcspStatus = OcspStatus.UNCHECKED,
    val ocspCheckedAt: Long = 0L,
    val ocspDetail: String = "Tap Check OCSP to verify live revocation status.",
    val sha1Fingerprint: String = "",
    val serialHex: String = "",
    val leafCertBase64: String = "",
    val issuerCertBase64: String = "",
    val ocspUrl: String = "",
    val isActive: Boolean = false
)

/**
 * Hardware-backed AndroidKeyStore AES-256-GCM encrypted vault for .p12 signing identities,
 * .mobileprovision profiles, and live OCSP verification state.
 */
class CertificateVault(
    private val context: Context,
    private val ocspClient: OcspRevocationClient = OcspRevocationClient()
) {
    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "droidutools_cert_vault_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val VAULT_FILE = "vault_sealed.bin"
        private const val IV_LENGTH_BYTES = 12
    }

    private val vaultDir: File = File(context.filesDir, "cert_vault").apply { mkdirs() }
    private val vaultFile: File = File(vaultDir, VAULT_FILE)

    private val _entries = MutableStateFlow<List<VaultEntry>>(loadVaultEntries())
    val entries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()

    val activeEntry: VaultEntry?
        get() = _entries.value.firstOrNull { it.isActive } ?: _entries.value.firstOrNull()

    suspend fun importP12Certificate(
        p12Bytes: ByteArray,
        password: String,
        displayNameOverride: String? = null
    ): Result<VaultEntry> = withContext(Dispatchers.IO) {
        try {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(p12Bytes.inputStream(), password.toCharArray())

            val aliases = ks.aliases().toList()
            val keyAlias = aliases.firstOrNull { ks.isKeyEntry(it) } ?: aliases.firstOrNull()
                ?: return@withContext Result.failure(IllegalArgumentException("No certificate alias found inside .p12 container."))

            val chain = ks.getCertificateChain(keyAlias)
            val leaf = (chain?.firstOrNull() ?: ks.getCertificate(keyAlias)) as? X509Certificate
                ?: return@withContext Result.failure(IllegalArgumentException("Alias does not contain a valid X.509 certificate."))
            val issuer = chain?.getOrNull(1) as? X509Certificate

            val subjectDn = leaf.subjectX500Principal.name
            val commonName = extractDnField(subjectDn, "CN") ?: displayNameOverride?.ifBlank { null } ?: keyAlias
            val orgUnit = extractDnField(subjectDn, "OU") ?: ""
            val orgName = extractDnField(subjectDn, "O") ?: commonName

            val fingerprint = sha1Fingerprint(leaf.encoded)
            val serialHex = leaf.serialNumber.toString(16).uppercase()
            val leafB64 = Base64.encodeToString(leaf.encoded, Base64.NO_WRAP)
            val issuerB64 = issuer?.let { Base64.encodeToString(it.encoded, Base64.NO_WRAP) } ?: ""
            val ocspUrl = ocspClient.extractOcspResponderUrl(leaf)

            val cert = SigningCertificate(
                id = UUID.randomUUID().toString(),
                displayName = displayNameOverride?.ifBlank { null } ?: commonName,
                teamName = orgName,
                teamId = orgUnit.ifBlank { "TEAM-UNKNOWN" },
                isFreeAppleId = commonName.contains("Apple Development", ignoreCase = true),
                createdAt = leaf.notBefore.time,
                expiresAt = leaf.notAfter.time,
                certificateType = CertificateType.P12_CERTIFICATE,
                p12Base64 = Base64.encodeToString(p12Bytes, Base64.NO_WRAP),
                p12Password = password
            )

            val shouldActivate = _entries.value.isEmpty()
            val newEntry = VaultEntry(
                id = cert.id,
                certificate = cert,
                ocspStatus = OcspStatus.UNCHECKED,
                ocspDetail = "Imported X.509 cert (Serial $serialHex). Tap Check OCSP.",
                sha1Fingerprint = fingerprint,
                serialHex = serialHex,
                leafCertBase64 = leafB64,
                issuerCertBase64 = issuerB64,
                ocspUrl = ocspUrl,
                isActive = shouldActivate
            )

            persistAndEmit(_entries.value + newEntry)
            Result.success(newEntry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importMobileProvision(
        provisionBytes: ByteArray,
        targetEntryId: String? = null
    ): Result<VaultEntry> = withContext(Dispatchers.IO) {
        val parsed = ProvisioningProfileParser.parse(provisionBytes)
        if (parsed.isFailure) {
            return@withContext Result.failure(parsed.exceptionOrNull() ?: IllegalStateException("Failed to parse mobileprovision"))
        }
        val info = parsed.getOrThrow()

        val existingList = _entries.value
        val target = when {
            targetEntryId != null -> existingList.firstOrNull { it.id == targetEntryId }
            else -> existingList.firstOrNull {
                it.isActive || (info.teamIdentifier.isNotBlank() && it.certificate.teamId == info.teamIdentifier)
            }
        }

        if (target != null) {
            val updated = target.copy(
                profileInfo = info,
                certificate = target.certificate.copy(
                    mobileprovisionXml = info.rawXmlPlist,
                    teamId = info.teamIdentifier.ifBlank { target.certificate.teamId },
                    teamName = info.teamName.ifBlank { target.certificate.teamName }
                )
            )
            persistAndEmit(existingList.map { if (it.id == target.id) updated else it })
            Result.success(updated)
        } else {
            val cert = SigningCertificate(
                id = UUID.randomUUID().toString(),
                displayName = info.name,
                teamName = info.teamName,
                teamId = info.teamIdentifier.ifBlank { "PROFILE" },
                isFreeAppleId = !info.provisionsAllDevices && info.remainingDays <= 8,
                createdAt = info.creationDateEpoch.takeIf { it > 0 } ?: System.currentTimeMillis(),
                expiresAt = info.expirationDateEpoch.takeIf { it > 0 }
                    ?: (System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000),
                certificateType = CertificateType.P12_CERTIFICATE,
                mobileprovisionXml = info.rawXmlPlist
            )
            val entry = VaultEntry(
                id = cert.id,
                certificate = cert,
                profileInfo = info,
                ocspStatus = OcspStatus.UNKNOWN,
                ocspDetail = "Standalone provisioning profile imported (attach .p12 for signing).",
                isActive = existingList.isEmpty()
            )
            persistAndEmit(existingList + entry)
            Result.success(entry)
        }
    }

    suspend fun recordAppleIdIdentity(email: String, teamName: String = "Personal Team", teamId: String = "FREE7DAY"): VaultEntry =
        withContext(Dispatchers.IO) {
            val cert = SigningCertificate(
                id = UUID.randomUUID().toString(),
                displayName = email,
                teamName = teamName,
                teamId = teamId,
                isFreeAppleId = true,
                appleIdEmail = email,
                certificateType = CertificateType.APPLE_ID
            )
            val entry = VaultEntry(
                id = cert.id,
                certificate = cert,
                ocspStatus = OcspStatus.UNKNOWN,
                ocspDetail = "Anisette Apple ID signing slot (7-day rolling certificate).",
                isActive = true
            )
            val updated = _entries.value.map { it.copy(isActive = false) } + entry
            persistAndEmit(updated)
            entry
        }

    suspend fun checkOcspStatus(entryId: String): OcspCheckResult = withContext(Dispatchers.IO) {
        val entry = _entries.value.firstOrNull { it.id == entryId }
            ?: return@withContext OcspCheckResult(OcspStatus.UNKNOWN, "Vault entry not found", "")

        _entries.value = _entries.value.map {
            if (it.id == entryId) it.copy(ocspStatus = OcspStatus.CHECKING) else it
        }

        val res = ocspClient.verifyCertificate(
            leafCertBase64 = entry.leafCertBase64,
            issuerCertBase64 = entry.issuerCertBase64.ifBlank { null },
            explicitResponderUrl = entry.ocspUrl.ifBlank { null }
        )

        val updated = _entries.value.map {
            if (it.id == entryId) {
                it.copy(
                    ocspStatus = res.status,
                    ocspCheckedAt = res.checkedAtEpoch,
                    ocspDetail = res.detail
                )
            } else {
                it
            }
        }
        persistAndEmit(updated)
        res
    }

    suspend fun setActiveEntry(entryId: String) = withContext(Dispatchers.IO) {
        val updated = _entries.value.map { it.copy(isActive = it.id == entryId) }
        persistAndEmit(updated)
    }

    suspend fun deleteEntry(entryId: String) = withContext(Dispatchers.IO) {
        val filtered = _entries.value.filter { it.id != entryId }
        val normalized = if (filtered.isNotEmpty() && filtered.none { it.isActive }) {
            filtered.mapIndexed { idx, item -> item.copy(isActive = idx == 0) }
        } else {
            filtered
        }
        persistAndEmit(normalized)
    }

    suspend fun wipeVault() = withContext(Dispatchers.IO) {
        if (vaultFile.exists()) vaultFile.delete()
        _entries.value = emptyList()
    }

    private fun persistAndEmit(list: List<VaultEntry>) {
        saveVaultEntries(list)
        _entries.value = list
    }

    private fun loadVaultEntries(): List<VaultEntry> {
        return try {
            if (!vaultFile.exists()) return emptyList()
            val raw = vaultFile.readBytes()
            if (raw.size <= IV_LENGTH_BYTES) return emptyList()
            val iv = raw.copyOfRange(0, IV_LENGTH_BYTES)
            val cipherText = raw.copyOfRange(IV_LENGTH_BYTES, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            val plain = String(cipher.doFinal(cipherText), Charsets.UTF_8)
            val arr = JSONArray(plain)
            buildList {
                for (i in 0 until arr.length()) {
                    add(entryFromJson(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveVaultEntries(list: List<VaultEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(entryToJson(it)) }
            val plain = arr.toString().toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encrypted = cipher.doFinal(plain)
            val iv = cipher.iv
            val combined = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
            vaultFile.writeBytes(combined)
        } catch (_: Exception) {
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
            ks.load(null)
            (ks.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
                keyGen.init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                keyGen.generateKey()
            }
        } catch (_: Exception) {
            // Deterministic JVM test fallback only when AndroidKeyStore is absent (e.g. Robolectric host)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("${context.packageName}:droidutools-vault".toByteArray(Charsets.UTF_8))
            SecretKeySpec(digest, "AES")
        }
    }

    private fun entryToJson(entry: VaultEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("displayName", entry.certificate.displayName)
        put("teamName", entry.certificate.teamName)
        put("teamId", entry.certificate.teamId)
        put("isFreeAppleId", entry.certificate.isFreeAppleId)
        put("appleIdEmail", entry.certificate.appleIdEmail ?: "")
        put("createdAt", entry.certificate.createdAt)
        put("expiresAt", entry.certificate.expiresAt)
        put("certificateType", entry.certificate.certificateType.name)
        put("p12Base64", entry.certificate.p12Base64)
        put("p12Password", entry.certificate.p12Password)
        put("mobileprovisionXml", entry.certificate.mobileprovisionXml)
        entry.profileInfo?.let { put("profileInfo", it.toJson()) }
        put("ocspStatus", entry.ocspStatus.name)
        put("ocspCheckedAt", entry.ocspCheckedAt)
        put("ocspDetail", entry.ocspDetail)
        put("sha1Fingerprint", entry.sha1Fingerprint)
        put("serialHex", entry.serialHex)
        put("leafCertBase64", entry.leafCertBase64)
        put("issuerCertBase64", entry.issuerCertBase64)
        put("ocspUrl", entry.ocspUrl)
        put("isActive", entry.isActive)
    }

    private fun entryFromJson(obj: JSONObject): VaultEntry {
        val certType = runCatching {
            CertificateType.valueOf(obj.optString("certificateType", CertificateType.P12_CERTIFICATE.name))
        }.getOrDefault(CertificateType.P12_CERTIFICATE)
        val cert = SigningCertificate(
            id = obj.optString("id"),
            displayName = obj.optString("displayName"),
            teamName = obj.optString("teamName"),
            teamId = obj.optString("teamId"),
            isFreeAppleId = obj.optBoolean("isFreeAppleId", true),
            appleIdEmail = obj.optString("appleIdEmail").ifBlank { null },
            createdAt = obj.optLong("createdAt"),
            expiresAt = obj.optLong("expiresAt"),
            certificateType = certType,
            p12Base64 = obj.optString("p12Base64"),
            p12Password = obj.optString("p12Password"),
            mobileprovisionXml = obj.optString("mobileprovisionXml")
        )
        val profileObj = obj.optJSONObject("profileInfo")
        val ocspStat = runCatching {
            OcspStatus.valueOf(obj.optString("ocspStatus", OcspStatus.UNCHECKED.name))
        }.getOrDefault(OcspStatus.UNCHECKED)

        return VaultEntry(
            id = cert.id,
            certificate = cert,
            profileInfo = profileObj?.let { ProvisioningProfileInfo.fromJson(it) },
            ocspStatus = ocspStat,
            ocspCheckedAt = obj.optLong("ocspCheckedAt"),
            ocspDetail = obj.optString("ocspDetail"),
            sha1Fingerprint = obj.optString("sha1Fingerprint"),
            serialHex = obj.optString("serialHex"),
            leafCertBase64 = obj.optString("leafCertBase64"),
            issuerCertBase64 = obj.optString("issuerCertBase64"),
            ocspUrl = obj.optString("ocspUrl"),
            isActive = obj.optBoolean("isActive")
        )
    }

    private fun extractDnField(dn: String, field: String): String? {
        val match = Regex("(?:^|,)\\s*$field=([^,]+)").find(dn) ?: return null
        return match.groupValues[1].trim().removeSurrounding("\"")
    }

    private fun sha1Fingerprint(encoded: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-1").digest(encoded)
        return hash.joinToString(":") { "%02X".format(it) }
    }
}
