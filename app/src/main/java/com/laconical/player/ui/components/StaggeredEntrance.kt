package com.laconical.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STAGGER_MS = 25L
private const val STAGGER_CAP = 8
private const val ANIM_DURATION_MS = 150
private const val SCROLL_IN_DURATION_MS = 90

/**
 * Fade + slide-up entrance animation staggered by [index].
 * Fires once on first composition. Uses graphicsLayer so layout is never disturbed.
 *
 * Two-speed mode:
 *   - Initial batch (index ≤ STAGGER_CAP): cascading delay + 150ms animation.
 *   - Scroll-in items (index > STAGGER_CAP): 0ms delay + 90ms animation.
 *
 * This prevents the old 540ms invisible-content window on fast scroll
 * (360ms max stagger delay + 180ms animation duration).
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val density = LocalDensity.current
    val startOffsetPx = with(density) { 16.dp.toPx() }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(startOffsetPx) }

    LaunchedEffect(index) {
        val isInitialBatch = index <= STAGGER_CAP
        val staggerDelay = if (isInitialBatch) index.toLong() * STAGGER_MS else 0L
        val duration = if (isInitialBatch) ANIM_DURATION_MS else SCROLL_IN_DURATION_MS
        if (staggerDelay > 0L) delay(staggerDelay)
        launch { alpha.animateTo(1f, tween(duration, easing = FastOutSlowInEasing)) }
        offsetY.animateTo(0f, tween(duration, easing = FastOutSlowInEasing))
    }

    graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}
