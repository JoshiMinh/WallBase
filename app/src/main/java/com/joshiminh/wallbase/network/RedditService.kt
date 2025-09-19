package com.joshiminh.wallbase.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface RedditService {
    @GET("r/{subreddit}/{sort}.json")
    suspend fun fetchSubreddit(
        @Path("subreddit") subreddit: String,
        @Path("sort") sort: String = "top",
        @Query("t") timeRange: String = "week",
        @Query("limit") limit: Int = 40
    ): RedditListingResponse
}
