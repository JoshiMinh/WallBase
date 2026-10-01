package com.joshiminh.wallbase.core.network

import retrofit2.http.GET

interface UpdateService {
    @GET("releases/latest")
    suspend fun fetchLatestRelease(): UpdateReleaseDto
}

