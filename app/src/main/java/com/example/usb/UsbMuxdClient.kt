// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.usb

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-performance Usbmuxd Client implementing the Apple usbmuxd socket protocol.
 * Handles device discovery, multiplexed port connections (lockdown, debugserver, AFC),
 * and pairing record management.
 */
class UsbMuxdClient(
    private val host: String = "127.0.0.1",
    private val tcpPort: Int = 27015
) {
    companion object {
        private const val TAG = "DroidUtools_UsbMuxClient"
        const val LOCKDOWN_PORT = 62078
    }

    private var currentTag = 1

    private fun nextTag(): Int = synchronized(this) { currentTag++ }

    private fun createSocket(): SocketChannelAdapter {
        // Try Unix Domain Socket first
        for (path in UsbMuxdDaemonManager.SOCKET_PATHS) {
            try {
                val localSocket = LocalSocket()
                localSocket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                return SocketChannelAdapter.Unix(localSocket)
            } catch (e: Exception) {
                // Ignore and try TCP
            }
        }

        // Fallback to TCP localhost
        val tcpSocket = Socket()
        tcpSocket.connect(InetSocketAddress(host, tcpPort), 2000)
        return SocketChannelAdapter.Tcp(tcpSocket)
    }

    /**
     * Converts a 16-bit port number to network byte order (big-endian) integer
     * as required by the usbmuxd Connect command.
     */
    private fun toNetworkPort(port: Int): Int {
        val buffer = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(port.toShort())
        val shortVal = ByteBuffer.wrap(buffer.array()).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
        return shortVal and 0xFFFF
    }

    /**
     * Sends the Listen command to stream device attach / detach events.
     */
    fun createListenPacket(): UsbMuxdPacket {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>BundleID</key><string>com.aistudio.droidutools</string>
                <key>ClientVersionString</key><string>droidutools-1.0</string>
                <key>MessageType</key><string>Listen</string>
                <key>ProgName</key><string>DroidUtools</string>
            </dict>
            </plist>
        """.trimIndent()
        return UsbMuxdPacket.createPlistPacket(nextTag(), xml)
    }

    /**
     * Creates a Connect packet to tunnel to an internal iOS service port.
     */
    fun createConnectPacket(deviceId: Int, port: Int): UsbMuxdPacket {
        val netPort = toNetworkPort(port)
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>BundleID</key><string>com.aistudio.droidutools</string>
                <key>ClientVersionString</key><string>droidutools-1.0</string>
                <key>MessageType</key><string>Connect</string>
                <key>ProgName</key><string>DroidUtools</string>
                <key>DeviceID</key><integer>$deviceId</integer>
                <key>PortNumber</key><integer>$netPort</integer>
            </dict>
            </plist>
        """.trimIndent()
        return UsbMuxdPacket.createPlistPacket(nextTag(), xml)
    }

    /**
     * Connects to a multiplexed port on the device and returns the active bidirectional channel.
     */
    suspend fun connectToDevicePort(deviceId: Int, port: Int): SocketChannelAdapter = withContext(Dispatchers.IO) {
        val channel = createSocket()
        val packet = createConnectPacket(deviceId, port)
        packet.writeToStream(channel.outputStream)

        val response = UsbMuxdPacket.readFromStream(channel.inputStream)
        if (response == null) {
            channel.close()
            throw IllegalStateException("Usbmuxd closed socket without response to Connect")
        }

        val respStr = response.getPayloadString()
        Log.d(TAG, "Connect response payload: $respStr")
        // Check for success (Number 0 in Result)
        if (respStr.contains("<key>Number</key><integer>0</integer>") || response.type == UsbMuxdPacket.TYPE_RESULT) {
            return@withContext channel
        }

        channel.close()
        throw IllegalStateException("Failed to connect to device port $port: $respStr")
    }

    /**
     * Unified Wi-Fi-first service transport resolver:
     * Uses direct LAN TCP whenever wirelessHost is reachable on the same network,
     * falling back seamlessly to USB usbmuxd multiplexed sockets.
     */
    suspend fun connectToServicePort(
        deviceId: Int,
        port: Int,
        wirelessHost: String? = null,
        wirelessPort: Int = LOCKDOWN_PORT
    ): SocketChannelAdapter = withContext(Dispatchers.IO) {
        if (!wirelessHost.isNullOrBlank()) {
            val targetPort = if (port == LOCKDOWN_PORT) wirelessPort else port
            try {
                val tcpSocket = Socket()
                tcpSocket.connect(InetSocketAddress(wirelessHost, targetPort), 2200)
                return@withContext SocketChannelAdapter.Tcp(tcpSocket)
            } catch (e: Exception) {
                Log.d(TAG, "Wi-Fi connect ($wirelessHost:$targetPort) failed, falling back to USB: ${e.message}")
            }
        }
        connectToDevicePort(deviceId, port)
    }

    /**
     * Generates a ReadPairRecord request.
     */
    fun createReadPairRecordPacket(udid: String): UsbMuxdPacket {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>BundleID</key><string>com.aistudio.droidutools</string>
                <key>ClientVersionString</key><string>droidutools-1.0</string>
                <key>MessageType</key><string>ReadPairRecord</string>
                <key>PairRecordID</key><string>$udid</string>
                <key>ProgName</key><string>DroidUtools</string>
            </dict>
            </plist>
        """.trimIndent()
        return UsbMuxdPacket.createPlistPacket(nextTag(), xml)
    }

    /**
     * Sends Apple Lockdown SetValue for Domain=com.apple.mobile.wireless_lockdown,
     * Key=EnableWifiConnections so the paired iOS device advertises port 62078 over Wi-Fi.
     */
    suspend fun setWifiSyncEnabled(
        deviceId: Int,
        enabled: Boolean,
        wirelessHost: String? = null,
        wirelessPort: Int = LOCKDOWN_PORT
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        var channel: SocketChannelAdapter? = null
        try {
            channel = connectToServicePort(deviceId, LOCKDOWN_PORT, wirelessHost, wirelessPort)
            val boolTag = if (enabled) "<true/>" else "<false/>"
            val plist = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Domain</key><string>com.apple.mobile.wireless_lockdown</string>
                    <key>Key</key><string>EnableWifiConnections</string>
                    <key>Label</key><string>DroidUtools</string>
                    <key>Request</key><string>SetValue</string>
                    <key>Value</key>$boolTag
                </dict>
                </plist>
            """.trimIndent().toByteArray(Charsets.UTF_8)

            val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(plist.size).array()
            channel.outputStream.write(header)
            channel.outputStream.write(plist)
            channel.outputStream.flush()

            val respHeader = ByteArray(4)
            val readHeader = channel.inputStream.read(respHeader)
            if (readHeader == 4) {
                val respLen = ByteBuffer.wrap(respHeader).order(ByteOrder.BIG_ENDIAN).int
                if (respLen in 1..262_144) {
                    val buf = ByteArray(respLen)
                    var total = 0
                    while (total < respLen) {
                        val r = channel.inputStream.read(buf, total, respLen - total)
                        if (r <= 0) break
                        total += r
                    }
                    val respXml = String(buf, 0, total, Charsets.UTF_8)
                    val ok = !respXml.contains("<key>Error</key>")
                    return@withContext Result.success(ok)
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.d(TAG, "Lockdown wireless toggle over socket unavailable: ${e.message}")
            Result.failure(e)
        } finally {
            channel?.close()
        }
    }

    /**
     * Connects to iOS lockdownd (port 62078) via Wi-Fi LAN (preferred) or USB usbmuxd
     * and queries basic device identity fields.
     */
    suspend fun queryLockdownBasicInfo(
        deviceId: Int,
        wirelessHost: String? = null,
        wirelessPort: Int = LOCKDOWN_PORT
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, String>()
        var channel: SocketChannelAdapter? = null
        try {
            channel = connectToServicePort(deviceId, LOCKDOWN_PORT, wirelessHost, wirelessPort)
            val getValuePlist = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key><string>DroidUtools</string>
                    <key>Request</key><string>GetValue</string>
                </dict>
                </plist>
            """.trimIndent().toByteArray(Charsets.UTF_8)

            // Lockdown protocol frames payloads with a 4-byte big-endian length header
            val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(getValuePlist.size).array()
            channel.outputStream.write(header)
            channel.outputStream.write(getValuePlist)
            channel.outputStream.flush()

            val respHeader = ByteArray(4)
            val readHeader = channel.inputStream.read(respHeader)
            if (readHeader == 4) {
                val respLen = ByteBuffer.wrap(respHeader).order(ByteOrder.BIG_ENDIAN).int
                if (respLen in 1..1_048_576) {
                    val payload = ByteArray(respLen)
                    var total = 0
                    while (total < respLen) {
                        val r = channel.inputStream.read(payload, total, respLen - total)
                        if (r <= 0) break
                        total += r
                    }
                    val xml = String(payload, 0, total, Charsets.UTF_8)
                    val keys = listOf(
                        "DeviceName", "ProductType", "ProductVersion", "BuildVersion",
                        "SerialNumber", "UniqueDeviceID", "WiFiAddress", "BluetoothAddress",
                        "ModelNumber", "ActivationState"
                    )
                    keys.forEach { key ->
                        val regex = Regex("<key>\\s*$key\\s*</key>\\s*<string>([^<]+)</string>")
                        regex.find(xml)?.groupValues?.getOrNull(1)?.trim()?.let { value ->
                            result[key] = value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Lockdown probe unavailable on deviceId=$deviceId: ${e.message}")
        } finally {
            channel?.close()
        }
        result
    }
}

/**
 * Polymorphic wrapper over Unix Domain Sockets and standard TCP Sockets.
 */
sealed class SocketChannelAdapter : AutoCloseable {
    abstract val inputStream: InputStream
    abstract val outputStream: OutputStream
    abstract val isConnected: Boolean

    class Unix(val localSocket: LocalSocket) : SocketChannelAdapter() {
        override val inputStream: InputStream get() = localSocket.inputStream
        override val outputStream: OutputStream get() = localSocket.outputStream
        override val isConnected: Boolean get() = localSocket.isConnected
        override fun close() {
            try { localSocket.close() } catch (e: Exception) {}
        }
    }

    class Tcp(val socket: Socket) : SocketChannelAdapter() {
        override val inputStream: InputStream get() = socket.getInputStream()
        override val outputStream: OutputStream get() = socket.getOutputStream()
        override val isConnected: Boolean get() = socket.isConnected && !socket.isClosed
        override fun close() {
            try { socket.close() } catch (e: Exception) {}
        }
    }
}
