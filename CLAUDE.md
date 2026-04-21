# CLAUDE.md

**Code quality:** Modern Kotlin. Use context7 MCP for docs.
**Git:** Commit after verifying changes on the associated branch.
**Worktrees:** Use `.worktrees/` for git worktree dirs.

## Project Overview

Laconical Player — open-source Android music player (Namida-inspired). Local audio playback with morphing transitions, particle effects, waveform visualization, and dominant-color theming.

## Build Commands

```bash
./gradlew assembleDebug          # debug build → app/build/outputs/apk/debug/
./gradlew clean assembleDebug    # clean build
./gradlew installDebug           # install on device
./gradlew lint
./gradlew :core:media:test       # Robolectric + Media3 unit tests
```

## Module Structure

| Module | Role |
|--------|------|
| `:app` | UI — `LibraryScreen`, `MainViewModel`, `MainActivity` |
| `:core:model` | `Track` data class |
| `:core:data` | `MediaRepository` + `LocalMediaRepositoryImpl` (MediaStore) |
| `:core:media` | `MusicPlayer`, `PlaybackService`, `AudioVisualizerManager`, `WaveformExtractor` |
| `:core:designsystem` | `LaconicalTheme` (M3), color/type tokens |

## Architecture

**State:** Single `@HiltViewModel` — `MainViewModel`. One screen (`LibraryScreen`) in `MainActivity.setContent`. NavHost with bottom-nav routes (Tracks, Albums, Artists, Playlists, Favorites).

**Key `StateFlow`s in `MainViewModel`:** `tracks`, `currentTrack`, `playingTrackDominantColor`, `isPlaying`, `currentPosition`, `duration`, `progress`, `queue`, `currentQueueIndex`, `shuffleModeEnabled`, `repeatMode`, `waveform`, `beatPulse`, `_waveformData`, `_currentNormalizedAmplitude` (60 fps ticker, decays to zero when paused — zero CPU at idle).

### Playback (Two Layers)

- **`PlaybackService`** — `MediaSessionService` foreground service; singleton `ExoPlayer` via Hilt `MediaModule`.
- **`MusicPlayerImpl`** — singleton `MediaController` (created once in `init`). Polls position at 50 ms. Listens to `onMediaItemTransition` + `onTimelineChanged` to keep `_currentMediaItemIndex` in sync after reorder.
- **`MediaPreWarmer`** — pre-warms `PlaybackService` at `LaconicalApp.onCreate`; controller released immediately, ExoPlayer survives.
- **`AudioVisualizerManager`** — Android `Visualizer` on ExoPlayer's `audioSessionId`. Sine-wave fallback on silence. `@Volatile` on `isVisualizerGeneratingRealData`.
- **`WaveformExtractor`** — Amplituda via `ContentResolver.openInputStream(uri)`. `Mutex` serializes concurrent calls.

### Morphing Player Transition (3-Phase, No Shared Element API)

- **Phase 1 (mini→full):** `BottomSheetScaffold.expandedFraction` (0→1). Ghost elements report `positionInRoot()`; overlay `lerp()`s between coordinate sets.
- **Phase 2+3 (full→queue):** `queueProgress` float drives a second `lerp()` from FullPlayer → QueueSheet header positions.
- **Ghost contract:** Source elements are invisible (alpha=0/Color.Transparent/placeholder Box). Overlay is the sole renderer and tap handler.

### Album Art Loading

Custom Coil 3: `AudioAlbumArtFetcher` + `AudioAlbumArtKeyer` (in `MainViewModel.kt`). Uses `MediaMetadataRetriever`. Global `ImageLoader` registered in `LaconicalApp`.

## Key Files

```
app/.../
  LaconicalApp.kt              # @HiltAndroidApp, global Coil ImageLoader
  ui/MainViewModel.kt          # All state + orchestration
  ui/LibraryScreen.kt          # Entire UI, BottomSheetScaffold, morph overlay
  ui/ColorUtils.kt             # Color.toHsl() extension (single source of truth)
  ui/SortOrder.kt              # Enum for track sort order
  ui/navigation/NavRoute.kt    # All route strings + helper fns
  ui/components/
    FullPlayer.kt              # Expanded player, VisualizerSeekBar, ParticleSystem
    MiniPlayer.kt              # Collapsed strip, morphing GlowIconButton controls
    QueueSheet.kt              # Full-screen queue, drag-to-reorder
    TrackListItem.kt           # Per-row Palette extraction + ParticlesEffectCanvas
    ParticlesEffectCanvas.kt   # Physics particles — mutate in LaunchedEffect only
    PlaylistCoverMosaic.kt     # 2×2 mosaic art (AudioArtData + Coil)
    PlaylistBottomSheet.kt     # ModalBottomSheet for create/rename playlist
    TrackContextMenu.kt        # DropdownMenu with track actions
  ui/screens/
    AlbumsScreen.kt / AlbumDetailScreen.kt
    ArtistsScreen.kt / ArtistDetailScreen.kt
    FavoritesScreen.kt
    PlaylistsScreen.kt / PlaylistDetailScreen.kt
  ui/viewmodels/
    AlbumsViewModel.kt / ArtistsViewModel.kt
    PlaylistsViewModel.kt / PlaylistDetailViewModel.kt

core/media/.../
  MusicPlayer.kt               # Interface + MusicPlayerImpl
  PlaybackService.kt           # MediaSessionService
  AudioVisualizerManager.kt    # Real-time Visualizer + sine fallback
  WaveformExtractor.kt         # Amplituda via ContentResolver
  MediaPreWarmer.kt
  PlaylistRepeatTest.kt        # Robolectric repeat-mode tests

core/data/.../
  LocalMediaRepositoryImpl.kt  # MediaStore (content URIs, no DATA column)
  UserDataRepository.kt / UserDataRepositoryImpl.kt
  db/entity/  FavoriteTrack, Playlist, PlaylistTrack, PlayHistory
  db/dao/     FavoriteDao, PlaylistDao, HistoryDao
  db/MusicDatabase.kt
  di/DatabaseModule.kt / DataModule.kt
```

## Tech Stack

Kotlin + Jetpack Compose (M3) · Hilt · Media3/ExoPlayer · Room · Coil 3 (custom audio art fetcher) · Amplituda · Android `Visualizer` API · Palette API · minSdk 26 / targetSdk 35

## Design System

- All colors semantic via `LaconicalTheme` — no raw hex in component code.
- Dominant album art color tints UI via `playingTrackDominantColor`.
- `Color.toHsl()` defined once in `ColorUtils.kt` — no inline duplicates.
- All motion: compositor-only (`scale`, `alpha`, `offset` via `lerp`).

## Memory Safety

- `MusicPlayerImpl`: one `MediaController` for process lifetime.
- `waveformJob`/`colorJob` in `MainViewModel`: cancelled before new one starts.
- `WaveformExtractor.mutex`: prevents concurrent Amplituda (not thread-safe).
- `ParticlesEffectCanvas`: mutates state only in `LaunchedEffect`, never in `Canvas`.

## Animation Pitfalls (Hard-Won Rules)

**Lerp linearity:** Freeze start-point before animation begins. Use sheet-relative coords for full player.

**Spring gating:** Spring ON only when fully at rest (expandedFraction ≥ 0.99 && queueProg < 0.01). Never apply spring during morph.

**Lazy list scroll:** Use `scrollToItem` (instant, at progress > 0.01f), never `animateScrollToItem`. Guard with `wasQueueOpen` so track changes don't yank the list.

**Drag-to-reorder pitfalls:**
1. `onTimelineChanged` required for index sync after reorder (not just `onMediaItemTransition`).
2. Use `rememberUpdatedState` for all gesture callbacks inside `pointerInput` — captured index goes stale on reorder.
3. Never `scrollToItem` in `onDragStart`. Clamp `visTarget` to `firstVisibleItemIndex` in `graphicsLayer` instead:
```kotlin
val visTarget = if (from > target) target.coerceAtLeast(firstVisible) else target
translationY = when {
    index == from  -> dy
    from < visTarget && index in (from + 1)..visTarget -> -itemHeightPx
    from > visTarget && index in visTarget until from  ->  itemHeightPx
    else -> 0f
}
```

**Stale lambda in `pointerInput`:** Never read a composition snapshot inside a `pointerInput` lambda. Read live source (`animatable.value`) or use `rememberUpdatedState`.

## Visual Design

Aesthetic-first (Namida-inspired): dominant-color gradients/glows · particle effects on active track · morphing mini↔full player · pulsating album art from waveform amplitude · compositor-only animations.
