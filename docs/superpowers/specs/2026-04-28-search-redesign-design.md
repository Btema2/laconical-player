# Search Redesign — Design Spec

**Date:** 2026-04-28
**Branch:** bugfixes
**Status:** Approved

---

## Overview

Replace the inline search toggle inside `LaconicalTopBar` with a dedicated full-screen search destination. The search screen opens when the user taps the search icon in the top bar, supports cross-entity search (Tracks, Artists, Albums, Playlists) with grouped results, and closes via system back gesture or a back arrow button.

---

## 1. Navigation & Entry/Exit

### Route
- Add `const val SEARCH = "search"` to `NavRoute`.
- Register `composable(NavRoute.SEARCH) { SearchScreen(...) }` inside the existing `NavHost` in `LibraryScreen`.

### Opening
- Tapping the search icon in `LaconicalTopBar` calls `navController.navigate(NavRoute.SEARCH)`.
- `LaconicalTopBar` reverts to its simple state permanently — no more inline search toggle. It always shows: app title + search icon + settings icon.

### Closing
- System back gesture pops the backstack automatically (no `BackHandler` needed).
- The back arrow button (outside the pill) calls `navController.popBackStack()` and clears the search query.

### Bottom nav & top bar visibility
- Both the bottom nav bar and `LaconicalTopBar` are hidden when `currentRoute == NavRoute.SEARCH`, using the same route-detection logic already used for `album_detail`, `artist_detail`, and `playlist_detail`.

### Enter/exit transitions
- Enter: slide down from top (~300ms, `EaseOutQuart`), overriding the default horizontal slide for this route only.
- Exit: slide back up on pop (~300ms, same curve).

---

## 2. Top Bar — Color & Spacing

### Spacing fix
- Remove the animated `statusBarHeight * animatedPaddingScale` padding (scale oscillates 0.7×–1.1×, clips into status bar at minimum).
- Replace with a constant `padding(top = statusBarHeight + 4.dp)` — fixed 4dp breathing room below the system status bar.

### Background color
- `containerColor` is derived from `playingTrackDominantColor: Color?` (already in `MainViewModel`).
- When non-null: darken the dominant color to ~15–20% luminosity using `Color.toHsl()` from `ColorUtils.kt`.
- When null (nothing playing): fall back to `Color(0xFF1A1A1A)` — neutral dark with slight warm gray.
- Transition: `animateColorAsState(tween(400ms))` so color cross-fades smoothly when the track changes.
- `LaconicalTopBar` receives one new param: `dominantColor: Color?`, and derives `containerColor` internally.

---

## 3. Search Screen Layout (`SearchScreen.kt`)

**Location:** `app/src/main/java/com/laconical/player/ui/screens/SearchScreen.kt`

### Structure (top to bottom)
1. **Search bar row** — pinned, respects `WindowInsets.statusBars` padding.
2. **Filter chip row** — horizontally scrollable, below the search bar.
3. **Content area** — `LazyColumn`, fills remaining screen.

### Search bar row
Three elements in a horizontal row:

- **Left (outside pill):** `IconButton` with a left-pointing back arrow. Always visible. Taps `navController.popBackStack()` and clears query + resets active chip to `All`.
- **Center (the pill):** Fills remaining width. `BasicTextField` inside a pill-shaped container. Placeholder: `"Search"` when empty. Cursor and typed text inside.
  - Right end of pill interior: an X `IconButton` that clears text and resets chip to `All`. Shown only when text is present (`AnimatedVisibility` fade in/out).
- **Right:** Nothing — the X lives inside the pill.

### Keyboard
- `FocusRequester` attached to the `BasicTextField`.
- `LaunchedEffect(Unit)` requests focus on composition, auto-raising the keyboard.

### Background
- Same dominant-color derivation as the top bar (same darkened color, same `animateColorAsState`). The search screen receives `dominantColor: Color?` as a param.

---

## 4. Filter Chips & Result Sections

### Filter chips
- Options: `All · Tracks · Artists · Albums · Playlists`
- Horizontally scrollable `LazyRow` using the existing `FilterChip` style (same as sort chips on Tracks screen).
- Selected chip uses dominant color highlight (same `selectedContainerColor` pattern).
- Only one chip active at a time. `All` is selected by default.
- Selection state is local to `SearchScreen` — no ViewModel needed.

### "All" view (default)
`LazyColumn` with sections in this order: **Albums → Artists → Tracks → Playlists**.

Each section header row:
- Left: icon + `"SectionName • count"` (e.g. `Albums • 4`)
- Right: `"View All"` pill button — tapping activates that section's chip and switches to single-category view.

Section layouts:
- **Albums:** Horizontal `LazyRow` of art cards (album art + title + track count beneath). Tapping navigates to `NavRoute.albumDetailRoute(albumName)`.
- **Artists:** Horizontal `LazyRow` of circular avatars + artist name beneath. Tapping navigates to `NavRoute.artistDetailRoute(artistName)`.
- **Tracks:** Standard vertical `TrackListItem` rows (reusing existing component). Tapping plays the track.
- **Playlists:** `TrackListItem`-style rows with mosaic art. Tapping navigates to playlist detail.

Sections with zero results are hidden entirely (`AnimatedVisibility`).

### Single-category views (chip selected)
Full vertical `LazyColumn` for that type only — no section header, no "View All":
- **Tracks:** List of `TrackListItem` rows.
- **Playlists:** List of playlist rows.
- **Artists:** 2-column `LazyVerticalGrid` of circular avatars + name.
- **Albums:** 2-column `LazyVerticalGrid` of art cards + title.

### ViewModel changes
Three new `StateFlow`s added to `MainViewModel`, following the existing `tracks` combine pattern:

```kotlin
val searchedArtists: StateFlow<List<String>>   // distinct artists filtered by _searchQuery
val searchedAlbums: StateFlow<List<String>>    // distinct albums filtered by _searchQuery
val searchedPlaylists: StateFlow<List<Playlist>> // playlists filtered by _searchQuery on name
```

`SearchScreen` collects all four (`tracks`, `searchedArtists`, `searchedAlbums`, `searchedPlaylists`).

Bottom padding: same `trackListBottomPadding` pattern as other screens (mini player height + bottom nav height + safe insets — though bottom nav is hidden on this screen, mini player padding still applies when keyboard is down).

---

## 5. Animations

### Screen enter/exit
- Enter: `slideInVertically { -it }` + `fadeIn` (~300ms, `EaseOutQuart`).
- Exit: `slideOutVertically { -it }` + `fadeOut` (~300ms).
- Overrides the default horizontal `navEnterTransition`/`navExitTransition` for the `search` route only, via the composable's `enterTransition`/`exitTransition` params.

### Top bar color transition
- `animateColorAsState(tween(400ms))` when `playingTrackDominantColor` changes.

### Filter chip selection
- Built-in Material 3 `FilterChip` animated color swap — no extra work.

### Result sections
- Each section uses `AnimatedVisibility(fadeIn + slideInVertically { it / 2 })` when appearing.
- Staggered by section order using existing `staggeredEntrance` modifier — Albums first, then Artists, then Tracks, then Playlists.

### Clear X inside pill
- `AnimatedVisibility(fadeIn / fadeOut)` — appears when `text.isNotEmpty()`, disappears when cleared.

### Mini player & keyboard
- In `LibraryScreen`, observe `WindowInsets.isImeVisible` and `currentRoute == NavRoute.SEARCH`.
- When both are true: `sheetPeekHeight = 0.dp` (mini player slides down and hides).
- When either is false: `sheetPeekHeight` restores to its normal value (mini player slides back up).
- Transition: `animateDpAsState(tween(200ms))` for smooth slide.

---

## Files Changed / Created

| File | Change |
|------|--------|
| `ui/navigation/NavRoute.kt` | Add `SEARCH = "search"` |
| `ui/components/LaconicalTopBar.kt` | Remove inline search toggle; add `dominantColor: Color?` param; fix spacing; derive container color |
| `ui/LibraryScreen.kt` | Register search route; hide top bar + bottom nav on search; IME-aware mini player peek height |
| `ui/screens/SearchScreen.kt` | **New file** — full search screen composable |
| `ui/MainViewModel.kt` | Add `searchedArtists`, `searchedAlbums`, `searchedPlaylists` StateFlows |

---

## Out of Scope

- Search history / recent searches (empty state when nothing typed).
- Server-side or streaming search.
- Sorting within search results.
