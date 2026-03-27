package com.joshiminh.wallbase.sources.reddit

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
