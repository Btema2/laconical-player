# Changelog

All notable changes to Laconical Player are documented here.

## [1.2] - 2026-07-19 — "Lyrics at last"

### Added
- Synced lyrics display with active-line highlight and tap-to-seek
- Full-screen lyrics view with blurred album-art background, morphing in from the full player as a third transition stage (mini ⟷ full ⟷ queue ⟷ lyrics)
- Lyrics retrieval chain: embedded tags (ID3 USLT / Vorbis comment) → sibling `.lrc` file → [LRCLIB](https://lrclib.net) API (opt-in, off by default)
- Room persistence for fetched lyrics — a track is never looked up online twice
- Settings restructured into **Privacy** and **Miscellaneous** submenus; Privacy submenu shows a privacy meter and per-feature online opt-ins, Miscellaneous holds lyrics source priority

### Changed
- Dependency security bumps (BouncyCastle, Apache Commons, Netty)
- CI hardening: dependency-submission graph pinned to a SHA so Dependabot resolves actual versions

## [1.1] - 2026-07-16 — "Small one, but still worth a bump"

### Added
- Sorting (title / artist / duration) on Tracks, Albums, Artists, and Playlists, plus a shuffle-all button on each
- New Settings page (About / Statistics)
- Mini player gestures: swipe left/right to skip, swipe down to dismiss, swipe up (from anywhere on the full player) to open the queue
- Adaptive full-player layout via `BoxWithConstraints` for more screen sizes
- CI: automated lint/test/build plus dependency security scanning (CodeQL, dependency-submission)

### Fixed
- Track title invisible in light mode (forced white)
- Mini↔full player morph stutter and end-of-collapse jump, caused by a throttled `onGloballyPositioned` in newer Compose foundation — fixed by freezing ghost-element anchors at rest instead of reading live position every frame
- Android 16 (SDK 36) support and related dependency updates

## [1.0] - 2026-06-09 — First usable release

### Added
- Context menus, in-place playlist picker morph
- Playlists, and Favorites as a first-class playlist
- Fullscreen player wired up to all playback controls
- Fading/scrolling marquee text for long titles
- Search tab, Albums tab, Artists tab, Playlists tab
- Signed, minified release build

### Changed
- Numerous performance and small UI fixes

## [0.1.0.0] - 2026-05-03

### Added
- Full-screen search with grouped results (tracks, albums, artists, playlists), filter chips, and animated header that morphs in-place from the top bar — no NavHost navigation, no keyboard jank
- Directional tab slide transitions — forward tabs slide left, backward tabs slide right, with a 250ms input blocker to prevent double-taps during the animation
- Track context menu morphs into a playlist picker in-place using a mode state machine with smooth `animateContentSize` transition
- Playlist-added toast notification confirms when a track is added to a playlist
- Staggered entrance animation for track list items — 90ms snap-in vs. the previous 540ms delay

### Changed
- Dynamic color system: all screens now read `LocalAppBackground` / `LocalAppSurface` composition locals driven by the playing track's dominant album color, replacing static `MaterialTheme.colorScheme.background` throughout the app
- Bottom nav uses `LocalAppSurface`, has a selection indicator pill, and removes the radial gradient
- Top bar reads `LocalAppBackground`; Material You / dynamic color disabled in favor of the app's own tinting
- MiniPlayer background softened — uses `LocalAppSurface` base with a lighter transparent gradient
- Queue now plays from the context where a track was tapped (album, artist, playlist, search) rather than always from the global track list
- `TrackMenuOverlay` replaces the old `AddToPlaylistOverlay` and `TrackContextMenu` — unified component with playlist picker built in

### Fixed
- Queue reorder now mutates `_currentQueue` instead of `_allTracks` — reordering no longer corrupts the main library list
- Queue display grays out tracks before the current position
- Invisible sheet touch trap removed when no track is playing
- `StaggeredEntrance` `LaunchedEffect` key corrected; 540ms comment accurate
- `PlaylistPickerBody` wrapped in `Column` so `LazyColumn` + footer lay out correctly
- `MainMenuBody` missing `Column` wrapper fixed; easing switched to `LinearOutSlowInEasing`
- Album art fallback icon now consistent across all track list screens (TrackListItem, PlaylistDetailScreen)

### Performance
- Album art caching: all tracks in the same album now share one cache entry (keyed on MediaStore `albumArtUri`) instead of one per track — eliminates repeated `MediaMetadataRetriever` opens during fast scroll on albums with repeated thumbnails
- `AudioAlbumArtFetcher` fast path: tries `ContentResolver.openInputStream` on the MediaStore albumart URI before falling back to full audio-file tag parsing
- Added 50 MB disk cache and 15% memory cache to the global Coil `ImageLoader`
- `SubcomposeAsyncImage` replaced with `AsyncImage` in `TrackListItem`, `PlaylistDetailScreen`, and `SearchScreen` — lighter composition per row during fast scroll

## [0.5.0-alpha] - 2026-04-18

### Added
- "Up Next" queue tab with custom queue design
- Smoother, refined animations throughout

### Fixed
- Bug fixes and performance improvements

## [0.4.0-alpha] - 2026-04-11

### Added
- Full-screen player with custom design
- Playback visual effects
- Morphing animation from the mini player on swipe

## [0.3.0-alpha] - 2026-02-22

### Added
- Mini player showing the current track
- Status bar blends into the app background
- Prettier bottom navigation
- Particles on the track list pause when playback pauses

## [0.2.0-alpha] - 2026-02-22

### Added
- Full rewrite of track list rows using custom Compose Row/Box
- Dynamic Palette-based theming for track titles and the global background
- Radial particle system emitting from album art on the active track
- Neon glow effect (`BlurMaskFilter`) on playing-track artwork
- Custom bottom navigation with a translucent glass effect and bounce animations
- New permission request screen with dedicated dark-mode styling

## [0.1.0-alpha] - 2026-02-19

### Added
- Infrastructure preview — initial project scaffolding
