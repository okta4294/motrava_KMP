package com.myapp.motrava.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

actual fun createHttpClientEngine(): HttpClientEngine = OkHttp.create {
    config {
        // Prioritize IPv4 addresses so IndiHome/Telkom IPv6 routing issues and blackholes are avoided
        dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return try {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    // Sort IPv4 addresses before IPv6 addresses
                    addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
                } catch (e: Exception) {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        })

        connectTimeout(15, TimeUnit.SECONDS)
        readTimeout(20, TimeUnit.SECONDS)
        writeTimeout(20, TimeUnit.SECONDS)
        retryOnConnectionFailure(true)
    }
}
