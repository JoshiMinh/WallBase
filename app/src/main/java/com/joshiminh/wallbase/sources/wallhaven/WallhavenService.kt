package com.joshiminh.wallbase.sources.wallhaven

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

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
    @Json(name = "data") val data: List<WallhavenWallpaper>?,
    @Json(name = "meta") val meta: WallhavenMeta?
)

@JsonClass(generateAdapter = true)
data class WallhavenWallpaper(
    @Json(name = "id") val id: String?,
    @Json(name = "url") val url: String?,
    @Json(name = "short_url") val shortUrl: String?,
    @Json(name = "path") val path: String?,
    @Json(name = "dimension_x") val dimensionX: Int?,
    @Json(name = "dimension_y") val dimensionY: Int?
)

@JsonClass(generateAdapter = true)
data class WallhavenMeta(
    @Json(name = "current_page") val currentPage: Int?,
    @Json(name = "last_page") val lastPage: Int?
)
