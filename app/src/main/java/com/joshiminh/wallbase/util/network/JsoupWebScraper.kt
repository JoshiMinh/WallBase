package com.joshiminh.wallbase.util.network

import com.joshiminh.wallbase.data.entity.WallpaperItem
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JsoupWebScraper @Inject constructor() : WebScraper {

    override suspend fun scrapePinterest(
        query: String,
        limit: Int,
        cursor: String?,
    ): ScrapePage = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val defaultUrl = "https://www.pinterest.com/wallpapersden/ultra-hd-wallpapers-collections/"
        val targetUrl = if (trimmed.isBlank() || trimmed.equals("wallpaper backgrounds", ignoreCase = true) || trimmed.equals("wallpapers", ignoreCase = true)) {
            defaultUrl
        } else if (trimmed.contains("pinterest.") || trimmed.contains("/")) {
            trimmed
        } else if (trimmed.startsWith("@")) {
            "https://www.pinterest.com/${trimmed.removePrefix("@")}/"
        } else {
            defaultUrl
        }

        val page = scrapePinterestUrl(targetUrl, limit, cursor)
        if (page != null && page.wallpapers.isNotEmpty()) {
            if (trimmed.isNotBlank() && !targetUrl.contains(trimmed, ignoreCase = true) && !trimmed.equals("wallpaper backgrounds", ignoreCase = true)) {
                val filtered = page.wallpapers.filter { item ->
                    item.title.contains(trimmed, ignoreCase = true)
                }
                if (filtered.isNotEmpty()) {
                    return@withContext ScrapePage(filtered, page.nextCursor)
                }
            }
            return@withContext page
        }

        ScrapePage(emptyList(), nextCursor = null)
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

            val document = Jsoup.connect(targetUrl)
                .userAgent(USER_AGENT)
                .referrer("https://www.google.com")
                .timeout(TIMEOUT_MS)
                .parser(org.jsoup.parser.Parser.xmlParser())
                .get()

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

    private fun fetch(url: String): Document =
        Jsoup.connect(url)
            .userAgent(USER_AGENT)
            .referrer("https://www.google.com")
            .timeout(TIMEOUT_MS)
            .get()

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
            val response = Jsoup.connect(endpoint)
                .ignoreContentType(true)
                .userAgent(USER_AGENT)
                .referrer("https://www.google.com")
                .timeout(TIMEOUT_MS)
                .header("Accept", "application/json, text/plain, */*")
                .method(Connection.Method.GET)
                .execute()

            val body = response.body()
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return@runCatching emptyList()
            val pinsArray = data.optJSONArray("pins") ?: return@runCatching emptyList()
            parsePinterestPins(pinsArray)
        }.getOrElse { emptyList() }
    }

    private fun parsePinterestPins(pinsArray: JSONArray): List<WallpaperItem> {
        val items = mutableListOf<WallpaperItem>()
        for (i in 0 until pinsArray.length()) {
            val pin = pinsArray.optJSONObject(i) ?: continue
            val id = pin.optString("id").takeIf { it.isNotBlank() } ?: continue
            val images = pin.optJSONObject("images") ?: continue

            val rawUrl = images.optJSONObject("564x")?.optString("url")
                ?: images.optJSONObject("236x")?.optString("url")
                ?: images.optJSONObject("237x")?.optString("url")
                ?: images.optJSONObject("orig")?.optString("url")
                ?: continue

            if (rawUrl.isBlank()) continue

            // Upgrade thumbnail URL to highest resolution originals
            val highResUrl = rawUrl
                .replace(Regex("/(236x|237x|564x|736x)/"), "/originals/")
                .replace("&amp;", "&")

            val desc = pin.optString("description").trim()
            val boardName = pin.optJSONObject("board")?.optString("name")?.trim().orEmpty()
            val pinnerName = pin.optJSONObject("pinner")?.let {
                it.optString("full_name").ifBlank { it.optString("username") }
            }?.trim().orEmpty()

            val title = cleanPinterestTitle(desc, boardName, pinnerName)
            val sourceUrl = pin.optString("link").takeIf { it.isNotBlank() }
                ?: "https://www.pinterest.com/pin/$id/"

            val imgObj = images.optJSONObject("564x") ?: images.optJSONObject("orig") ?: images.optJSONObject("236x")
            val width = imgObj?.optInt("width")?.takeIf { it > 0 }
            val height = imgObj?.optInt("height")?.takeIf { it > 0 }

            items += WallpaperItem(
                id = "pin_$id",
                title = title,
                imageUrl = highResUrl,
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
