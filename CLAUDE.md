# CLAUDE.md

**Code quality:** Modern Kotlin.
**Graphify (MANDATORY):** Before exploring codebase (source browsing, grep, Read on unfamiliar files), run `graphify query "<question>"` first when `graphify-out/graph.json` exists. Use `graphify path "<A>" "<B>"` for relationships, `graphify explain "<concept>"` for focused concepts. Only fall back to raw grep/Read after graphify oriented you, or to modify/debug specific known lines. Applies to subagents too — include this rule in subagent prompts involving code exploration. See graphify section below for full details.
**Dependency docs (MANDATORY):** Deps are bumped aggressively via Dependabot (Compose BOM, Media3, Coil 3, Kotlin/KSP, AGP are often months ahead of training data). APIs and behavior shift between these versions. Before using or debugging ANY library API here, fetch current docs via **context7 MCP** (`resolve-library-id` → `query-docs`) — do NOT rely on memory. This is how the morph regression below was diagnosed.
**Worktrees:** Use `.worktrees/` for git worktree dirs.
**SDK pin:** compileSdk/targetSdk stay at 36. Bump to 37 tried once (`5e2d44a`) and reverted (`9afe984`) — do not redo it, including as a side effect of a dependency bump, without checking why it broke first.
**Release checklist:** Before tagging/publishing a GitHub release, bump `versionCode`/`versionName` in `app/build.gradle.kts` FIRST, commit it, then `./gradlew assembleRelease` from that commit, then tag. v1.2 shipped with the APK still reporting `versionName "1.1"` (`versionCode=2`) because the bump was skipped — fixed after the fact by re-cutting the APK and replacing the release asset.

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
| `:core:data` | `MediaRepository` + `LocalMediaRepositoryImpl` (MediaStore); lyrics chain (`lyrics/`), Room DB |
| `:core:media` | `MusicPlayer`, `PlaybackService`, `AudioVisualizerManager`, `WaveformExtractor` |
| `:core:designsystem` | `LaconicalTheme` (M3), color/type tokens |

## Architecture

**State:** Single `@HiltViewModel` — `MainViewModel`. One screen (`LibraryScreen`) in `MainActivity.setContent`. NavHost with bottom-nav routes (Tracks, Albums, Artists, Playlists, Favorites) **plus** a full-screen `SETTINGS` route.

**Settings route:** `NavRoute.SETTINGS` is a full-screen destination inside the same `NavHost`. It enters/exits via the normal horizontal slide (`navEnterTransition`/`navPopExitTransition`). The main chrome (top bar + bottom nav) fades out as the slide happens — driven by `chromeAlpha` (an `Animatable<Float>`) that is animated in a `LaunchedEffect(onSettings)` in sync with `SLIDE_DURATION_MS = 250 ms`. See Animation Pitfalls → TopBar decoupling for why the top bar must be a floating overlay, not in the scaffold `topBar` slot.

**Key `StateFlow`s in `MainViewModel`:** `tracks`, `currentTrack`, `playingTrackDominantColor`, `isPlaying`, `currentPosition`, `duration`, `progress`, `queue` (backed by `_currentQueue` — context-specific, not global `_allTracks`), `currentQueueIndex`, `shuffleModeEnabled`, `repeatMode`, `waveform`, `beatPulse`, `_waveformData`, `_currentNormalizedAmplitude` (60 fps ticker, decays to zero when paused — zero CPU at idle), `searchedAlbums`, `searchedArtists`, `searchedPlaylists`, `playlistArtTracks`.

### Playback (Two Layers)

- **`PlaybackService`** — `MediaSessionService` foreground service; singleton `ExoPlayer` via Hilt `MediaModule`.
- **`MusicPlayerImpl`** — singleton `MediaController` (created once in `init`). Polls position at 50 ms. Listens to `onMediaItemTransition` + `onTimelineChanged` to keep `_currentMediaItemIndex` in sync after reorder.
- **`MediaPreWarmer`** — pre-warms `PlaybackService` at `LaconicalApp.onCreate`; controller released immediately, ExoPlayer survives.
- **`AudioVisualizerManager`** — Android `Visualizer` on ExoPlayer's `audioSessionId`. Sine-wave fallback on silence. `@Volatile` on `isVisualizerGeneratingRealData`.
- **`WaveformExtractor`** — Amplituda via `ContentResolver.openInputStream(uri)`. `Mutex` serializes concurrent calls.

### Lyrics (feature/lyrics)

Chain: **memory LRU(100) → Room (`lyrics` table, DB v2) → local sources (embedded tags, sibling `.lrc`) → LRCLIB API (opt-in, default OFF)**. Inspired by PixelPlayerOSS + Namida (see `docs/superpowers/specs/2026-07-19-lyrics-retrieval-design.md`).

- `core:model/lyrics` — pure-JVM `LrcParser`, `Id3UsltParser` (Media3's Id3Decoder does NOT parse USLT; MP3 needs the hand-rolled parser), `currentLineIndex` binary search.
- `core:data/lyrics` — `LyricsRepositoryImpl` (persist Found/Instrumental, never NotFound), `LrcLibClient` (scored matching, never first-result), `LyricsSettingsStore` (DataStore `lyrics_settings`).
- **Dep edge `core:media → core:data`** (acyclic): `Media3EmbeddedFormatLyricsExtractor` implements the `core:data` port `EmbeddedFormatLyricsExtractor`. Media3 1.10: `MetadataRetriever.Builder` instance API; VorbisComment moved to `androidx.media3.extractor.metadata.vorbis`.
- **Privacy rule:** track changes run local chain only (`lyricsJob` in MainViewModel, same cancel-restart pattern as waveform/color); LRCLIB fires only from `openLyrics()`/`refreshLyrics()`.
- UI: `LyricsSheet.kt` is a lyrics **layer** — blurred-art background, top-bar chrome, big synced lyrics with a colorful active-line highlight, loading/message popup card, fade-in shuffle/repeat. Composed **inside** `QueueMorphLayer` (`LibraryScreen.kt`), below the morph overlay, mirroring `QueueSheet`'s relationship to it. It **is** part of the morph system: opening lyrics (`lyricsAnimatable`, mirrors `queueAnimatable`) is a `full → lyrics` stage-2 transition chained onto the existing `full → queue` lerp (`lerp(lerp(full, queue, queueProg), lyrics, lyricsProg)` per element) — mutually exclusive with queue, enforced at the two open triggers (`onShowQueue`/`onOpenLyrics` in `LibraryScreen`), not by branching the lerp math. Album art → thumbnail, title/artist → top-bar (left-aligned, no centered-text mode on `FadingMarqueeText`), prev/play/next → controls row all morph; shuffle/repeat fade in (no full-player position to travel from). The seek bar is the one new frozen anchor — `MorphAnchors` gained `seekBarLeftPx/TopPx/WidthPx/HeightPx`, reported by `FullPlayer`'s `VisualizerSeekBar` at collapsed rest same as art/title/controls; the overlay renders a second real `VisualizerSeekBar` lerping from that anchor into the lyrics view's bottom cluster, with drag disabled (`enabled` param) and the waveform-phase animation frozen mid-morph (reuses the existing `isPlaying && expandedFraction > 0.01f` gate). Lyrics-side target coordinates are computed from dp constants, not reported via new `onGloballyPositioned` calls — see Animation Pitfalls. Background blur is a hand-rolled Coil 3 `DownscaleBlurTransformation` (`components/BlurTransformation.kt`) since `Modifier.blur` is API 31+ only and Coil 3 has no built-in blur.
- Settings layout: **Privacy** nav row (mask icon, subtitle = privacy meter e.g. `Ultra Super High ❤️‍🔥` / `High 😀`) → **Miscellaneous** nav row (tune icon, subtitle = `Lyrics source priority`) → About → Statistics. Privacy submenu (`NavRoute.SETTINGS_PRIVACY`): privacy meter card at top (general, opt-in online features lower it via `PrivacyMeter.kt` tradeoffs) + per-feature pills (today: Lyrics online search). Miscellaneous submenu (`NavRoute.SETTINGS_MISCELLANEOUS`): lyrics source priority chips. Chrome fade (`onSettings`) covers all three settings routes.
- `.lrc` sibling is best-effort on API 29+ (scoped storage); works fully on API ≤ 28.

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
    LyricsSheet.kt             # Full-screen lyrics overlay (synced highlight, tap-to-seek)
    LaconicalBottomNav.kt      # Bottom nav with selection indicator pill
    NavTransitions.kt          # Directional slide transition specs (SLIDE_DURATION_MS = 250 ms, internal→exported)
    StaggeredEntrance.kt       # Staggered list-item entrance modifier
  ui/screens/
    AlbumsScreen.kt / AlbumDetailScreen.kt
    ArtistsScreen.kt / ArtistDetailScreen.kt
    FavoritesScreen.kt
    PlaylistsScreen.kt / PlaylistDetailScreen.kt
    SearchScreen.kt            # Search results overlay (inline in LibraryScreen)
    SettingsScreen.kt          # Full-screen settings — About card, Statistics card, dominant-color accent/separator
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
  db/entity/  FavoriteTrack, Playlist, PlaylistTrack, PlayHistory, LyricsEntity
  db/dao/     FavoriteDao, PlaylistDao, HistoryDao, LyricsDao
  db/MusicDatabase.kt          # v2; Migrations.kt holds MIGRATION_1_2
  di/DatabaseModule.kt / DataModule.kt / LyricsModule.kt / LyricsNetworkModule.kt
  lyrics/     LyricsRepository[Impl], EmbeddedLyricsSource, SiblingLrcSource,
              LrcLibClient, LrcLibScorer, LyricsSettingsStore, EmbeddedFormatLyricsExtractor
```

## Tech Stack

Kotlin + Jetpack Compose (M3) · Hilt · Media3/ExoPlayer · Room · Coil 3 (custom audio art fetcher) · Amplituda · Android `Visualizer` API · Palette API · minSdk 26 / targetSdk 35

## Design System

- All colors semantic via `LaconicalTheme` — no raw hex in component code.
- Dominant album art color tints UI via `playingTrackDominantColor`.
- `Color.toHsl()` defined once in `ColorUtils.kt` — no inline duplicates.
- All motion: compositor-only (`scale`, `alpha`, `offset` via `lerp`).
- **Settings accent color:** HSV from dominant hue, saturation 0.45, value 0.75 — vibrant but controlled.
- **Settings separator color:** HSV from dominant hue, saturation 0.15, value 0.40, alpha 0.80 — grayish tinted line.
- **Settings icon adaptive-icon caveat:** `R.mipmap.ic_launcher` resolves to an `<adaptive-icon>` XML on API 26+. `painterResource` cannot decode it (throws `IllegalArgumentException`). Rasterize via `ContextCompat.getDrawable(...).toBitmap(192, 192).asImageBitmap()` and display with `Image(bitmap = ...)` instead.

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

**TopBar decoupling for full-screen nav routes (Settings pattern — PR #48):** Do NOT place `LaconicalTopBar` in `BottomSheetScaffold`'s `topBar` slot when any `NavHost` route is full-screen (e.g. Settings). Root cause chain:
1. `BottomSheetScaffold` offsets its entire content area below the `topBar` slot height.
2. Conditionally composing/deconstructing the topBar (even with an alpha fade) causes the Scaffold to recompute content height, snapping the content by `topBarHeight` in one frame — visible as the Settings screen teleporting up/down at transition edges.
3. A secondary double-padding bug arises if you also manually add `topBarHeight` to the content Box: the Scaffold already offsets for the slot, so you pay the height twice.

**Correct pattern:**
- Leave `BottomSheetScaffold`'s `topBar` slot absent (or `null`).
- Render `LaconicalTopBar` as a `Box` overlay (`Alignment.TopCenter`) inside the Scaffold content lambda.
- Control visibility with `chromeAlpha: Animatable<Float>` — fade-only via `graphicsLayer { alpha = chromeAlpha.value }` (draw-time only, no layout reflow).
- Animate `chromeAlpha` in a `LaunchedEffect(onSettings)` synced to `SLIDE_DURATION_MS`.
- Apply a *static* `padding(top = topBarHeight)` where `topBarHeight = statusBarPadding + 60.dp` individually to every main-tab composable inside `NavHost`. Full-screen routes (Settings) do **not** get this padding — they handle `statusBarPadding` themselves.
- The `chromeAlpha` driver also gates `BottomNav` visibility (`if (chromeAlpha.value > 0f)`) and multiplies its `graphicsLayer` alpha (`navBarVisualAlpha * chromeAlpha.value`) so both chrome elements fade together with the transition.

## Visual Design

Aesthetic-first (Namida-inspired): dominant-color gradients/glows · particle effects on active track · morphing mini↔full player · pulsating album art from waveform amplitude · compositor-only animations.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
