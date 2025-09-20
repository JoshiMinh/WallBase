package com.joshiminh.wallbase.sources.google_photos

import com.joshiminh.wallbase.util.network.readResponseOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val PHOTOS_ALBUMS_ENDPOINT = "https://photoslibrary.googleapis.com/v1/albums"
private const val PHOTOS_MEDIA_SEARCH_ENDPOINT = "https://photoslibrary.googleapis.com/v1/mediaItems:search"

data class GooglePhotosAlbum(
    val id: String,
    val title: String,
    val mediaItemsCount: Long?,
    val coverPhotoBaseUrl: String?
)

data class GooglePhotosMediaItem(
    val id: String,
    val baseUrl: String?,
    val mimeType: String?,
    val filename: String?
)

suspend fun fetchGooglePhotosAlbums(token: String): List<GooglePhotosAlbum> = withContext(Dispatchers.IO) {
    if (token.isBlank()) return@withContext emptyList()

    val albums = mutableListOf<GooglePhotosAlbum>()
    var pageToken: String? = null
    do {
        val urlBuilder = StringBuilder(PHOTOS_ALBUMS_ENDPOINT).append("?pageSize=50")
        if (!pageToken.isNullOrBlank()) {
            urlBuilder.append("&pageToken=").append(pageToken)
        }
        val connection = (URL(urlBuilder.toString()).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val response = connection.readResponseOrThrow()
            val json = JSONObject(response)
            val items = json.optJSONArray("albums") ?: JSONArray()
            for (index in 0 until items.length()) {
                val album = items.optJSONObject(index) ?: continue
                val id = album.optString("id").takeIf { it.isNotBlank() } ?: continue
                val title = album.optString("title").ifBlank { "Untitled album" }
                val countString = album.optString("mediaItemsCount")
                val count = countString.toLongOrNull()
                val cover = album.optString("coverPhotoBaseUrl").takeIf { it.isNotBlank() }
                albums += GooglePhotosAlbum(
                    id = id,
                    title = title,
                    mediaItemsCount = count,
                    coverPhotoBaseUrl = cover
                )
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } finally {
            connection.disconnect()
        }
    } while (!pageToken.isNullOrBlank())

    albums.sortBy { it.title.lowercase() }
    albums
}

suspend fun fetchGooglePhotosMediaItems(
    token: String,
    albumId: String,
    pageSize: Int = 25
): List<GooglePhotosMediaItem> = withContext(Dispatchers.IO) {
    if (token.isBlank() || albumId.isBlank()) return@withContext emptyList()

    val connection = (URL(PHOTOS_MEDIA_SEARCH_ENDPOINT).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
    }

    try {
        val payload = JSONObject()
            .put("albumId", albumId)
            .put("pageSize", pageSize)
        connection.outputStream.use { stream ->
            stream.write(payload.toString().toByteArray())
        }
        val response = connection.readResponseOrThrow()
        val json = JSONObject(response)
        val items = json.optJSONArray("mediaItems") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val mimeType = item.optString("mimeType").takeIf { it.isNotBlank() }
                if (mimeType?.startsWith("image/") == false) continue
                add(
                    GooglePhotosMediaItem(
                        id = id,
                        baseUrl = item.optString("baseUrl").takeIf { it.isNotBlank() },
                        mimeType = mimeType,
                        filename = item.optString("filename").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}
