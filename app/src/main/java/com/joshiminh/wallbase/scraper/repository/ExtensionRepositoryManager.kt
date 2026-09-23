package com.joshiminh.wallbase.scraper.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.joshiminh.wallbase.data.dao.SourceDao
import com.joshiminh.wallbase.data.entity.SourceEntity
import com.joshiminh.wallbase.data.entity.SourceKeys
import com.joshiminh.wallbase.scraper.model.ExtensionRepo
import com.joshiminh.wallbase.scraper.model.ExtensionRepoItem
import com.joshiminh.wallbase.scraper.model.SourceManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtensionRepositoryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val okHttpClient: OkHttpClient,
    private val sourceDao: SourceDao,
    private val dataStore: DataStore<Preferences>
) {

    private val manifestAdapter by lazy { moshi.adapter(SourceManifest::class.java) }
    private val repoAdapter by lazy { moshi.adapter(ExtensionRepo::class.java) }

    private val extensionsDir by lazy {
        File(context.filesDir, "extensions").apply { if (!exists()) mkdirs() }
    }

    private companion object {
        val SUBSCRIBED_REPOS_KEY = stringSetPreferencesKey("subscribed_extension_repos")
        val INSTALLED_IDS_KEY = stringSetPreferencesKey("installed_extension_ids")
        const val DEFAULT_COMMUNITY_REPO = "https://raw.githubusercontent.com/JoshiMinh/WallBase/main/extensions/repo.json"
    }

    /**
     * Reads all built-in bundled extension manifests from app assets.
     */
    suspend fun getBuiltInManifests(): List<SourceManifest> = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        val assetFiles = runCatching { assetManager.list("extensions") }.getOrNull() ?: emptyArray()

        assetFiles.filter { it.endsWith(".json") && it != "repo.json" }.mapNotNull { fileName ->
            runCatching {
                assetManager.open("extensions/$fileName").bufferedReader().use { reader ->
                    manifestAdapter.fromJson(reader.readText())
                }
            }.getOrNull()
        }
    }

    /**
     * Initializes default extension sources into Room DB if not already added.
     */
    suspend fun ensureDefaultExtensionsInstalled() = withContext(Dispatchers.IO) {
        val builtIns = getBuiltInManifests()
        val existingKeys = sourceDao.getSourceKeys().toSet()

        builtIns.forEach { manifest ->
            val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"
            if (sourceKey !in existingKeys) {
                // Save manifest file
                saveManifestToFile(manifest)
                // Register in Room Database
                val entity = SourceEntity(
                    key = sourceKey,
                    providerKey = SourceKeys.EXTENSION,
                    title = manifest.name,
                    description = manifest.description ?: "${manifest.name} Wallpapers",
                    iconRes = null,
                    iconUrl = manifest.iconUrl,
                    showInExplore = true,
                    isEnabled = true,
                    isLocal = false,
                    config = manifest.id
                )
                sourceDao.insertSource(entity)
            }
        }

        // Auto subscribe default community repo if not already subscribed
        dataStore.edit { prefs ->
            val current = prefs[SUBSCRIBED_REPOS_KEY] ?: emptySet()
            if (DEFAULT_COMMUNITY_REPO !in current) {
                prefs[SUBSCRIBED_REPOS_KEY] = current + DEFAULT_COMMUNITY_REPO
            }
        }
    }

    /**
     * Observe list of all installed SourceManifests (built-in + user imported).
     */
    fun observeInstalledManifests(): Flow<List<SourceManifest>> =
        dataStore.data.map {
            getInstalledManifestsList()
        }.distinctUntilChanged()

    suspend fun getInstalledManifestsList(): List<SourceManifest> = withContext(Dispatchers.IO) {
        val builtInMap = getBuiltInManifests().associateBy { it.id }.toMutableMap()
        val customFiles = extensionsDir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()

        customFiles.forEach { file ->
            runCatching {
                val json = file.readText()
                val manifest = manifestAdapter.fromJson(json)
                if (manifest != null) {
                    builtInMap[manifest.id] = manifest
                }
            }
        }
        builtInMap.values.toList()
    }

    suspend fun getManifestById(id: String): SourceManifest? = withContext(Dispatchers.IO) {
        // 1. Check disk file
        val file = File(extensionsDir, "$id.json")
        if (file.exists()) {
            runCatching { manifestAdapter.fromJson(file.readText()) }.getOrNull()?.let { return@withContext it }
        }
        // 2. Check built-ins
        getBuiltInManifests().firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    suspend fun installOrUpdateManifest(manifest: SourceManifest): SourceEntity = withContext(Dispatchers.IO) {
        saveManifestToFile(manifest)

        val sourceKey = "${SourceKeys.EXTENSION}:${manifest.id}"
        val existing = sourceDao.getSourceByKey(sourceKey)

        val entity = if (existing != null) {
            existing.copy(
                title = manifest.name,
                description = manifest.description ?: existing.description,
                iconUrl = manifest.iconUrl ?: existing.iconUrl,
                config = manifest.id
            ).also { sourceDao.updateSource(it) }
        } else {
            val newEntity = SourceEntity(
                key = sourceKey,
                providerKey = SourceKeys.EXTENSION,
                title = manifest.name,
                description = manifest.description ?: "${manifest.name} Wallpapers",
                iconRes = null,
                iconUrl = manifest.iconUrl,
                showInExplore = true,
                isEnabled = true,
                isLocal = false,
                config = manifest.id
            )
            val id = sourceDao.insertSource(newEntity)
            newEntity.copy(id = id)
        }

        dataStore.edit { prefs ->
            val current = prefs[INSTALLED_IDS_KEY] ?: emptySet()
            prefs[INSTALLED_IDS_KEY] = current + manifest.id
        }

        entity
    }

    suspend fun uninstallManifest(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(extensionsDir, "$id.json")
        if (file.exists()) {
            file.delete()
        }
        val sourceKey = "${SourceKeys.EXTENSION}:$id"
        val existing = sourceDao.getSourceByKey(sourceKey)
        if (existing != null) {
            sourceDao.deleteSourceById(existing.id)
        }
        dataStore.edit { prefs ->
            val current = prefs[INSTALLED_IDS_KEY] ?: emptySet()
            prefs[INSTALLED_IDS_KEY] = current - id
        }
        true
    }

    fun parseManifestFromJson(jsonString: String): Result<SourceManifest> = runCatching {
        manifestAdapter.fromJson(jsonString)
            ?: throw IllegalArgumentException("Invalid manifest JSON")
    }

    suspend fun fetchRemoteRepo(url: String): Result<ExtensionRepo> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val responseBody = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                response.body?.string().orEmpty()
            }
            repoAdapter.fromJson(responseBody)
                ?: throw IllegalArgumentException("Malformed repository JSON manifest")
        }.recoverCatching {
            context.assets.open("extensions/repo.json").bufferedReader().use { reader ->
                repoAdapter.fromJson(reader.readText())
                    ?: throw IllegalArgumentException("Malformed local repository manifest")
            }
        }
    }

    suspend fun downloadAndInstallFromUrl(manifestUrl: String): Result<SourceManifest> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(manifestUrl).build()
            val responseBody = okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}: ${response.message}")
                response.body?.string().orEmpty()
            }
            val manifest = parseManifestFromJson(responseBody).getOrThrow()
            installOrUpdateManifest(manifest)
            manifest
        }
    }

    fun observeSubscribedRepos(): Flow<Set<String>> =
        dataStore.data.map { prefs ->
            prefs[SUBSCRIBED_REPOS_KEY] ?: setOf(DEFAULT_COMMUNITY_REPO)
        }.distinctUntilChanged()

    suspend fun addSubscribedRepo(url: String) {
        dataStore.edit { prefs ->
            val current = prefs[SUBSCRIBED_REPOS_KEY] ?: setOf(DEFAULT_COMMUNITY_REPO)
            prefs[SUBSCRIBED_REPOS_KEY] = current + url.trim()
        }
    }

    suspend fun removeSubscribedRepo(url: String) {
        dataStore.edit { prefs ->
            val current = prefs[SUBSCRIBED_REPOS_KEY] ?: setOf(DEFAULT_COMMUNITY_REPO)
            prefs[SUBSCRIBED_REPOS_KEY] = current - url.trim()
        }
    }

    private fun saveManifestToFile(manifest: SourceManifest) {
        val file = File(extensionsDir, "${manifest.id}.json")
        file.writeText(manifestAdapter.toJson(manifest))
    }
}

