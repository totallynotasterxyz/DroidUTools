package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.entities.DeviceEntity
import com.example.installer.DownloadProgress
import com.example.installer.IpaDownloader
import com.example.tools.DeviceControlManager
import com.example.updater.GitHubReleaseClient
import com.example.updater.GitHubReleaseResult
import com.example.usb.DaemonStatus
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DroidUtools", appName)
    }

    @Test
    fun `generateJsonDeviceReport formats valid JSON with live DeviceEntity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DeviceControlManager(context)
        val device = DeviceEntity(
            udid = "00008110-TEST-UDID",
            deviceName = "Test iPhone 14 Pro",
            productType = "iPhone15,2",
            modelNumber = "A2650",
            productVersion = "17.4.1",
            buildVersion = "21E236",
            serialNumber = "C39TEST123",
            ecid = "0x0011223344",
            imei = "350000000000001",
            wifiAddress = "AA:BB:CC:DD:EE:FF",
            bluetoothAddress = "AA:BB:CC:DD:EE:00",
            activePairingMethod = "RP_PAIRING",
            connectionType = "USB",
            isDeveloperModeEnabled = true,
            isActivated = true,
            batteryLevel = 88,
            batteryHealth = 96,
            batteryCycles = 120,
            totalDiskCapacityBytes = 128_000_000_000L,
            freeDiskSpaceBytes = 48_000_000_000L
        )

        val jsonText = manager.generateJsonDeviceReport(
            udid = device.udid,
            device = device,
            appVersion = "1.2.3",
            daemonStatus = DaemonStatus.RUNNING,
            socketType = "TCP (127.0.0.1:27015)"
        )
        val parsed = JSONObject(jsonText)
        assertEquals("DroidUtools v1.2.3", parsed.getString("report_generator"))
        assertEquals("Test iPhone 14 Pro", parsed.getJSONObject("device_info").getString("device_name"))
        assertEquals("RP_PAIRING", parsed.getJSONObject("device_info").getString("pairing_method"))
        assertEquals(88, parsed.getJSONObject("battery").getInt("percentage"))
        assertEquals(80_000_000_000L, parsed.getJSONObject("storage").getLong("used_bytes"))
        assertEquals("RUNNING", parsed.getJSONObject("host_environment").getString("usbmuxd_status"))
    }

    @Test
    fun `GitHubReleaseClient returns Error cleanly when UpdateConfig is placeholder`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = GitHubReleaseClient(context)
        val result = client.fetchReleases()
        assertTrue(result is GitHubReleaseResult.Error)
    }

    @Test
    fun `IpaDownloader rejects non-http URLs with DownloadProgress Error`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val downloader = IpaDownloader(context)
        val events = downloader.downloadIpa("ftp://invalid.example.com/test.ipa", "test.ipa").toList()
        assertTrue(events.lastOrNull() is DownloadProgress.Error)
    }
}
