# Pre-compose Queue Off the Morph Hot Path — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the FullPlayer→Queue morph lag by composing the queue's `LazyColumn` rows once on an idle frame after the FullPlayer settles, instead of on frame 1 of the open animation.

**Architecture:** Mount `QueueSheet` invisibly (alpha 0 via the existing `graphicsLayer`, non-interactive, parked on the current track) while the FullPlayer is fully open and idle. The morph then animates already-built nodes. The sheet unmounts when the FullPlayer collapses to mini. One-shot — no background loop, zero idle CPU.

**Tech Stack:** Kotlin, Jetpack Compose (M3), `Animatable`, `LazyColumn`, Coil 3.

## Global Constraints

- Modern Kotlin; no `!!`, prefer `val`, immutable patterns (project CLAUDE.md).
- Compositor-only animation properties (`alpha`, `translationY` via `graphicsLayer`) — never animate layout props.
- `scrollToItem` only (instant) — never `animateScrollToItem` (would traverse intermediate rows → Coil loads for every in-between song).
- Do NOT touch: mini→full morph, `MorphAnchors` capture, springs, or drag-to-reorder math.
- `!bottomSheet.isAnimationRunning` is load-bearing for any "settled" gate — never compose during the morph tail.
- No unit harness exists for the morph; automated gate per task = `./gradlew assembleDebug` + `./gradlew lint`. Behavioral gate = manual on-device (device/emulator).
- All changes confined to `app/.../ui/LibraryScreen.kt` and `app/.../ui/components/QueueSheet.kt`.

---

### Task 1: Gate QueueSheet interactivity on `progress` (touch pass-through)

When the sheet is mounted invisibly (Task 3), it must not intercept FullPlayer touches. Strip all pointer/click nodes when `progress <= 0.5f`. This task is behavior-preserving on its own (the sheet still only mounts when open today, and `progress` crosses 0.5 early in the open animation).

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/QueueSheet.kt`

**Interfaces:**
- Consumes: existing `QueueSheet(progress: Float, …)` param.
- Produces: `QueueTrackRow(… , interactive: Boolean, …)` — new boolean param threaded from `QueueSheet`.

- [ ] **Step 1: Derive `interactive` in `QueueSheet`**

In `QueueSheet`, just after the existing `val listState = rememberLazyListState()` (≈ line 100), add:

```kotlin
// Interactive only once the sheet is mostly open. While invisible (pre-warm) or in the
// first half of the open animation, every pointer node is stripped so touches pass through
// to the FullPlayer beneath (the morph layer sits above it). The QueueSheet root Box has no
// pointer node, so stripping the children below makes the whole subtree non-hit-testable.
val interactive = progress > 0.5f
```

- [ ] **Step 2: Gate the header swipe-down `pointerInput`**

Replace the header `Column`'s modifier (the `.pointerInput(Unit) { awaitEachGesture { … } }` block, ≈ lines 130-149) so the gesture is only attached when interactive:

```kotlin
Column(
    modifier = Modifier
        .then(
            if (interactive) Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            val velocity = tracker.calculateVelocity()
                            onDragEnd(velocity.y)
                            break
                        }
                        tracker.addPosition(change.uptimeMillis, change.position)
                        onDragDelta(change.positionChange().y)
                        change.consume()
                    } while (true)
                }
            } else Modifier
        )
) {
```

- [ ] **Step 3: Thread `interactive` into the `QueueTrackRow` call**

In the `itemsIndexed` block, add `interactive = interactive,` to the `QueueTrackRow(...)` call (e.g. right after `queueSize = queue.size,`):

```kotlin
QueueTrackRow(
    track = track,
    index = index,
    queueSize = queue.size,
    interactive = interactive,
    isCurrentTrack = isCurrentTrack,
    // … rest unchanged …
```

- [ ] **Step 4: Add the `interactive` parameter to `QueueTrackRow`**

In the `QueueTrackRow` signature (≈ line 275), add the param (place it after `queueSize: Int,`):

```kotlin
private fun QueueTrackRow(
    track: Track,
    index: Int,
    queueSize: Int,
    interactive: Boolean,
    isCurrentTrack: Boolean,
    // … rest unchanged …
```

- [ ] **Step 5: Gate the row `.clickable`**

In `QueueTrackRow`, replace the inner `Row`'s `.clickable(onClick = onTrackClick)` (≈ line 346) with a conditional:

```kotlin
.then(if (interactive) Modifier.clickable(onClick = onTrackClick) else Modifier)
```

- [ ] **Step 6: Gate the drag-handle `pointerInput`**

Replace the drag-handle `Box`'s `.pointerInput(track.id) { detectDragGesturesAfterLongPress(…) }` (≈ lines 419-426) with:

```kotlin
.then(
    if (interactive) Modifier.pointerInput(track.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { latestOnDragStart() },
            onDrag = { _, offset -> latestOnDragDelta(offset.y) },
            onDragEnd = { latestOnDragEnd() },
            onDragCancel = { latestOnDragCancel() }
        )
    } else Modifier
)
```

- [ ] **Step 7: Build + lint**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Run: `./gradlew lint`
Expected: `BUILD SUCCESSFUL` (no new errors).

- [ ] **Step 8: Manual sanity (no regression)**

Install (`./gradlew installDebug`), open a track → FullPlayer → swipe up to queue. Verify: queue opens, rows tap-to-seek, drag-handle reorder, and header swipe-down dismiss all still work when the queue is open (`progress` reaches 1 → interactive). No visible change yet vs. before.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/QueueSheet.kt
git commit -m "refactor: gate QueueSheet interactivity on progress for touch pass-through"
```

---

### Task 2: Park the list on the current track while the sheet is invisible

Pre-warm (Task 3) must compose the rows **around `currentIndex`** — the rows shown on open. Park the `LazyColumn` on the current track whenever the sheet is closed/invisible, so the open animation triggers zero scroll-jump composition.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/QueueSheet.kt`

**Interfaces:**
- Consumes: existing `listState`, `progress`, `currentIndex`, `queue`.
- Produces: no new symbols.

- [ ] **Step 1: Replace the scroll `LaunchedEffect`**

Replace the existing open-scroll effect (≈ lines 106-113, the `var wasQueueOpen …` + `LaunchedEffect(progress > 0.01f, currentIndex, queue.size) { … }`) with:

```kotlin
// While the sheet is closed/invisible (including the pre-warm mount), park the list on the
// current track so the rows composed ahead of time are exactly the ones shown on open — the
// open animation then triggers no scroll-jump composition. Re-parks on track change while
// invisible (user can't see the yank). Once open, stop auto-scrolling so browsing isn't yanked.
// scrollToItem (not animateScrollToItem) avoids composing every intermediate row.
var wasQueueOpen by remember { mutableStateOf(false) }
LaunchedEffect(progress > 0.01f, currentIndex, queue.size) {
    val isOpen = progress > 0.01f
    if (currentIndex >= 0 && queue.isNotEmpty()) {
        if (!isOpen) {
            listState.scrollToItem(currentIndex.coerceIn(0, queue.lastIndex))
        } else if (!wasQueueOpen) {
            listState.scrollToItem(currentIndex.coerceIn(0, queue.lastIndex))
        }
    }
    wasQueueOpen = isOpen
}
```

- [ ] **Step 2: Build + lint**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Run: `./gradlew lint`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual sanity**

Install, play a list, skip to a mid-list track, open queue → it still lands scrolled to the current track (unchanged behavior; pre-warm not wired yet so no perf change expected here).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/QueueSheet.kt
git commit -m "refactor: park queue list on current track while invisible"
```

---

### Task 3: Pre-warm trigger + mount gate (wire it up)

Mount `QueueSheet` invisibly once the FullPlayer is fully open and idle. This is the integration point where the lag fix becomes observable.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

**Interfaces:**
- Consumes: existing `expandedFraction: Float`, `bottomSheet` (the `bottomSheetState`), `queueAnimatable`, and `QueueMorphLayer(...)`.
- Produces: `QueueMorphLayer(… , prewarm: Boolean, …)` — new boolean param.

- [ ] **Step 1: Add the pre-warm trigger in `LibraryScreen`**

Immediately after the existing auto-collapse effect (the `LaunchedEffect(expandedFraction) { if (expandedFraction < 0.3f && queueAnimatable.value > 0f) … }`, ≈ lines 410-414), add:

```kotlin
// Pre-compose the queue list while the full player sits fully open and idle, so opening the
// queue animates already-built rows instead of composing a screenful on frame 1. One-shot:
// flips true once per settle and never polls, so an idle full player burns no CPU.
// `!isAnimationRunning` is load-bearing — composing during the mini→full morph tail would land
// the row-composition spike on the very animation we keep smooth. withFrameNanos defers one
// frame past the settle frame so the compose lands on a clean idle frame.
var queuePrewarm by remember { mutableStateOf(false) }
val fullyOpenIdle = expandedFraction >= 0.99f && !bottomSheet.isAnimationRunning
LaunchedEffect(fullyOpenIdle) {
    if (fullyOpenIdle) {
        withFrameNanos {}
        queuePrewarm = true
    } else {
        queuePrewarm = false
    }
}
```

- [ ] **Step 2: Pass `prewarm` into the `QueueMorphLayer` call**

In the `QueueMorphLayer(...)` call (≈ lines 868-888), add `prewarm = queuePrewarm,` (e.g. right after `queueAnimatable = queueAnimatable,`):

```kotlin
QueueMorphLayer(
    queueAnimatable = queueAnimatable,
    prewarm = queuePrewarm,
    viewModel = viewModel,
    // … rest unchanged …
```

- [ ] **Step 3: Add the `prewarm` parameter to `QueueMorphLayer`**

In the `QueueMorphLayer` signature (≈ line 1094), add the param after `queueAnimatable`:

```kotlin
private fun QueueMorphLayer(
    queueAnimatable: Animatable<Float, AnimationVector1D>,
    prewarm: Boolean,
    viewModel: MainViewModel,
    // … rest unchanged …
```

- [ ] **Step 4: Widen the mount gate**

In `QueueMorphLayer`, change the sheet mount condition (≈ line 1131) from `if (queueProg > 0.001f) {` to:

```kotlin
if (queueProg > 0.001f || prewarm) {
```

(The body — `QueueSheet(progress = queueProg, … modifier = Modifier.graphicsLayer { translationY = (1f - queueProg) * slideDistance; alpha = queueProg.coerceIn(0f, 1f) })` — is unchanged; at `queueProg == 0` the existing `alpha = queueProg` already renders it invisible.)

- [ ] **Step 5: Confirm imports**

Ensure `androidx.compose.runtime.withFrameNanos` is imported (LibraryScreen already uses `androidx.compose.runtime.*` per its import block — verify the wildcard or add the explicit import if the file uses explicit runtime imports).

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. If unresolved `withFrameNanos`, add `import androidx.compose.runtime.withFrameNanos` and rebuild.

- [ ] **Step 6: Lint**

Run: `./gradlew lint`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Manual verification (the acceptance gate)**

Install (`./gradlew installDebug`). Verify each:

1. **Primary fix:** Open a track → FullPlayer → swipe up to queue. The open is smooth (no frame-1 hitch) on the **first** open.
2. **Touch pass-through:** With FullPlayer open (queue closed) and idle, drag the seek bar and tap transport buttons — all respond (the invisible mounted sheet does not block input).
3. **No morph regression:** mini→full expand is unchanged — no stutter at the end of expand.
4. **Re-warm:** collapse to mini, re-open FullPlayer, open queue again → still smooth.
5. **Parked position:** skip to a mid-list track before opening → queue opens already scrolled to it, no jump.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "perf: pre-compose queue off the full→queue morph hot path"
```

---

### Task 4: Fold in the in-flight QueueSheet image change + final review

The working tree already has an uncommitted `SubcomposeAsyncImage`→`AsyncImage` change in `QueueSheet.kt` (reduces per-row subcomposition). It is complementary; commit it so the branch is clean, then do a final pass.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/QueueSheet.kt` (already-staged-in-tree diff)

- [ ] **Step 1: Confirm the change is the expected one**

Run: `git diff app/src/main/java/com/laconical/player/ui/components/QueueSheet.kt`
Expected: shows `SubcomposeAsyncImage` → `AsyncImage`, the `error = { … }` block removed, and a `MusicNote` `Icon` moved to sit behind the `AsyncImage` (plus the import swap).

- [ ] **Step 2: Build + lint with everything together**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Run: `./gradlew lint`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit the image change**

```bash
git add app/src/main/java/com/laconical/player/ui/components/QueueSheet.kt
git commit -m "perf: use AsyncImage in queue rows to drop per-row subcomposition"
```

- [ ] **Step 4: Final on-device regression sweep**

Re-run the five checks from Task 3 Step 7 once more on the fully-integrated branch. All must hold. If the single idle compose frame visibly hitches on low-end hardware, open a follow-up for Approach C (trim per-row cost: drop per-row `animateFloatAsState`, lazy `pointerInput`) — out of scope for this plan.

---

## Self-Review

**Spec coverage:**
- Pre-warm trigger (`queuePrewarm`, `fullyOpenIdle`, `withFrameNanos`) → Task 3 Step 1. ✓
- Mount gate `|| prewarm` + param → Task 3 Steps 2-4. ✓
- Touch pass-through (`interactive = progress > 0.5f`, strip clickable/pointerInputs) → Task 1. ✓
- Park list on current track while invisible → Task 2. ✓
- Non-goal (no Coil pre-warm) → respected (no such task). ✓
- Complementary AsyncImage change → Task 4. ✓
- Verification checklist (5 manual checks + build/lint) → Task 3 Step 7, Task 4 Step 4. ✓

**Placeholder scan:** No TBD/TODO; every code step shows full code. Approach-C fallback is explicitly out of scope, not a placeholder. ✓

**Type consistency:** `interactive: Boolean` defined in `QueueTrackRow` (Task 1 Step 4) and passed in Task 1 Step 3 — names match. `prewarm: Boolean` defined in `QueueMorphLayer` (Task 3 Step 3) and passed in Task 3 Step 2 — match. `queuePrewarm` / `fullyOpenIdle` used only within `LibraryScreen` scope. `bottomSheet` matches the existing variable name in `LibraryScreen`. ✓
