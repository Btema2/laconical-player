package com.laconical.player.ui

import androidx.compose.ui.graphics.Color

/**
 * Converts a [Color] to an HSL float array.
 * Returns [hue ∈ 0..1, saturation ∈ 0..1, lightness ∈ 0..1].
 *
 * Single source of truth — used by both LibraryScreen and FullPlayer.
 */
internal fun Color.toHsl(): FloatArray {
    val hsl = FloatArray(3)
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    hsl[2] = (max + min) / 2
    if (max == min) {
        hsl[0] = 0f; hsl[1] = 0f
    } else {
        val d = max - min
        hsl[1] = if (hsl[2] > 0.5f) d / (2f - max - min) else d / (max + min)
        when (max) {
            r -> hsl[0] = (g - b) / d + (if (g < b) 6f else 0f)
            g -> hsl[0] = (b - r) / d + 2f
            b -> hsl[0] = (r - g) / d + 4f
        }
        hsl[0] /= 6f
    }
    return hsl
}
