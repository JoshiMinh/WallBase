package com.joshiminh.wallbase.network

import com.joshiminh.wallbase.data.wallpapers.WallpaperItem
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class JsoupWebScraper : WebScraper {

    override suspend fun scrapePinterest(query: String, limit: Int): List<WallpaperItem> = runCatching {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "https://www.pinterest.com/search/pins/?q=$encodedQuery"
        extractImages(url, limit) { element ->
            element.attr("alt").ifBlank { "Pinterest Pin" }
        }
    }.getOrElse { emptyList() }

    override suspend fun scrapeImagesFromUrl(url: String, limit: Int): List<WallpaperItem> = runCatching {
        extractImages(url, limit) { element ->
            element.attr("alt").ifBlank { element.attr("title") }
        }
    }.getOrElse { emptyList() }

    private suspend fun extractImages(
        pageUrl: String,
        limit: Int,
        titleProvider: (Element) -> String
    ): List<WallpaperItem> = withContext(Dispatchers.IO) {
        val document = fetch(pageUrl)
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<WallpaperItem>()

        document.select(IMAGE_SELECTOR).forEach { element ->
            if (results.size >= limit) return@forEach

            val resolvedUrl = element.extractImageUrl()?.replace("&amp;", "&") ?: return@forEach
            if (!resolvedUrl.startsWith("http", ignoreCase = true) || !resolvedUrl.hasSupportedExtension()) {
                return@forEach
            }
            if (!seen.add(resolvedUrl)) return@forEach

            val anchor = element.closest("a[href]")
            val sourceUrl = anchor?.absUrl("href").takeUnless { it.isNullOrBlank() } ?: pageUrl
            val title = titleProvider(element).ifBlank { anchor?.attr("title") ?: "Wallpaper" }

            results += WallpaperItem(
                id = resolvedUrl.hashCode().toString(),
                title = title.trim().ifEmpty { "Wallpaper" },
                imageUrl = resolvedUrl,
                sourceUrl = sourceUrl
            )
        }

        results
    }

    private fun fetch(url: String): Document =
        Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .referrer("https://www.google.com")
            .timeout(TIMEOUT_MS)
            .get()

    private companion object {
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; WallBase) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        private const val IMAGE_SELECTOR = "img[src], img[data-src], img[data-lazy-src], img[data-original], img[data-actualsrc]"
        private val IMAGE_ATTRIBUTES = listOf("src", "data-src", "data-lazy-src", "data-original", "data-actualsrc")
    }

    private fun Element.extractImageUrl(): String? {
        return IMAGE_ATTRIBUTES.asSequence()
            .map { attribute -> absUrl(attribute) }
            .firstOrNull { it.isNotBlank() }
    }

    private fun String.hasSupportedExtension(): Boolean {
        val normalized = substringBefore('?')
            .substringBefore('#')
            .lowercase(Locale.ROOT)
        return normalized.endsWith(".jpg") ||
            normalized.endsWith(".jpeg") ||
            normalized.endsWith(".png") ||
            normalized.endsWith(".webp")
    }
}
