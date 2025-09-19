package com.joshiminh.wallbase.data.wallpapers

import com.joshiminh.wallbase.data.source.RedditCommunity
import com.joshiminh.wallbase.data.source.Source
import com.joshiminh.wallbase.data.source.SourceKeys
import com.joshiminh.wallbase.network.RedditPost
import com.joshiminh.wallbase.network.RedditService
import com.joshiminh.wallbase.network.RedditSubredditChild
import com.joshiminh.wallbase.network.RedditSubredditListingResponse
import com.joshiminh.wallbase.network.WebScraper
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WallpaperRepository(
    private val redditService: RedditService,
    private val webScraper: WebScraper,
    private val pinterestQuery: String = DEFAULT_PINTEREST_QUERY,
    private val customWebsiteUrl: String = DEFAULT_CUSTOM_WEBSITE
) {

    suspend fun fetchWallpapersFor(source: Source): List<WallpaperItem> {
        val provider = source.providerKey.lowercase(Locale.ROOT)
        val wallpapers = when (provider) {
            SourceKeys.REDDIT -> fetchRedditWallpapers(source.config ?: DEFAULT_REDDIT_SUBREDDIT)
            SourceKeys.PINTEREST -> webScraper.scrapePinterest(pinterestQuery, limit = 30)
            SourceKeys.WEBSITES -> webScraper.scrapeImagesFromUrl(customWebsiteUrl, limit = 30)
            else -> emptyList()
        }
        return wallpapers.map { it.copy(sourceName = source.title) }
    }

    suspend fun searchRedditCommunities(query: String, limit: Int = 10): List<RedditCommunity> =
        withContext(Dispatchers.IO) {
            runCatching {
                redditService.searchSubreddits(query = query, limit = limit)
            }.mapCatching(RedditSubredditListingResponse::toCommunities)
                .getOrElse { emptyList() }
        }

    private suspend fun fetchRedditWallpapers(subreddit: String): List<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = subreddit.normalizeSubredditName()
            redditService.fetchSubreddit(subreddit = normalized)
        }.mapCatching { response ->
            response.data?.children.orEmpty().mapNotNull { child ->
                val post = child.data ?: return@mapNotNull null
                val imageUrl = post.resolveImageUrl() ?: return@mapNotNull null
                WallpaperItem(
                    id = post.id,
                    title = post.title,
                    imageUrl = imageUrl,
                    sourceUrl = post.permalink?.let { "https://www.reddit.com$it" } ?: imageUrl
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun RedditPost.resolveImageUrl(): String? {
        val previewUrl = preview?.images.orEmpty().firstOrNull()?.source?.url
        val directUrl = overriddenUrl ?: url
        return (previewUrl ?: directUrl)
            ?.replace("&amp;", "&")
            ?.takeIf { it.hasSupportedImageExtension() }
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

    private companion object {
        private const val DEFAULT_REDDIT_SUBREDDIT = "wallpapers"
        private const val DEFAULT_PINTEREST_QUERY = "wallpaper backgrounds"
        private const val DEFAULT_CUSTOM_WEBSITE = "https://www.pixelstalk.net/category/wallpapers/4k-wallpapers/"
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
                description = subreddit.publicDescription.takeIf { it.isNotBlank() },
                iconUrl = subreddit.iconImage ?: subreddit.communityIcon
            )
        }
}
