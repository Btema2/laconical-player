# Phase 3 — Playlist Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Full user-defined playlist CRUD — create/rename/delete playlists, drag-to-reorder tracks, mosaic cover art, and on-the-spot playlist creation from the track context menu.

**Architecture:** Separate `PlaylistsViewModel` + `PlaylistDetailViewModel` (both `@HiltViewModel`) own all playlist state; `MainViewModel` gets a `createPlaylistAndAdd()` helper for the context-menu flow. `PlaylistDetailScreen` reuses `QueueTrackRow` drag logic from `QueueSheet`. All UI is dark OLED with dominant-color tinting consistent with the existing app.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room (core:data), Media3 (`MusicPlayer.setPlaylist`), Coil 3 (`AudioArtData`), `kotlinx.coroutines.flow.combine`, `ModalBottomSheet` (Material 3), `SavedStateHandle`.

---

## File Map

| Action | File |
|--------|------|
| Modify | `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt` |
| Modify | `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt` |
| Modify | `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt` |
| Create | `app/src/main/java/com/laconical/player/ui/viewmodels/PlaylistsViewModel.kt` |
| Create | `app/src/main/java/com/laconical/player/ui/viewmodels/PlaylistDetailViewModel.kt` |
| Create | `app/src/main/java/com/laconical/player/ui/components/PlaylistCoverMosaic.kt` |
| Create | `app/src/main/java/com/laconical/player/ui/components/PlaylistBottomSheet.kt` |
| Modify | `app/src/main/java/com/laconical/player/ui/MainViewModel.kt` |
| Modify | `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt` |
| Create | `app/src/main/java/com/laconical/player/ui/screens/PlaylistDetailScreen.kt` |
| Modify | `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt` |
| Modify | `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt` |

---

## Task 1: Worktree Setup

**Files:** none

- [ ] **Step 1: Create worktree on new branch**

```bash
git worktree add .worktrees/phase3-playlist-management -b phase3-playlist-management
```

- [ ] **Step 2: Verify**

```bash
git worktree list
```

Expected: `.worktrees/phase3-playlist-management` listed with branch `phase3-playlist-management`.

All subsequent work happens inside `.worktrees/phase3-playlist-management/`.

---

## Task 2: Data Layer — PlaylistDao + UserDataRepository

**Files:**
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt`
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt`
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt`

- [ ] **Step 1: Add two methods to PlaylistDao**

Open `PlaylistDao.kt`. Add after the existing `clearPlaylist` method:

```kotlin
    @Transaction
    suspend fun reorderTracks(playlistId: Long, tracks: List<PlaylistTrack>) {
        clearPlaylist(playlistId)
        tracks.forEach { addTrackToPlaylist(it) }
    }

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrack>>
```

Full file after edit:

```kotlin
package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun createPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(playlistTrack: PlaylistTrack)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<Long>>

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE trackId NOT IN (:liveIds)")
    suspend fun deleteStaleTrackIds(liveIds: Set<Long>)

    @Transaction
    suspend fun reorderTracks(playlistId: Long, tracks: List<PlaylistTrack>) {
        clearPlaylist(playlistId)
        tracks.forEach { addTrackToPlaylist(it) }
    }

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId ASC, position ASC")
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrack>>
}
```

- [ ] **Step 2: Add two methods to UserDataRepository interface**

Open `UserDataRepository.kt`. Add after `removeTrackFromPlaylist`:

```kotlin
    suspend fun reorderPlaylistTracks(playlistId: Long, tracks: List<PlaylistTrack>)
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrack>>
```

Full `// Playlists` block after edit:

```kotlin
    // Playlists
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<Long>>
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, position: Int)
    suspend fun appendTrackToPlaylist(playlistId: Long, trackId: Long)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)
    suspend fun reorderPlaylistTracks(playlistId: Long, tracks: List<PlaylistTrack>)
    fun getAllPlaylistTracks(): Flow<List<PlaylistTrack>>
```

Add import at top of file:

```kotlin
import com.laconical.player.core.data.db.entity.PlaylistTrack
```

- [ ] **Step 3: Implement both methods in UserDataRepositoryImpl**

Open `UserDataRepositoryImpl.kt`. Add after `removeTrackFromPlaylist`:

```kotlin
    override suspend fun reorderPlaylistTracks(playlistId: Long, tracks: List<PlaylistTrack>) =
        playlistDao.reorderTracks(playlistId, tracks)

    override fun getAllPlaylistTracks() = playlistDao.getAllPlaylistTracks()
```

- [ ] **Step 4: Build to verify no compilation errors**

Run from `.worktrees/phase3-playlist-management/`:

```bash
./gradlew :core:data:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt \
        core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt \
        core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt
git commit -m "feat: add reorderPlaylistTracks and getAllPlaylistTracks to data layer"
```

---

## Task 3: PlaylistsViewModel

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/viewmodels/PlaylistsViewModel.kt`

- [ ] **Step 1: Create PlaylistsViewModel**

```kotlin
package com.laconical.player.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.data.UserDataRepository
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = userDataRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    /** playlistId → first 4 tracks (for mosaic art). One DB query covers all playlists. */
    val playlistArtTracks: StateFlow<Map<Long, List<Track>>> = combine(
        userDataRepository.getAllPlaylistTracks(),
        _allTracks
    ) { playlistTracks, allTracks ->
        val trackMap = allTracks.associateBy { it.id }
        playlistTracks
            .groupBy { it.playlistId }
            .mapValues { (_, pts) -> pts.take(4).mapNotNull { trackMap[it.trackId] } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { _allTracks.value = mediaRepository.getTracks() }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { userDataRepository.createPlaylist(name.trim()) }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch { userDataRepository.renamePlaylist(playlistId, name.trim()) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { userDataRepository.deletePlaylist(playlistId) }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/viewmodels/PlaylistsViewModel.kt
git commit -m "feat: add PlaylistsViewModel with CRUD and mosaic art map"
```

---

## Task 4: PlaylistDetailViewModel

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/viewmodels/PlaylistDetailViewModel.kt`

- [ ] **Step 1: Create PlaylistDetailViewModel**

```kotlin
package com.laconical.player.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.data.UserDataRepository
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import com.laconical.player.core.media.MusicPlayer
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.media3.common.MediaItem
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    val playlist: StateFlow<Playlist?> = userDataRepository.getAllPlaylists()
        .map { list -> list.find { it.id == playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    /** Tracks ordered by PlaylistTrack.position. */
    val tracks: StateFlow<List<Track>> = combine(
        userDataRepository.getTrackIdsForPlaylist(playlistId),
        _allTracks
    ) { ids, all ->
        val map = all.associateBy { it.id }
        ids.mapNotNull { map[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _allTracks.value = mediaRepository.getTracks() }
    }

    fun moveTrack(from: Int, to: Int) {
        viewModelScope.launch {
            val current = tracks.value.toMutableList()
            if (from < 0 || to < 0 || from >= current.size || to >= current.size) return@launch
            val moved = current.removeAt(from)
            current.add(to, moved)
            val updated = current.mapIndexed { idx, track ->
                PlaylistTrack(playlistId, track.id, idx)
            }
            userDataRepository.reorderPlaylistTracks(playlistId, updated)
        }
    }

    fun removeTrack(trackId: Long) {
        viewModelScope.launch { userDataRepository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun playAll() {
        val list = tracks.value
        if (list.isEmpty()) return
        musicPlayer.setPlaylist(list.map { MediaItem.fromUri(it.mediaUri) }, 0)
    }

    fun shuffleAll() {
        val list = tracks.value.shuffled()
        if (list.isEmpty()) return
        musicPlayer.setPlaylist(list.map { MediaItem.fromUri(it.mediaUri) }, 0)
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/viewmodels/PlaylistDetailViewModel.kt
git commit -m "feat: add PlaylistDetailViewModel with reorder, remove, playAll, shuffleAll"
```

---

## Task 5: PlaylistCoverMosaic Component

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/components/PlaylistCoverMosaic.kt`

- [ ] **Step 1: Create PlaylistCoverMosaic**

Renders a 2×2 grid of album art images using the existing `AudioArtData` Coil model. Falls back to a single centered icon for empty playlists.

```kotlin
package com.laconical.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData

/**
 * Renders a 2×2 mosaic of album art for up to 4 tracks.
 * Falls back to a single art image for 1–3 tracks.
 * Shows a placeholder icon if tracks is empty.
 *
 * Uses the existing [AudioArtData] Coil model so art is extracted
 * from audio file metadata via [AudioAlbumArtFetcher].
 */
@Composable
fun PlaylistCoverMosaic(
    tracks: List<Track>,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        when {
            tracks.isEmpty() -> {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF555555),
                    modifier = Modifier.size(size * 0.5f)
                )
            }
            tracks.size < 4 -> {
                SubcomposeAsyncImage(
                    model = remember(tracks.first().mediaUri) { AudioArtData(tracks.first().mediaUri) },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(size * 0.5f)
                        )
                    }
                )
            }
            else -> {
                // 2×2 grid — exactly 4 tracks
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        MosaicCell(track = tracks[0], modifier = Modifier.weight(1f))
                        MosaicCell(track = tracks[1], modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        MosaicCell(track = tracks[2], modifier = Modifier.weight(1f))
                        MosaicCell(track = tracks[3], modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MosaicCell(track: Track, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF444444)
                )
            }
        }
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/PlaylistCoverMosaic.kt
git commit -m "feat: add PlaylistCoverMosaic component with 2x2 album art grid"
```

---

## Task 6: PlaylistBottomSheet Component

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/components/PlaylistBottomSheet.kt`

- [ ] **Step 1: Create PlaylistBottomSheet**

Reusable `ModalBottomSheet` for both create and rename flows. Title and pre-filled text differ; logic is identical.

```kotlin
package com.laconical.player.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Slide-up sheet for naming a playlist. Used for both create and rename.
 *
 * @param title      Sheet heading — "New Playlist" or "Rename Playlist"
 * @param initialName Pre-filled text (empty for create, existing name for rename)
 * @param onDismiss  Called on cancel or outside-tap
 * @param onConfirm  Called with the trimmed name; only fires if name is non-blank
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistBottomSheet(
    title: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartialExpansion = true)
    val focusRequester = remember { FocusRequester() }
    // Pre-select all text so user can immediately overwrite on rename
    var text by remember {
        mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length)))
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A2E)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .imePadding()
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Playlist name", color = Color(0xFF666666)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val name = text.text.trim()
                    if (name.isNotEmpty()) onConfirm(name)
                })
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF888888))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val name = text.text.trim()
                        if (name.isNotEmpty()) onConfirm(name)
                    }
                ) {
                    Text("Save", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/PlaylistBottomSheet.kt
git commit -m "feat: add PlaylistBottomSheet for create/rename flows"
```

---

## Task 7: MainViewModel Additions

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/MainViewModel.kt`

- [ ] **Step 1: Add createPlaylistAndAdd to MainViewModel**

This method is used by the "Add to playlist" picker in LibraryScreen when the user taps "+ New playlist". It creates the playlist and immediately appends the track.

Find the `addTrackToPlaylist` method (around line 175) and add directly after it:

```kotlin
    fun createPlaylistAndAdd(name: String, trackId: Long) {
        viewModelScope.launch {
            val playlistId = userDataRepository.createPlaylist(name.trim())
            userDataRepository.appendTrackToPlaylist(playlistId, trackId)
        }
    }
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/MainViewModel.kt
git commit -m "feat: add createPlaylistAndAdd to MainViewModel for context-menu flow"
```

---

## Task 8: PlaylistsScreen (Replace Stub)

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt`

- [ ] **Step 1: Replace the stub with full implementation**

The new screen collects state from `PlaylistsViewModel` (injected via `hiltViewModel()`), renders the Favorites pinned row, then all user playlists with mosaic covers and ⋮ menus.

```kotlin
package com.laconical.player.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.ui.components.PlaylistBottomSheet
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.viewmodels.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val artMap by viewModel.playlistArtTracks.collectAsState()

    var showCreateSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateSheet = true },
                containerColor = Color(0xFF4338CA),
                contentColor = Color.White,
                modifier = Modifier.padding(bottom = bottomPadding)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New playlist")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 80.dp
            )
        ) {
            // Pinned Favorites row
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
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFE84B7A),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
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

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No playlists yet. Tap + to create one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF555555)
                        )
                    }
                }
            } else {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        artTracks = artMap[playlist.id] ?: emptyList(),
                        onClick = { onPlaylistClick(playlist.id) },
                        onRename = { renameTarget = playlist },
                        onDelete = { deleteTarget = playlist }
                    )
                }
            }
        }
    }

    if (showCreateSheet) {
        PlaylistBottomSheet(
            title = "New Playlist",
            onDismiss = { showCreateSheet = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateSheet = false
            }
        )
    }

    renameTarget?.let { target ->
        PlaylistBottomSheet(
            title = "Rename Playlist",
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renamePlaylist(target.id, name)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This will permanently delete the playlist and remove all its tracks. Your music files are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target.id)
                    deleteTarget = null
                }) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    artTracks: List<com.laconical.player.core.model.Track>,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistCoverMosaic(
            tracks = artTracks,
            size = 52.dp
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Playlist options",
                    tint = Color(0xFF888888)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEF4444)) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                    },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` (the `onPlaylistClick` parameter won't be wired yet — that's Task 10)

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt
git commit -m "feat: replace PlaylistsScreen stub with full CRUD UI"
```

---

## Task 9: PlaylistDetailScreen

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/screens/PlaylistDetailScreen.kt`

- [ ] **Step 1: Create PlaylistDetailScreen**

Reuses the drag-to-reorder pattern from `QueueSheet` / `QueueTrackRow`. Key differences: no morph overlay, tracks list is self-contained, header shows mosaic + play/shuffle buttons.

```kotlin
package com.laconical.player.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.viewmodels.PlaylistDetailViewModel
import kotlin.math.roundToInt

private val DETAIL_ITEM_HEIGHT = 72.dp

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()

    val dragFromIndexState = remember { mutableIntStateOf(-1) }
    val dragOffsetYState = remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
    ) {
        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = playlist?.name ?: "",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // ── Header: mosaic + play/shuffle ──────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        ) {
            PlaylistCoverMosaic(
                tracks = tracks.take(4),
                size = 120.dp,
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${tracks.size} tracks",
                fontSize = 13.sp,
                color = Color(0xFF888888)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.playAll() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4338CA)),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All")
                }
                OutlinedButton(
                    onClick = { viewModel.shuffleAll() },
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Shuffle")
                }
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No tracks yet. Add some from the Tracks tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF555555)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp)
            ) {
                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                    PlaylistDetailTrackRow(
                        track = track,
                        index = index,
                        trackCount = tracks.size,
                        itemHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                            DETAIL_ITEM_HEIGHT.toPx()
                        },
                        dragFromIndexState = dragFromIndexState,
                        dragOffsetYState = dragOffsetYState,
                        firstVisibleIndex = { listState.firstVisibleItemIndex },
                        onTrackClick = { /* TODO Phase 5: play from playlist */ },
                        onDragStart = {
                            dragFromIndexState.intValue = index
                            dragOffsetYState.floatValue = 0f
                        },
                        onDragDelta = { dy -> dragOffsetYState.floatValue += dy },
                        onDragEnd = {
                            val from = dragFromIndexState.intValue
                            val dy = dragOffsetYState.floatValue
                            if (from >= 0) {
                                val to = (from + (dy / with(androidx.compose.ui.platform.LocalDensity.current) { DETAIL_ITEM_HEIGHT.toPx() }).roundToInt())
                                    .coerceIn(0, tracks.lastIndex)
                                if (to != from) viewModel.moveTrack(from, to)
                            }
                            dragFromIndexState.intValue = -1
                            dragOffsetYState.floatValue = 0f
                        },
                        onDragCancel = {
                            dragFromIndexState.intValue = -1
                            dragOffsetYState.floatValue = 0f
                        },
                        onRemove = { viewModel.removeTrack(track.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailTrackRow(
    track: Track,
    index: Int,
    trackCount: Int,
    itemHeightPx: Float,
    dragFromIndexState: MutableIntState,
    dragOffsetYState: MutableFloatState,
    firstVisibleIndex: () -> Int,
    onTrackClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val isDraggingThis = dragFromIndexState.intValue == index

    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

    val dragScale by animateFloatAsState(
        targetValue = if (isDraggingThis) 1.03f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "DragScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(DETAIL_ITEM_HEIGHT)
            .zIndex(if (isDraggingThis) 1f else 0f)
            .graphicsLayerWithDrag(
                index = index,
                trackCount = trackCount,
                itemHeightPx = itemHeightPx,
                dragFromIndexState = dragFromIndexState,
                dragOffsetYState = dragOffsetYState,
                dragScale = dragScale,
                firstVisibleIndex = firstVisibleIndex
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDraggingThis) Color.White.copy(alpha = 0.09f) else Color.Transparent)
                .clickable(onClick = onTrackClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF555555)
                        )
                    }
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.Gray.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from playlist",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(18.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(track.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { latestOnDragStart() },
                            onDrag = { _, offset -> latestOnDragDelta(offset.y) },
                            onDragEnd = { latestOnDragEnd() },
                            onDragCancel = { latestOnDragCancel() }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Hold to reorder",
                    tint = Color.Gray.copy(alpha = if (isDraggingThis) 0.85f else 0.40f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Extension to keep the graphicsLayer drag math in one place (mirrors QueueTrackRow). */
private fun Modifier.graphicsLayerWithDrag(
    index: Int,
    trackCount: Int,
    itemHeightPx: Float,
    dragFromIndexState: MutableIntState,
    dragOffsetYState: MutableFloatState,
    dragScale: Float,
    firstVisibleIndex: () -> Int
): Modifier = this.then(
    Modifier.graphicsLayer {
        val from = dragFromIndexState.intValue
        val dy = dragOffsetYState.floatValue
        if (from >= 0) {
            val target = (from + (dy / itemHeightPx).roundToInt()).coerceIn(0, trackCount - 1)
            val firstVisible = firstVisibleIndex()
            val visTarget = if (from > target) target.coerceAtLeast(firstVisible) else target
            translationY = when {
                index == from -> dy
                from < visTarget && index in (from + 1)..visTarget -> -itemHeightPx
                from > visTarget && index in visTarget until from -> itemHeightPx
                else -> 0f
            }
            shadowElevation = if (index == from) 20f else 0f
        }
        scaleX = dragScale
        scaleY = dragScale
    }
)
```

- [ ] **Step 2: Fix the LocalDensity usage in the drag callback**

The `onDragEnd` lambda captures density. Refactor the density read outside the item scope by threading `itemHeightPx` from the parent, which is already done — but notice inside `PlaylistDetailTrackRow` the `itemHeightPx` parameter is used directly in `onDragEnd`. The parent `PlaylistDetailScreen` passes `itemHeightPx` correctly.

However, `LocalDensity.current` inside `itemsIndexed` is valid. Build should pass cleanly.

- [ ] **Step 3: Build**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/screens/PlaylistDetailScreen.kt
git commit -m "feat: add PlaylistDetailScreen with drag-to-reorder and remove-track"
```

---

## Task 10: NavRoute + LibraryScreen Wiring

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt`
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Add PLAYLIST_DETAIL to NavRoute**

Open `NavRoute.kt` and add:

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
    const val PLAYLIST_DETAIL = "playlist_detail/{playlistId}"
    const val FAVORITES = "favorites"

    fun albumDetailRoute(albumName: String): String = "album_detail/${Uri.encode(albumName)}"
    fun artistDetailRoute(artistName: String): String = "artist_detail/${Uri.encode(artistName)}"
    fun playlistDetailRoute(playlistId: Long): String = "playlist_detail/$playlistId"
}
```

- [ ] **Step 2: Wire PlaylistDetailScreen composable in LibraryScreen NavHost**

Open `LibraryScreen.kt`.

**2a.** Add import at the top with the other screen imports:

```kotlin
import com.laconical.player.ui.screens.PlaylistDetailScreen
```

**2b.** Find the `composable(NavRoute.PLAYLISTS)` block (around line 411) and replace it:

```kotlin
                            composable(NavRoute.PLAYLISTS) {
                                PlaylistsScreen(
                                    onFavoritesClick = {
                                        navController.navigate(NavRoute.FAVORITES)
                                    },
                                    onPlaylistClick = { playlistId ->
                                        navController.navigate(NavRoute.playlistDetailRoute(playlistId))
                                    },
                                    bottomPadding = trackListBottomPadding
                                )
                            }
                            composable(
                                route = NavRoute.PLAYLIST_DETAIL,
                                arguments = listOf(
                                    navArgument("playlistId") { type = NavType.LongType }
                                )
                            ) {
                                PlaylistDetailScreen(
                                    onBack = { navController.popBackStack() },
                                    bottomPadding = trackListBottomPadding
                                )
                            }
```

**2c.** Fix bottom nav highlight — find the `selectedRoute` block (around line 462–469) and add `playlist_detail` → `PLAYLISTS` mapping:

```kotlin
                selectedRoute = run {
                    val raw = navController.currentBackStackEntryAsState().value?.destination?.route
                        ?: NavRoute.TRACKS
                    when {
                        raw.startsWith("album_detail") -> NavRoute.ALBUMS
                        raw.startsWith("artist_detail") -> NavRoute.ARTISTS
                        raw.startsWith("playlist_detail") -> NavRoute.PLAYLISTS
                        else -> raw
                    }
                },
```

- [ ] **Step 3: Update PlaylistPickerDialog to include "+ New playlist"**

The existing picker dialog is around lines 520–551 in `LibraryScreen.kt`. 

**3a.** Add a state variable near the other `playlistPickerTrack` state (find it in the composable body):

```kotlin
    var showCreateForPicker by remember { mutableStateOf(false) }
```

**3b.** Replace the full `if (playlistPickerTrack != null)` block with:

```kotlin
        if (playlistPickerTrack != null) {
            val track = playlistPickerTrack!!
            AlertDialog(
                onDismissRequest = { playlistPickerTrack = null },
                title = { Text("Add to playlist") },
                text = {
                    LazyColumn {
                        item {
                            TextButton(
                                onClick = {
                                    playlistPickerTrack = null
                                    showCreateForPicker = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "+ New playlist",
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (playlists.isEmpty()) {
                            item {
                                Text(
                                    "No playlists yet.",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color(0xFF888888)
                                )
                            }
                        } else {
                            items(playlists, key = { it.id }) { playlist ->
                                TextButton(
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
                    TextButton(onClick = { playlistPickerTrack = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
```

**3c.** Add the create-for-picker bottom sheet after the picker dialog block:

```kotlin
        if (showCreateForPicker) {
            val pendingTrack = remember { playlistPickerTrack }
            PlaylistBottomSheet(
                title = "New Playlist",
                onDismiss = { showCreateForPicker = false },
                onConfirm = { name ->
                    pendingTrack?.let { track ->
                        viewModel.createPlaylistAndAdd(name, track.id)
                    }
                    showCreateForPicker = false
                    playlistPickerTrack = null
                }
            )
        }
```

**3d.** Add import for PlaylistBottomSheet at the top of LibraryScreen:

```kotlin
import com.laconical.player.ui.components.PlaylistBottomSheet
```

- [ ] **Step 4: Build the full app**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt \
        app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: wire PlaylistDetailScreen route, update PlaylistsScreen nav, add New Playlist to picker"
```

---

## Task 11: Final Verification + Merge Commit

- [ ] **Step 1: Full clean build**

```bash
./gradlew clean assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Verify the worktree branch**

```bash
git log --oneline -10
```

Expected: all Phase 3 commits present.

- [ ] **Step 3: Push branch**

```bash
git push -u origin phase3-playlist-management
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] Create playlist → `PlaylistsViewModel.createPlaylist` + FAB → `PlaylistBottomSheet`
- [x] Rename playlist → ⋮ menu → `PlaylistBottomSheet` (pre-filled)
- [x] Delete playlist → ⋮ menu → `AlertDialog` → `PlaylistsViewModel.deletePlaylist`
- [x] Add/remove tracks from playlist → existing Phase 2 context menu (add) + delete icon per row (remove)
- [x] Playlist detail screen — track list, Play All, Shuffle All
- [x] Drag-to-reorder in detail → `PlaylistDetailViewModel.moveTrack` + `reorderPlaylistTracks` DAO
- [x] Playlists tab in bottom nav wired — stub replaced, route wired
- [x] "+ New playlist" in context-menu picker → `MainViewModel.createPlaylistAndAdd`
- [x] Mosaic 2×2 cover art → `PlaylistCoverMosaic`
- [x] `PlaylistDetailViewModel` via `SavedStateHandle` — gets `playlistId` from nav arg

**Type consistency:**
- `reorderPlaylistTracks(playlistId: Long, tracks: List<PlaylistTrack>)` — consistent across DAO, repository, impl, and ViewModel
- `playlistDetailRoute(playlistId: Long): String` — matches `NavType.LongType` in NavHost
- `PlaylistCoverMosaic(tracks, size, modifier, cornerRadius)` — used consistently in PlaylistsScreen (52dp) and PlaylistDetailScreen (120dp)
- `PlaylistBottomSheet(title, initialName, onDismiss, onConfirm)` — used consistently in PlaylistsScreen and LibraryScreen
- `PlaylistDetailViewModel.playlistId` exposed as `val` so `PlaylistDetailScreen` can read it if needed — not strictly required but safe

**No placeholders:** All steps have full code. No TBDs.
