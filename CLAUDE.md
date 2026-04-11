# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**Code quality:** Write modern, good quality code. Use context7 mcp for up-to-date documentation and leverage availbalse skills for better code quality.

## Project Overview

Laconical Player is a fully open-source Android music player inspired by Namida Player's aesthetic. It plays local audio files with a visually rich UI featuring morphing transitions, particle effects, waveform visualization, and dominant-color theming.

## Build Commands

```bash
# Debug build
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/

# Clean build
./gradlew clean assembleDebug

# Install on connected device
./gradlew installDebug

# Default AGP lint
./gradlew lint
```

No custom test suite is wired up — only boilerplate JUnit4 dependencies are declared.

## Module Structure

Multi-module Gradle project (Kotlin DSL):

| Module | Role |
|--------|------|
| `:app` | UI layer — single `LibraryScreen`, `MainViewModel`, `MainActivity` |
| `:core:model` | `Track` data class only |
| `:core:data` | `MediaRepository` interface + `LocalMediaRepositoryImpl` (MediaStore queries) |
| `:core:media` | `MusicPlayer`, `PlaybackService`, `AudioVisualizerManager`, `WaveformExtractor` |
| `:core:designsystem` | `LaconicalTheme` (Material 3), Color, Type tokens |

## Architecture

### State Management

All state lives in a single `@HiltViewModel` — `MainViewModel`. No navigation component is used; the entire app is one screen (`LibraryScreen`) in `MainActivity.setContent`.

Key `StateFlow`s in `MainViewModel`:

- `tracks` — MediaStore audio, filtered by `searchQuery` via `combine()`
- `currentTrack` — currently playing `Track?`
- `playingTrackDominantColor` — extracted by Palette API on every track change
- `isPlaying`, `currentPosition`, `duration`, `progress` — delegated from `MusicPlayerImpl`
- `waveform`, `beatPulse` — from `AudioVisualizerManager` (real-time)
- `_waveformData` — static waveform from Amplituda (decoded on track change)
- `_currentNormalizedAmplitude` — amplitude ticker scrubbing `_waveformData` by playback position at ~60 fps while playing; suspends via `isPlaying.first { it }` when paused and amplitude decays to zero (zero CPU wakeups at idle); drives album art pulse

### Playback System (Two Layers)

**`PlaybackService`** — `MediaSessionService` foreground service holding a singleton `ExoPlayer` (Hilt-injected via `MediaModule`). Handles background playback and audio focus.

**`MusicPlayerImpl`** — Holds a singleton `MediaController` connected to `PlaybackService` over Media3 IPC (created once in `init`, not recreated on each call — fixes the original MediaController leak). All commands (`play`, `pause`, `seekTo`, `playMediaItem`) delegate to this controller. Position is polled at 50 ms intervals.

**`AudioVisualizerManager`** — Attaches Android `Visualizer` to ExoPlayer's `audioSessionId` for real-time waveform capture. Falls back to a sine-wave fake animation if the visualizer reports silence. Uses `@Volatile` on the `isVisualizerGeneratingRealData` flag to ensure cross-thread visibility between the audio callback thread and `Dispatchers.Default`.

**`WaveformExtractor`** — Uses the Amplituda library to decode the whole audio file into `List<Int>` amplitude data for the static seek-bar waveform. Accepts a content `Uri`; uses `ContentResolver.openInputStream(uri)` internally (Amplituda 2.3.1 has no `Uri` overload). A `Mutex` serializes concurrent calls so rapid track switching never corrupts state.

### The Morphing Player Transition

The architectural centerpiece of `LibraryScreen` is the mini→full player morph. There is **no** shared element transition API used. Instead:

- `BottomSheetScaffold` drives `expandedFraction` (0f = collapsed, 1f = expanded).
- Ghost/invisible placeholder elements in `MiniPlayer` and `FullPlayer` report their `positionInRoot()` via `onGloballyPositioned` callbacks.
- A single overlay `Box` (album art, title text, playback buttons) uses `lerp()` on `expandedFraction` to interpolate position and size between the two sets of coordinates, landing pixel-perfectly on the ghost elements.

### Album Art Loading

Custom Coil 3 pipeline: `AudioAlbumArtFetcher` + `AudioAlbumArtKeyer` (currently defined in `MainViewModel.kt`, logically misplaced). Uses `MediaMetadataRetriever` to extract embedded album art from audio files, bypassing Coil's default content-URI handler. The global `ImageLoader` is registered in `LaconicalApp`.

## Key File Locations

```
app/src/main/kotlin/com/laconical/player/
  LaconicalApp.kt          # @HiltAndroidApp, global Coil ImageLoader

app/src/main/java/com/laconical/player/ui/
  MainViewModel.kt         # All state + orchestration logic
  LibraryScreen.kt         # Entire app UI, BottomSheetScaffold, morph overlay
  ColorUtils.kt            # Shared Color.toHsl() extension (single source of truth)
  components/
    FullPlayer.kt          # Expanded player, VisualizerSeekBar, ParticleSystem
    MiniPlayer.kt          # Collapsed strip, morphing GlowIconButton controls
    TrackListItem.kt       # Per-row Palette extraction + ParticlesEffectCanvas
    ParticlesEffectCanvas.kt  # Physics particles — mutations in LaunchedEffect, draw is pure

core/media/.../
  MusicPlayer.kt           # Interface + MusicPlayerImpl (singleton MediaController)
  PlaybackService.kt       # MediaSessionService foreground service
  AudioVisualizerManager.kt  # Real-time Visualizer + sine-wave fallback
  WaveformExtractor.kt     # Amplituda via ContentResolver.openInputStream(Uri)

core/data/.../
  LocalMediaRepositoryImpl.kt  # MediaStore queries — uses content URIs, no DATA column
```

## Tech Stack

- Kotlin + Jetpack Compose (Material 3)
- Hilt (DI)
- Media3 / ExoPlayer (playback + MediaSessionService)
- Room (declared, not yet used)
- Coil 3 (image loading with custom audio art fetcher)
- Amplituda (static waveform extraction)
- Android `Visualizer` API (real-time waveform)
- Palette API (dominant color extraction)
- minSdk 26, targetSdk/compileSdk 35

## Design System

All UI components are built against `LaconicalTheme` from `:core:designsystem`. Design constraints:
- Color palette is semantically driven — never hardcoded raw hex values in component code.
- Dominant album art color tints the UI via `playingTrackDominantColor` passed through the component tree.
- Typography and spacing come from Material 3 tokens defined in `core/designsystem`.
- All motion uses compositor-friendly properties only (`scale`, `alpha`, `offset` via `lerp`).
- `Color.toHsl()` is defined once in `ColorUtils.kt` (`com.laconical.player.ui`) and imported wherever HSL conversion is needed — no inline duplicates.

## Memory Safety

Zero known memory leaks. Key properties:
- `MusicPlayerImpl` holds one `MediaController` for the process lifetime (not recreated per-call).
- `waveformJob` and `colorJob` in `MainViewModel` are cancelled before a new one starts on track change.
- `WaveformExtractor.mutex` prevents concurrent Amplituda calls, which is not thread-safe.
- `@Volatile` on `AudioVisualizerManager.isVisualizerGeneratingRealData` ensures the audio thread's writes are visible on `Dispatchers.Default`.
- `ParticlesEffectCanvas` mutates particle state only inside `LaunchedEffect` — never inside the `Canvas` draw phase.

## Visual Design Principles

The app is intentionally aesthetic-first, inspired by Namida Player:
- Dominant color extracted from album art drives background gradients and glow effects.
- Particle effects on the active track row and full player.
- Morphing shared-element-style transitions between mini and full player.
- Pulsating album art driven by waveform amplitude.
- All animations use compositor-friendly properties (transform, opacity, scale).
