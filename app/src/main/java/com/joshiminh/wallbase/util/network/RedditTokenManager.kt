package com.joshiminh.wallbase.util.network

import com.joshiminh.wallbase.sources.RedditAuthService
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RedditTokenManager @Inject constructor(
    private val redditAuthService: RedditAuthService,
    private val clientIdProvider: () -> String,
) {
    @Volatile
    private var redditToken: String? = null

    @Volatile
    private var redditTokenExpiresAt: Long = 0

    @Volatile
    private var tokenClientId: String? = null

    fun getRedditAccessToken(): String {
        val now = System.currentTimeMillis()
        val clientId = clientIdProvider()
        val currentToken = redditToken
        if (currentToken != null && tokenClientId == clientId && now < redditTokenExpiresAt) {
            return currentToken
        }

        synchronized(this) {
            if (redditToken != null && tokenClientId == clientId && now < redditTokenExpiresAt) {
                return redditToken!!
            }

            if (clientId.isBlank()) {
                return ""
            }

            val authHeader = okhttp3.Credentials.basic(clientId, "")
            val deviceId = UUID.randomUUID().toString()

            return try {
                val response = redditAuthService.getAccessToken(authHeader = authHeader, deviceId = deviceId).execute()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    redditToken = body.accessToken
                    tokenClientId = clientId
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
