package com.joshiminh.wallbase.util.network

import com.joshiminh.wallbase.sources.RedditAuthService
import java.util.UUID

class RedditTokenManager(
    private val redditAuthService: RedditAuthService,
    private val clientId: String
) {
    @Volatile
    private var redditToken: String? = null

    @Volatile
    private var redditTokenExpiresAt: Long = 0

    fun getRedditAccessToken(): String {
        val now = System.currentTimeMillis()
        val currentToken = redditToken
        if (currentToken != null && now < redditTokenExpiresAt) {
            return currentToken
        }

        synchronized(this) {
            if (redditToken != null && now < redditTokenExpiresAt) {
                return redditToken!!
            }

            if (clientId == "YOUR_CLIENT_ID" || clientId.isBlank()) {
                return ""
            }

            val authHeader = okhttp3.Credentials.basic(clientId, "")
            val deviceId = UUID.randomUUID().toString()

            return try {
                val response = redditAuthService.getAccessToken(authHeader = authHeader, deviceId = deviceId).execute()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    redditToken = body.accessToken
                    redditTokenExpiresAt = now + (body.expiresIn * 1000) - 60000
                    redditToken!!
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }
}
