package com.joshiminh.wallbase.data.repository

import android.net.Uri
import com.joshiminh.wallbase.sources.reddit.RedditCommunity
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.sources.reddit.RedditListingResponse
import com.joshiminh.wallbase.sources.reddit.RedditPost
import com.joshiminh.wallbase.sources.reddit.RedditService
import com.joshiminh.wallbase.sources.reddit.RedditSubredditChild
import com.joshiminh.wallbase.sources.reddit.RedditSubredditListingResponse
import com.joshiminh.wallbase.sources.danbooru.DanbooruPost
import com.joshiminh.wallbase.sources.danbooru.DanbooruService
import com.joshiminh.wallbase.sources.unsplash.UnsplashPhoto
import com.joshiminh.wallbase.sources.unsplash.UnsplashSearchResponse
import com.joshiminh.wallbase.sources.unsplash.UnsplashService
import com.joshiminh.wallbase.sources.wallhaven.WallhavenResponse
import com.joshiminh.wallbase.sources.wallhaven.WallhavenService
import com.joshiminh.wallbase.sources.wallhaven.WallhavenWallpaper
import com.joshiminh.wallbase.util.network.WebScraper
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WallpaperPage(
    val wallpapers: List<WallpaperItem>,
    val nextCursor: String?
)

class WallpaperRepository(
    private val redditService: RedditService,
    private val webScraper: WebScraper,
    private val wallhavenService: WallhavenService,
    private val danbooruService: DanbooruService,
    private val unsplashService: UnsplashService,
    private val pinterestQuery: String = DEFAULT_PINTEREST_QUERY,
    private val customWebsiteUrl: String = DEFAULT_CUSTOM_WEBSITE
) {

    suspend fun fetchWallpapersFor(
        source: Source,
        query: String? = null,
        cursor: String? = null
    ): WallpaperPage {
        val provider = source.providerKey.lowercase(Locale.ROOT)
        val trimmedQuery = query?.trim()?.takeIf { it.isNotEmpty() }
        val page = when (provider) {
            SourceKeys.REDDIT ->
                fetchRedditWallpapers(
                    subreddit = source.config ?: DEFAULT_REDDIT_SUBREDDIT,
                    query = trimmedQuery,
                    cursor = cursor
                )
            SourceKeys.PINTEREST -> {
                val config = source.config
                val scrapePage = when {
                    trimmedQuery != null ->
                        webScraper.scrapePinterest(trimmedQuery, limit = 30, cursor = cursor)
                    config.isNullOrBlank() ->
                        webScraper.scrapePinterest(pinterestQuery, limit = 30, cursor = cursor)
                    config.startsWith("http", ignoreCase = true) ->
                        webScraper.scrapeImagesFromUrl(config, limit = 30, cursor = cursor)
                    else -> webScraper.scrapePinterest(config, limit = 30, cursor = cursor)
                }
                WallpaperPage(wallpapers = scrapePage.wallpapers, nextCursor = scrapePage.nextCursor)
            }
            SourceKeys.WALLHAVEN -> fetchWallhavenWallpapers(
                config = source.config,
                query = trimmedQuery,
                cursor = cursor
            )
            SourceKeys.DANBOORU -> fetchDanbooruWallpapers(
                config = source.config,
                query = trimmedQuery,
                cursor = cursor
            )
            SourceKeys.UNSPLASH -> fetchUnsplashWallpapers(
                config = source.config,
                query = trimmedQuery,
                cursor = cursor
            )
            SourceKeys.ALPHA_CODERS -> {
                val defaultUrl = source.config ?: DEFAULT_ALPHA_CODERS_URL
                val targetUrl = trimmedQuery?.let { buildAlphaCodersSearchUrl(it) } ?: defaultUrl
                val scrapePage = webScraper.scrapeImagesFromUrl(
                    targetUrl,
                    limit = 30,
                    cursor = cursor
                )
                WallpaperPage(
                    wallpapers = scrapePage.wallpapers,
                    nextCursor = scrapePage.nextCursor
                )
            }
            SourceKeys.WEBSITES -> {
                val url = source.config ?: customWebsiteUrl
                val targetUrl = trimmedQuery?.let { buildWebsiteSearchUrl(url, it) } ?: url
                val scrapePage = webScraper.scrapeImagesFromUrl(
                    targetUrl,
                    limit = 30,
                    cursor = cursor
                )
                WallpaperPage(
                    wallpapers = scrapePage.wallpapers,
                    nextCursor = scrapePage.nextCursor
                )
            }
            else -> WallpaperPage(emptyList(), nextCursor = null)
        }
        val mapped = page.wallpapers.map {
            it.copy(
                sourceName = source.title,
                sourceKey = source.key
            )
        }
        return WallpaperPage(wallpapers = mapped, nextCursor = page.nextCursor)
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
                        wallhavenService.getCollection(
                            username = username,
                            collectionId = collectionId,
                            page = pageNumber,
                            perPage = WALLHAVEN_PAGE_LIMIT
                        )
                    }.mapCatching { response ->
                        response.toWallpaperPage(pageNumber)
                    }.getOrElse { WallpaperPage(emptyList(), nextCursor = null) }
                }
            }

            WallhavenMode.SEARCH -> {
                val params = parsed.params.toMutableMap()
                if (!query.isNullOrBlank()) {
                    params["q"] = query
                }
                params.putIfAbsent("q", DEFAULT_WALLHAVEN_QUERY)
                params["page"] = pageNumber.toString()
                params.putIfAbsent("per_page", WALLHAVEN_PAGE_LIMIT.toString())
                runCatching { wallhavenService.search(params) }
                    .mapCatching { response -> response.toWallpaperPage(pageNumber) }
                    .getOrElse { WallpaperPage(emptyList(), nextCursor = null) }
            }
        }
    }

    private suspend fun fetchDanbooruWallpapers(
        config: String?,
        query: String?,
        cursor: String?
    ): WallpaperPage = withContext(Dispatchers.IO) {
        val pageNumber = cursor?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val params = parseQueryParameters(config).toMutableMap()
        val combinedTags = combineDanbooruTags(params["tags"], query)
        params["tags"] = combinedTags ?: DEFAULT_DANBOORU_TAGS
        params["page"] = pageNumber.toString()
        params["limit"] = DANBOORU_PAGE_LIMIT.toString()
        val posts = runCatching { danbooruService.getPosts(params) }.getOrElse { emptyList() }
        val items = posts.mapNotNull { it.toWallpaperItem() }
        val nextCursor = if (posts.size < DANBOORU_PAGE_LIMIT) null else (pageNumber + 1).toString()
        WallpaperPage(items, nextCursor)
    }

    private suspend fun fetchUnsplashWallpapers(
        config: String?,
        query: String?,
        cursor: String?
    ): WallpaperPage = withContext(Dispatchers.IO) {
        val perPage = UNSPLASH_PAGE_LIMIT
        val pageNumber = cursor?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        when (val parsed = parseUnsplashConfig(config)) {
            is UnsplashConfig.Collection -> {
                val photos = runCatching {
                    unsplashService.getCollectionPhotos(
                        id = parsed.id,
                        page = pageNumber,
                        perPage = perPage
                    )
                }.getOrElse { emptyList() }
                val items = photos.mapNotNull { it.toWallpaperItem() }
                val nextCursor = if (photos.size < perPage) null else (pageNumber + 1).toString()
                WallpaperPage(items, nextCursor)
            }

            is UnsplashConfig.UserLikes -> {
                val photos = runCatching {
                    unsplashService.getUserLikes(
                        username = parsed.username,
                        page = pageNumber,
                        perPage = perPage
                    )
                }.getOrElse { emptyList() }
                val items = photos.mapNotNull { it.toWallpaperItem() }
                val nextCursor = if (photos.size < perPage) null else (pageNumber + 1).toString()
                WallpaperPage(items, nextCursor)
            }

            is UnsplashConfig.UserPhotos -> {
                val photos = runCatching {
                    unsplashService.getUserPhotos(
                        username = parsed.username,
                        page = pageNumber,
                        perPage = perPage
                    )
                }.getOrElse { emptyList() }
                val items = photos.mapNotNull { it.toWallpaperItem() }
                val nextCursor = if (photos.size < perPage) null else (pageNumber + 1).toString()
                WallpaperPage(items, nextCursor)
            }

            is UnsplashConfig.Search -> {
                val effectiveQuery = query?.takeIf { it.isNotBlank() }
                    ?: parsed.query?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_UNSPLASH_QUERY
                val response = runCatching {
                    unsplashService.searchPhotos(
                        query = effectiveQuery,
                        page = pageNumber,
                        perPage = perPage
                    )
                }.getOrElse { return@withContext WallpaperPage(emptyList(), nextCursor = null) }
                val photos = response.results.orEmpty()
                val items = photos.mapNotNull { it.toWallpaperItem() }
                val nextCursor = when {
                    response.totalPages != null && pageNumber < response.totalPages ->
                        (pageNumber + 1).toString()
                    photos.size < perPage -> null
                    else -> (pageNumber + 1).toString()
                }
                WallpaperPage(items, nextCursor)
            }
        }
    }

    suspend fun searchRedditCommunities(query: String, limit: Int = 10): List<RedditCommunity> =
        withContext(Dispatchers.IO) {
            runCatching { redditService.searchSubreddits(query = query, limit = limit) }
                .mapCatching { response -> response.toCommunities() }
                .getOrElse { emptyList() }
        }

    private suspend fun fetchRedditWallpapers(
        subreddit: String,
        query: String?,
        cursor: String?
    ): WallpaperPage =
        withContext(Dispatchers.IO) {
            runCatching {
                val normalized = subreddit.normalizeSubredditName()
                if (query.isNullOrBlank()) {
                    redditService.fetchSubreddit(
                        subreddit = normalized,
                        limit = REDDIT_PAGE_LIMIT,
                        after = cursor
                    )
                } else {
                    redditService.searchSubredditPosts(
                        subreddit = normalized,
                        query = query,
                        restrictToSubreddit = 1,
                        limit = REDDIT_PAGE_LIMIT,
                        after = cursor
                    )
                }
            }.mapCatching { response ->
                WallpaperPage(
                    wallpapers = response.toWallpaperItems(),
                    nextCursor = response.data?.after
                )
            }.getOrElse { WallpaperPage(emptyList(), nextCursor = null) }
        }

    private fun RedditPost.resolveImageUrl(): String? {
        val previewUrl = preview?.images.orEmpty().firstOrNull()?.source?.url
        val directUrl = overriddenUrl ?: url
        return (previewUrl ?: directUrl)
            ?.replace("&amp;", "&")
            ?.takeIf { it.hasSupportedImageExtension() }
    }

    private fun RedditListingResponse.toWallpaperItems(): List<WallpaperItem> {
        return data?.children.orEmpty().mapNotNull { child ->
            val post = child.data ?: return@mapNotNull null
            val imageUrl = post.resolveImageUrl() ?: return@mapNotNull null
            val dimensions = post.preview?.images.orEmpty().firstOrNull()?.source
            WallpaperItem(
                id = post.id,
                title = post.title,
                imageUrl = imageUrl,
                sourceUrl = post.permalink?.let { "https://www.reddit.com$it" } ?: imageUrl,
                width = dimensions?.width,
                height = dimensions?.height
            )
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

    private fun String.normalizeSubredditName(): String = this
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .substringAfter("reddit.com/", this)
        .removePrefix("r/")
        .substringBefore('/')
        .trim()
        .lowercase(Locale.ROOT)

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

    private fun buildAlphaCodersSearchUrl(query: String): String {
        val encoded = Uri.encode(query)
        return "https://wall.alphacoders.com/search.php?search=$encoded"
    }

    private fun parseQueryParameters(config: String?): Map<String, String> {
        if (config.isNullOrBlank()) return emptyMap()
        val uri = runCatching { Uri.parse(config) }.getOrElse { return emptyMap() }
        return uri.queryParameterNames.associateWith { name ->
            uri.getQueryParameter(name).orEmpty()
        }.filterValues { it.isNotBlank() }
    }

    private fun combineDanbooruTags(base: String?, query: String?): String? {
        val tags = mutableListOf<String>()
        if (!base.isNullOrBlank()) {
            tags += base.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        }
        if (!query.isNullOrBlank()) {
            tags += query.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
        }
        if (tags.isEmpty()) return null
        return tags.joinToString(" ") { it.replace(' ', '_') }
    }

    private fun parseWallhavenConfig(config: String?): WallhavenConfig {
        if (config.isNullOrBlank()) {
            return WallhavenConfig(mode = WallhavenMode.SEARCH)
        }
        val uri = runCatching { Uri.parse(config) }.getOrElse {
            return WallhavenConfig(mode = WallhavenMode.SEARCH)
        }
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        if (segments.size >= 3 && segments[0].equals("collections", ignoreCase = true)) {
            val username = segments.getOrNull(1)
            val collectionId = segments.getOrNull(2)
            if (!username.isNullOrBlank() && !collectionId.isNullOrBlank()) {
                return WallhavenConfig(
                    mode = WallhavenMode.COLLECTION,
                    collectionUser = username,
                    collectionId = collectionId
                )
            }
        }

        val params = mutableMapOf<String, String>()
        uri.queryParameterNames.forEach { name ->
            val value = uri.getQueryParameter(name)
            if (!value.isNullOrBlank()) {
                params[name] = value
            }
        }
        val firstSegment = segments.firstOrNull()?.lowercase(Locale.ROOT)
        when (firstSegment) {
            "toplist" -> params.putIfAbsent("sorting", "toplist")
            "latest" -> params.putIfAbsent("sorting", "date_added")
            "random" -> params.putIfAbsent("sorting", "random")
        }
        return WallhavenConfig(mode = WallhavenMode.SEARCH, params = params)
    }

    private fun parseUnsplashConfig(config: String?): UnsplashConfig {
        if (config.isNullOrBlank()) return UnsplashConfig.Search(query = null)
        val uri = runCatching { Uri.parse(config) }.getOrElse {
            return UnsplashConfig.Search(query = null)
        }
        val segments = uri.pathSegments.filter { it.isNotBlank() }
        if (segments.size >= 3 && segments[0].equals("collections", ignoreCase = true)) {
            val id = segments.getOrNull(1)
            if (!id.isNullOrBlank()) {
                return UnsplashConfig.Collection(id)
            }
        }
        if (segments.size >= 2 && segments[0].equals("s", ignoreCase = true) &&
            segments[1].equals("photos", ignoreCase = true)
        ) {
            val term = segments.drop(2)
                .joinToString(" ") { it.replace('-', ' ') }
                .takeIf { it.isNotBlank() }
            val queryParam = uri.getQueryParameter("query")
                ?: uri.getQueryParameter("q")
                ?: term
            return UnsplashConfig.Search(queryParam)
        }
        val first = segments.firstOrNull()
        if (!first.isNullOrBlank() && first.startsWith("@")) {
            val username = first.removePrefix("@")
            val next = segments.getOrNull(1)
            return when {
                next.equals("likes", ignoreCase = true) -> UnsplashConfig.UserLikes(username)
                else -> UnsplashConfig.UserPhotos(username)
            }
        }
        val queryParam = uri.getQueryParameter("query") ?: uri.getQueryParameter("q")
        return UnsplashConfig.Search(queryParam)
    }

    private fun WallhavenResponse.toWallpaperPage(requestedPage: Int): WallpaperPage {
        val wallpapers = data.orEmpty().mapNotNull { it.toWallpaperItem() }
        val current = meta?.currentPage
        val last = meta?.lastPage
        val nextCursor = when {
            current != null && last != null && current < last -> (current + 1).toString()
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
        return WallpaperItem(
            id = "wallhaven_$idValue",
            title = titleValue,
            imageUrl = imageUrl,
            sourceUrl = sourceUrl,
            width = dimensionX,
            height = dimensionY
        )
    }

    private fun DanbooruPost.toWallpaperItem(): WallpaperItem? {
        val imageUrl = largeFileUrl ?: fileUrl ?: return null
        val idValue = id?.toString() ?: imageUrl.hashCode().toString()
        val titleValue = tagStringGeneral
            ?.split('_', ' ')
            ?.filter { it.isNotBlank() }
            ?.joinToString(" ")
            ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
            ?.takeIf { it.isNotBlank() }
            ?: "Danbooru wallpaper"
        val sourceUrl = id?.let { "https://danbooru.donmai.us/posts/$it" } ?: imageUrl
        return WallpaperItem(
            id = "danbooru_$idValue",
            title = titleValue,
            imageUrl = imageUrl,
            sourceUrl = sourceUrl,
            width = imageWidth,
            height = imageHeight
        )
    }

    private fun UnsplashPhoto.toWallpaperItem(): WallpaperItem? {
        val imageUrl = urls?.full ?: urls?.raw ?: urls?.regular ?: return null
        val idValue = id?.takeIf { it.isNotBlank() } ?: imageUrl.hashCode().toString()
        val titleValue = description?.takeIf { it.isNotBlank() }
            ?: altDescription?.takeIf { it.isNotBlank() }
            ?: user?.name?.takeIf { it.isNotBlank() }?.let { "$it on Unsplash" }
            ?: "Unsplash wallpaper"
        val sourceUrl = links?.html ?: imageUrl
        return WallpaperItem(
            id = "unsplash_$idValue",
            title = titleValue,
            imageUrl = imageUrl,
            sourceUrl = sourceUrl,
            width = width,
            height = height
        )
    }

    private data class WallhavenConfig(
        val mode: WallhavenMode,
        val params: Map<String, String> = emptyMap(),
        val collectionUser: String? = null,
        val collectionId: String? = null
    )

    private enum class WallhavenMode { SEARCH, COLLECTION }

    private sealed interface UnsplashConfig {
        data class Search(val query: String?) : UnsplashConfig
        data class Collection(val id: String) : UnsplashConfig
        data class UserPhotos(val username: String) : UnsplashConfig
        data class UserLikes(val username: String) : UnsplashConfig
    }

    private companion object {
        private const val DEFAULT_REDDIT_SUBREDDIT = "wallpapers"
        private const val DEFAULT_PINTEREST_QUERY = "wallpaper backgrounds"
        private const val DEFAULT_CUSTOM_WEBSITE =
            "https://www.pixelstalk.net/category/wallpapers/4k-wallpapers/"
        private const val DEFAULT_WALLHAVEN_QUERY = "wallpapers"
        private const val DEFAULT_DANBOORU_TAGS = "wallpaper rating:s"
        private const val DEFAULT_UNSPLASH_QUERY = "wallpapers"
        private const val DEFAULT_ALPHA_CODERS_URL =
            "https://wall.alphacoders.com/search.php?search=wallpaper"
        private const val REDDIT_PAGE_LIMIT = 30
        private const val WALLHAVEN_PAGE_LIMIT = 30
        private const val DANBOORU_PAGE_LIMIT = 30
        private const val UNSPLASH_PAGE_LIMIT = 30
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}