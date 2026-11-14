// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class UpdateDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "droidutools_updates_channel"
        const val AVAILABLE_CHANNEL_ID = "droidutools_update_available_channel"
        const val NOTIFICATION_ID = 9102
        const val AVAILABLE_NOTIFICATION_ID = 9103

        const val ACTION_START_DOWNLOAD = "com.example.updater.ACTION_START_DOWNLOAD"
        const val ACTION_UPDATE_PROGRESS = "com.example.updater.ACTION_UPDATE_PROGRESS"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.updater.ACTION_CANCEL_DOWNLOAD"
        const val ACTION_STOP_SERVICE = "com.example.updater.ACTION_STOP_SERVICE"

        const val EXTRA_VERSION_NAME = "extra_version_name"
        const val EXTRA_PROGRESS_PERCENT = "extra_progress_percent"
        const val EXTRA_SPEED_TEXT = "extra_speed_text"
        const val EXTRA_ETA_TEXT = "extra_eta_text"

        private val _cancelEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val cancelEvents: SharedFlow<Unit> = _cancelEvents.asSharedFlow()

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val downloadChannel = NotificationChannel(
                    CHANNEL_ID,
                    "App Update Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress of DroidUtools background APK downloads"
                    setShowBadge(false)
                }

                val availableChannel = NotificationChannel(
                    AVAILABLE_CHANNEL_ID,
                    "Update available",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when a new DroidUtools release is available"
                    setShowBadge(true)
                }

                nm.createNotificationChannel(downloadChannel)
                nm.createNotificationChannel(availableChannel)
            }
        }

        fun notifyUpdateAvailable(context: Context, release: ReleaseInfo) {
            ensureNotificationChannels(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                AVAILABLE_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, AVAILABLE_CHANNEL_ID)
                .setContentTitle("Update available: DroidUtools v${release.versionName}")
                .setContentText("Tap to review release notes and install (${release.channel.displayName}).")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(AVAILABLE_NOTIFICATION_ID, notification)
        }

        fun start(context: Context, versionName: String) {
            ensureNotificationChannels(context)
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_VERSION_NAME, versionName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateProgress(context: Context, versionName: String, percent: Int, speed: String, eta: String) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_VERSION_NAME, versionName)
                putExtra(EXTRA_PROGRESS_PERCENT, percent)
                putExtra(EXTRA_SPEED_TEXT, speed)
                putExtra(EXTRA_ETA_TEXT, eta)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, UpdateDownloadService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }
    }

    private var activeVersionName = "Update"

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                activeVersionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: "Update"
                startForegroundWithNotification(0, "Starting...", "")
            }
            ACTION_UPDATE_PROGRESS -> {
                val percent = intent.getIntExtra(EXTRA_PROGRESS_PERCENT, 0)
                val speed = intent.getStringExtra(EXTRA_SPEED_TEXT) ?: ""
                val eta = intent.getStringExtra(EXTRA_ETA_TEXT) ?: ""
                activeVersionName = intent.getStringExtra(EXTRA_VERSION_NAME) ?: activeVersionName
                updateNotification(percent, speed, eta)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                _cancelEvents.tryEmit(Unit)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification(percent: Int, speed: String, eta: String) {
        val notification = buildNotification(percent, speed, eta)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(percent: Int, speed: String, eta: String) {
        val notification = buildNotification(percent, speed, eta)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(percent: Int, speed: String, eta: String): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, UpdateDownloadService::class.java).apply {
            action = ACTION_CANCEL_DOWNLOAD
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (speed.isNotBlank() && eta.isNotBlank()) {
            "$speed • $eta"
        } else {
            "Downloading..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading DroidUtools v$activeVersionName")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
