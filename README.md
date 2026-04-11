# 🎵 Laconical Player

A modern, privacy-focused, FOSS music player for Android. Built with Jetpack Compose and Media3, inspired by Namida's aesthetics and Material 3 simplicity.

## Features
- **Namida-Inspired Aesthetics:** Dynamic UI that responds to the currently playing track's color palette, with per-track dominant color extraction driving gradients and glow effects.
- **Morphing Mini→Full Player:** Custom morph transition with no shared-element API — lerp-based overlay interpolates position and size between collapsed and expanded states.
- **Live Waveform Visualization:** Two layers — static per-track waveform from Amplituda for the seek bar, and real-time audio capture via Android `Visualizer` for the full-player visualizer.
- **Particle System:** Physics-based floating particles tied to playback state on the active track row and full player.
- **Pulsating Album Art:** Album art scale driven by decoded waveform amplitude, synced to playback position at 60 fps.
- **Search:** Real-time filtering of local library tracks.
- **High-Performance Audio:** Powered by Android Media3 (ExoPlayer) with reliable background playback via a foreground `MediaSessionService`.
- **Design System:** All UI components follow the `LaconicalTheme` design system — Material 3 tokens, consistent color semantics, and compositor-friendly animations throughout.
- **Zero Known Memory Leaks:** Singleton `MediaController`, scoped coroutine jobs, `@Volatile` cross-thread flags, and `Mutex`-serialized audio extraction eliminate the leak surface.
- **Privacy First:** Offline, no trackers, no internet required.

## Tech Stack
- **UI:** Jetpack Compose (Material 3), `LaconicalTheme` design system
- **Images:** Coil 3 with a custom `AudioAlbumArtFetcher` for embedded album art
- **Colors:** Android Palette API for dominant color extraction
- **Audio Engine:** Media3 / ExoPlayer (playback + `MediaSessionService`)
- **Waveform:** Amplituda (static, per-file) + Android `Visualizer` API (real-time)
- **Dependency Injection:** Hilt
- **Architecture:** Multi-module Gradle project (Kotlin DSL), single-screen Compose UI, `HiltViewModel` for all state.

## Build it yourself
1. Install **Android Studio Ladybug** (or newer) to get the required SDK 35/36.
2. Clone this repo
3. Run: `./gradlew assembleDebug`
4. Find the APK at `app/build/outputs/apk/debug/`

<br>

> **A Note on "Vibe-Coding"**
> This project has been basically written by vibe-coding. I know more about music aesthetics and UI vibes than I do about the deep internals of the Android SDK. It started out as my typical "research and figure it out" programming, but then I leaned on **Claude Code** (and occasionally Google Antigravity) to architect and write the heavy lifting. If the code looks like a sophisticated AI wrote it, it's because it did. I just make sure it feels right.

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports and PRs welcome.

## 📜 License
Licensed under the [GNU General Public License v3.0](LICENSE).