# Search Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the inline search toggle in `LaconicalTopBar` with a full-screen dedicated search destination that searches across Tracks, Artists, Albums, and Playlists with grouped results, a vertical slide-in transition, and keyboard-aware mini player hiding.

**Architecture:** `NavRoute.SEARCH` is added to the existing `NavHost` in `LibraryScreen`. The new `SearchScreen.kt` composable owns its layout from status bar to bottom edge. Three new `StateFlow`s in `MainViewModel` power cross-entity search. `LaconicalTopBar` is simplified to title + search icon + settings, with background colour derived from the dominant album colour.

**Tech Stack:** Kotlin · Jetpack Compose · Material 3 · Hilt · Media3 · Coil 3 · `animateDpAsState` · `WindowInsets.ime`

---

## File Map

| Action | File |
|--------|------|
| Modify | `ui/navigation/NavRoute.kt` |
| Modify | `ui/ColorUtils.kt` |
| Modify | `ui/components/LaconicalTopBar.kt` |
| Modify | `ui/MainViewModel.kt` |
| **Create** | `ui/screens/SearchScreen.kt` |
| Modify | `ui/LibraryScreen.kt` |

---

## Task 1 — Add SEARCH route constant

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt`

- [ ] **Step 1: Add the constant**

Open `NavRoute.kt`. Add one line inside the `object NavRoute` block after the existing constants:

```kotlin
const val SEARCH = "search"
```

Full file after change:
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
    const val SEARCH = "search"

    fun albumDetailRoute(albumName: String): String = "album_detail/${Uri.encode(albumName)}"
    fun artistDetailRoute(artistName: String): String = "artist_detail/${Uri.encode(artistName)}"
    fun playlistDetailRoute(playlistId: Long): String = "playlist_detail/$playlistId"
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt
git commit -m "feat: add SEARCH nav route constant"
```

---

## Task 2 — Add `deriveBarColor` utility + refactor LaconicalTopBar

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/ColorUtils.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/components/LaconicalTopBar.kt`

### Step 2a — Add `deriveBarColor` to ColorUtils

- [ ] **Step 1: Add the extension to ColorUtils.kt**

Open `ColorUtils.kt`. Append this function after the existing `toHsl()`:

```kotlin
internal fun Color?.deriveBarColor(): Color {
    if (this == null) return Color(0xFF1A1A1A)
    return Color(
        red   = 0.034f + red   * 0.15f,
        green = 0.034f + green * 0.15f,
        blue  = 0.0425f + blue * 0.15f,
        alpha = 1f
    )
}
```

This is a linear blend: 85% of a near-black base + 15% of the dominant colour — slightly more tinted than the screen background (which uses 8%), giving the top bar a subtle distinction while staying very dark. When `null` (nothing playing) it returns `0xFF1A1A1A`.

### Step 2b — Rewrite LaconicalTopBar

The top bar loses its inline search toggle entirely and gains:
- `dominantColor: Color?` param — drives animated container colour via `deriveBarColor()`
- `onSearchClick: () -> Unit` param — navigates to search
- Fixed status-bar spacing (`statusBarHeight + 4.dp`) instead of the animated scale

- [ ] **Step 2: Replace LaconicalTopBar.kt completely**

```kotlin
package com.laconical.player.ui.components

import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.ui.deriveBarColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaconicalTopBar(
    dominantColor: Color?,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val containerColor by animateColorAsState(
        targetValue = dominantColor.deriveBarColor(),
        animationSpec = tween(400),
        label = "TopBarColor"
    )

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor
        ),
        title = {
            Text(
                text = "Laconical Library",
                fontFamily = FontFamily.Serif,
                fontSize = 28.sp,
                color = Color.White
            )
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { /* TODO: Settings */ }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            }
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.padding(top = statusBarHeight + 4.dp)
    )
}
```

- [ ] **Step 3: Verify build compiles**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: errors about `LaconicalTopBar` call site in `LibraryScreen.kt` (wrong params). That is expected — we fix it in Task 5. If there are unexpected errors in `LaconicalTopBar.kt` itself, fix them before continuing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/ColorUtils.kt \
        app/src/main/java/com/laconical/player/ui/components/LaconicalTopBar.kt
git commit -m "refactor: simplify LaconicalTopBar — dominant colour bg, fixed spacing, no inline search"
```

---

## Task 3 — Add search StateFlows to MainViewModel

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/MainViewModel.kt`

`MainViewModel` currently filters tracks by `_searchQuery`. We add three more StateFlows that filter albums, artists, and playlists by the same query. Each album/artist StateFlow returns one representative `Track` per group so Coil can load art from `track.mediaUri` using the existing `AudioArtData` mechanism.

- [ ] **Step 1: Add the three StateFlows to MainViewModel**

Find the block where `tracks` is defined (the `combine(_allTracks, _searchQuery, _sortOrder)` call). Add the three new StateFlows immediately after it:

```kotlin
val searchedAlbums: StateFlow<List<Track>> = combine(_allTracks, _searchQuery) { allTracks, query ->
    if (query.isBlank()) emptyList()
    else allTracks
        .filter { it.album.contains(query, ignoreCase = true) }
        .distinctBy { it.album.lowercase() }
        .sortedBy { it.album.lowercase() }
}.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

val searchedArtists: StateFlow<List<Track>> = combine(_allTracks, _searchQuery) { allTracks, query ->
    if (query.isBlank()) emptyList()
    else allTracks
        .filter { it.artist.contains(query, ignoreCase = true) }
        .distinctBy { it.artist.lowercase() }
        .sortedBy { it.artist.lowercase() }
}.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

val searchedPlaylists: StateFlow<List<Playlist>> = combine(
    _searchQuery,
    userDataRepository.getAllPlaylists()
) { query, allPlaylists ->
    if (query.isBlank()) emptyList()
    else allPlaylists.filter { it.name.contains(query, ignoreCase = true) }
}.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

Make sure `Playlist` is imported: `import com.laconical.player.core.data.db.entity.Playlist`

- [ ] **Step 2: Verify build**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: same call-site errors about `LaconicalTopBar` as before, no new errors. The new StateFlows must compile without issue.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/MainViewModel.kt
git commit -m "feat: add searchedAlbums, searchedArtists, searchedPlaylists StateFlows"
```

---

## Task 4 — Create SearchScreen.kt

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/screens/SearchScreen.kt`

This is the full search screen. It owns its layout top-to-bottom:
1. Coloured header (bar colour from `dominantColor.deriveBarColor()`) containing the search pill + filter chips
2. A `LazyColumn` (or `LazyVerticalGrid` for artist/album-only views) for results

The screen is stateless regarding navigation — it receives callbacks. Filter chip selection (`SearchFilter`) is local state.

- [ ] **Step 1: Create SearchScreen.kt**

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.components.StaggeredEntrance.staggeredEntrance
import com.laconical.player.ui.components.TrackListItem
import com.laconical.player.ui.deriveBarColor

enum class SearchFilter(val label: String) {
    ALL("All"),
    TRACKS("Tracks"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    PLAYLISTS("Playlists")
}

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
) {
    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }
    val focusRequester = remember { FocusRequester() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val context = LocalContext.current
    val loader = SingletonImageLoader.get(context)

    val containerColor by animateColorAsState(
        targetValue = dominantColor.deriveBarColor(),
        animationSpec = tween(400),
        label = "SearchBarColor"
    )

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────
        Column(modifier = Modifier.background(containerColor)) {
            Spacer(modifier = Modifier.height(statusBarPadding + 4.dp))

            // Search bar row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    onNavigateBack()
                    onSearchQueryChange("")
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                val interactionSource = remember { MutableInteractionSource() }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        onSearchQueryChange(it)
                        selectedFilter = SearchFilter.ALL
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = searchQuery,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = interactionSource,
                            placeholder = {
                                Text(
                                    text = "Search",
                                    style = TextStyle(
                                        color = Color.Gray.copy(alpha = 0.6f),
                                        fontSize = 16.sp
                                    )
                                )
                            },
                            trailingIcon = {
                                AnimatedVisibility(
                                    visible = searchQuery.isNotEmpty(),
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    IconButton(onClick = {
                                        onSearchQueryChange("")
                                        selectedFilter = SearchFilter.ALL
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear search",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                            container = {
                                Box(
                                    Modifier.border(
                                        1.dp,
                                        Color.Gray.copy(alpha = 0.4f),
                                        RoundedCornerShape(24.dp)
                                    )
                                )
                            }
                        )
                    }
                )
            }

            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                filter.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = (dominantColor ?: Color(0xFF404040))
                                .copy(alpha = 0.35f),
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
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ── Results ───────────────────────────────────────────────────────
        if (searchQuery.isNotBlank()) {
            when (selectedFilter) {
                SearchFilter.ALL -> AllResultsContent(
                    tracks = tracks,
                    searchedAlbums = searchedAlbums,
                    searchedArtists = searchedArtists,
                    searchedPlaylists = searchedPlaylists,
                    playlistArtTracks = playlistArtTracks,
                    dominantColor = dominantColor,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    loader = loader,
                    onTrackClick = onTrackClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = onArtistClick,
                    onPlaylistClick = onPlaylistClick,
                    onViewAllTracks = { selectedFilter = SearchFilter.TRACKS },
                    onViewAllArtists = { selectedFilter = SearchFilter.ARTISTS },
                    onViewAllAlbums = { selectedFilter = SearchFilter.ALBUMS },
                    onViewAllPlaylists = { selectedFilter = SearchFilter.PLAYLISTS }
                )
                SearchFilter.TRACKS -> TracksOnlyContent(
                    tracks = tracks,
                    currentTrack = currentTrack,
                    isPlaying = isPlaying,
                    favoriteIds = favoriteIds,
                    onTrackClick = onTrackClick,
                    onFavoriteToggle = onFavoriteToggle
                )
                SearchFilter.ARTISTS -> ArtistsOnlyContent(
                    artists = searchedArtists,
                    loader = loader,
                    onArtistClick = onArtistClick
                )
                SearchFilter.ALBUMS -> AlbumsOnlyContent(
                    albums = searchedAlbums,
                    loader = loader,
                    onAlbumClick = onAlbumClick
                )
                SearchFilter.PLAYLISTS -> PlaylistsOnlyContent(
                    playlists = searchedPlaylists,
                    playlistArtTracks = playlistArtTracks,
                    onPlaylistClick = onPlaylistClick
                )
            }
        }
    }
}

// ── "All" grouped view ────────────────────────────────────────────────────────

@Composable
private fun AllResultsContent(
    tracks: List<Track>,
    searchedAlbums: List<Track>,
    searchedArtists: List<Track>,
    searchedPlaylists: List<Playlist>,
    playlistArtTracks: Map<Long, List<Track>>,
    dominantColor: Color?,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    loader: ImageLoader,
    onTrackClick: (List<Track>, Int) -> Unit,
    onFavoriteToggle: (Long) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onViewAllTracks: () -> Unit,
    onViewAllArtists: () -> Unit,
    onViewAllAlbums: () -> Unit,
    onViewAllPlaylists: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (searchedAlbums.isNotEmpty()) {
            item(key = "albums_header") {
                SectionHeader("Albums", searchedAlbums.size, dominantColor, onViewAllAlbums)
            }
            item(key = "albums_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchedAlbums, key = { it.album }) { track ->
                        AlbumCard(track = track, loader = loader, onClick = onAlbumClick)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (searchedArtists.isNotEmpty()) {
            item(key = "artists_header") {
                SectionHeader("Artists", searchedArtists.size, dominantColor, onViewAllArtists)
            }
            item(key = "artists_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(searchedArtists, key = { it.artist }) { track ->
                        ArtistCard(track = track, loader = loader, onClick = onArtistClick)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (tracks.isNotEmpty()) {
            item(key = "tracks_header") {
                SectionHeader("Tracks", tracks.size, dominantColor, onViewAllTracks)
            }
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackListItem(
                    track = track,
                    isActiveTrack = currentTrack?.id == track.id,
                    isPlaybackActive = isPlaying,
                    isFavorite = favoriteIds.contains(track.id),
                    onFavoriteToggle = { onFavoriteToggle(track.id) },
                    onClick = { onTrackClick(tracks, index) },
                    modifier = Modifier.staggeredEntrance(index)
                )
            }
        }

        if (searchedPlaylists.isNotEmpty()) {
            item(key = "playlists_header") {
                SectionHeader("Playlists", searchedPlaylists.size, dominantColor, onViewAllPlaylists)
            }
            items(searchedPlaylists, key = { it.id }) { playlist ->
                SearchPlaylistRow(
                    playlist = playlist,
                    artTracks = playlistArtTracks[playlist.id] ?: emptyList(),
                    onClick = { onPlaylistClick(playlist.id) }
                )
            }
        }
    }
}

// ── Single-category views ─────────────────────────────────────────────────────

@Composable
private fun TracksOnlyContent(
    tracks: List<Track>,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onFavoriteToggle: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            TrackListItem(
                track = track,
                isActiveTrack = currentTrack?.id == track.id,
                isPlaybackActive = isPlaying,
                isFavorite = favoriteIds.contains(track.id),
                onFavoriteToggle = { onFavoriteToggle(track.id) },
                onClick = { onTrackClick(tracks, index) },
                modifier = Modifier.staggeredEntrance(index)
            )
        }
    }
}

@Composable
private fun ArtistsOnlyContent(
    artists: List<Track>,
    loader: ImageLoader,
    onArtistClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(artists, key = { _, track -> track.artist }) { index, track ->
            ArtistCard(
                track = track,
                loader = loader,
                onClick = onArtistClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index)
            )
        }
    }
}

@Composable
private fun AlbumsOnlyContent(
    albums: List<Track>,
    loader: ImageLoader,
    onAlbumClick: (String) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(albums, key = { _, track -> track.album }) { index, track ->
            AlbumCard(
                track = track,
                loader = loader,
                onClick = onAlbumClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredEntrance(index)
            )
        }
    }
}

@Composable
private fun PlaylistsOnlyContent(
    playlists: List<Playlist>,
    playlistArtTracks: Map<Long, List<Track>>,
    onPlaylistClick: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
            SearchPlaylistRow(
                playlist = playlist,
                artTracks = playlistArtTracks[playlist.id] ?: emptyList(),
                onClick = { onPlaylistClick(playlist.id) },
                modifier = Modifier.staggeredEntrance(index)
            )
        }
    }
}

// ── Shared small composables ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    dominantColor: Color?,
    onViewAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title • $count",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        TextButton(
            onClick = onViewAll,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.textButtonColors(
                contentColor = (dominantColor ?: Color(0xFF888888)).copy(alpha = 0.9f)
            )
        ) {
            Text("View All", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AlbumCard(
    track: Track,
    loader: ImageLoader,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier.width(130.dp)
) {
    Column(
        modifier = modifier.clickable { onClick(track.album) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubcomposeAsyncImage(
            model = AudioArtData(track.mediaUri),
            imageLoader = loader,
            contentDescription = track.album,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.fillMaxSize(0.4f)
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.album,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun ArtistCard(
    track: Track,
    loader: ImageLoader,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier.width(90.dp)
) {
    Column(
        modifier = modifier.clickable { onClick(track.artist) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubcomposeAsyncImage(
            model = AudioArtData(track.mediaUri),
            imageLoader = loader,
            contentDescription = track.artist,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.fillMaxSize(0.4f)
                    )
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = track.artist,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SearchPlaylistRow(
    playlist: Playlist,
    artTracks: List<Track>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistCoverMosaic(tracks = artTracks, size = 52.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = playlist.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
```

- [ ] **Step 2: Note on `staggeredEntrance` import**

`staggeredEntrance` is defined in `StaggeredEntrance.kt`. Check the exact import path:

```bash
grep -rn "fun Modifier.staggeredEntrance\|package" \
  app/src/main/java/com/laconical/player/ui/components/StaggeredEntrance.kt
```

Update the import in `SearchScreen.kt` if it differs from `com.laconical.player.ui.components.StaggeredEntrance.staggeredEntrance`. It may simply be a top-level extension in the components package, in which case the import is `com.laconical.player.ui.components.staggeredEntrance`.

- [ ] **Step 3: Build check**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | grep -E "error:|warning:" | head -20
```

Fix any import errors. Common ones:
- `SearchFilter.entries` requires Kotlin 1.9+ — if it fails, use `SearchFilter.values().toList()`
- `aspectRatio` needs `import androidx.compose.foundation.layout.aspectRatio`
- `ImageLoader` needs `import coil3.ImageLoader`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/screens/SearchScreen.kt
git commit -m "feat: add SearchScreen with grouped results, filter chips, and animated header"
```

---

## Task 5 — Wire LibraryScreen

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

Five changes are needed:

1. Collect the three new StateFlows
2. Hoist the `raw` route variable and derive `isSearchRoute`
3. Make `sheetPeekHeight` animated and IME-aware
4. Update `LaconicalTopBar` call site (new params, hide on search)
5. Hide bottom nav on search route
6. Register `NavRoute.SEARCH` composable in NavHost

### Step 5a — Collect new StateFlows

- [ ] **Step 1: Add three new state collections near the top of `LibraryScreen`**

Find the block where `searchQuery` is collected (around line 133). Add immediately after it:

```kotlin
val searchedAlbums   by viewModel.searchedAlbums.collectAsState()
val searchedArtists  by viewModel.searchedArtists.collectAsState()
val searchedPlaylists by viewModel.searchedPlaylists.collectAsState()
```

### Step 5b — Hoist route variable + IME detection

Currently `raw` is computed inside the `LaconicalBottomNav` block. Hoist it above the `BottomSheetScaffold`.

- [ ] **Step 2: Add these three lines before the `Box(modifier = Modifier.fillMaxSize())` that wraps the `Surface`**

```kotlin
val raw = navController.currentBackStackEntryAsState().value?.destination?.route
    ?: NavRoute.TRACKS
val isSearchRoute = raw == NavRoute.SEARCH

val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
val isImeVisible = imeBottom > 0
```

`LocalDensity.current` is already used elsewhere in the file — no new import needed. `WindowInsets.ime` needs `import androidx.compose.foundation.layout.ime` if not already present.

### Step 5c — Animate sheetPeekHeight

- [ ] **Step 3: Replace the `sheetPeekHeight` val with an animated version**

Find this block (around line 188):
```kotlin
val sheetPeekHeight = if (currentTrack != null)
    miniPlayerHeight + bottomNavHeight + bottomInsets
else
    0.dp
```

Replace with:
```kotlin
val logicalPeekHeight = if (currentTrack != null)
    miniPlayerHeight + bottomNavHeight + bottomInsets
else
    0.dp

val sheetPeekHeight by animateDpAsState(
    targetValue = if (isSearchRoute && isImeVisible) 0.dp else logicalPeekHeight,
    animationSpec = tween(200),
    label = "PeekHeight"
)
```

Add import: `import androidx.compose.animation.core.animateDpAsState`

Leave `trackListBottomPadding` unchanged — it already computes correctly from `currentTrack` and doesn't need to respond to IME state.

Also update `maxOffset` to use `logicalPeekHeight` so `expandedFraction` is not disturbed by the IME animation:

Find:
```kotlin
val maxOffset = if (containerHeightPx > 0f)
    containerHeightPx - with(density) { sheetPeekHeight.toPx() }
else 1000f
```

Replace with:
```kotlin
val maxOffset = if (containerHeightPx > 0f)
    containerHeightPx - with(density) { logicalPeekHeight.toPx() }
else 1000f
```

### Step 5d — Update LaconicalTopBar call site

- [ ] **Step 4: Update the `topBar` lambda**

Find:
```kotlin
topBar = {
    if (hasPermission) {
        LaconicalTopBar(
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery
        )
    }
}
```

Replace with:
```kotlin
topBar = {
    if (hasPermission && !isSearchRoute) {
        LaconicalTopBar(
            dominantColor = playingTrackDominantColor,
            onSearchClick = { navController.navigate(NavRoute.SEARCH) }
        )
    }
}
```

### Step 5e — Hide bottom nav on search route

- [ ] **Step 5: Add `!isSearchRoute` guard to the bottom nav condition**

Find:
```kotlin
if (hasPermission && expandedFraction < 0.99f) {
    val navBarHeightPx = with(density) { (bottomNavHeight + bottomInsets).toPx() }
    LaconicalBottomNav(
```

Replace the condition:
```kotlin
if (hasPermission && expandedFraction < 0.99f && !isSearchRoute) {
    val navBarHeightPx = with(density) { (bottomNavHeight + bottomInsets).toPx() }
    LaconicalBottomNav(
```

Also remove the inner `val raw = ...` computation inside the `LaconicalBottomNav` block — it's now hoisted. The `LaconicalBottomNav` `selectedRoute` param currently computes `raw` inline:

```kotlin
selectedRoute = run {
    val raw = navController.currentBackStackEntryAsState().value?.destination?.route
        ?: NavRoute.TRACKS
    when {
        raw.startsWith("album_detail")    -> NavRoute.ALBUMS
        raw.startsWith("artist_detail")   -> NavRoute.ARTISTS
        raw.startsWith("playlist_detail") -> NavRoute.PLAYLISTS
        else -> raw
    }
},
```

Replace with (using the already-hoisted `raw`):
```kotlin
selectedRoute = when {
    raw.startsWith("album_detail")    -> NavRoute.ALBUMS
    raw.startsWith("artist_detail")   -> NavRoute.ARTISTS
    raw.startsWith("playlist_detail") -> NavRoute.PLAYLISTS
    else -> raw
},
```

### Step 5f — Register SearchScreen in NavHost

- [ ] **Step 6: Add the search composable to the NavHost block**

Find the NavHost's closing `}` brace (after all existing `composable(...)` entries). Add the search route before it, with vertical transitions:

```kotlin
composable(
    route = NavRoute.SEARCH,
    enterTransition = {
        slideInVertically(animationSpec = tween(300)) { -it } +
        fadeIn(animationSpec = tween(300))
    },
    exitTransition = {
        slideOutVertically(animationSpec = tween(300)) { -it } +
        fadeOut(animationSpec = tween(300))
    },
    popEnterTransition = {
        slideInVertically(animationSpec = tween(300)) { -it } +
        fadeIn(animationSpec = tween(300))
    },
    popExitTransition = {
        slideOutVertically(animationSpec = tween(300)) { -it } +
        fadeOut(animationSpec = tween(300))
    }
) {
    SearchScreen(
        searchQuery        = searchQuery,
        onSearchQueryChange = viewModel::updateSearchQuery,
        tracks             = tracks,
        searchedAlbums     = searchedAlbums,
        searchedArtists    = searchedArtists,
        searchedPlaylists  = searchedPlaylists,
        playlistArtTracks  = playlistArtTracks,
        dominantColor      = playingTrackDominantColor,
        currentTrack       = currentTrack,
        isPlaying          = isPlaying,
        favoriteIds        = favoriteIds,
        onNavigateBack     = {
            navController.popBackStack()
            viewModel.updateSearchQuery("")
        },
        onTrackClick       = { trackList, index -> viewModel.playTracks(trackList, index) },
        onFavoriteToggle   = viewModel::toggleFavorite,
        onAlbumClick       = { navController.navigate(NavRoute.albumDetailRoute(it)) },
        onArtistClick      = { navController.navigate(NavRoute.artistDetailRoute(it)) },
        onPlaylistClick    = { navController.navigate(NavRoute.playlistDetailRoute(it)) }
    )
}
```

Add required imports at the top of `LibraryScreen.kt`:
```kotlin
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.laconical.player.ui.screens.SearchScreen
```

- [ ] **Step 7: Note on `isPlaying` and `tracks` in the search composable scope**

Inside the `composable(NavRoute.SEARCH)` block, `tracks`, `currentTrack`, `isPlaying`, `favoriteIds`, and `playlistArtTracks` must be in scope. Check that they are collected at the `LibraryScreen` level (not inside a nested `composable(NavRoute.TRACKS)` block). If any are nested, hoist their collection to the top of `LibraryScreen`.

- [ ] **Step 8: Full build**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. Fix any remaining import or type errors.

- [ ] **Step 9: Install and manual test**

```bash
./gradlew installDebug
```

Test checklist:
- [ ] Top bar shows correct tinted colour when a track is playing
- [ ] Top bar sits correctly below status bar (not clipping into it)
- [ ] Tap search icon → search screen slides down from top, keyboard opens automatically
- [ ] Back arrow → returns to previous tab, query clears
- [ ] System back swipe → same as back arrow
- [ ] Type a query → Albums, Artists, Tracks, Playlists sections appear
- [ ] Sections with no results are hidden
- [ ] "View All" on Albums → chip switches to Albums, grid view appears
- [ ] Clear X appears only when text is present, tapping it clears and resets to All
- [ ] Keyboard visible → mini player hidden; dismiss keyboard → mini player reappears
- [ ] Bottom nav absent on search screen; present on all other screens

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: wire SearchScreen into LibraryScreen — route, nav guards, IME peek, dominant colour top bar"
```

---

## Self-Review Checklist

- [x] `NavRoute.SEARCH` added — Task 1
- [x] Top bar colour from dominant colour — Task 2 + Task 5d
- [x] Top bar spacing fixed (constant `statusBarHeight + 4.dp`) — Task 2
- [x] Inline search toggle removed from `LaconicalTopBar` — Task 2
- [x] Search opens from search icon in top bar — Task 5d
- [x] Full-screen destination, no bottom nav slot — Task 5e
- [x] Keyboard auto-opens on screen entry — Task 4 (`FocusRequester`)
- [x] Back arrow outside pill closes search — Task 4
- [x] Clear X inside pill, visible only with text — Task 4
- [x] Pill shows "Search" placeholder — Task 4
- [x] Filter chips: All / Tracks / Artists / Albums / Playlists — Task 4
- [x] "All" view: grouped sections Albums → Artists → Tracks → Playlists — Task 4
- [x] "View All" switches chip to single-category view — Task 4
- [x] Empty sections hidden — Task 4 (conditional rendering)
- [x] Single-category: Tracks/Playlists as list, Artists/Albums as 2-col grid — Task 4
- [x] System back closes search (NavController popBackStack, no BackHandler needed) — Task 5f
- [x] Mini player hidden when keyboard up on search screen — Task 5c
- [x] Mini player returns when keyboard dismissed — Task 5c (`animateDpAsState`)
- [x] Staggered entrance animations on result items — Task 4
- [x] Vertical slide-in/out transition for search screen — Task 5f
- [x] Top bar hidden on search screen — Task 5d
- [x] Bottom nav hidden on search screen — Task 5e
- [x] `searchedAlbums`, `searchedArtists`, `searchedPlaylists` StateFlows — Task 3
