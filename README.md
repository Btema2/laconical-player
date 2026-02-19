# Laconical Player

A modern, FOSS, local-first music player for Android built with Jetpack Compose and Media3. Inspired by Namida aesthetics.

## Features
- **Modern UI:** Built entirely with Jetpack Compose using Material 3 and following Namida aesthetics.
- **Media3 Integration:** High-performance, production-ready audio playback service extending `MediaSessionService`.
- **Local Media Scanning:** Efficient `MediaStore` repository implementation to fetch and manage local audio files.
- **Clean Architecture:** Strictly modularized design (UI, Core Model, Core Data, Core Media) with Hilt for dependency injection.
- **Modern Build System:** Powered by AGP 9.0 and Gradle 9.1 with built-in Kotlin support and JVM 21.

## Tech Stack
- **Language:** Kotlin 2.2
- **UI:** Jetpack Compose (Material 3)
- **Dependency Injection:** Hilt
- **Database:** Room (Initialized)
- **Media Playback:** Media3 1.6.0 (ExoPlayer)
- **Build System:** AGP 9.0.1, Gradle 9.1

## Project Status
**Pre-alpha:** Infrastructure, foundational setup, and core playback services are complete. The app can now scan for local media items and initiate playback in the background.

## How to Build
To build the debug APK, run:
```bash
./gradlew assembleDebug
```

## License
TBD (FOSS)
