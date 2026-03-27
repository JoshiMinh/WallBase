package com.joshiminh.wallbase.sources.reddit

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

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