package com.joshiminh.wallbase.data.entity.source

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sources",
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["provider_key", "config"], unique = true)
    ]
)
data class SourceEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "source_id")
    val id: Long = 0,
    @ColumnInfo(name = "key")
    val key: String,
    @ColumnInfo(name = "provider_key")
    val providerKey: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "icon_res")
    val iconRes: Int?,
    @ColumnInfo(name = "icon_url")
    val iconUrl: String?,
    @ColumnInfo(name = "show_in_explore")
    val showInExplore: Boolean,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "is_local")
    val isLocal: Boolean,
    @ColumnInfo(name = "config")
    val config: String?
) {
    fun toDomain(): Source = Source(
        id = id,
        iconRes = iconRes?.takeIf { it != 0 },
        iconUrl = iconUrl?.takeUnless { it.isBlank() },
        title = title,
        description = description,
        showInExplore = showInExplore,
        enabled = isEnabled,
        key = key,
        providerKey = providerKey,
        isLocal = isLocal,
        config = config
    )

    companion object {
        fun fromSeed(seed: SourceSeed): SourceEntity = SourceEntity(
            key = seed.key,
            providerKey = seed.providerKey,
            title = seed.title,
            description = seed.description,
            iconRes = seed.iconRes,
            iconUrl = seed.iconUrl,
            showInExplore = seed.showInExplore,
            isEnabled = seed.enabledByDefault,
            isLocal = seed.isLocal,
            config = seed.config
        )
    }
}