# 🎵 Laconical Player

A modern, privacy-focused, FOSS music player for Android. Built with Jetpack Compose and Media3, inspired by Namida's aesthetics and Material 3 simplicity.

## Features
- **Modular Architecture:** Cleanly separated Core-Media, Core-Data, and UI modules.
- **MediaStore Integration:** Scans your device for music automatically.
- **Background Playback:** High-performance audio engine using Media3.
- **Modern UI:** Built entirely with Jetpack Compose.
- **Privacy First:** No internet, no trackers, strictly offline.

## Tech Stack
- **Language:** Kotlin 2.2+
- **Build System:** AGP 9.0.1 + Gradle 9.1.0 (Built-in Kotlin support)
- **Audio Engine:** Media3 (ExoPlayer)
- **Dependency Injection:** Hilt 2.59.1
- **UI:** Jetpack Compose (Material 3)

## How to Build
1. Install **Android Studio Ladybug** (or newer) to get the required SDK 35/36.
2. Clone this repo
3. Run: `./gradlew assembleDebug`
4. Find the APK at `app/build/outputs/apk/debug/`

<br>

> **A Note on "Vibe-Coding"**
> This project has been basically written by vibe-coding. I know more about music aesthetics and UI vibes than I do about the deep internals of the Android SDK. It started out as my typical "research and figure it out" programming, but then I cut out the and just used **Google Antigravity** to architect and write the heavy lifting. If the code looks like a sophisticated AI wrote it, it's because it did. I just make sure it feels right.

## 📜 License
Licensed under the [GNU General Public License v3.0](LICENSE).