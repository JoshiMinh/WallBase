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

        val extracted = when (feed.extraction.format.lowercase(Locale.ROOT)) {
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
            "xml", "rss", "atom" -> extractFromXml(
                xml = responseBody,
                manifest = manifest,
                feed = feed,
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

        val determinedNextCursor = when {
            extracted.isEmpty() -> null
            pagination.type.equals("cursor", ignoreCase = true) -> {
                extracted.lastOrNull()?.id ?: nextCursor
            }
            else -> nextCursor
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
        headersBuilder.add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        headersBuilder.add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")

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

    private fun extractFromXml(
        xml: String,
        manifest: SourceManifest,
        feed: FeedDefinition,
        rule: ExtractionRule,
        currentUrl: String
    ): List<WallpaperItem> {
        val doc = Jsoup.parse(xml, manifest.baseUrl, org.jsoup.parser.Parser.xmlParser())
        val itemSelector = rule.itemSelector ?: "entry, item"
        val elements = doc.select(itemSelector)
        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"

        return elements.mapNotNull { element ->
            runCatching {
                extractHtmlItem(element, rule.fields, manifest, sourceKey, currentUrl)
            }.getOrNull()
        }
    }

    private fun extractFromHtml(
        html: String,
        manifest: SourceManifest,
        feed: FeedDefinition,
        rule: ExtractionRule,
        currentUrl: String
    ): List<WallpaperItem> {
        val doc = Jsoup.parse(html, manifest.baseUrl)
        val itemSelector = rule.itemSelector ?: "img"
        val elements = doc.select(itemSelector)
        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"

        return elements.mapNotNull { element ->
            runCatching {
                extractHtmlItem(element, rule.fields, manifest, sourceKey, currentUrl)
            }.getOrNull()
        }
    }

    private fun extractHtmlItem(
        element: Element,
        fields: Map<String, FieldExtractor>,
        manifest: SourceManifest,
        sourceKey: String,
        currentUrl: String
    ): WallpaperItem? {
        val imageUrl = extractFieldValue(element, fields["imageUrl"] ?: fields["fullUrl"])
            ?: extractFieldValue(element, fields["thumbnailUrl"])
            ?: return null

        val thumbnailUrl = extractFieldValue(element, fields["thumbnailUrl"]) ?: imageUrl
        val title = extractFieldValue(element, fields["title"]) ?: "${manifest.name} Wallpaper"
        val sourceUrl = extractFieldValue(element, fields["sourceUrl"]) ?: currentUrl
        val id = extractFieldValue(element, fields["id"]) ?: UUID.nameUUIDFromBytes(imageUrl.toByteArray()).toString()
        val width = extractFieldValue(element, fields["width"])?.toIntOrNull()
        val height = extractFieldValue(element, fields["height"])?.toIntOrNull()

        return WallpaperItem(
            id = id,
            title = title.ifBlank { manifest.name },
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            sourceUrl = sourceUrl,
            sourceName = manifest.name,
            sourceKey = sourceKey,
            width = width,
            height = height
        )
    }

    private fun extractFieldValue(element: Element, extractor: FieldExtractor?): String? {
        if (extractor == null) return null
        val targetElement = if (extractor.selector.isNullOrBlank()) {
            element
        } else {
            element.selectFirst(extractor.selector) ?: return extractor.defaultValue
        }

        var rawValue = when (extractor.attribute?.lowercase(Locale.ROOT)) {
            null, "", "text" -> targetElement.text()
            "html" -> targetElement.html()
            "href", "abs:href" -> targetElement.absUrl("href").ifBlank { targetElement.attr("href") }
            "src", "abs:src" -> targetElement.absUrl("src").ifBlank { targetElement.attr("src") }
            else -> targetElement.attr(extractor.attribute)
        }

        if (rawValue.isBlank() && extractor.defaultValue != null) {
            rawValue = extractor.defaultValue
        }

        if (!extractor.regex.isNullOrBlank()) {
            val match = Regex(extractor.regex).find(rawValue)
            rawValue = match?.groupValues?.getOrNull(1) ?: match?.value.orEmpty()
        }

        if (extractor.regexReplace != null) {
            rawValue = rawValue.replace(Regex(extractor.regexReplace.find), extractor.regexReplace.replace)
        }

        return applyTransform(rawValue, extractor.transform)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractFromJson(
        json: String,
        manifest: SourceManifest,
        feed: FeedDefinition,
        rule: ExtractionRule,
        currentUrl: String
    ): List<WallpaperItem> {
        val rootData: Any? = runCatching {
            if (json.trim().startsWith("[")) {
                jsonListAdapter.fromJson(json)
            } else {
                jsonMapAdapter.fromJson(json)
            }
        }.getOrNull() ?: return emptyList()

        val itemArray = if (rule.itemSelector.isNullOrBlank()) {
            rootData as? List<Any?> ?: listOf(rootData)
        } else {
            resolveJsonPath(rootData, rule.itemSelector) as? List<Any?> ?: emptyList()
        }

        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"

        return itemArray.mapNotNull { itemNode ->
            if (itemNode == null) return@mapNotNull null
            runCatching {
                extractJsonItem(itemNode, rule.fields, manifest, sourceKey, currentUrl)
            }.getOrNull()
        }
    }

    private fun extractJsonItem(
        itemNode: Any,
        fields: Map<String, FieldExtractor>,
        manifest: SourceManifest,
        sourceKey: String,
        currentUrl: String
    ): WallpaperItem? {
        val imageUrl = extractJsonFieldValue(itemNode, fields["imageUrl"] ?: fields["fullUrl"])
            ?: extractJsonFieldValue(itemNode, fields["thumbnailUrl"])
            ?: return null

        val thumbnailUrl = extractJsonFieldValue(itemNode, fields["thumbnailUrl"]) ?: imageUrl
        val title = extractJsonFieldValue(itemNode, fields["title"]) ?: "${manifest.name} Wallpaper"
        val sourceUrl = extractJsonFieldValue(itemNode, fields["sourceUrl"]) ?: currentUrl
        val id = extractJsonFieldValue(itemNode, fields["id"]) ?: UUID.nameUUIDFromBytes(imageUrl.toByteArray()).toString()
        val width = extractJsonFieldValue(itemNode, fields["width"])?.toIntOrNull()
        val height = extractJsonFieldValue(itemNode, fields["height"])?.toIntOrNull()

        return WallpaperItem(
            id = id,
            title = title.ifBlank { manifest.name },
            imageUrl = imageUrl,
            thumbnailUrl = thumbnailUrl,
            sourceUrl = sourceUrl,
            sourceName = manifest.name,
            sourceKey = sourceKey,
            width = width,
            height = height
        )
    }

    private fun extractJsonFieldValue(node: Any, extractor: FieldExtractor?): String? {
        if (extractor == null) return null
        val targetPath = extractor.jsonPath ?: extractor.selector
        var rawValue = if (targetPath.isNullOrBlank()) {
            node.toString()
        } else {
            resolveJsonPath(node, targetPath)?.toString() ?: extractor.defaultValue
        } ?: return extractor.defaultValue

        if (!extractor.regex.isNullOrBlank()) {
            val match = Regex(extractor.regex).find(rawValue)
            rawValue = match?.groupValues?.getOrNull(1) ?: match?.value.orEmpty()
        }

        if (extractor.regexReplace != null) {
            rawValue = rawValue.replace(Regex(extractor.regexReplace.find), extractor.regexReplace.replace)
        }

        return applyTransform(rawValue, extractor.transform)
    }

    private fun extractFromRegex(
        content: String,
        manifest: SourceManifest,
        rule: ExtractionRule,
        currentUrl: String
    ): List<WallpaperItem> {
        val pattern = rule.itemSelector?.let { Regex(it, RegexOption.DOT_MATCHES_ALL) } ?: return emptyList()
        val matches = pattern.findAll(content)
        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"

        return matches.mapNotNull { match ->
            val imageUrl = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = match.groupValues.getOrNull(2) ?: manifest.name
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

    private fun applyTransform(value: String, transform: String?): String {
        return when (transform?.lowercase(Locale.ROOT)) {
            "url_decode" -> runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
            "trim" -> value.trim()
            "prepend_base_url" -> value // Handled if needed
            else -> value
        }
    }
}

