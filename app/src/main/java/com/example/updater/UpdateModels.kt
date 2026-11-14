// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.updater

import java.io.File

/**
 * Release distribution channels selectable by the user in Settings.
 */
enum class UpdateChannel(val displayName: String, val description: String) {
    STABLE("Stable", "Official thoroughly-tested releases only (recommended)"),
    PRERELEASE("Pre-release", "Includes beta and release candidate testing builds"),
    NIGHTLY("Nightly", "Automated daily development builds with cutting-edge changes")
}

/**
 * Metadata for a downloadable asset attached to a GitHub release.
 */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String
)

/**
 * Complete release metadata retrieved from GitHub Releases API.
 */
data class ReleaseInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Long,
    val channel: UpdateChannel,
    val releaseNotes: String,
    val publishedAt: String,
    val apkAsset: ReleaseAsset,
    val sha256Asset: ReleaseAsset?,
    val htmlUrl: String
)

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    COMPLETED,
    FAILED
}

data class DownloadProgress(
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val error: String? = null
) {
    val speedFormatted: String
        get() {
            val kb = speedBytesPerSec / 1024.0
            return if (kb > 1024.0) {
                String.format("%.1f MB/s", kb / 1024.0)
            } else {
                String.format("%.0f KB/s", kb)
            }
        }

    val etaFormatted: String
        get() {
            return if (etaSeconds <= 0) {
                "--"
            } else if (etaSeconds < 60) {
                "${etaSeconds}s remaining"
            } else {
                val mins = etaSeconds / 60
                val secs = etaSeconds % 60
                "${mins}m ${secs}s remaining"
            }
        }

    val sizeFormatted: String
        get() {
            val downloadedMb = bytesDownloaded / (1024.0 * 1024.0)
            val totalMb = totalBytes / (1024.0 * 1024.0)
            return if (totalBytes > 0) {
                String.format("%.1f / %.1f MB", downloadedMb, totalMb)
            } else {
                String.format("%.1f MB", downloadedMb)
            }
        }
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val lastCheckedMs: Long) : UpdateState
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: DownloadProgress) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateState
    data class Installing(val release: ReleaseInfo, val method: String) : UpdateState
    data class Error(val message: String, val retryAfterMs: Long? = null) : UpdateState
}

/**
 * Semantic version parser and comparator.
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        // If one is pre-release and the other isn't, the one without pre-release is newer
        if (preRelease == null && other.preRelease != null) return 1
        if (preRelease != null && other.preRelease == null) return -1
        if (preRelease != null && other.preRelease != null) {
            return preRelease.compareTo(other.preRelease)
        }
        return 0
    }

    companion object {
        fun parse(versionString: String): SemVer? {
            return try {
                val cleaned = versionString.trim().removePrefix("v").removePrefix("V")
                val parts = cleaned.split("-", limit = 2)
                val coreParts = parts[0].split(".")
                val major = coreParts.getOrNull(0)?.toIntOrNull() ?: 0
                val minor = coreParts.getOrNull(1)?.toIntOrNull() ?: 0
                val patch = coreParts.getOrNull(2)?.toIntOrNull() ?: 0
                val preRelease = parts.getOrNull(1)
                SemVer(major, minor, patch, preRelease)
            } catch (_: Exception) {
                null
            }
        }
    }
}
