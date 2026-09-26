package com.joshiminh.wallbase.util.network

import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Resilient DNS resolver that combines System DNS with DNS-over-HTTPS (DoH) fallback.
 *
 * Automatically detects and bypasses ISP DNS spoofing/poisoning (such as returning 127.0.0.1 or 0.0.0.0
 * for Reddit, Pixiv, etc.) by routing through Cloudflare and Google DoH servers.
 */
class ResilientDns(
    private val dohResolvers: List<Dns>,
    private val systemDns: Dns = Dns.SYSTEM,
) : Dns {

    companion object {
        private val DOMAINS_PREFER_DOH = setOf(
            "reddit.com",
            "redd.it",
            "redditstatic.com",
            "redditmedia.com",
            "pixiv.net",
            "pximg.net",
            "wallhaven.cc",
            "alphacoders.com",
            "pinimg.com",
            "pinterest.com",
            "unsplash.com",
            "pexels.com"
        )

        fun create(cacheDir: File? = null): ResilientDns {
            val bootstrapBuilder = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)

            if (cacheDir != null) {
                val dohCacheDir = File(cacheDir, "doh_cache")
                bootstrapBuilder.cache(Cache(dohCacheDir, 5L * 1024 * 1024))
            }
            val bootstrapClient = bootstrapBuilder.build()

            val cloudflareDns = runCatching {
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        InetAddress.getByName("1.1.1.1"),
                        InetAddress.getByName("1.0.0.1"),
                        InetAddress.getByName("2606:4700:4700::1111"),
                        InetAddress.getByName("2606:4700:4700::1001")
                    )
                    .includeIPv6(true)
                    .build()
            }.getOrNull()

            val googleDns = runCatching {
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url("https://dns.google/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("8.8.4.4"),
                        InetAddress.getByName("2001:4860:4860::8888"),
                        InetAddress.getByName("2001:4860:4860::8844")
                    )
                    .includeIPv6(true)
                    .build()
            }.getOrNull()

            val quad9Dns = runCatching {
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url("https://dns.quad9.net/dns-query".toHttpUrl())
                    .bootstrapDnsHosts(
                        InetAddress.getByName("9.9.9.9"),
                        InetAddress.getByName("149.112.112.112")
                    )
                    .includeIPv6(true)
                    .build()
            }.getOrNull()

            val resolvers = listOfNotNull(cloudflareDns, googleDns, quad9Dns)

            return ResilientDns(
                dohResolvers = resolvers,
                systemDns = Dns.SYSTEM
            )
        }

        fun isPoisonedOrInvalid(address: InetAddress): Boolean {
            if (address.isLoopbackAddress || address.isAnyLocalAddress) return true
            val bytes = address.address
            if (bytes.size == 4) {
                // 127.0.0.0/8 (loopback sinkhole)
                if (bytes[0] == 127.toByte()) return true
                // 0.0.0.0 (unspecified / sinkhole)
                if (bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() && bytes[3] == 0.toByte()) return true
            }
            return false
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val lowerHost = hostname.lowercase()

        // If local or IP literal, standard lookup
        if (lowerHost == "localhost" || lowerHost.endsWith(".local") || lowerHost.endsWith(".internal")) {
            return systemDns.lookup(hostname)
        }

        val preferDoH = DOMAINS_PREFER_DOH.any { lowerHost == it || lowerHost.endsWith(".$it") }

        if (preferDoH) {
            val dohResult = resolveWithDoH(hostname)
            if (dohResult.isNotEmpty()) {
                return dohResult
            }
        }

        // Try System DNS
        try {
            val systemAddresses = systemDns.lookup(hostname).filterNot { isPoisonedOrInvalid(it) }
            if (systemAddresses.isNotEmpty()) {
                return systemAddresses
            }
        } catch (_: Exception) {
            // Fall back to DoH below
        }

        // Fallback to DoH
        val dohFallback = resolveWithDoH(hostname)
        if (dohFallback.isNotEmpty()) {
            return dohFallback
        }

        throw UnknownHostException("Unable to resolve host '$hostname' via system DNS and DoH fallback resolvers")
    }

    private fun resolveWithDoH(hostname: String): List<InetAddress> {
        for (resolver in dohResolvers) {
            try {
                val addresses = resolver.lookup(hostname).filterNot { isPoisonedOrInvalid(it) }
                if (addresses.isNotEmpty()) {
                    return addresses
                }
            } catch (_: Exception) {
                // Try next DoH resolver
            }
        }
        return emptyList()
    }
}
