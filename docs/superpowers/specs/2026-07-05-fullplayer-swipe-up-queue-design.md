# FullPlayer swipe-up-anywhere-to-queue, follow-finger

## Problem

Swiping up on the album-art thumbnail in FullPlayer already opens the queue, but it's release-triggered only (accumulate `totalDy`, fire a fixed 300ms `tween` on release — doesn't track the finger). Swiping up on any *other* empty space in FullPlayer does something worse: no gesture claims it, so the drag falls through to `BottomSheetScaffold`'s own draggable. The sheet is already fully expanded, so it overscroll-bounces and springs back. Only the `sheetContent` (FullPlayer's own background/layout) visibly bounces — the `QueueMorphLayer` overlay (album art, title, controls) lives outside `sheetContent` as a sibling in the outer `Box`, driven only by `expandedFraction`/`queueProg`, so it stays completely still during the bounce. Visually: background jumps, morph elements don't move, then it snaps back.

## Goal

Swiping up anywhere on FullPlayer's empty space opens the queue, same as the thumbnail does today, but follow-finger (live-tracked), replacing the old release-only thumbnail gesture with one unified gesture across the whole player.

## Approach

Move the gesture from the thumbnail-only `Spacer` to a `pointerInput` on FullPlayer's root content `Box` (the one wrapping `ParticleSystem` + `BoxWithConstraints`). Keep the existing two-phase detect-then-claim shape (already proven, coexists safely with the sheet's drag and with sibling buttons):

- **Phase 1** — accumulate `totalDy` **without consuming**, so downward drags still bubble to the sheet's own collapse-by-drag (unaffected by this change).
- **Decided upward** → **Phase 2** claims the gesture: `change.consume()` every move, feed live delta up every frame (follow-finger), track velocity with `VelocityTracker` (already used elsewhere in `LibraryScreen.kt`).
- **Release** → fire end callback with final velocity.

Sitting at the root means it still receives events under a button/TextButton — a real drag past touch slop cancels that control's own press (standard Compose tap-gesture-cancel-on-move), so a drag starting on a control still opens the queue; plain taps on controls are unaffected (taps never cross slop, so this gesture never claims them).

### FullPlayer.kt signature change

```kotlin
onShowQueue: () -> Unit = {},              // unchanged — tap-triggered, wired to "UP NEXT" TextButton
onQueueDragDelta: (dy: Float) -> Unit = {},
onQueueDragEnd: (velocityY: Float) -> Unit = {},
```

Old Spacer-only `pointerInput` block (queueThresholdPx / release-only `onShowQueue()`) is deleted — fully superseded by the root-level gesture.

### LibraryScreen.kt wiring

```kotlin
onQueueDragDelta = { dy ->
    val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
    val newProg = (queueAnimatable.value - dy / screenH).coerceIn(0f, 1f)
    scope.launch { queueAnimatable.snapTo(newProg) }
},
onQueueDragEnd = { velocityY ->
    scope.launch {
        if (queueAnimatable.value > 0.5f || velocityY < -QUEUE_OPEN_FLICK_VELOCITY_PX) {
            queueAnimatable.animateTo(1f, tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing))
        } else {
            queueAnimatable.animateTo(0f, tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing))
        }
    }
},
```

- `dy / screenH` normalization matches `QueueSheet`'s own `onDragDelta` (line ~1512).
- `value > 0.5f` threshold matches `QueueSheet`'s existing dismiss-commit logic.
- New addition: a fast upward flick (`velocityY` very negative) commits open even from a short drag — matches flick-detection precedent already used elsewhere in this file (miniplayer skip/dismiss). `QUEUE_OPEN_FLICK_VELOCITY_PX` is a new tuning constant, same order of magnitude as existing flick-velocity constants.
- No extra gating (`expandedFraction`/`queueProg` checks) needed — the gesture is only reachable while FullPlayer is actually laid out and receiving touches (sheet already expanded), same as today.

## Edge cases

- Downward drag on empty space: unaffected, still un-consumed, still bubbles to sheet-collapse.
- Tap on any control (shuffle/repeat/favorite/menu/collapse/UP NEXT): unaffected.
- Drag starting on a control, moving up past slop: control's press cancels, queue-open gesture claims. Acceptable/desirable.
- Seek bar: `VisualizerSeekBar` uses `detectDragGestures` (omnidirectional, not horizontal-locked), so a seek drag with vertical wobble past touch-slop could race with the root gesture. Guarded by an `event.changes.any { it.isConsumed }` bail-out in Phase 1 — if the seek bar's detector consumed first, the root gesture exits immediately. No automated test for this; verify manually on device.

## Testing

No unit-test coverage possible for gesture feel (Compose gesture testing needs instrumented tests; none exist for the current thumbnail gesture either). Verification is manual on-device: gesture claim/no-claim boundaries above, follow-finger tracking smoothness, release-threshold feel, flick-open feel. Same on-device-testing blocker already noted for the two prior swipe features (down-to-remove, left/right-to-skip) — still pending a device/emulator.

Build (`assembleDebug`), lint, and existing tests must still pass — no test changes expected since no new testable logic (pure gesture/animation wiring).

## Out of scope

- No changes to `onShowQueue`/"UP NEXT" tap button.
- No changes to `QueueSheet`'s own drag-to-dismiss gesture.
- No axis-locking needed (no competing horizontal gesture at FullPlayer-background level).

## Branch

`feature/fullplayer-swipe-up-queue`, off `main`.
