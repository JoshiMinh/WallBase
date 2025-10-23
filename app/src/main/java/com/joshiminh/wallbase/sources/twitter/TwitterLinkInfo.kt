package com.joshiminh.wallbase.sources.twitter

import java.net.URI
import java.net.URISyntaxException
import java.util.LinkedHashMap
import java.util.Locale

sealed class TwitterLinkInfo {
    data class Tweet(
        val originalUrl: String,
        val canonicalUrl: String,
        val scrapeUrl: String,
        val username: String?,
        val statusId: String
    ) : TwitterLinkInfo()

    data class Media(
        val originalUrl: String,
        val imageUrl: String
    ) : TwitterLinkInfo()
}

fun parseTwitterLink(raw: String): TwitterLinkInfo? {
    val uri = try {
        URI(raw)
    } catch (_: URISyntaxException) {
        return null
    }
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val normalizedHost = host.removePrefix("www.")

    return when {
        normalizedHost.endsWith("twimg.com") -> {
            TwitterLinkInfo.Media(
                originalUrl = raw,
                imageUrl = upgradeTwitterMediaQuality(raw)
            )
        }

        normalizedHost.endsWith("twitter.com") ||
            normalizedHost.endsWith("x.com") ||
            normalizedHost.endsWith("vxtwitter.com") ||
            normalizedHost.endsWith("fxtwitter.com") ||
            normalizedHost == "mobile.twitter.com" -> {
            val path = uri.path?.trim('/') ?: return null
            val segments = path.split('/').filter { it.isNotBlank() }
            if (segments.size < 3) return null

            val isStatusPath = segments[1].equals("status", ignoreCase = true)
            if (!isStatusPath) return null

            val usernameSegment = segments[0]
            val statusId = segments[2].takeWhile { it.isDigit() }
            if (statusId.isEmpty()) return null

            val username = usernameSegment.takeUnless { it.equals("i", ignoreCase = true) }
            val canonicalHost = "x.com"
            val canonicalPath = "/${usernameSegment}/status/$statusId"
            val canonicalUrl = URI(
                "https",
                canonicalHost,
                canonicalPath,
                null,
                null
            ).toString()

            val scrapeHost = when {
                normalizedHost.endsWith("vxtwitter.com") || normalizedHost.endsWith("fxtwitter.com") -> normalizedHost
                else -> "vxtwitter.com"
            }
            val scrapeUrl = URI(
                "https",
                null,
                scrapeHost,
                -1,
                canonicalPath,
                uri.query,
                null
            ).toString()

            TwitterLinkInfo.Tweet(
                originalUrl = raw,
                canonicalUrl = canonicalUrl,
                scrapeUrl = scrapeUrl,
                username = username,
                statusId = statusId
            )
        }

        else -> null
    }
}

fun upgradeTwitterMediaQuality(url: String): String {
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return url
    }
    val params = parseQueryParameters(uri.rawQuery)
    params["name"] = "orig"
    val newQuery = params.entries.joinToString("&") { (key, value) ->
        if (value.isEmpty()) key else "$key=$value"
    }
    return URI(
        uri.scheme,
        uri.userInfo,
        uri.host,
        uri.port,
        uri.path,
        newQuery.ifBlank { null },
        uri.fragment
    ).toString()
}

private fun parseQueryParameters(query: String?): LinkedHashMap<String, String> {
    val map = LinkedHashMap<String, String>()
    if (query.isNullOrBlank()) return map
    val parts = query.split('&')
    for (part in parts) {
        if (part.isBlank()) continue
        val pieces = part.split('=', limit = 2)
        val key = pieces.getOrNull(0)?.takeIf { it.isNotBlank() } ?: continue
        val value = pieces.getOrNull(1) ?: ""
        map[key] = value
    }
    return map
}