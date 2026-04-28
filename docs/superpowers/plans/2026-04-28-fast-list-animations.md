# Fast List Animations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the 360ms invisible-content delay for scroll-in items by giving them a zero-delay 90ms animation, while keeping the initial cascade for the first 8 items.

**Architecture:** All logic lives in `StaggeredEntrance.kt`. A single boolean (`isInitialBatch = index <= STAGGER_CAP`) gates which timing constants are used. No call-site changes — all four screens (Tracks, Albums, Artists, Playlists) benefit automatically.

**Tech Stack:** Kotlin · Jetpack Compose · `androidx.compose.animation.core` (Animatable, tween, FastOutSlowInEasing)

---

### Task 1: Update StaggeredEntrance constants and two-speed logic

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/StaggeredEntrance.kt`

- [ ] **Step 1: Open the file and read current state**

Confirm current content matches:
```kotlin
private const val STAGGER_MS = 30L
private const val STAGGER_CAP = 12
private const val ANIM_DURATION_MS = 180
```

- [ ] **Step 2: Replace the entire file with the updated implementation**

```kotlin
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
 * This prevents the old 360ms invisible-content window on fast scroll.
 */
fun Modifier.staggeredEntrance(index: Int): Modifier = composed {
    val density = LocalDensity.current
    val startOffsetPx = with(density) { 16.dp.toPx() }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(startOffsetPx) }

    LaunchedEffect(Unit) {
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
```

- [ ] **Step 3: Build to confirm no compilation errors**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Manually verify on device / emulator**

Install debug build and test both behaviours:

```bash
./gradlew installDebug
```

Checklist:
1. Open **Tracks** tab → items 0–8 cascade in with a visible waterfall (completes ~350ms). ✓
2. Scroll **fast** to the bottom → newly appearing items snap in within ~90ms, no empty space visible for more than a blink. ✓
3. Repeat checks on **Albums**, **Artists**, **Playlists** tabs. ✓

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/StaggeredEntrance.kt
git commit -m "perf: speed up scroll-in list items — 90ms snap vs 540ms delay"
```
