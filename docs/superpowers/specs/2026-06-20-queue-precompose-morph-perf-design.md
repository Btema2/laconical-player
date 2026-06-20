# Pre-compose Queue Off the Full→Queue Morph Hot Path

**Date:** 2026-06-20
**Status:** Approved (design)
**Scope:** `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`, `app/.../ui/components/QueueSheet.kt`

## Problem

The FullPlayer → Queue morph (`queueAnimatable` driving 0→1) lags. Diagnosis ruled out
both prior suspects:

- **Not subcomposition** — already mitigated (`SubcomposeAsyncImage` → `AsyncImage`,
  uncommitted change in `QueueSheet.kt`). Lag persisted after this change.
- **Not image decode** — a second open with album art already in Coil memory cache shows
  only a near-unnoticeable improvement; it still lags.

The dominant cost is **Compose composition**. `QueueSheet` is gated
`if (queueProg > 0.001f)`, so it **unmounts on close and recomposes from scratch on every
open**. On frame 1 of the morph, the `LazyColumn` composes a screenful (~8) of
`QueueTrackRow`s. Each row builds: a Coil `ImageRequest` (in `remember`), two `pointerInput`
blocks (row drag-handle + long-press reorder), a `graphicsLayer` drag lambda, and a per-row
`animateFloatAsState` (drag-scale spring). ~8 of these composing on one animation frame is the
spike.

## Goal

Move the row-composition cost **off the animation hot path** onto an idle frame after the
FullPlayer has settled, so the morph animates only already-built nodes (alpha + translation).
Constraint: **no continuous background work** — the FullPlayer must not lag while idle, so the
pre-warm is a one-shot, not a polling loop.

## Non-goals

- No Coil cache pre-warming (decode is not the bottleneck; would add complexity for the
  "near-unnoticeable" gain already observed).
- No change to the mini→full morph, anchor capture, springs, or drag-to-reorder behavior.
- No attempt to spread composition across multiple frames (the single idle frame is invisible
  because nothing else animates; progressive composition is unnecessary complexity / YAGNI).

## Approach (selected: A — pre-compose on full-player settle)

Mount `QueueSheet` invisibly (alpha 0, non-interactive, parked on the current track) once the
FullPlayer is fully open and idle. Composition happens on that idle frame. Keep it mounted
while the FullPlayer is open; unmount when the FullPlayer collapses to mini (low-resource).

Rejected alternatives:

- **B — stop unmounting on close only.** Minimal, but the first-ever open still composes during
  the animation → first open still lags. Weaker.
- **C — trim per-row composition cost** (drop per-row `animateFloatAsState`, lazy `pointerInput`,
  lighter row). Invasive, risks drag-reorder, uncertain payoff. **Held in reserve** as a fallback
  if A's single idle compose frame still hitches on low-end hardware.

## Component changes

### 1. Pre-warm trigger — `LibraryScreen.kt`

Add near the `queueAnimatable` / `expandedFraction` declarations:

```kotlin
// Pre-compose the queue list while the full player sits idle, so opening the queue
// animates already-built rows instead of composing a screenful on frame 1. One-shot:
// no loop, zero idle CPU once latched.
var queuePrewarm by remember { mutableStateOf(false) }
val fullyOpenIdle = expandedFraction >= 0.99f && !bottomSheet.isAnimationRunning
LaunchedEffect(fullyOpenIdle) {
    if (fullyOpenIdle) {
        withFrameNanos {}      // one idle frame past the settle frame, then pre-compose
        queuePrewarm = true
    } else {
        queuePrewarm = false   // collapse to mini → unmount, free the rows
    }
}
```

`!bottomSheet.isAnimationRunning` is **load-bearing** (same rule as `anchorsAtRest`): never
compose during the mini→full morph tail, or the row-composition spike lands on the morph we are
trying to keep smooth and reintroduces a tail stutter.

### 2. Mount gate — `QueueMorphLayer` (in `LibraryScreen.kt`)

- Add a `prewarm: Boolean` parameter.
- Pass `prewarm = queuePrewarm` from the `QueueMorphLayer(...)` call site.
- Change the sheet gate:

```kotlin
if (queueProg > 0.001f || prewarm) {
    QueueSheet( progress = queueProg, /* …unchanged… */ )
}
```

The existing `Modifier.graphicsLayer { alpha = queueProg; translationY = (1f - queueProg) * slideDistance }`
already renders the sheet invisible at `queueProg == 0` — no extra hiding logic needed.

### 3. Touch pass-through when invisible — `QueueSheet.kt`

The morph layer sits **above** FullPlayer in the outer Box. A mounted full-screen `QueueSheet`
must not intercept FullPlayer touches (seek bar, transport buttons) while invisible.

- Derive `val interactive = progress > 0.5f` (no new public param required; `progress` already
  flows in). Thread it into `QueueTrackRow`.
- When `!interactive`, omit:
  - row `.clickable(onClick = onTrackClick)`
  - the drag-handle `pointerInput` (long-press reorder)
  - the header swipe-down-to-dismiss `pointerInput`
- The `QueueSheet` root `Box` has `.background(...)` but **no** pointer node, so with the
  interactive nodes stripped the whole subtree is non-hit-testable → touches pass through to
  FullPlayer beneath.

When the queue actually opens, `progress` crosses 0.5 and interactivity is restored before the
sheet is fully visible. (The morph overlay, not the ghost header, already owns header taps during
the transition, so stripping header interaction below 0.5 changes nothing visible.)

### 4. Park list on current track while invisible — `QueueSheet.kt` scroll effect

Pre-warm must compose the rows **around `currentIndex`** (the rows shown on open), not index 0.
Otherwise `scrollToItem(currentIndex)` on open composes fresh rows and the spike returns.

```kotlin
var wasQueueOpen by remember { mutableStateOf(false) }
LaunchedEffect(progress > 0.01f, currentIndex, queue.size) {
    val isOpen = progress > 0.01f
    if (currentIndex >= 0 && queue.isNotEmpty()) {
        if (!isOpen) {
            // Pre-warm/closed & invisible: park the list on the current track so the rows
            // composed ahead of time are exactly the ones shown on open — no scroll-jump
            // composition during the animation. Re-parks on track change (invisible → no yank).
            listState.scrollToItem(currentIndex.coerceIn(0, queue.lastIndex))
        } else if (!wasQueueOpen) {
            // Redundant safety once parked; preserves prior open-time behavior.
            listState.scrollToItem(currentIndex.coerceIn(0, queue.lastIndex))
        }
    }
    wasQueueOpen = isOpen
}
```

`scrollToItem` (instant, not `animateScrollToItem`) — preserves the existing rule of never
traversing intermediate items (which would trigger Coil loads for every song in between).

## Data flow

```
FullPlayer settles (expandedFraction ≥ 0.99 && !isAnimationRunning)
  → queuePrewarm = true
  → QueueSheet mounts: alpha 0, non-interactive (progress < 0.5), parked on currentIndex
  → ~8 rows compose on ONE idle frame (FullPlayer idle → frame drop invisible)
User flicks to queue
  → queueProg 0→1: animates alpha + translation of already-built nodes
  → progress > 0.5 → interactive
  → smooth (zero new composition on the hot path)
FullPlayer collapses to mini
  → fullyOpenIdle = false → queuePrewarm = false → QueueSheet unmounts → rows freed
```

## Edge cases

| Case | Handling |
|------|----------|
| Queue empty / `currentIndex == -1` | Scroll effect guarded; no mount work of value. |
| Track auto-advances while FullPlayer open | Scroll effect re-fires on `currentIndex`, re-parks invisibly (no visible yank). |
| User flicks to queue before pre-warm fires | Falls back to current behavior (composes during anim). Best-effort; one frame. |
| Collapse directly from queue-open | Both `queueProg` and `expandedFraction` settle; sheet stays mounted until `queueProg ≈ 0`, then unmounts. |
| Config change (rotation) | `queuePrewarm` re-derives once the sheet resettles; no special handling. |

## Verification

Manual on-device (consistent with how prior morph work was validated — Compose animation timing
is not meaningfully unit-testable here):

1. **Primary:** cold first open of the queue is smooth (the reported bug).
2. **Pass-through:** while FullPlayer is open and idle, the seek bar and transport buttons still
   respond (the mounted invisible sheet does not block input).
3. **No regression:** the mini→full morph is unchanged — no tail stutter at the end of expand.
4. **Re-warm:** collapse to mini, re-open FullPlayer, open queue again → still smooth.
5. **Low-resource:** FullPlayer sitting idle shows no sustained CPU from the queue (one-shot,
   not a loop).

Build gates: `./gradlew assembleDebug` and `./gradlew lint`.

**Fallback:** if the single idle compose frame visibly hitches on low-end hardware, apply
Approach C (trim per-row cost) on top of this change.
