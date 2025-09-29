package com.joshiminh.wallbase.util.wallpapers

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Serialises [WallpaperAdjustments] to and from a JSON representation so edit
 * settings can be stored in the database.
 */
object WallpaperAdjustmentsJson {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter: JsonAdapter<StoredAdjustments> =
        moshi.adapter(StoredAdjustments::class.java)

    fun encode(adjustments: WallpaperAdjustments?): String? {
        val normalized = adjustments?.sanitized() ?: return null
        val storedCrop = when (val crop = normalized.crop) {
            WallpaperCrop.Auto -> StoredCrop(mode = "auto", settings = null)
            WallpaperCrop.Original -> StoredCrop(mode = "original", settings = null)
            WallpaperCrop.Square -> StoredCrop(mode = "square", settings = null)
            is WallpaperCrop.Custom -> StoredCrop(
                mode = "custom",
                settings = StoredCropSettings(
                    left = crop.settings.left,
                    top = crop.settings.top,
                    right = crop.settings.right,
                    bottom = crop.settings.bottom
                )
            )
        }

        val stored = StoredAdjustments(
            brightness = normalized.brightness,
            filter = normalized.filter.name,
            crop = storedCrop
        )

        return adapter.nullSafe().toJson(stored)
    }

    fun decode(value: String?): WallpaperAdjustments? {
        if (value.isNullOrBlank()) return null
        val stored = runCatching { adapter.fromJson(value) }.getOrNull() ?: return null
        val brightness = stored.brightness?.coerceIn(-0.5f, 0.5f) ?: 0f
        val filter = stored.filter?.let { name ->
            runCatching { WallpaperFilter.valueOf(name) }.getOrNull()
        } ?: WallpaperFilter.NONE

        val crop = when (stored.crop?.mode?.lowercase()) {
            "original" -> WallpaperCrop.Original
            "square" -> WallpaperCrop.Square
            "custom" -> {
                val settings = stored.crop.settings
                if (settings != null) {
                    WallpaperCrop.Custom(
                        WallpaperCropSettings(
                            left = settings.left,
                            top = settings.top,
                            right = settings.right,
                            bottom = settings.bottom
                        ).sanitized()
                    )
                } else {
                    WallpaperCrop.Auto
                }
            }
            "auto" -> WallpaperCrop.Auto
            else -> WallpaperCrop.Auto
        }

        return WallpaperAdjustments(
            brightness = brightness,
            filter = filter,
            crop = crop
        ).sanitized()
    }

    private data class StoredAdjustments(
        val brightness: Float?,
        val filter: String?,
        val crop: StoredCrop?
    )

    private data class StoredCrop(
        val mode: String?,
        val settings: StoredCropSettings?
    )

    private data class StoredCropSettings(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}
