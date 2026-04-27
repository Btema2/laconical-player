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

private const val STAGGER_MS = 30L
private const val STAGGER_CAP = 12
private const val ANIM_DURATION_MS = 180

/**
 * Fade + slide-up entrance animation staggered by [index].
 * Fires once on first composition. Uses graphicsLayer so layout is never disturbed.
 * Stagger is capped at [STAGGER_CAP] items — items beyond that all animate at the same delay,
 * keeping the cascade snappy regardless of list length.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val density = LocalDensity.current
    val startOffsetPx = with(density) { 20.dp.toPx() }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(startOffsetPx) }

    LaunchedEffect(Unit) {
        val staggerDelay = minOf(index, STAGGER_CAP).toLong() * STAGGER_MS
        if (staggerDelay > 0L) delay(staggerDelay)
        launch { alpha.animateTo(1f, tween(ANIM_DURATION_MS, easing = FastOutSlowInEasing)) }
        offsetY.animateTo(0f, tween(ANIM_DURATION_MS, easing = FastOutSlowInEasing))
    }

    graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}
