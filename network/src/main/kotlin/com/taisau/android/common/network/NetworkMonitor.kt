package com.taisau.android.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class NetworkMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _info = MutableStateFlow(NetworkInfo())
    val info: StateFlow<NetworkInfo> = _info.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    val observe: Flow<NetworkInfo> = callbackFlow {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(computeNetworkInfo())
            }

            override fun onLost(network: Network) {
                trySend(computeNetworkInfo())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(computeNetworkInfo())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, cb)
        trySend(computeNetworkInfo())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(cb)
        }
    }.distinctUntilChanged()

    fun start() {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _info.value = computeNetworkInfo()
            }

            override fun onLost(network: Network) {
                _info.value = computeNetworkInfo()
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                _info.value = computeNetworkInfo()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, cb)
        _info.value = computeNetworkInfo()
        callback = cb
    }

    fun stop() {
        callback?.let { connectivityManager.unregisterNetworkCallback(it) }
        callback = null
    }

    fun refresh() {
        _info.value = computeNetworkInfo()
    }

    private fun computeNetworkInfo(): NetworkInfo {
        val network = connectivityManager.activeNetwork ?: return NetworkInfo()
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return NetworkInfo()

        val connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectionType.BLUETOOTH
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            else -> ConnectionType.OTHER
        }
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        return NetworkInfo(
            isConnected = connected,
            type = type,
            isMetered = metered,
        )
    }

    companion object {
        @Volatile
        private var defaultInstance: NetworkMonitor? = null

        fun getInstance(context: Context): NetworkMonitor {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: NetworkMonitor(context).also { defaultInstance = it; it.start() }
            }
        }
    }
}
