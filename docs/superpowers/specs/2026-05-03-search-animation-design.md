# Search Animation Redesign

**Date:** 2026-05-03  
**Branch:** bugfixes  
**Status:** Approved — ready for implementation

## Problem

Current search opens via `NavRoute.SEARCH` inside `NavHost`, triggering a `slideInVertically` navigation transition (slides from top). Library content disappears abruptly as the new route composable replaces it. No spatial continuity between search icon and search bar.

## Solution

Replace navigation-based search with an **in-place animated search bar** that expands from the search icon position. Library content fades out with a subtle scale-down. No navigation event — `isSearchOpen: Boolean` state lives in `LibraryScreen`.

---

## Animation Choreography

### Open (tap search icon → ~380ms total)

| Element | Animation | Duration | Easing | Delay |
|---|---|---|---|---|
| Title text | Fade out + `translateX(-24px)` | 300ms | FastOutSlowIn | 0ms |
| Settings icon | Fade out + `scale(0.7)` | 220ms | FastOutSlowIn | 0ms |
| Search icon | Fade out | 150ms | Linear | 0ms |
| Search bar | Width expands from 36px → full (`left:8 right:8`), fade in | 380ms | FastOutSlowIn | 0ms |
| Back arrow (inside bar) | Fade in | 180ms | Linear | 240ms |
| Placeholder text | Fade in | 180ms | Linear | 290ms |
| NavHost content | Fade out + `scale(0.96)`, transform-origin top center | 300ms | FastOutSlowIn | 0ms |
| Search results overlay | Fade in | 250ms | FastOutSlowIn | 100ms |
| Result rows | Stagger fade-in + `translateY(8px→0)` | 200ms each | FastOutSlowIn | +60ms per row |

Keyboard (`FocusRequester.requestFocus()`) fires in `LaunchedEffect(isSearchOpen)` when `isSearchOpen = true`.

### Close (back arrow tap or system back)

All animations reversed, 200ms duration. Query cleared. Keyboard dismissed.

---

## Architecture

### Files changed

**`LaconicalTopBar.kt`** — modified  
- New params: `isSearchOpen: Boolean`, `searchQuery: String`, `onSearchOpen: () -> Unit`, `onSearchClose: () -> Unit`, `onQueryChange: (String) -> Unit`  
- `Animatable(0f)` drives `expandProgress` (0=collapsed, 1=expanded)  
- `LaunchedEffect(isSearchOpen)` animates to 0f or 1f  
- `FocusRequester` declared here; `requestFocus()` on open  
- Title, settings icon, search icon: `graphicsLayer { alpha = lerp(...); translationX = lerp(...) }` driven by `expandProgress`  
- Search bar: `width = lerp(36.dp, fullWidth, expandProgress)`, positioned via `onGloballyPositioned` of search icon  
- Back arrow + placeholder inside bar: separate `alpha = lerp(0f, 1f, ((expandProgress - 0.65f) / 0.35f).coerceIn(0f, 1f))`

**`LibraryScreen.kt`** — modified  
- Add `var isSearchOpen by remember { mutableStateOf(false) }` (replaces `isOnSearch` nav check)  
- Pass `isSearchOpen` + handlers to `LaconicalTopBar`  
- Wrap `NavHost` box in `graphicsLayer { alpha = lerp(1f, 0f, contentFadeProgress); scaleX = lerp(1f, 0.96f, contentFadeProgress); scaleY = scaleX }` — `contentFadeProgress` is `Animatable` driven by `isSearchOpen`  
- `AnimatedVisibility(isSearchOpen, enter=fadeIn(tween(250, 100)), exit=fadeOut(tween(200)))` wraps `SearchScreen`  
- `BackHandler(isSearchOpen) { isSearchOpen = false }` replaces nav back  
- Remove `topBar = { if (!isOnSearch) LaconicalTopBar(...) }` conditional — TopBar always shown, handles its own hide/show internally  
- Remove `navController.navigate(NavRoute.SEARCH)` call

**`SearchScreen.kt`** — modified  
- Remove header section (back arrow row + search field row)  
- Params removed: `onNavigateBack`, `dominantColor` (already have it via VM if needed)  
- Now renders only filter chips + results content  
- Rename to `SearchResultsPanel` to reflect new role (results only)

**`NavRoute.kt`** — modified  
- Remove `const val SEARCH = "search"`

**`NavTransitions.kt`** — unchanged  
(SEARCH was not in TAB_ORDER; no effect)

### State flow

```
User taps 🔍
  → onSearchOpen() in LibraryScreen
  → isSearchOpen = true
  → LaconicalTopBar: LaunchedEffect fires → expandProgress animates 0→1
  → LibraryScreen: contentFadeProgress animates 0→1
  → AnimatedVisibility(isSearchOpen) shows SearchResultsPanel
  → FocusRequester.requestFocus() → keyboard opens

User taps ← or system back
  → BackHandler fires → isSearchOpen = false
  → expandProgress animates 1→0
  → contentFadeProgress animates 1→0
  → AnimatedVisibility hides SearchResultsPanel
  → onQueryChange("") clears search state
```

---

## Constraints

- No `SharedElementTransition` API — keep consistent with existing ghost-overlay morph pattern
- All animation properties compositor-only: `alpha`, `scaleX/Y`, `translationX` via `graphicsLayer`
- `Animatable` for expand + content fade (not `animateFloatAsState`) — need coroutine control for sequencing
- `FocusRequester` owned by `LaconicalTopBar`, not `SearchResultsPanel`
- `isOnSearch` references in `LibraryScreen` (sheetPeekHeight logic, bottom nav visibility) replaced with `isSearchOpen`
