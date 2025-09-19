package com.joshiminh.wallbase.network

import com.squareup.moshi.Json

data class RedditListingResponse(
    val data: RedditListingData? = null
)

data class RedditListingData(
    val children: List<RedditChild>? = null
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
    val url: String? = null
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
