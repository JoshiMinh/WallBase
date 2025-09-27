package com.joshiminh.wallbase.data.repository

import com.joshiminh.wallbase.BuildConfig
import com.joshiminh.wallbase.util.network.UpdateService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UpdateRepository(
    private val service: UpdateService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    sealed class UpdateResult {
        data object UpToDate : UpdateResult()
        data class UpdateAvailable(
            val version: String,
            val notes: String?,
            val downloadUrl: String?
        ) : UpdateResult()

        data class Error(val throwable: Throwable) : UpdateResult()
    }

    suspend fun checkForUpdates(): UpdateResult = withContext(ioDispatcher) {
        try {
            val release = service.fetchLatestRelease()
            val remoteVersion = parseVersion(release.tagName)
            val currentVersion = parseVersion(BuildConfig.VERSION_NAME)

            if (remoteVersion == null || currentVersion == null) {
                return@withContext UpdateResult.Error(
                    IllegalStateException("Unable to parse version information.")
                )
            }

            if (remoteVersion > currentVersion) {
                UpdateResult.UpdateAvailable(
                    version = remoteVersion.display,
                    notes = release.changelog,
                    downloadUrl = release.downloadUrl
                )
            } else {
                UpdateResult.UpToDate
            }
        } catch (error: Throwable) {
            UpdateResult.Error(error)
        }
    }

    private fun parseVersion(raw: String?): SemanticVersion? {
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

    private data class SemanticVersion(
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

