// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

data class AfcFileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val modifiedTimestamp: Long = System.currentTimeMillis()
) {
    val formattedSize: String
        get() = if (isDirectory) "--" else when {
            sizeBytes > 1024 * 1024 * 1024 -> "${String.format("%.1f", sizeBytes / (1024.0 * 1024 * 1024))} GB"
            sizeBytes > 1024 * 1024 -> "${String.format("%.1f", sizeBytes / (1024.0 * 1024))} MB"
            sizeBytes > 1024 -> "${sizeBytes / 1024} KB"
            else -> "$sizeBytes B"
        }
}

class AfcFileManager {

    private var currentPath = "/Media"

    fun getCurrentPath(): String = currentPath

    suspend fun listDirectory(path: String): Result<List<AfcFileItem>> = withContext(Dispatchers.IO) {
        currentPath = path
        delay(300)
        // Simulated AFC Media file system
        val items = when (path) {
            "/", "/Media" -> listOf(
                AfcFileItem("DCIM", "$path/DCIM", isDirectory = true),
                AfcFileItem("PhotoData", "$path/PhotoData", isDirectory = true),
                AfcFileItem("Downloads", "$path/Downloads", isDirectory = true),
                AfcFileItem("Recordings", "$path/Recordings", isDirectory = true),
                AfcFileItem("Books", "$path/Books", isDirectory = true),
                AfcFileItem("iTunes_Control", "$path/iTunes_Control", isDirectory = true),
                AfcFileItem("airdrop_history.plist", "$path/airdrop_history.plist", isDirectory = false, sizeBytes = 14_200L),
                AfcFileItem("device_backup.xml", "$path/device_backup.xml", isDirectory = false, sizeBytes = 85_400L)
            )
            "/Media/DCIM" -> listOf(
                AfcFileItem("100APPLE", "$path/100APPLE", isDirectory = true),
                AfcFileItem("101APPLE", "$path/101APPLE", isDirectory = true)
            )
            "/Media/DCIM/100APPLE" -> listOf(
                AfcFileItem("IMG_0001.JPG", "$path/IMG_0001.JPG", isDirectory = false, sizeBytes = 3_450_000L),
                AfcFileItem("IMG_0002.HEIC", "$path/IMG_0002.HEIC", isDirectory = false, sizeBytes = 2_150_000L),
                AfcFileItem("IMG_0003.MOV", "$path/IMG_0003.MOV", isDirectory = false, sizeBytes = 48_200_000L)
            )
            "/Media/Downloads" -> listOf(
                AfcFileItem("SampleApp.ipa", "$path/SampleApp.ipa", isDirectory = false, sizeBytes = 24_500_000L),
                AfcFileItem("ConfigurationProfile.mobileconfig", "$path/ConfigurationProfile.mobileconfig", isDirectory = false, sizeBytes = 8_200L)
            )
            else -> listOf(
                AfcFileItem("Readme.txt", "$path/Readme.txt", isDirectory = false, sizeBytes = 1024L)
            )
        }
        Result.success(items)
    }

    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        delay(200)
        Result.success(Unit)
    }

    suspend fun uploadFile(localFile: File, remoteDirectory: String): Result<Unit> = withContext(Dispatchers.IO) {
        delay(500)
        Result.success(Unit)
    }
}
