# FullPlayer Swipe-Up-Anywhere-to-Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Swiping up anywhere on FullPlayer's empty space opens the queue, follow-finger, instead of only working on the album-art thumbnail (release-only) or bouncing the background (everywhere else).

**Architecture:** Move the existing thumbnail-only swipe-up gesture from a dedicated `Spacer.pointerInput` to FullPlayer's root content `Box`. Same two-phase detect-then-claim shape (don't consume until an upward drag is confirmed past touch slop, so downward drags still bubble to the sheet's own collapse-by-drag). Replace the old release-only `onShowQueue()` call with two new live callbacks (`onQueueDragDelta`, `onQueueDragEnd`) wired in `LibraryScreen.kt` directly to `queueAnimatable.snapTo`/`animateTo`, mirroring `QueueSheet`'s own existing drag-to-dismiss wiring.

**Tech Stack:** Kotlin, Jetpack Compose (`pointerInput`, `awaitEachGesture`, `VelocityTracker`, `Animatable`).

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-05-fullplayer-swipe-up-queue-design.md`.
- `onShowQueue: () -> Unit` stays unchanged — still wired to the tap-triggered "UP NEXT" `TextButton` in `FullPlayer.kt:370`.
- No new gating on `expandedFraction`/`queueProg` — gesture only reachable while FullPlayer is laid out and receiving touches (sheet already expanded), same as today.
- Never read a composition snapshot inside a `pointerInput` lambda — use `rememberUpdatedState` for the two new callbacks (per this repo's hard-won rule in `CLAUDE.md` under Animation Pitfalls / stale lambda).
- No automated test exists (or is feasible) for Compose gesture feel — verification is `./gradlew assembleDebug` + `./gradlew lint` (compile/lint correctness) plus a manual on-device checklist, same precedent as the two prior swipe features.
- Branch: `feature/fullplayer-swipe-up-queue`, off `main`.

---

## Task 1: Create branch, move gesture to FullPlayer's root, wire follow-finger callbacks

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt:56-70` (signature), `:128-133` (root Box), `:202-249` (old gesture block, delete)
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt:107` (new tuning constant), `:651-658` (`onShowQueue` wiring site, add two new params)

**Interfaces:**
- Produces: `FullPlayer(..., onQueueDragDelta: (dy: Float) -> Unit = {}, onQueueDragEnd: (velocityY: Float) -> Unit = {})` — new params, called from `LibraryScreen.kt`'s `FullPlayer(...)` invocation inside `BottomSheetScaffold`'s `sheetContent`.
- Consumes (already in scope at the wiring site in `LibraryScreen.kt`, no new plumbing needed): `queueAnimatable: Animatable<Float, AnimationVector1D>` (declared line 239), `density` (line 284), `configuration` (line 286), `scope: CoroutineScope`, `QUEUE_ANIM_MS` (line 107).

- [ ] **Step 1: Create the feature branch**

```bash
git checkout -b feature/fullplayer-swipe-up-queue
```

- [ ] **Step 2: Add the new tuning constant in `LibraryScreen.kt`**

Open `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`. Immediately after line 107 (`private const val QUEUE_ANIM_MS = 300`), add:

```kotlin

// ── Swipe-up-anywhere-to-queue tuning ───────────────────────────────────────
// Fast upward flick commits the queue open even from a short drag, matching the
// flick-velocity precedent already used for dismiss/skip below. Same magnitude
// as DISMISS_FLICK_VELOCITY_DP — both represent "a fast flick", not a slow drag.
private const val QUEUE_OPEN_FLICK_VELOCITY_DP = 800
```

- [ ] **Step 3: Update `FullPlayer`'s signature in `FullPlayer.kt`**

Open `app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt`. Replace the parameter block (current lines 56-70):

```kotlin
@Composable
fun FullPlayer(
    viewModel: MainViewModel,
    expandedFraction: Float,
    onCollapse: () -> Unit,
    onTitlePositioned: (Float) -> Unit = {},
    /** Reports root-space top-left (x, y) of the artist text for the morphing overlay. */
    onArtistPositioned: (Float, Float) -> Unit = { _, _ -> },
    /** Reports root-space center (x, y) of Prev, Play, Next buttons for the morphing overlay. */
    onPlayControlsPositioned: (prevX: Float, prevY: Float, playX: Float, playY: Float, nextX: Float, nextY: Float) -> Unit = { _, _, _, _, _, _ -> },
    onAlbumArtPositioned: (x: Float, y: Float, sizePx: Float) -> Unit = { _, _, _ -> },
    onShowQueue: () -> Unit = {},
    /** Live delta (px) of an in-progress swipe-up-to-queue drag, follow-finger. */
    onQueueDragDelta: (dy: Float) -> Unit = {},
    /** Fired on release of a swipe-up-to-queue drag, with the final vertical velocity (px/s). */
    onQueueDragEnd: (velocityY: Float) -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShowMenu: () -> Unit = {},
) {
```

- [ ] **Step 4: Add the `VelocityTracker` import**

In the same file's import block (top of file, alongside the other `androidx.compose.ui.input.pointer.*` imports at lines 33-34), add:

```kotlin
import androidx.compose.ui.input.pointer.util.VelocityTracker
```

- [ ] **Step 5: Add `rememberUpdatedState` wrappers for the new callbacks**

Directly after the existing `val track = currentTrack!!` line (current line 83), add:

```kotlin
    val latestOnQueueDragDelta by rememberUpdatedState(onQueueDragDelta)
    val latestOnQueueDragEnd by rememberUpdatedState(onQueueDragEnd)
```

- [ ] **Step 6: Move the gesture from the thumbnail `Spacer` to the root `Box`**

Replace the root `Box` modifier chain (current lines 128-133):

```kotlin
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .background(animatedBg)
    ) {
```

with:

```kotlin
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .background(animatedBg)
            // Swipe-up-anywhere-to-queue, follow-finger. Root-level so it still receives
            // events under a button/TextButton — a real drag past touch slop cancels that
            // control's own press (Compose's normal tap-gesture-cancel-on-move), so plain
            // taps on controls are unaffected (they never cross slop, so this never claims
            // them), while a drag starting on a control still opens the queue.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    val touchSlop = viewConfiguration.touchSlop
                    var totalDy = 0f
                    var goesUp = false
                    var decided = false
                    // Phase 1 — detect direction WITHOUT consuming, so a downward drag stays
                    // fully available to the sheet's own collapse-by-drag underneath.
                    while (!decided) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture // tap, no drag
                        tracker.addPosition(change.uptimeMillis, change.position)
                        totalDy += change.positionChange().y
                        if (abs(totalDy) > touchSlop) {
                            decided = true
                            goesUp = totalDy < 0f
                        }
                    }
                    if (!goesUp) return@awaitEachGesture // downward → sheet collapses
                    // Phase 2 — claim the upward drag, follow the finger live.
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        latestOnQueueDragDelta(change.positionChange().y)
                        if (!change.pressed) break
                    }
                    latestOnQueueDragEnd(tracker.calculateVelocity().y)
                }
            }
    ) {
```

- [ ] **Step 7: Delete the old thumbnail-only gesture block**

In the album art `Spacer` (current lines 203-249), remove the `.pointerInput(Unit) { ... }` modifier entirely, leaving only the `.onGloballyPositioned { ... }` reporting call. Replace:

```kotlin
                Spacer(
                    modifier = Modifier
                        .size(artSizeDp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            onAlbumArtPositioned(pos.x, pos.y, coords.size.width.toFloat())
                        }
                        // A downward drag is intentionally NOT consumed, so it bubbles to the
                        // BottomSheetScaffold's own sheet drag — the player then collapses
                        // live and finger-tracked, exactly like dragging the background. Only
                        // an upward drag is claimed here, preserving the swipe-up-for-queue
                        // gesture (release-triggered, as before).
                        .pointerInput(Unit) {
                            val queueThresholdPx = 80.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val touchSlop = viewConfiguration.touchSlop
                                var totalDy = 0f
                                var goesUp = false
                                var decided = false
                                // Phase 1 — detect direction WITHOUT consuming, so a downward
                                // drag stays fully available to the sheet underneath.
                                while (!decided) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@awaitEachGesture
                                    if (!change.pressed) return@awaitEachGesture // tap, no drag
                                    totalDy += change.positionChange().y
                                    if (abs(totalDy) > touchSlop) {
                                        decided = true
                                        goesUp = totalDy < 0f
                                    }
                                }
                                if (!goesUp) return@awaitEachGesture // downward → sheet collapses
                                // Phase 2 — claim the upward drag for the queue gesture.
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    change.consume()
                                    totalDy += change.positionChange().y
                                    if (!change.pressed) break
                                }
                                if (totalDy < -queueThresholdPx) onShowQueue()
                            }
                        }
                )
```

with:

```kotlin
                Spacer(
                    modifier = Modifier
                        .size(artSizeDp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            onAlbumArtPositioned(pos.x, pos.y, coords.size.width.toFloat())
                        }
                )
```

- [ ] **Step 8: Wire the two new callbacks in `LibraryScreen.kt`**

In `LibraryScreen.kt`, find the `FullPlayer(...)` call (current lines 635-665) and change the `onShowQueue` block (current lines 651-658) from:

```kotlin
                            onShowQueue = {
                                scope.launch {
                                    queueAnimatable.animateTo(
                                        1f,
                                        tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing)
                                    )
                                }
                            },
```

to:

```kotlin
                            onShowQueue = {
                                scope.launch {
                                    queueAnimatable.animateTo(
                                        1f,
                                        tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing)
                                    )
                                }
                            },
                            onQueueDragDelta = { dy ->
                                val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
                                val newProg = (queueAnimatable.value - dy / screenH).coerceIn(0f, 1f)
                                scope.launch { queueAnimatable.snapTo(newProg) }
                            },
                            onQueueDragEnd = { velocityY ->
                                val flickVelocityPx = with(density) {
                                    QUEUE_OPEN_FLICK_VELOCITY_DP.dp.toPx()
                                }
                                scope.launch {
                                    if (queueAnimatable.value > 0.5f || velocityY < -flickVelocityPx) {
                                        queueAnimatable.animateTo(
                                            1f,
                                            tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing)
                                        )
                                    } else {
                                        queueAnimatable.animateTo(
                                            0f,
                                            tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            },
```

- [ ] **Step 9: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Lint**

```bash
./gradlew lint
```

Expected: no new warnings/errors introduced (baseline unchanged from `main`).

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/FullPlayer.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "$(cat <<'EOF'
feat: swipe-up-anywhere-to-queue on FullPlayer, follow-finger

Fixes the background-bounce bug where swiping up on empty space in
FullPlayer (outside the album-art thumbnail) fell through to
BottomSheetScaffold's own draggable and overscroll-bounced, since the
QueueMorphLayer overlay lives outside sheetContent and never reacted.

Moves the existing thumbnail-only swipe-up-to-queue gesture to
FullPlayer's root content Box (same detect-then-claim shape, still
never consumes downward drags) and replaces the old release-only
onShowQueue() trigger with live onQueueDragDelta/onQueueDragEnd
callbacks wired straight to queueAnimatable.snapTo/animateTo, mirroring
QueueSheet's own existing drag-to-dismiss wiring.
EOF
)"
```

---

## Task 2: Manual on-device verification checklist

No automated test can exercise Compose pointer-gesture feel; this task documents the manual pass required before merge (same blocker already noted for the two prior swipe features — down-to-remove and left/right-to-skip — both still pending device access as of this plan).

**Files:**
- None (verification only — no code changes in this task).

- [ ] **Step 1: Install the debug build on a device or emulator**

```bash
./gradlew installDebug
```

- [ ] **Step 2: Manual checklist — run through each and note pass/fail**

1. Open FullPlayer (tap the mini player). Swipe up slowly starting on **empty space below the album art** (not the thumbnail itself, not a button) — queue should follow the finger up smoothly, no background bounce.
2. Release the drag past halfway up — queue should finish opening (`animateTo(1f)`).
3. Release the drag before halfway, slowly (no flick) — queue should spring back closed (`animateTo(0f)`).
4. Quick upward flick from a short drag (well under halfway) — queue should still commit open (flick-velocity shortcut).
5. Swipe up starting **on the album art thumbnail** — same follow-finger behavior as step 1 (unified gesture, no behavior difference by position).
6. Swipe **down** on empty space — player should collapse live, finger-tracked, exactly as before this change (regression check).
7. Tap each control — shuffle, repeat, favorite, collapse arrow, overflow menu, "UP NEXT" text button — each should fire its own action normally, with no interference from the new gesture (regression check).
8. Press-and-drag-up starting directly on a button (e.g. the play button) — button's own press should cancel and the queue-open gesture should take over (per spec's documented edge case).
9. Horizontal drag on the seek bar — should seek normally, must not falsely trigger the queue gesture (per spec's documented edge case).

- [ ] **Step 3: Record result**

If all checks pass, note so in the PR description when opening it. If any check fails, file the specific failing behavior (which step, what happened vs. expected) before merging — do not merge with a known-failing check from this list.

---

## Self-Review Notes

- **Spec coverage:** Root-Box gesture move (spec §Approach) → Task 1 Steps 3-7. Live follow-finger `onQueueDragDelta`/`onQueueDragEnd` wiring (spec §LibraryScreen.kt wiring) → Task 1 Step 8. `onShowQueue` left untouched (spec §Out of scope) → Task 1 Step 8 shows it unchanged above the diff. Edge cases (spec §Edge cases) → Task 2 checklist steps 6-9. Manual-only verification (spec §Testing) → Task 2. Branch name (spec §Branch) → Task 1 Step 1.
- **Placeholder scan:** No TBD/TODO; every step has complete code or an exact command with expected output.
- **Type consistency:** `onQueueDragDelta: (dy: Float) -> Unit` / `onQueueDragEnd: (velocityY: Float) -> Unit` declared in Task 1 Step 3 match the call site in Task 1 Step 8 and the internal calls in Step 6 (`latestOnQueueDragDelta(...)`, `latestOnQueueDragEnd(...)`) exactly.
