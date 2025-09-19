package com.joshiminh.wallbase.data.wallpapers

import com.joshiminh.wallbase.data.Source
import com.joshiminh.wallbase.network.RedditPost
import com.joshiminh.wallbase.network.RedditService
import com.joshiminh.wallbase.network.WebScraper
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WallpaperRepository(
    private val redditService: RedditService,
    private val webScraper: WebScraper,
    private val redditSources: Map<String, String> = DEFAULT_REDDIT_SUBREDDITS,
    private val pinterestQuery: String = DEFAULT_PINTEREST_QUERY,
    private val customWebsiteUrl: String = DEFAULT_CUSTOM_WEBSITE
) {

    suspend fun fetchWallpapersFor(source: Source): List<WallpaperItem> {
        val key = source.normalizedKey()
        return when (key) {
            REDDIT_KEY -> fetchRedditWallpapers(redditSources[key] ?: DEFAULT_REDDIT_SUBREDDIT)
            PINTEREST_KEY -> webScraper.scrapePinterest(pinterestQuery, limit = 30)
            WEBSITES_KEY -> webScraper.scrapeImagesFromUrl(customWebsiteUrl, limit = 30)
            else -> emptyList()
        }
    }

    private suspend fun fetchRedditWallpapers(subreddit: String): List<WallpaperItem> = withContext(Dispatchers.IO) {
        runCatching {
            redditService.fetchSubreddit(subreddit = subreddit)
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

    private fun Source.normalizedKey(): String = title.trim().lowercase(Locale.ROOT)

    private companion object {
        private const val REDDIT_KEY = "reddit"
        private const val PINTEREST_KEY = "pinterest"
        private const val WEBSITES_KEY = "websites"

        private const val DEFAULT_REDDIT_SUBREDDIT = "wallpapers"
        private const val DEFAULT_PINTEREST_QUERY = "wallpaper backgrounds"
        private const val DEFAULT_CUSTOM_WEBSITE = "https://www.pixelstalk.net/category/wallpapers/4k-wallpapers/"

        private val DEFAULT_REDDIT_SUBREDDITS = mapOf(
            REDDIT_KEY to DEFAULT_REDDIT_SUBREDDIT
        )
    }
}
