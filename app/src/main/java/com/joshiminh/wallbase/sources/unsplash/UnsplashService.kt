package com.joshiminh.wallbase.sources.unsplash

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Query

interface UnsplashService {
    @Headers("Accept: application/json")
    @GET("napi/search/photos")
    suspend fun searchPhotos(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): UnsplashSearchResponse

    @Headers("Accept: application/json")
    @GET("napi/collections/{id}/photos")
    suspend fun getCollectionPhotos(
        @Path("id") id: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): List<UnsplashPhoto>

    @Headers("Accept: application/json")
    @GET("napi/users/{username}/photos")
    suspend fun getUserPhotos(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): List<UnsplashPhoto>

    @Headers("Accept: application/json")
    @GET("napi/users/{username}/likes")
    suspend fun getUserLikes(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int
    ): List<UnsplashPhoto>
}

@JsonClass(generateAdapter = true)
data class UnsplashSearchResponse(
    @Json(name = "results") val results: List<UnsplashPhoto>?,
    @Json(name = "total_pages") val totalPages: Int?
)

@JsonClass(generateAdapter = true)
data class UnsplashPhoto(
    @Json(name = "id") val id: String?,
    @Json(name = "description") val description: String?,
    @Json(name = "alt_description") val altDescription: String?,
    @Json(name = "urls") val urls: UnsplashPhotoUrls?,
    @Json(name = "links") val links: UnsplashPhotoLinks?,
    @Json(name = "width") val width: Int?,
    @Json(name = "height") val height: Int?,
    @Json(name = "user") val user: UnsplashUser?
)

@JsonClass(generateAdapter = true)
data class UnsplashPhotoUrls(
    @Json(name = "raw") val raw: String?,
    @Json(name = "full") val full: String?,
    @Json(name = "regular") val regular: String?,
    @Json(name = "small") val small: String?
)

@JsonClass(generateAdapter = true)
data class UnsplashPhotoLinks(
    @Json(name = "html") val html: String?
)

@JsonClass(generateAdapter = true)
data class UnsplashUser(
    @Json(name = "name") val name: String?
)
