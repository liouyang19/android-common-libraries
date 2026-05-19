package com.taisau.android.common.utils.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object NetworkUtils {

    enum class NetworkType {
        NETWORK_WIFI,
        NETWORK_MOBILE,
        NETWORK_ETHERNET,
        NETWORK_BLUETOOTH,
        NETWORK_VPN,
        NETWORK_NONE
    }

    private fun getConnectivityManager(context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    fun isConnected(context: Context): Boolean {
        val cm = getConnectivityManager(context)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = getConnectivityManager(context)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isMobileData(context: Context): Boolean {
        val cm = getConnectivityManager(context)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun getNetworkType(context: Context): NetworkType {
        val cm = getConnectivityManager(context)
        val network = cm.activeNetwork ?: return NetworkType.NETWORK_NONE
        val caps = cm.getNetworkCapabilities(network) ?: return NetworkType.NETWORK_NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.NETWORK_WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.NETWORK_MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.NETWORK_ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> NetworkType.NETWORK_BLUETOOTH
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.NETWORK_VPN
            else -> NetworkType.NETWORK_NONE
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return isConnected(context)
    }

    fun observeNetworkState(context: Context): Flow<Boolean> = callbackFlow {
        val cm = getConnectivityManager(context)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val connected = networkCapabilities.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
                )
                trySend(connected)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        trySend(isConnected(context))
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }
}
