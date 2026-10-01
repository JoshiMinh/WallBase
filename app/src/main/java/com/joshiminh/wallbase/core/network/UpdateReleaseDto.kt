package com.joshiminh.wallbase.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateAssetDto(
    @param:Json(name = "name") val name: String = "",
    @param:Json(name = "browser_download_url") val browserDownloadUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class UpdateReleaseDto(
    @param:Json(name = "tag_name") val tagName: String,
    @param:Json(name = "body") val changelog: String? = null,
    @param:Json(name = "html_url") val htmlUrl: String? = null,
    @param:Json(name = "assets") val assets: List<UpdateAssetDto>? = null
) {
    val downloadUrl: String?
        get() = assets?.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug") }?.browserDownloadUrl
            ?: assets?.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
            ?: htmlUrl

    val apkDownloadUrl: String?
        get() = assets?.firstOrNull { it.name.endsWith(".apk") && !it.name.contains("debug") }?.browserDownloadUrl
            ?: assets?.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl

    val releasePageUrl: String?
        get() = htmlUrl
}
