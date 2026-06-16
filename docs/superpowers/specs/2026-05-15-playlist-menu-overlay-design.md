# Playlist Context Menu Overlay

**Date:** 2026-05-15
**Branch:** betterplaylistmenu
**Status:** Approved

## Problem

`PlaylistRow` uses a native `DropdownMenu` — generic Material 3 widget with no animation, no scrim, emoji icons. Inconsistent with the polished `TrackMenuOverlay` which morphs album art from the row into a centered card with scrim, scale/fade animation, and SVG icons.

## Goal

Replace the `DropdownMenu` with a `PlaylistMenuOverlay` that is visually and behaviourally 1:1 with `TrackMenuOverlay`, adapted for playlist-specific actions (Rename, Delete).

---

## Design

### Visual

- Full-screen dark scrim (`0xCC000000`, alpha driven by `progress`)
- Centered rounded card (`RoundedCornerShape(20.dp)`) with:
  - **Header:** dominant-color tinted background (same formula as `TrackMenuOverlay`), 64×64dp mosaic cover + playlist name + track count
  - **Body:** two `MenuRow` items — Rename (white) and Delete Playlist (red `0xFFEF4444`)
- Card entrance: `scaleX/Y` lerp `0.92→1`, `alpha` `0→1`, `translationY` `48→0`, `tween(320, FastOutSlowInEasing)`
- Card exit: reverse, `tween(200, FastOutSlowInEasing)`
- `BackHandler` dismisses

### Morph Mechanic

`PlaylistCoverMosaic` is a regular Composable — no bitmap capture needed. Same ghost-overlay pattern as `TrackMenuOverlay`:

1. **Floating layer** (`PlaylistCoverMosaic` in a positioned `Box`):
   - Offset lerps from `artStartOffsetPx` (row thumbnail root-space position) → `targetOffsetPx` (card header ghost position) driven by `progress`
   - Size lerps `52.dp → 64.dp`
   - Corner lerps `10f → 14f` dp
   - Alpha: `lerp(1f, 0f, (progress * 4f).coerceIn(0f, 1f))` — fades out in first quarter

2. **In-card ghost** (`Box` with `onGloballyPositioned` giving `targetOffsetPx`):
   - Alpha: `lerp(0f, 1f, (progress * 4f).coerceIn(0f, 1f))` — fades in as floating fades out
   - Size `64.dp`, corner `14.dp`

Row mosaic size is `52.dp` (matches current `PlaylistRow`). Card header ghost size is `64.dp` (matches `TrackMenuOverlay`).

---

## Architecture

### State — lifted to LibraryScreen

```kotlin
var contextMenuPlaylist by remember { mutableStateOf<Playlist?>(null) }
var contextMenuPlaylistArtOffset by remember { mutableStateOf(Offset.Zero) }
var contextMenuPlaylistArtSize by remember { mutableFloatStateOf(0f) }
```

`PlaylistMenuOverlay` rendered in the outermost `Box` of `LibraryScreen` (same layer as `TrackMenuOverlay`) — required so root-space offsets are correct.

### ViewModel access

`PlaylistsViewModel` is `@HiltViewModel`. LibraryScreen obtains it via `hiltViewModel()` — Hilt scope is the same NavBackStackEntry/Activity so rename/delete calls go to the same instance. `PlaylistsScreen` continues using its own `hiltViewModel()` — same shared instance under the hood.

### Rename dialog

LibraryScreen shows the existing `PlaylistBottomSheet` when `onRename` fires from the overlay:

```kotlin
contextMenuPlaylist?.let { target ->
    PlaylistBottomSheet(
        title = "Rename Playlist",
        initialName = target.name,
        onDismiss = { contextMenuPlaylist = null },
        onConfirm = { name ->
            playlistsViewModel.renamePlaylist(target.id, name)
            contextMenuPlaylist = null
        }
    )
}
```

Overlay is dismissed before the sheet shows (`onDismiss()` called first inside overlay's `onRename`).

### Delete dialog

LibraryScreen shows the existing `AlertDialog` for delete confirmation (same dialog body currently in `PlaylistsScreen` — just moves up).

---

## Files

| File | Change |
|------|--------|
| `ui/components/PlaylistMenuOverlay.kt` | **NEW** — full overlay composable |
| `ui/screens/PlaylistsScreen.kt` | Add `onMenuOpen` param · Remove `DropdownMenu`, `renameTarget`/`deleteTarget` state, `PlaylistBottomSheet`, `AlertDialog` |
| `ui/LibraryScreen.kt` | Add overlay state vars · Obtain `PlaylistsViewModel` via `hiltViewModel()` · Render `PlaylistMenuOverlay` in outermost Box · Show rename/delete dialogs · Pass `onMenuOpen` to `PlaylistsScreen` |

---

## Component Signature

```kotlin
@Composable
fun PlaylistMenuOverlay(
    playlist: Playlist,
    artTracks: List<Track>,
    artStartOffsetPx: Offset,
    artStartSizePx: Float,
    dominantColor: Color?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
)
```

---

## Out of Scope

- No mode-switching inside the overlay (no "add to playlist" sub-panel — playlists don't add to other playlists)
- No play/shuffle actions — those live on `PlaylistDetailScreen`
- No changes to `PlaylistCoverMosaic` itself
