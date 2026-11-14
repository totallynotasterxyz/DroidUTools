// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.installer

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed interface DownloadProgress {
    data class Progress(val progress: Float, val downloadedBytes: Long, val totalBytes: Long, val speedKbps: Long) : DownloadProgress
    data class Success(val downloadedFile: File) : DownloadProgress
    data class Error(val message: String) : DownloadProgress
}

class IpaDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun downloadIpa(url: String, targetFileName: String? = null): Flow<DownloadProgress> = flow {
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("https://", ignoreCase = true) && !trimmedUrl.startsWith("http://", ignoreCase = true)) {
            emit(DownloadProgress.Error("Invalid URL. Please enter an http:// or https:// direct IPA link."))
            return@flow
        }

        val downloadDir = File(context.cacheDir, "ipa_downloads")
        if (!downloadDir.exists()) downloadDir.mkdirs()

        val rawName = targetFileName ?: trimmedUrl.substringAfterLast("/").substringBefore("?").ifBlank { "app_${System.currentTimeMillis()}" }
        val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_").let {
            if (it.endsWith(".ipa", ignoreCase = true)) it else "$it.ipa"
        }
        val tempFile = File(downloadDir, "$sanitizedName.part")
        val targetFile = File(downloadDir, sanitizedName)

        try {
            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "DroidUtools/1.1 (Android)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(DownloadProgress.Error("HTTP error ${response.code}: ${response.message.ifBlank { "Download failed" }}"))
                    return@flow
                }

                val body = response.body ?: run {
                    emit(DownloadProgress.Error("Empty response body from server"))
                    return@flow
                }

                val totalBytes = body.contentLength()
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var lastTime = System.currentTimeMillis()
                var lastRead = 0L
                var speedKbps = 0L

                emit(DownloadProgress.Progress(0f, 0L, totalBytes, 0L))

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalRead += read

                            val currentTime = System.currentTimeMillis()
                            val timeDiff = currentTime - lastTime
                            if (timeDiff >= 350L) {
                                speedKbps = if (timeDiff > 0) ((totalRead - lastRead) * 1000L / timeDiff) / 1024L else 0L
                                lastTime = currentTime
                                lastRead = totalRead

                                val progress = if (totalBytes > 0L) (totalRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                                emit(DownloadProgress.Progress(progress, totalRead, totalBytes, speedKbps))
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }

            emit(DownloadProgress.Success(targetFile))
        } catch (e: Exception) {
            tempFile.delete()
            emit(DownloadProgress.Error(e.message ?: "Network error while downloading IPA"))
        }
    }.flowOn(Dispatchers.IO)
}
