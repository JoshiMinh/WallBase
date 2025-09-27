package com.joshiminh.wallbase.util.network

import retrofit2.http.GET

interface UpdateService {
    @GET("releases/latest")
    suspend fun fetchLatestRelease(): UpdateReleaseDto
}
