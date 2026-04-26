# Track Menu → Playlist Picker Morph Transition

**Date:** 2026-04-26
**Branch:** bugfixes

## Problem

Tapping "Add to Playlist" in `TrackMenuOverlay` dismisses the overlay and spawns `AddToPlaylistOverlay` as a separate composable. This produces a visible jump: the card disappears and re-enters with a fresh animation, and the album art re-morphs from scratch.

## Goal

The menu card morphs in-place. Thumbnail stays. Body content crossfades. Card height animates. Back from playlist picker returns to the main menu (no dismiss).

---

## Approach: Single Unified Overlay with Internal State Machine

Merge `TrackMenuOverlay` and `AddToPlaylistOverlay` into a single composable. `AddToPlaylistOverlay.kt` is deleted.

### State

```kotlin
enum class TrackMenuMode { MAIN, PLAYLIST }

val progress = remember { Animatable(0f) }        // enter/exit (unchanged)
val switchProgress = remember { Animatable(0f) }  // 0=MAIN, 1=PLAYLIST, bidirectional
var mode by remember { mutableStateOf(TrackMenuMode.MAIN) }
```

### Back Handler Chain

```
BackHandler:
  PLAYLIST mode → mode = MAIN immediately, switchProgress 1→0 (250ms) in parallel
  MAIN mode     → progress 1→0 (200ms) → onDismiss()
```

"Add to Playlist" row tapped → `mode = PLAYLIST` immediately + `scope.launch { switchProgress.animateTo(1f, tween(280)) }` in parallel (no dismiss).

`mode` always changes first so `AnimatedContent` triggers the correct crossfade. `switchProgress` drives art handoff and thumbnail size — these animate alongside `AnimatedContent`'s own fade.

---

## Section 1: Signature Changes

`TrackMenuOverlay` gains playlist parameters. `onAddToPlaylist` is removed (becomes internal).

```kotlin
fun TrackMenuOverlay(
    track: Track,
    artStartOffsetPx: Offset,
    artStartSizePx: Float,
    isFavorite: Boolean,
    dominantColor: Color?,
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    onDismiss: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNewPlaylist: () -> Unit,
)
```

LibraryScreen: remove `AddToPlaylistOverlay` block, pass `playlists` and `playlistArtTracks` into `TrackMenuOverlay`, remove `playlistPickerTrack` state variable.

---

## Section 2: Thumbnail Handoff

Two art layers coexist during the initial entry morph, then hand off seamlessly when mode switches.

**Floating art overlay** (root-space, drawn above card — existing ghost pattern):
```kotlin
alpha = lerp(1f, 0f, (switchProgress.value * 4f).coerceIn(0f, 1f))
// invisible by switchProgress = 0.25
```
Position/size lerp to ghost target as before during entry (`progress`-driven). Once `switchProgress > 0`, floats art is gone.

**In-card thumbnail** (replaces the transparent ghost Box):
```kotlin
// Real SubcomposeAsyncImage always rendered
size   = lerp(64.dp, 56.dp, switchProgress.value)
corner = lerp(14.dp, 12.dp, switchProgress.value)
alpha  = lerp(0f, 1f, (switchProgress.value * 4f).coerceIn(0f, 1f))
```

The swap is invisible: at `switchProgress=0` the floating art sits exactly on top of the in-card image (same coords, same size). Both crossfade in ~70ms.

`onGloballyPositioned` measurement stays on the in-card box for initial morph target.

---

## Section 3: Body Crossfade

```kotlin
AnimatedContent(
    targetState = mode,
    transitionSpec = {
        fadeIn(tween(200, delayMillis = 100)) togetherWith fadeOut(tween(100))
    }
) { currentMode ->
    when (currentMode) {
        TrackMenuMode.MAIN     -> MainMenuBody(...)
        TrackMenuMode.PLAYLIST -> PlaylistPickerBody(...)
    }
}
```

Old content fades out in 100ms. New content fades in after 100ms delay (200ms duration). No overlap.

**Card height:** `Modifier.animateContentSize(animationSpec = tween(280, easing = FastOutSlowInEasing))` on the outer `Column`. Grows/shrinks automatically.

---

## Section 4: Header Text Crossfade

Same `AnimatedContent(mode)` pattern for the text column next to the thumbnail:

```
MAIN:     title (15sp SemiBold) / artist (13sp) / album (11sp)
PLAYLIST: "ADD TO PLAYLIST" (11sp label, tracked) / title (15sp SemiBold) / artist (13sp)
```

---

## Animation Timeline (mode switch)

```
t=0ms    switchProgress starts 0→1 (280ms, FastOutSlowInEasing)
t=0ms    floating art alpha 1→0 (~70ms)
t=0ms    in-card art alpha 0→1 (~70ms)   ← seamless swap
t=0ms    header text fades out (100ms)
t=0ms    old body rows fade out (100ms)
t=100ms  new header text fades in (200ms)
t=100ms  playlist body fades in (200ms)
t=0ms    card height animates via animateContentSize
t=280ms  animation complete, mode = PLAYLIST
```

Reverse (back press from PLAYLIST): same sequence mirrored, 250ms.

---

## Files Changed

| File | Change |
|------|--------|
| `TrackMenuOverlay.kt` | Major rewrite — absorbs playlist picker, adds mode state (~330→~420 lines) |
| `AddToPlaylistOverlay.kt` | **Deleted** |
| `LibraryScreen.kt` | ~15 lines — remove `AddToPlaylistOverlay` block, remove `playlistPickerTrack` state, update `TrackMenuOverlay` call |

---

## What Stays the Same

- Initial morph (thumbnail travels from row to card header) — unchanged
- Entry/exit animation for `progress` — unchanged
- `PlaylistPickerRow`, `PlaylistCoverMosaic` composables — moved/reused as private functions in `TrackMenuOverlay.kt`
- `MenuRow` private composable — unchanged
- Scrim behavior — unchanged
