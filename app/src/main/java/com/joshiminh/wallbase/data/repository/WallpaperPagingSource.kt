package com.joshiminh.wallbase.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.joshiminh.wallbase.data.entity.Source
import com.joshiminh.wallbase.data.entity.WallpaperItem

/**
 * [PagingSource] implementation that delegates cursor-based pagination to [WallpaperRepository].
 * Supports Reddit, Wallhaven, Pinterest, and custom web sources seamlessly.
 */
class WallpaperPagingSource(
    private val wallpaperRepository: WallpaperRepository,
    private val source: Source,
    private val query: String? = null
) : PagingSource<String, WallpaperItem>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, WallpaperItem> {
        return try {
            val cursor = params.key
            val page = wallpaperRepository.fetchWallpapersFor(
                source = source,
                query = query,
                cursor = cursor
            )

            LoadResult.Page(
                data = page.wallpapers,
                prevKey = null,
                nextKey = if (page.wallpapers.isEmpty()) null else page.nextCursor
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, WallpaperItem>): String? {
        // Cursor-based network sources refresh from the top (initial page cursor = null)
        return null
    }
}
