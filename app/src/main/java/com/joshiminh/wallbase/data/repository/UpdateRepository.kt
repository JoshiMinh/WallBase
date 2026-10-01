package com.joshiminh.wallbase.data.repository

import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.core.network.UpdateService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val service: UpdateService,
    private val okHttpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    constructor(
        service: UpdateService,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(service, OkHttpClient(), ioDispatcher)

    sealed class UpdateResult {
        data object UpToDate : UpdateResult()
        data class UpdateAvailable(
            val version: String,
            val notes: String?,
            val downloadUrl: String?,
            val apkDownloadUrl: String? = null,
            val releasePageUrl: String? = null
        ) : UpdateResult()

        data class Error(val throwable: Throwable) : UpdateResult()
    }

    suspend fun checkForUpdates(currentVersionOverride: String? = null): UpdateResult = withContext(ioDispatcher) {
        try {
            val release = service.fetchLatestRelease()
            val remoteVersion = parseVersion(release.tagName)
            val currentVersion = parseVersion(currentVersionOverride ?: BuildConfig.VERSION_NAME)

            if (remoteVersion == null || currentVersion == null) {
                return@withContext UpdateResult.Error(
                    IllegalStateException("Unable to parse version information.")
                )
            }

            if (remoteVersion > currentVersion) {
                UpdateResult.UpdateAvailable(
                    version = remoteVersion.display,
                    notes = release.changelog,
                    downloadUrl = release.downloadUrl,
                    apkDownloadUrl = release.apkDownloadUrl,
                    releasePageUrl = release.releasePageUrl ?: release.htmlUrl
                )
            } else {
                UpdateResult.UpToDate
            }
        } catch (error: Throwable) {
            UpdateResult.Error(error)
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        destinationFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(ioDispatcher) {
        runCatching {
            destinationFile.parentFile?.mkdirs()
            val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.tmp")
            if (tempFile.exists()) tempFile.delete()

            val request = Request.Builder()
                .url(downloadUrl)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw java.io.IOException("HTTP ${response.code}: ${response.message}")
            }

            val body = response.body ?: throw java.io.IOException("Empty response body from update server")
            val totalBytes = body.contentLength()

            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        onProgress(totalRead, totalBytes)
                    }
                    output.flush()
                }
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }
            destinationFile
        }
    }

    internal fun parseVersion(raw: String?): SemanticVersion? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val sanitized = trimmed.removePrefix("v").removePrefix("V")
        val parts = sanitized.split('-', limit = 2)
        val versionNumbers = parts.firstOrNull()?.split('.') ?: return null
        val major = versionNumbers.getOrNull(0)?.toIntOrNull() ?: return null
        val minor = versionNumbers.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = versionNumbers.getOrNull(2)?.toIntOrNull() ?: 0
        val preRelease = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
        return SemanticVersion(major, minor, patch, preRelease, display = sanitized)
    }

    internal data class SemanticVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preRelease: String?,
        val display: String
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)

            return when {
                preRelease.isNullOrBlank() && other.preRelease.isNullOrBlank() -> 0
                preRelease.isNullOrBlank() -> 1
                other.preRelease.isNullOrBlank() -> -1
                else -> preRelease.compareTo(other.preRelease)
            }
        }
    }
}


