# Laconical Player — Next Steps Roadmap

**Date:** 2026-04-18
**Goal:** Personal daily-driver music player, open-source GitHub portfolio project.
**Strategy:** Architecture-led (Approach B) — wire Room + Navigation first, then build features on top cleanly.

---

## Current State

### Working
- ExoPlayer + MediaSessionService background playback
- Queue management: shuffle, repeat, drag-to-reorder
- MediaStore audio scanning + search
- Real-time waveform visualizer + static seek-bar waveform
- Album art extraction + Palette dominant color theming
- 3-phase morphing transition (mini → full → queue)
- Particle effects, pulsating album art
- Material 3 design system

### Dead / Stub (UI present, no logic)
- Bottom nav tabs: Albums, Artists, Playlists — renders but does nothing
- Track context menu (TODO in TrackListItem.kt:214)
- Settings button (TODO in LaconicalTopBar.kt:161)

### Declared but unused deps
- Room 2.6.1 (no entities/DAOs written)
- Navigation Compose 2.8.5 (no NavHost/NavController)
- Media3 UI Compose (no usage)
- Coil video (no usage)

---

## Phased Plan

### Phase 1 — Architecture Foundation ✅ AGREED
_Goal: Unlock all persistence and multi-screen features cleanly._

**Room DB (`core:data`):**
- Entities: `Playlist`, `PlaylistTrack` (join), `FavoriteTrack`, `PlayHistory`
- DAOs: `PlaylistDao`, `FavoriteDao`, `HistoryDao`
- `MusicDatabase` (Room database class)
- `UserDataRepository` interface + `UserDataRepositoryImpl`
- Hilt binding in `DataModule`

**Navigation (`app`):**
- `NavHost` inside `LibraryScreen` (above mini player, below top bar)
- Routes: `tracks`, `albums`, `artists`, `playlists` (stub)
- Bottom nav wired to NavController
- Morph overlay and BottomSheetScaffold untouched

---

### Phase 2 — Core Daily-Use Features
_Goal: Fix the most frustrating daily-use gaps._

- **Sorting & filtering** — sort track list by: title, artist, duration, date added (ViewModel only, no persistence)
- **Albums view** — grid of albums, tap opens album track list
- **Artists view** — list of artists, tap opens artist track list
- **Track context menu** — long-press or ⋮ button: Add to playlist, Add to favorites, View album, View artist
- **Favorites** — heart toggle per track, Favorites smart playlist (Room-backed)

---

### Phase 3 — Playlist Management
_Goal: Full user-defined playlist CRUD._

- Create / rename / delete playlists
- Add/remove tracks from playlists
- Playlist detail screen (track list, play all, shuffle)
- Playlists tab in bottom nav (replaces stub)

---

### Phase 4 — Settings Screen
_Goal: Make the app configurable._

- Theme: dark / light / follow system
- Default sort order
- Auto-play on launch toggle
- Skip silence toggle
- About screen (version, GitHub link, licenses)

---

### Phase 5 — Polish & Portfolio
_Goal: Impress GitHub visitors, improve daily use quality-of-life._

- Play history (recently played list, play counts)
- Sleep timer
- Equalizer (10-band + presets via Android AudioEffect API)
- Lyrics display (local .lrc files or embedded tags)
- Fast scroll index overlay for large libraries
- Gapless / crossfade playback
- Remove unused deps (coil-video, media3-ui-compose, appcompat, google-material)
- README + screenshots for GitHub

---

## Architecture Notes

- All new screens go in `app/src/main/java/com/laconical/player/ui/screens/`
- All new Room code goes in `core/data`
- `MainViewModel` stays as playback orchestrator; add per-screen ViewModels for Albums, Artists, Playlists
- `UserDataRepository` is the single source of truth for favorites, playlists, history
- Navigation does NOT affect the morph overlay — it only switches the content above the mini player

---

## Status

- [x] Phase 1 design agreed
- [ ] Phase 2–5 design in progress (brainstorming session)
