package com.joshiminh.wallbase.sources

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.data.entity.SourceSeed

interface WallhavenService {
    @GET("search")
    suspend fun search(
        @QueryMap options: Map<String, String>
    ): WallhavenResponse

    @GET("collections/{username}/{collectionId}")
    suspend fun getCollection(
        @Path("username") username: String,
        @Path("collectionId") collectionId: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): WallhavenResponse
}

@JsonClass(generateAdapter = true)
data class WallhavenResponse(
    @param:Json(name = "data") val data: List<WallhavenWallpaper>?,
    @param:Json(name = "meta") val meta: WallhavenMeta?
)

@JsonClass(generateAdapter = true)
data class WallhavenWallpaper(
    @param:Json(name = "id") val id: String?,
    @param:Json(name = "url") val url: String?,
    @param:Json(name = "short_url") val shortUrl: String?,
    @param:Json(name = "path") val path: String?,
    @param:Json(name = "dimension_x") val dimensionX: Int?,
    @param:Json(name = "dimension_y") val dimensionY: Int?
)

@JsonClass(generateAdapter = true)
data class WallhavenMeta(
    @param:Json(name = "current_page") val currentPage: Int?,
    @param:Json(name = "last_page") val lastPage: Int?
)

/** Wallhaven's public search endpoint works without an account or API key. */
val WallhavenSource = SourceSeed(
    key = "wallhaven:featured",
    providerKey = SourceKeys.WALLHAVEN,
    iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=wallhaven.cc",
    title = "Featured wallpapers",
    description = "Fresh wallpapers from Wallhaven's public catalog",
    showInExplore = true,
    enabledByDefault = true,
    config = "https://wallhaven.cc/search?q=wallpapers&purity=100&sorting=toplist",
)
