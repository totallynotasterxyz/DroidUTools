// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Aster <spaminhaler1276@gmail.com>

package com.example.wireless

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

enum class WirelessReachability {
    PROBING,
    REACHABLE,
    OFFLINE
}

data class WirelessDiscoveredDevice(
    val serviceName: String,
    val hostIp: String,
    val port: Int = 62078,
    val udidHint: String = "",
    val wifiMacHint: String = "",
    val reachability: WirelessReachability = WirelessReachability.PROBING,
    val latencyMs: Long = -1L,
    val isManual: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

/**
 * Discovers iOS devices on the local Wi-Fi LAN using Android NsdManager (_apple-mobdev2._tcp and
 * _remotepairing._tcp) plus manual IPv4/IPv6 endpoint addition and TCP port-62078 reachability probing.
 */
class WirelessDeviceWatcher(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "DroidUtools_WifiWatcher"
        const val SERVICE_MOBDEV2 = "_apple-mobdev2._tcp."
        const val SERVICE_REMOTEPAIRING = "_remotepairing._tcp."
        const val DEFAULT_LOCKDOWN_PORT = 62078
    }

    private val nsdManager: NsdManager? = runCatching {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }.getOrNull()

    private val wifiManager: WifiManager? = runCatching {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }.getOrNull()

    private var multicastLock: WifiManager.MulticastLock? = null
    private val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()

    private val _wirelessDevices = MutableStateFlow<List<WirelessDiscoveredDevice>>(emptyList())
    val wirelessDevices: StateFlow<List<WirelessDiscoveredDevice>> = _wirelessDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>("Wireless standby — tap Scan Wi-Fi or add an IP")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun startScanning() {
        if (_isScanning.value) return
        val mgr = nsdManager
        if (mgr == null) {
            _statusMessage.value = "NsdManager unavailable on this host; use Manual IP connect."
            return
        }

        acquireMulticastLock()
        _isScanning.value = true
        _statusMessage.value = "Scanning LAN mDNS (_apple-mobdev2 & _remotepairing)..."

        listOf(SERVICE_MOBDEV2, SERVICE_REMOTEPAIRING).forEach { serviceType ->
            val listener = createDiscoveryListener(mgr, serviceType)
            runCatching {
                mgr.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                synchronized(activeListeners) { activeListeners += listener }
            }.onFailure { err ->
                Log.w(TAG, "Failed starting discovery for $serviceType: ${err.message}")
            }
        }
    }

    fun stopScanning() {
        val mgr = nsdManager
        synchronized(activeListeners) {
            activeListeners.forEach { listener ->
                runCatching { mgr?.stopServiceDiscovery(listener) }
            }
            activeListeners.clear()
        }
        releaseMulticastLock()
        _isScanning.value = false
        _statusMessage.value = if (_wirelessDevices.value.isNotEmpty()) {
            "Found ${_wirelessDevices.value.size} wireless iOS endpoint(s)"
        } else {
            "Wireless scan paused"
        }
    }

    fun rescan() {
        stopScanning()
        startScanning()
        scope.launch { refreshReachability() }
    }

    suspend fun addManualEndpoint(
        hostInput: String,
        port: Int = DEFAULT_LOCKDOWN_PORT,
        udidHint: String = "",
        label: String = ""
    ): Result<WirelessDiscoveredDevice> = withContext(Dispatchers.IO) {
        val host = hostInput.trim()
        if (host.isBlank() || host.contains(" ")) {
            return@withContext Result.failure(IllegalArgumentException("Enter a valid LAN IP address or hostname."))
        }
        if (port !in 1..65535) {
            return@withContext Result.failure(IllegalArgumentException("Port must be between 1 and 65535."))
        }

        val latency = probeTcpEndpoint(host, port)
        val entry = WirelessDiscoveredDevice(
            serviceName = label.ifBlank { "Manual Wi-Fi ($host)" },
            hostIp = host,
            port = port,
            udidHint = udidHint.trim(),
            reachability = if (latency != null) WirelessReachability.REACHABLE else WirelessReachability.OFFLINE,
            latencyMs = latency ?: -1L,
            isManual = true,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        upsertWirelessDevice(entry)
        _statusMessage.value = if (latency != null) {
            "✓ Reachable wireless iOS endpoint at $host:$port (${latency}ms)"
        } else {
            "Saved $host:$port (currently offline — ensure Wi-Fi Sync is enabled over USB)"
        }
        Result.success(entry)
    }

    suspend fun refreshReachability() = withContext(Dispatchers.IO) {
        val snapshot = _wirelessDevices.value
        if (snapshot.isEmpty()) return@withContext
        val updated = snapshot.map { dev ->
            val rtt = probeTcpEndpoint(dev.hostIp, dev.port)
            dev.copy(
                reachability = if (rtt != null) WirelessReachability.REACHABLE else WirelessReachability.OFFLINE,
                latencyMs = rtt ?: -1L,
                lastSeenTimestamp = if (rtt != null) System.currentTimeMillis() else dev.lastSeenTimestamp
            )
        }
        _wirelessDevices.value = updated
    }

    fun probeTcpEndpoint(host: String, port: Int, timeoutMs: Int = 1800): Long? {
        return try {
            val start = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } catch (_: Exception) {
            null
        }
    }

    fun clearAll() {
        stopScanning()
        _wirelessDevices.value = emptyList()
    }

    internal fun parseServiceIdentity(serviceName: String): Pair<String, String> {
        val trimmed = serviceName.trim()
        val macRegex = Regex("([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})")
        val mac = macRegex.find(trimmed)?.groupValues?.getOrNull(1)?.lowercase() ?: ""
        val udidRegex = Regex("([0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}|[0-9A-Fa-f]{40}|[0-9A-Fa-f]{24})")
        val udid = udidRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
        return mac to udid
    }

    private fun upsertWirelessDevice(entry: WirelessDiscoveredDevice) {
        val current = _wirelessDevices.value.toMutableList()
        val existingIdx = current.indexOfFirst {
            it.hostIp.equals(entry.hostIp, ignoreCase = true) && it.port == entry.port
        }
        if (existingIdx >= 0) {
            val prev = current[existingIdx]
            current[existingIdx] = entry.copy(
                udidHint = entry.udidHint.ifBlank { prev.udidHint },
                wifiMacHint = entry.wifiMacHint.ifBlank { prev.wifiMacHint },
                isManual = entry.isManual || prev.isManual
            )
        } else {
            current.add(0, entry)
        }
        _wirelessDevices.value = current
    }

    private fun createDiscoveryListener(mgr: NsdManager, serviceType: String): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS discovery started: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveServiceCompat(mgr, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val (mac, udid) = parseServiceIdentity(serviceInfo.serviceName)
                _wirelessDevices.value = _wirelessDevices.value.map { dev ->
                    if (dev.serviceName == serviceInfo.serviceName ||
                        (mac.isNotBlank() && dev.wifiMacHint == mac) ||
                        (udid.isNotBlank() && dev.udidHint == udid)
                    ) {
                        dev.copy(reachability = WirelessReachability.OFFLINE, latencyMs = -1L)
                    } else {
                        dev
                    }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS start failed for $serviceType code=$errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveServiceCompat(mgr: NsdManager, info: NsdServiceInfo) {
        runCatching {
            mgr.resolveService(
                info,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.d(TAG, "Resolve failed (${serviceInfo.serviceName}): $errorCode")
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val hostAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            resolved.hostAddresses.firstOrNull { it.address.size == 4 }?.hostAddress
                                ?: resolved.hostAddresses.firstOrNull()?.hostAddress
                        } else {
                            resolved.host?.hostAddress
                        } ?: return

                        val port = resolved.port.takeIf { it in 1..65535 } ?: DEFAULT_LOCKDOWN_PORT
                        val (mac, udid) = parseServiceIdentity(resolved.serviceName)

                        scope.launch(Dispatchers.IO) {
                            val rtt = probeTcpEndpoint(hostAddress, port)
                            upsertWirelessDevice(
                                WirelessDiscoveredDevice(
                                    serviceName = resolved.serviceName,
                                    hostIp = hostAddress,
                                    port = port,
                                    udidHint = udid,
                                    wifiMacHint = mac,
                                    reachability = if (rtt != null) WirelessReachability.REACHABLE else WirelessReachability.OFFLINE,
                                    latencyMs = rtt ?: -1L,
                                    isManual = false
                                )
                            )
                        }
                    }
                }
            )
        }
    }

    private fun acquireMulticastLock() {
        runCatching {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("DroidUtools_mDNS")?.apply {
                    setReferenceCounted(false)
                }
            }
            multicastLock?.acquire()
        }
    }

    private fun releaseMulticastLock() {
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
    }
}
