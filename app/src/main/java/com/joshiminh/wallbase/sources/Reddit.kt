package com.joshiminh.wallbase.sources

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.SourceSeed

/**
 * Lightweight representation of a subreddit returned from Reddit's search endpoint.
 */
data class RedditCommunity(
    val name: String,
    val displayName: String,
    val title: String,
    val description: String?,
    val iconUrl: String?
)

data class RedditListingResponse(
    val data: RedditListingData? = null
)

data class RedditListingData(
    val children: List<RedditChild>? = null,
    val after: String? = null
)

data class RedditChild(
    val data: RedditPost? = null
)

data class RedditPost(
    val id: String = "",
    val title: String = "",
    val permalink: String? = null,
    @Json(name = "url_overridden_by_dest") val overriddenUrl: String? = null,
    val url: String? = null,
    val preview: RedditPreview? = null
)

data class RedditPreview(
    val images: List<RedditPreviewImage>? = null
)

data class RedditPreviewImage(
    val source: RedditPreviewSource? = null
)

data class RedditPreviewSource(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class RedditSubredditListingResponse(
    val data: RedditSubredditListingData? = null
)

data class RedditSubredditListingData(
    val children: List<RedditSubredditChild>? = null
)

data class RedditSubredditChild(
    val data: RedditSubreddit? = null
)

data class RedditSubreddit(
    val name: String = "",
    @Json(name = "display_name") val displayName: String = "",
    @Json(name = "display_name_prefixed") val displayNamePrefixed: String = "",
    val title: String = "",
    @Json(name = "public_description") val publicDescription: String? = null,
    @Json(name = "icon_img") val iconImage: String? = null,
    @Json(name = "community_icon") val communityIcon: String? = null
)

interface RedditService {
    @GET("r/{subreddit}/{sort}.json")
    suspend fun fetchSubreddit(
        @Path("subreddit") subreddit: String,
        @Path("sort") sort: String = "top",
        @Query("t") timeRange: String = "week",
        @Query("limit") limit: Int = 30,
        @Query("after") after: String? = null
    ): RedditListingResponse

    @GET("subreddits/search.json")
    suspend fun searchSubreddits(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("include_over_18") includeOver18: Int = 0
    ): RedditSubredditListingResponse

    @GET("r/{subreddit}/search.json")
    suspend fun searchSubredditPosts(
        @Path("subreddit") subreddit: String,
        @Query("q") query: String,
        @Query("restrict_sr") restrictToSubreddit: Int = 1,
        @Query("limit") limit: Int = 30,
        @Query("sort") sort: String = "relevance",
        @Query("after") after: String? = null
    ): RedditListingResponse
}

val RedditSource = SourceSeed(
    key = "reddit:wallpapers",
    providerKey = SourceKeys.REDDIT,
    iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=reddit.com",
    title = "r/wallpapers",
    description = "Top posts from r/wallpapers",
    showInExplore = true,
    enabledByDefault = true,
    config = "wallpapers"
)


