package com.joshiminh.wallbase.data.repository

import com.joshiminh.wallbase.util.network.UpdateAssetDto
import com.joshiminh.wallbase.util.network.UpdateReleaseDto
import com.joshiminh.wallbase.util.network.UpdateService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun testParseVersion_variousFormats() {
        val repo = UpdateRepository(
            service = object : UpdateService {
                override suspend fun fetchLatestRelease(): UpdateReleaseDto = error("Not needed")
            }
        )

        val v1 = repo.parseVersion("v6.7")
        assertNotNull(v1)
        assertEquals(6, v1?.major)
        assertEquals(7, v1?.minor)
        assertEquals(0, v1?.patch)
        assertNull(v1?.preRelease)
        assertEquals("6.7", v1?.display)

        val v2 = repo.parseVersion("V6.7.2")
        assertNotNull(v2)
        assertEquals(6, v2?.major)
        assertEquals(7, v2?.minor)
        assertEquals(2, v2?.patch)

        val v3 = repo.parseVersion("6.6-beta1")
        assertNotNull(v3)
        assertEquals(6, v3?.major)
        assertEquals(6, v3?.minor)
        assertEquals(0, v3?.patch)
        assertEquals("beta1", v3?.preRelease)

        assertNull(repo.parseVersion(null))
        assertNull(repo.parseVersion(""))
        assertNull(repo.parseVersion("invalid"))
    }

    @Test
    fun testSemanticVersionComparison() {
        val repo = UpdateRepository(
            service = object : UpdateService {
                override suspend fun fetchLatestRelease(): UpdateReleaseDto = error("Not needed")
            }
        )

        val v66 = repo.parseVersion("v6.6")!!
        val v67 = repo.parseVersion("v6.7")!!
        val v661 = repo.parseVersion("v6.6.1")!!
        val v65 = repo.parseVersion("v6.5")!!
        val v66Beta = repo.parseVersion("v6.6-beta1")!!

        assertTrue("v6.7 should be greater than v6.6", v67 > v66)
        assertTrue("v6.6.1 should be greater than v6.6", v661 > v66)
        assertTrue("v6.5 should be less than v6.6", v65 < v66)
        assertTrue("v6.6 should be greater than v6.6-beta1", v66 > v66Beta)
        assertEquals(0, v66.compareTo(repo.parseVersion("6.6")!!))
    }

    @Test
    fun testDownloadUrl_prefersReleaseApk() {
        val dtoWithBothApks = UpdateReleaseDto(
            tagName = "v6.7",
            htmlUrl = "https://github.com/JoshiMinh/WallBase/releases/tag/v6.7",
            assets = listOf(
                UpdateAssetDto(
                    name = "app-debug.apk",
                    browserDownloadUrl = "https://github.com/download/app-debug.apk"
                ),
                UpdateAssetDto(
                    name = "app-release.apk",
                    browserDownloadUrl = "https://github.com/download/app-release.apk"
                )
            )
        )
        assertEquals(
            "https://github.com/download/app-release.apk",
            dtoWithBothApks.downloadUrl
        )

        val dtoWithOnlyDebug = UpdateReleaseDto(
            tagName = "v6.7",
            htmlUrl = "https://github.com/JoshiMinh/WallBase/releases/tag/v6.7",
            assets = listOf(
                UpdateAssetDto(
                    name = "app-debug.apk",
                    browserDownloadUrl = "https://github.com/download/app-debug.apk"
                )
            )
        )
        assertEquals(
            "https://github.com/download/app-debug.apk",
            dtoWithOnlyDebug.downloadUrl
        )

        val dtoWithoutApk = UpdateReleaseDto(
            tagName = "v6.7",
            htmlUrl = "https://github.com/JoshiMinh/WallBase/releases/tag/v6.7",
            assets = emptyList()
        )
        assertEquals(
            "https://github.com/JoshiMinh/WallBase/releases/tag/v6.7",
            dtoWithoutApk.downloadUrl
        )
    }

    @Test
    fun testMoshiDeserialization_withGitHubApiResponse() {
        val json = """
            {
                "tag_name": "v6.7",
                "body": "## What's new in v6.7\n- Enhanced update downloader\n- Bottom navigation tweaks",
                "html_url": "https://github.com/JoshiMinh/WallBase/releases/tag/v6.7",
                "assets": [
                    {
                        "name": "app-debug.apk",
                        "browser_download_url": "https://github.com/JoshiMinh/WallBase/releases/download/v6.7/app-debug.apk"
                    },
                    {
                        "name": "app-release.apk",
                        "browser_download_url": "https://github.com/JoshiMinh/WallBase/releases/download/v6.7/app-release.apk"
                    }
                ]
            }
        """.trimIndent()

        val adapter = moshi.adapter(UpdateReleaseDto::class.java)
        val release = adapter.fromJson(json)

        assertNotNull(release)
        assertEquals("v6.7", release?.tagName)
        assertTrue(release?.changelog?.contains("Enhanced update downloader") == true)
        assertEquals(2, release?.assets?.size)
        assertEquals(
            "https://github.com/JoshiMinh/WallBase/releases/download/v6.7/app-release.apk",
            release?.downloadUrl
        )
    }

    @Test
    fun testCheckForUpdates_updateAvailable() = runBlocking {
        val mockService = object : UpdateService {
            override suspend fun fetchLatestRelease(): UpdateReleaseDto {
                return UpdateReleaseDto(
                    tagName = "v6.7",
                    changelog = "Bug fixes and new features",
                    htmlUrl = "https://github.com/JoshiMinh/WallBase/releases/tag/v6.7",
                    assets = listOf(
                        UpdateAssetDto(
                            name = "app-release.apk",
                            browserDownloadUrl = "https://github.com/download/app-release.apk"
                        )
                    )
                )
            }
        }

        val repository = UpdateRepository(mockService, Dispatchers.Unconfined)
        val result = repository.checkForUpdates(currentVersionOverride = "6.6")

        assertTrue(result is UpdateRepository.UpdateResult.UpdateAvailable)
        val update = result as UpdateRepository.UpdateResult.UpdateAvailable
        assertEquals("6.7", update.version)
        assertEquals("Bug fixes and new features", update.notes)
        assertEquals("https://github.com/download/app-release.apk", update.downloadUrl)
    }

    @Test
    fun testCheckForUpdates_upToDate() = runBlocking {
        val mockService = object : UpdateService {
            override suspend fun fetchLatestRelease(): UpdateReleaseDto {
                return UpdateReleaseDto(
                    tagName = "v6.6",
                    changelog = "Release notes",
                    htmlUrl = "https://github.com/JoshiMinh/WallBase/releases/tag/v6.6"
                )
            }
        }

        val repository = UpdateRepository(mockService, Dispatchers.Unconfined)
        val result = repository.checkForUpdates(currentVersionOverride = "6.6")

        assertTrue(result is UpdateRepository.UpdateResult.UpToDate)
    }

    @Test
    fun testCheckForUpdates_errorHandling() = runBlocking {
        val mockService = object : UpdateService {
            override suspend fun fetchLatestRelease(): UpdateReleaseDto {
                throw java.io.IOException("Network unreachable")
            }
        }

        val repository = UpdateRepository(mockService, Dispatchers.Unconfined)
        val result = repository.checkForUpdates(currentVersionOverride = "6.6")

        assertTrue(result is UpdateRepository.UpdateResult.Error)
        val error = result as UpdateRepository.UpdateResult.Error
        assertEquals("Network unreachable", error.throwable.message)
    }
}
