package com.joshiminh.wallbase.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteImageFetcherTest {
    private val fetcher = RemoteImageFetcher()

    @Test
    fun resolvesOpenGraphImageAgainstPageUrl() {
        val imageUrl = fetcher.resolveImageUrlFromHtml(
            baseUrl = "https://example.com/wallpapers/item",
            html = "<html><head><meta property=\"og:image\" content=\"../images/wallpaper.jpg\"></head></html>"
        )

        assertEquals("https://example.com/images/wallpaper.jpg", imageUrl)
    }

    @Test
    fun usesRedditPreviewImageWhenMetadataIsMissing() {
        val imageUrl = fetcher.resolveImageUrlFromHtml(
            baseUrl = "https://www.reddit.com/r/wallpapers/comments/1/example/",
            html = "<html><body><img src=\"https://preview.redd.it/wallpaper.jpg?width=1080\"></body></html>"
        )

        assertEquals("https://preview.redd.it/wallpaper.jpg?width=1080", imageUrl)
    }

    @Test
    fun ignoresPagesWithoutImageCandidates() {
        val imageUrl = fetcher.resolveImageUrlFromHtml(
            baseUrl = "https://example.com/post",
            html = "<html><body><p>No image here</p></body></html>"
        )

        assertNull(imageUrl)
    }
}
