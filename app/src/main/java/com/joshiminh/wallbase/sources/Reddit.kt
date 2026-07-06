package com.joshiminh.wallbase.sources

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.SourceSeed

@JsonClass(generateAdapter = true)
data class RedditCommunity(
    val name: String,
    val displayName: String,
    val title: String,
    val description: String?,
    val iconUrl: String?
)

@JsonClass(generateAdapter = true)
data class RedditListingResponse(
    val data: RedditListingData? = null
)

@JsonClass(generateAdapter = true)
data class RedditListingData(
    val children: List<RedditChild>? = null,
    val after: String? = null
)

@JsonClass(generateAdapter = true)
data class RedditChild(
    val data: RedditPost? = null
)

@JsonClass(generateAdapter = true)
data class RedditPost(
    val id: String = "",
    val title: String = "",
    val author: String? = null,
    val permalink: String? = null,
    @Json(name = "url_overridden_by_dest") val overriddenUrl: String? = null,
    val url: String? = null,
    val preview: RedditPreview? = null,
    @Json(name = "is_gallery") val isGallery: Boolean = false,
    @Json(name = "media_metadata") val mediaMetadata: Map<String, RedditMediaMetadata>? = null,
    @Json(name = "post_hint") val postHint: String? = null
)

@JsonClass(generateAdapter = true)
data class RedditMediaMetadata(
    val status: String? = null,
    val e: String? = null,
    val m: String? = null,
    val s: RedditMediaSource? = null
)

@JsonClass(generateAdapter = true)
data class RedditMediaSource(
    @Json(name = "u") val url: String? = null,
    @Json(name = "x") val width: Int? = null,
    @Json(name = "y") val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class RedditPreview(
    val images: List<RedditPreviewImage>? = null
)

@JsonClass(generateAdapter = true)
data class RedditPreviewImage(
    val source: RedditPreviewSource? = null
)

@JsonClass(generateAdapter = true)
data class RedditPreviewSource(
    val url: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class RedditSubredditListingResponse(
    val data: RedditSubredditListingData? = null
)

@JsonClass(generateAdapter = true)
data class RedditSubredditListingData(
    val children: List<RedditSubredditChild>? = null
)

@JsonClass(generateAdapter = true)
data class RedditSubredditChild(
    val data: RedditSubreddit? = null
)

@JsonClass(generateAdapter = true)
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
        @Query("t") timeRange: String = "all",
        @Query("limit") limit: Int = 30,
        @Query("after") after: String? = null
    ): RedditListingResponse

    @GET("subreddits/search.json")
    suspend fun searchSubreddits(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("include_over_18") includeOver18: Boolean = false
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

@JsonClass(generateAdapter = true)
data class RedditTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "scope") val scope: String
)

interface RedditAuthService {
    @retrofit2.http.FormUrlEncoded
    @retrofit2.http.POST("api/v1/access_token")
    fun getAccessToken(
        @retrofit2.http.Header("Authorization") authHeader: String,
        @retrofit2.http.Field("grant_type") grantType: String = "https://oauth.reddit.com/grants/installed_client",
        @retrofit2.http.Field("device_id") deviceId: String
    ): retrofit2.Call<RedditTokenResponse>
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