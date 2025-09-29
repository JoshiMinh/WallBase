package com.joshiminh.wallbase.util.network

import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
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

// Top-level so it can be called from companion objects too.
private fun ensurePinterestPath(path: String): String {
    val trimmed = if (path.startsWith("/")) path else "/$path"
    return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
}

class JsoupWebScraper : WebScraper {

    override suspend fun scrapePinterest(
        query: String,
        limit: Int,
        cursor: String?,
    ): ScrapePage = runCatching {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "https://www.pinterest.com/search/pins/?q=$encodedQuery"
        extractImages(url, limit, cursor) { element ->
            element.attr("alt").ifBlank { "Pinterest Pin" }
        }
    }.getOrElse { ScrapePage(emptyList(), nextCursor = null) }

    override suspend fun scrapeImagesFromUrl(
        url: String,
        limit: Int,
        cursor: String?,
    ): ScrapePage {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.ROOT) ?: ""
        val specialized = when {
            host.contains("pinterest.") -> runCatching {
                scrapePinterestUrl(url, limit, cursor)
            }.getOrNull()
            else -> null
        }
        if (specialized != null) return specialized

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
    ): ScrapePage? {
        if (cursor.isNullOrBlank()) {
            val info = parsePinterestUrl(pageUrl) ?: return null
            return runCatching {
                val document = fetch(pageUrl)
                parsePinterestInitialPage(document, info, limit)
            }.getOrNull()
        }

        val parsedCursor = PinterestCursor.decode(cursor) ?: return null
        return runCatching {
            fetchPinterestContinuation(parsedCursor)
        }.getOrNull()
    }

    private fun parsePinterestInitialPage(
        document: Document,
        info: PinterestUrlInfo,
        limit: Int,
    ): ScrapePage? {
        val scripts = document.select("script[id=__PWS_DATA__], script[id=__PWS_INITIAL_PROPS__]")
        scripts.forEach { element ->
            val data = element.data().takeIf { it.isNotBlank() } ?: return@forEach
            val root = runCatching { JSONObject(data) }.getOrNull() ?: return@forEach
            val resourceName = info.type.resourceName
            val resource = root.findPinterestResource(resourceName) ?: return@forEach
            val response = resource.optJSONObject("resource_response") ?: return@forEach
            val dataArray = response.optJSONArray("data") ?: JSONArray()
            val pins = parsePinterestPins(dataArray)
            val bookmark = response.optString("bookmark").takeIf { it.isNotBlank() }
            val options = resource.optJSONObject("resource")?.optJSONObject("options")
            val boardId = options?.optString("board_id").takeIf { !it.isNullOrBlank() }
            val slug = options?.optString("slug").takeIf { !it.isNullOrBlank() } ?: info.boardSlug
            val pageSize = options?.optInt("page_size")?.takeIf { it > 0 } ?: limit
            val nextCursor = bookmark?.let {
                PinterestCursor(
                    type = resourceName,
                    username = info.username,
                    slug = slug,
                    boardId = boardId,
                    pagePath = ensurePinterestPath(info.pagePath),
                    bookmark = it,
                    pageSize = pageSize
                ).encode()
            }
            if (pins.isNotEmpty()) {
                return ScrapePage(pins, nextCursor)
            }
        }
        return null
    }

    private suspend fun fetchPinterestContinuation(cursor: PinterestCursor): ScrapePage? =
        withContext(Dispatchers.IO) {
            val endpoint = when (cursor.type) {
                PinterestResourceType.BOARD.resourceName -> PINTEREST_BOARD_ENDPOINT
                PinterestResourceType.USER_PINS.resourceName -> PINTEREST_USER_PINS_ENDPOINT
                else -> return@withContext null
            }
            val options = JSONObject().apply {
                put("isPrefetch", false)
                put("page_size", cursor.pageSize)
                put("bookmarks", JSONArray().apply { put(cursor.bookmark) })
                cursor.boardId?.let { put("board_id", it) }
                cursor.slug?.let { put("slug", it) }
                cursor.username?.let { put("username", it) }
            }
            val payload = JSONObject()
                .put("options", options)
                .put("context", JSONObject())
            val responseBody = Jsoup.connect(endpoint)
                .ignoreContentType(true)
                .method(Connection.Method.GET)
                .userAgent(USER_AGENT)
                .referrer("https://www.google.com")
                .timeout(TIMEOUT_MS)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .data("source_url", cursor.pagePath)
                .data("data", payload.toString())
                .data("_", System.currentTimeMillis().toString())
                .execute()
                .body()
            val json = runCatching { JSONObject(responseBody) }.getOrNull() ?: return@withContext null
            val response = json.optJSONObject("resource_response") ?: return@withContext null
            val data = response.optJSONArray("data") ?: JSONArray()
            val pins = parsePinterestPins(data)
            val bookmark = response.optString("bookmark").takeIf { it.isNotBlank() }
            val nextCursor = bookmark?.let { cursor.copy(bookmark = it).encode() }
            ScrapePage(pins, nextCursor)
        }

    private fun parsePinterestPins(data: JSONArray): List<WallpaperItem> {
        val items = mutableListOf<WallpaperItem>()
        for (index in 0 until data.length()) {
            val pin = data.optJSONObject(index) ?: continue
            val id = pin.optString("id").takeIf { it.isNotBlank() } ?: continue
            val images = pin.optJSONObject("images") ?: continue
            val imageUrl = PINTEREST_IMAGE_ORDER.asSequence()
                .mapNotNull { sizeKey ->
                    images.optJSONObject(sizeKey)?.optString("url")?.takeIf { it.isNotBlank() }
                }
                .firstOrNull()
                ?.replace("\\u0026", "&")
                ?.replace("\\u003d", "=")
                ?: continue

            val title = pin.optString("title")
                .ifBlank { pin.optString("grid_title") }
                .ifBlank { pin.optJSONObject("grid_description")?.optString("text") ?: "" }
                .ifBlank { "Pinterest Pin" }

            val sourceUrl = pin.optString("link")
                .ifBlank { pin.optString("seo_link") }
                .ifBlank { "https://www.pinterest.com/pin/$id/" }

            val orig = images.optJSONObject("orig")
            val width = orig?.optInt("width")?.takeIf { it > 0 }
            val height = orig?.optInt("height")?.takeIf { it > 0 }

            items += WallpaperItem(
                id = id,
                title = title,
                imageUrl = imageUrl,
                sourceUrl = sourceUrl,
                width = width,
                height = height
            )
        }
        return items
    }

    private fun JSONObject.findPinterestResource(name: String): JSONObject? {
        optJSONArray("resourceResponses")?.let { array ->
            for (index in 0 until array.length()) {
                val candidate = array.optJSONObject(index)
                if (candidate?.optString("name") == name) {
                    return candidate
                }
            }
        }
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = opt(key)
            when (value) {
                is JSONObject -> {
                    val match = value.findPinterestResource(name)
                    if (match != null) return match
                }
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val child = value.optJSONObject(i) ?: continue
                        val match = child.findPinterestResource(name)
                        if (match != null) return match
                    }
                }
            }
        }
        return null
    }

    private fun parsePinterestUrl(pageUrl: String): PinterestUrlInfo? {
        val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!host.contains("pinterest.")) return null
        val segments = uri.path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        val username = segments[0]
        if (username.isBlank()) return null
        val defaultPath = if (segments.size >= 2) uri.path else "/$username/_pins/"
        val pagePath = ensurePinterestPath(defaultPath)
        return when {
            segments.size >= 2 && segments[1].equals("_pins", ignoreCase = true) ->
                PinterestUrlInfo(PinterestResourceType.USER_PINS, username, null, pagePath)
            segments.size >= 2 && segments[1].equals("_created", ignoreCase = true) ->
                PinterestUrlInfo(PinterestResourceType.USER_PINS, username, null, pagePath)
            segments.size >= 2 -> {
                val slug = segments[1]
                PinterestUrlInfo(PinterestResourceType.BOARD, username, slug, pagePath)
            }
            else -> PinterestUrlInfo(
                PinterestResourceType.USER_PINS,
                username,
                null,
                ensurePinterestPath("/$username/_pins/")
            )
        }
    }

    private data class PinterestUrlInfo(
        val type: PinterestResourceType,
        val username: String,
        val boardSlug: String?,
        val pagePath: String
    )

    private enum class PinterestResourceType(val resourceName: String) {
        BOARD("BoardFeedResource"),
        USER_PINS("UserPinsResource")
    }

    private data class PinterestCursor(
        val type: String,
        val username: String?,
        val slug: String?,
        val boardId: String?,
        val pagePath: String,
        val bookmark: String,
        val pageSize: Int
    ) {
        fun encode(): String = JSONObject().apply {
            put("type", type)
            put("username", username)
            put("slug", slug)
            put("boardId", boardId)
            put("pagePath", pagePath)
            put("bookmark", bookmark)
            put("pageSize", pageSize)
        }.toString()

        companion object {
            fun decode(raw: String): PinterestCursor? = runCatching {
                val json = JSONObject(raw)
                val type = json.optString("type").takeIf { it.isNotBlank() } ?: return@runCatching null
                val pathValue = json.optString("pagePath").takeIf { it.isNotBlank() } ?: return@runCatching null
                val bookmark = json.optString("bookmark").takeIf { it.isNotBlank() } ?: return@runCatching null
                val pageSize = json.optInt("pageSize").takeIf { it > 0 } ?: 30
                PinterestCursor(
                    type = type,
                    username = json.optString("username").takeIf { it.isNotBlank() },
                    slug = json.optString("slug").takeIf { it.isNotBlank() },
                    boardId = json.optString("boardId").takeIf { it.isNotBlank() },
                    pagePath = ensurePinterestPath(pathValue),
                    bookmark = bookmark,
                    pageSize = pageSize
                )
            }.getOrNull()
        }
    }

    private companion object {
        private const val TIMEOUT_MS = 15_000
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; WallBase) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        private const val IMAGE_SELECTOR =
            "img[src], img[data-src], img[data-lazy-src], img[data-original], img[data-actualsrc]"
        private val IMAGE_ATTRIBUTES =
            listOf("src", "data-src", "data-lazy-src", "data-original", "data-actualsrc")
        private val PINTEREST_IMAGE_ORDER = listOf("orig", "736x", "600x", "564x", "474x", "236x")

        private const val PINTEREST_BOARD_ENDPOINT =
            "https://www.pinterest.com/resource/BoardFeedResource/get/"
        private const val PINTEREST_USER_PINS_ENDPOINT =
            "https://www.pinterest.com/resource/UserPinsResource/get/"
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