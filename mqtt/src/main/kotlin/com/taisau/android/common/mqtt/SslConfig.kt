package com.taisau.android.common.mqtt

import android.net.http.SslCertificate
import java.io.InputStream

data class SslConfig(
    val caCertificateStream: InputStream? = null,
    val clientCertificateStream: InputStream? = null,
    val clientKeyPassword: String? = null
)