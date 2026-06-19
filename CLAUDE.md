# CLAUDE.md

**Code quality:** Modern Kotlin.
**Dependency docs (MANDATORY):** Deps are bumped aggressively via Dependabot (Compose BOM, Media3, Coil 3, Kotlin/KSP, AGP are often months ahead of training data). APIs and behavior shift between these versions. Before using or debugging ANY library API here, fetch current docs via **context7 MCP** (`resolve-library-id` → `query-docs`) — do NOT rely on memory. This is how the morph regression below was diagnosed.
**Git:** Commit after verifying changes on the associated branch. Before committing, verify `git config user.name`/`user.email` are set (global `~/.gitconfig`: name "Btema2", email "temabiill@gmail.com" — matches GitHub account). If unset/wrong, git falls back to OS username (lowercase, unlinked on GitHub) — fix config before committing, don't just proceed.
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

**Key `StateFlow`s in `MainViewModel`:** `tracks`, `currentTrack`, `playingTrackDominantColor`, `isPlaying`, `currentPosition`, `duration`, `progress`, `queue` (backed by `_currentQueue` — context-specific, not global `_allTracks`), `currentQueueIndex`, `shuffleModeEnabled`, `repeatMode`, `waveform`, `beatPulse`, `_waveformData`, `_currentNormalizedAmplitude` (60 fps ticker, decays to zero when paused — zero CPU at idle), `searchedAlbums`, `searchedArtists`, `searchedPlaylists`, `playlistArtTracks`.

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
- **Start-point anchor:** the mini-side lerp origin is the *collapsed* `sheetRootYPx`, captured in `LibraryScreen` (`collapsedSheetRootYPx`) at rest, gated on `currentTrack != null`, then passed into `QueueMorphLayer`. Getting this wrong is the source of two distinct bugs — see Animation Pitfalls → Morph start-point.

### Album Art Loading

Custom Coil 3: `AudioAlbumArtFetcher` + `AudioAlbumArtKeyer` (in `MainViewModel.kt`). `AudioArtData` carries both `uri` (audio file) and `albumArtUri` (MediaStore `content://media/external/audio/albumart/<albumId>`). Keyer uses `albumArtUri` when present — all tracks in same album share one cache entry. Fetcher fast path: `ContentResolver.openInputStream(albumArtUri)` before falling back to `MediaMetadataRetriever`. Global `ImageLoader` has 50 MB disk cache + 15% memory cache, registered in `LaconicalApp`.

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
    TrackMenuOverlay.kt        # Unified track menu — morphs to playlist picker in-place
    LaconicalBottomNav.kt      # Bottom nav with selection indicator pill
    NavTransitions.kt          # Directional slide transition specs
    StaggeredEntrance.kt       # Staggered list-item entrance modifier
  ui/screens/
    AlbumsScreen.kt / AlbumDetailScreen.kt
    ArtistsScreen.kt / ArtistDetailScreen.kt
    FavoritesScreen.kt
    PlaylistsScreen.kt / PlaylistDetailScreen.kt
    SearchScreen.kt            # Search results overlay (inline in LibraryScreen)
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

**Morph is fragile to Compose-foundation version bumps — freeze all anchors at rest (regression from compose-bom 2025.02.00 → 2026.05.01, PR #23 / `f94d7a3`).** The mini→full overlay lerps full-side targets computed as `(fullXxxPx - sheetRootYPx)`, all sourced from `onGloballyPositioned` → `positionInRoot()`. That difference is only correct if every value updates in **lockstep every frame**. Newer Compose backs `onGloballyPositioned` with a throttled/debounced rect tracker ("fires when position *may* have changed"; see `Modifier.onLayoutRectChanged`), so the values desync per-frame → the "constant" wobbles. Symptoms: **stuttering / jumps back and forth**, and a **disappear-then-reappear flash** when the live-value readiness gate flickers. Fix (in `LibraryScreen.kt`): snapshot every ghost position + sheet root **atomically into a `MorphAnchors` while the sheet is at rest** (`expandedFraction < 0.05f`, `currentTrack != null`), hold it frozen, and gate the overlay on the latched anchors (not live values). The lerp then depends only on the smooth `expandedFraction` driver. Also: `bottomSheetState.requireOffset()` throws transiently in newer foundation — never fall back to `maxOffset` (that = collapsed → snaps the morph shut for a frame); cache the last valid offset in a non-snapshot holder. Diagnose version-specific Compose behavior changes via **context7 MCP**, not memory.

**Morph start-point (mini→full lerp) — three traps, hit in order while fixing one cold-start bug:**

1. **Capture the collapsed anchor in the parent, never in the overlay.** `collapsedSheetRootYPx` is captured in `LibraryScreen` (always composed). The morph overlay (`QueueMorphLayer`) is gated on `allGhostsReady`; on a cold start the ghosts first report their positions *mid-expand*, so a capture living inside the overlay never sees the at-rest value. The lerp then starts from a *moving* point → curved "hook" (Y races ~2×) + a visible snap when the value finally latches. Tell-tale symptom: the jump happens **only on the first slide** and disappears after a few (the latched value gets remembered once captured at rest).
2. **Gate capture on `currentTrack != null`.** With no track, `logicalPeekHeight` is `0`, so the sheet rests entirely below the screen and `sheetRootYPx` equals the screen bottom. Capturing then anchors the mini elements off-screen — symptom: overlay elements **"come up from under the screen"** during the expand. Capture only once a track exists.
3. **Do NOT reconstruct the anchor from offset math.** `collapsed = sheetRootYPx + maxOffset * expandedFraction` looks algebraically exact but `sheetRootYPx` does **not** track the sheet offset 1:1 — the term overshoots and pushes elements off-screen. Capture the real measured value at rest (`expandedFraction < 0.05f`) in a `LaunchedEffect`, then hold it frozen through the expand.

**Lerp linearity:** the captured collapsed anchor keeps the mini-side start-point still during the morph (a live `sheetRootYPx` moves both lerp endpoints → curved path). Full-player side uses sheet-relative coords (`absY - sheetRootYPx`, stable as the sheet scrolls); album-art full side is a pure constant.

**Back-step blip at the END of a fast tap/fling collapse, never on slow drag — anchors must only capture when the sheet is IDLE.** Symptom: near the end of a fast collapse the morph elements jump *back* toward the full-player layout for ~1 frame, then snap forward and finish. Invisible on a slow finger drag. **Dominant root cause (the real fix):** `sheetRootYPx` and the full-side ghost positions are measured on nodes *inside the sheet*, so they physically move as the sheet slides, written through the throttled/debounced `onGloballyPositioned`. The frozen-`MorphAnchors` capture `LaunchedEffect` re-fires when `anchorsAtRest` flips true. Gating that on `expandedFraction < 0.05f` *alone* is wrong: on a fast collapse the fraction crosses 0.05 **while the sheet is still moving at speed**, so the capture reads a *lagging mid-motion* `sheetRootYPx` and overwrites the anchors with a bad value. `anchors.sheetRootYPx` is the **mini-side origin** (`miniSheetRootYPx`, dominant near the end of the collapse) → every morph element jumps; the settled callback then re-captures the correct value → snap-forward. Fix: `anchorsAtRest = expandedFraction < 0.05f && !bottomSheetState.isAnimationRunning` (material3 1.4+). Held anchors are invariant to the sheet offset, so refusing to capture mid-animation loses nothing — only ever (re)capture from a settled, idle layout pass. NOTE: a monotonic-`expandedFraction` driver guard (clamp toward `targetValue` while `isAnimationRunning`) does **not** fix this — the jump comes from the frozen anchor changing, not from the driver. (That guard is kept as defense-in-depth against a separate offset-cache back-step, but it is secondary.)

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
