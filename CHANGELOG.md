# Changelog

All notable changes to Laconical Player are documented here.

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
