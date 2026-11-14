// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.updater

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.data.dataStore
import com.example.legal.ConsentManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class UpdateManager(
    private val context: Context,
    private val consentManager: ConsentManager,
    private val scope: CoroutineScope,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(context),
    private val apkDownloader: ApkDownloader = ApkDownloader()
) {
    companion object {
        val KEY_AUTO_CHECK_ENABLED = booleanPreferencesKey("updater_auto_check_enabled")
        val KEY_LAST_CHECK_TIMESTAMP = longPreferencesKey("updater_last_check_timestamp")
        val KEY_UPDATE_CHANNEL = stringPreferencesKey("updater_selected_channel")
        val KEY_SKIPPED_VERSIONS = stringSetPreferencesKey("updater_skipped_version_codes")
        val KEY_LAST_RUN_VERSION_CODE = longPreferencesKey("updater_last_run_version_code")
        private const val MIN_EXTRA_STORAGE_BYTES = 50L * 1024L * 1024L // 50 MB safety headroom
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _hasUpdateBadge = MutableStateFlow(false)
    val hasUpdateBadge: StateFlow<Boolean> = _hasUpdateBadge.asStateFlow()

    private var activeDownloadJob: Job? = null
    private var downloadedApkFile: File? = null

    init {
        UpdateDownloadService.ensureNotificationChannels(context)

        scope.launch {
            UpdateDownloadService.cancelEvents.collect {
                cancelDownload()
            }
        }

        scope.launch {
            AppInstaller.installerEvents.collect { event ->
                when (event) {
                    is PackageInstallerEvent.Success -> {
                        _updateState.value = UpdateState.Idle
                        _hasUpdateBadge.value = false
                    }
                    is PackageInstallerEvent.Error -> {
                        _updateState.value = UpdateState.Error(event.message)
                    }
                }
            }
        }
    }

    val autoCheckEnabled: StateFlow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[KEY_AUTO_CHECK_ENABLED] ?: true }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val currentChannel: StateFlow<UpdateChannel> = context.dataStore.data
        .map { prefs ->
            val name = prefs[KEY_UPDATE_CHANNEL] ?: UpdateChannel.STABLE.name
            try { UpdateChannel.valueOf(name) } catch (_: Exception) { UpdateChannel.STABLE }
        }
        .stateIn(scope, SharingStarted.Eagerly, UpdateChannel.STABLE)

    val lastCheckTimestamp: StateFlow<Long> = context.dataStore.data
        .map { prefs -> prefs[KEY_LAST_CHECK_TIMESTAMP] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    val installedVersionCode: Long
        get() {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }
            } catch (_: Exception) {
                1L
            }
        }

    val installedVersionName: String
        get() {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                pInfo.versionName ?: "1.1.0"
            } catch (_: Exception) {
                "1.1.0"
            }
        }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_CHECK_ENABLED] = enabled }
    }

    suspend fun setUpdateChannel(channel: UpdateChannel) {
        context.dataStore.edit { prefs -> prefs[KEY_UPDATE_CHANNEL] = channel.name }
        checkForUpdates(manual = true)
    }

    suspend fun skipVersion(versionCode: Long) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_SKIPPED_VERSIONS] ?: emptySet()
            prefs[KEY_SKIPPED_VERSIONS] = existing + versionCode.toString()
        }
        _updateState.value = UpdateState.Idle
        _hasUpdateBadge.value = false
    }

    /**
     * Checks if the app was just upgraded to show the "What's New" release notes sheet,
     * and cleans up any leftover update APKs from the app cache.
     */
    suspend fun checkShouldShowWhatsNew(): Boolean {
        val prefs = context.dataStore.data.first()
        val lastRunCode = prefs[KEY_LAST_RUN_VERSION_CODE] ?: installedVersionCode
        val currentCode = installedVersionCode

        return if (currentCode > lastRunCode) {
            AppInstaller.cleanUpdateCache(context)
            context.dataStore.edit { editPrefs ->
                editPrefs[KEY_LAST_RUN_VERSION_CODE] = currentCode
            }
            true
        } else {
            if (!prefs.contains(KEY_LAST_RUN_VERSION_CODE)) {
                context.dataStore.edit { editPrefs ->
                    editPrefs[KEY_LAST_RUN_VERSION_CODE] = currentCode
                }
            }
            false
        }
    }

    /**
     * Checks for updates from GitHub Releases API.
     * Respects 12-hour throttling on automated checks, and STRICTLY blocks execution
     * if the user has not yet accepted legal consent.
     */
    fun checkForUpdates(manual: Boolean = false) {
        scope.launch {
            // 1. Consent Gate Rule: Send NOTHING before consent has been accepted
            val isConsentAccepted = consentManager.isConsentAccepted.first()
            if (!isConsentAccepted) {
                return@launch
            }

            // 2. Throttling Rule: Automatic checks at most once every 12 hours
            if (!manual) {
                val isAutoEnabled = autoCheckEnabled.first()
                if (!isAutoEnabled) return@launch

                val lastChecked = lastCheckTimestamp.first()
                val now = System.currentTimeMillis()
                if (now - lastChecked < UpdateConfig.AUTO_CHECK_INTERVAL_MS) {
                    return@launch
                }
            }

            if (!hasActiveNetwork()) {
                if (manual) {
                    _updateState.value = UpdateState.Error(
                        "No internet connection detected. Connect to Wi-Fi or mobile data and retry."
                    )
                }
                return@launch
            }

            _updateState.value = UpdateState.Checking

            val nowMs = System.currentTimeMillis()
            context.dataStore.edit { prefs ->
                prefs[KEY_LAST_CHECK_TIMESTAMP] = nowMs
            }

            when (val result = releaseClient.fetchReleases()) {
                is GitHubReleaseResult.Success -> {
                    val channel = currentChannel.first()
                    val prefs = context.dataStore.data.first()
                    val skippedCodes = prefs[KEY_SKIPPED_VERSIONS] ?: emptySet()

                    val targetRelease = findBestUpdate(
                        releases = result.releases,
                        selectedChannel = channel,
                        currentVersionCode = installedVersionCode,
                        currentVersionName = installedVersionName,
                        skippedCodes = skippedCodes,
                        manual = manual
                    )

                    if (targetRelease != null) {
                        _updateState.value = UpdateState.UpdateAvailable(targetRelease)
                        _hasUpdateBadge.value = true
                        if (!manual) {
                            UpdateDownloadService.notifyUpdateAvailable(context, targetRelease)
                        }
                    } else {
                        _updateState.value = UpdateState.UpToDate(nowMs)
                        _hasUpdateBadge.value = false
                    }
                }

                is GitHubReleaseResult.RateLimited -> {
                    _updateState.value = UpdateState.Error(
                        message = result.message,
                        retryAfterMs = result.resetTimestampSeconds * 1000L
                    )
                }

                is GitHubReleaseResult.Error -> {
                    _updateState.value = UpdateState.Error(result.message)
                }
            }
        }
    }

    private fun findBestUpdate(
        releases: List<ReleaseInfo>,
        selectedChannel: UpdateChannel,
        currentVersionCode: Long,
        currentVersionName: String,
        skippedCodes: Set<String>,
        manual: Boolean
    ): ReleaseInfo? {
        val currentSemVer = SemVer.parse(currentVersionName)

        for (release in releases) {
            val channelMatches = when (selectedChannel) {
                UpdateChannel.STABLE -> release.channel == UpdateChannel.STABLE
                UpdateChannel.PRERELEASE -> release.channel == UpdateChannel.STABLE || release.channel == UpdateChannel.PRERELEASE
                UpdateChannel.NIGHTLY -> true
            }

            if (!channelMatches) continue

            // Never offer a downgrade: versionCode must be strictly higher
            if (release.versionCode <= currentVersionCode) continue

            // Also compare semantic versions when both parse cleanly (except nightly date tags)
            if (release.channel != UpdateChannel.NIGHTLY && currentSemVer != null) {
                val candidateSemVer = SemVer.parse(release.versionName)
                if (candidateSemVer != null && candidateSemVer <= currentSemVer) {
                    continue
                }
            }

            // Respect skipped versions during automatic checks
            if (!manual && skippedCodes.contains(release.versionCode.toString())) continue

            return release
        }
        return null
    }

    /**
     * Initiates the resumable streaming download of the release APK after checking storage space.
     */
    fun startDownload(release: ReleaseInfo) {
        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            try {
                if (!hasActiveNetwork()) {
                    _updateState.value = UpdateState.Error("No internet connection. Download aborted.")
                    return@launch
                }

                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val availableBytes = getAvailableStorageBytes(updatesDir)
                val requiredBytes = release.apkAsset.sizeBytes + MIN_EXTRA_STORAGE_BYTES
                if (release.apkAsset.sizeBytes > 0 && availableBytes < requiredBytes) {
                    val neededMb = requiredBytes / (1024L * 1024L)
                    val availMb = availableBytes / (1024L * 1024L)
                    _updateState.value = UpdateState.Error(
                        "Insufficient storage space: need ~$neededMb MB free in cache, only $availMb MB available."
                    )
                    return@launch
                }

                val targetFile = File(updatesDir, release.apkAsset.name)
                downloadedApkFile = targetFile

                UpdateDownloadService.start(context, release.versionName)

                apkDownloader.download(
                    url = release.apkAsset.downloadUrl,
                    targetFile = targetFile,
                    totalExpectedBytes = release.apkAsset.sizeBytes
                ).collect { progress ->
                    when (progress.status) {
                        DownloadStatus.DOWNLOADING -> {
                            _updateState.value = UpdateState.Downloading(release, progress)
                            UpdateDownloadService.updateProgress(
                                context = context,
                                versionName = release.versionName,
                                percent = (progress.progress * 100).toInt(),
                                speed = progress.speedFormatted,
                                eta = progress.etaFormatted
                            )
                        }

                        DownloadStatus.COMPLETED -> {
                            UpdateDownloadService.stop(context)
                            verifyDownloadedApk(release, targetFile)
                        }

                        DownloadStatus.FAILED -> {
                            UpdateDownloadService.stop(context)
                            _updateState.value = UpdateState.Error(
                                progress.error ?: "Download failed. Please try again."
                            )
                        }

                        DownloadStatus.PAUSED -> {
                            UpdateDownloadService.stop(context)
                        }

                        else -> {}
                    }
                }
            } catch (e: CancellationException) {
                UpdateDownloadService.stop(context)
                _updateState.value = UpdateState.UpdateAvailable(release)
            } catch (e: Exception) {
                UpdateDownloadService.stop(context)
                _updateState.value = UpdateState.Error("Download error: ${e.message}")
            }
        }
    }

    fun cancelDownload() {
        activeDownloadJob?.cancel()
        UpdateDownloadService.stop(context)
        val currentState = _updateState.value
        if (currentState is UpdateState.Downloading) {
            _updateState.value = UpdateState.UpdateAvailable(currentState.release)
        } else {
            _updateState.value = UpdateState.Idle
        }
    }

    private suspend fun verifyDownloadedApk(release: ReleaseInfo, apkFile: File) {
        _updateState.value = UpdateState.Downloading(
            release = release,
            progress = DownloadProgress(
                progress = 1.0f,
                status = DownloadStatus.VERIFYING
            )
        )

        var expectedSha256: String? = null
        if (release.sha256Asset != null) {
            expectedSha256 = fetchSha256FromAsset(release.sha256Asset.downloadUrl)
        }

        // Strict verification: if sha256Asset exists but download failed, fail safely rather than skipping
        if (release.sha256Asset != null && expectedSha256.isNullOrBlank()) {
            apkFile.delete()
            _updateState.value = UpdateState.Error(
                "Could not fetch SHA-256 verification file from GitHub. Downloaded APK deleted for safety."
            )
            return
        }

        val verification = UpdateVerifier.verifyApk(
            context = context,
            apkFile = apkFile,
            expectedSha256 = expectedSha256
        )

        when (verification) {
            is VerificationResult.Success -> {
                _updateState.value = UpdateState.ReadyToInstall(release, apkFile)
            }
            is VerificationResult.Failed -> {
                _updateState.value = UpdateState.Error("Security verification failed: ${verification.reason}")
            }
        }
    }

    private suspend fun fetchSha256FromAsset(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).header("User-Agent", UpdateConfig.USER_AGENT).build()
            val client = OkHttpClient()
            val res = client.newCall(req).execute()
            if (res.isSuccessful) {
                res.body?.string()?.trim()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes installation of the verified APK.
     * Never auto-installs without the user's explicit tap.
     */
    fun installUpdate(release: ReleaseInfo, apkFile: File) {
        scope.launch {
            _updateState.value = UpdateState.Installing(release, "Installing update...")

            when (val result = AppInstaller.installApk(context, apkFile)) {
                is InstallResult.RootSuccess -> {
                    _updateState.value = UpdateState.Idle
                    _hasUpdateBadge.value = false
                }
                is InstallResult.SessionStarted -> {
                    // Keep dialog alive or idle until PackageInstaller broadcast arrives
                    _updateState.value = UpdateState.Installing(release, result.message)
                }
                is InstallResult.Failed -> {
                    _updateState.value = UpdateState.Error(
                        "Installation failed: ${result.error}. Check root or 'Install unknown apps' permission."
                    )
                }
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    private fun hasActiveNetwork(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun getAvailableStorageBytes(dir: File): Long {
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}
