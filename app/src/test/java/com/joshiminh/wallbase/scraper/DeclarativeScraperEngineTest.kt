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
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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

    @Test
    fun testRedditCursorExtractionAndFallback() = runTest {
        val redditJsonResponse = """
            {
              "data": {
                "after": "t3_cursor123",
                "children": [
                  {
                    "data": {
                      "id": "post_1",
                      "title": "Cosmic Aurora",
                      "url": "https://i.redd.it/post1.jpg",
                      "thumbnail": "https://b.thumbs.redditmedia.com/thumb1.jpg",
                      "permalink": "/r/wallpaper/comments/post_1/cosmic_aurora/"
                    }
                  },
                  {
                    "data": {
                      "id": "post_2",
                      "title": "Gallery Post",
                      "url_overridden_by_dest": "https://i.redd.it/post2.png",
                      "thumbnail": "default",
                      "permalink": "/r/wallpaper/comments/post_2/gallery_post/"
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(redditJsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val testEngine = DeclarativeScraperEngine(mockClient, moshi)

        val manifest = SourceManifest(
            id = "reddit",
            name = "Reddit",
            baseUrl = "https://www.reddit.com",
            version = "1.0.0",
            versionCode = 1,
            feeds = listOf(
                FeedDefinition(
                    id = "hot",
                    title = "Hot",
                    url = "https://www.reddit.com/r/wallpaper/hot.json?after={cursor}&limit={limit}",
                    pagination = PaginationConfig(type = "cursor"),
                    extraction = ExtractionRule(
                        format = "json",
                        itemSelector = "data.children",
                        nextCursorJsonPath = "data.after",
                        fields = mapOf(
                            "id" to FieldExtractor(jsonPath = "data.id"),
                            "title" to FieldExtractor(jsonPath = "data.title"),
                            "fullUrl" to FieldExtractor(
                                jsonPath = "data.url_overridden_by_dest",
                                fallbackJsonPath = "data.url"
                            ),
                            "thumbnailUrl" to FieldExtractor(
                                jsonPath = "data.thumbnail",
                                fallbackJsonPath = "data.url"
                            ),
                            "sourceUrl" to FieldExtractor(
                                jsonPath = "data.permalink",
                                transform = "prepend_base_url"
                            )
                        )
                    )
                )
            )
        )

        val result = testEngine.scrape(manifest = manifest, feedId = "hot")

        assertEquals(2, result.wallpapers.size)
        assertEquals("t3_cursor123", result.nextCursor)

        val first = result.wallpapers[0]
        assertEquals("post_1", first.id)
        assertEquals("Cosmic Aurora", first.title)
        assertEquals("https://i.redd.it/post1.jpg", first.imageUrl)
        assertEquals("https://www.reddit.com/r/wallpaper/comments/post_1/cosmic_aurora/", first.sourceUrl)

        val second = result.wallpapers[1]
        assertEquals("post_2", second.id)
        assertEquals("Gallery Post", second.title)
        assertEquals("https://i.redd.it/post2.png", second.imageUrl)
        assertEquals("https://www.reddit.com/r/wallpaper/comments/post_2/gallery_post/", second.sourceUrl)
    }

    @Test
    fun testUrlTransformsAndHttpEnsure() = runTest {
        val jsonResponse = """
            [
              {
                "id": 999,
                "name": "Art Piece",
                "preview_url": "//safebooru.org/thumbnails/999.jpg",
                "sample_url": "safebooru.org/images/999.jpg"
              }
            ]
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val testEngine = DeclarativeScraperEngine(mockClient, moshi)

        val manifest = SourceManifest(
            id = "safebooru",
            name = "Safebooru",
            baseUrl = "https://safebooru.org",
            version = "1.0.0",
            versionCode = 1,
            feeds = listOf(
                FeedDefinition(
                    id = "latest",
                    title = "Latest",
                    url = "https://safebooru.org/index.php?page=dapi&s=post&q=index&json=1",
                    extraction = ExtractionRule(
                        format = "json",
                        fields = mapOf(
                            "id" to FieldExtractor(jsonPath = "id"),
                            "title" to FieldExtractor(jsonPath = "name"),
                            "fullUrl" to FieldExtractor(
                                jsonPath = "sample_url",
                                transform = "ensure_https"
                            ),
                            "thumbnailUrl" to FieldExtractor(
                                jsonPath = "preview_url",
                                transform = "ensure_https"
                            )
                        )
                    )
                )
            )
        )

        val result = testEngine.scrape(manifest = manifest, feedId = "latest")
        assertEquals(1, result.wallpapers.size)
        val item = result.wallpapers[0]
        assertEquals("https://safebooru.org/images/999.jpg", item.imageUrl)
        assertEquals("https://safebooru.org/thumbnails/999.jpg", item.thumbnailUrl)
    }
}


