package com.joshiminh.wallbase.data.repository

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Singleton
class RemoteImageFetcher @Inject constructor() {
    suspend fun fetch(url: String): RemoteImage? = withContext(Dispatchers.IO) {
        runCatching { fetchRemoteImage(url, 0, mutableSetOf()) }.getOrNull()
    }

    private fun fetchRemoteImage(
        url: String,
        depth: Int,
        visited: MutableSet<String>
    ): RemoteImage? {
        if (depth > REMOTE_MAX_REDIRECTS || !visited.add(url)) {
            return null
        }

        val connection = runCatching { URL(url).openConnection() }.getOrNull() ?: return null
        if (connection !is HttpURLConnection) {
            connection.connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
            connection.readTimeout = REMOTE_READ_TIMEOUT_MS
            return runCatching {
                connection.getInputStream().use { stream ->
                    val bytes = stream.readBytes()
                    val rawType = connection.contentType
                    val likelyImage = rawType
                        ?.lowercase(Locale.ROOT)
                        ?.startsWith("image/") == true || looksLikeImageUrl(url)
                    RemoteImage(bytes, rawType, likelyImage)
                }
            }.getOrNull()
        }

        return connection.runCatching {
            instanceFollowRedirects = false
            connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
            readTimeout = REMOTE_READ_TIMEOUT_MS
            setRequestProperty("User-Agent", REMOTE_USER_AGENT)
            setRequestProperty("Accept", REMOTE_ACCEPT_HEADER)
            setRequestProperty("Accept-Language", REMOTE_ACCEPT_LANGUAGE)
            setRequestProperty("Referer", REMOTE_REFERER)
            connect()

            val status = responseCode
            if (status in 300..399) {
                val location = getHeaderField("Location")?.takeIf { it.isNotBlank() }
                disconnect()
                val resolved = location?.let { resolveUrl(url, it) }
                return resolved?.let { fetchRemoteImage(it, depth + 1, visited) }
            }
            if (status >= 400) {
                disconnect()
                return null
            }

            val rawContentType = getHeaderField("Content-Type")
            val rawMimeType = rawContentType?.substringBefore(';')?.trim()
            val mimeType = rawMimeType?.lowercase(Locale.ROOT)
            val contentDisposition = getHeaderField("Content-Disposition")
            val finalUrl = connection.url.toString()
            val bytes = inputStream.use { it.readBytes() }

            if (bytes.isEmpty()) {
                disconnect()
                return null
            }

            if (mimeType != null && mimeType.startsWith("image/")) {
                disconnect()
                return RemoteImage(bytes, rawMimeType, true)
            }
            if (!contentDisposition.isNullOrBlank() && contentDisposition.contains("filename", ignoreCase = true)) {
                disconnect()
                return RemoteImage(bytes, rawMimeType, true)
            }
            if (mimeType == null && looksLikeImageUrl(finalUrl)) {
                disconnect()
                return RemoteImage(bytes, rawMimeType, true)
            }

            val effectiveType = mimeType ?: ""
            if (effectiveType.contains("text/html") || effectiveType.contains("application/xhtml")) {
                val charset = parseCharset(rawContentType)
                val html = bytes.toString(charset)
                val nextUrl = resolveImageUrlFromHtml(finalUrl, html)
                disconnect()
                if (!nextUrl.isNullOrBlank()) {
                    return fetchRemoteImage(nextUrl, depth + 1, visited)
                }
                return null
            }

            disconnect()
            null
        }.getOrNull()
    }

    private fun parseCharset(contentType: String?): Charset {
        if (contentType == null) return StandardCharsets.UTF_8
        val parts = contentType.split(';')
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("charset=", ignoreCase = true)) {
                val value = trimmed.substringAfter('=')
                return runCatching { Charset.forName(value) }.getOrDefault(StandardCharsets.UTF_8)
            }
        }
        return StandardCharsets.UTF_8
    }

    internal fun resolveImageUrlFromHtml(baseUrl: String, html: String): String? {
        val document = runCatching { Jsoup.parse(html, baseUrl) }.getOrNull() ?: return null
        val host = runCatching { URI(baseUrl).host?.lowercase(Locale.ROOT) }.getOrNull() ?: ""

        metaImageCandidates(document).forEach { candidate ->
            if (candidate.isNotBlank()) {
                return candidate
            }
        }

        hostSpecificImage(document, host)?.let { return it }

        return document.select(IMAGE_FALLBACK_SELECTOR)
            .asSequence()
            .mapNotNull { element ->
                element.resolveImageCandidate(
                    "data-full",
                    "data-fullsrc",
                    "data-src",
                    "data-lazy-src",
                    "data-original",
                    "src"
                )
            }
            .firstOrNull { isLikelyImageCandidate(it) }
    }

    private fun metaImageCandidates(document: Document): Sequence<String> {
        return sequence {
            val selectors = listOf(
                "meta[property=og:image:secure_url]",
                "meta[property=og:image]",
                "meta[name=og:image]",
                "meta[name=twitter:image:src]",
                "meta[name=twitter:image]",
                "meta[property=twitter:image]",
                "meta[itemprop=image]",
                "link[rel=image_src]",
                "meta[name=thumbnail]"
            )
            for (selector in selectors) {
                val element = document.selectFirst(selector) ?: continue
                val value = when (element.tagName()) {
                    "link" -> element.absUrl("href").ifBlank { element.attr("href") }
                    else -> element.absUrl("content").ifBlank { element.attr("content") }
                }
                if (value.isNotBlank()) {
                    yield(value)
                }
            }
        }
    }

    private fun hostSpecificImage(document: Document, host: String): String? {
        if (host.contains("wallhaven.cc")) {
            document.selectFirst("#wallpaper")?.let { element ->
                element.resolveImageCandidate("data-cfsrc", "data-src", "src")?.let { return it }
            }
        }
        if (host.contains("reddit.com")) {
            document.select("img[src]").firstOrNull { element ->
                val candidate = element.absUrl("src")
                candidate.contains("preview.redd.it") || candidate.contains("i.redd.it")
            }?.let { element ->
                element.resolveImageCandidate("src")?.let { return it }
            }
        }
        return null
    }

    private fun Element.resolveImageCandidate(vararg attributes: String): String? {
        val base = ownerDocument()?.baseUri()
        for (attribute in attributes) {
            val absolute = absUrl(attribute)
            if (absolute.isNotBlank()) {
                return absolute
            }
            val raw = attr(attribute)
            if (raw.isNotBlank()) {
                val normalized = when {
                    raw.startsWith("//") -> "https:$raw"
                    base != null && (raw.startsWith("/") || !raw.startsWith("http", ignoreCase = true)) ->
                        runCatching { URL(URL(base), raw).toString() }.getOrElse { raw }
                    else -> raw
                }
                if (normalized.isNotBlank()) {
                    return normalized
                }
            }
        }
        return null
    }

    internal fun looksLikeImageUrl(url: String): Boolean {
        val normalized = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return IMAGE_EXTENSIONS.any { normalized.endsWith(it) }
    }

    private fun isLikelyImageCandidate(url: String): Boolean {
        if (!url.startsWith("http", ignoreCase = true)) return false
        if (looksLikeImageUrl(url)) return true
        val host = runCatching { URI(url).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return host.contains("redd.it") ||
            host.contains("redditmedia.com") ||
            host.contains("wallhaven.cc") ||
            host.contains("pinimg.com") ||
            host.contains("twimg.com")
    }

    private fun resolveUrl(baseUrl: String, location: String): String {
        return runCatching { URL(URL(baseUrl), location).toString() }.getOrElse { location }
    }

    data class RemoteImage(
        val bytes: ByteArray,
        val mimeType: String?,
        val isLikelyImage: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RemoteImage

            if (!bytes.contentEquals(other.bytes)) return false
            if (mimeType != other.mimeType) return false
            if (isLikelyImage != other.isLikelyImage) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + (mimeType?.hashCode() ?: 0)
            result = 31 * result + isLikelyImage.hashCode()
            return result
        }
    }

    private companion object {
        private const val REMOTE_CONNECT_TIMEOUT_MS = 15_000
        private const val REMOTE_READ_TIMEOUT_MS = 20_000
        private const val REMOTE_MAX_REDIRECTS = 5
        private const val REMOTE_USER_AGENT = "WallBase/1.0 (Android)"
        private const val REMOTE_REFERER = "https://www.google.com/"
        private const val REMOTE_ACCEPT_HEADER = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"
        private const val REMOTE_ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val IMAGE_FALLBACK_SELECTOR =
            "img[src],img[data-src],img[data-full],img[data-fullsrc],img[data-lazy-src],img[data-original]"
        private val IMAGE_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif", ".heic", ".heif", ".jfif"
        )
    }
}
