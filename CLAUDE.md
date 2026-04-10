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
- `_currentNormalizedAmplitude` — 60 fps ticker scrubbing `_waveformData` by playback position; drives album art pulse

### Playback System (Two Layers)

**`PlaybackService`** — `MediaSessionService` foreground service holding a singleton `ExoPlayer` (Hilt-injected via `MediaModule`). Handles background playback and audio focus.

**`MusicPlayerImpl`** — Creates a `MediaController` connected to `PlaybackService` over Media3 IPC. All commands (`play`, `pause`, `seekTo`, `playMediaItem`) delegate to this controller. Position is polled at 50 ms intervals.

**`AudioVisualizerManager`** — Attaches Android `Visualizer` to ExoPlayer's `audioSessionId` for real-time waveform capture. Falls back to a sine-wave fake animation if the visualizer reports silence.

**`WaveformExtractor`** — Uses the Amplituda library to decode the whole audio file into `List<Int>` amplitude data for the static seek-bar waveform.

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
  MainActivity.kt          # Entry point, edge-to-edge setup

app/src/main/java/com/laconical/player/ui/
  MainViewModel.kt         # All state + orchestration logic
  LibraryScreen.kt         # Entire app UI (~520 lines), BottomSheetScaffold
  components/
    FullPlayer.kt          # Expanded player, VisualizerSeekBar, ParticleSystem
    MiniPlayer.kt          # Collapsed strip, GlowIconButton controls
    TrackListItem.kt       # Per-row Palette extraction + ParticlesEffectCanvas
    ParticlesEffectCanvas.kt

core/media/.../
  MusicPlayer.kt           # Interface + MusicPlayerImpl (MediaController)
  PlaybackService.kt       # MediaSessionService foreground service
  AudioVisualizerManager.kt
  WaveformExtractor.kt
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

## Visual Design Principles

The app is intentionally aesthetic-first, inspired by Namida Player:
- Dominant color extracted from album art drives background gradients and glow effects.
- Particle effects on the active track row and full player.
- Morphing shared-element-style transitions between mini and full player.
- Pulsating album art driven by waveform amplitude.
- All animations use compositor-friendly properties (transform, opacity, scale).
