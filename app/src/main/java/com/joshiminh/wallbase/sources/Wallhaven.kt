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
        @Query("page") page: Int? = null,
        @QueryMap options: Map<String, String>? = null
    ): WallhavenResponse
}

@JsonClass(generateAdapter = true)
data class WallhavenResponse(
    @param:Json(name = "data") val data: List<WallhavenWallpaper>?,
    @param:Json(name = "meta") val meta: WallhavenMeta?
)

@JsonClass(generateAdapter = true)
data class WallhavenThumbs(
    @param:Json(name = "large") val large: String? = null,
    @param:Json(name = "original") val original: String? = null,
    @param:Json(name = "small") val small: String? = null
)

@JsonClass(generateAdapter = true)
data class WallhavenWallpaper(
    @param:Json(name = "id") val id: String?,
    @param:Json(name = "url") val url: String?,
    @param:Json(name = "short_url") val shortUrl: String?,
    @param:Json(name = "views") val views: Int? = null,
    @param:Json(name = "favorites") val favorites: Int? = null,
    @param:Json(name = "source") val source: String? = null,
    @param:Json(name = "purity") val purity: String? = null,
    @param:Json(name = "category") val category: String? = null,
    @param:Json(name = "dimension_x") val dimensionX: Int?,
    @param:Json(name = "dimension_y") val dimensionY: Int?,
    @param:Json(name = "resolution") val resolution: String? = null,
    @param:Json(name = "ratio") val ratio: String? = null,
    @param:Json(name = "file_size") val fileSize: Long? = null,
    @param:Json(name = "file_type") val fileType: String? = null,
    @param:Json(name = "created_at") val createdAt: String? = null,
    @param:Json(name = "colors") val colors: List<String>? = null,
    @param:Json(name = "path") val path: String?,
    @param:Json(name = "thumbs") val thumbs: WallhavenThumbs? = null
)

@JsonClass(generateAdapter = true)
data class WallhavenMeta(
    @param:Json(name = "current_page") val currentPage: Int?,
    @param:Json(name = "last_page") val lastPage: Int?,
    @param:Json(name = "per_page") val perPage: Any? = null,
    @param:Json(name = "total") val total: Int? = null,
    @param:Json(name = "query") val query: Any? = null,
    @param:Json(name = "seed") val seed: String? = null
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
    config = "https://wallhaven.cc/toplist",
)
