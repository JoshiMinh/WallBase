package com.joshiminh.wallbase.data.source

import com.joshiminh.wallbase.R
import com.joshiminh.wallbase.data.local.dao.SourceDao
import com.joshiminh.wallbase.data.local.entity.SourceEntity
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SourceRepository(
    private val sourceDao: SourceDao
) {
    fun observeSources(): Flow<List<Source>> =
        sourceDao.observeSources().map { entities -> entities.map(SourceEntity::toDomain) }

    suspend fun setSourceEnabled(source: Source, enabled: Boolean) {
        sourceDao.setSourceEnabled(source.key, enabled)
    }

    suspend fun addRedditSource(slug: String, displayName: String, description: String?): Source {
        val normalized = slug.normalizeSubreddit().takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Enter a subreddit name")
        val existing = sourceDao.findSourceByProviderAndConfig(SourceKeys.REDDIT, normalized)
        if (existing != null) {
            throw IllegalStateException("Subreddit already added")
        }

        val entity = SourceEntity(
            key = buildRedditKey(normalized),
            providerKey = SourceKeys.REDDIT,
            title = displayName,
            description = description?.takeIf { it.isNotBlank() } ?: displayName,
            iconRes = R.drawable.reddit,
            showInExplore = true,
            isEnabled = true,
            isLocal = false,
            config = normalized
        )
        val id = sourceDao.insertSource(entity)
        return entity.copy(id = id).toDomain()
    }

    suspend fun removeSource(source: Source) {
        sourceDao.deleteSourceById(source.id)
    }

    private fun buildRedditKey(config: String): String = "${SourceKeys.REDDIT}:$config"

    private fun String.normalizeSubreddit(): String {
        val trimmed = trim()
        val withoutScheme = trimmed
            .removePrefixIgnoreCase("https://")
            .removePrefixIgnoreCase("http://")
            .removePrefixIgnoreCase("www.")
        val afterDomain = withoutScheme.substringAfterDomain("reddit.com/")
        return afterDomain
            .removePrefixIgnoreCase("r/")
            .substringBefore('/')
            .trim()
            .lowercase(Locale.ROOT)
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
}
