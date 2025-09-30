@file:Suppress("unused")

package com.joshiminh.wallbase.data.repository

import androidx.core.net.toUri
import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.dao.SourceDao
import com.joshiminh.wallbase.data.dao.WallpaperDao
import com.joshiminh.wallbase.data.entity.source.Source
import com.joshiminh.wallbase.data.entity.source.SourceEntity
import com.joshiminh.wallbase.data.entity.source.SourceKeys
import com.joshiminh.wallbase.sources.reddit.RedditCommunity
import com.joshiminh.wallbase.sources.twitter.TwitterLinkInfo
import com.joshiminh.wallbase.sources.twitter.parseTwitterLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class SourceRepository(
    private val sourceDao: SourceDao,
    private val wallpaperDao: WallpaperDao,
    private val localStorage: LocalStorageCoordinator
) {

    enum class RemoteSourceType {
        REDDIT,
        PINTEREST,
        WALLHAVEN,
        DANBOORU,
        UNSPLASH,
        ALPHA_CODERS,
        TWITTER,
        WEBSITE
    }

    fun observeSources(): Flow<List<Source>> =
        sourceDao.observeSources().map { entities ->
            entities.map { entity -> entity.sanitized().toDomain() }
        }

    fun observeSource(key: String): Flow<Source?> =
        sourceDao.observeSourceByKey(key).map { entity -> entity?.sanitized()?.toDomain() }

    suspend fun setSourceEnabled(source: Source, enabled: Boolean) {
        sourceDao.setSourceEnabled(source.key, enabled)
    }

    fun detectRemoteSourceType(input: String): RemoteSourceType? =
        parseRemoteSourceInput(input)?.type

    suspend fun addSourceFromInput(input: String): Source {
        val parsed = parseRemoteSourceInput(input)
            ?: throw IllegalArgumentException("Enter a supported subreddit or wallpaper URL.")
        return when (parsed) {
            is RemoteSourceInput.Reddit -> addRedditSource(
                slug = parsed.slug,
                displayName = parsed.displayName,
                description = null
            )

            is RemoteSourceInput.Pinterest -> addPinterestSource(parsed.url)
            is RemoteSourceInput.Wallhaven -> addWallhavenSource(parsed.url)
            is RemoteSourceInput.Danbooru -> addDanbooruSource(parsed.url)
            is RemoteSourceInput.Unsplash -> addUnsplashSource(parsed.url)
            is RemoteSourceInput.AlphaCoders -> addAlphaCodersSource(parsed.url)
            is RemoteSourceInput.Twitter -> addTwitterSource(parsed.url)
            is RemoteSourceInput.Website -> addWebsiteSource(parsed.url)
        }
    }

    suspend fun addRedditCommunity(community: RedditCommunity): Source {
        return addRedditSource(
            slug = community.name,
            displayName = community.displayName,
            description = community.description
        )
    }

    suspend fun removeSource(source: Source, deleteWallpapers: Boolean): Int {
        val wallpapers = if (deleteWallpapers) {
            wallpaperDao.getWallpapersBySource(source.key)
        } else {
            emptyList()
        }
        sourceDao.deleteSourceById(source.id)
        if (!deleteWallpapers) return 0

        wallpapers.forEach { entity ->
            val localUri = entity.localUri
            if (!localUri.isNullOrBlank() && (entity.isDownloaded || entity.sourceKey == SourceKeys.LOCAL)) {
                runCatching { localStorage.deleteDocument(localUri.toUri()) }
                    .onFailure { error ->
                        if (error is IllegalStateException) throw error
                    }
            }
        }

        return wallpaperDao.deleteBySourceKey(source.key)
    }

    suspend fun updateSource(source: Source, input: String): Source {
        val existing = sourceDao.getSourceByKey(source.key)
            ?: throw IllegalArgumentException("Source not found")
        if (existing.isLocal) {
            throw IllegalArgumentException("Local sources can't be edited.")
        }

        val parsed = parseRemoteSourceInput(input)
            ?: throw IllegalArgumentException("Enter a supported subreddit or wallpaper URL.")

        val updated = when (existing.providerKey) {
            SourceKeys.REDDIT -> {
                val redditInput = parsed as? RemoteSourceInput.Reddit
                    ?: throw IllegalArgumentException("Enter a subreddit name or URL.")
                updateRedditSource(existing, redditInput)
            }

            SourceKeys.PINTEREST -> {
                val urlInput = parsed as? RemoteSourceInput.Pinterest
                    ?: throw IllegalArgumentException("Enter a Pinterest board or URL.")
                updateWebsiteSource(existing, urlInput.url, RemoteSourceType.PINTEREST, SourceKeys.PINTEREST)
            }

            SourceKeys.WALLHAVEN -> {
                val urlInput = parsed as? RemoteSourceInput.Wallhaven
                    ?: throw IllegalArgumentException("Enter a Wallhaven search or collection URL.")
                updateWebsiteSource(existing, urlInput.url, RemoteSourceType.WALLHAVEN, SourceKeys.WALLHAVEN)
            }

            SourceKeys.DANBOORU -> {
                val urlInput = parsed as? RemoteSourceInput.Danbooru
                    ?: throw IllegalArgumentException("Enter a Danbooru tag or URL.")
                updateWebsiteSource(existing, urlInput.url, RemoteSourceType.DANBOORU, SourceKeys.DANBOORU)
            }

            SourceKeys.UNSPLASH -> {
                val urlInput = parsed as? RemoteSourceInput.Unsplash
                    ?: throw IllegalArgumentException("Enter an Unsplash collection or URL.")
                updateWebsiteSource(existing, urlInput.url, RemoteSourceType.UNSPLASH, SourceKeys.UNSPLASH)
            }

            SourceKeys.ALPHA_CODERS -> {
                val urlInput = parsed as? RemoteSourceInput.AlphaCoders
                    ?: throw IllegalArgumentException("Enter an AlphaCoders category or URL.")
                updateWebsiteSource(existing, urlInput.url, RemoteSourceType.ALPHA_CODERS, SourceKeys.ALPHA_CODERS)
            }

            SourceKeys.TWITTER -> {
                val urlInput = parsed as? RemoteSourceInput.Twitter
                    ?: throw IllegalArgumentException("Enter an X (Twitter) post or media link.")
                updateWebsiteSource(existing, urlInput.url, RemoteSourceType.TWITTER, SourceKeys.TWITTER)
            }

            SourceKeys.WEBSITES -> {
                val urlInput = parsed as? RemoteSourceInput.Website
                    ?: throw IllegalArgumentException("Enter a website URL.")
                updateWebsiteSource(existing, urlInput.url, RemoteSourceType.WEBSITE, SourceKeys.WEBSITES)
            }

            else -> throw IllegalArgumentException("This source can't be edited.")
        }

        sourceDao.updateSource(updated)
        if (updated.key != existing.key) {
            wallpaperDao.updateSourceKey(existing.key, updated.key)
        }
        return updated.toDomain()
    }

    private suspend fun addRedditSource(
        slug: String,
        displayName: String,
        description: String?
    ): Source {
        val normalized = slug.tryNormalizeSubreddit()
            ?: throw IllegalArgumentException("Enter a subreddit name")
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.REDDIT, normalized)
        if (existing != null) {
            throw IllegalStateException("Subreddit already added")
        }

        val entity = SourceEntity(
            key = buildRedditKey(normalized),
            providerKey = SourceKeys.REDDIT,
            title = displayName.ifBlank { "r/$normalized" },
            description = description?.takeIf { it.isNotBlank() } ?: "r/$normalized",
            iconRes = null,
            iconUrl = buildFaviconUrl("reddit.com"),
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = normalized
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun addPinterestSource(url: NormalizedUrl): Source {
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.PINTEREST, url.value)
        if (existing != null) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, RemoteSourceType.PINTEREST)
        val entity = SourceEntity(
            key = buildWebsiteKey(SourceKeys.PINTEREST, url),
            providerKey = SourceKeys.PINTEREST,
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = url.value
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun addWallhavenSource(url: NormalizedUrl): Source {
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.WALLHAVEN, url.value)
        if (existing != null) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, RemoteSourceType.WALLHAVEN)
        val entity = SourceEntity(
            key = buildWebsiteKey(SourceKeys.WALLHAVEN, url),
            providerKey = SourceKeys.WALLHAVEN,
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = url.value
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun addDanbooruSource(url: NormalizedUrl): Source {
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.DANBOORU, url.value)
        if (existing != null) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, RemoteSourceType.DANBOORU)
        val entity = SourceEntity(
            key = buildWebsiteKey(SourceKeys.DANBOORU, url),
            providerKey = SourceKeys.DANBOORU,
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = url.value
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun addUnsplashSource(url: NormalizedUrl): Source {
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.UNSPLASH, url.value)
        if (existing != null) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, RemoteSourceType.UNSPLASH)
        val entity = SourceEntity(
            key = buildWebsiteKey(SourceKeys.UNSPLASH, url),
            providerKey = SourceKeys.UNSPLASH,
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = url.value
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun addAlphaCodersSource(url: NormalizedUrl): Source {
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.ALPHA_CODERS, url.value)
        if (existing != null) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, RemoteSourceType.ALPHA_CODERS)
        val entity = SourceEntity(
            key = buildWebsiteKey(SourceKeys.ALPHA_CODERS, url),
            providerKey = SourceKeys.ALPHA_CODERS,
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = url.value
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun addTwitterSource(url: NormalizedUrl): Source {
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.TWITTER, url.value)
        if (existing != null) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, RemoteSourceType.TWITTER)
        val entity = SourceEntity(
            key = buildWebsiteKey(SourceKeys.TWITTER, url),
            providerKey = SourceKeys.TWITTER,
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = url.value
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun addWebsiteSource(url: NormalizedUrl): Source {
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.WEBSITES, url.value)
        if (existing != null) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, RemoteSourceType.WEBSITE)
        val entity = SourceEntity(
            key = buildWebsiteKey(SourceKeys.WEBSITES, url),
            providerKey = SourceKeys.WEBSITES,
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = url.value
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).sanitized().toDomain()
    }

    private suspend fun updateRedditSource(
        existing: SourceEntity,
        redditInput: RemoteSourceInput.Reddit
    ): SourceEntity {
        val normalized = redditInput.slug.tryNormalizeSubreddit()
            ?: throw IllegalArgumentException("Enter a subreddit name")
        val duplicate = sourceDao.findSourceByProviderAndConfig(SourceKeys.REDDIT, normalized)
        if (duplicate != null && duplicate.id != existing.id) {
            throw IllegalStateException("Subreddit already added")
        }

        val updated = existing.copy(
            key = buildRedditKey(normalized),
            title = redditInput.displayName.ifBlank { "r/$normalized" },
            description = "r/$normalized",
            iconRes = null,
            iconUrl = buildFaviconUrl("reddit.com"),
            config = normalized
        )
        return updated.sanitized()
    }

    private suspend fun updateWebsiteSource(
        existing: SourceEntity,
        url: NormalizedUrl,
        type: RemoteSourceType,
        providerKey: String
    ): SourceEntity {
        val duplicate = sourceDao.findSourceByProviderAndConfig(providerKey, url.value)
        if (duplicate != null && duplicate.id != existing.id) {
            throw IllegalStateException("Source already added")
        }

        val metadata = buildWebsiteMetadata(url, type)
        val updated = existing.copy(
            key = buildWebsiteKey(providerKey, url),
            title = metadata.title,
            description = metadata.description,
            iconRes = metadata.fallbackIcon,
            iconUrl = metadata.iconUrl,
            config = url.value
        )
        return updated.sanitized()
    }

    private fun SourceEntity.sanitized(): SourceEntity {
        val sanitizedIconRes = when (providerKey) {
            SourceKeys.LOCAL -> iconRes?.takeIf { it != 0 }
            else -> null
        }
        val normalizedIconUrl = iconUrl
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it.isNetworkUrl() }
        val resolvedIconUrl = normalizedIconUrl ?: resolveDefaultIconUrl(providerKey, config)

        val requiresUpdate = sanitizedIconRes != iconRes || resolvedIconUrl != iconUrl
        return if (requiresUpdate) {
            copy(iconRes = sanitizedIconRes, iconUrl = resolvedIconUrl)
        } else {
            this
        }
    }

    private fun String.isNetworkUrl(): Boolean {
        val lower = lowercase(Locale.ROOT)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun resolveDefaultIconUrl(providerKey: String, config: String?): String? {
        return when (providerKey) {
            SourceKeys.REDDIT -> {
                buildFaviconUrl("reddit.com")
            }
            SourceKeys.PINTEREST -> {
                val host = config
                    ?.let { it.tryNormalizeUrl() }
                    ?.host
                    ?.takeIf { it.isNotBlank() }
                val domain = when (host) {
                    null -> "pinterest.com"
                    "pin.it" -> "pinterest.com"
                    else -> host
                }
                buildFaviconUrl(domain)
            }
            SourceKeys.TWITTER -> buildFaviconUrl("x.com")
            SourceKeys.WALLHAVEN,
            SourceKeys.DANBOORU,
            SourceKeys.UNSPLASH,
            SourceKeys.ALPHA_CODERS,
            SourceKeys.WEBSITES -> {
                config
                    ?.let { it.tryNormalizeUrl() }
                    ?.host
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::buildFaviconUrl)
            }
            else -> {
                null
            }
        }
    }

    private fun parseRemoteSourceInput(input: String): RemoteSourceInput? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val subreddit = trimmed.tryNormalizeSubreddit()
        if (!subreddit.isNullOrBlank()) {
            return RemoteSourceInput.Reddit(slug = subreddit)
        }

        val normalizedUrl = trimmed.tryNormalizeUrl() ?: return null
        val host = normalizedUrl.host

        return when {
            host.contains("reddit", ignoreCase = true) -> {
                val slugFromUrl = normalizedUrl.value.tryNormalizeSubreddit()
                if (slugFromUrl != null) {
                    RemoteSourceInput.Reddit(slugFromUrl)
                } else {
                    null
                }
            }

            host.contains("pinterest", ignoreCase = true) || host == "pin.it" -> {
                RemoteSourceInput.Pinterest(normalizedUrl)
            }

            host.contains("wallhaven", ignoreCase = true) || host == "whvn.cc" -> {
                RemoteSourceInput.Wallhaven(normalizedUrl)
            }

            host.contains("danbooru", ignoreCase = true) -> {
                RemoteSourceInput.Danbooru(normalizedUrl)
            }

            host.contains("unsplash", ignoreCase = true) -> {
                RemoteSourceInput.Unsplash(normalizedUrl)
            }

            host.contains("alphacoders", ignoreCase = true) -> {
                RemoteSourceInput.AlphaCoders(normalizedUrl)
            }

            host.contains("twitter", ignoreCase = true) ||
                host.endsWith("x.com", ignoreCase = true) ||
                host.contains("twimg.com", ignoreCase = true) ||
                host.contains("vxtwitter.com", ignoreCase = true) ||
                host.contains("fxtwitter.com", ignoreCase = true) -> {
                val twitterInfo = parseTwitterLink(normalizedUrl.value)
                val canonicalValue = when (twitterInfo) {
                    is TwitterLinkInfo.Tweet -> twitterInfo.canonicalUrl
                    is TwitterLinkInfo.Media -> twitterInfo.imageUrl
                    null -> normalizedUrl.value
                }
                val canonicalUrl = canonicalValue.tryNormalizeUrl() ?: normalizedUrl
                RemoteSourceInput.Twitter(canonicalUrl)
            }

            else -> RemoteSourceInput.Website(normalizedUrl)
        }
    }

    private fun buildRedditKey(config: String): String = "${SourceKeys.REDDIT}:$config"

    private fun buildWebsiteKey(provider: String, url: NormalizedUrl): String {
        val sanitized = url.value
            .removePrefix("https://")
            .removePrefix("http://")
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .ifBlank { url.host.replace(Regex("[^A-Za-z0-9]+"), "_") }
        return "$provider:$sanitized"
    }


    private fun buildWebsiteMetadata(
        url: NormalizedUrl,
        type: RemoteSourceType
    ): WebsiteMetadata {
        val hostName = url.host
            .removePrefix("www.")
            .split('.')
            .filterNot { it.isBlank() || it.equals("www", ignoreCase = true) || it.length == 2 }
            .joinToString(" ") { it.toDisplayNameSegment() }
            .ifBlank { url.host.toDisplayNameSegment() }

        val pathSegment = url.path
            .split('/')
            .lastOrNull { it.isNotBlank() }
            ?.toDisplayNameSegment()

        val queryLabel = url.queryParam("q")?.toDisplayNameSegment()

        val title = when (type) {
            RemoteSourceType.PINTEREST -> listOfNotNull("Pinterest (limited)", queryLabel ?: pathSegment)
                .joinToString(" - ")
                .ifBlank { "Pinterest (limited)" }

            RemoteSourceType.WALLHAVEN -> listOfNotNull("Wallhaven", queryLabel ?: pathSegment)
                .joinToString(" - ")
                .ifBlank { "Wallhaven" }

            RemoteSourceType.DANBOORU -> listOfNotNull("Danbooru", queryLabel ?: pathSegment)
                .joinToString(" - ")
                .ifBlank { "Danbooru" }

            RemoteSourceType.UNSPLASH -> listOfNotNull("Unsplash", queryLabel ?: pathSegment)
                .joinToString(" - ")
                .ifBlank { "Unsplash" }

            RemoteSourceType.ALPHA_CODERS -> listOfNotNull("AlphaCoders", queryLabel ?: pathSegment)
                .joinToString(" - ")
                .ifBlank { "AlphaCoders" }

            RemoteSourceType.TWITTER -> listOfNotNull("X (Twitter)", queryLabel ?: pathSegment)
                .joinToString(" - ")
                .ifBlank { "X (Twitter)" }

            RemoteSourceType.WEBSITE -> listOfNotNull(hostName, queryLabel ?: pathSegment)
                .joinToString(" - ")
                .ifBlank { hostName }

            else -> hostName
        }

        val iconDomain = when (type) {
            RemoteSourceType.REDDIT -> "reddit.com"
            RemoteSourceType.PINTEREST -> when (val host = url.host) {
                "pin.it" -> "pinterest.com"
                else -> host
            }
            RemoteSourceType.TWITTER -> "x.com"
            else -> url.host
        }
        val iconUrl = buildFaviconUrl(iconDomain)

        return WebsiteMetadata(
            title = title,
            description = url.value,
            iconUrl = iconUrl,
            fallbackIcon = null
        )
    }

    private fun buildFaviconUrl(host: String): String {
        val sanitizedHost = host
            .removePrefix("www.")
            .ifBlank { host }
        return "https://www.google.com/s2/favicons?sz=128&domain=$sanitizedHost"
    }

    private fun String.tryNormalizeSubreddit(): String? {
        val trimmed = trim()
        if (trimmed.isBlank()) return null
        val withoutScheme = trimmed
            .removePrefixIgnoreCase("https://")
            .removePrefixIgnoreCase("http://")
            .removePrefixIgnoreCase("www.")
        val afterDomain = withoutScheme.substringAfterDomain("reddit.com/")
        val normalized = afterDomain
            .removePrefixIgnoreCase("r/")
            .substringBefore('/')
            .trim()
            .lowercase(Locale.ROOT)
        return normalized.takeIf { it.isNotBlank() && SUBREDDIT_PATTERN.matches(it) }
    }

    private fun String?.tryNormalizeUrl(): NormalizedUrl? {
        val candidate = this?.trim().orEmpty()
        if (candidate.isBlank()) return null
        val prefixed = if (!candidate.startsWith("http://", true) &&
            !candidate.startsWith("https://", true)) {
            "https://$candidate"
        } else candidate

        return try {
            val url = URL(prefixed)
            val scheme = url.protocol?.lowercase(Locale.ROOT)
                ?.takeIf { it == "http" || it == "https" } ?: "https"
            val host = url.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() } ?: return null
            val port = if (url.port != -1 && url.port != url.defaultPort) ":${url.port}" else ""
            val path = url.path?.trim('/') ?: ""
            val pathComponent = if (path.isBlank()) "" else "/$path"
            val query = url.query
            val fragment = url.ref
            val normalized = buildString {
                append(scheme).append("://").append(host).append(port).append(pathComponent)
                if (!query.isNullOrBlank()) append('?').append(query)
                if (!fragment.isNullOrBlank()) append('#').append(fragment)
            }
            NormalizedUrl(normalized, host, path, query)
        } catch (_: MalformedURLException) {
            null
        }
    }

    private fun String.substringAfterDomain(domain: String): String {
        val index = indexOf(domain, ignoreCase = true)
        return if (index >= 0) substring(index + domain.length) else this
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String {
        return if (startsWith(prefix, ignoreCase = true)) {
            substring(prefix.length)
        } else {
            this
        }
    }

    private fun String.toDisplayNameSegment(): String {
        if (isBlank()) return this
        return split('-', '_', '+')
            .filter { it.isNotBlank() }
            .joinToString(" ") { segment ->
                val lower = segment.lowercase(Locale.ROOT)
                lower.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                }
            }
            .ifBlank { replaceFirstChar { it.uppercaseChar() } }
    }

    private fun NormalizedUrl.queryParam(name: String): String? {
        val queryValue = query ?: return null
        return queryValue.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2) {
                    pieces[0] to pieces[1]
                } else {
                    null
                }
            }
            .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
            ?.second
            ?.let { value ->
                runCatching {
                    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
                }.getOrElse { value }
            }
    }

    private data class WebsiteMetadata(
        val title: String,
        val description: String,
        val iconUrl: String?,
        val fallbackIcon: Int?
    )

    private data class NormalizedUrl(
        val value: String,
        val host: String,
        val path: String,
        val query: String?
    )

    private sealed class RemoteSourceInput(val type: RemoteSourceType) {
        class Reddit(val slug: String) : RemoteSourceInput(RemoteSourceType.REDDIT) {
            val displayName: String = "r/$slug"
        }

        class Pinterest(val url: NormalizedUrl) : RemoteSourceInput(RemoteSourceType.PINTEREST)

        class Wallhaven(val url: NormalizedUrl) : RemoteSourceInput(RemoteSourceType.WALLHAVEN)

        class Danbooru(val url: NormalizedUrl) : RemoteSourceInput(RemoteSourceType.DANBOORU)

        class Unsplash(val url: NormalizedUrl) : RemoteSourceInput(RemoteSourceType.UNSPLASH)

        class AlphaCoders(val url: NormalizedUrl) : RemoteSourceInput(RemoteSourceType.ALPHA_CODERS)

        class Twitter(val url: NormalizedUrl) : RemoteSourceInput(RemoteSourceType.TWITTER)

        class Website(val url: NormalizedUrl) : RemoteSourceInput(RemoteSourceType.WEBSITE)
    }

    private companion object {
        private val SUBREDDIT_PATTERN = Regex("[a-z0-9_]+")
    }
}