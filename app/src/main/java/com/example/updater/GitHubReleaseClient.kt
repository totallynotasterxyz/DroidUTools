// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.updater

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface GitHubReleaseResult {
    data class Success(val releases: List<ReleaseInfo>, val fromCache: Boolean = false) : GitHubReleaseResult
    data class RateLimited(val message: String, val resetTimeFormatted: String, val resetTimestampSeconds: Long) : GitHubReleaseResult
    data class Error(val message: String) : GitHubReleaseResult
}

class GitHubReleaseClient(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private val KEY_RELEASES_ETAG = stringPreferencesKey("github_releases_etag")
        private val KEY_RELEASES_CACHE_JSON = stringPreferencesKey("github_releases_cached_json")
    }

    suspend fun fetchReleases(): GitHubReleaseResult = withContext(Dispatchers.IO) {
        try {
            if (UpdateConfig.GITHUB_OWNER.contains("[") || UpdateConfig.REPO_NAME.contains("[") ||
                UpdateConfig.GITHUB_OWNER.isBlank() || UpdateConfig.REPO_NAME.isBlank()
            ) {
                return@withContext GitHubReleaseResult.Error(
                    "Release repository is not configured in UpdateConfig."
                )
            }

            // Read saved ETag and cached JSON from private DataStore
            val prefs = context.dataStore.data.first()
            val savedEtag = prefs[KEY_RELEASES_ETAG]
            val cachedJson = prefs[KEY_RELEASES_CACHE_JSON]

            val requestBuilder = Request.Builder()
                .url(UpdateConfig.RELEASES_API_URL)
                .header("User-Agent", UpdateConfig.USER_AGENT)
                .header("Accept", "application/vnd.github.v3+json")

            // Attach If-None-Match if we have a valid cached response
            if (!savedEtag.isNullOrBlank() && !cachedJson.isNullOrBlank()) {
                requestBuilder.header("If-None-Match", savedEtag)
            }

            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()

            when (response.code) {
                304 -> {
                    // Not Modified: 0 rate-limit consumed. Parse from cache.
                    if (!cachedJson.isNullOrBlank()) {
                        val parsed = parseReleasesJson(cachedJson)
                        GitHubReleaseResult.Success(releases = parsed, fromCache = true)
                    } else {
                        // Fallback: re-fetch without ETag
                        fetchWithoutEtag()
                    }
                }

                200 -> {
                    val responseBody = response.body?.string() ?: ""
                    val newEtag = response.header("ETag")

                    // Cache response body and ETag
                    context.dataStore.edit { editPrefs ->
                        if (!newEtag.isNullOrBlank()) {
                            editPrefs[KEY_RELEASES_ETAG] = newEtag
                        }
                        if (responseBody.isNotBlank()) {
                            editPrefs[KEY_RELEASES_CACHE_JSON] = responseBody
                        }
                    }

                    val releases = parseReleasesJson(responseBody)
                    GitHubReleaseResult.Success(releases = releases, fromCache = false)
                }

                403, 429 -> {
                    val resetHeader = response.header("x-ratelimit-reset")
                    val resetSeconds = resetHeader?.toLongOrNull() ?: 0L
                    val formattedTime = if (resetSeconds > 0) {
                        val date = Date(resetSeconds * 1000L)
                        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                        sdf.format(date)
                    } else {
                        "shortly"
                    }

                    // If cached response exists, offer it with a notice
                    if (!cachedJson.isNullOrBlank()) {
                        val parsed = parseReleasesJson(cachedJson)
                        if (parsed.isNotEmpty()) {
                            return@withContext GitHubReleaseResult.Success(releases = parsed, fromCache = true)
                        }
                    }

                    GitHubReleaseResult.RateLimited(
                        message = "GitHub API rate limit reached. Next update check available at $formattedTime.",
                        resetTimeFormatted = formattedTime,
                        resetTimestampSeconds = resetSeconds
                    )
                }

                else -> {
                    if (!cachedJson.isNullOrBlank()) {
                        val parsed = parseReleasesJson(cachedJson)
                        if (parsed.isNotEmpty()) {
                            return@withContext GitHubReleaseResult.Success(releases = parsed, fromCache = true)
                        }
                    }
                    GitHubReleaseResult.Error("GitHub responded with HTTP ${response.code}: ${response.message}")
                }
            }
        } catch (e: IOException) {
            // Offline or network error: Attempt to serve cached releases
            val prefs = try { context.dataStore.data.first() } catch (_: Exception) { null }
            val cachedJson = prefs?.get(KEY_RELEASES_CACHE_JSON)
            if (!cachedJson.isNullOrBlank()) {
                val parsed = parseReleasesJson(cachedJson)
                if (parsed.isNotEmpty()) {
                    return@withContext GitHubReleaseResult.Success(releases = parsed, fromCache = true)
                }
            }
            GitHubReleaseResult.Error("Network error: Could not reach GitHub servers. Check your internet connection.")
        } catch (e: Exception) {
            GitHubReleaseResult.Error("Error parsing update information: ${e.message}")
        }
    }

    private suspend fun fetchWithoutEtag(): GitHubReleaseResult {
        val request = Request.Builder()
            .url(UpdateConfig.RELEASES_API_URL)
            .header("User-Agent", UpdateConfig.USER_AGENT)
            .header("Accept", "application/vnd.github.v3+json")
            .build()
        val response = okHttpClient.newCall(request).execute()
        val body = response.body?.string() ?: ""
        return if (response.isSuccessful && body.isNotBlank()) {
            GitHubReleaseResult.Success(parseReleasesJson(body))
        } else {
            GitHubReleaseResult.Error("Failed to fetch releases from GitHub: ${response.code}")
        }
    }

    private fun parseReleasesJson(jsonString: String): List<ReleaseInfo> {
        val results = mutableListOf<ReleaseInfo>()
        val array = JSONArray(jsonString)

        for (i in 0 until array.length()) {
            val releaseObj = array.getJSONObject(i)
            val isDraft = releaseObj.optBoolean("draft", false)
            if (isDraft) continue // Skip drafts

            val isPrerelease = releaseObj.optBoolean("prerelease", false)
            val tagName = releaseObj.optString("tag_name", "")
            val htmlUrl = releaseObj.optString("html_url", "")
            val body = releaseObj.optString("body", "")
            val publishedAt = releaseObj.optString("published_at", "")

            val assetsArray = releaseObj.optJSONArray("assets") ?: JSONArray()
            var apkAsset: ReleaseAsset? = null
            var sha256Asset: ReleaseAsset? = null

            for (j in 0 until assetsArray.length()) {
                val assetObj = assetsArray.getJSONObject(j)
                val assetName = assetObj.optString("name", "")
                val downloadUrl = assetObj.optString("browser_download_url", "")
                val sizeBytes = assetObj.optLong("size", 0L)
                val contentType = assetObj.optString("content_type", "")

                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkAsset = ReleaseAsset(
                        name = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        contentType = contentType
                    )
                } else if (assetName.endsWith(".sha256", ignoreCase = true)) {
                    sha256Asset = ReleaseAsset(
                        name = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        contentType = contentType
                    )
                }
            }

            if (apkAsset == null) continue // Skip releases without an APK asset

            // Parse Version Name & Version Code
            // Format: DroidUtools-v{versionName}-{versionCode}-{buildType}.apk or tag v1.1.0
            val versionName = tagName.removePrefix("v").removePrefix("V")
            val versionCode = extractVersionCode(apkAsset.name, tagName)

            val channel = when {
                tagName.startsWith("nightly", ignoreCase = true) || apkAsset.name.contains("nightly", ignoreCase = true) ->
                    UpdateChannel.NIGHTLY
                isPrerelease || tagName.contains("-beta") || tagName.contains("-rc") ->
                    UpdateChannel.PRERELEASE
                else ->
                    UpdateChannel.STABLE
            }

            results.add(
                ReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    versionCode = versionCode,
                    channel = channel,
                    releaseNotes = body,
                    publishedAt = publishedAt,
                    apkAsset = apkAsset,
                    sha256Asset = sha256Asset,
                    htmlUrl = htmlUrl
                )
            )
        }

        return results
    }

    private fun extractVersionCode(apkName: String, tagName: String): Long {
        // Try matching "DroidUtools-v1.1.0-105-release.apk" -> 105
        val regex = Regex("""-v?[0-9.]+-([0-9]+)-""")
        val match = regex.find(apkName)
        val codeStr = match?.groupValues?.getOrNull(1)
        if (!codeStr.isNullOrBlank()) {
            return codeStr.toLongOrNull() ?: 1L
        }

        // Fallback: parse semver major*10000 + minor*100 + patch
        val semVer = SemVer.parse(tagName)
        if (semVer != null) {
            return (semVer.major * 10000L) + (semVer.minor * 100L) + semVer.patch.toLong()
        }
        return 1L
    }
}
