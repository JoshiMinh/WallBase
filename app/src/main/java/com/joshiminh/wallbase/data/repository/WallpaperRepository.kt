package com.joshiminh.wallbase.data.repository

import android.net.Uri
import com.joshiminh.wallbase.sources.reddit.RedditCommunity
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.data.entity.wallpaper.WallpaperItem
import com.joshiminh.wallbase.sources.reddit.RedditPost
import com.joshiminh.wallbase.sources.reddit.RedditService
import com.joshiminh.wallbase.sources.reddit.RedditSubredditChild
import com.joshiminh.wallbase.sources.reddit.RedditSubredditListingResponse
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
                val wallpapers = when {
                    trimmedQuery != null -> webScraper.scrapePinterest(trimmedQuery, limit = 30)
                    config.isNullOrBlank() -> webScraper.scrapePinterest(pinterestQuery, limit = 30)
                    config.startsWith("http", ignoreCase = true) ->
                        webScraper.scrapeImagesFromUrl(config, limit = 30)
                    else -> webScraper.scrapePinterest(config, limit = 30)
                }
                WallpaperPage(wallpapers = wallpapers, nextCursor = null)
            }
            SourceKeys.WEBSITES -> {
                val url = source.config ?: customWebsiteUrl
                val targetUrl = trimmedQuery?.let { buildWebsiteSearchUrl(url, it) } ?: url
                WallpaperPage(
                    wallpapers = webScraper.scrapeImagesFromUrl(targetUrl, limit = 30),
                    nextCursor = null
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
                    redditService.fetchSubreddit(subreddit = normalized, after = cursor)
                } else {
                    redditService.searchSubredditPosts(
                        subreddit = normalized,
                        query = query,
                        restrictToSubreddit = 1,
                        limit = 40,
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

    private companion object {
        private const val DEFAULT_REDDIT_SUBREDDIT = "wallpapers"
        private const val DEFAULT_PINTEREST_QUERY = "wallpaper backgrounds"
        private const val DEFAULT_CUSTOM_WEBSITE =
            "https://www.pixelstalk.net/category/wallpapers/4k-wallpapers/"
    }
}