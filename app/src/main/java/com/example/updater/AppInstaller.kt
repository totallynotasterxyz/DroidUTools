// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.example.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

sealed interface InstallResult {
    data object RootSuccess : InstallResult
    data class SessionStarted(val message: String) : InstallResult
    data class Failed(val error: String) : InstallResult
}

sealed interface PackageInstallerEvent {
    data object Success : PackageInstallerEvent
    data class Error(val message: String) : PackageInstallerEvent
}

object AppInstaller {

    private const val ACTION_INSTALL_COMMIT = "com.example.updater.ACTION_INSTALL_COMMIT"

    private val _installerEvents = MutableSharedFlow<PackageInstallerEvent>(extraBufferCapacity = 4)
    val installerEvents: SharedFlow<PackageInstallerEvent> = _installerEvents.asSharedFlow()

    /**
     * Attempts a silent root install first via `pm install -r`.
     * If root install fails, falls back to the Android PackageInstaller session flow.
     */
    suspend fun installApk(
        context: Context,
        apkFile: File
    ): InstallResult = withContext(Dispatchers.IO) {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return@withContext InstallResult.Failed("APK file not found on disk.")
        }

        // Strategy 1: Silent Root Install via pm install -r
        val rootResult = attemptRootInstall(context, apkFile)
        if (rootResult is InstallResult.RootSuccess) {
            cleanUpdateCache(context, keepFile = null)
            return@withContext rootResult
        }

        // Strategy 2: PackageInstaller Session Flow (REQUEST_INSTALL_PACKAGES)
        launchPackageInstallerSession(context, apkFile)
    }

    private suspend fun attemptRootInstall(context: Context, apkFile: File): InstallResult {
        return try {
            val suPath = RootShell.findSuBinary()
                ?: return InstallResult.Failed("Root binary (su) not found.")

            // Ensure the APK file is readable by the Android system package manager
            RootShell.execute("chmod 644 '${apkFile.absolutePath}'")

            // Execute silent replacement install
            val installCmd = "pm install -r '${apkFile.absolutePath}'"
            val cmdResult = RootShell.execute(installCmd, timeoutSec = 60)

            val fullOutput = (cmdResult.stdout + cmdResult.stderr).joinToString(" ")
            if (cmdResult.exitCode == 0 && fullOutput.contains("Success", ignoreCase = true)) {
                // Relaunch the application automatically after a successful root install
                delay(800)
                val relaunchCmd =
                    "am start -n '${context.packageName}/com.example.MainActivity' -a android.intent.action.MAIN -c android.intent.category.LAUNCHER"
                RootShell.execute(relaunchCmd)
                InstallResult.RootSuccess
            } else {
                val errorMsg = cmdResult.stderr.firstOrNull() ?: fullOutput.ifBlank { "Unknown pm install error" }
                InstallResult.Failed("Root pm install failed: $errorMsg")
            }
        } catch (e: Exception) {
            InstallResult.Failed("Root install exception: ${e.message}")
        }
    }

    private fun launchPackageInstallerSession(context: Context, apkFile: File): InstallResult {
        var session: PackageInstaller.Session? = null
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            val sizeBytes = apkFile.length()
            session.openWrite("droidutools_update.apk", 0, sizeBytes).use { out ->
                FileInputStream(apkFile).use { input ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                    session.fsync(out)
                }
            }

            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_COMMIT
            }
            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val statusReceiver = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                pendingIntentFlags
            )

            session.commit(statusReceiver.intentSender)
            InstallResult.SessionStarted("PackageInstaller session started. Confirm installation if prompted.")
        } catch (e: SecurityException) {
            session?.abandon()
            InstallResult.Failed("Install permission required: enable 'Install unknown apps' for DroidUtools.")
        } catch (e: Exception) {
            session?.abandon()
            InstallResult.Failed("PackageInstaller session failed: ${e.message}")
        } finally {
            try {
                session?.close()
            } catch (_: Exception) {}
        }
    }

    internal fun handleInstallBroadcast(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirmIntent?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                cleanUpdateCache(context, keepFile = null)
                _installerEvents.tryEmit(PackageInstallerEvent.Success)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                _installerEvents.tryEmit(PackageInstallerEvent.Error("Installation was cancelled or interrupted."))
            }
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                _installerEvents.tryEmit(PackageInstallerEvent.Error("Installation failed due to insufficient device storage."))
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                _installerEvents.tryEmit(
                    PackageInstallerEvent.Error("Package verification rejected by Android: ${message ?: "signature or version conflict"}")
                )
            }
            else -> {
                _installerEvents.tryEmit(
                    PackageInstallerEvent.Error("Installation interrupted (${message ?: "status code $status"}). Please retry.")
                )
            }
        }
    }

    /**
     * Purges stale update APKs from the app cache.
     */
    fun cleanUpdateCache(context: Context, keepFile: File? = null) {
        try {
            val cacheDir = File(context.cacheDir, "updates")
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (keepFile == null || file.absolutePath != keepFile.absolutePath) {
                        file.delete()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    class InstallResultReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_INSTALL_COMMIT) {
                handleInstallBroadcast(context, intent)
            }
        }
    }
}
