# FullPlayer Responsiveness — Design Spec

**Date:** 2026-06-17
**Scope:** Portrait smartphones, 9:16–9:19 aspect ratios. No tablet/landscape mode.

## Problem

FullPlayer was developed against one reference device. Three failure modes on other phones:

1. **Bad proportions** — hardcoded `Spacer(height = 64.dp)` above art and fixed `fillMaxWidth(0.95f).aspectRatio(1f)` for art cause overflow on short phones and excessive whitespace on tall ones.
2. **Weird control sizes** — play button (72dp) and skip icons (48dp) are fixed; look oversized on small art, undersized on large art.
3. **Morph misalignment** — `QueueMorphLayer` computes album art position as `statusBarPadding + 16.dp + 48.dp + 64.dp` (manual reconstruction). Any layout shift breaks the mini→full morph transition.

## Design

### 1. Adaptive art size via `BoxWithConstraints`

Wrap FullPlayer's content column in `BoxWithConstraints` applied after `statusBarsPadding()` and `padding(horizontal = 24.dp, vertical = 16.dp)`, so `maxWidth`/`maxHeight` reflect actual usable space.

Compute one shared `artSizeDp`:

```kotlin
val artSizeDp = minOf(maxWidth * 0.95f, maxHeight * 0.42f)
```

- `maxWidth * 0.95f` — current behaviour; wins on taller/wider phones.
- `maxHeight * 0.42f` — height cap; wins on short phones (360×640dp → ≈243dp art).
- Example values:
  - 360×640dp screen (24dp status bar): art = min(298, 243) = **243dp**
  - 360×800dp screen: art = min(298, 319) = **298dp** (width-limited)
  - 430×932dp screen: art = min(363, 369) = **363dp** (width-limited)

Replace `Modifier.fillMaxWidth(0.95f).aspectRatio(1f)` on the album art `Spacer` with `Modifier.size(artSizeDp)`.

Replace `Spacer(Modifier.height(64.dp))` above the art with `Spacer(Modifier.weight(0.08f))` — grows/shrinks with available height while keeping breathing room below the top bar.

The existing `Spacer(Modifier.weight(0.165f))` between art and track info is kept unchanged.

### 2. Proportional control sizes

Derive a `controlScale` from `artSizeDp`, clamped to prevent extreme changes:

```kotlin
val controlScale = (artSizeDp.value / 280f).coerceIn(0.85f, 1.15f)
val playButtonSize = 72.dp * controlScale   // range: ~61–83dp
val skipIconSize   = 48.dp * controlScale   // range: ~41–55dp
```

`PlaybackControls` receives `playButtonSize` and `skipIconSize` as parameters instead of hardcoded values. All other icon sizes (shuffle/repeat at 22dp, like at 28dp) remain unchanged.

### 3. Album art position callback (morph fix)

Add `onAlbumArtPositioned: (x: Float, y: Float, sizePx: Float) -> Unit` parameter to `FullPlayer`. The album art `Spacer` reports its measured root-space position via `onGloballyPositioned`, same pattern as title/artist/controls already use:

```kotlin
Spacer(
    modifier = Modifier
        .size(artSizeDp)
        .onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            onAlbumArtPositioned(pos.x, pos.y, with(density) { artSizeDp.toPx() })
        }
        .pointerInput(Unit) { /* existing drag gesture */ }
)
```

`LibraryScreen` stores three new state vars (`fullArtTopPx`, `fullArtLeftPx`, `fullArtSizePx`, all init to `-1f`) and passes them into `QueueMorphLayer`.

`QueueMorphLayer` replaces the hardcoded formula with measured values:

```kotlin
val fullArtSizeDp = if (fullArtSizePx >= 0f) with(density) { fullArtSizePx.toDp() }
                    else (screenWidthDp - 48.dp) * 0.95f          // first-frame fallback
val fullArtLeftDp = if (fullArtLeftPx >= 0f) with(density) { fullArtLeftPx.toDp() }
                    else (screenWidthDp - fullArtSizeDp) / 2f
val fullArtTopDp  = if (fullArtTopPx >= 0f) with(density) { (fullArtTopPx - sheetRootYPx).toDp() }
                    else statusBarPadding + 16.dp + 48.dp + 64.dp  // first-frame fallback
```

`allGhostsReady` gate in `LibraryScreen` extended to include `fullArtSizePx >= 0f`.

## Files Changed

| File | Change |
|------|--------|
| `FullPlayer.kt` | Wrap content in `BoxWithConstraints`; compute `artSizeDp`/`controlScale`; replace fixed spacer and art modifier; add `onAlbumArtPositioned` callback; pass size params to `PlaybackControls` |
| `LibraryScreen.kt` | Add `fullArtTopPx`/`fullArtLeftPx`/`fullArtSizePx` state; wire `onAlbumArtPositioned`; extend `allGhostsReady`; pass art state into `QueueMorphLayer` |
| `LibraryScreen.kt` (`QueueMorphLayer`) | Replace hardcoded art position formula with measured values + fallbacks |

## What Does Not Change

- Morph animation logic, spring/tween parameters, lerp math
- Title, artist, playback controls ghost/measurement system
- `QueueSheet`, `MiniPlayer`, all other screens
- Landscape/tablet handling (out of scope)

## Constraints

- Portrait-only phones, minSdk 26
- Compositor-only animations preserved
- Morph ghost contract preserved: album art `Spacer` remains the invisible layout anchor; overlay renders actual art
