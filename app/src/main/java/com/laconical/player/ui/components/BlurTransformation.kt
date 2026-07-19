package com.laconical.player.ui.components

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * Soft blur via downscale→upscale, safe on minSdk 26 (unlike [androidx.compose.ui.draw.blur],
 * which silently no-ops below API 31). Used for the lyrics view's blurred album-art background.
 *
 * Coil 3 has no built-in blur transformation (confirmed via context7 docs query) — this
 * hand-rolled version trades blur quality for being cheap and available across the whole
 * minSdk range: shrink to a fraction of the source size, then scale back up, letting bilinear
 * filtering do the smoothing. The upscale target is capped at [maxOutputPx] on the long edge
 * (not the source resolution) — a large embedded cover (e.g. 3000×3000) would otherwise
 * produce a full-resolution software bitmap for a background that only ever fills a phone
 * screen. Coil forces `allowHardware = false` whenever a transformation is present, so `input`
 * here is always a readable software bitmap, safe for `createScaledBitmap`.
 */
class DownscaleBlurTransformation(
    private val scaleFactor: Float = 0.015f,
    private val maxOutputPx: Int = 900,
) : Transformation() {

    override val cacheKey: String = "${DownscaleBlurTransformation::class.java.name}-$scaleFactor-$maxOutputPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val longEdge = maxOf(input.width, input.height).coerceAtLeast(1)
        val cap = (maxOutputPx.toFloat() / longEdge).coerceAtMost(1f)
        val outWidth = (input.width * cap).toInt().coerceAtLeast(1)
        val outHeight = (input.height * cap).toInt().coerceAtLeast(1)

        val smallWidth = (outWidth * scaleFactor).toInt().coerceAtLeast(1)
        val smallHeight = (outHeight * scaleFactor).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, smallWidth, smallHeight, true)
        val blurred = Bitmap.createScaledBitmap(small, outWidth, outHeight, true)
        if (small !== blurred) small.recycle()
        return blurred
    }
}
