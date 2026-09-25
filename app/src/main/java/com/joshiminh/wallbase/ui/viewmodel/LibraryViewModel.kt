@file:Suppress("UNCHECKED_CAST")

package com.joshiminh.wallbase.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joshiminh.wallbase.data.entity.AlbumItem
import com.joshiminh.wallbase.data.entity.CategoryItem
import com.joshiminh.wallbase.data.entity.CategoryWithWallpapers
import com.joshiminh.wallbase.data.entity.WallpaperItem
import com.joshiminh.wallbase.data.repository.AlbumLayout
import com.joshiminh.wallbase.data.repository.LibraryRepository
import com.joshiminh.wallbase.data.repository.LibraryRepository.DirectAddResult
import com.joshiminh.wallbase.data.repository.SettingsRepository
import com.joshiminh.wallbase.data.repository.SettingsPreferences
import com.joshiminh.wallbase.data.repository.WallpaperLayout
import com.joshiminh.wallbase.util.AlbumSortOption
import com.joshiminh.wallbase.util.WallpaperSortOption
import com.joshiminh.wallbase.util.sortedWith
import com.joshiminh.wallbase.util.DownloadedFilter
import com.joshiminh.wallbase.util.filterByDownloadStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val messageFlow = MutableStateFlow<String?>(null)
    private val isCreatingAlbum = MutableStateFlow(false)
    private val selectedCategoryId = MutableStateFlow<Long?>(null)
    private val selectionActionInProgress = MutableStateFlow(false)
    private val selectionAction = MutableStateFlow<SelectionAction?>(null)
    private val directAddInProgress = MutableStateFlow(false)
    private val directAddStatus = MutableStateFlow<Boolean?>(null)
    private val wallpaperSort = MutableStateFlow(WallpaperSortOption.RECENTLY_ADDED)
    private val albumSort = MutableStateFlow(AlbumSortOption.TITLE_ASCENDING)
    private val downloadedFilter = MutableStateFlow(DownloadedFilter.SHOW_ALL)
    private val favoritesOnly = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private var storageLimitBytes: Long = 0L

    val uiState: StateFlow<LibraryUiState> =
        combine(
            repository.observeSavedWallpapers(),
            repository.observeAlbums(),
            repository.observeCategoriesWithWallpapers(),
            selectedCategoryId,
            isCreatingAlbum,
            selectionActionInProgress,
            selectionAction,
            messageFlow,
            wallpaperSort,
            albumSort,
            settingsRepository.preferences,
            directAddInProgress,
            directAddStatus,
            downloadedFilter,
            favoritesOnly,
            isRefreshing
        ) { values ->
            values.toLibraryStateInputs()
        }.map { inputs ->
            storageLimitBytes = inputs.preferences.storageLimitBytes

            val categoryItems = inputs.categoriesWithWallpapers.map { it.toCategoryItem() }
            val categoriesEnabled = inputs.preferences.categoriesEnabled

            val baseWallpapers = inputs.wallpapers
                .sortedWith(inputs.wallpaperSortOption)
                .filterByDownloadStatus(inputs.downloadedFilter)
            val filteredWallpapers = if (inputs.favoritesOnly) {
                baseWallpapers.filter { it.isFavorite }
            } else {
                baseWallpapers
            }

            val categoryFilteredWallpapers = if (categoriesEnabled && inputs.selectedCategoryId != null) {
                val category = inputs.categoriesWithWallpapers.find { it.category.id == inputs.selectedCategoryId }
                val allowedWallpaperIds = category?.wallpapers.orEmpty().map { it.id }.toSet()
                val allowedKeys = category?.wallpapers.orEmpty().map { "${it.sourceKey}:${it.remoteId ?: it.id}" }.toSet()
                filteredWallpapers.filter { it.id in allowedKeys || it.remoteIdentifierWithinSource()?.toLongOrNull() in allowedWallpaperIds }
            } else {
                filteredWallpapers
            }

            LibraryUiState(
                wallpapers = categoryFilteredWallpapers,
                allWallpapersCount = filteredWallpapers.size,
                albums = inputs.albums.sortedWith(inputs.albumSortOption),
                categories = categoryItems,
                selectedCategoryId = inputs.selectedCategoryId,
                categoriesEnabled = categoriesEnabled,
                isCreatingAlbum = inputs.isCreatingAlbum,
                isSelectionActionInProgress = inputs.isSelectionActionInProgress,
                selectionAction = inputs.selectionAction,
                message = inputs.message,
                wallpaperSortOption = inputs.wallpaperSortOption,
                albumSortOption = inputs.albumSortOption,
                wallpaperGridColumns = inputs.preferences.wallpaperGridColumns,
                albumLayout = inputs.preferences.albumLayout,
                wallpaperLayout = inputs.preferences.wallpaperLayout,
                isDirectAddInProgress = inputs.isDirectAddInProgress,
                directAddCompleted = inputs.directAddCompleted,
                downloadedFilter = inputs.downloadedFilter,
                favoritesOnly = inputs.favoritesOnly,
                isRefreshing = inputs.isRefreshing,
                showDownloadBadge = inputs.preferences.showDownloadBadge
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LibraryUiState()
        )

    fun refreshLibrary() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                repository.rescanLibrary()
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun updateFavoritesFilter(enabled: Boolean) {
        favoritesOnly.value = enabled
    }

    fun renameWallpaper(wallpaper: WallpaperItem, customTitle: String?) {
        viewModelScope.launch {
            val trimmed = customTitle?.trim()?.takeIf { it.isNotBlank() }
            val success = repository.renameWallpaper(wallpaper, trimmed)
            if (success) {
                messageFlow.value = if (trimmed != null) "Wallpaper renamed" else "Wallpaper name reset to original"
            } else {
                messageFlow.value = "Unable to rename wallpaper"
            }
        }
    }

    fun updateWallpaperSort(option: WallpaperSortOption) {
        wallpaperSort.value = option
    }

    fun updateAlbumSort(option: AlbumSortOption) {
        albumSort.value = option
    }

    fun updateDownloadedFilter(filter: DownloadedFilter) {
        downloadedFilter.value = filter
    }

    fun updateWallpaperGridColumns(columns: Int) {
        viewModelScope.launch {
            settingsRepository.setWallpaperGridColumns(columns)
        }
    }

    fun updateWallpaperLayout(layout: WallpaperLayout) {
        viewModelScope.launch {
            settingsRepository.setWallpaperLayout(layout)
        }
    }

    fun updateAlbumLayout(layout: AlbumLayout) {
        viewModelScope.launch {
            settingsRepository.setAlbumLayout(layout)
        }
    }

    fun addDirectWallpaper(link: String) {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) {
            messageFlow.value = "Enter a direct image link"
            return
        }
        if (directAddInProgress.value) return

        directAddStatus.value = null
        viewModelScope.launch {
            directAddInProgress.value = true
            val outcome = runCatching { repository.addDirectWallpaper(trimmed) }
                .getOrElse { error ->
                    DirectAddResult.Failure(error.localizedMessage ?: "Unable to add wallpaper")
                }
            directAddInProgress.value = false
            when (outcome) {
                is DirectAddResult.Success -> {
                    val title = outcome.wallpaper.title.takeIf { it.isNotBlank() } ?: "Wallpaper"
                    messageFlow.value = "Added \"$title\" to your library"
                    directAddStatus.value = true
                }

                is DirectAddResult.AlreadyExists -> {
                    val title = outcome.wallpaper?.title?.takeIf { it.isNotBlank() } ?: "Wallpaper"
                    messageFlow.value = "\"$title\" is already in your library"
                    directAddStatus.value = true
                }

                is DirectAddResult.Failure -> {
                    messageFlow.value = outcome.reason
                    directAddStatus.value = false
                }
            }
        }
    }

    fun createAlbum(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            messageFlow.value = "Enter a name for your album"
            return
        }
        if (isCreatingAlbum.value) return

        viewModelScope.launch {
            isCreatingAlbum.value = true
            val result = runCatching { repository.createAlbum(trimmed) }
            isCreatingAlbum.value = false
            messageFlow.update {
                result.fold(
                    onSuccess = { album -> "Created \"${album.title}\"" },
                    onFailure = { t -> t.localizedMessage ?: "Unable to create album" }
                )
            }
        }
    }

    fun removeWallpapers(wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.REMOVE_FROM_LIBRARY
            selectionActionInProgress.value = true
            val result = runCatching { repository.removeWallpapers(wallpapers) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { removed ->
                        when {
                            removed > 1 -> "Removed $removed wallpapers from your library"
                            removed == 1 -> "Removed 1 wallpaper from your library"
                            else -> "No wallpapers were removed"
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to remove wallpapers" }
                )
            }
        }
    }

    fun addWallpapersToAlbum(albumId: Long, wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.ADD_TO_ALBUM
            selectionActionInProgress.value = true
            val result = runCatching { repository.addWallpapersToAlbum(albumId, wallpapers) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { outcome ->
                        when {
                            outcome.addedToAlbum > 0 && (outcome.alreadyPresent > 0 || outcome.skipped > 0) ->
                                "Added ${outcome.addedToAlbum} wallpapers (skipped ${outcome.alreadyPresent + outcome.skipped})"
                            outcome.addedToAlbum > 0 ->
                                "Added ${outcome.addedToAlbum} wallpapers to the album"
                            outcome.alreadyPresent > 0 && outcome.skipped == 0 ->
                                "All selected wallpapers are already in this album"
                            outcome.skipped > 0 ->
                                "Unable to add ${outcome.skipped} wallpapers to the album"
                            else -> "All selected wallpapers are already in this album"
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to update album" }
                )
            }
        }
    }

    fun downloadWallpapers(wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.DOWNLOAD
            selectionActionInProgress.value = true
            val result = runCatching { repository.downloadWallpapers(wallpapers, storageLimitBytes) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { summary ->
                        when {
                            summary.downloaded > 0 && summary.failed > 0 ->
                                "Downloaded ${summary.downloaded} wallpapers (failed ${summary.failed})"
                            summary.downloaded > 0 && summary.blocked > 0 ->
                                "Downloaded ${summary.downloaded} wallpapers (blocked ${summary.blocked} by storage limit)"
                            summary.downloaded > 0 ->
                                "Downloaded ${summary.downloaded} wallpapers"
                            summary.blocked > 0 ->
                                "Storage limit reached. Download blocked."
                            summary.skipped > 0 ->
                                "Selected wallpapers are already saved locally"
                            else -> "No wallpapers were downloaded"
                        }
                    },
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to download wallpapers"
                    }
                )
            }
        }
    }

    fun deleteAlbums(albumIds: Collection<Long>) {
        if (albumIds.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.DELETE_ALBUMS
            selectionActionInProgress.value = true
            val result = runCatching { repository.deleteAlbums(albumIds) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { deleted ->
                        when {
                            deleted > 1 -> "Deleted $deleted albums"
                            deleted == 1 -> "Deleted 1 album"
                            else -> "No albums were deleted"
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to delete albums" }
                )
            }
        }
    }

    fun downloadAlbums(albumIds: Collection<Long>) {
        if (albumIds.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.DOWNLOAD
            selectionActionInProgress.value = true
            val result = runCatching { repository.downloadAlbums(albumIds, storageLimitBytes) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { summary ->
                        when {
                            summary.downloaded > 0 && summary.failed > 0 ->
                                "Downloaded ${summary.downloaded} wallpapers (failed ${summary.failed})"
                            summary.downloaded > 0 && summary.blocked > 0 ->
                                "Downloaded ${summary.downloaded} wallpapers (blocked ${summary.blocked} by storage limit)"
                            summary.downloaded > 0 ->
                                "Downloaded ${summary.downloaded} wallpapers"
                            summary.blocked > 0 ->
                                "Storage limit reached. Download blocked."
                            summary.skipped > 0 ->
                                "Selected wallpapers are already saved locally"
                            else -> "No wallpapers were downloaded"
                        }
                    },
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to download wallpapers"
                    }
                )
            }
        }
    }

    fun removeDownloads(wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.REMOVE_DOWNLOADS
            selectionActionInProgress.value = true
            val result = runCatching { repository.removeDownloads(wallpapers) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { summary ->
                        when {
                            summary.removed > 0 && summary.failed > 0 ->
                                "Removed downloads for ${summary.removed} wallpapers (failed ${summary.failed})"
                            summary.removed > 0 ->
                                "Removed downloads for ${summary.removed} wallpapers"
                            summary.skipped > 0 ->
                                "Selected wallpapers don't have local downloads"
                            else -> "No downloads were removed"
                        }
                    },
                    onFailure = { throwable ->
                        throwable.localizedMessage ?: "Unable to remove downloads"
                    }
                )
            }
        }
    }

    fun createAlbumAndAdd(title: String, wallpapers: List<WallpaperItem>) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            messageFlow.value = "Enter a name for your album"
            return
        }
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.ADD_TO_ALBUM
            selectionActionInProgress.value = true
            val result = runCatching {
                val album = repository.createAlbum(trimmed)
                val association = repository.addWallpapersToAlbum(album.id, wallpapers)
                album to association
            }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { (album, outcome) ->
                        when {
                            outcome.addedToAlbum > 0 -> "Added ${outcome.addedToAlbum} wallpapers to \"${album.title}\""
                            outcome.alreadyPresent > 0 -> "Wallpapers are already in \"${album.title}\""
                            outcome.skipped > 0 -> "Unable to add ${outcome.skipped} wallpapers to \"${album.title}\""
                            else -> "Updated \"${album.title}\""
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to create album" }
                )
            }
        }
    }

    fun selectCategory(categoryId: Long?) {
        selectedCategoryId.value = categoryId
    }

    fun createCategory(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) {
            messageFlow.value = "Enter a name for your category"
            return
        }
        viewModelScope.launch {
            val result = repository.createCategory(trimmed)
            messageFlow.update {
                result.fold(
                    onSuccess = { cat -> "Created category \"${cat.title}\"" },
                    onFailure = { t -> t.localizedMessage ?: "Unable to create category" }
                )
            }
        }
    }

    fun renameCategory(category: CategoryItem, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val result = repository.renameCategory(category.id, trimmed)
            messageFlow.update {
                result.fold(
                    onSuccess = { "Category renamed to \"$trimmed\"" },
                    onFailure = { t -> t.localizedMessage ?: "Unable to rename category" }
                )
            }
        }
    }

    fun deleteCategory(category: CategoryItem) {
        viewModelScope.launch {
            val result = repository.deleteCategory(category.id)
            if (selectedCategoryId.value == category.id) {
                selectedCategoryId.value = null
            }
            messageFlow.update {
                result.fold(
                    onSuccess = { "Deleted category \"${category.title}\"" },
                    onFailure = { t -> t.localizedMessage ?: "Unable to delete category" }
                )
            }
        }
    }

    fun reorderCategories(categoryIds: List<Long>) {
        viewModelScope.launch {
            repository.reorderCategories(categoryIds)
        }
    }

    fun addWallpapersToCategory(categoryId: Long, wallpapers: List<WallpaperItem>) {
        if (wallpapers.isEmpty() || selectionActionInProgress.value) return
        viewModelScope.launch {
            selectionAction.value = SelectionAction.ADD_TO_CATEGORY
            selectionActionInProgress.value = true
            val result = runCatching { repository.addWallpapersToCategory(categoryId, wallpapers) }
            selectionActionInProgress.value = false
            selectionAction.value = null
            messageFlow.update {
                result.fold(
                    onSuccess = { res ->
                        when {
                            res.addedToCategory > 0 -> "Added ${res.addedToCategory} wallpapers to category"
                            res.alreadyPresent > 0 -> "Wallpapers already in category"
                            else -> "No wallpapers added"
                        }
                    },
                    onFailure = { t -> t.localizedMessage ?: "Unable to add to category" }
                )
            }
        }
    }

    fun setWallpaperCategories(wallpaper: WallpaperItem, categoryIds: Set<Long>) {
        viewModelScope.launch {
            runCatching {
                repository.setWallpaperCategories(wallpaper, categoryIds)
            }.onSuccess {
                messageFlow.value = "Categories updated"
            }.onFailure { t ->
                messageFlow.value = t.localizedMessage ?: "Failed to update categories"
            }
        }
    }

    fun consumeMessage() {
        messageFlow.value = null
    }

    fun consumeDirectAddStatus() {
        directAddStatus.value = null
    }

    @androidx.compose.runtime.Immutable
    data class LibraryUiState(
        val wallpapers: List<WallpaperItem> = emptyList(),
        val allWallpapersCount: Int = 0,
        val albums: List<AlbumItem> = emptyList(),
        val categories: List<CategoryItem> = emptyList(),
        val selectedCategoryId: Long? = null,
        val categoriesEnabled: Boolean = true,
        val isCreatingAlbum: Boolean = false,
        val isSelectionActionInProgress: Boolean = false,
        val selectionAction: SelectionAction? = null,
        val message: String? = null,
        val wallpaperSortOption: WallpaperSortOption = WallpaperSortOption.RECENTLY_ADDED,
        val albumSortOption: AlbumSortOption = AlbumSortOption.TITLE_ASCENDING,
        val wallpaperGridColumns: Int = 2,
        val albumLayout: AlbumLayout = AlbumLayout.CARD_LIST,
        val wallpaperLayout: WallpaperLayout = WallpaperLayout.GRID,
        val isDirectAddInProgress: Boolean = false,
        val directAddCompleted: Boolean? = null,
        val downloadedFilter: DownloadedFilter = DownloadedFilter.SHOW_ALL,
        val favoritesOnly: Boolean = false,
        val isRefreshing: Boolean = false,
        val showDownloadBadge: Boolean = true
    )

    enum class SelectionAction {
        DOWNLOAD,
        REMOVE_FROM_LIBRARY,
        ADD_TO_ALBUM,
        ADD_TO_CATEGORY,
        REMOVE_DOWNLOADS,
        DELETE_ALBUMS
    }

}

private data class LibraryStateInputs(
    val wallpapers: List<WallpaperItem>,
    val albums: List<AlbumItem>,
    val categoriesWithWallpapers: List<CategoryWithWallpapers>,
    val selectedCategoryId: Long?,
    val isCreatingAlbum: Boolean,
    val isSelectionActionInProgress: Boolean,
    val selectionAction: LibraryViewModel.SelectionAction?,
    val message: String?,
    val wallpaperSortOption: WallpaperSortOption,
    val albumSortOption: AlbumSortOption,
    val preferences: SettingsPreferences,
    val isDirectAddInProgress: Boolean,
    val directAddCompleted: Boolean?,
    val downloadedFilter: DownloadedFilter,
    val favoritesOnly: Boolean,
    val isRefreshing: Boolean
)

private fun Array<Any?>.toLibraryStateInputs(): LibraryStateInputs {
    return LibraryStateInputs(
        wallpapers = this[0] as List<WallpaperItem>,
        albums = this[1] as List<AlbumItem>,
        categoriesWithWallpapers = this[2] as List<CategoryWithWallpapers>,
        selectedCategoryId = this[3] as Long?,
        isCreatingAlbum = this[4] as Boolean,
        isSelectionActionInProgress = this[5] as Boolean,
        selectionAction = this[6] as LibraryViewModel.SelectionAction?,
        message = this[7] as String?,
        wallpaperSortOption = this[8] as WallpaperSortOption,
        albumSortOption = this[9] as AlbumSortOption,
        preferences = this[10] as SettingsPreferences,
        isDirectAddInProgress = this[11] as Boolean,
        directAddCompleted = this[12] as Boolean?,
        downloadedFilter = this[13] as DownloadedFilter,
        favoritesOnly = this[14] as Boolean,
        isRefreshing = this[15] as Boolean
    )
}


