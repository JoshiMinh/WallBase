package com.joshiminh.wallbase.util.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateReleaseDto(
    @param:Json(name = "tag_name") val tagName: String,
    @param:Json(name = "body") val changelog: String? = null,
    @param:Json(name = "html_url") val downloadUrl: String? = null
)
