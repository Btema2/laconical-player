# FullPlayer Polish — Design Spec

**Date:** 2026-06-09  
**Status:** Approved

---

## Overview

Polish the FullPlayer and its supporting morph overlay with four focused improvements:

1. Fade + marquee scrolling for overflowing track/artist text in all three player states
2. Wire the 3-dots button to open `TrackMenuOverlay` without the art morph animation
3. Wire the like button to `viewModel.toggleFavorite` with a spring pop animation
4. Fade the album name label in the FullPlayer header

---

## 1. Fade + Marquee Text

### Composable: `FadingMarqueeText`

**Location:** `app/.../ui/components/FadingMarqueeText.kt`

A single reusable composable wrapping `Text` with two behaviors:

**Fade (always active):**  
`Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }` +  
`drawWithContent { drawContent(); drawRect(Brush.horizontalGradient(0.7f→Black, 1.0f→Transparent), blendMode = BlendMode.DstIn) }`  
This is a pure alpha mask — works on any background, no artifact on short text.

**Marquee (conditional):**  
`Modifier.basicMarquee(animationMode = MarqueeAnimationMode.Immediately, initialDelayMillis = 3000, repeatDelayMillis = 2500, velocity = 80.dp)` applied only when `isScrolling = true`. Text uses `softWrap = false`, `overflow = TextOverflow.Clip`.

**Signature:**
```kotlin
@Composable
fun FadingMarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    isScrolling: Boolean,
    modifier: Modifier = Modifier,
)
```

### Usage in `QueueMorphLayer`

Replace both `Text(currentTrack.title, ...)` and `Text(currentTrack.artist, ...)` with `FadingMarqueeText`.

**Stability flag** — pass `isScrolling = isStable` where:
```kotlin
val isStable = (expandedFraction < 0.05f && queueProg < 0.05f) ||  // mini at rest
               (expandedFraction > 0.95f && queueProg < 0.05f) ||  // full at rest
               (queueProg > 0.95f)                                   // queue at rest
```
During any transition, `isScrolling = false` — marquee pauses so it doesn't compete with the position lerp.

Applies to all three stable states: mini player, full player, queue header.

### Usage in `FullPlayer`

The album label (`track.album.uppercase()`) gets the right-side fade modifier inline — no marquee, no `FadingMarqueeText` needed, just the `drawWithContent` gradient applied directly to the existing `Text`.

> Note: The title and artist in `FullPlayer` are invisible ghosts (alpha=0). The real rendering is in `QueueMorphLayer`. The album label is the only real text in `FullPlayer` that needs fade.

---

## 2. 3-dots → `TrackMenuOverlay` (no art morph)

### `TrackMenuOverlay` change

Add parameter: `skipArtMorph: Boolean = false`

When `skipArtMorph = true`:
- Floating art layer: `alpha = 0f` always (the flying-thumbnail animation is suppressed)
- In-card art alpha: `(prog * 2f).coerceIn(0f, 1f)` — fades in with the card instead of waiting for `switchProgress`

All other behavior (card scale/fade/slide-up, menu items, playlist picker morph) is unchanged.

### `FullPlayer` change

Add parameter: `onShowMenu: () -> Unit`  
Wire the 3-dots `IconButton(onClick = onShowMenu)`.

### `LibraryScreen` change

Add state: `var isMenuFromFullPlayer by remember { mutableStateOf(false) }`

In the `FullPlayer` invocation:
```kotlin
onShowMenu = {
    contextMenuTrack = currentTrack
    isMenuFromFullPlayer = true
}
```

In the `TrackMenuOverlay` invocation:
```kotlin
skipArtMorph = isMenuFromFullPlayer,
onDismiss = {
    contextMenuTrack = null
    isMenuFromFullPlayer = false
},
```

---

## 3. Like Button — Wiring + Pop Animation

### `FullPlayer` new parameters

```kotlin
isFavorite: Boolean,
onToggleFavorite: () -> Unit,
```

### Icon toggle

- Active: `Icons.Filled.Favorite`, tint `Color(0xFFE84B7A)`
- Inactive: `Icons.Outlined.FavoriteBorder`, tint `Color.White`

### Pop animation

```kotlin
var likePressed by remember { mutableStateOf(false) }
val likeScale by animateFloatAsState(
    targetValue = if (likePressed) 1.4f else 1f,
    animationSpec = spring(dampingRatio = 0.3f, stiffness = 600f),
    label = "LikeScale"
)
LaunchedEffect(likePressed) {
    if (likePressed) { delay(50); likePressed = false }
}
```

`onClick = { likePressed = true; onToggleFavorite() }` — fires on both like and unlike.

Apply `Modifier.graphicsLayer { scaleX = likeScale; scaleY = likeScale }` to the icon.

### `LibraryScreen` wiring

```kotlin
isFavorite = favoriteIds.contains(currentTrack?.id),
onToggleFavorite = { currentTrack?.let { viewModel.toggleFavorite(it.id) } },
```

---

## 4. "Go to Album / Artist" from FullPlayer Menu

When `isMenuFromFullPlayer = true`, the `onViewAlbum` and `onViewArtist` callbacks passed to `TrackMenuOverlay` prepend a `scope.launch { scaffoldState.bottomSheetState.partialExpand() }` call before navigating.

Sequence on tap: `TrackMenuOverlay` calls `dismiss()` (its internal animated close) → callback fires → player collapses → NavController navigates to Album/Artist screen.

---

## 5. Album Name Header Fade (FullPlayer)

The `track.album.uppercase()` `Text` in the FullPlayer top bar gets a right-side fade using the same `drawWithContent` + `BlendMode.DstIn` gradient. No marquee — it's a header label. Applied inline; no new composable needed.

---

## Files Changed

| File | Change |
|------|--------|
| `ui/components/FadingMarqueeText.kt` | New file — reusable fade+marquee composable |
| `ui/components/TrackMenuOverlay.kt` | Add `skipArtMorph` param; conditional art alpha |
| `ui/components/FullPlayer.kt` | Add `onShowMenu`, `isFavorite`, `onToggleFavorite`; like pop; album fade |
| `ui/LibraryScreen.kt` | Wire new FullPlayer params; `isMenuFromFullPlayer` state; collapse-then-navigate callbacks |

---

## Out of Scope

- LYRICS button functionality (placeholder kept as-is)
- Any changes to QueueSheet, MiniPlayer, or other screens
- Marquee in non-player contexts (search results, playlists, etc.)
