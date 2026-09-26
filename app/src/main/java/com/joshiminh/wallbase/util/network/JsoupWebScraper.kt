package com.joshiminh.wallbase.util.network

import com.joshiminh.wallbase.data.entity.WallpaperItem
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupWebScraper @Inject constructor(
    private val okHttpClient: OkHttpClient
) : WebScraper {

    override suspend fun scrapePinterest(
        query: String,
        limit: Int,
        cursor: String?,
    ): ScrapePage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val defaultUrl = "https://www.pinterest.com/wallpapersden/ultra-hd-wallpapers-collections/"
        when {
            trimmed.isBlank() || trimmed.equals("wallpaper backgrounds", ignoreCase = true) || trimmed.equals("wallpapers", ignoreCase = true) -> {
                scrapePinterestUrl(defaultUrl, limit, cursor) ?: ScrapePage(emptyList(), nextCursor = null)
            }
            trimmed.contains("pinterest.") || trimmed.contains("/") -> {
                scrapePinterestUrl(trimmed, limit, cursor) ?: ScrapePage(emptyList(), nextCursor = null)
            }
            trimmed.startsWith("@") -> {
                val profileUrl = "https://www.pinterest.com/${trimmed.removePrefix("@")}/"
                scrapePinterestUrl(profileUrl, limit, cursor) ?: ScrapePage(emptyList(), nextCursor = null)
            }
            else -> {
                scrapePinterestSearch(trimmed, limit, cursor)
            }
        }
    }

    override suspend fun scrapeReddit(
        subreddit: String,
        query: String?,
        cursor: String?
    ): ScrapePage = withContext(Dispatchers.IO) {
        runCatching {
            val cleanSubreddit = subreddit.trim().removePrefix("r/").removePrefix("/")
            val baseUrl = if (!query.isNullOrBlank()) {
                val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
                "https://www.reddit.com/r/$cleanSubreddit/search.rss?q=$encoded&restrict_sr=1&sort=relevance"
            } else {
                "https://www.reddit.com/r/$cleanSubreddit/top/.rss?t=all"
            }
            val targetUrl = if (!cursor.isNullOrBlank()) {
                val separator = if (baseUrl.contains('?')) '&' else '?'
                "$baseUrl${separator}after=${URLEncoder.encode(cursor, StandardCharsets.UTF_8.toString())}"
            } else {
                baseUrl
            }

            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.google.com")
                .build()

            val responseBody = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }

            val document = Jsoup.parse(responseBody, targetUrl, org.jsoup.parser.Parser.xmlParser())

            val entries = document.select("entry")
            val items = mutableListOf<WallpaperItem>()
            var lastId: String? = null

            for (entry in entries) {
                val rawId = entry.selectFirst("id")?.text().orEmpty()
                if (rawId.isNotBlank()) {
                    lastId = rawId
                }
                val title = entry.selectFirst("title")?.text()?.ifBlank { "Reddit Wallpaper" } ?: "Reddit Wallpaper"
                val link = entry.selectFirst("link")?.attr("href").takeUnless { it.isNullOrBlank() } ?: targetUrl
                val contentHtml = entry.selectFirst("content")?.text().orEmpty()
                val contentDoc = if (contentHtml.isNotBlank()) Jsoup.parse(contentHtml) else null

                val directLink = contentDoc?.select("a[href]")?.asSequence()
                    ?.map { it.attr("href") }
                    ?.firstOrNull { it.hasSupportedExtension() || it.contains("i.redd.it") || it.contains("i.imgur.com") }

                val previewImg = contentDoc?.selectFirst("img[src]")?.attr("src")
                    ?: entry.selectFirst("media\\:thumbnail")?.attr("url")

                val candidateUrl = (directLink ?: previewImg)
                    ?.replace("&amp;", "&")
                    ?.let { if (it.startsWith("http://", ignoreCase = true)) "https://" + it.substring(7) else it }
                if (candidateUrl != null && candidateUrl.startsWith("http", ignoreCase = true)) {
                    val idValue = if (rawId.isNotBlank()) "reddit_$rawId" else "reddit_${candidateUrl.hashCode()}"
                    items += WallpaperItem(
                        id = idValue,
                        title = title,
                        imageUrl = candidateUrl,
                        sourceUrl = link
                    )
                }
            }

            val nextCursor = if (items.isNotEmpty() && lastId != null) lastId else null
            ScrapePage(items, nextCursor)
        }.getOrElse {
            ScrapePage(emptyList(), nextCursor = null)
        }
    }

    override suspend fun scrapeImagesFromUrl(
        url: String,
        limit: Int,
        cursor: String?,
    ): ScrapePage {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT) ?: ""
        val isPinterest = host.contains("pinterest.") || host == "pin.it" || url.contains("pinterest.com")
        if (isPinterest) {
            val specialized = runCatching {
                scrapePinterestUrl(url, limit, cursor)
            }.getOrNull()
            if (specialized != null && specialized.wallpapers.isNotEmpty()) {
                return specialized
            }
        }

        return runCatching {
            extractImages(url, limit, cursor) { element ->
                element.attr("alt").ifBlank { element.attr("title") }
            }
        }.getOrElse { ScrapePage(emptyList(), nextCursor = null) }
    }

    private suspend fun extractImages(
        pageUrl: String,
        limit: Int,
        cursor: String?,
        titleProvider: (Element) -> String
    ): ScrapePage = withContext(Dispatchers.IO) {
        val document = fetch(pageUrl)
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<WallpaperItem>()
        val offset = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
        val maxToCollect = offset + limit + 1

        document.select(IMAGE_SELECTOR).forEach { element ->
            if (results.size >= maxToCollect) return@forEach

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

        val fromIndex = offset.coerceAtMost(results.size)
        val toIndex = (fromIndex + limit).coerceAtMost(results.size)
        val pageItems = if (fromIndex >= toIndex) {
            emptyList()
        } else {
            results.subList(fromIndex, toIndex).toList()
        }
        val hasMore = results.size > toIndex
        val nextCursor = if (hasMore) toIndex.toString() else null
        ScrapePage(pageItems, nextCursor)
    }

    private fun fetch(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.google.com")
            .build()
        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        return Jsoup.parse(responseBody, url)
    }

    private suspend fun scrapePinterestUrl(
        pageUrl: String,
        limit: Int,
        cursor: String?,
    ): ScrapePage? = withContext(Dispatchers.IO) {
        val info = parsePinterestUrl(pageUrl) ?: return@withContext null
        val items = mutableListOf<WallpaperItem>()

        // 1. If it has a board slug, try board pidgets first
        if (!info.boardSlug.isNullOrBlank()) {
            val boardEndpoint = "https://api.pinterest.com/v3/pidgets/boards/${info.username}/${info.boardSlug}/pins/"
            val boardPins = fetchPidgetsPins(boardEndpoint)
            if (boardPins.isNotEmpty()) {
                items.addAll(boardPins)
            }
        }

        // 2. If board was empty or url is a user profile, fetch user pidgets
        if (items.isEmpty()) {
            val userEndpoint = "https://api.pinterest.com/v3/pidgets/users/${info.username}/pins/"
            val userPins = fetchPidgetsPins(userEndpoint)
            if (userPins.isNotEmpty()) {
                items.addAll(userPins)
            }
        }

        if (items.isEmpty()) return@withContext null

        val offset = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
        val fromIndex = offset.coerceAtMost(items.size)
        val toIndex = (fromIndex + limit).coerceAtMost(items.size)
        val pagedItems = if (fromIndex >= toIndex) emptyList() else items.subList(fromIndex, toIndex).toList()
        val nextCursor = if (items.size > toIndex) toIndex.toString() else null

        ScrapePage(pagedItems, nextCursor)
    }

    private fun fetchPidgetsPins(endpoint: String): List<WallpaperItem> {
        return runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://www.google.com")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string().orEmpty()
            }

            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return@runCatching emptyList()
            val pinsArray = data.optJSONArray("pins") ?: return@runCatching emptyList()
            parsePinterestPins(pinsArray)
        }.getOrElse { emptyList() }
    }

    private suspend fun scrapePinterestSearch(
        query: String,
        limit: Int,
        cursor: String?,
    ): ScrapePage = withContext(Dispatchers.IO) {
        runCatching {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            val searchUrl = "https://www.pinterest.com/search/pins/?q=$encodedQuery"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", MOBILE_USER_AGENT)
                .header("Referer", "https://www.google.com")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .build()

            val body = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
            val document = Jsoup.parse(body, searchUrl)

            val imgElements = document.select("img[elementtiming=grid-gated-pin-image-search], img[src*='i.pinimg.com/236x/'], img[src*='i.pinimg.com/474x/'], img[src*='i.pinimg.com/736x/']")
            val items = mutableListOf<WallpaperItem>()
            val seenUrls = mutableSetOf<String>()

            for (img in imgElements) {
                val src = img.attr("src").ifBlank { img.attr("data-src") }
                if (src.isBlank() || !src.contains("i.pinimg.com")) continue
                if (src.contains("/60x60/") || src.contains("/75x75/")) continue

                val srcSet = img.attr("srcSet").ifBlank { img.attr("srcset") }
                val highResUrl = extractHighResFromSrcSet(srcSet)
                    ?: src.replace(Regex("/(236x|237x|474x|564x|736x)/"), "/originals/").replace("&amp;", "&")
                if (!seenUrls.add(highResUrl)) continue

                val thumbUrl = extractThumbFromSrcSet(srcSet) ?: src.replace("&amp;", "&")
                val filename = highResUrl.substringAfterLast('/').substringBefore('?')
                val id = filename.substringBeforeLast('.').ifBlank { highResUrl.hashCode().toString() }
                val alt = img.attr("alt").trim()
                val title = if (alt.isNotBlank() && !alt.equals("Pin", ignoreCase = true) && !alt.equals("Image", ignoreCase = true)) {
                    alt.take(100)
                } else {
                    "$query Wallpaper"
                }

                items += WallpaperItem(
                    id = "pin_$id",
                    title = title,
                    imageUrl = highResUrl,
                    thumbnailUrl = thumbUrl,
                    sourceUrl = "https://www.pinterest.com/search/pins/?q=$encodedQuery",
                    width = if (highResUrl.contains("/originals/")) 1440 else null,
                    height = if (highResUrl.contains("/originals/")) 2560 else null
                )
            }

            val offset = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
            val fromIndex = offset.coerceAtMost(items.size)
            val toIndex = (fromIndex + limit).coerceAtMost(items.size)
            val pagedItems = if (fromIndex >= toIndex) emptyList() else items.subList(fromIndex, toIndex).toList()
            val nextCursor = if (items.size > toIndex) toIndex.toString() else null

            ScrapePage(pagedItems, nextCursor)
        }.getOrElse {
            ScrapePage(emptyList(), nextCursor = null)
        }
    }

    private fun extractHighResFromSrcSet(srcSet: String): String? {
        if (srcSet.isBlank()) return null
        val match = Regex("""(https://i\.pinimg\.com/originals/[^\s,]+)""").find(srcSet)
        if (match != null) return match.groupValues[1]
        val match736 = Regex("""(https://i\.pinimg\.com/736x/[^\s,]+)""").find(srcSet)
        if (match736 != null) return match736.groupValues[1].replace("/736x/", "/originals/")
        return null
    }

    private fun extractThumbFromSrcSet(srcSet: String): String? {
        if (srcSet.isBlank()) return null
        val match736 = Regex("""(https://i\.pinimg\.com/736x/[^\s,]+)""").find(srcSet)
        if (match736 != null) return match736.groupValues[1]
        val match474 = Regex("""(https://i\.pinimg\.com/474x/[^\s,]+)""").find(srcSet)
        if (match474 != null) return match474.groupValues[1]
        val match236 = Regex("""(https://i\.pinimg\.com/236x/[^\s,]+)""").find(srcSet)
        if (match236 != null) return match236.groupValues[1]
        return null
    }

    private fun parsePinterestPins(pinsArray: JSONArray): List<WallpaperItem> {
        val items = mutableListOf<WallpaperItem>()
        for (i in 0 until pinsArray.length()) {
            val pin = pinsArray.optJSONObject(i) ?: continue
            val id = pin.optString("id").takeIf { it.isNotBlank() } ?: continue
            val images = pin.optJSONObject("images") ?: continue

            val rawUrl = images.optJSONObject("orig")?.optString("url")
                ?: images.optJSONObject("564x")?.optString("url")
                ?: images.optJSONObject("236x")?.optString("url")
                ?: images.optJSONObject("237x")?.optString("url")
                ?: continue

            if (rawUrl.isBlank()) continue

            // Upgrade thumbnail URL to highest resolution originals
            val highResUrl = rawUrl
                .replace(Regex("/(236x|237x|564x|736x)/"), "/originals/")
                .replace("&amp;", "&")

            val thumbUrl = images.optJSONObject("564x")?.optString("url")
                ?: images.optJSONObject("236x")?.optString("url")
                ?: rawUrl

            val desc = pin.optString("description").trim()
            val boardName = pin.optJSONObject("board")?.optString("name")?.trim().orEmpty()
            val pinnerName = pin.optJSONObject("pinner")?.let {
                it.optString("full_name").ifBlank { it.optString("username") }
            }?.trim().orEmpty()

            val title = cleanPinterestTitle(desc, boardName, pinnerName)
            val sourceUrl = pin.optString("link").takeIf { it.isNotBlank() }
                ?: "https://www.pinterest.com/pin/$id/"

            val imgObj = images.optJSONObject("orig") ?: images.optJSONObject("564x") ?: images.optJSONObject("236x")
            val width = imgObj?.optInt("width")?.takeIf { it > 0 }
            val height = imgObj?.optInt("height")?.takeIf { it > 0 }

            items += WallpaperItem(
                id = "pin_$id",
                title = title,
                imageUrl = highResUrl,
                thumbnailUrl = thumbUrl,
                sourceUrl = sourceUrl,
                width = width,
                height = height
            )
        }
        return items
    }

    private fun cleanPinterestTitle(desc: String, board: String, pinner: String): String {
        val firstLine = desc.lines().firstOrNull().orEmpty()
        val withoutHashtags = firstLine.replace(Regex("#\\w+"), "").trim()
        return when {
            withoutHashtags.isNotBlank() -> withoutHashtags.take(100)
            board.isNotBlank() -> board
            pinner.isNotBlank() -> "By $pinner"
            else -> "Pinterest Wallpaper"
        }
    }

    private fun parsePinterestUrl(pageUrl: String): PinterestUrlInfo? {
        val trimmed = pageUrl.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("@")

        val parts = trimmed.split('?', '#')[0].trim('/').split('/')
        if (parts.isEmpty() || parts[0].isBlank()) return null

        val username = if (parts[0].contains("pinterest.") || parts[0] == "pin.it") {
            if (parts.size >= 2) parts[1] else return null
        } else {
            parts[0]
        }

        if (username.isBlank() || username.equals("pin", ignoreCase = true) || username.equals("search", ignoreCase = true)) {
            return null
        }

        val boardSlug = if (parts[0].contains("pinterest.")) {
            if (parts.size >= 3 && !parts[2].startsWith("_")) parts[2] else null
        } else {
            if (parts.size >= 2 && !parts[1].startsWith("_")) parts[1] else null
        }

        return PinterestUrlInfo(username = username, boardSlug = boardSlug)
    }

    private data class PinterestUrlInfo(
        val username: String,
        val boardSlug: String?
    )

    private companion object {
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
        private const val IMAGE_SELECTOR =
            "img[src], img[data-src], img[data-lazy-src], img[data-original], img[data-actualsrc]"
        private val IMAGE_ATTRIBUTES =
            listOf("src", "data-src", "data-lazy-src", "data-original", "data-actualsrc")
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
