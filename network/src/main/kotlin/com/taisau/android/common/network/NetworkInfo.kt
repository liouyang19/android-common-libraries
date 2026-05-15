package com.taisau.android.common.network

data class NetworkInfo(
    val isConnected: Boolean = false,
    val type: ConnectionType = ConnectionType.NONE,
    val isMetered: Boolean = true,
)

enum class ConnectionType {
    NONE, WIFI, MOBILE, ETHERNET, BLUETOOTH, VPN, OTHER,
}
