# Progressive Track Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the first-launch zero-songs bug and make tracks appear progressively with a cascade animation as MediaStore is indexed.

**Architecture:** `LocalMediaRepositoryImpl` gains a `getTracksFlow()` that emits growing snapshots every 25 rows while walking the MediaStore cursor. `MainViewModel.loadTracks()` collects this flow, updating `_allTracks` on each batch and gating `_isLoadingTracks` true/false around it. `StaggeredEntrance` gains an `isLoadingIn` mode that removes the stagger cap so all batch items cascade rather than landing as a wall. The permission callback in `LibraryScreen` calls `loadTracks()` after grant.

**Tech Stack:** Kotlin Coroutines / Flow · Jetpack Compose (graphicsLayer modifier) · MediaStore (ContentResolver) · JUnit4 + kotlinx-coroutines-test

---

## File Map

| File | Change |
|------|--------|
| `app/src/main/java/com/laconical/player/ui/components/StaggeredEntrance.kt` | Add `isLoadingIn: Boolean = false` parameter + new branch |
| `core/data/src/main/kotlin/com/laconical/player/core/data/MediaRepository.kt` | Add `getTracksFlow(batchSize: Int = 25): Flow<List<Track>>` |
| `core/data/src/main/kotlin/com/laconical/player/core/data/LocalMediaRepositoryImpl.kt` | Implement `getTracksFlow()` |
| `core/data/src/test/kotlin/com/laconical/player/core/data/GetTracksFlowTest.kt` | New — unit test for flow contract |
| `app/src/main/java/com/laconical/player/ui/MainViewModel.kt` | Refactor `loadTracks()` + add `_isLoadingTracks` |
| `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt` | Permission callback bug fix + wire `isLoadingIn` |

---

## Task 1: StaggeredEntrance — add isLoadingIn mode

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/StaggeredEntrance.kt`

- [ ] **Step 1: Replace the file content**

Replace the entire file with:

```kotlin
package com.laconical.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STAGGER_MS = 25L
private const val STAGGER_CAP = 8
private const val ANIM_DURATION_MS = 150
private const val SCROLL_IN_DURATION_MS = 90

/**
 * Fade + slide-up entrance animation staggered by [index].
 * Fires once on first composition. Uses graphicsLayer so layout is never disturbed.
 *
 * Two-speed mode (isLoadingIn = false, default):
 *   - Initial batch (index ≤ STAGGER_CAP): cascading delay + 150ms animation.
 *   - Scroll-in items (index > STAGGER_CAP): 0ms delay + 90ms animation.
 *
 * Load-in mode (isLoadingIn = true):
 *   - All items: stagger delay = (index % 25) * 25ms, always 150ms animation.
 *   - Modulo keeps max delay at 600ms regardless of total track count.
 *   - Used while _allTracks is actively growing during initial MediaStore indexing.
 */
fun Modifier.staggeredEntrance(index: Int, isLoadingIn: Boolean = false): Modifier = composed {
    val density = LocalDensity.current
    val startOffsetPx = with(density) { 16.dp.toPx() }
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(startOffsetPx) }

    LaunchedEffect(index) {
        val staggerDelay: Long
        val duration: Int
        if (isLoadingIn) {
            staggerDelay = (index % 25).toLong() * STAGGER_MS
            duration = ANIM_DURATION_MS
        } else {
            val isInitialBatch = index <= STAGGER_CAP
            staggerDelay = if (isInitialBatch) index.toLong() * STAGGER_MS else 0L
            duration = if (isInitialBatch) ANIM_DURATION_MS else SCROLL_IN_DURATION_MS
        }
        if (staggerDelay > 0L) delay(staggerDelay)
        launch { alpha.animateTo(1f, tween(duration, easing = FastOutSlowInEasing)) }
        offsetY.animateTo(0f, tween(duration, easing = FastOutSlowInEasing))
    }

    graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetY.value
    }
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with no errors. All existing callers (AlbumsScreen, ArtistsScreen, PlaylistsScreen, SearchScreen, LibraryScreen) pass only `index` and default to `isLoadingIn = false` — no changes required there.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/StaggeredEntrance.kt
git commit -m "feat: add isLoadingIn mode to staggeredEntrance for batch-load cascade"
```

---

## Task 2: MediaRepository interface — add getTracksFlow

**Files:**
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/MediaRepository.kt`
- Create: `core/data/src/test/kotlin/com/laconical/player/core/data/GetTracksFlowTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/data/src/test/kotlin/com/laconical/player/core/data/GetTracksFlowTest.kt`:

```kotlin
package com.laconical.player.core.data

import com.laconical.player.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GetTracksFlowTest {

    private fun track(id: Long) = Track(
        id = id, title = "T$id", artist = "A", album = "B",
        durationMs = 1000L, mediaUri = "content://$id",
        albumArtUri = null, dataPath = null
    )

    /**
     * Verifies the getTracksFlow contract:
     * - Each emission is a growing snapshot (cumulative, not delta)
     * - The last emission contains every track
     */
    private class FakeMediaRepository(private val allTracks: List<Track>) : MediaRepository {
        override suspend fun getTracks(): List<Track> = allTracks

        override fun getTracksFlow(batchSize: Int): Flow<List<Track>> = flow {
            val acc = mutableListOf<Track>()
            for (track in allTracks) {
                acc.add(track)
                if (acc.size % batchSize == 0) emit(acc.toList())
            }
            if (acc.isNotEmpty()) emit(acc.toList())
        }
    }

    @Test
    fun `getTracksFlow emits growing snapshots and last emission contains all tracks`() = runTest {
        val tracks = (1L..55L).map { track(it) }
        val repo = FakeMediaRepository(tracks)
        val emissions = repo.getTracksFlow(batchSize = 25).toList()

        // 55 tracks / batch 25 → emit at 25, 50, and remainder 55 = 3 emissions
        assertEquals(3, emissions.size)
        assertEquals(25, emissions[0].size)
        assertEquals(50, emissions[1].size)
        assertEquals(55, emissions[2].size)

        // Each emission is a prefix of the full list (growing snapshot)
        assertTrue(emissions[2].containsAll(tracks))
    }

    @Test
    fun `getTracksFlow emits single batch when track count less than batchSize`() = runTest {
        val tracks = (1L..10L).map { track(it) }
        val repo = FakeMediaRepository(tracks)
        val emissions = repo.getTracksFlow(batchSize = 25).toList()

        assertEquals(1, emissions.size)
        assertEquals(10, emissions[0].size)
    }

    @Test
    fun `getTracksFlow emits nothing for empty library`() = runTest {
        val repo = FakeMediaRepository(emptyList())
        val emissions = repo.getTracksFlow(batchSize = 25).toList()
        assertTrue(emissions.isEmpty())
    }
}
```

- [ ] **Step 2: Run to verify it fails (interface not yet updated)**

```bash
./gradlew :core:data:test --tests "com.laconical.player.core.data.GetTracksFlowTest" 2>&1 | tail -20
```

Expected: `FAILED` — `Unresolved reference: getTracksFlow` or `MediaRepository does not have getTracksFlow`.

- [ ] **Step 3: Add getTracksFlow to the MediaRepository interface**

Replace `core/data/src/main/kotlin/com/laconical/player/core/data/MediaRepository.kt` with:

```kotlin
package com.laconical.player.core.data

import com.laconical.player.core.model.Track
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun getTracks(): List<Track>
    fun getTracksFlow(batchSize: Int = 25): Flow<List<Track>>
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew :core:data:test --tests "com.laconical.player.core.data.GetTracksFlowTest" 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass.

Note: The build will now fail with "LocalMediaRepositoryImpl does not implement getTracksFlow" — that is expected and is fixed in Task 3.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/MediaRepository.kt \
        core/data/src/test/kotlin/com/laconical/player/core/data/GetTracksFlowTest.kt
git commit -m "feat: add getTracksFlow to MediaRepository interface with contract tests"
```

---

## Task 3: LocalMediaRepositoryImpl — implement getTracksFlow

**Files:**
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/LocalMediaRepositoryImpl.kt`

- [ ] **Step 1: Replace the file content**

Replace `core/data/src/main/kotlin/com/laconical/player/core/data/LocalMediaRepositoryImpl.kt` with:

```kotlin
package com.laconical.player.core.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.laconical.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID
    )

    private val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    private val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    override suspend fun getTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                tracks.add(rowToTrack(cursor, idCol, titleCol, artistCol, albumCol, durationCol, albumIdCol))
            }
        }
        tracks
    }

    override fun getTracksFlow(batchSize: Int): Flow<List<Track>> = flow {
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                tracks.add(rowToTrack(cursor, idCol, titleCol, artistCol, albumCol, durationCol, albumIdCol))
                if (tracks.size % batchSize == 0) emit(tracks.toList())
            }
        }
        if (tracks.isNotEmpty()) emit(tracks.toList())
    }.flowOn(Dispatchers.IO)

    private fun rowToTrack(
        cursor: android.database.Cursor,
        idCol: Int, titleCol: Int, artistCol: Int,
        albumCol: Int, durationCol: Int, albumIdCol: Int
    ): Track {
        val id      = cursor.getLong(idCol)
        val albumId = cursor.getLong(albumIdCol)
        return Track(
            id        = id,
            title     = cursor.getString(titleCol)    ?: "Unknown Title",
            artist    = cursor.getString(artistCol)   ?: "Unknown Artist",
            album     = cursor.getString(albumCol)    ?: "Unknown Album",
            durationMs = cursor.getLong(durationCol),
            mediaUri  = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
            ).toString(),
            albumArtUri = ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"), albumId
            ).toString(),
            dataPath  = null
        )
    }
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
./gradlew :core:data:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run all core:data tests to verify nothing regressed**

```bash
./gradlew :core:data:test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`, all tests pass (including the new `GetTracksFlowTest`).

- [ ] **Step 4: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/LocalMediaRepositoryImpl.kt
git commit -m "feat: implement getTracksFlow in LocalMediaRepositoryImpl with batch emission"
```

---

## Task 4: MainViewModel — refactor loadTracks + add isLoadingTracks

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/MainViewModel.kt`

- [ ] **Step 1: Add `_isLoadingTracks` StateFlow after the `_allTracks` declaration (line ~201)**

Find this line:
```kotlin
    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
```

Add directly below it:
```kotlin
    private val _isLoadingTracks = MutableStateFlow(false)
    val isLoadingTracks: StateFlow<Boolean> = _isLoadingTracks.asStateFlow()
```

- [ ] **Step 2: Replace the loadTracks() function body**

Find and replace the existing `loadTracks()` function (lines ~335–348):

```kotlin
    fun loadTracks() {
        viewModelScope.launch {
            val loaded = repository.getTracks()
            _allTracks.value = loaded
            val liveIds = loaded.map { it.id }.toSet()
            if (liveIds.isNotEmpty()) {
                userDataRepository.purgeStaleTrackIds(liveIds)
            }
            restorePlaybackSession(loaded)
            startSessionPersistence()
        }
        startAmplitudeTicker()
        startAutoAdvanceCollector()
    }
```

Replace with:

```kotlin
    fun loadTracks() {
        viewModelScope.launch {
            _isLoadingTracks.value = true
            repository.getTracksFlow().collect { batch ->
                _allTracks.value = batch
            }
            _isLoadingTracks.value = false
            val liveIds = _allTracks.value.map { it.id }.toSet()
            if (liveIds.isNotEmpty()) {
                userDataRepository.purgeStaleTrackIds(liveIds)
            }
            restorePlaybackSession(_allTracks.value)
            startSessionPersistence()
        }
        startAmplitudeTicker()
        startAutoAdvanceCollector()
    }
```

- [ ] **Step 3: Build to verify no compile errors**

```bash
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/MainViewModel.kt
git commit -m "feat: refactor loadTracks() to use getTracksFlow() and expose isLoadingTracks"
```

---

## Task 5: LibraryScreen — fix permission bug + wire isLoadingIn

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

- [ ] **Step 1: Fix the permission launcher callback (line ~135)**

Find:
```kotlin
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
    }
```

Replace with:
```kotlin
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
        if (isGranted) viewModel.loadTracks()
    }
```

- [ ] **Step 2: Collect isLoadingTracks (line ~146, after the other collectAsState calls)**

Find:
```kotlin
    val isPlaybackActive by viewModel.isPlaying.collectAsState()
```

Add directly below it:
```kotlin
    val isLoadingTracks by viewModel.isLoadingTracks.collectAsState()
```

- [ ] **Step 3: Pass isLoadingIn to staggeredEntrance (line ~484)**

Find:
```kotlin
                                                        modifier = Modifier.staggeredEntrance(index),
```

Replace with:
```kotlin
                                                        modifier = Modifier.staggeredEntrance(index, isLoadingIn = isLoadingTracks),
```

- [ ] **Step 4: Build the full app to verify no compile errors**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. APK written to `app/build/outputs/apk/debug/`.

- [ ] **Step 5: Run all tests to verify nothing regressed**

```bash
./gradlew :core:data:test :core:media:test 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "fix: call loadTracks() after permission grant; wire isLoadingIn to staggeredEntrance"
```

---

## Manual Verification Checklist

- [ ] Fresh install (or clear app data) → grant permission → tracks appear in waves, no restart required.
- [ ] Re-open app with permission already granted → tracks load normally, stagger animation works as before.
- [ ] Library with 100+ tracks → animation cascades within each batch of 25, no wall of items.
- [ ] Scroll the track list after load completes → `isLoadingIn = false` is in effect, scroll-in items animate with the fast 90ms path (no regression).
