# Search Animation Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace nav-based search (slideInVertically jank) with an in-place search bar that expands from the search icon, with library content fading + scaling behind it.

**Architecture:** `isSearchOpen: Boolean` state lives in `LibraryScreen`. `LaconicalTopBar` owns the expand `Animatable` and `FocusRequester`. `SearchScreen` is stripped to results-only (`SearchResultsPanel`). No `NavRoute.SEARCH`, no navigation event.

**Tech Stack:** Kotlin, Jetpack Compose, `Animatable`, `graphicsLayer`, `BackHandler`, `FocusRequester`, `lerp`

---

## File Map

| File | Change |
|---|---|
| `app/.../ui/components/LaconicalTopBar.kt` | Rewrite — expand animation, search field, back arrow |
| `app/.../ui/LibraryScreen.kt` | Add `isSearchOpen` state, content fade/scale, `BackHandler`, `SearchResultsPanel` overlay |
| `app/.../ui/screens/SearchScreen.kt` | Strip header — rename composable to `SearchResultsPanel` |
| `app/.../ui/navigation/NavRoute.kt` | Remove `SEARCH` const |

---

## Task 1: Remove `NavRoute.SEARCH` and strip the nav composable

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Delete `SEARCH` from NavRoute.kt**

Open `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt`.

Remove line:
```kotlin
const val SEARCH = "search"
```

File after edit:
```kotlin
package com.laconical.player.ui.navigation

import android.net.Uri

object NavRoute {
    const val TRACKS = "tracks"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "album_detail/{albumName}"
    const val ARTISTS = "artists"
    const val ARTIST_DETAIL = "artist_detail/{artistName}"
    const val PLAYLISTS = "playlists"
    const val FAVORITES = "favorites"
    const val PLAYLIST_DETAIL = "playlist_detail/{playlistId}"

    fun albumDetailRoute(albumName: String): String = "album_detail/${Uri.encode(albumName)}"
    fun artistDetailRoute(artistName: String): String = "artist_detail/${Uri.encode(artistName)}"
    fun playlistDetailRoute(playlistId: Long): String = "playlist_detail/$playlistId"
}
```

- [ ] **Step 2: Remove the SEARCH composable block from LibraryScreen.kt**

In `LibraryScreen.kt`, find and delete the entire `composable(NavRoute.SEARCH) { ... }` block (currently lines ~536–577). It looks like:

```kotlin
composable(
    route = NavRoute.SEARCH,
    enterTransition = { slideInVertically { -it } + fadeIn(tween(200)) },
    exitTransition = { fadeOut(tween(150)) },
    popEnterTransition = { fadeIn(tween(150)) },
    popExitTransition = { slideOutVertically { -it } + fadeOut(tween(200)) }
) {
    val tracks by viewModel.tracks.collectAsState()
    val isPlaybackActive by viewModel.isPlaying.collectAsState()
    BackHandler {
        viewModel.updateSearchQuery("")
        navController.popBackStack()
    }
    SearchScreen(
        // ... all params
    )
}
```

Delete that entire block. Also remove these now-unused imports from the top of the file:
```kotlin
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.laconical.player.ui.screens.SearchScreen
```

- [ ] **Step 3: Remove the `isOnSearch` variable and replace all usages**

Find and delete:
```kotlin
val rawRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    ?: NavRoute.TRACKS
val isOnSearch = rawRoute == NavRoute.SEARCH
```

Replace with just:
```kotlin
val rawRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    ?: NavRoute.TRACKS
```

Then find every `isOnSearch` reference and replace:

1. `sheetPeekHeight` target — change:
   ```kotlin
   targetValue = if (isOnSearch && imeVisible) 0.dp else logicalPeekHeight,
   ```
   to (temporarily use `false` — will be wired to `isSearchOpen` in Task 3):
   ```kotlin
   targetValue = logicalPeekHeight,
   ```

2. TopBar condition — change:
   ```kotlin
   topBar = {
       if (hasPermission && !isOnSearch) {
           LaconicalTopBar(
               onSearchClick = { navController.navigate(NavRoute.SEARCH) }
           )
       }
   }
   ```
   to (temporarily always show, wire search click stub):
   ```kotlin
   topBar = {
       if (hasPermission) {
           LaconicalTopBar(
               onSearchClick = { /* TODO: wired in Task 3 */ }
           )
       }
   }
   ```

3. Bottom nav condition — change:
   ```kotlin
   if (hasPermission && expandedFraction < 0.99f && !isOnSearch) {
   ```
   to (temporarily always show when appropriate):
   ```kotlin
   if (hasPermission && expandedFraction < 0.99f) {
   ```

- [ ] **Step 4: Verify build compiles**

```bash
cd /home/btema2/smart-things/code/laconical-player
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Fix any remaining `NavRoute.SEARCH` or `isOnSearch` references if needed.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "refactor: remove NavRoute.SEARCH composable, drop isOnSearch — prep for in-place search"
```

---

## Task 2: Rewrite `LaconicalTopBar` with expand animation

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/LaconicalTopBar.kt`

The top bar will own:
- `expandProgress: Animatable(0f)` — 0=collapsed (normal), 1=expanded (search open)
- `FocusRequester` for the search text field
- All element alpha/translate driven by `expandProgress` via `graphicsLayer`

- [ ] **Step 1: Replace the entire file contents**

```kotlin
package com.laconical.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.laconical.player.ui.LocalAppBackground
import kotlinx.coroutines.launch

@Composable
fun LaconicalTopBar(
    isSearchOpen: Boolean,
    searchQuery: String,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val containerColor = LocalAppBackground.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val expandProgress = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }

    // Animate bar open/close
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            expandProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
            focusRequester.requestFocus()
        } else {
            expandProgress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    // Track search icon position in root coords so the bar can start from there
    var searchIconRootX by remember { mutableStateOf(0f) }
    var topBarWidthPx by remember { mutableStateOf(0f) }

    // Compute bar left offset in dp: lerp from search icon x → 8.dp
    val barLeftDp: Dp = with(density) {
        lerp(
            start = (searchIconRootX / density.density) - 8f, // icon center minus half bar start (approx)
            stop = 8f,
            fraction = expandProgress.value
        ).dp
    }

    // Inner alpha for back arrow and placeholder: only fades in in last 35% of progress
    val innerAlpha = ((expandProgress.value - 0.65f) / 0.35f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarHeight + 4.dp)
            .height(56.dp)
            .onGloballyPositioned { topBarWidthPx = it.size.width.toFloat() }
    ) {
        // ── Title ───────────────────────────────────────────────────
        Text(
            text = "Laconical Library",
            fontFamily = FontFamily.Serif,
            fontSize = 28.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .graphicsLayer {
                    alpha = lerp(1f, 0f, (expandProgress.value / 0.5f).coerceIn(0f, 1f))
                    translationX = lerp(0f, -24.dp.toPx(), expandProgress.value)
                }
        )

        // ── Right icons (Settings + Search) ─────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            // Settings icon — fades + scales out
            IconButton(
                onClick = { /* TODO: Settings */ },
                modifier = Modifier.graphicsLayer {
                    val p = (expandProgress.value / 0.55f).coerceIn(0f, 1f)
                    alpha = lerp(1f, 0f, p)
                    scaleX = lerp(1f, 0.7f, p)
                    scaleY = lerp(1f, 0.7f, p)
                }
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }

            // Search icon — fades out quickly
            IconButton(
                onClick = onSearchOpen,
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        searchIconRootX = coords.positionInRoot().x
                    }
                    .graphicsLayer {
                        alpha = lerp(1f, 0f, (expandProgress.value / 0.4f).coerceIn(0f, 1f))
                    }
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            }
        }

        // ── Expanding search bar ────────────────────────────────────
        // Grows from the search icon position to fill the bar minus 8dp left margin
        val barAlpha = (expandProgress.value / 0.2f).coerceIn(0f, 1f)
        if (expandProgress.value > 0.01f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .graphicsLayer { alpha = barAlpha }
                    .width(
                        with(density) {
                            lerp(
                                start = 36f,
                                stop = topBarWidthPx - 8.dp.toPx(),
                                fraction = expandProgress.value
                            ).toDp()
                        }
                    )
                    .height(40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = Color(0xFF1E1E28),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0xFF3A3A4A),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                    .padding(horizontal = 4.dp)
            ) {
                // Back arrow
                IconButton(
                    onClick = {
                        onQueryChange("")
                        onSearchClose()
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { alpha = innerAlpha }
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close search",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text field
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = innerAlpha }
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color.White),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search tracks, albums…",
                                style = TextStyle(color = Color(0xFF666666), fontSize = 15.sp)
                            )
                        }
                        inner()
                    }
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify build**

```bash
cd /home/btema2/smart-things/code/laconical-player
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. The new signature will cause a compile error in `LibraryScreen.kt` — that's expected and fixed in Task 3.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/LaconicalTopBar.kt
git commit -m "feat: LaconicalTopBar — expand-from-icon search bar animation with Animatable"
```

---

## Task 3: Strip `SearchScreen` header — rename composable to `SearchResultsPanel`

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/screens/SearchScreen.kt`

The top bar is gone from `SearchScreen`. This composable becomes results-only: filter chips + result lists. The `onNavigateBack` and `dominantColor` params are removed; the screen starts directly with the chip row.

- [ ] **Step 1: Rename the composable and remove header params**

In `SearchScreen.kt`, change the function signature from:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    tracks: List<Track>,
    searchedAlbums: List<Track>,
    searchedArtists: List<Track>,
    searchedPlaylists: List<Playlist>,
    playlistArtTracks: Map<Long, List<Track>>,
    dominantColor: Color?,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onNavigateBack: () -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier
)
```

to:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultsPanel(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    tracks: List<Track>,
    searchedAlbums: List<Track>,
    searchedArtists: List<Track>,
    searchedPlaylists: List<Playlist>,
    playlistArtTracks: Map<Long, List<Track>>,
    dominantColor: Color?,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    modifier: Modifier = Modifier
)
```

- [ ] **Step 2: Remove the header `Column` block from the composable body**

Delete the entire `// ── Header ──` block (the `Column` containing the status bar spacer, the `Row` with back arrow + `BasicTextField`, and the `LazyRow` filter chips with `Spacer`). Keep only the results content starting with `if (searchQuery.isNotBlank())`.

Also remove these now-unused imports:
```kotlin
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
```

Also remove `LaunchedEffect(Unit) { focusRequester.requestFocus() }` and `val focusRequester = remember { FocusRequester() }`.

The body now starts with:

```kotlin
var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
val context = LocalContext.current
val loader = SingletonImageLoader.get(context)
val containerColor = LocalAppBackground.current

// Add filter chips row here (move up from removed header):
Column(modifier = modifier.fillMaxSize().background(containerColor)) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SearchFilter.entries) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { selectedFilter = filter },
                label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = (dominantColor ?: Color(0xFF404040)).copy(alpha = 0.35f),
                    selectedLabelColor = Color.White,
                    containerColor = Color.Transparent,
                    labelColor = Color(0xFF888888)
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedFilter == filter,
                    borderColor = Color(0xFF444444),
                    selectedBorderColor = Color.Transparent
                )
            )
        }
    }

    if (searchQuery.isNotBlank()) {
        when (selectedFilter) {
            SearchFilter.ALL -> AllResultsContent(/* ... existing args ... */)
            SearchFilter.TRACKS -> TracksOnlyContent(/* ... existing args ... */)
            SearchFilter.ARTISTS -> ArtistsOnlyContent(/* ... existing args ... */)
            SearchFilter.ALBUMS -> AlbumsOnlyContent(/* ... existing args ... */)
            SearchFilter.PLAYLISTS -> PlaylistsOnlyContent(/* ... existing args ... */)
        }
    }
}
```

Keep all `AllResultsContent`, `TracksOnlyContent`, `ArtistsOnlyContent`, `AlbumsOnlyContent`, `PlaylistsOnlyContent` private composables unchanged.

- [ ] **Step 3: Verify build (will still fail — LibraryScreen import not updated yet)**

```bash
./gradlew assembleDebug 2>&1 | grep "error:" | head -10
```

Expected errors: `LibraryScreen.kt` referencing `SearchScreen` which no longer exists. That's fine — fixed in Task 4.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/screens/SearchScreen.kt
git commit -m "refactor: SearchScreen → SearchResultsPanel, strip header/back-arrow/search-field"
```

---

## Task 4: Wire `isSearchOpen` in `LibraryScreen` — content fade/scale + `SearchResultsPanel` overlay

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Add `isSearchOpen` state and `contentFadeProgress` Animatable**

In `LibraryScreen`, after the `queueAnimatable` declaration, add:

```kotlin
var isSearchOpen by remember { mutableStateOf(false) }
val contentFadeProgress = remember { Animatable(0f) }

LaunchedEffect(isSearchOpen) {
    if (isSearchOpen) {
        contentFadeProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
    } else {
        contentFadeProgress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
    }
}
```

- [ ] **Step 2: Add `BackHandler` for search close**

After the existing `BackHandler(enabled = isExpanded)` block, add:

```kotlin
BackHandler(enabled = isSearchOpen) {
    viewModel.updateSearchQuery("")
    isSearchOpen = false
}
```

- [ ] **Step 3: Update `sheetPeekHeight` to use `isSearchOpen`**

Change:
```kotlin
targetValue = logicalPeekHeight,
```
back to properly use `isSearchOpen`:
```kotlin
targetValue = if (isSearchOpen && imeVisible) 0.dp else logicalPeekHeight,
```

- [ ] **Step 4: Update `LaconicalTopBar` call with new signature**

Replace the `topBar = { ... }` block:

```kotlin
topBar = {
    if (hasPermission) {
        LaconicalTopBar(
            isSearchOpen = isSearchOpen,
            searchQuery = searchQuery,
            onSearchOpen = { isSearchOpen = true },
            onSearchClose = { isSearchOpen = false },
            onQueryChange = viewModel::updateSearchQuery
        )
    }
}
```

- [ ] **Step 5: Apply `graphicsLayer` fade+scale to the NavHost `Box`**

The `NavHost` is currently inside a `Box(modifier = Modifier.fillMaxSize())`. Wrap that box's modifier with `graphicsLayer`:

Change:
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        ...
    ) {
```

to:
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            val p = contentFadeProgress.value
            alpha = lerp(1f, 0f, p)
            scaleX = lerp(1f, 0.96f, p)
            scaleY = lerp(1f, 0.96f, p)
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
        }
) {
    NavHost(
        navController = navController,
        ...
    ) {
```

- [ ] **Step 6: Add `SearchResultsPanel` overlay with `AnimatedVisibility`**

After the bottom nav `AnimatedVisibility` block (after the closing `}` of the `if (hasPermission && expandedFraction < 0.99f)` block), add the search results overlay. Place it just before the morph overlay section (before the `QueueMorphLayer` call). The overlay must be inside the outermost `Box(modifier = Modifier.fillMaxSize())`:

```kotlin
// ── Search results overlay ───────────────────────────────────────────────
AnimatedVisibility(
    visible = isSearchOpen,
    enter = fadeIn(animationSpec = tween(250, delayMillis = 100)),
    exit = fadeOut(animationSpec = tween(200))
) {
    val tracks by viewModel.tracks.collectAsState()
    val isPlaybackActive by viewModel.isPlaying.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp + 56.dp)
            .background(LocalAppBackground.current)
    ) {
        SearchResultsPanel(
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            tracks = tracks,
            searchedAlbums = searchedAlbums,
            searchedArtists = searchedArtists,
            searchedPlaylists = searchedPlaylists,
            playlistArtTracks = playlistArtTracks,
            dominantColor = playingTrackDominantColor,
            currentTrack = currentTrack,
            isPlaying = isPlaybackActive,
            favoriteIds = favoriteIds,
            onTrackClick = { list, idx -> viewModel.playTracks(list, idx) },
            onFavoriteToggle = { viewModel.toggleFavorite(it) },
            onAlbumClick = { albumName ->
                isSearchOpen = false
                viewModel.updateSearchQuery("")
                navController.navigate(NavRoute.albumDetailRoute(albumName))
            },
            onArtistClick = { artistName ->
                isSearchOpen = false
                viewModel.updateSearchQuery("")
                navController.navigate(NavRoute.artistDetailRoute(artistName))
            },
            onPlaylistClick = { playlistId ->
                isSearchOpen = false
                viewModel.updateSearchQuery("")
                navController.navigate(NavRoute.playlistDetailRoute(playlistId))
            }
        )
    }
}
```

- [ ] **Step 7: Update bottom nav to use `isSearchOpen` instead of old `isOnSearch`**

The bottom nav is already using `!isOnSearch` which we deleted. Make sure it reads:
```kotlin
if (hasPermission && expandedFraction < 0.99f) {
```
(no search check needed — nav fades naturally as content scales down and is obscured by the overlay).

- [ ] **Step 8: Update imports in `LibraryScreen.kt`**

Add:
```kotlin
import com.laconical.player.ui.screens.SearchResultsPanel
```

Remove any remaining reference to `SearchScreen` import.

- [ ] **Step 9: Verify full build**

```bash
cd /home/btema2/smart-things/code/laconical-player
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with no errors.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: in-place search — isSearchOpen state, content fade+scale, SearchResultsPanel overlay"
```

---

## Task 5: Install on device and verify animation

- [ ] **Step 1: Install debug build**

```bash
cd /home/btema2/smart-things/code/laconical-player
./gradlew installDebug 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` and APK installed.

- [ ] **Step 2: Manual verification checklist**

Open the app and verify:

1. **Open search** — tap search icon. Bar should expand smoothly from icon position leftward. Title fades left. Settings fades + scales. Bar fills top bar area.
2. **Back arrow appears** — after bar expansion (~240ms delay), back arrow and placeholder fade in inside the bar.
3. **Content recedes** — library content (track list etc.) fades out AND scales slightly down (0.96). Should look like it's receding behind the bar.
4. **Search results** — type a query. Results fade in with stagger.
5. **Close via back arrow** — tap ← inside bar. Bar collapses, content fades back in. Query cleared.
6. **Close via system back** — press device back button while search is open. Same result.
7. **Navigate from result** — tap an album/artist from results. Search closes, navigate to detail.
8. **No jank** — no slide-from-top. No abrupt content disappearance.
9. **Bottom nav** — should NOT show while search is open (obscured by overlay).
10. **Mini player** — check that it still shows correctly when search is closed and a track is playing.

- [ ] **Step 3: Fix any visual issues found**

Common things to tweak if needed:
- Bar width calculation off → adjust the `stop = topBarWidthPx - 8.dp.toPx()` padding in the bar width lerp
- Bar starts at wrong position → adjust `searchIconRootX` arithmetic in `LaconicalTopBar`
- Content scale origin wrong → adjust `TransformOrigin` values (0.5f, 0f = top center)
- Back arrow timing off → adjust the `0.65f` threshold in `innerAlpha` formula

- [ ] **Step 4: Final commit**

```bash
git add -p  # stage only intentional fixes
git commit -m "fix: search animation polish — timing and position adjustments"
```

---

## Self-Review

**Spec coverage check:**
- ✅ Bar expands from icon position (Task 2 — `onGloballyPositioned` + `lerp` from `searchIconRootX`)
- ✅ Title fades out + translateX (Task 2 — `graphicsLayer` on title)
- ✅ Settings icon fades + scales (Task 2 — `graphicsLayer` on settings)
- ✅ Search icon fades out (Task 2 — `graphicsLayer` on search icon)
- ✅ Back arrow + placeholder delayed fade-in (Task 2 — `innerAlpha` formula)
- ✅ Content fade + scale(0.96) transform-origin top center (Task 4 step 5)
- ✅ SearchResultsPanel with `AnimatedVisibility fadeIn` 250ms delay 100ms (Task 4 step 6)
- ✅ `FocusRequester.requestFocus()` on open (Task 2 — `LaunchedEffect(isSearchOpen)`)
- ✅ `BackHandler` for search close (Task 4 step 2)
- ✅ Query cleared on close (Task 4 — `onSearchClose` + `BackHandler`)
- ✅ `NavRoute.SEARCH` removed (Task 1)
- ✅ `isOnSearch` removed, all usages replaced with `isSearchOpen` (Tasks 1 + 4)
- ✅ `sheetPeekHeight` wired to `isSearchOpen && imeVisible` (Task 4 step 3)
- ✅ Navigate from search results closes search (Task 4 step 6 — `isSearchOpen = false` before navigate)

**Type consistency check:**
- `LaconicalTopBar` new params: `isSearchOpen`, `searchQuery`, `onSearchOpen`, `onSearchClose`, `onQueryChange` — used consistently in Tasks 2 and 4
- `SearchResultsPanel` (renamed from `SearchScreen`) — `onNavigateBack` and `dominantColor` removed in Task 3, not referenced in Task 4 ✅
- `contentFadeProgress: Animatable` declared in Task 4 step 1, used in step 5 ✅
- `isSearchOpen: Boolean` declared in Task 4 step 1, used in steps 2, 3, 4, 6, 7 ✅
