// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.tools

import android.util.Log
import com.example.usb.SocketChannelAdapter
import com.example.usb.UsbMuxdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class TunnelRule(
    val id: String,
    val label: String,
    val localPort: Int,
    val devicePort: Int,
    val udid: String = "",
    val deviceId: Int = 1,
    val wirelessHost: String? = null,
    val isActive: Boolean = false,
    val activeConnections: Int = 0,
    val bytesTransferred: Long = 0L,
    val lastError: String? = null
)

/**
 * Multi-port TCP forwarder (pure-Kotlin iproxy implementation).
 * Binds localhost ports cleanly and relays bidirectional traffic over Wi-Fi LAN (preferred)
 * or USB usbmuxd sockets to any iOS service port.
 */
class IproxyTunnelManager(
    private val usbMuxdClient: UsbMuxdClient,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DroidUtools_IproxyMgr"
    }

    private val _tunnels = MutableStateFlow(
        listOf(
            TunnelRule("ssh_default", "OpenSSH / Dropbear", localPort = 2222, devicePort = 22),
            TunnelRule("wda_default", "WebDriverAgent Relay", localPort = 8100, devicePort = 8100),
            TunnelRule("lldb_default", "Debugserver / LLDB", localPort = 1234, devicePort = 1234)
        )
    )
    val tunnels: StateFlow<List<TunnelRule>> = _tunnels.asStateFlow()

    private val serverSockets = ConcurrentHashMap<String, ServerSocket>()
    private val acceptJobs = ConcurrentHashMap<String, Job>()

    fun addTunnelRule(
        label: String,
        localPort: Int,
        devicePort: Int,
        deviceId: Int,
        udid: String,
        wirelessHost: String? = null
    ): TunnelRule {
        val rule = TunnelRule(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Port $localPort → $devicePort" },
            localPort = localPort,
            devicePort = devicePort,
            udid = udid,
            deviceId = deviceId,
            wirelessHost = wirelessHost
        )
        _tunnels.value = _tunnels.value + rule
        return rule
    }

    fun removeTunnelRule(id: String) {
        stopTunnel(id)
        _tunnels.value = _tunnels.value.filter { it.id != id }
    }

    fun startTunnel(id: String, deviceId: Int, udid: String, wirelessHost: String? = null) {
        val rule = _tunnels.value.firstOrNull { it.id == id } ?: return
        if (acceptJobs.containsKey(id)) return

        updateRule(id) { it.copy(deviceId = deviceId, udid = udid, wirelessHost = wirelessHost, lastError = null) }

        val job = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(rule.localPort, 16, InetAddress.getByName("127.0.0.1"))
                serverSockets[id] = server
                updateRule(id) { it.copy(isActive = true, lastError = null) }

                while (isActive && !server.isClosed) {
                    val clientSocket = try {
                        server.accept()
                    } catch (_: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClientConnection(id, clientSocket, deviceId, rule.devicePort, wirelessHost)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Tunnel ${rule.localPort}->${rule.devicePort} error: ${e.message}")
                updateRule(id) {
                    it.copy(
                        isActive = false,
                        lastError = e.message ?: "Unable to bind 127.0.0.1:${rule.localPort}"
                    )
                }
            } finally {
                stopTunnelInternal(id)
            }
        }
        acceptJobs[id] = job
    }

    fun stopTunnel(id: String) {
        acceptJobs.remove(id)?.cancel()
        stopTunnelInternal(id)
    }

    fun stopAll() {
        acceptJobs.keys.toList().forEach { stopTunnel(it) }
    }

    fun buildSshCommand(localPort: Int, user: String = "root"): String {
        return "ssh -p $localPort -o StrictHostKeyChecking=no $user@127.0.0.1"
    }

    private suspend fun handleClientConnection(
        ruleId: String,
        localSocket: Socket,
        deviceId: Int,
        devicePort: Int,
        wirelessHost: String?
    ) {
        var muxChannel: SocketChannelAdapter? = null
        updateRule(ruleId) { it.copy(activeConnections = it.activeConnections + 1) }
        try {
            muxChannel = usbMuxdClient.connectToServicePort(deviceId, devicePort, wirelessHost)
            val remoteIn = muxChannel.inputStream
            val remoteOut = muxChannel.outputStream
            val localIn = localSocket.getInputStream()
            val localOut = localSocket.getOutputStream()

            val upstream = scope.launch(Dispatchers.IO) {
                pumpStreams(ruleId, localIn, remoteOut)
            }
            pumpStreams(ruleId, remoteIn, localOut)
            upstream.cancel()
        } catch (e: Exception) {
            updateRule(ruleId) { it.copy(lastError = "Device port $devicePort unreachable: ${e.message}") }
        } finally {
            runCatching { localSocket.close() }
            runCatching { muxChannel?.close() }
            updateRule(ruleId) { it.copy(activeConnections = (it.activeConnections - 1).coerceAtLeast(0)) }
        }
    }

    private fun pumpStreams(ruleId: String, input: InputStream, output: OutputStream) {
        val buf = ByteArray(16 * 1024)
        while (true) {
            val read = try {
                input.read(buf)
            } catch (_: Exception) {
                -1
            }
            if (read <= 0) break
            try {
                output.write(buf, 0, read)
                output.flush()
                updateRule(ruleId) { it.copy(bytesTransferred = it.bytesTransferred + read) }
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun stopTunnelInternal(id: String) {
        serverSockets.remove(id)?.let { runCatching { it.close() } }
        updateRule(id) { it.copy(isActive = false, activeConnections = 0) }
    }

    private fun updateRule(id: String, transform: (TunnelRule) -> TunnelRule) {
        _tunnels.value = _tunnels.value.map { if (it.id == id) transform(it) else it }
    }
}
