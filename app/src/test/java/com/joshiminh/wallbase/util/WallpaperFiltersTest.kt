package com.joshiminh.wallbase.util

import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.util.wallpapers.WallpaperCropSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperFiltersTest {

    private fun createWallpaper(id: String, width: Int?, height: Int?): WallpaperItem {
        return WallpaperItem(
            id = id,
            title = "Test Wallpaper $id",
            imageUrl = "https://example.com/$id.jpg",
            sourceUrl = "https://example.com/source/$id",
            width = width,
            height = height
        )
    }

    @Test
    fun testMinResolutionMatching() {
        val anyFilter = MinResolution.ANY
        val fhdFilter = MinResolution.FHD_1080P
        val qhdFilter = MinResolution.QHD_1440P
        val uhdFilter = MinResolution.UHD_4K

        val wallpaper4k = createWallpaper("4k", 3840, 2160)
        val wallpaperPhone4k = createWallpaper("4k_vert", 2160, 3840)
        val wallpaper1080p = createWallpaper("1080p", 1920, 1080)
        val wallpaper720p = createWallpaper("720p", 1280, 720)
        val wallpaperUnknown = createWallpaper("unknown", null, null)

        // ANY matches all
        assertTrue(anyFilter.matches(wallpaper4k.width, wallpaper4k.height))
        assertTrue(anyFilter.matches(wallpaper1080p.width, wallpaper1080p.height))
        assertTrue(anyFilter.matches(wallpaper720p.width, wallpaper720p.height))
        assertTrue(anyFilter.matches(wallpaperUnknown.width, wallpaperUnknown.height))

        // 1080p+ matches 1080p and 4k (both orientations)
        assertTrue(fhdFilter.matches(wallpaper1080p.width, wallpaper1080p.height))
        assertTrue(fhdFilter.matches(wallpaper4k.width, wallpaper4k.height))
        assertTrue(fhdFilter.matches(wallpaperPhone4k.width, wallpaperPhone4k.height))
        assertFalse(fhdFilter.matches(wallpaper720p.width, wallpaper720p.height))
        assertTrue(fhdFilter.matches(wallpaperUnknown.width, wallpaperUnknown.height)) // Unknown preserved

        // 4K matches only 4K
        assertTrue(uhdFilter.matches(wallpaper4k.width, wallpaper4k.height))
        assertTrue(uhdFilter.matches(wallpaperPhone4k.width, wallpaperPhone4k.height))
        assertFalse(uhdFilter.matches(wallpaper1080p.width, wallpaper1080p.height))
        assertFalse(uhdFilter.matches(wallpaper720p.width, wallpaper720p.height))
    }

    @Test
    fun testFilterByMinResolution() {
        val items = listOf(
            createWallpaper("1", 3840, 2160),
            createWallpaper("2", 1920, 1080),
            createWallpaper("3", 1280, 720),
            createWallpaper("4", 800, 600)
        )

        val filteredFhd = items.filterByMinResolution(MinResolution.FHD_1080P)
        assertEquals(2, filteredFhd.size)
        assertEquals(listOf("1", "2"), filteredFhd.map { it.id })

        val filtered4k = items.filterByMinResolution(MinResolution.UHD_4K)
        assertEquals(1, filtered4k.size)
        assertEquals("1", filtered4k.first().id)
    }

    @Test
    fun testMinResolutionFromStorage() {
        assertEquals(MinResolution.HD_720P, MinResolution.fromStorage("720p"))
        assertEquals(MinResolution.HD_720P, MinResolution.fromStorage("hd"))
        assertEquals(MinResolution.FHD_1080P, MinResolution.fromStorage("1080p"))
        assertEquals(MinResolution.FHD_1080P, MinResolution.fromStorage("fhd"))
        assertEquals(MinResolution.QHD_1440P, MinResolution.fromStorage("1440p"))
        assertEquals(MinResolution.QHD_1440P, MinResolution.fromStorage("2k"))
        assertEquals(MinResolution.UHD_4K, MinResolution.fromStorage("4k"))
        assertEquals(MinResolution.UHD_4K, MinResolution.fromStorage("uhd"))
        assertEquals(MinResolution.ANY, MinResolution.fromStorage("unknown"))
        assertEquals(MinResolution.ANY, MinResolution.fromStorage(null))
    }

    @Test
    fun testWallpaperCropSettingsCenteredForAspectRatio() {
        // Landscape original (16:9 = 1.7778) to Portrait (9:16 = 0.5625)
        val cropPortrait = WallpaperCropSettings.centeredForAspectRatio(9f / 16f, 16f / 9f)
        assertTrue(cropPortrait.left > 0f)
        assertTrue(cropPortrait.right < 1f)
        assertEquals(0f, cropPortrait.top, 0.001f)
        assertEquals(1f, cropPortrait.bottom, 0.001f)
        assertEquals(cropPortrait.left, 1f - cropPortrait.right, 0.001f)

        // Portrait original (9:16 = 0.5625) to Landscape (16:9 = 1.7778)
        val cropLandscape = WallpaperCropSettings.centeredForAspectRatio(16f / 9f, 9f / 16f)
        assertEquals(0f, cropLandscape.left, 0.001f)
        assertEquals(1f, cropLandscape.right, 0.001f)
        assertTrue(cropLandscape.top > 0f)
        assertTrue(cropLandscape.bottom < 1f)
        assertEquals(cropLandscape.top, 1f - cropLandscape.bottom, 0.001f)
    }
}
