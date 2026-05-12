# Create Playlist Dialog — Design Spec

**Date:** 2026-05-12
**Branch:** improvedPlaylist

---

## Overview

Replace `PlaylistBottomSheet` (used for create) with a new `CreatePlaylistDialog` component that matches the visual language of `TrackMenuOverlay`: dark floating rounded-rect card, scrim, spring-like entry animation. The rename flow keeps `PlaylistBottomSheet` unchanged.

---

## Visual Design

### Structure

Full-screen `Box(fillMaxSize)` overlay:

1. **Scrim** — `Color(0xCC000000)`, clickable (full dismiss)
2. **Card** — positioned in top 15% of screen (`BoxWithConstraints` → `padding(top = maxHeight * 0.15f, horizontal = 24.dp)`, `contentAlignment = Alignment.TopCenter`)
3. **Keyboard** — card is fixed in upper portion; keyboard appears below it, no overlap, no `imePadding` needed

### Card Layout

```
┌─────────────────────────────────────┐  ← RoundedCornerShape(20.dp)
│  [QueueMusic icon]  Create new       │  ← Header: bg #1A1A24
│                     playlist         │    icon: QueueMusic, #7C6FE0, 22dp
│                                      │    text: white, 15sp, SemiBold
├──────────────────────────────────────┤  ← Divider: #2A2A35, 0.5dp
│  ┌──────────────────────────────┐   │  ← Body: bg #12121A, padding 16/18dp
│  │  Playlist name               │   │    OutlinedTextField, singleLine
│  └──────────────────────────────┘   │
│                   [Cancel]  [Create] │    Cancel: TextButton, #888888
└─────────────────────────────────────┘    Create: filled purple #7C6FE0
                                            disabled (alpha 0.4) until name non-empty
```

Colors follow existing TrackMenuOverlay/PlaylistPickerBody conventions — no new raw hex values introduced beyond what already exists in those files.

---

## Component API

**File:** `app/.../ui/components/CreatePlaylistDialog.kt`

```kotlin
@Composable
fun CreatePlaylistDialog(
    originOffset: Offset?,       // null → simple scale+fade entry (PlaylistsScreen)
                                 // non-null → morph from origin point (track menu)
    onDismiss: () -> Unit,       // background tap or Cancel — full close
    onBack: () -> Unit,          // system back gesture — return to picker (or same as onDismiss)
    onConfirm: (String) -> Unit  // Create tapped with non-empty, trimmed name
)
```

**Internal state:**
- `val progress = remember { Animatable(0f) }` — drives all entry/exit transforms
- `var text by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }`
- `val focusRequester = remember { FocusRequester() }`
- `val scope = rememberCoroutineScope()`

---

## Animation

### Entry — from track menu (originOffset != null)

The "New Playlist" row in `PlaylistPickerBody` measures its root-space position via `onGloballyPositioned` and passes it through `onCreateNewPlaylist(offset)`.

```
scale:        0.82 → 1.0
alpha:        0.0  → 1.0
translationY: (originOffset.y - cardCenterY) → 0f
duration:     280ms, FastOutSlowIn
```

After `animateTo(1f)` completes → `focusRequester.requestFocus()` (keyboard auto-appears).

### Entry — from PlaylistsScreen (originOffset == null)

Simple appearance, no spatial origin:

```
scale:        0.88 → 1.0
alpha:        0.0  → 1.0
translationY: -16dp → 0f
duration:     280ms, FastOutSlowIn
```

After `animateTo(1f)` completes → `focusRequester.requestFocus()`.

### Exit

```
scale:    1.0 → 0.88
alpha:    1.0 → 0.0
duration: 200ms, FastOutSlowIn
```

Exit runs then callback fires (dismiss/back/confirm). Confirm fires immediately after submit (no exit delay).

---

## Back Gesture & Dismiss Logic

| Trigger | Action |
|---------|--------|
| System back | `onBack()` |
| Cancel button | `onDismiss()` |
| Background tap (scrim) | `onDismiss()` |
| Create button (name non-empty) | `onConfirm(name.trim())` |
| IME Done action | same as Create |

`BackHandler` is installed inside `CreatePlaylistDialog` with `enabled = true`.

---

## State Changes — LibraryScreen

### New state variables

```kotlin
var newPlaylistOriginOffset by remember { mutableStateOf(Offset.Zero) }
// showCreateForPicker already exists
```

### onCreateNewPlaylist wiring (TrackMenuOverlay callback)

```kotlin
onCreateNewPlaylist = { originOffset ->
    newPlaylistOriginOffset = originOffset
    pendingNewPlaylistTrack = track
    // contextMenuTrack intentionally NOT cleared — overlay stays alive
    showCreateForPicker = true
},
```

### CreatePlaylistDialog placement

Rendered **after** `TrackMenuOverlay` block in the root `Box` (higher z-order):

```kotlin
if (showCreateForPicker) {
    CreatePlaylistDialog(
        originOffset = newPlaylistOriginOffset.takeIf { contextMenuTrack != null },
        onDismiss = {
            showCreateForPicker = false
            contextMenuTrack = null
        },
        onBack = {
            showCreateForPicker = false
            // contextMenuTrack stays — reveals TrackMenuOverlay in PLAYLIST mode
        },
        onConfirm = { name ->
            pendingNewPlaylistTrack?.let { t ->
                viewModel.createPlaylistAndAdd(name, t.id)
            }
            pendingNewPlaylistTrack = null
            showCreateForPicker = false
            contextMenuTrack = null
        }
    )
}
```

---

## Changes — TrackMenuOverlay

`onCreateNewPlaylist` callback signature changes:

```kotlin
// Before
onCreateNewPlaylist: () -> Unit

// After
onCreateNewPlaylist: (originOffset: Offset) -> Unit
```

In `PlaylistPickerBody`, the "New Playlist" row measures its position:

```kotlin
var rowOffset by remember { mutableStateOf(Offset.Zero) }

Row(
    modifier = Modifier
        .onGloballyPositioned { coords -> rowOffset = coords.positionInRoot() }
        .clickable { onCreateNew(rowOffset) }
        ...
)
```

---

## Changes — PlaylistsScreen

`PlaylistBottomSheet` for create is **removed** from `PlaylistsScreen`. Instead, `PlaylistsScreen` accepts a new callback parameter and delegates upward:

```kotlin
// PlaylistsScreen.kt — signature change
@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,   // ← new, replaces internal showCreateSheet logic
    bottomPadding: Dp = 0.dp,
    ...
)

// NewPlaylistRow click wires to onCreatePlaylist() instead of showCreateSheet = true
```

`showCreateSheet` state and `PlaylistBottomSheet` create usage are **removed** from `PlaylistsScreen`.

`CreatePlaylistDialog` is rendered in the root `Box` of `LibraryScreen` (alongside `TrackMenuOverlay`), so it layers above bottom nav. `LibraryScreen` owns `showCreateFromPlaylistsTab` state:

```kotlin
// LibraryScreen — new state
var showCreateFromPlaylistsTab by remember { mutableStateOf(false) }

// NavHost — pass callback down to PlaylistsScreen
composable(NavRoute.PLAYLISTS) {
    PlaylistsScreen(
        onCreatePlaylist = { showCreateFromPlaylistsTab = true },
        ...
    )
}

// Root Box — after TrackMenuOverlay block
if (showCreateFromPlaylistsTab) {
    CreatePlaylistDialog(
        originOffset = null,
        onDismiss = { showCreateFromPlaylistsTab = false },
        onBack = { showCreateFromPlaylistsTab = false },
        onConfirm = { name ->
            viewModel.createPlaylist(name)
            showCreateFromPlaylistsTab = false
        }
    )
}
```

`PlaylistBottomSheet` remains for **rename only** — no changes to rename flow.

---

## Files Changed

| File | Change |
|------|--------|
| `ui/components/CreatePlaylistDialog.kt` | **New** — the component |
| `ui/components/TrackMenuOverlay.kt` | `onCreateNewPlaylist` signature + row offset measurement |
| `ui/LibraryScreen.kt` | State vars, wiring, overlay placement, pass callback to PlaylistsScreen |
| `ui/screens/PlaylistsScreen.kt` | Replace `PlaylistBottomSheet` create usage with callback; accept `onCreatePlaylist` param |

---

## Out of Scope

- Rename flow — `PlaylistBottomSheet` unchanged
- Delete confirmation dialog — `AlertDialog` unchanged
- Any changes to `PlaylistDetailScreen` or playlist ViewModels
