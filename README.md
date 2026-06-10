# 🎵 Laconical Player

A highly aesthetic, open-source Android music player. I built this because I care about the looks of my mp3 player and absolute privacy. 

Built with **Kotlin**, **Jetpack Compose (Material 3)**, and **Android Media3 (ExoPlayer)**.

---

## 📸 Screenshots

<p align="center">
  <img src="otherAssets/Screenshot_20260609-150720_Laconical Player.png" width="31%" alt="Full Player UI" />
  <img src="otherAssets/Screenshot_20260609-150706_Laconical Player.png" width="31%" alt="Main Tracks List" />
  <img src="otherAssets/Screenshot_20260609-150711_Laconical Player.png" width="31%" alt="Artist/Album Detail" />
</p>

<details>
  <summary><b>Show More Screens (Queue, Mosaics, Settings)</b></summary>
  <br>
  <p align="center">
    <img src="otherAssets/Screenshot_20260609-150724_Laconical Player.png" width="23%" alt="Queue Sheet" />
    <img src="otherAssets/Screenshot_20260609-150854_Laconical Player.png" width="23%" alt="Album Cover Mosaic" />
    <img src="otherAssets/Screenshot_20260609-150934_Laconical Player.png" width="23%" alt="Search Interface" />
    <img src="otherAssets/Screenshot_20260609-151022_Laconical Player.png" width="23%" alt="Settings & Toggles" />
  </p>
</details>

---

## ✨ Features (What makes it good?)

* **Namida-Inspired Aesthetics**  
  A UI that is actually alive. The player extracts the dominant color palette from your current track's album art on-the-fly and paints the interface with smooth gradients and glows.
* **Morphing Player Transition**  
  A gorgeous custom 3-phase transition (MiniPlayer ⟷ FullPlayer ⟷ Queue Sheet) built using custom coordinate interpolation (lerp). No sluggish Android shared-element APIs here.
* **Dual-Layer Waveform seeking**  
  Two separate visualization layers: a static pre-decoded waveform seekbar powered by Amplituda, and a real-time audio visualizer driven by the Android `Visualizer` API. (Fails safe to a smooth sine-wave when silent so it never looks dead).
* **Physics Particles**  
  Floating particle fields on active track list rows and the full player, tied directly to playback state and audio amplitude.
* **60fps Pulsating Art**  
  The album artwork scales and pulses in real time, synced perfectly to decoded waveform amplitude and playback.
* **Offline & Privacy-First**  
  No trackers, no analytics, no network permissions. Your music stays on your device.
* **Zero Memory Leaks**  
  Under the hood, we use serialized database operations, scoped coroutine lifecycles, and a single process-lifetime `MediaController` so the app won't hog your background RAM.

---

## 🛠️ Build It Yourself

Laconical Player is fully compilable locally. 

### Prerequisites
- **Android Studio Ladybug** (or newer)
- Android SDK 35/36 installed

### Build Command
Compile the debug APK with:
```bash
./gradlew assembleDebug
```
The resulting APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🧠 The "Vibe-Coding" Story
> This project has been heavily vibe-coded. I know more about music aesthetics and UI design than I do about the deep internals of the Android SDK. 
> 
> It started as a hobby project to learn and experiment. But as the architecture grew, I leaned on **Claude Code** and **Google Antigravity** to write the heavy-lifting, handle memory-safety, and wire up the low-level Android APIs. If the codebase looks like a sophisticated AI wrote it—it's because it did. I just make sure the music feels right.

---

## 📜 License
This project is licensed under the [GNU General Public License v3.0](LICENSE).