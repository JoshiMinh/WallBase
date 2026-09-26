package com.joshiminh.wallbase.scraper.engine

import android.net.Uri
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.scraper.model.ExtractionRule
import com.joshiminh.wallbase.scraper.model.FeedDefinition
import com.joshiminh.wallbase.scraper.model.FieldExtractor
import com.joshiminh.wallbase.scraper.model.PaginationConfig
import com.joshiminh.wallbase.scraper.model.SourceManifest
import com.joshiminh.wallbase.util.network.ScrapePage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeclarativeScraperEngine @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {

    private val jsonMapAdapter by lazy {
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        moshi.adapter<Map<String, Any?>>(type)
    }

    private val jsonListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, Any::class.java)
        moshi.adapter<List<Any?>>(type)
    }

    suspend fun scrape(
        manifest: SourceManifest,
        feedId: String? = null,
        query: String? = null,
        cursor: String? = null,
        limit: Int = 30,
        userSettings: Map<String, String> = emptyMap()
    ): ScrapePage = withContext(Dispatchers.IO) {
        val isSearching = !query.isNullOrBlank() && manifest.search != null
        val feed = when {
            isSearching -> manifest.search!!
            !feedId.isNullOrBlank() -> manifest.feeds.firstOrNull { it.id == feedId }
                ?: manifest.feeds.firstOrNull()
            else -> manifest.feeds.firstOrNull()
        } ?: throw IllegalArgumentException("No feed or search definition found in manifest '${manifest.name}'")

        val pagination = feed.pagination ?: PaginationConfig()
        val (pageParam, currentVal, nextCursor) = calculatePagination(pagination, cursor, limit)

        val targetUrl = buildUrl(
            template = feed.url,
            baseUrl = manifest.baseUrl,
            query = query.orEmpty(),
            pageParam = pageParam,
            cursor = cursor.orEmpty(),
            limit = limit,
            userSettings = userSettings
        )

        val request = buildHttpRequest(
            manifest = manifest,
            feed = feed,
            url = targetUrl,
            query = query,
            pageParam = pageParam,
            userSettings = userSettings
        )

        val responseBody = runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                }
                response.body?.string().orEmpty()
            }
        }.getOrElse { error ->
            throw IllegalStateException("Failed fetching ${manifest.name}: ${error.localizedMessage}", error)
        }

        val (extracted, dynamicNextCursor) = when (feed.extraction.format.lowercase(Locale.ROOT)) {
            "json" -> extractFromJson(
                json = responseBody,
                manifest = manifest,
                feed = feed,
                rule = feed.extraction,
                currentUrl = targetUrl
            )
            "regex" -> extractFromRegex(
                content = responseBody,
                manifest = manifest,
                rule = feed.extraction,
                currentUrl = targetUrl
            )
            else -> extractFromHtml(
                html = responseBody,
                manifest = manifest,
                feed = feed,
                rule = feed.extraction,
                currentUrl = targetUrl
            )
        }

        val determinedNextCursor = if (extracted.isEmpty()) {
            null
        } else if (pagination.type.equals("cursor", ignoreCase = true)) {
            dynamicNextCursor?.takeIf { it.isNotBlank() }
        } else {
            dynamicNextCursor ?: nextCursor
        }

        ScrapePage(
            wallpapers = extracted,
            nextCursor = determinedNextCursor
        )
    }

    private fun calculatePagination(
        pagination: PaginationConfig,
        cursor: String?,
        limit: Int
    ): Triple<String, Int, String?> {
        return when (pagination.type.lowercase(Locale.ROOT)) {
            "cursor" -> {
                val current = cursor.orEmpty()
                Triple(current, 0, null)
            }
            "offset" -> {
                val currentOffset = cursor?.toIntOrNull() ?: 0
                val nextOffset = currentOffset + limit
                Triple(currentOffset.toString(), currentOffset, nextOffset.toString())
            }
            "none" -> {
                Triple("1", 1, null)
            }
            else -> { // page_number default
                val currentPage = cursor?.toIntOrNull() ?: pagination.startPage
                val nextPage = currentPage + pagination.step
                Triple(currentPage.toString(), currentPage, nextPage.toString())
            }
        }
    }

    private fun buildUrl(
        template: String,
        baseUrl: String,
        query: String,
        pageParam: String,
        cursor: String,
        limit: Int,
        userSettings: Map<String, String>
    ): String {
        val encodedQuery = runCatching { URLEncoder.encode(query, StandardCharsets.UTF_8.name()) }.getOrDefault(query)
        var url = template
            .replace("{baseUrl}", baseUrl.trimEnd('/'))
            .replace("{query}", encodedQuery)
            .replace("{page}", pageParam)
            .replace("{cursor}", cursor)
            .replace("{limit}", limit.toString())
            .replace("{offset}", pageParam)

        userSettings.forEach { (key, value) ->
            url = url.replace("{setting:$key}", value)
        }

        return if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            "${baseUrl.trimEnd('/')}/${url.trimStart('/')}"
        }
    }

    private fun buildHttpRequest(
        manifest: SourceManifest,
        feed: FeedDefinition,
        url: String,
        query: String?,
        pageParam: String,
        userSettings: Map<String, String>
    ): Request {
        val headersBuilder = Headers.Builder()
        headersBuilder.add("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36 WallBase/6.5")

        manifest.headers?.forEach { (k, v) ->
            headersBuilder.set(k, substituteVariables(v, query, pageParam, userSettings))
        }
        feed.headers?.forEach { (k, v) ->
            headersBuilder.set(k, substituteVariables(v, query, pageParam, userSettings))
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .headers(headersBuilder.build())

        if (feed.method.equals("POST", ignoreCase = true)) {
            val bodyContent = feed.body?.let { substituteVariables(it, query, pageParam, userSettings) }.orEmpty()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            requestBuilder.post(bodyContent.toRequestBody(mediaType))
        } else {
            requestBuilder.get()
        }

        return requestBuilder.build()
    }

    private fun substituteVariables(
        template: String,
        query: String?,
        page: String,
        userSettings: Map<String, String>
    ): String {
        var result = template
            .replace("{query}", query.orEmpty())
            .replace("{page}", page)
        userSettings.forEach { (k, v) ->
            result = result.replace("{setting:$k}", v)
        }
        return result
    }

    private fun extractFromHtml(
        html: String,
        manifest: SourceManifest,
        feed: FeedDefinition,
        rule: ExtractionRule,
        currentUrl: String
    ): Pair<List<WallpaperItem>, String?> {
        val doc = Jsoup.parse(html, manifest.baseUrl)
        val itemSelector = rule.itemSelector ?: "img"
        val elements = doc.select(itemSelector)
        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"

        val items = elements.mapNotNull { element ->
            runCatching {
                extractHtmlItem(element, rule.fields, manifest, sourceKey, currentUrl)
            }.getOrNull()
        }

        val nextCursor = rule.nextCursorSelector?.let { selector ->
            doc.selectFirst(selector)?.let { el ->
                el.attr("href").ifBlank { el.text() }
            }
        }

        return Pair(items, nextCursor)
    }

    private fun extractHtmlItem(
        element: Element,
        fields: Map<String, FieldExtractor>,
        manifest: SourceManifest,
        sourceKey: String,
        currentUrl: String
    ): WallpaperItem? {
        val rawImageUrl = extractFieldValue(element, fields["imageUrl"] ?: fields["fullUrl"], manifest.baseUrl)
            ?: extractFieldValue(element, fields["thumbnailUrl"], manifest.baseUrl)
            ?: return null

        val imageUrl = rawImageUrl.sanitizeUrl() ?: return null
        val rawThumb = extractFieldValue(element, fields["thumbnailUrl"], manifest.baseUrl)?.sanitizeUrl()
        val thumbnailUrl = if (rawThumb != null && rawThumb.isHttpUrl()) rawThumb else imageUrl
        val rawTitle = extractFieldValue(element, fields["title"], manifest.baseUrl)
        val title = unescapeHtml(rawTitle)?.ifBlank { manifest.name } ?: manifest.name
        val sourceUrl = extractFieldValue(element, fields["sourceUrl"], manifest.baseUrl)?.sanitizeUrl() ?: currentUrl
        val id = extractFieldValue(element, fields["id"], manifest.baseUrl)?.takeIf { it.isNotBlank() }
            ?: UUID.nameUUIDFromBytes(imageUrl.toByteArray()).toString()
        val width = extractFieldValue(element, fields["width"], manifest.baseUrl)?.toIntOrNull()
        val height = extractFieldValue(element, fields["height"], manifest.baseUrl)?.toIntOrNull()

        return WallpaperItem(
            id = id,
            title = title,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            sourceUrl = sourceUrl,
            sourceName = manifest.name,
            sourceKey = sourceKey,
            width = width,
            height = height
        )
    }

    private fun extractFieldValue(element: Element, extractor: FieldExtractor?, baseUrl: String): String? {
        if (extractor == null) return null
        val targetElement = when {
            extractor.selector.isNullOrBlank() -> element
            else -> element.selectFirst(extractor.selector)
                ?: extractor.fallbackSelector?.let { element.selectFirst(it) }
                ?: return extractor.defaultValue
        }

        val requestedAttr = extractor.attribute?.lowercase(Locale.ROOT)
        var rawValue: String = when (requestedAttr) {
            null, "", "text" -> targetElement.text()
            "html" -> targetElement.html()
            "href", "abs:href" -> targetElement.absUrl("href").ifBlank { targetElement.attr("href") }
            "src", "abs:src" -> extractImageSrc(targetElement)
            else -> targetElement.attr(extractor.attribute)
        }

        if (rawValue.isBlank() && (requestedAttr == "src" || requestedAttr == "abs:src" || requestedAttr == null)) {
            rawValue = extractImageSrc(targetElement)
        }

        if (rawValue.isBlank() && extractor.defaultValue != null) {
            rawValue = extractor.defaultValue
        }

        if (!extractor.regex.isNullOrBlank()) {
            val match = Regex(extractor.regex).find(rawValue)
            rawValue = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() } ?: match?.value.orEmpty()
        }

        if (extractor.regexReplace != null) {
            rawValue = rawValue.replace(Regex(extractor.regexReplace.find), extractor.regexReplace.replace)
        }

        return applyTransform(rawValue, extractor.transform, baseUrl)
    }

    private fun extractImageSrc(element: Element): String {
        val candidates = listOf(
            "abs:data-src", "abs:data-lazy-src", "abs:data-original", "abs:data-actualsrc",
            "abs:data-big-image", "abs:data-src-retina", "abs:src",
            "data-src", "data-lazy-src", "data-original", "data-actualsrc",
            "data-big-image", "data-src-retina", "src"
        )
        for (attr in candidates) {
            val v = if (attr.startsWith("abs:")) element.absUrl(attr.removePrefix("abs:")) else element.attr(attr)
            if (v.isNotBlank() && !v.startsWith("data:image", ignoreCase = true) && !v.contains("blank.gif", ignoreCase = true)) {
                return v
            }
        }

        // Try srcset
        val srcset = element.attr("srcset").ifBlank { element.attr("data-srcset") }
        if (srcset.isNotBlank()) {
            val parsed = parseBestSrcFromSrcset(srcset)
            if (parsed.isNotBlank()) return parsed
        }

        // Check child img in case of picture or wrapper container
        val childImg = element.selectFirst("img, picture source")
        if (childImg != null && childImg != element) {
            return extractImageSrc(childImg)
        }

        return element.absUrl("src").ifBlank { element.attr("src") }
    }

    private fun parseBestSrcFromSrcset(srcset: String): String {
        return srcset.split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split(Regex("\\s+"))
                if (parts.isNotEmpty() && parts[0].isNotBlank()) parts[0] else null
            }
            .lastOrNull()
            .orEmpty()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractFromJson(
        json: String,
        manifest: SourceManifest,
        feed: FeedDefinition,
        rule: ExtractionRule,
        currentUrl: String
    ): Pair<List<WallpaperItem>, String?> {
        val rootData: Any? = runCatching {
            if (json.trim().startsWith("[")) {
                jsonListAdapter.fromJson(json)
            } else {
                jsonMapAdapter.fromJson(json)
            }
        }.getOrNull() ?: return Pair(emptyList(), null)

        val itemArray = if (rule.itemSelector.isNullOrBlank()) {
            rootData as? List<Any?> ?: listOf(rootData)
        } else {
            resolveJsonPath(rootData, rule.itemSelector) as? List<Any?> ?: emptyList()
        }

        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"

        val items = itemArray.mapNotNull { itemNode ->
            if (itemNode == null) return@mapNotNull null
            runCatching {
                extractJsonItem(itemNode, rule.fields, manifest, sourceKey, currentUrl)
            }.getOrNull()
        }

        val cursorPath = rule.nextCursorJsonPath ?: rule.nextCursorSelector
        val nextCursor = if (!cursorPath.isNullOrBlank()) {
            resolveJsonPath(rootData, cursorPath)?.toJsonString()
        } else if (rootData is Map<*, *>) {
            // Auto detect common cursor fields
            (resolveJsonPath(rootData, "data.after")
                ?: resolveJsonPath(rootData, "after")
                ?: resolveJsonPath(rootData, "next_cursor")
                ?: resolveJsonPath(rootData, "meta.next_cursor")
                ?: resolveJsonPath(rootData, "next_page"))?.toJsonString()
        } else {
            null
        }

        return Pair(items, nextCursor)
    }

    private fun extractJsonItem(
        itemNode: Any,
        fields: Map<String, FieldExtractor>,
        manifest: SourceManifest,
        sourceKey: String,
        currentUrl: String
    ): WallpaperItem? {
        val rawImageUrl = extractJsonFieldValue(itemNode, fields["imageUrl"] ?: fields["fullUrl"], manifest.baseUrl)
            ?: extractJsonFieldValue(itemNode, fields["thumbnailUrl"], manifest.baseUrl)
            ?: return null

        val imageUrl = rawImageUrl.sanitizeUrl() ?: return null
        val rawThumb = extractJsonFieldValue(itemNode, fields["thumbnailUrl"], manifest.baseUrl)?.sanitizeUrl()
        val thumbnailUrl = if (rawThumb != null && rawThumb.isHttpUrl()) rawThumb else imageUrl
        val rawTitle = extractJsonFieldValue(itemNode, fields["title"], manifest.baseUrl)
        val title = unescapeHtml(rawTitle)?.ifBlank { manifest.name } ?: manifest.name
        val sourceUrl = extractJsonFieldValue(itemNode, fields["sourceUrl"], manifest.baseUrl)?.sanitizeUrl() ?: currentUrl
        val id = extractJsonFieldValue(itemNode, fields["id"], manifest.baseUrl)?.takeIf { it.isNotBlank() }
            ?: UUID.nameUUIDFromBytes(imageUrl.toByteArray()).toString()
        val width = extractJsonFieldValue(itemNode, fields["width"], manifest.baseUrl)?.toIntOrNull()
        val height = extractJsonFieldValue(itemNode, fields["height"], manifest.baseUrl)?.toIntOrNull()

        return WallpaperItem(
            id = id,
            title = title,
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            sourceUrl = sourceUrl,
            sourceName = manifest.name,
            sourceKey = sourceKey,
            width = width,
            height = height
        )
    }

    private fun extractJsonFieldValue(node: Any, extractor: FieldExtractor?, baseUrl: String): String? {
        if (extractor == null) return null
        val targetPath = extractor.jsonPath ?: extractor.selector
        var rawValue: String? = if (!targetPath.isNullOrBlank()) {
            resolveJsonPath(node, targetPath).toJsonString()
        } else null

        if (rawValue.isNullOrBlank()) {
            val fallbackPath = extractor.fallbackJsonPath ?: extractor.fallbackSelector
            if (!fallbackPath.isNullOrBlank()) {
                rawValue = resolveJsonPath(node, fallbackPath).toJsonString()
            }
        }

        if (rawValue.isNullOrBlank()) {
            rawValue = if (targetPath.isNullOrBlank()) node.toJsonString() else extractor.defaultValue
        }

        if (rawValue.isNullOrBlank()) return extractor.defaultValue

        if (!extractor.regex.isNullOrBlank()) {
            val match = Regex(extractor.regex).find(rawValue)
            rawValue = match?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() } ?: match?.value.orEmpty()
        }

        if (extractor.regexReplace != null) {
            rawValue = rawValue.replace(Regex(extractor.regexReplace.find), extractor.regexReplace.replace)
        }

        return applyTransform(rawValue, extractor.transform, baseUrl)
    }

    private fun Any?.toJsonString(): String? {
        if (this == null) return null
        return when (this) {
            is Number -> {
                val d = this.toDouble()
                if (d % 1.0 == 0.0 && d >= Long.MIN_VALUE && d <= Long.MAX_VALUE) {
                    this.toLong().toString()
                } else {
                    this.toString()
                }
            }
            else -> this.toString()
        }
    }

    private fun extractFromRegex(
        content: String,
        manifest: SourceManifest,
        rule: ExtractionRule,
        currentUrl: String
    ): Pair<List<WallpaperItem>, String?> {
        val pattern = rule.itemSelector?.let { Regex(it, RegexOption.DOT_MATCHES_ALL) } ?: return Pair(emptyList(), null)
        val matches = pattern.findAll(content)
        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"

        val items = matches.mapNotNull { match ->
            val rawImageUrl = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val imageUrl = rawImageUrl.sanitizeUrl() ?: return@mapNotNull null
            val title = unescapeHtml(match.groupValues.getOrNull(2)) ?: manifest.name
            val id = UUID.nameUUIDFromBytes(imageUrl.toByteArray()).toString()
            WallpaperItem(
                id = id,
                title = title,
                imageUrl = imageUrl,
                thumbnailUrl = imageUrl,
                sourceUrl = currentUrl,
                sourceName = manifest.name,
                sourceKey = sourceKey
            )
        }.toList()

        return Pair(items, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveJsonPath(root: Any?, path: String): Any? {
        if (root == null || path.isBlank()) return root
        val parts = path.split('.')
        var current: Any? = root

        for (part in parts) {
            when (current) {
                is Map<*, *> -> {
                    current = (current as Map<String, Any?>)[part]
                }
                is List<*> -> {
                    val index = part.toIntOrNull()
                    current = if (index != null && index in (current as List<Any?>).indices) {
                        current[index]
                    } else {
                        null
                    }
                }
                else -> return null
            }
        }
        return current
    }

    private fun applyTransform(value: String, transform: String?, baseUrl: String): String {
        return when (transform?.lowercase(Locale.ROOT)) {
            "url_decode" -> runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
            "trim" -> value.trim()
            "ensure_https" -> {
                when {
                    value.startsWith("https://", ignoreCase = true) -> value
                    value.startsWith("//") -> "https:$value"
                    value.startsWith("http://", ignoreCase = true) -> "https://${value.substring(7)}"
                    else -> "https://$value"
                }
            }
            "prepend_base_url" -> {
                when {
                    value.startsWith("http://") || value.startsWith("https://") -> value
                    value.startsWith("//") -> "https:$value"
                    value.startsWith("/") -> "${baseUrl.trimEnd('/')}$value"
                    else -> "${baseUrl.trimEnd('/')}/$value"
                }
            }
            "pixiv_artwork" -> {
                if (value.all { it.isDigit() }) "https://www.pixiv.net/artworks/$value" else value
            }
            "html_unescape" -> unescapeHtml(value) ?: value
            "strip_query" -> value.substringBefore('?')
            else -> value
        }
    }

    private fun String.sanitizeUrl(): String? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        return trimmed
            .replace("&amp;", "&")
            .replace("\\/", "/")
            .replace(" ", "%20")
    }

    private fun String.isHttpUrl(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("//")) &&
            !lower.endsWith("/default") &&
            !lower.equals("default", ignoreCase = true) &&
            !lower.equals("self", ignoreCase = true) &&
            !lower.equals("nsfw", ignoreCase = true) &&
            !lower.equals("image", ignoreCase = true) &&
            !lower.equals("spoiler", ignoreCase = true)
    }

    private fun unescapeHtml(text: String?): String? {
        if (text == null) return null
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x2F;", "/")
            .replace("&#x27;", "'")
            .trim()
    }
}

