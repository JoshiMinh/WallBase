package com.joshiminh.wallbase.util.network

import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

class ResilientDnsTest {

    @Test
    fun isPoisonedOrInvalid_detectsLoopbackAndSinkhole() {
        val loopback = InetAddress.getByName("127.0.0.1")
        val loopbackOther = InetAddress.getByName("127.0.0.2")
        val anyLocal = InetAddress.getByName("0.0.0.0")

        assertTrue(ResilientDns.isPoisonedOrInvalid(loopback))
        assertTrue(ResilientDns.isPoisonedOrInvalid(loopbackOther))
        assertTrue(ResilientDns.isPoisonedOrInvalid(anyLocal))

        val validPublic = InetAddress.getByName("151.101.1.140")
        val cloudflareIp = InetAddress.getByName("1.1.1.1")

        assertFalse(ResilientDns.isPoisonedOrInvalid(validPublic))
        assertFalse(ResilientDns.isPoisonedOrInvalid(cloudflareIp))
    }

    @Test
    fun lookup_fallsBackToDoh_whenSystemReturnsPoisonedLoopback() {
        val poisonedIp = InetAddress.getByName("127.0.0.1")
        val validIp = InetAddress.getByName("151.101.65.140")

        val mockSystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(poisonedIp)
        }
        val mockDohDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(validIp)
        }

        val resilientDns = ResilientDns(
            dohResolvers = listOf(mockDohDns),
            systemDns = mockSystemDns
        )

        val result = resilientDns.lookup("somecustomdomain.com")
        assertEquals(listOf(validIp), result)
    }

    @Test
    fun lookup_fallsBackToDoh_whenSystemDnsThrowsException() {
        val validIp = InetAddress.getByName("151.101.65.140")

        val mockSystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException("System DNS error")
        }
        val mockDohDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(validIp)
        }

        val resilientDns = ResilientDns(
            dohResolvers = listOf(mockDohDns),
            systemDns = mockSystemDns
        )

        val result = resilientDns.lookup("randomservice.org")
        assertEquals(listOf(validIp), result)
    }

    @Test
    fun lookup_prefersDoh_forRedditDomains() {
        val validRedditIp = InetAddress.getByName("151.101.1.140")
        var systemDnsCalled = false

        val mockSystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                systemDnsCalled = true
                return listOf(InetAddress.getByName("127.0.0.1"))
            }
        }
        val mockDohDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(validRedditIp)
        }

        val resilientDns = ResilientDns(
            dohResolvers = listOf(mockDohDns),
            systemDns = mockSystemDns
        )

        val result = resilientDns.lookup("www.reddit.com")
        assertEquals(listOf(validRedditIp), result)
        assertFalse("System DNS should not be called when DoH succeeds for prefer-DoH domains", systemDnsCalled)
    }

    @Test
    fun lookup_throwsUnknownHostException_whenAllResolversFail() {
        val mockSystemDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(InetAddress.getByName("127.0.0.1"))
        }
        val mockDohDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = throw UnknownHostException("DoH network error")
        }

        val resilientDns = ResilientDns(
            dohResolvers = listOf(mockDohDns),
            systemDns = mockSystemDns
        )

        try {
            resilientDns.lookup("unreachabledomain.xyz")
            fail("Expected UnknownHostException")
        } catch (e: UnknownHostException) {
            assertTrue(e.message?.contains("Unable to resolve host") == true)
        }
    }
}
