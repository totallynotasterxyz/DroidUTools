// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.IosBridgeRouter
import com.example.data.DeviceDatabase
import com.example.data.DeviceRepository
import com.example.data.entities.DeviceEntity
import com.example.tools.IproxyTunnelManager
import com.example.usb.UsbMuxdClient
import com.example.wireless.WirelessDeviceWatcher
import com.example.wireless.WirelessDiscoveredDevice
import com.example.wireless.WirelessReachability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WirelessModeTest {

    @Test
    fun `WirelessDeviceWatcher parses MAC and UDID hints from Bonjour service names`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val watcher = WirelessDeviceWatcher(context, scope)

        val (mac1, udid1) = watcher.parseServiceIdentity("aa:bb:cc:dd:ee:ff@fe80::1")
        assertEquals("aa:bb:cc:dd:ee:ff", mac1)
        assertEquals("", udid1)

        val (mac2, udid2) = watcher.parseServiceIdentity("00008110-0011223344556677@192.168.1.55")
        assertEquals("", mac2)
        assertEquals("00008110-0011223344556677", udid2)

        val (mac3, udid3) = watcher.parseServiceIdentity("DC:A6:32:10:20:30-0123456789ABCDEF0123456789ABCDEF01234567")
        assertEquals("dc:a6:32:10:20:30", mac3)
        assertEquals("0123456789ABCDEF0123456789ABCDEF01234567", udid3)

        scope.cancel()
    }

    @Test
    fun `WirelessDeviceWatcher validates manual IP inputs and probes live TCP endpoints`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val watcher = WirelessDeviceWatcher(context, scope)

            val invalidHost = watcher.addManualEndpoint("bad host with spaces", 62078)
            assertTrue(invalidHost.isFailure)

            val invalidPort = watcher.addManualEndpoint("127.0.0.1", 70000)
            assertTrue(invalidPort.isFailure)

            val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
            val livePort = server.localPort
            val acceptThread = thread {
                runCatching { server.accept().close() }
            }

            val reachableRes = watcher.addManualEndpoint(
                hostInput = "127.0.0.1",
                port = livePort,
                udidHint = "00008120-TESTWIRELESS",
                label = "Studio Bench iPhone"
            ).getOrThrow()

            acceptThread.join(1000)
            server.close()

            assertEquals("127.0.0.1", reachableRes.hostIp)
            assertEquals(livePort, reachableRes.port)
            assertEquals(WirelessReachability.REACHABLE, reachableRes.reachability)
            assertTrue(reachableRes.latencyMs >= 1L)
            assertTrue(reachableRes.isManual)

            val offlineRes = watcher.addManualEndpoint("127.0.0.1", livePort).getOrThrow()
            assertEquals(WirelessReachability.OFFLINE, offlineRes.reachability)
            assertEquals(-1L, offlineRes.latencyMs)

            watcher.clearAll()
            assertTrue(watcher.wirelessDevices.value.isEmpty())
            scope.cancel()
        }
    }

    @Test
    fun `UsbMuxdClient prefers Wi-Fi host and sends EnableWifiConnections lockdown XML`() {
        runBlocking {
            val muxClient = UsbMuxdClient()
            val capturedXml = StringBuilder()

            val server = ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"))
            val port = server.localPort
            val serverThread = thread {
                runCatching {
                    val client = server.accept()
                    val input = client.getInputStream()
                    val header = ByteArray(4)
                    val n = input.read(header)
                    if (n == 4) {
                        val len = ((header[0].toInt() and 0xFF) shl 24) or
                            ((header[1].toInt() and 0xFF) shl 16) or
                            ((header[2].toInt() and 0xFF) shl 8) or
                            (header[3].toInt() and 0xFF)
                        val body = ByteArray(len)
                        var read = 0
                        while (read < len) {
                            val r = input.read(body, read, len - read)
                            if (r <= 0) break
                            read += r
                        }
                        capturedXml.append(String(body, 0, read, Charsets.UTF_8))

                        val replyXml = """
                            <?xml version="1.0" encoding="UTF-8"?>
                            <plist version="1.0"><dict><key>Request</key><string>SetValue</string><key>Error</key><string></string></dict></plist>
                        """.trimIndent().toByteArray(Charsets.UTF_8)
                        val out = client.getOutputStream()
                        out.write(
                            byteArrayOf(
                                ((replyXml.size shr 24) and 0xFF).toByte(),
                                ((replyXml.size shr 16) and 0xFF).toByte(),
                                ((replyXml.size shr 8) and 0xFF).toByte(),
                                (replyXml.size and 0xFF).toByte()
                            )
                        )
                        out.write(replyXml)
                        out.flush()
                    }
                    client.close()
                }
            }

            val result = muxClient.setWifiSyncEnabled(
                deviceId = 1,
                enabled = true,
                wirelessHost = "127.0.0.1",
                wirelessPort = port
            )
            serverThread.join(1500)
            server.close()

            assertTrue(result.isSuccess)
            val sent = capturedXml.toString()
            assertTrue(sent.contains("<string>SetValue</string>"))
            assertTrue(sent.contains("com.apple.mobile.wireless_lockdown"))
            assertTrue(sent.contains("EnableWifiConnections"))
            assertTrue(sent.contains("<true/>"))
        }
    }

    @Test
    fun `DeviceRepository matches wireless endpoints to saved devices and prioritizes Wi-Fi routing`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, DeviceDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            val muxClient = UsbMuxdClient()
            val bridge = IosBridgeRouter(context, muxClient)
            val repo = DeviceRepository(
                deviceDao = db.deviceDao(),
                pairingRecordDao = db.pairingRecordDao(),
                activityLogDao = db.activityLogDao(),
                bridgeRouter = bridge,
                scope = scope
            )

            val saved = DeviceEntity(
                udid = "00008120-0011223344556677",
                deviceName = "Aster iPhone 15 Pro",
                productType = "iPhone16,1",
                modelNumber = "A2848",
                productVersion = "17.5",
                buildVersion = "21F79",
                serialNumber = "G6C1234567",
                ecid = "0x1234",
                imei = "350000",
                wifiAddress = "aa:bb:cc:11:22:33",
                bluetoothAddress = "aa:bb:cc:11:22:34",
                activePairingMethod = "RP_PAIRING",
                connectionType = "USB",
                isDeveloperModeEnabled = true,
                isActivated = true,
                batteryLevel = 95,
                batteryHealth = 99,
                batteryCycles = 42,
                totalDiskCapacityBytes = 256_000_000_000L,
                freeDiskSpaceBytes = 120_000_000_000L
            )
            db.deviceDao().insertOrUpdateDevice(saved)

            val offlineEndpoint = WirelessDiscoveredDevice(
                serviceName = "aa:bb:cc:11:22:33@192.168.1.99",
                hostIp = "192.168.1.99",
                port = 62078,
                wifiMacHint = "aa:bb:cc:11:22:33",
                reachability = WirelessReachability.OFFLINE
            )
            assertNull(repo.resolveWirelessEndpoint(saved, listOf(offlineEndpoint)))

            val liveEndpoint = offlineEndpoint.copy(
                reachability = WirelessReachability.REACHABLE,
                latencyMs = 4L
            )
            val resolved = repo.resolveWirelessEndpoint(saved, listOf(liveEndpoint))
            assertNotNull(resolved)
            assertEquals("192.168.1.99", resolved?.hostIp)

            repo.syncWirelessDevices(listOf(liveEndpoint))
            val updated = db.deviceDao().getDeviceByUdid(saved.udid)
            assertNotNull(updated)
            assertEquals("NETWORK", updated?.connectionType)
            assertEquals("192.168.1.99", updated?.lastKnownIp)
            assertTrue(updated?.isWifiSyncEnabled == true)

            repo.enableWifiSyncForDevice(saved.udid, 1, enabled = false)
            val disabled = db.deviceDao().getDeviceByUdid(saved.udid)
            assertTrue(disabled?.isWifiSyncEnabled == false)

            val manualNew = WirelessDiscoveredDevice(
                serviceName = "iPad Bench",
                hostIp = "192.168.1.210",
                port = 62078,
                reachability = WirelessReachability.OFFLINE,
                isManual = true
            )
            repo.syncWirelessDevices(listOf(manualNew))
            val manualInserted = db.deviceDao().getDeviceByUdid("WIFI-192.168.1.210")
            assertNotNull(manualInserted)
            assertEquals("iPad Bench", manualInserted?.deviceName)

            val logs = db.activityLogDao().getRecentLogs(10).first()
            assertTrue(logs.any { it.tag == "WIFI_SYNC" })

            db.close()
            scope.cancel()
        }
    }

    @Test
    fun `IproxyTunnelManager records wirelessHost preference for Wi-Fi-first forwarding rules`() {
        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val manager = IproxyTunnelManager(UsbMuxdClient(), scope)

        val rule = manager.addTunnelRule(
            label = "Wi-Fi SSH",
            localPort = 2299,
            devicePort = 22,
            deviceId = 1,
            udid = "WIFI-192.168.1.88",
            wirelessHost = "192.168.1.88"
        )
        assertEquals("192.168.1.88", rule.wirelessHost)
        assertTrue(manager.tunnels.value.any { it.id == rule.id && it.wirelessHost == "192.168.1.88" })

        manager.stopAll()
        scope.cancel()
    }
}
