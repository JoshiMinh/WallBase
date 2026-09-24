package com.joshiminh.wallbase.scraper

import com.joshiminh.wallbase.scraper.engine.DeclarativeScraperEngine
import com.joshiminh.wallbase.scraper.model.ExtractionRule
import com.joshiminh.wallbase.scraper.model.FeedDefinition
import com.joshiminh.wallbase.scraper.model.FieldExtractor
import com.joshiminh.wallbase.scraper.model.PaginationConfig
import com.joshiminh.wallbase.scraper.model.RegexReplaceRule
import com.joshiminh.wallbase.scraper.model.SourceManifest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeclarativeScraperEngineTest {

    private lateinit var moshi: Moshi
    private lateinit var engine: DeclarativeScraperEngine

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        engine = DeclarativeScraperEngine(
            okHttpClient = OkHttpClient(),
            moshi = moshi
        )
    }

    @Test
    fun testManifestJsonDeserialization() {
        val json = """
            {
              "id": "test_alphacoders",
              "name": "AlphaCoders Test",
              "baseUrl": "https://wall.alphacoders.com",
              "version": "1.0.0",
              "versionCode": 1,
              "feeds": [
                {
                  "id": "newest",
                  "title": "Newest",
                  "url": "https://wall.alphacoders.com/newest.php?page={page}",
                  "extraction": {
                    "format": "html",
                    "itemSelector": ".thumb-container",
                    "fields": {
                      "title": { "selector": "img", "attribute": "alt" },
                      "thumbnailUrl": { "selector": "img", "attribute": "src" },
                      "fullUrl": {
                        "selector": "img",
                        "attribute": "src",
                        "regexReplace": { "find": "/thumb-", "replace": "/" }
                      }
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val adapter = moshi.adapter(SourceManifest::class.java)
        val manifest = adapter.fromJson(json)

        assertNotNull(manifest)
        assertEquals("test_alphacoders", manifest?.id)
        assertEquals("AlphaCoders Test", manifest?.name)
        assertEquals(1, manifest?.feeds?.size)
        assertEquals("html", manifest?.feeds?.first()?.extraction?.format)
        assertEquals(".thumb-container", manifest?.feeds?.first()?.extraction?.itemSelector)
    }

    @Test
    fun testPaginationConfigCalculations() {
        val config = PaginationConfig(
            type = "page_number",
            startPage = 1,
            step = 1
        )
        assertEquals("page_number", config.type)
        assertEquals(1, config.startPage)
        assertEquals(1, config.step)

        val offsetConfig = PaginationConfig(
            type = "offset",
            startPage = 0,
            step = 30
        )
        assertEquals("offset", offsetConfig.type)
    }
}

