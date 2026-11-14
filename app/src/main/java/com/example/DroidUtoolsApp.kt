// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example

import android.app.Application
import com.example.bridge.IosBridgeRouter
import com.example.data.DeviceDatabase
import com.example.data.DeviceRepository
import com.example.data.PreferencesManager
import com.example.installer.InstallationProxyClient
import com.example.legal.ConsentManager
import com.example.pairing.PairingManager
import com.example.root.RootChecker
import com.example.updater.UpdateManager
import com.example.usb.UsbDeviceWatcher
import com.example.usb.UsbMuxdClient
import com.example.usb.UsbMuxdDaemonManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DroidUtoolsApp : Application() {

    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val database by lazy { DeviceDatabase.getInstance(this) }
    val preferencesManager by lazy { PreferencesManager(this) }
    val consentManager by lazy { ConsentManager(this, database, appScope) }
    val updateManager by lazy {
        UpdateManager(
            context = this,
            consentManager = consentManager,
            scope = appScope
        )
    }
    val rootChecker by lazy { RootChecker() }
    val usbMuxdClient by lazy { UsbMuxdClient() }
    val usbMuxdDaemonManager by lazy { UsbMuxdDaemonManager() }
    val usbDeviceWatcher by lazy { UsbDeviceWatcher(this, appScope) }
    val wirelessDeviceWatcher by lazy { com.example.wireless.WirelessDeviceWatcher(this, appScope) }
    val iosBridgeRouter by lazy { IosBridgeRouter(this, usbMuxdClient) }
    val deviceRepository by lazy {
        DeviceRepository(
            deviceDao = database.deviceDao(),
            pairingRecordDao = database.pairingRecordDao(),
            activityLogDao = database.activityLogDao(),
            bridgeRouter = iosBridgeRouter,
            scope = appScope
        )
    }
    val installationProxyClient by lazy { InstallationProxyClient(usbMuxdClient) }
    val pairingManager by lazy {
        PairingManager(
            context = this,
            pairingRecordDao = database.pairingRecordDao(),
            preferencesManager = preferencesManager,
            usbMuxdClient = usbMuxdClient,
            scope = appScope
        )
    }
    val jitManager by lazy {
        com.example.jit.JitManager(
            context = this,
            usbMuxdClient = usbMuxdClient,
            scope = appScope
        )
    }
    val dvtStreamClient by lazy { com.example.mirror.DvtStreamClient() }
    val wdaClient by lazy { com.example.mirror.WebDriverAgentClient() }
    val certificateVault by lazy { com.example.signer.CertificateVault(this) }
    val ipaSigner by lazy { com.example.signer.IpaSigner(this) }
    val afcFileManager by lazy { com.example.tools.AfcFileManager() }
    val appBackupManager by lazy {
        com.example.tools.AppBackupManager(
            context = this,
            backupDao = database.backupSnapshotDao(),
            afcManager = afcFileManager,
            scope = appScope
        )
    }
    val iproxyTunnelManager by lazy {
        com.example.tools.IproxyTunnelManager(
            usbMuxdClient = usbMuxdClient,
            scope = appScope
        )
    }

    override fun onCreate() {
        super.onCreate()
        usbDeviceWatcher.startWatching()
    }
}
