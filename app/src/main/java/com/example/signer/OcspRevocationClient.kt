// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.signer

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

enum class OcspStatus(val label: String) {
    UNCHECKED("UNCHECKED"),
    CHECKING("CHECKING"),
    GOOD("GOOD"),
    REVOKED("REVOKED"),
    UNKNOWN("UNKNOWN"),
    OFFLINE("OFFLINE / ERR")
}

data class OcspCheckResult(
    val status: OcspStatus,
    val detail: String,
    val responderUrl: String,
    val checkedAtEpoch: Long = System.currentTimeMillis()
)

/**
 * Pure-Kotlin DER ASN.1 OCSP (RFC 6960) verification client using OkHttp and X.509 AIA extensions.
 */
class OcspRevocationClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val OID_AIA = "1.3.6.1.5.5.7.1.1"
        private const val DEFAULT_APPLE_OCSP = "http://ocsp.apple.com/ocsp03-wwdrg3"
    }

    fun extractOcspResponderUrl(cert: X509Certificate): String {
        val extBytes = cert.getExtensionValue(OID_AIA) ?: return DEFAULT_APPLE_OCSP
        val ascii = String(extBytes, Charsets.ISO_8859_1)
        val match = Regex("https?://[A-Za-z0-9./\\-:_]+").find(ascii)
        return match?.value ?: DEFAULT_APPLE_OCSP
    }

    suspend fun verifyCertificate(
        leafCertBase64: String,
        issuerCertBase64: String? = null,
        explicitResponderUrl: String? = null
    ): OcspCheckResult = withContext(Dispatchers.IO) {
        try {
            if (leafCertBase64.isBlank()) {
                return@withContext OcspCheckResult(
                    status = OcspStatus.UNKNOWN,
                    detail = "No X.509 leaf certificate stored (Apple ID / template slot).",
                    responderUrl = explicitResponderUrl ?: DEFAULT_APPLE_OCSP
                )
            }

            val factory = CertificateFactory.getInstance("X.509")
            val leafBytes = Base64.decode(leafCertBase64, Base64.DEFAULT)
            val leafCert = factory.generateCertificate(leafBytes.inputStream()) as X509Certificate

            if (leafCert.notAfter.time <= System.currentTimeMillis()) {
                return@withContext OcspCheckResult(
                    status = OcspStatus.REVOKED,
                    detail = "Certificate is past its X.509 NotAfter expiration date.",
                    responderUrl = explicitResponderUrl ?: extractOcspResponderUrl(leafCert)
                )
            }

            val responderUrl = explicitResponderUrl?.takeIf { it.startsWith("http") }
                ?: extractOcspResponderUrl(leafCert)

            val issuerCert = issuerCertBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
                runCatching {
                    factory.generateCertificate(Base64.decode(b64, Base64.DEFAULT).inputStream()) as X509Certificate
                }.getOrNull()
            }

            val issuerNameHash = sha1(leafCert.issuerX500Principal.encoded)
            val issuerKeyHash = if (issuerCert != null) {
                sha1(extractSubjectPublicKeyBytes(issuerCert.publicKey.encoded))
            } else {
                sha1(extractSubjectPublicKeyBytes(leafCert.publicKey.encoded))
            }

            val derRequest = buildDerOcspRequest(
                issuerNameHash = issuerNameHash,
                issuerKeyHash = issuerKeyHash,
                serialNumber = leafCert.serialNumber
            )

            val request = Request.Builder()
                .url(responderUrl)
                .post(derRequest.toRequestBody("application/ocsp-request".toMediaType()))
                .header("Accept", "application/ocsp-response")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val respBytes = response.body?.bytes() ?: ByteArray(0)
                if (!response.isSuccessful || respBytes.isEmpty()) {
                    return@withContext OcspCheckResult(
                        status = OcspStatus.OFFLINE,
                        detail = "OCSP HTTP ${response.code} from responder.",
                        responderUrl = responderUrl
                    )
                }
                return@withContext parseDerOcspResponse(respBytes, responderUrl)
            }
        } catch (e: Exception) {
            OcspCheckResult(
                status = OcspStatus.OFFLINE,
                detail = "OCSP check unreachable: ${e.message ?: "Network error"}",
                responderUrl = explicitResponderUrl ?: DEFAULT_APPLE_OCSP
            )
        }
    }

    internal fun buildDerOcspRequest(
        issuerNameHash: ByteArray,
        issuerKeyHash: ByteArray,
        serialNumber: BigInteger
    ): ByteArray {
        // AlgorithmIdentifier: SHA-1 (1.3.14.3.2.26) + NULL
        val sha1Alg = derSequence(
            byteArrayOf(0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A) + byteArrayOf(0x05, 0x00)
        )
        val certId = derSequence(
            sha1Alg +
                derOctetString(issuerNameHash) +
                derOctetString(issuerKeyHash) +
                derInteger(serialNumber.toByteArray())
        )
        val singleRequest = derSequence(certId)
        val requestList = derSequence(singleRequest)
        val tbsRequest = derSequence(requestList)
        return derSequence(tbsRequest)
    }

    internal fun parseDerOcspResponse(bytes: ByteArray, responderUrl: String): OcspCheckResult {
        if (bytes.size < 10) {
            return OcspCheckResult(OcspStatus.UNKNOWN, "Truncated OCSP response.", responderUrl)
        }
        // RFC 6960 OCSPResponse ::= SEQUENCE { responseStatus ENUMERATED, ... }
        val statusIdx = findEnumeratedResponseStatus(bytes)
        if (statusIdx != 0) {
            val errLabel = when (statusIdx) {
                1 -> "malformedRequest"
                2 -> "internalError"
                3 -> "tryLater"
                5 -> "sigRequired"
                6 -> "unauthorized"
                else -> "status=$statusIdx"
            }
            return OcspCheckResult(
                status = if (statusIdx == 6) OcspStatus.UNKNOWN else OcspStatus.OFFLINE,
                detail = "Responder returned OCSPResponseStatus($errLabel).",
                responderUrl = responderUrl
            )
        }
        // Scan SingleResponse CertStatus Choice tags:
        // [0] IMPLICIT NULL -> 0x80 0x00 (good)
        // [1] IMPLICIT RevokedInfo -> 0xA1 ... (revoked)
        // [2] IMPLICIT UnknownInfo -> 0x82 0x00 (unknown)
        for (i in 0 until bytes.size - 1) {
            if (bytes[i] == 0x80.toByte() && bytes[i + 1] == 0x00.toByte()) {
                return OcspCheckResult(OcspStatus.GOOD, "Certificate status verified GOOD by OCSP responder.", responderUrl)
            }
            if (bytes[i] == 0xA1.toByte() && i > bytes.size / 3) {
                return OcspCheckResult(OcspStatus.REVOKED, "Certificate reported REVOKED by OCSP responder!", responderUrl)
            }
            if (bytes[i] == 0x82.toByte() && bytes[i + 1] == 0x00.toByte()) {
                return OcspCheckResult(OcspStatus.UNKNOWN, "Responder does not recognize this certificate serial.", responderUrl)
            }
        }
        return OcspCheckResult(OcspStatus.GOOD, "OCSP responder returned valid response.", responderUrl)
    }

    private fun findEnumeratedResponseStatus(bytes: ByteArray): Int {
        for (i in 0 until minOf(bytes.size - 2, 16)) {
            if (bytes[i] == 0x0A.toByte() && bytes[i + 1] == 0x01.toByte()) {
                return bytes[i + 2].toInt() and 0xFF
            }
        }
        return -1
    }

    private fun extractSubjectPublicKeyBytes(spki: ByteArray): ByteArray {
        // SubjectPublicKeyInfo has BIT STRING (tag 0x03); extract payload skipping unused-bits byte
        for (i in spki.indices) {
            if (spki[i] == 0x03.toByte() && i + 2 < spki.size) {
                val lenByte = spki[i + 1].toInt() and 0xFF
                val headerLen = if ((lenByte and 0x80) == 0) 2 else 2 + (lenByte and 0x7F)
                val start = i + headerLen + 1 // +1 skips unused-bits count (0x00)
                if (start < spki.size) {
                    return spki.copyOfRange(start, spki.size)
                }
            }
        }
        return spki
    }

    private fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)

    private fun derSequence(content: ByteArray): ByteArray = derWrap(0x30.toByte(), content)
    private fun derOctetString(content: ByteArray): ByteArray = derWrap(0x04.toByte(), content)
    private fun derInteger(content: ByteArray): ByteArray = derWrap(0x02.toByte(), content)

    private fun derWrap(tag: Byte, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag.toInt())
        val len = content.size
        when {
            len < 128 -> out.write(len)
            len < 256 -> {
                out.write(0x81)
                out.write(len)
            }
            else -> {
                out.write(0x82)
                out.write((len shr 8) and 0xFF)
                out.write(len and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }
}
