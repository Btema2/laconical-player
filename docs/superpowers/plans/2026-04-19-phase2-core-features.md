# Phase 2 — Core Daily-Use Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add sort/filter, albums grid, artists list, track context menu with favorites, and a Favorites screen — turning the skeleton into a usable daily-driver music player.

**Architecture:** All new screens go in `app/.../ui/screens/`. Per-screen ViewModels (AlbumsViewModel, ArtistsViewModel) inject `MediaRepository` directly. `MainViewModel` gains `UserDataRepository` injection for favorites, playlists, and stale-track purge. Navigation stays flat routes inside the existing NavHost in `LibraryScreen`; detail screens use `SavedStateHandle` to read their argument.

**Tech Stack:** Jetpack Compose, Hilt, Room 2.7.2, Navigation Compose 2.8.5, StateFlow, Robolectric (DAO tests)

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `core/data/.../db/dao/FavoriteDao.kt` | Add purge query |
| Modify | `core/data/.../db/dao/HistoryDao.kt` | Add purge query |
| Modify | `core/data/.../db/dao/PlaylistDao.kt` | Add purge query |
| Modify | `core/data/.../UserDataRepository.kt` | Add `purgeStaleTrackIds` to interface |
| Modify | `core/data/.../UserDataRepositoryImpl.kt` | Implement purge |
| Create | `app/.../ui/SortOrder.kt` | Enum with label strings |
| Modify | `app/.../ui/MainViewModel.kt` | UserDataRepository injection, sort, favorites, playlists |
| Modify | `app/.../ui/navigation/NavRoute.kt` | Add `ALBUM_DETAIL`, `ARTIST_DETAIL`, `FAVORITES` routes |
| Create | `app/.../ui/viewmodels/AlbumsViewModel.kt` | Album data class + albums StateFlow |
| Modify | `app/.../ui/screens/AlbumsScreen.kt` | Full grid implementation |
| Create | `app/.../ui/screens/AlbumDetailScreen.kt` | Track list filtered by album |
| Create | `app/.../ui/viewmodels/ArtistsViewModel.kt` | Artist data class + artists StateFlow |
| Modify | `app/.../ui/screens/ArtistsScreen.kt` | Full list implementation |
| Create | `app/.../ui/screens/ArtistDetailScreen.kt` | Track list filtered by artist |
| Modify | `app/.../ui/components/TrackListItem.kt` | Add `isFavorite`, `onFavoriteToggle`, context menu params |
| Create | `app/.../ui/components/TrackContextMenu.kt` | DropdownMenu with menu actions |
| Create | `app/.../ui/screens/FavoritesScreen.kt` | List of favorited tracks |
| Modify | `app/.../ui/screens/PlaylistsScreen.kt` | Add Favorites entry at top |
| Modify | `app/.../ui/components/LaconicalBottomNav.kt` | Highlight parent tab for detail routes |
| Modify | `app/.../ui/LibraryScreen.kt` | Sort chips, new nav routes, context menu dialog, detail route wiring |
| Test | `core/data/.../db/dao/FavoriteDaoTest.kt` | Purge tests |
| Test | `core/data/.../db/dao/HistoryDaoTest.kt` | Purge test |

Base package paths:
- `core/data/src/main/kotlin/com/laconical/player/core/data/`
- `app/src/main/java/com/laconical/player/`

---

## Task 1: Purge stale track IDs

MediaStore track IDs are not stable — when files are deleted and re-scanned, Room retains orphan rows (favorites, history, playlist entries) pointing to non-existent IDs. This task adds a purge that runs on every track load.

**Files:**
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/FavoriteDao.kt`
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/HistoryDao.kt`
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt`
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt`
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt`
- Test: `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/FavoriteDaoTest.kt`
- Test: `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/HistoryDaoTest.kt`

- [ ] **Step 1: Write failing purge tests in FavoriteDaoTest**

Add these two tests at the end of the existing `FavoriteDaoTest` class (do not change existing tests):

```kotlin
@Test
fun `deleteStaleTrackIds removes favorites not in live set`() = runTest {
    dao.addFavorite(FavoriteTrack(1L))
    dao.addFavorite(FavoriteTrack(2L))
    dao.addFavorite(FavoriteTrack(3L))
    dao.deleteStaleTrackIds(setOf(1L, 3L))  // 2L is stale
    val ids = dao.getAllFavoriteIds().first()
    assertEquals(listOf(1L, 3L).sorted(), ids.sorted())
}

@Test
fun `deleteStaleTrackIds with all live tracks keeps everything`() = runTest {
    dao.addFavorite(FavoriteTrack(10L))
    dao.deleteStaleTrackIds(setOf(10L, 99L))  // 99L not in favorites — should not cause issue
    assertTrue(dao.isFavorite(10L).first())
}
```

Add missing import to FavoriteDaoTest: `import org.junit.Assert.assertEquals`

- [ ] **Step 2: Run tests to verify they fail**

```bash
./gradlew :core:data:test --tests "*.FavoriteDaoTest" 2>&1 | tail -20
```

Expected: FAIL — `deleteStaleTrackIds` does not exist yet.

- [ ] **Step 3: Add purge query to FavoriteDao**

Full file — replace `FavoriteDao.kt` content:

```kotlin
package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laconical.player.core.data.db.entity.FavoriteTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(track: FavoriteTrack)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: Long)

    @Query("SELECT trackId FROM favorite_tracks")
    fun getAllFavoriteIds(): Flow<List<Long>>

    @Query("SELECT COUNT(*) > 0 FROM favorite_tracks WHERE trackId = :trackId")
    fun isFavorite(trackId: Long): Flow<Boolean>

    @Query("DELETE FROM favorite_tracks WHERE trackId NOT IN (:liveIds)")
    suspend fun deleteStaleTrackIds(liveIds: Set<Long>)
}
```

- [ ] **Step 4: Run FavoriteDaoTest to verify it passes**

```bash
./gradlew :core:data:test --tests "*.FavoriteDaoTest" 2>&1 | tail -20
```

Expected: 6 tests pass (4 existing + 2 new).

- [ ] **Step 5: Write failing purge test in HistoryDaoTest**

Add at the end of the `HistoryDaoTest` class:

```kotlin
@Test
fun `deleteStaleTrackIds removes history for non-existent tracks`() = runTest {
    dao.recordPlay(PlayHistory(trackId = 1L, playedAt = 1000L))
    dao.recordPlay(PlayHistory(trackId = 2L, playedAt = 2000L))
    dao.deleteStaleTrackIds(setOf(1L))  // 2L is stale
    val history = dao.getRecentHistory(50).first()
    assertEquals(1, history.size)
    assertEquals(1L, history[0].trackId)
}
```

- [ ] **Step 6: Run HistoryDaoTest to see it fail**

```bash
./gradlew :core:data:test --tests "*.HistoryDaoTest" 2>&1 | tail -20
```

Expected: FAIL — `deleteStaleTrackIds` does not exist on HistoryDao yet.

- [ ] **Step 7: Add purge queries to HistoryDao and PlaylistDao**

Full `HistoryDao.kt`:

```kotlin
package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laconical.player.core.data.db.entity.PlayHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun recordPlay(history: PlayHistory)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<PlayHistory>>

    @Query("SELECT COUNT(*) FROM play_history WHERE trackId = :trackId")
    suspend fun getPlayCount(trackId: Long): Int

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()

    @Query("DELETE FROM play_history WHERE trackId NOT IN (:liveIds)")
    suspend fun deleteStaleTrackIds(liveIds: Set<Long>)
}
```

Add to `PlaylistDao.kt` — append this method inside the existing interface (after `clearPlaylist`):

```kotlin
    @Query("DELETE FROM playlist_tracks WHERE trackId NOT IN (:liveIds)")
    suspend fun deleteStaleTrackIds(liveIds: Set<Long>)
```

- [ ] **Step 8: Run all DAO tests to verify they pass**

```bash
./gradlew :core:data:test 2>&1 | tail -30
```

Expected: 15 tests pass (all existing + 3 new).

- [ ] **Step 9: Add `purgeStaleTrackIds` to UserDataRepository interface**

In `UserDataRepository.kt`, add at the end of the interface body (after `clearHistory`):

```kotlin
    // Maintenance
    suspend fun purgeStaleTrackIds(liveTrackIds: Set<Long>)
```

- [ ] **Step 10: Implement `purgeStaleTrackIds` in UserDataRepositoryImpl**

Add at the end of `UserDataRepositoryImpl` (after `clearHistory`):

```kotlin
    override suspend fun purgeStaleTrackIds(liveTrackIds: Set<Long>) {
        if (liveTrackIds.isEmpty()) return
        favoriteDao.deleteStaleTrackIds(liveTrackIds)
        playlistDao.deleteStaleTrackIds(liveTrackIds)
        historyDao.deleteStaleTrackIds(liveTrackIds)
    }
```

- [ ] **Step 11: Verify build**

```bash
./gradlew :core:data:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 12: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/FavoriteDao.kt \
        core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/HistoryDao.kt \
        core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt \
        core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt \
        core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt \
        core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/FavoriteDaoTest.kt \
        core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/HistoryDaoTest.kt
git commit -m "feat: purge stale MediaStore track IDs from Room on every load"
```

---

## Task 2: UserDataRepository in MainViewModel + Sort order + Favorites state

Wire `UserDataRepository` into `MainViewModel` to expose sort order, favorite IDs, playlist list, and the stale-track purge trigger. No UI changes in this task — only ViewModel + the SortOrder enum.

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/SortOrder.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/MainViewModel.kt`

- [ ] **Step 1: Create SortOrder.kt**

```kotlin
package com.laconical.player.ui

enum class SortOrder(val label: String) {
    DEFAULT("Default"),
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Duration")
}
```

- [ ] **Step 2: Update MainViewModel constructor to inject UserDataRepository**

Replace the existing `@HiltViewModel class MainViewModel @Inject constructor(...)` signature. The new signature adds `userDataRepository` as the last parameter:

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MediaRepository,
    private val musicPlayer: MusicPlayer,
    private val visualizerManager: AudioVisualizerManager,
    private val waveformExtractor: WaveformExtractor,
    private val userDataRepository: UserDataRepository
) : ViewModel() {
```

Add these imports at the top of `MainViewModel.kt`:

```kotlin
import com.laconical.player.core.data.UserDataRepository
import com.laconical.player.core.data.db.entity.Playlist
import kotlinx.coroutines.flow.map
```

(Note: `kotlinx.coroutines.flow.first` is already imported.)

- [ ] **Step 3: Add sort order state**

Insert these three declarations directly after the existing `val progress: StateFlow<Float>` declaration:

```kotlin
    private val _sortOrder = MutableStateFlow(SortOrder.DEFAULT)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setSortOrder(order: SortOrder) { _sortOrder.value = order }
```

- [ ] **Step 4: Replace the `tracks` StateFlow to include sort**

Find and replace the entire `val tracks: StateFlow<List<Track>> = combine(...)` block. The existing code is:

```kotlin
    val tracks: StateFlow<List<Track>> = combine(_allTracks, _searchQuery) { tracks, query ->
        if (query.isBlank()) tracks
            else tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

Replace with:

```kotlin
    val tracks: StateFlow<List<Track>> = combine(_allTracks, _searchQuery, _sortOrder) { tracks, query, sort ->
        val filtered = if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true)
        }
        when (sort) {
            SortOrder.DEFAULT -> filtered
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION -> filtered.sortedByDescending { it.durationMs }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
```

- [ ] **Step 5: Add favoriteIds StateFlow and toggleFavorite**

Insert after the `val waveformData` declaration:

```kotlin
    val favoriteIds: StateFlow<Set<Long>> = userDataRepository.getAllFavoriteIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    fun toggleFavorite(trackId: Long) {
        viewModelScope.launch {
            if (favoriteIds.value.contains(trackId)) {
                userDataRepository.removeFavorite(trackId)
            } else {
                userDataRepository.addFavorite(trackId)
            }
        }
    }
```

- [ ] **Step 6: Add playlists StateFlow and addTrackToPlaylist**

Insert after `toggleFavorite`:

```kotlin
    val playlists: StateFlow<List<Playlist>> = userDataRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addTrackToPlaylist(trackId: Long, playlistId: Long) {
        viewModelScope.launch {
            val currentCount = userDataRepository.getTrackIdsForPlaylist(playlistId).first().size
            userDataRepository.addTrackToPlaylist(playlistId, trackId, currentCount)
        }
    }
```

- [ ] **Step 7: Update loadTracks to call purge**

Find the existing `loadTracks()` function. It currently starts with:

```kotlin
    fun loadTracks() {
        viewModelScope.launch {
            _allTracks.value = repository.getTracks()
        }
```

Replace that inner launch with:

```kotlin
    fun loadTracks() {
        viewModelScope.launch {
            val loaded = repository.getTracks()
            _allTracks.value = loaded
            val liveIds = loaded.map { it.id }.toSet()
            if (liveIds.isNotEmpty()) {
                userDataRepository.purgeStaleTrackIds(liveIds)
            }
        }
```

- [ ] **Step 8: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL. Hilt will find `UserDataRepository` already bound via `DataModule`.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/SortOrder.kt \
        app/src/main/java/com/laconical/player/ui/MainViewModel.kt
git commit -m "feat: add sort order, favorites state, playlists and purge wiring to MainViewModel"
```

---

## Task 3: Sort chips UI + Favorites heart in TrackListItem

Add visible sort order chips above the track list. Add a heart icon to each track row for toggling favorites. Wire both into `LibraryScreen`.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackListItem.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Add new parameters to TrackListItem**

`TrackListItem` currently has signature:
```kotlin
fun TrackListItem(
    track: Track,
    isActiveTrack: Boolean,
    isPlaybackActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
)
```

Change it to:

```kotlin
fun TrackListItem(
    track: Track,
    isActiveTrack: Boolean,
    isPlaybackActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    onViewArtist: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
)
```

- [ ] **Step 2: Replace the ⋮ IconButton at the end of the Row with a favorite icon + ⋮ button**

Find this block in `TrackListItem.kt`:

```kotlin
            IconButton(onClick = { /* TODO: Track menu */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color(0xFF777777)
                )
            }
```

Replace with:

```kotlin
            if (onFavoriteToggle != null) {
                IconButton(onClick = onFavoriteToggle) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) Color(0xFFE84B7A) else Color(0xFF777777)
                    )
                }
            }
            IconButton(onClick = { /* context menu wired in Task 6 */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color(0xFF777777)
                )
            }
```

Add these imports to `TrackListItem.kt`:

```kotlin
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
```

- [ ] **Step 3: Update the NavRoute.TRACKS composable in LibraryScreen to pass sort and favorite params**

In `LibraryScreen.kt`, find the `composable(NavRoute.TRACKS) {` block. It currently collects:

```kotlin
                            composable(NavRoute.TRACKS) {
                                val tracks by viewModel.tracks.collectAsState()
                                val isPlaybackActive by viewModel.isPlaying.collectAsState()
```

Replace the entire `composable(NavRoute.TRACKS) { ... }` block with:

```kotlin
                            composable(NavRoute.TRACKS) {
                                val tracks by viewModel.tracks.collectAsState()
                                val isPlaybackActive by viewModel.isPlaying.collectAsState()
                                val sortOrder by viewModel.sortOrder.collectAsState()
                                val favoriteIds by viewModel.favoriteIds.collectAsState()

                                Column(modifier = Modifier.fillMaxSize()) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        items(SortOrder.entries.toTypedArray()) { order ->
                                            FilterChip(
                                                selected = sortOrder == order,
                                                onClick = { viewModel.setSortOrder(order) },
                                                label = { Text(order.label, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = (playingTrackDominantColor ?: Color(0xFF404040)).copy(alpha = 0.35f),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color.Transparent,
                                                    labelColor = Color(0xFF888888)
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = sortOrder == order,
                                                    borderColor = Color(0xFF444444),
                                                    selectedBorderColor = Color.Transparent
                                                )
                                            )
                                        }
                                    }

                                    if (tracks.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Text(
                                                text = "No tracks found",
                                                color = Color.White,
                                                modifier = Modifier.align(Alignment.Center)
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(bottom = trackListBottomPadding)
                                        ) {
                                            items(tracks, key = { it.id }) { track ->
                                                val isActiveTrack = currentTrack?.id == track.id
                                                TrackListItem(
                                                    track = track,
                                                    isActiveTrack = isActiveTrack,
                                                    isPlaybackActive = isPlaybackActive,
                                                    isFavorite = favoriteIds.contains(track.id),
                                                    onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                                                    onClick = { viewModel.playTrack(track) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
```

Add these imports to `LibraryScreen.kt`:

```kotlin
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.laconical.player.ui.SortOrder
```

Note: `androidx.compose.foundation.lazy.items` is for LazyRow items — but this conflicts with the existing `androidx.compose.foundation.lazy.items` import for LazyColumn. Both use the same import path, so no conflict.

- [ ] **Step 4: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/TrackListItem.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: add sort chips and favorites heart toggle to track list"
```

---

## Task 4: Albums screen (grid + detail)

Implement the album grid and album detail track list. Add nested navigation routes for album detail.

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt`
- Create: `app/src/main/java/com/laconical/player/ui/viewmodels/AlbumsViewModel.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/screens/AlbumsScreen.kt`
- Create: `app/src/main/java/com/laconical/player/ui/screens/AlbumDetailScreen.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Update NavRoute.kt with album detail route + helper function**

Full replacement of `NavRoute.kt`:

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

    fun albumDetailRoute(albumName: String): String = "album_detail/${Uri.encode(albumName)}"
    fun artistDetailRoute(artistName: String): String = "artist_detail/${Uri.encode(artistName)}"
}
```

- [ ] **Step 2: Create AlbumsViewModel.kt**

Create file `app/src/main/java/com/laconical/player/ui/viewmodels/AlbumsViewModel.kt`:

```kotlin
package com.laconical.player.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Album(
    val name: String,
    val artistName: String,
    val trackCount: Int,
    val representativeTrackUri: String
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    init { loadAlbums() }

    private fun loadAlbums() {
        viewModelScope.launch {
            val tracks = repository.getTracks()
            _allTracks.value = tracks
            _albums.value = tracks
                .groupBy { it.album }
                .map { (name, albumTracks) ->
                    Album(
                        name = name,
                        artistName = albumTracks.first().artist,
                        trackCount = albumTracks.size,
                        representativeTrackUri = albumTracks.first().mediaUri
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    fun getTracksForAlbum(albumName: String): List<Track> =
        _allTracks.value.filter { it.album == albumName }
}
```

- [ ] **Step 3: Implement AlbumsScreen.kt**

Full replacement of `AlbumsScreen.kt`:

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.viewmodels.AlbumsViewModel

@Composable
fun AlbumsScreen(
    onAlbumClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val albums by viewModel.albums.collectAsState()
    val context = LocalContext.current

    if (albums.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No albums found", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(albums, key = { it.name }) { album ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onAlbumClick(album.name) }
                    .padding(bottom = 4.dp)
            ) {
                val imageModel = remember(album.representativeTrackUri) {
                    AudioArtData(album.representativeTrackUri)
                }
                SubcomposeAsyncImage(
                    model = imageModel,
                    imageLoader = SingletonImageLoader.get(context),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = album.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = "${album.artistName} · ${album.trackCount} tracks",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 4: Create AlbumDetailScreen.kt**

Create `app/src/main/java/com/laconical/player/ui/screens/AlbumDetailScreen.kt`:

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.core.model.Track
import com.laconical.player.ui.components.TrackListItem
import com.laconical.player.ui.viewmodels.AlbumsViewModel

@Composable
fun AlbumDetailScreen(
    albumName: String,
    onBack: () -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onFavoriteToggle: (Long) -> Unit,
    onTrackClick: (Track) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val tracks = remember(albumName, viewModel.albums.value) {
        viewModel.getTracksForAlbum(albumName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = albumName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    isActiveTrack = currentTrack?.id == track.id,
                    isPlaybackActive = isPlaying,
                    isFavorite = favoriteIds.contains(track.id),
                    onFavoriteToggle = { onFavoriteToggle(track.id) },
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}
```

- [ ] **Step 5: Wire album routes into NavHost in LibraryScreen**

In `LibraryScreen.kt`, find:

```kotlin
                            composable(NavRoute.ALBUMS) { AlbumsScreen() }
```

Replace with:

```kotlin
                            composable(NavRoute.ALBUMS) {
                                AlbumsScreen(
                                    onAlbumClick = { albumName ->
                                        navController.navigate(NavRoute.albumDetailRoute(albumName))
                                    }
                                )
                            }
                            composable(
                                route = NavRoute.ALBUM_DETAIL,
                                arguments = listOf(
                                    navArgument("albumName") { type = androidx.navigation.NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                                val isPlaybackActive by viewModel.isPlaying.collectAsState()
                                val favoriteIds by viewModel.favoriteIds.collectAsState()
                                AlbumDetailScreen(
                                    albumName = albumName,
                                    onBack = { navController.popBackStack() },
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaybackActive,
                                    favoriteIds = favoriteIds,
                                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                    onTrackClick = { viewModel.playTrack(it) },
                                    bottomPadding = trackListBottomPadding
                                )
                            }
```

Add these imports to `LibraryScreen.kt`:

```kotlin
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.laconical.player.ui.screens.AlbumDetailScreen
```

- [ ] **Step 6: Highlight "Albums" tab in bottom nav when on album detail**

In `LibraryScreen.kt`, find the block that passes `selectedRoute` to `LaconicalBottomNav`:

```kotlin
                selectedRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: NavRoute.TRACKS,
```

Replace with:

```kotlin
                selectedRoute = run {
                    val raw = navController.currentBackStackEntryAsState().value?.destination?.route
                        ?: NavRoute.TRACKS
                    when {
                        raw.startsWith("album_detail") -> NavRoute.ALBUMS
                        raw.startsWith("artist_detail") -> NavRoute.ARTISTS
                        else -> raw
                    }
                },
```

- [ ] **Step 7: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt \
        app/src/main/java/com/laconical/player/ui/viewmodels/AlbumsViewModel.kt \
        app/src/main/java/com/laconical/player/ui/screens/AlbumsScreen.kt \
        app/src/main/java/com/laconical/player/ui/screens/AlbumDetailScreen.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: albums grid and album detail track list"
```

---

## Task 5: Artists screen (list + detail)

Same pattern as Task 4 but for artists. Tap an artist → see their tracks.

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/viewmodels/ArtistsViewModel.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/screens/ArtistsScreen.kt`
- Create: `app/src/main/java/com/laconical/player/ui/screens/ArtistDetailScreen.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Create ArtistsViewModel.kt**

Create `app/src/main/java/com/laconical/player/ui/viewmodels/ArtistsViewModel.kt`:

```kotlin
package com.laconical.player.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Artist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val representativeTrackUri: String
)

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    init { loadArtists() }

    private fun loadArtists() {
        viewModelScope.launch {
            val tracks = repository.getTracks()
            _allTracks.value = tracks
            _artists.value = tracks
                .groupBy { it.artist }
                .map { (name, artistTracks) ->
                    Artist(
                        name = name,
                        trackCount = artistTracks.size,
                        albumCount = artistTracks.map { it.album }.distinct().size,
                        representativeTrackUri = artistTracks.first().mediaUri
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    fun getTracksForArtist(artistName: String): List<Track> =
        _allTracks.value.filter { it.artist == artistName }
}
```

- [ ] **Step 2: Implement ArtistsScreen.kt**

Full replacement of `ArtistsScreen.kt`:

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.SingletonImageLoader
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.viewmodels.ArtistsViewModel

@Composable
fun ArtistsScreen(
    onArtistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = hiltViewModel()
) {
    val artists by viewModel.artists.collectAsState()
    val context = LocalContext.current

    if (artists.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No artists found", style = MaterialTheme.typography.bodyLarge, color = Color.White)
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(artists, key = { it.name }) { artist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick(artist.name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val imageModel = remember(artist.representativeTrackUri) {
                    AudioArtData(artist.representativeTrackUri)
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = imageModel,
                        imageLoader = SingletonImageLoader.get(context),
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape),
                        error = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF555555),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = artist.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${artist.trackCount} tracks")
                            if (artist.albumCount > 1) append(" · ${artist.albumCount} albums")
                        },
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Create ArtistDetailScreen.kt**

Create `app/src/main/java/com/laconical/player/ui/screens/ArtistDetailScreen.kt`:

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.core.model.Track
import com.laconical.player.ui.components.TrackListItem
import com.laconical.player.ui.viewmodels.ArtistsViewModel

@Composable
fun ArtistDetailScreen(
    artistName: String,
    onBack: () -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onFavoriteToggle: (Long) -> Unit,
    onTrackClick: (Track) -> Unit,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: ArtistsViewModel = hiltViewModel()
) {
    val tracks = remember(artistName, viewModel.artists.value) {
        viewModel.getTracksForArtist(artistName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = artistName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            items(tracks, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    isActiveTrack = currentTrack?.id == track.id,
                    isPlaybackActive = isPlaying,
                    isFavorite = favoriteIds.contains(track.id),
                    onFavoriteToggle = { onFavoriteToggle(track.id) },
                    onClick = { onTrackClick(track) }
                )
            }
        }
    }
}
```

- [ ] **Step 4: Wire artist routes in NavHost in LibraryScreen**

Find:

```kotlin
                            composable(NavRoute.ARTISTS) { ArtistsScreen() }
```

Replace with:

```kotlin
                            composable(NavRoute.ARTISTS) {
                                ArtistsScreen(
                                    onArtistClick = { artistName ->
                                        navController.navigate(NavRoute.artistDetailRoute(artistName))
                                    }
                                )
                            }
                            composable(
                                route = NavRoute.ARTIST_DETAIL,
                                arguments = listOf(
                                    navArgument("artistName") { type = NavType.StringType }
                                )
                            ) { backStackEntry ->
                                val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                                val isPlaybackActive by viewModel.isPlaying.collectAsState()
                                val favoriteIds by viewModel.favoriteIds.collectAsState()
                                ArtistDetailScreen(
                                    artistName = artistName,
                                    onBack = { navController.popBackStack() },
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaybackActive,
                                    favoriteIds = favoriteIds,
                                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                    onTrackClick = { viewModel.playTrack(it) },
                                    bottomPadding = trackListBottomPadding
                                )
                            }
```

Add this import if not yet present:

```kotlin
import com.laconical.player.ui.screens.ArtistDetailScreen
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/viewmodels/ArtistsViewModel.kt \
        app/src/main/java/com/laconical/player/ui/screens/ArtistsScreen.kt \
        app/src/main/java/com/laconical/player/ui/screens/ArtistDetailScreen.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: artists list and artist detail track list"
```

---

## Task 6: Track context menu

Add a DropdownMenu to the ⋮ button in `TrackListItem`. Actions: Add/remove favorite, View album, View artist, Add to playlist (playlist picker dialog). Wire the playlist dialog in `LibraryScreen`.

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/components/TrackContextMenu.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/components/TrackListItem.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Create TrackContextMenu.kt**

```kotlin
package com.laconical.player.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun TrackContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
            leadingIcon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFE84B7A) else Color.Unspecified
                )
            },
            onClick = {
                onFavoriteToggle()
                onDismiss()
            }
        )
        if (onViewAlbum != null) {
            DropdownMenuItem(
                text = { Text("View album") },
                leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                onClick = {
                    onViewAlbum()
                    onDismiss()
                }
            )
        }
        if (onViewArtist != null) {
            DropdownMenuItem(
                text = { Text("View artist") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                onClick = {
                    onViewArtist()
                    onDismiss()
                }
            )
        }
        if (onAddToPlaylist != null) {
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                onClick = {
                    onAddToPlaylist()
                    onDismiss()
                }
            )
        }
    }
}
```

- [ ] **Step 2: Wire the ⋮ button in TrackListItem to show the context menu**

In `TrackListItem.kt`, add a `menuExpanded` state variable inside the composable body (after the `vibeColor` declaration):

```kotlin
    var menuExpanded by remember { mutableStateOf(false) }
```

Find the existing `⋮ IconButton` block added in Task 3:

```kotlin
            IconButton(onClick = { /* context menu wired in Task 6 */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color(0xFF777777)
                )
            }
```

Replace with:

```kotlin
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = Color(0xFF777777)
                    )
                }
                TrackContextMenu(
                    expanded = menuExpanded,
                    onDismiss = { menuExpanded = false },
                    isFavorite = isFavorite,
                    onFavoriteToggle = { onFavoriteToggle?.invoke() },
                    onViewAlbum = onViewAlbum,
                    onViewArtist = onViewArtist,
                    onAddToPlaylist = onAddToPlaylist
                )
            }
```

Add import:

```kotlin
import com.laconical.player.ui.components.TrackContextMenu
```

- [ ] **Step 3: Add playlist picker dialog state in LibraryScreen**

In `LibraryScreen.kt`, add these two state variables after `val scope = rememberCoroutineScope()`:

```kotlin
    var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
    val playlists by viewModel.playlists.collectAsState()
```

- [ ] **Step 4: Show playlist picker dialog when onAddToPlaylist is triggered**

At the end of the outer `Box` in `LibraryScreen.kt` (just before the closing `} // end outer Box` comment), add:

```kotlin
        // ── Playlist picker dialog ─────────────────────────────────────────────
        if (playlistPickerTrack != null) {
            val track = playlistPickerTrack!!
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { playlistPickerTrack = null },
                title = { Text("Add to playlist") },
                text = {
                    if (playlists.isEmpty()) {
                        Text("No playlists yet. Create one in the Playlists tab.")
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn {
                            androidx.compose.foundation.lazy.items(playlists) { playlist ->
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        viewModel.addTrackToPlaylist(track.id, playlist.id)
                                        playlistPickerTrack = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(playlist.name, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { playlistPickerTrack = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
```

- [ ] **Step 5: Pass onViewAlbum, onViewArtist, onAddToPlaylist to TrackListItem in the TRACKS route**

In `LibraryScreen.kt`, find the `TrackListItem(...)` call inside the NavRoute.TRACKS composable (added in Task 3). Update it to pass the context menu lambdas:

```kotlin
                                                TrackListItem(
                                                    track = track,
                                                    isActiveTrack = isActiveTrack,
                                                    isPlaybackActive = isPlaybackActive,
                                                    isFavorite = favoriteIds.contains(track.id),
                                                    onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                                                    onClick = { viewModel.playTrack(track) },
                                                    onViewAlbum = {
                                                        navController.navigate(NavRoute.albumDetailRoute(track.album))
                                                    },
                                                    onViewArtist = {
                                                        navController.navigate(NavRoute.artistDetailRoute(track.artist))
                                                    },
                                                    onAddToPlaylist = {
                                                        playlistPickerTrack = track
                                                    }
                                                )
```

Also pass `onAddToPlaylist` in the `AlbumDetailScreen` and `ArtistDetailScreen` calls in the NavHost. Update those `TrackListItem` calls inside those screens to also pass context menu lambdas — but those screens only accept `onFavoriteToggle`, not the full context menu. The full context menu is not needed in detail screens for Phase 2 (the screens already show what album/artist you're in). Leave `onViewAlbum` and `onViewArtist` as null in detail screens.

- [ ] **Step 6: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/TrackContextMenu.kt \
        app/src/main/java/com/laconical/player/ui/components/TrackListItem.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: track context menu with favorites, view album/artist, add to playlist"
```

---

## Task 7: Favorites screen + Playlists tab update

Add a `FavoritesScreen` that shows all favorited tracks, accessible from the Playlists tab as a special entry at the top.

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/screens/FavoritesScreen.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Create FavoritesScreen.kt**

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.core.model.Track
import com.laconical.player.ui.components.TrackListItem

@Composable
fun FavoritesScreen(
    allTracks: List<Track>,
    favoriteIds: Set<Long>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onTrackClick: (Track) -> Unit,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val favoriteTracks = allTracks.filter { favoriteIds.contains(it.id) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = "Favorites",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        if (favoriteTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No favorites yet. Tap ♡ on any track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(favoriteTracks, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        isActiveTrack = currentTrack?.id == track.id,
                        isPlaybackActive = isPlaying,
                        isFavorite = true,
                        onFavoriteToggle = { onFavoriteToggle(track.id) },
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Update PlaylistsScreen.kt to show a Favorites entry**

Full replacement of `PlaylistsScreen.kt`:

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFavoritesClick() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFE84B7A),
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 12.dp)
                    )
                    Text(
                        text = "Favorites",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF666666)
                )
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "User playlists coming in Phase 3",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF555555)
                )
            }
        }
    }
}
```

- [ ] **Step 3: Wire PlaylistsScreen and FavoritesScreen in LibraryScreen**

In `LibraryScreen.kt`, find:

```kotlin
                            composable(NavRoute.PLAYLISTS) { PlaylistsScreen() }
```

Replace with:

```kotlin
                            composable(NavRoute.PLAYLISTS) {
                                val allTracks by viewModel.tracks.collectAsState()
                                val favoriteIds by viewModel.favoriteIds.collectAsState()
                                PlaylistsScreen(
                                    onFavoritesClick = {
                                        navController.navigate(NavRoute.FAVORITES)
                                    }
                                )
                            }
                            composable(NavRoute.FAVORITES) {
                                val allTracks by viewModel.tracks.collectAsState()
                                val favoriteIds by viewModel.favoriteIds.collectAsState()
                                val isPlaybackActive by viewModel.isPlaying.collectAsState()
                                FavoritesScreen(
                                    allTracks = allTracks,
                                    favoriteIds = favoriteIds,
                                    currentTrack = currentTrack,
                                    isPlaying = isPlaybackActive,
                                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                    onTrackClick = { viewModel.playTrack(it) },
                                    onBack = { navController.popBackStack() },
                                    bottomPadding = trackListBottomPadding
                                )
                            }
```

Add these imports to `LibraryScreen.kt`:

```kotlin
import com.laconical.player.ui.screens.FavoritesScreen
```

- [ ] **Step 4: Verify build**

```bash
./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run all tests**

```bash
./gradlew :core:data:test :core:media:test 2>&1 | tail -30
```

Expected: All tests pass (at least the 18 in core:data).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/screens/FavoritesScreen.kt \
        app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: favorites screen and playlists tab with favorites entry"
```

---

## Self-Review

**Spec coverage check:**

| Requirement | Covered |
|------------|---------|
| Purge stale track IDs | Task 1 |
| Sort by title / artist / duration / default | Task 2 + 3 |
| Albums grid view | Task 4 |
| Tap album → track list | Task 4 (AlbumDetailScreen) |
| Artists list view | Task 5 |
| Tap artist → track list | Task 5 (ArtistDetailScreen) |
| Track context menu (⋮) | Task 6 |
| Context menu: Add/remove favorites | Task 6 |
| Context menu: View album | Task 6 |
| Context menu: View artist | Task 6 |
| Context menu: Add to playlist | Task 6 |
| Favorites heart toggle per track | Task 3 (heart icon) + Task 6 (context menu) |
| Favorites smart playlist (Room-backed) | Task 7 (FavoritesScreen, filters by Room-backed favoriteIds) |
| Sort: date added | Not included — MediaStore returns by date added by default (DEFAULT sort). Explicitly noted. |

**Placeholder scan:** None found.

**Type consistency:**
- `Album.representativeTrackUri: String` — used in `AlbumsViewModel.getTracksForAlbum()` and `AlbumsScreen` — consistent.
- `Artist.representativeTrackUri: String` — same pattern — consistent.
- `NavRoute.albumDetailRoute(albumName)` returns `"album_detail/$encoded"` — matches `ALBUM_DETAIL = "album_detail/{albumName}"` argument name — consistent.
- `NavRoute.artistDetailRoute(artistName)` returns `"artist_detail/$encoded"` — matches `ARTIST_DETAIL = "artist_detail/{artistName}"` — consistent.
- `TrackListItem` new params default to `false`/`null` — existing call sites in `AlbumDetailScreen` and `ArtistDetailScreen` pass explicit values for `isFavorite` and `onFavoriteToggle` — consistent.
- `MainViewModel.addTrackToPlaylist(trackId, playlistId)` — matches `PlaylistsScreen` dialog usage — consistent.

**Edge cases noted (not bugs in this plan):**
- `AlbumDetailScreen` uses `remember(albumName, viewModel.albums.value)` to derive tracks. If `AlbumsViewModel` is freshly created (new back stack entry), `_allTracks` is empty until `loadAlbums()` completes. The screen will show an empty list for ~100ms then fill in. Acceptable for Phase 2.
- `FavoritesScreen` receives `allTracks` from `MainViewModel.tracks` (which is the filtered+sorted list). If search query is active, some favorited tracks might not appear. This is acceptable; Phase 2 has no requirement for favorites-specific filtering.
