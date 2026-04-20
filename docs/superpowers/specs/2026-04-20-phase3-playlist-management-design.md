# Phase 3 — Playlist Management Design

**Date:** 2026-04-20
**Goal:** Full user-defined playlist CRUD with a polished, immersive dark UI consistent with the existing app aesthetic.

---

## Agreed Decisions

| Decision | Choice |
|----------|--------|
| Create/rename UI | `ModalBottomSheet` with `TextField` |
| Playlist detail track list | Drag-to-reorder (reuse queue code) |
| Playlist cover art | 2×2 mosaic of first 4 track album arts |
| "Add to playlist" picker | Includes "+ New playlist" at top (on-the-spot creation) |
| Rename / delete affordance | ⋮ button per playlist row |
| ViewModel architecture | Separate `PlaylistsViewModel` + `PlaylistDetailViewModel` |

---

## Data Layer

### Addition: batch reorder method

`PlaylistDao`:
```kotlin
@Transaction
suspend fun reorderTracks(tracks: List<PlaylistTrack>)
// delete all for playlistId, re-insert with updated positions
```

`UserDataRepository` + `UserDataRepositoryImpl`:
```kotlin
suspend fun reorderPlaylistTracks(playlistId: Long, tracks: List<PlaylistTrack>)
```

Everything else (create, rename, delete, add/remove track, getTrackIds) already exists from Phase 1.

---

## ViewModel Layer

### `PlaylistsViewModel` (`app`, `@HiltViewModel`)

State:
- `playlists: StateFlow<List<Playlist>>` — from `userDataRepository.getAllPlaylists()`
- `allTracks: StateFlow<List<Track>>` — from `mediaRepository` (for mosaic art lookup)

Operations:
- `createPlaylist(name: String)`
- `renamePlaylist(id: Long, name: String)`
- `deletePlaylist(id: Long)`

### `PlaylistDetailViewModel` (`app`, `@HiltViewModel`)

Saved state: `playlistId: Long` via `SavedStateHandle`

State:
- `playlist: StateFlow<Playlist?>` — single playlist metadata
- `tracks: StateFlow<List<Track>>` — ordered by `PlaylistTrack.position`, joined with MediaStore

Operations:
- `moveTrack(from: Int, to: Int)` — updates positions, calls `reorderPlaylistTracks`
- `removeTrack(trackId: Long)`
- `playAll()` — delegates to `MusicPlayer`
- `shuffleAll()` — delegates to `MusicPlayer`

---

## UI Layer

### `PlaylistsScreen` (replace stub)

Layout:
- `LazyColumn` of playlist rows
- Each row: mosaic cover (48dp) | name + track count | ⋮ button
- Floating action button (bottom-right): opens create `ModalBottomSheet`
- Favorites entry pinned at top (unchanged from Phase 2 stub)
- Empty state: "No playlists yet. Tap + to create one."

⋮ menu options per row: **Rename** / **Delete** (delete triggers confirmation dialog)

### `PlaylistDetailScreen` (new)

Layout:
- Top bar: playlist name + back button
- Header: mosaic cover (large, 120dp) + track count + "Play all" / "Shuffle" buttons
- `LazyColumn` of `TrackListItem` rows with drag handle (reuse `QueueSheet` drag logic)
- Context menu per track: **Remove from playlist** (plus existing track actions)

### `PlaylistCoverMosaic` (new shared component)

- Takes `List<Track>` (up to 4)
- 2×2 grid of `AsyncImage` (Coil)
- Falls back to single image if 1–3 tracks, placeholder icon if empty
- Sizes: 48dp (list row), 120dp (detail header)

### `PlaylistBottomSheet` (new shared component)

- `ModalBottomSheet` with a single `OutlinedTextField` + confirm/cancel buttons
- Used for both **create** and **rename** (title changes, pre-filled for rename)
- Validates: non-empty name, trims whitespace

### `PlaylistPickerDialog` update

- Add "+ New playlist" row at top of the existing picker `AlertDialog`
- Tapping it closes the picker and opens `PlaylistBottomSheet` to create, then auto-adds the track to the new playlist

---

## Navigation

New route: `NavRoute.PLAYLIST_DETAIL = "playlist_detail/{playlistId}"`

Wired in `LibraryScreen` NavHost:
```kotlin
composable(
    route = NavRoute.PLAYLIST_DETAIL,
    arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
) { backStackEntry ->
    val playlistId = backStackEntry.arguments!!.getLong("playlistId")
    PlaylistDetailScreen(playlistId = playlistId, onBack = { navController.popBackStack() })
}
```

Bottom nav Playlists tab stays highlighted when on `playlist_detail` (handled by existing parent-route highlight logic).

---

## File Locations

```
app/src/main/java/com/laconical/player/ui/
  screens/
    PlaylistsScreen.kt          # replaces stub
    PlaylistDetailScreen.kt     # new
  components/
    PlaylistCoverMosaic.kt      # new
    PlaylistBottomSheet.kt      # new
  viewmodels/
    PlaylistsViewModel.kt       # new
    PlaylistDetailViewModel.kt  # new

core/data/.../
  db/dao/PlaylistDao.kt         # add reorderTracks()
  UserDataRepository.kt         # add reorderPlaylistTracks()
  UserDataRepositoryImpl.kt     # implement reorderPlaylistTracks()
```

---

## Design System

- Style: Dark Mode (OLED) — matches existing app
- Album art dominant color tints headers and FAB (consistent with `playingTrackDominantColor` pattern)
- All touch targets ≥ 48dp
- Drag handles: 48dp wide, full row height hit area
- Mosaic images: use existing `AudioAlbumArtFetcher` Coil pipeline
- Animations: `ModalBottomSheet` slide-in 300ms ease-out; list item enter stagger 30ms
