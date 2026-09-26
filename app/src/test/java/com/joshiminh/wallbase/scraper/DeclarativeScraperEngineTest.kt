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

    @Test
    fun testPixivMaster1200RegexAndArtworkTransform() = runTest {
        val pixivJsonResponse = """
            {
              "contents": [
                {
                  "illust_id": 11223344,
                  "title": "Anime Scenery &amp; Sky",
                  "url": "https://i.pximg.net/c/240x480/img-master/img/2024/05/10/12/00/00/11223344_p0_master1200.jpg",
                  "width": 1920,
                  "height": 1080
                }
              ]
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pixivJsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val testEngine = DeclarativeScraperEngine(mockClient, moshi)

        val manifest = SourceManifest(
            id = "pixiv",
            name = "Pixiv",
            baseUrl = "https://www.pixiv.net",
            version = "1.2.0",
            versionCode = 3,
            feeds = listOf(
                FeedDefinition(
                    id = "daily",
                    title = "Daily Ranking",
                    url = "https://www.pixiv.net/ranking.php?mode=daily&content=illust&p={page}&format=json",
                    extraction = ExtractionRule(
                        format = "json",
                        itemSelector = "contents",
                        fields = mapOf(
                            "id" to FieldExtractor(jsonPath = "illust_id"),
                            "title" to FieldExtractor(jsonPath = "title", transform = "html_unescape"),
                            "thumbnailUrl" to FieldExtractor(jsonPath = "url"),
                            "fullUrl" to FieldExtractor(
                                jsonPath = "url",
                                regexReplace = RegexReplaceRule(find = "/c/[^/]+/", replace = "/")
                            ),
                            "sourceUrl" to FieldExtractor(jsonPath = "illust_id", transform = "pixiv_artwork"),
                            "width" to FieldExtractor(jsonPath = "width"),
                            "height" to FieldExtractor(jsonPath = "height")
                        )
                    )
                )
            )
        )

        val result = testEngine.scrape(manifest = manifest, feedId = "daily")
        assertEquals(1, result.wallpapers.size)
        val item = result.wallpapers[0]
        assertEquals("11223344", item.id)
        assertEquals("Anime Scenery & Sky", item.title)
        assertEquals("https://i.pximg.net/img-master/img/2024/05/10/12/00/00/11223344_p0_master1200.jpg", item.imageUrl)
        assertEquals("https://i.pximg.net/c/240x480/img-master/img/2024/05/10/12/00/00/11223344_p0_master1200.jpg", item.thumbnailUrl)
        assertEquals("https://www.pixiv.net/artworks/11223344", item.sourceUrl)
    }

    @Test
    fun testUnsplashSmallThumbnailAndFullResolution() = runTest {
        val unsplashJsonResponse = """
            [
              {
                "id": "abc123xyz",
                "alt_description": "Mountain sunset &amp; lake",
                "urls": {
                  "raw": "https://images.unsplash.com/photo-123",
                  "full": "https://images.unsplash.com/photo-123?full=1",
                  "regular": "https://images.unsplash.com/photo-123?w=1080",
                  "small": "https://images.unsplash.com/photo-123?w=400",
                  "thumb": "https://images.unsplash.com/photo-123?w=200"
                },
                "links": {
                  "html": "https://unsplash.com/photos/abc123xyz"
                },
                "width": 4000,
                "height": 3000
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
                    .body(unsplashJsonResponse.toResponseBody("application/json".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val testEngine = DeclarativeScraperEngine(mockClient, moshi)

        val manifest = SourceManifest(
            id = "unsplash",
            name = "Unsplash",
            baseUrl = "https://unsplash.com",
            version = "1.2.0",
            versionCode = 3,
            feeds = listOf(
                FeedDefinition(
                    id = "wallpapers",
                    title = "Wallpapers",
                    url = "https://unsplash.com/napi/topics/wallpapers/photos?page={page}&per_page={limit}",
                    extraction = ExtractionRule(
                        format = "json",
                        fields = mapOf(
                            "id" to FieldExtractor(jsonPath = "id"),
                            "title" to FieldExtractor(
                                jsonPath = "alt_description",
                                fallbackJsonPath = "description",
                                defaultValue = "Unsplash Wallpaper",
                                transform = "html_unescape"
                            ),
                            "thumbnailUrl" to FieldExtractor(
                                jsonPath = "urls.small",
                                fallbackJsonPath = "urls.thumb"
                            ),
                            "fullUrl" to FieldExtractor(
                                jsonPath = "urls.full",
                                fallbackJsonPath = "urls.regular"
                            ),
                            "sourceUrl" to FieldExtractor(jsonPath = "links.html"),
                            "width" to FieldExtractor(jsonPath = "width"),
                            "height" to FieldExtractor(jsonPath = "height")
                        )
                    )
                )
            )
        )

        val result = testEngine.scrape(manifest = manifest, feedId = "wallpapers")
        assertEquals(1, result.wallpapers.size)
        val item = result.wallpapers[0]
        assertEquals("abc123xyz", item.id)
        assertEquals("Mountain sunset & lake", item.title)
        assertEquals("https://images.unsplash.com/photo-123?w=400", item.thumbnailUrl)
        assertEquals("https://images.unsplash.com/photo-123?full=1", item.imageUrl)
        assertEquals("https://unsplash.com/photos/abc123xyz", item.sourceUrl)
    }

    @Test
    fun testAlphaCodersLazyLoadAndThumbStripping() = runTest {
        val alphaCodersHtml = """
            <div class="thumb-container-big">
                <a href="/big.php?i=987654">
                    <img class="thumb-img" alt="Cyberpunk City at Night" data-src="https://images7.alphacoders.com/987/thumb-350-987654.png" src="data:image/gif;base64,placeholder" />
                </a>
            </div>
            <div class="wallpaper-thumb">
                <a href="https://wall.alphacoders.com/wallpaper/555444/neon-girl">
                    <picture>
                        <source srcset="https://images.alphacoders.com/555/thumbbig-555444.webp" />
                        <img class="img-responsive" alt="Neon Girl" data-src="https://images.alphacoders.com/555/thumbbig-555444.webp" src="" />
                    </picture>
                </a>
            </div>
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(alphaCodersHtml.toResponseBody("text/html".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val testEngine = DeclarativeScraperEngine(mockClient, moshi)

        val manifest = SourceManifest(
            id = "alphacoders",
            name = "AlphaCoders",
            baseUrl = "https://wall.alphacoders.com",
            version = "1.2.0",
            versionCode = 3,
            feeds = listOf(
                FeedDefinition(
                    id = "newest",
                    title = "Newest",
                    url = "https://wall.alphacoders.com/newest_wallpapers.php?page={page}",
                    extraction = ExtractionRule(
                        format = "html",
                        itemSelector = ".thumb-container-big, .thumb-container, div.boxgrid, div.wallpaper-thumb",
                        fields = mapOf(
                            "id" to FieldExtractor(
                                selector = "a[href*='wallpaper'], a[href*='big.php']",
                                attribute = "href",
                                regex = "(?:wallpaper/|i=)([0-9]+)"
                            ),
                            "title" to FieldExtractor(
                                selector = "img.thumb-img, img.img-responsive, img",
                                attribute = "alt",
                                transform = "html_unescape"
                            ),
                            "thumbnailUrl" to FieldExtractor(
                                selector = "img.thumb-img, img.img-responsive, img",
                                attribute = "src"
                            ),
                            "fullUrl" to FieldExtractor(
                                selector = "img.thumb-img, img.img-responsive, img",
                                attribute = "src",
                                regexReplace = RegexReplaceRule(find = "thumb-[0-9]+-|thumbbig-|thumb-", replace = "")
                            ),
                            "sourceUrl" to FieldExtractor(
                                selector = "a[href*='wallpaper'], a[href*='big.php']",
                                attribute = "href",
                                transform = "prepend_base_url"
                            )
                        )
                    )
                )
            )
        )

        val result = testEngine.scrape(manifest = manifest, feedId = "newest")
        assertEquals(2, result.wallpapers.size)

        val first = result.wallpapers[0]
        assertEquals("987654", first.id)
        assertEquals("Cyberpunk City at Night", first.title)
        assertEquals("https://images7.alphacoders.com/987/987654.png", first.imageUrl)
        assertEquals("https://images7.alphacoders.com/987/thumb-350-987654.png", first.thumbnailUrl)
        assertEquals("https://wall.alphacoders.com/big.php?i=987654", first.sourceUrl)

        val second = result.wallpapers[1]
        assertEquals("555444", second.id)
        assertEquals("Neon Girl", second.title)
        assertEquals("https://images.alphacoders.com/555/555444.webp", second.imageUrl)
        assertEquals("https://images.alphacoders.com/555/thumbbig-555444.webp", second.thumbnailUrl)
        assertEquals("https://wall.alphacoders.com/wallpaper/555444/neon-girl", second.sourceUrl)
    }

    @Test
    fun testPexelsHtmlExtractionAndResolutionParam() = runTest {
        val pexelsHtml = """
            <article data-testid="item">
                <a href="https://www.pexels.com/photo/misty-forest-1234567/">
                    <img src="https://images.pexels.com/photos/1234567/pexels-photo-1234567.jpeg?auto=compress&cs=tinysrgb&dpr=1&w=500" alt="Misty Forest at Sunrise" />
                </a>
            </article>
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(pexelsHtml.toResponseBody("text/html".toMediaTypeOrNull()))
                    .build()
            })
            .build()

        val testEngine = DeclarativeScraperEngine(mockClient, moshi)

        val manifest = SourceManifest(
            id = "pexels",
            name = "Pexels",
            baseUrl = "https://www.pexels.com",
            version = "1.2.0",
            versionCode = 3,
            feeds = listOf(
                FeedDefinition(
                    id = "wallpapers",
                    title = "Wallpapers",
                    url = "https://www.pexels.com/search/wallpaper/?page={page}",
                    extraction = ExtractionRule(
                        format = "html",
                        itemSelector = "article[data-testid='item'], [data-testid='photo-card'], div[data-testid='grid-item'], div.MediaCard_card__",
                        fields = mapOf(
                            "id" to FieldExtractor(
                                selector = "a[href*='photo']",
                                attribute = "href",
                                regex = "(?:photo/.*-([0-9]+)|photo/([0-9]+)|-([0-9]+)/?)"
                            ),
                            "title" to FieldExtractor(
                                selector = "img",
                                attribute = "alt",
                                defaultValue = "Pexels Wallpaper",
                                transform = "html_unescape"
                            ),
                            "thumbnailUrl" to FieldExtractor(
                                selector = "img",
                                attribute = "src",
                                regexReplace = RegexReplaceRule(find = "\\?.*", replace = "?auto=compress&cs=tinysrgb&dpr=1&w=500")
                            ),
                            "fullUrl" to FieldExtractor(
                                selector = "img",
                                attribute = "src",
                                regexReplace = RegexReplaceRule(find = "\\?.*", replace = "?auto=compress&cs=tinysrgb&dpr=1&h=2560")
                            ),
                            "sourceUrl" to FieldExtractor(
                                selector = "a[href*='photo']",
                                attribute = "href",
                                transform = "prepend_base_url"
                            )
                        )
                    )
                )
            )
        )

        val result = testEngine.scrape(manifest = manifest, feedId = "wallpapers")
        assertEquals(1, result.wallpapers.size)
        val item = result.wallpapers[0]
        assertEquals("1234567", item.id)
        assertEquals("Misty Forest at Sunrise", item.title)
        assertEquals("https://images.pexels.com/photos/1234567/pexels-photo-1234567.jpeg?auto=compress&cs=tinysrgb&dpr=1&w=500", item.thumbnailUrl)
        assertEquals("https://images.pexels.com/photos/1234567/pexels-photo-1234567.jpeg?auto=compress&cs=tinysrgb&dpr=1&h=2560", item.imageUrl)
        assertEquals("https://www.pexels.com/photo/misty-forest-1234567/", item.sourceUrl)
    }
}


