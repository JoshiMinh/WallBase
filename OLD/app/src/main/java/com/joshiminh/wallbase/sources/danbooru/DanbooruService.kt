package com.joshiminh.wallbase.sources.danbooru

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.QueryMap

interface DanbooruService {
    @GET("posts.json")
    suspend fun getPosts(
        @QueryMap options: Map<String, String>
    ): List<DanbooruPost>
}

@JsonClass(generateAdapter = true)
data class DanbooruPost(
    @Json(name = "id") val id: Long?,
    @Json(name = "file_url") val fileUrl: String?,
    @Json(name = "large_file_url") val largeFileUrl: String?,
    @Json(name = "preview_file_url") val previewFileUrl: String?,
    @Json(name = "image_width") val imageWidth: Int?,
    @Json(name = "image_height") val imageHeight: Int?,
    @Json(name = "tag_string_general") val tagStringGeneral: String?
)
