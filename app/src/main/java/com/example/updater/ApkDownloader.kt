// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.updater

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

class ApkDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    /**
     * Downloads an APK to disk with resume support, speed & ETA calculations,
     * and exponential backoff retry.
     */
    fun download(
        url: String,
        targetFile: File,
        totalExpectedBytes: Long
    ): Flow<DownloadProgress> = flow {
        val maxRetries = 3
        var attempt = 0
        var isSuccess = false

        targetFile.parentFile?.mkdirs()

        while (attempt < maxRetries && !isSuccess) {
            try {
                attempt++
                val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

                // If already completely downloaded
                if (totalExpectedBytes > 0 && existingBytes >= totalExpectedBytes) {
                    emit(
                        DownloadProgress(
                            bytesDownloaded = existingBytes,
                            totalBytes = totalExpectedBytes,
                            progress = 1.0f,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L,
                            status = DownloadStatus.COMPLETED
                        )
                    )
                    isSuccess = true
                    return@flow
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .header("User-Agent", UpdateConfig.USER_AGENT)

                // Resume support via Range header
                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful && response.code != 206) {
                    throw IOException("Server returned HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IOException("Empty response body from server")
                val isPartial = response.code == 206
                val contentLength = body.contentLength()
                val totalBytes = if (isPartial) {
                    existingBytes + contentLength
                } else {
                    if (contentLength > 0) contentLength else totalExpectedBytes
                }

                val raf = RandomAccessFile(targetFile, "rw")
                if (isPartial) {
                    raf.seek(existingBytes)
                } else {
                    raf.setLength(0L) // Truncate and restart
                    raf.seek(0L)
                }

                var currentBytes = if (isPartial) existingBytes else 0L
                val inputStream = body.byteStream()
                val buffer = ByteArray(32768) // 32KB buffer

                var lastEmissionTime = System.currentTimeMillis()
                var bytesSinceLastSample = 0L
                var currentSpeed = 0L

                emit(
                    DownloadProgress(
                        bytesDownloaded = currentBytes,
                        totalBytes = totalBytes,
                        progress = if (totalBytes > 0) currentBytes.toFloat() / totalBytes else 0f,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L,
                        status = DownloadStatus.DOWNLOADING
                    )
                )

                try {
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        currentBytes += bytesRead
                        bytesSinceLastSample += bytesRead

                        val now = System.currentTimeMillis()
                        val deltaMs = now - lastEmissionTime

                        // Emit update every 250ms for smooth UI feedback
                        if (deltaMs >= 250) {
                            currentSpeed = (bytesSinceLastSample * 1000L) / deltaMs
                            val remainingBytes = if (totalBytes > currentBytes) totalBytes - currentBytes else 0L
                            val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L
                            val progressFrac = if (totalBytes > 0) currentBytes.toFloat() / totalBytes else 0f

                            emit(
                                DownloadProgress(
                                    bytesDownloaded = currentBytes,
                                    totalBytes = totalBytes,
                                    progress = progressFrac.coerceIn(0f, 1f),
                                    speedBytesPerSec = currentSpeed,
                                    etaSeconds = eta,
                                    status = DownloadStatus.DOWNLOADING
                                )
                            )

                            bytesSinceLastSample = 0L
                            lastEmissionTime = now
                        }
                    }

                    // Completed successfully
                    emit(
                        DownloadProgress(
                            bytesDownloaded = currentBytes,
                            totalBytes = totalBytes,
                            progress = 1.0f,
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L,
                            status = DownloadStatus.COMPLETED
                        )
                    )
                    isSuccess = true
                } finally {
                    try { inputStream.close() } catch (_: Exception) {}
                    try { raf.close() } catch (_: Exception) {}
                    try { body.close() } catch (_: Exception) {}
                }

            } catch (e: CancellationException) {
                emit(DownloadProgress(status = DownloadStatus.PAUSED, error = "Download cancelled"))
                throw e
            } catch (e: Exception) {
                if (attempt >= maxRetries) {
                    emit(
                        DownloadProgress(
                            status = DownloadStatus.FAILED,
                            error = "Download failed after $maxRetries attempts: ${e.message}"
                        )
                    )
                    return@flow
                }
                // Exponential backoff: 1s, 2s, 4s...
                val backoffMs = (1000L * (1 shl (attempt - 1)))
                delay(backoffMs)
            }
        }
    }.flowOn(Dispatchers.IO)
}
