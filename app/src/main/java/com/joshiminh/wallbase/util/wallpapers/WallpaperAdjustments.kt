package com.joshiminh.wallbase.util.wallpapers

import android.graphics.Bitmap
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.abs

/**
 * Encapsulates the user controlled edits applied to a wallpaper before it is
 * downloaded or set on the device.
 */
data class WallpaperAdjustments(
    val brightness: Float = 0f,
    val hue: Float = 0f,
    val filter: WallpaperFilter = WallpaperFilter.NONE,
    val crop: WallpaperCrop = WallpaperCrop.Auto,
) {
    val isIdentity: Boolean
        get() =
            brightness == 0f &&
                hue == 0f &&
                filter == WallpaperFilter.NONE &&
                crop == WallpaperCrop.Auto

    val cropSettings: WallpaperCropSettings?
        get() = (crop as? WallpaperCrop.Custom)?.settings

    fun sanitized(): WallpaperAdjustments {
        val clippedBrightness = brightness.coerceIn(-0.5f, 0.5f)
        val clippedHue = hue.coerceIn(-180f, 180f)
        val sanitizedCrop = when (val current = crop) {
            is WallpaperCrop.Custom -> WallpaperCrop.Custom(current.settings.sanitized())
            else -> current
        }
        return copy(
            brightness = clippedBrightness,
            hue = clippedHue,
            crop = sanitizedCrop
        )
    }

    fun normalizedCropSettings(): WallpaperCropSettings? =
        (crop as? WallpaperCrop.Custom)?.settings?.sanitized()
}

/**
 * Describes high level filter effects that can be composed through a
 * [android.graphics.ColorMatrix].
 */
enum class WallpaperFilter {
    NONE,
    GRAYSCALE,
    SEPIA,
}

sealed class WallpaperCrop {
    /**
     * Crop to the device's current screen aspect ratio. This mirrors the
     * previous behaviour of [WallpaperApplier] before editing was supported.
     */
    data object Auto : WallpaperCrop()

    /** Preserve the original bitmap dimensions. */
    data object Original : WallpaperCrop()

    /** Produce a centered square crop. */
    data object Square : WallpaperCrop()

    /** Use the caller provided [WallpaperCropSettings]. */
    data class Custom(val settings: WallpaperCropSettings) : WallpaperCrop()

    companion object {
        val presets: List<WallpaperCrop> = listOf(Auto, Original, Square)
    }
}

fun WallpaperCrop.displayName(): String = when (this) {
    WallpaperCrop.Auto -> "Auto"
    WallpaperCrop.Original -> "Original"
    WallpaperCrop.Square -> "Square"
    is WallpaperCrop.Custom -> "Custom"
}

@Parcelize
data class WallpaperCropSettings(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) : Parcelable {
    init {
        require(!left.isNaN() && !top.isNaN() && !right.isNaN() && !bottom.isNaN()) {
            "Crop settings must be finite numbers"
        }
    }

    fun widthFraction(): Float = (right - left).coerceAtLeast(0f)

    fun heightFraction(): Float = (bottom - top).coerceAtLeast(0f)

    fun aspectRatio(): Float {
        val width = widthFraction()
        val height = heightFraction()
        if (width <= 0f || height <= 0f) return 1f
        return width / height
    }

    fun sanitized(minSize: Float = MIN_FRACTION): WallpaperCropSettings {
        var l = left.coerceIn(0f, 1f)
        var r = right.coerceIn(0f, 1f)
        var t = top.coerceIn(0f, 1f)
        var b = bottom.coerceIn(0f, 1f)

        if (r < l) {
            val tmp = l
            l = r
            r = tmp
        }
        if (b < t) {
            val tmp = t
            t = b
            b = tmp
        }

        if (abs(r - l) < minSize) {
            val mid = (l + r) / 2f
            l = (mid - minSize / 2f).coerceAtLeast(0f)
            r = (mid + minSize / 2f).coerceAtMost(1f)
            if (r - l < minSize) {
                if (mid < 0.5f) {
                    r = (l + minSize).coerceAtMost(1f)
                } else {
                    l = (r - minSize).coerceAtLeast(0f)
                }
            }
        }

        if (abs(b - t) < minSize) {
            val mid = (t + b) / 2f
            t = (mid - minSize / 2f).coerceAtLeast(0f)
            b = (mid + minSize / 2f).coerceAtMost(1f)
            if (b - t < minSize) {
                if (mid < 0.5f) {
                    b = (t + minSize).coerceAtMost(1f)
                } else {
                    t = (b - minSize).coerceAtLeast(0f)
                }
            }
        }

        val width = (r - l).coerceAtLeast(minSize)
        val height = (b - t).coerceAtLeast(minSize)
        val clampedLeft = l.coerceIn(0f, 1f - width)
        val clampedTop = t.coerceIn(0f, 1f - height)
        return WallpaperCropSettings(
            left = clampedLeft,
            top = clampedTop,
            right = (clampedLeft + width).coerceAtMost(1f),
            bottom = (clampedTop + height).coerceAtMost(1f)
        )
    }

    fun offsetBy(deltaX: Float, deltaY: Float): WallpaperCropSettings {
        val width = widthFraction()
        val height = heightFraction()
        val newLeft = (left + deltaX).coerceIn(0f, 1f - width)
        val newTop = (top + deltaY).coerceIn(0f, 1f - height)
        return WallpaperCropSettings(newLeft, newTop, newLeft + width, newTop + height)
    }

    fun encodeToString(): String = listOf(left, top, right, bottom).joinToString(",") { value ->
        value.toString()
    }

    companion object {
        private const val MIN_FRACTION = 0.05f

        val Full: WallpaperCropSettings = WallpaperCropSettings()

        fun fromString(value: String?): WallpaperCropSettings? {
            if (value.isNullOrBlank()) return null
            val parts = value.split(',')
            if (parts.size != 4) return null
            return try {
                val left = parts[0].toFloat()
                val top = parts[1].toFloat()
                val right = parts[2].toFloat()
                val bottom = parts[3].toFloat()
                WallpaperCropSettings(left, top, right, bottom).sanitized()
            } catch (_: NumberFormatException) {
                null
            }
        }

        fun minFraction(): Float = MIN_FRACTION
    }
}

/**
 * Result returned by [WallpaperEditor] after applying [WallpaperAdjustments].
 */
data class EditedWallpaper(
    val bitmap: Bitmap,
)
