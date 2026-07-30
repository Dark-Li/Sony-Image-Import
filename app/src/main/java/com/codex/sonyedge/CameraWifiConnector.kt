package com.codex.sonyedge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Requests and owns a local-only connection to a camera Wi-Fi access point. */
class CameraWifiConnector(
    context: Context,
    private val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        ?: throw IllegalStateException("ConnectivityManager is unavailable")
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private var generation = 0
    private var listener: Listener? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var timeoutAction: Runnable? = null
    private var connectedNetwork: Network? = null
    private var closed = false

    init {
        require(timeoutMillis > 0) { "Connection timeout must be greater than zero" }
    }

    fun connect(ssid: String, password: String, listener: Listener) {
        val normalizedSsid = ssid.trim()
        require(normalizedSsid.isNotEmpty()) { "Camera Wi-Fi SSID must not be blank" }

        val token: Int
        synchronized(lock) {
            check(!closed) { "CameraWifiConnector is closed" }
            releaseLocked()
            generation++
            token = generation
            this.listener = listener
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectWithNetworkSpecifier(token, normalizedSsid, password)
        } else {
            connectWithLegacyConfiguration(token, normalizedSsid, password)
        }
    }

    fun cancel() {
        synchronized(lock) {
            generation++
            listener = null
            releaseLocked()
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) {
                return
            }
            closed = true
            generation++
            listener = null
            releaseLocked()
        }
    }

    private fun connectWithNetworkSpecifier(token: Int, ssid: String, password: String) {
        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
        if (password.isNotEmpty()) {
            specifierBuilder.setWpa2Passphrase(password)
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()
        val callback = createNetworkCallback(token) { true }

        synchronized(lock) {
            if (token != generation || closed) {
                return
            }
            networkCallback = callback
        }

        try {
            connectivityManager.requestNetwork(request, callback, timeoutMillis)
        } catch (error: Exception) {
            finishUnavailable(token, error)
        }
    }

    @Suppress("DEPRECATION")
    private fun connectWithLegacyConfiguration(token: Int, ssid: String, password: String) {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            finishUnavailable(token, IllegalStateException("WifiManager is unavailable"))
            return
        }

        val callback = createNetworkCallback(token) {
            currentSsid(wifiManager) == ssid
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        synchronized(lock) {
            if (token != generation || closed) {
                return
            }
            networkCallback = callback
        }

        try {
            connectivityManager.registerNetworkCallback(request, callback)

            val configuration = WifiConfiguration().apply {
                SSID = quoteWifiValue(ssid)
                if (password.isEmpty()) {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                } else {
                    preSharedKey = quoteWifiValue(password)
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                }
            }
            val existing = wifiManager.configuredNetworks
                ?.firstOrNull { unquoteWifiValue(it.SSID) == ssid }
            val networkId = if (existing == null) {
                wifiManager.addNetwork(configuration)
            } else {
                configuration.networkId = existing.networkId
                wifiManager.updateNetwork(configuration).takeIf { it >= 0 } ?: existing.networkId
            }
            if (networkId < 0) {
                finishUnavailable(token, IllegalStateException("Android rejected Wi-Fi configuration"))
                return
            }

            if (!wifiManager.enableNetwork(networkId, true) || !wifiManager.reconnect()) {
                finishUnavailable(token, IllegalStateException("Android rejected Wi-Fi connection"))
                return
            }
            scheduleLegacyTimeout(token)
        } catch (error: Exception) {
            finishUnavailable(token, error)
        }
    }

    private fun createNetworkCallback(
        token: Int,
        acceptsNetwork: (Network) -> Boolean,
    ): ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (!acceptsNetwork(network)) {
                return
            }
            handleConnected(token, network)
        }

        override fun onUnavailable() {
            finishUnavailable(token, null)
        }

        override fun onLost(network: Network) {
            handleLost(token, network)
        }
    }

    private fun handleConnected(token: Int, network: Network) {
        val currentListener: Listener
        synchronized(lock) {
            if (token != generation || closed || connectedNetwork == network) {
                return
            }
            timeoutAction?.let(mainHandler::removeCallbacks)
            timeoutAction = null
            try {
                CameraWifiBinding.setPreferredNetwork(appContext, network)
            } catch (error: Exception) {
                finishUnavailable(token, error)
                return
            }
            connectedNetwork = network
            currentListener = listener ?: return
        }
        postEvent(token, currentListener, Event.Connected(network))
    }

    private fun handleLost(token: Int, network: Network) {
        val currentListener: Listener
        synchronized(lock) {
            if (token != generation || connectedNetwork != network) {
                return
            }
            currentListener = listener ?: return
            CameraWifiBinding.clearPreferredNetwork(network)
            generation++
            listener = null
            releaseLocked()
        }
        mainHandler.post { currentListener.onEvent(Event.Lost) }
    }

    private fun finishUnavailable(token: Int, cause: Throwable?) {
        val currentListener: Listener
        synchronized(lock) {
            if (token != generation) {
                return
            }
            currentListener = listener ?: return
            generation++
            listener = null
            releaseLocked()
        }
        mainHandler.post { currentListener.onEvent(Event.Unavailable(cause)) }
    }

    private fun postEvent(token: Int, target: Listener, event: Event) {
        mainHandler.post {
            val shouldDeliver = synchronized(lock) {
                token == generation && listener === target && !closed
            }
            if (shouldDeliver) {
                target.onEvent(event)
            }
        }
    }

    private fun scheduleLegacyTimeout(token: Int) {
        val action = Runnable { finishUnavailable(token, null) }
        synchronized(lock) {
            if (token != generation || connectedNetwork != null) {
                return
            }
            timeoutAction = action
        }
        mainHandler.postDelayed(action, timeoutMillis.toLong())
    }

    private fun releaseLocked() {
        timeoutAction?.let(mainHandler::removeCallbacks)
        timeoutAction = null

        connectedNetwork?.let(CameraWifiBinding::clearPreferredNetwork)
        connectedNetwork = null

        networkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        networkCallback = null
    }

    @Suppress("DEPRECATION")
    private fun currentSsid(wifiManager: WifiManager): String? =
        unquoteWifiValue(wifiManager.connectionInfo?.ssid)

    private fun quoteWifiValue(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun unquoteWifiValue(value: String?): String? {
        if (value == null || value == WifiManager.UNKNOWN_SSID) {
            return null
        }
        return value.removePrefix("\"").removeSuffix("\"")
    }

    fun interface Listener {
        fun onEvent(event: Event)
    }

    sealed interface Event {
        data class Connected(val network: Network) : Event
        data class Unavailable(val cause: Throwable? = null) : Event
        data object Lost : Event
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000
    }
}
