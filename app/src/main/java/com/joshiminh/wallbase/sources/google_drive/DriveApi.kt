package com.joshiminh.wallbase.sources.google_drive

import android.net.Uri
import com.joshiminh.wallbase.util.network.readResponseOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"

data class DriveFolder(val id: String, val name: String)

data class DriveImage(
    val id: String,
    val name: String,
    val mimeType: String,
    val thumbnailLink: String?,
    val webViewLink: String?,
    val webContentLink: String?
)

suspend fun fetchDriveFolders(token: String): List<DriveFolder> = withContext(Dispatchers.IO) {
    if (token.isBlank()) return@withContext emptyList()

    val uri = Uri.parse(DRIVE_FILES_ENDPOINT)
        .buildUpon()
        .appendQueryParameter(
            "q",
            "mimeType='application/vnd.google-apps.folder' and trashed=false"
        )
        .appendQueryParameter("fields", "files(id,name)")
        .appendQueryParameter("spaces", "drive")
        .build()

    val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Authorization", "Bearer $token")
    }

    try {
        val response = connection.readResponseOrThrow()
        val files = JSONObject(response).optJSONArray("files") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until files.length()) {
                val obj = files.optJSONObject(index) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = obj.optString("name").ifBlank { "Untitled folder" }
                add(DriveFolder(id = id, name = name))
            }
        }
    } finally {
        connection.disconnect()
    }
}

suspend fun fetchDriveImages(token: String, folderId: String): List<DriveImage> = withContext(Dispatchers.IO) {
    if (token.isBlank() || folderId.isBlank()) return@withContext emptyList()

    val query = "'$folderId' in parents and trashed=false and mimeType contains 'image/'"
    val uri = Uri.parse(DRIVE_FILES_ENDPOINT)
        .buildUpon()
        .appendQueryParameter("q", query)
        .appendQueryParameter(
            "fields",
            "files(id,name,mimeType,thumbnailLink,webViewLink,webContentLink)"
        )
        .appendQueryParameter("spaces", "drive")
        .build()

    val connection = (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Authorization", "Bearer $token")
    }

    try {
        val response = connection.readResponseOrThrow()
        val files = JSONObject(response).optJSONArray("files") ?: return@withContext emptyList()
        buildList {
            for (index in 0 until files.length()) {
                val obj = files.optJSONObject(index) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = obj.optString("name").ifBlank { "Untitled image" }
                val mimeType = obj.optString("mimeType").ifBlank { "" }
                add(
                    DriveImage(
                        id = id,
                        name = name,
                        mimeType = mimeType,
                        thumbnailLink = obj.optString("thumbnailLink").takeIf { it.isNotBlank() },
                        webViewLink = obj.optString("webViewLink").takeIf { it.isNotBlank() },
                        webContentLink = obj.optString("webContentLink").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    } finally {
        connection.disconnect()
    }
}