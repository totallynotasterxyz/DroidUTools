// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class JitKeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "droidutools_jit_channel"
        const val NOTIFICATION_ID = 3001
        const val ACTION_START_JIT = "com.example.service.START_JIT"
        const val ACTION_STOP_JIT = "com.example.service.STOP_JIT"
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_PID = "extra_pid"

        fun startKeepAlive(context: Context, appName: String, pid: Int) {
            val intent = Intent(context, JitKeepAliveService::class.java).apply {
                action = ACTION_START_JIT
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_PID, pid)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopKeepAlive(context: Context) {
            val intent = Intent(context, JitKeepAliveService::class.java).apply {
                action = ACTION_STOP_JIT
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_JIT) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: "iOS Application"
        val pid = intent?.getIntExtra(EXTRA_PID, 0) ?: 0

        val notification = createNotification("JIT Active: $appName (PID: $pid) • Debugserver Connected")
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DroidUtools JIT Keep-Alive",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps debugserver tunnel active to prevent JIT revocation"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DroidUtools JIT Session")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
