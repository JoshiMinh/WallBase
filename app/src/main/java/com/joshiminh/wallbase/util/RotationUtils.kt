package com.joshiminh.wallbase.util

import androidx.compose.ui.graphics.vector.ImageVector
import com.joshiminh.wallbase.util.wallpapers.WallpaperTarget

data class RotationTargetOption(
    val target: WallpaperTarget,
    val label: String,
    val icon: ImageVector
)

fun formatIntervalText(value: Long, unit: RotationIntervalUnit): String {
    val unitLabel = when (unit) {
        RotationIntervalUnit.MINUTES -> if (value == 1L) "minute" else "minutes"
        RotationIntervalUnit.HOURS -> if (value == 1L) "hour" else "hours"
        RotationIntervalUnit.DAYS -> if (value == 1L) "day" else "days"
        RotationIntervalUnit.WEEKS -> if (value == 1L) "week" else "weeks"
    }
    return "$value $unitLabel"
}

enum class RotationIntervalUnit(val displayName: String, val minutesPerUnit: Long) {
    MINUTES("Minutes", 1),
    HOURS("Hours", 60),
    DAYS("Days", 1440),
    WEEKS("Weeks", 10080);

    fun toMinutes(value: Long): Long? {
        if (value <= 0) return null
        if (value > Long.MAX_VALUE / minutesPerUnit) return null
        return value * minutesPerUnit
    }

    fun valueFromMinutes(minutes: Long): Long {
        return when (this) {
            MINUTES -> minutes.coerceAtLeast(1)
            else -> ((minutes + minutesPerUnit - 1) / minutesPerUnit).coerceAtLeast(1)
        }
    }

    fun displayValue(minutes: Long): String = valueFromMinutes(minutes).toString()

    companion object {
        fun fromMinutes(minutes: Long): RotationIntervalUnit {
            return when {
                minutes % WEEKS.minutesPerUnit == 0L -> WEEKS
                minutes % DAYS.minutesPerUnit == 0L -> DAYS
                minutes % HOURS.minutesPerUnit == 0L -> HOURS
                else -> MINUTES
            }
        }
    }
}
