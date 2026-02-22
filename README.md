# 🎵 Laconical Player

A modern, privacy-focused, FOSS music player for Android. Built with Jetpack Compose and Media3, inspired by Namida's aesthetics and Material 3 simplicity.

## Features
- **Namida-Inspired Aesthetics:** Dynamic UI that responds to the currently playing track's color palette.
- **Search:** Real-time filtering of local library tracks.
- **High-Performance Audio:** Powered by Android Media3 (ExoPlayer) with reliable background playback.
- **Privacy First:** Offline, no trackers, no internet required.

## Tech Stack
- **UI:** Jetpack Compose (Material 3)
- **Networking/Images:** Coil 3 (Custom implementation for local data)
- **Colors:** Android Palette API
- **Audio Engine:** Media3 (ExoPlayer)
- **Dependency Injection:** Hilt 2.59.1
- **Architecture:** Multi-module project with clean separation of concerns.

## Build it yourself
1. Install **Android Studio Ladybug** (or newer) to get the required SDK 35/36.
2. Clone this repo
3. Run: `./gradlew assembleDebug`
4. Find the APK at `app/build/outputs/apk/debug/`

<br>

> **A Note on "Vibe-Coding"**
> This project has been basically written by vibe-coding. I know more about music aesthetics and UI vibes than I do about the deep internals of the Android SDK. It started out as my typical "research and figure it out" programming, but then I cut out the and just used **Google Antigravity** to architect and write the heavy lifting. If the code looks like a sophisticated AI wrote it, it's because it did. I just make sure it feels right.

## 📜 License
Licensed under the [GNU General Public License v3.0](LICENSE).