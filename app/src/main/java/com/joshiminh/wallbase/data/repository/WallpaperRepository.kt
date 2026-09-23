package com.joshiminh.wallbase.data.repository

import android.net.Uri
import com.joshiminh.wallbase.sources.RedditCommunity
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.sources.RedditListingResponse
import com.joshiminh.wallbase.sources.RedditPost
import com.joshiminh.wallbase.sources.RedditService
import com.joshiminh.wallbase.sources.RedditSubredditChild
import com.joshiminh.wallbase.sources.RedditSubredditListingResponse
import com.joshiminh.wallbase.sources.WallhavenResponse
import com.joshiminh.wallbase.sources.WallhavenService
import com.joshiminh.wallbase.sources.WallhavenWallpaper
import com.joshiminh.wallbase.util.network.ScrapePage
import com.joshiminh.wallbase.util.network.WebScraper
import com.joshiminh.wallbase.scraper.engine.DeclarativeScraperEngine
import com.joshiminh.wallbase.scraper.repository.ExtensionRepositoryManager
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class WallpaperPage(
    val wallpapers: List<WallpaperItem>,
    val nextCursor: String?
)

@Singleton
class WallpaperRepository @Inject constructor(
    private val redditService: RedditService,
    private val webScraper: WebScraper,
    private val wallhavenService: WallhavenService,
    private val credentialStore: SourceCredentialStore,
    private val declarativeScraperEngine: DeclarativeScraperEngine,
    private val extensionRepositoryManager: ExtensionRepositoryManager,
) {
    private val pinterestQuery: String = DEFAULT_PINTEREST_QUERY
    private val customWebsiteUrl: String = DEFAULT_CUSTOM_WEBSITE

    fun getWallpaperPagingData(
        source: Source,
        query: String? = null,
        pageSize: Int = 24
    ): Flow<PagingData<WallpaperItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = pageSize,
                prefetchDistance = 6,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { WallpaperPagingSource(this, source, query) }
        ).flow
    }

    suspend fun fetchWallpapersFor(
        source: Source,
        query: String? = null,
        cursor: String? = null
    ): WallpaperPage {
        val provider = source.providerKey.lowercase(Locale.ROOT)
        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val page = when (provider) {
            SourceKeys.WALLHAVEN -> fetchWallhavenWallpapers(
                config = source.config,
                query = trimmedQuery,
                cursor = cursor
            )
            SourceKeys.REDDIT -> fetchRedditWallpapers(
                subreddit = source.config ?: DEFAULT_REDDIT_SUBREDDIT,
                query = trimmedQuery,
                cursor = cursor
            )
            SourceKeys.PINTEREST -> fetchPinterestWallpapers(
                config = source.config,
                query = trimmedQuery,
                cursor = cursor
            )
            SourceKeys.WEBSITES -> fetchWebsiteWallpapers(
                config = source.config,
                query = trimmedQuery,
                cursor = cursor
            )
            SourceKeys.EXTENSION -> fetchExtensionWallpapers(
                source = source,
                query = trimmedQuery,
                cursor = cursor
            )
            else -> throw UnsupportedSourceException(source.title)
        }
        val mapped = page.wallpapers.map {
            it.copy(
                sourceName = source.title,
                sourceKey = source.key
            )
        }
        return WallpaperPage(wallpapers = mapped, nextCursor = page.nextCursor)
    }

    private suspend fun fetchExtensionWallpapers(
        source: Source,
        query: String?,
        cursor: String?
    ): WallpaperPage = withContext(Dispatchers.IO) {
        val extensionId = source.config ?: source.key.removePrefix("${SourceKeys.EXTENSION}:")
        val manifest = extensionRepositoryManager.getManifestById(extensionId)
            ?: throw IllegalStateException("Extension manifest not found for '${source.title}'")

        val result = declarativeScraperEngine.scrape(
            manifest = manifest,
            query = query,
            cursor = cursor
        )

        WallpaperPage(
            wallpapers = result.wallpapers,
            nextCursor = result.nextCursor
        )
    }

    private suspend fun fetchWallhavenWallpapers(
        config: String?,
        query: String?,
        cursor: String?
    ): WallpaperPage = withContext(Dispatchers.IO) {
        val parsed = parseWallhavenConfig(config)
        val pageNumber = cursor?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        when (parsed.mode) {
            WallhavenMode.COLLECTION -> {
                val username = parsed.collectionUser
                val collectionId = parsed.collectionId
                if (username.isNullOrBlank() || collectionId.isNullOrBlank()) {
                    WallpaperPage(emptyList(), nextCursor = null)
                } else {
                    runCatching {
                        val options = parsed.params.toMutableMap()
                        options["page"] = pageNumber.toString()
                        wallhavenService.getCollection(
                            username = username,
                            collectionId = collectionId,
                            page = pageNumber,
                            options = options.ifEmpty { null }
                        ).toWallpaperPage(pageNumber)
                    }.getOrElse { WallpaperPage(emptyList(), nextCursor = null) }
                }
            }

            WallhavenMode.SEARCH -> {
                val params = parsed.params.toMutableMap()
                if (!query.isNullOrBlank()) {
                    params["q"] = query
                }
                params.putIfAbsent("purity", "100")
                params.putIfAbsent("categories", "111")
                params["page"] = pageNumber.toString()
                runCatching {
                    wallhavenService.search(params).toWallpaperPage(pageNumber)
                }.getOrElse { WallpaperPage(emptyList(), nextCursor = null) }
            }
        }
    }

    private suspend fun fetchPinterestWallpapers(
        config: String?,
        query: String?,
        cursor: String?
    ): WallpaperPage = withContext(Dispatchers.IO) {
        val scrapePage = when {
            !query.isNullOrBlank() -> webScraper.scrapePinterest(
                query = query,
                limit = 30,
                cursor = cursor
            )
            !config.isNullOrBlank() -> webScraper.scrapeImagesFromUrl(
                url = config,
                limit = 30,
                cursor = cursor
            )
            else -> webScraper.scrapePinterest(
                query = pinterestQuery,
                limit = 30,
                cursor = cursor
            )
        }
        WallpaperPage(scrapePage.wallpapers, scrapePage.nextCursor)
    }

    private suspend fun fetchWebsiteWallpapers(
        config: String?,
        query: String?,
        cursor: String?
    ): WallpaperPage = withContext(Dispatchers.IO) {
        val baseUrl = config ?: customWebsiteUrl
        val targetUrl = query?.let { buildWebsiteSearchUrl(baseUrl, it) } ?: baseUrl
        val scrapePage = webScraper.scrapeImagesFromUrl(
            url = targetUrl,
            limit = 30,
            cursor = cursor
        )
        WallpaperPage(scrapePage.wallpapers, scrapePage.nextCursor)
    }

    suspend fun searchRedditCommunities(query: String, limit: Int = 10): List<RedditCommunity> =
        withContext(Dispatchers.IO) {
            val apiResult = runCatching {
                redditService.searchSubreddits(query = query, limit = limit).toCommunities()
            }.getOrNull()
            if (!apiResult.isNullOrEmpty()) return@withContext apiResult

            val normalized = query.normalizeSubredditName()
            if (normalized.isNotBlank()) {
                listOf(
                    RedditCommunity(
                        name = normalized,
                        displayName = "r/$normalized",
                        title = "r/$normalized",
                        description = "Subreddit feed",
                        iconUrl = null
                    )
                )
            } else {
                emptyList()
            }
        }

    private suspend fun fetchRedditWallpapers(
        subreddit: String,
        query: String?,
        cursor: String?
    ): WallpaperPage =
        withContext(Dispatchers.IO) {
            val normalized = subreddit.normalizeSubredditName().ifBlank { DEFAULT_REDDIT_SUBREDDIT }
            val response = runCatching {
                if (query.isNullOrBlank()) {
                    redditService.fetchSubreddit(
                        subreddit = normalized,
                        limit = REDDIT_PAGE_LIMIT,
                        after = cursor,
                    )
                } else {
                    redditService.searchSubredditPosts(
                        subreddit = normalized,
                        query = query,
                        restrictToSubreddit = 1,
                        limit = REDDIT_PAGE_LIMIT,
                        after = cursor,
                    )
                }
            }.getOrNull()

            val items = response?.toWallpaperItems().orEmpty()
            if (items.isNotEmpty()) {
                WallpaperPage(
                    wallpapers = items,
                    nextCursor = response?.data?.after,
                )
            } else {
                val rssResult = webScraper.scrapeReddit(normalized, query = query, cursor = cursor)
                WallpaperPage(rssResult.wallpapers, rssResult.nextCursor)
            }
        }

    private fun RedditPost.resolveImages(): List<WallpaperItem> {
        if (!mediaMetadata.isNullOrEmpty()) {
            val galleryItems = mediaMetadata.values
                .mapNotNull { metadata ->
                    val source = metadata.s ?: return@mapNotNull null
                    val imageUrl = source.url?.replace("&amp;", "&") ?: return@mapNotNull null
                    WallpaperItem(
                        id = "reddit_${id}_${imageUrl.hashCode()}",
                        title = title,
                        imageUrl = imageUrl,
                        sourceUrl = permalink?.let { "https://www.reddit.com$it" } ?: imageUrl,
                        width = source.width,
                        height = source.height
                    )
                }
            if (galleryItems.isNotEmpty()) return galleryItems
        }

        val directUrl = overriddenUrl ?: url
        val previewSource = preview?.images.orEmpty().firstOrNull()?.source
        val previewUrl = previewSource?.url?.replace("&amp;", "&")
        val cleanDirectUrl = directUrl?.replace("&amp;", "&")

        val candidateUrl = when {
            cleanDirectUrl != null && cleanDirectUrl.hasSupportedImageExtension() -> cleanDirectUrl
            postHint == "image" && cleanDirectUrl != null -> cleanDirectUrl
            previewUrl != null -> previewUrl
            cleanDirectUrl != null && (cleanDirectUrl.contains("i.redd.it") || cleanDirectUrl.contains("imgur.com")) -> cleanDirectUrl
            else -> null
        }?.let { if (it.startsWith("http://", ignoreCase = true)) "https://" + it.substring(7) else it }

        return if (candidateUrl != null) {
            listOf(
                WallpaperItem(
                    id = "reddit_$id",
                    title = title,
                    imageUrl = candidateUrl,
                    sourceUrl = permalink?.let { "https://www.reddit.com$it" } ?: candidateUrl,
                    width = previewSource?.width,
                    height = previewSource?.height
                )
            )
        } else {
            emptyList()
        }
    }

    private fun RedditListingResponse.toWallpaperItems(): List<WallpaperItem> {
        return data?.children.orEmpty().flatMap { child ->
            child.data?.resolveImages().orEmpty()
        }
    }

    private fun String.hasSupportedImageExtension(): Boolean {
        val normalized = substringBefore('?')
            .substringBefore('#')
            .lowercase(Locale.ROOT)
        return normalized.endsWith(".jpg") ||
                normalized.endsWith(".jpeg") ||
                normalized.endsWith(".png") ||
                normalized.endsWith(".webp")
    }

    private fun RedditSubredditListingResponse.toCommunities(): List<RedditCommunity> {
        return data?.children.orEmpty()
            .mapNotNull(RedditSubredditChild::data)
            .mapNotNull { subreddit ->
                val name = subreddit.displayName.ifBlank { subreddit.name }
                if (name.isBlank()) return@mapNotNull null
                RedditCommunity(
                    name = name.normalizeSubredditName(),
                    displayName = subreddit.displayNamePrefixed.takeIf { it.isNotBlank() }
                        ?: "r/${name.normalizeSubredditName()}",
                    title = subreddit.title.takeIf { it.isNotBlank() } ?: name,
                    description = subreddit.publicDescription?.takeIf { it.isNotBlank() },
                    iconUrl = (subreddit.iconImage ?: subreddit.communityIcon)
                        ?.replace("&amp;", "&")
                )
            }
    }

    private fun String.normalizeSubredditName(): String {
        val trimmed = this.trim()
        if (trimmed.isBlank()) return ""
        return trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .substringAfter("reddit.com/", trimmed)
            .removePrefix("r/")
            .substringBefore('/')
            .trim()
            .lowercase(Locale.ROOT)
    }

    private fun buildWebsiteSearchUrl(baseUrl: String, query: String): String {
        return runCatching {
            val uri = Uri.parse(baseUrl)
            val builder = uri.buildUpon()
            builder.clearQuery()
            uri.queryParameterNames
                .filter { it != "q" }
                .forEach { name ->
                    val value = uri.getQueryParameter(name)
                    if (!value.isNullOrBlank()) {
                        builder.appendQueryParameter(name, value)
                    }
                }
            builder.appendQueryParameter("q", query)
            builder.build().toString()
        }.getOrElse {
            val separator = if (baseUrl.contains('?')) '&' else '?'
            "$baseUrl$separator" + "q=${Uri.encode(query)}"
        }
    }

    private fun parseQueryParameters(config: String?): Map<String, String> {
        if (config.isNullOrBlank()) return emptyMap()
        val uri = runCatching { Uri.parse(config) }.getOrElse { return emptyMap() }
        return uri.queryParameterNames.associateWith { name ->
            uri.getQueryParameter(name).orEmpty()
        }.filterValues { it.isNotBlank() }
    }

    private fun parseWallhavenConfig(config: String?): WallhavenConfig {
        if (config.isNullOrBlank()) {
            return WallhavenConfig(mode = WallhavenMode.SEARCH, params = mapOf("sorting" to "toplist"))
        }
        val uri = runCatching { Uri.parse(config) }.getOrElse {
            return WallhavenConfig(mode = WallhavenMode.SEARCH)
        }
        val segments = uri.pathSegments.filter { it.isNotBlank() }

        // Collections: /collections/{username}/{id}
        if (segments.size >= 3 && segments[0].equals("collections", ignoreCase = true)) {
            val username = segments.getOrNull(1)
            val collectionId = segments.getOrNull(2)
            if (!username.isNullOrBlank() && !collectionId.isNullOrBlank()) {
                val params = parseQueryParams(uri)
                return WallhavenConfig(
                    mode = WallhavenMode.COLLECTION,
                    collectionUser = username,
                    collectionId = collectionId,
                    params = params
                )
            }
        }

        // Collections / Favorites under user: /user/{username}/collections/{id} or /user/{username}/favorites/{id}
        if (segments.size >= 4 && segments[0].equals("user", ignoreCase = true) &&
            (segments[2].equals("collections", ignoreCase = true) || segments[2].equals("favorites", ignoreCase = true))
        ) {
            val username = segments.getOrNull(1)
            val collectionId = segments.getOrNull(3)
            if (!username.isNullOrBlank() && !collectionId.isNullOrBlank()) {
                val params = parseQueryParams(uri)
                return WallhavenConfig(
                    mode = WallhavenMode.COLLECTION,
                    collectionUser = username,
                    collectionId = collectionId,
                    params = params
                )
            }
        }

        val params = parseQueryParams(uri).toMutableMap()

        // User profile uploads: /user/{username} or /user/{username}/uploads
        if (segments.size >= 2 && segments[0].equals("user", ignoreCase = true)) {
            val username = segments[1]
            if (username.isNotBlank()) {
                params.putIfAbsent("q", "@$username")
            }
        }

        // Tag search: /tag/{id}
        if (segments.size >= 2 && segments[0].equals("tag", ignoreCase = true)) {
            val tagId = segments[1]
            if (tagId.isNotBlank()) {
                params.putIfAbsent("q", "id:$tagId")
            }
        }

        val firstSegment = segments.firstOrNull()?.lowercase(Locale.ROOT)
        when (firstSegment) {
            "toplist" -> params.putIfAbsent("sorting", "toplist")
            "latest" -> params.putIfAbsent("sorting", "date_added")
            "random" -> params.putIfAbsent("sorting", "random")
            "hot" -> params.putIfAbsent("sorting", "hot")
        }
        return WallhavenConfig(mode = WallhavenMode.SEARCH, params = params)
    }

    private fun parseQueryParams(uri: Uri): Map<String, String> {
        val params = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { name ->
            val value = uri.getQueryParameter(name)
            if (!value.isNullOrBlank()) {
                params[name] = value
            }
        }
        return params
    }

    private fun WallhavenResponse.toWallpaperPage(requestedPage: Int): WallpaperPage {
        val wallpapers = data.orEmpty().mapNotNull { it.toWallpaperItem() }
        val current = meta?.currentPage
        val last = meta?.lastPage
        val nextCursor = when {
            current != null && last != null && current < last -> (current + 1).toString()
            current != null && last != null && current >= last -> null
            wallpapers.isEmpty() -> null
            wallpapers.size < WALLHAVEN_PAGE_LIMIT -> null
            else -> (requestedPage + 1).toString()
        }
        return WallpaperPage(wallpapers, nextCursor)
    }

    private fun WallhavenWallpaper.toWallpaperItem(): WallpaperItem? {
        val imageUrl = path ?: return null
        val idValue = id?.takeIf { it.isNotBlank() } ?: imageUrl.hashCode().toString()
        val titleValue = id?.let { "Wallhaven #$it" } ?: "Wallhaven wallpaper"
        val sourceUrl = url ?: shortUrl ?: imageUrl
        val thumbUrl = thumbs?.large ?: thumbs?.small ?: thumbs?.original
        return WallpaperItem(
            id = "wallhaven_$idValue",
            title = titleValue,
            imageUrl = imageUrl,
            thumbnailUrl = thumbUrl,
            sourceUrl = sourceUrl,
            width = dimensionX,
            height = dimensionY
        )
    }

    private data class WallhavenConfig(
        val mode: WallhavenMode,
        val params: Map<String, String> = emptyMap(),
        val collectionUser: String? = null,
        val collectionId: String? = null
    )

    private enum class WallhavenMode { SEARCH, COLLECTION }

    private companion object {
        private const val DEFAULT_REDDIT_SUBREDDIT = "wallpapers"
        private const val DEFAULT_PINTEREST_QUERY = "wallpaper backgrounds"
        private const val DEFAULT_CUSTOM_WEBSITE =
            "https://www.pixelstalk.net/category/wallpapers/4k-wallpapers/"
        private const val REDDIT_PAGE_LIMIT = 30
        private const val WALLHAVEN_PAGE_LIMIT = 24
    }
}

class CredentialRequiredException(credentialName: String) : IllegalStateException(
    "$credentialName is required. Add it in Settings > Source connections.",
)

class UnsupportedSourceException(sourceName: String) : IllegalStateException(
    "$sourceName is not available in the no-key public build. Use Wallhaven instead.",
)

private fun String.requireConfigured(credentialName: String) {
    if (isBlank()) throw CredentialRequiredException(credentialName)
}
