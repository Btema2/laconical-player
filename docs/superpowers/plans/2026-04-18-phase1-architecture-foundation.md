# Phase 1 — Architecture Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire Room DB (persistence layer) and Navigation Compose (multi-screen routing) so all future features (playlists, favorites, albums view, artists view) have a clean foundation to build on.

**Architecture:** Room lives entirely in `:core:data` — entities, DAOs, `MusicDatabase`, `UserDataRepository` interface + impl, and a Hilt `DatabaseModule`. Navigation lives in `:app` — a sealed `NavRoute`, stub screens for Albums/Artists/Playlists, and a `NavHost` wired inside `LibraryScreen` that replaces the current dead bottom-nav tab logic.

**Tech Stack:** Room 2.6.1, Navigation Compose 2.8.5, Hilt, Robolectric 4.13, kotlinx-coroutines-test

---

## File Map

### Create
| File | Responsibility |
|------|---------------|
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/FavoriteTrack.kt` | Room entity — one row per favorited track ID |
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/Playlist.kt` | Room entity — user-defined playlist metadata |
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/PlaylistTrack.kt` | Room join entity — ordered track membership in a playlist |
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/PlayHistory.kt` | Room entity — one row per play event (trackId + timestamp) |
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/FavoriteDao.kt` | DAO — toggle/query favorites |
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt` | DAO — CRUD playlists + membership |
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/HistoryDao.kt` | DAO — insert + query recent play history |
| `core/data/src/main/kotlin/com/laconical/player/core/data/db/MusicDatabase.kt` | Room database class — registers all entities + DAOs |
| `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt` | Interface — single source of truth for user-generated data |
| `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt` | Impl — delegates to Room DAOs |
| `core/data/src/main/kotlin/com/laconical/player/core/data/di/DatabaseModule.kt` | Hilt module — provides `MusicDatabase` + `UserDataRepository` |
| `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/FavoriteDaoTest.kt` | Robolectric tests for FavoriteDao |
| `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/PlaylistDaoTest.kt` | Robolectric tests for PlaylistDao |
| `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/HistoryDaoTest.kt` | Robolectric tests for HistoryDao |
| `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt` | Sealed class — all app route strings in one place |
| `app/src/main/java/com/laconical/player/ui/screens/AlbumsScreen.kt` | Stub composable — "Coming soon" placeholder |
| `app/src/main/java/com/laconical/player/ui/screens/ArtistsScreen.kt` | Stub composable — "Coming soon" placeholder |
| `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt` | Stub composable — "Coming soon" placeholder |

### Modify
| File | Change |
|------|--------|
| `core/data/build.gradle.kts` | Add `testOptions { unitTests { isIncludeAndroidResources = true } }` + Room testing deps |
| `gradle/libs.versions.toml` | Add `androidx-room-testing` and `androidx-test-core` library entries |
| `core/data/src/main/kotlin/com/laconical/player/core/data/di/DataModule.kt` | Add `UserDataRepository` binding |
| `app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt` | Lift `selectedItem` state — accept `selectedRoute: String` + `onTabSelected: (String) -> Unit` |
| `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt` | Add `NavController`, wire `NavHost` replacing the direct `LazyColumn`, pass nav params to `LaconicalBottomNav` |

---

## Task 1: Add test infrastructure to core:data

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/data/build.gradle.kts`

- [ ] **Step 1: Add library entries to version catalog**

In `gradle/libs.versions.toml`, add after the `androidx-room-compiler` line:

```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version = "1.6.1" }
```

- [ ] **Step 2: Enable Android resources in unit tests and add test deps**

Replace the `dependencies { ... }` block in `core/data/build.gradle.kts` with:

```kotlin
android {
    // ... existing android block content unchanged ...
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:model"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
}
```

- [ ] **Step 3: Verify the module compiles**

```bash
./gradlew :core:data:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml core/data/build.gradle.kts
git commit -m "chore: add Room testing deps to core:data"
```

---

## Task 2: Create Room entities

**Files:**
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/FavoriteTrack.kt`
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/Playlist.kt`
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/PlaylistTrack.kt`
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/PlayHistory.kt`

- [ ] **Step 1: Create FavoriteTrack entity**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/FavoriteTrack.kt
package com.laconical.player.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tracks")
data class FavoriteTrack(
    @PrimaryKey val trackId: Long
)
```

- [ ] **Step 2: Create Playlist entity**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/Playlist.kt
package com.laconical.player.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: Create PlaylistTrack join entity**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/PlaylistTrack.kt
package com.laconical.player.core.data.db.entity

import androidx.room.Entity

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"]
)
data class PlaylistTrack(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)
```

- [ ] **Step 4: Create PlayHistory entity**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/PlayHistory.kt
package com.laconical.player.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 5: Verify entities compile**

```bash
./gradlew :core:data:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/db/entity/
git commit -m "feat: add Room entities (FavoriteTrack, Playlist, PlaylistTrack, PlayHistory)"
```

---

## Task 3: Create DAOs

**Files:**
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/FavoriteDao.kt`
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt`
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/HistoryDao.kt`

- [ ] **Step 1: Create FavoriteDao**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/FavoriteDao.kt
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
}
```

- [ ] **Step 2: Create PlaylistDao**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/PlaylistDao.kt
package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: Playlist): Long

    @Update
    suspend fun updatePlaylist(playlist: Playlist)

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
}
```

- [ ] **Step 3: Create HistoryDao**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/HistoryDao.kt
package com.laconical.player.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.laconical.player.core.data.db.entity.PlayHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPlay(history: PlayHistory)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<PlayHistory>>

    @Query("SELECT COUNT(*) FROM play_history WHERE trackId = :trackId")
    suspend fun getPlayCount(trackId: Long): Int

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}
```

- [ ] **Step 4: Verify DAOs compile**

```bash
./gradlew :core:data:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/db/dao/
git commit -m "feat: add Room DAOs (FavoriteDao, PlaylistDao, HistoryDao)"
```

---

## Task 4: Create MusicDatabase

**Files:**
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/db/MusicDatabase.kt`

- [ ] **Step 1: Create MusicDatabase**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/db/MusicDatabase.kt
package com.laconical.player.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.laconical.player.core.data.db.dao.FavoriteDao
import com.laconical.player.core.data.db.dao.HistoryDao
import com.laconical.player.core.data.db.dao.PlaylistDao
import com.laconical.player.core.data.db.entity.FavoriteTrack
import com.laconical.player.core.data.db.entity.PlayHistory
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack

@Database(
    entities = [FavoriteTrack::class, Playlist::class, PlaylistTrack::class, PlayHistory::class],
    version = 1,
    exportSchema = true
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
}
```

- [ ] **Step 2: Verify it compiles (Room KSP generates the implementation)**

```bash
./gradlew :core:data:kspDebugKotlin
```

Expected: `BUILD SUCCESSFUL` and a schema file appears at `core/data/schemas/com.laconical.player.core.data.db.MusicDatabase/1.json`

- [ ] **Step 3: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/db/MusicDatabase.kt
git add core/data/schemas/
git commit -m "feat: add MusicDatabase Room class with schema export"
```

---

## Task 5: Write and pass DAO tests

**Files:**
- Create: `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/FavoriteDaoTest.kt`
- Create: `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/PlaylistDaoTest.kt`
- Create: `core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/HistoryDaoTest.kt`

- [ ] **Step 1: Write FavoriteDaoTest**

```kotlin
// core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/FavoriteDaoTest.kt
package com.laconical.player.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.entity.FavoriteTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FavoriteDaoTest {

    private lateinit var db: MusicDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MusicDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.favoriteDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `addFavorite makes isFavorite return true`() = runTest {
        dao.addFavorite(FavoriteTrack(trackId = 42L))
        assertTrue(dao.isFavorite(42L).first())
    }

    @Test
    fun `removeFavorite makes isFavorite return false`() = runTest {
        dao.addFavorite(FavoriteTrack(trackId = 42L))
        dao.removeFavorite(42L)
        assertFalse(dao.isFavorite(42L).first())
    }

    @Test
    fun `getAllFavoriteIds returns all inserted ids`() = runTest {
        dao.addFavorite(FavoriteTrack(1L))
        dao.addFavorite(FavoriteTrack(2L))
        dao.addFavorite(FavoriteTrack(3L))
        val ids = dao.getAllFavoriteIds().first()
        assertTrue(ids.containsAll(listOf(1L, 2L, 3L)))
    }

    @Test
    fun `addFavorite twice does not duplicate`() = runTest {
        dao.addFavorite(FavoriteTrack(99L))
        dao.addFavorite(FavoriteTrack(99L))
        val ids = dao.getAllFavoriteIds().first()
        assertTrue(ids.count { it == 99L } == 1)
    }
}
```

- [ ] **Step 2: Run FavoriteDaoTest — expect PASS**

```bash
./gradlew :core:data:test --tests "*.FavoriteDaoTest"
```

Expected: `4 tests completed, 0 failures`

- [ ] **Step 3: Write PlaylistDaoTest**

```kotlin
// core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/PlaylistDaoTest.kt
package com.laconical.player.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDaoTest {

    private lateinit var db: MusicDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MusicDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.playlistDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `createPlaylist appears in getAllPlaylists`() = runTest {
        dao.createPlaylist(Playlist(name = "Chill Vibes"))
        val playlists = dao.getAllPlaylists().first()
        assertEquals(1, playlists.size)
        assertEquals("Chill Vibes", playlists[0].name)
    }

    @Test
    fun `deletePlaylist removes it from getAllPlaylists`() = runTest {
        val id = dao.createPlaylist(Playlist(name = "Temp"))
        dao.deletePlaylist(id)
        assertTrue(dao.getAllPlaylists().first().isEmpty())
    }

    @Test
    fun `addTrackToPlaylist appears in getTrackIdsForPlaylist`() = runTest {
        val playlistId = dao.createPlaylist(Playlist(name = "Test"))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, trackId = 10L, position = 0))
        val tracks = dao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(10L), tracks)
    }

    @Test
    fun `removeTrackFromPlaylist removes only that track`() = runTest {
        val playlistId = dao.createPlaylist(Playlist(name = "Test"))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 10L, 0))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 20L, 1))
        dao.removeTrackFromPlaylist(playlistId, 10L)
        val tracks = dao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(20L), tracks)
    }

    @Test
    fun `tracks returned in position order`() = runTest {
        val playlistId = dao.createPlaylist(Playlist(name = "Ordered"))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 30L, 2))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 10L, 0))
        dao.addTrackToPlaylist(PlaylistTrack(playlistId, 20L, 1))
        val tracks = dao.getTrackIdsForPlaylist(playlistId).first()
        assertEquals(listOf(10L, 20L, 30L), tracks)
    }
}
```

- [ ] **Step 4: Run PlaylistDaoTest — expect PASS**

```bash
./gradlew :core:data:test --tests "*.PlaylistDaoTest"
```

Expected: `5 tests completed, 0 failures`

- [ ] **Step 5: Write HistoryDaoTest**

```kotlin
// core/data/src/test/kotlin/com/laconical/player/core/data/db/dao/HistoryDaoTest.kt
package com.laconical.player.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.entity.PlayHistory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryDaoTest {

    private lateinit var db: MusicDatabase
    private lateinit var dao: HistoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MusicDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.historyDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `recordPlay appears in getRecentHistory`() = runTest {
        dao.recordPlay(PlayHistory(trackId = 5L, playedAt = 1000L))
        val history = dao.getRecentHistory().first()
        assertEquals(1, history.size)
        assertEquals(5L, history[0].trackId)
    }

    @Test
    fun `getRecentHistory returns newest first`() = runTest {
        dao.recordPlay(PlayHistory(trackId = 1L, playedAt = 1000L))
        dao.recordPlay(PlayHistory(trackId = 2L, playedAt = 3000L))
        dao.recordPlay(PlayHistory(trackId = 3L, playedAt = 2000L))
        val history = dao.getRecentHistory().first()
        assertEquals(listOf(2L, 3L, 1L), history.map { it.trackId })
    }

    @Test
    fun `getPlayCount returns correct count`() = runTest {
        dao.recordPlay(PlayHistory(trackId = 7L))
        dao.recordPlay(PlayHistory(trackId = 7L))
        dao.recordPlay(PlayHistory(trackId = 7L))
        assertEquals(3, dao.getPlayCount(7L))
    }

    @Test
    fun `clearHistory empties the table`() = runTest {
        dao.recordPlay(PlayHistory(trackId = 1L))
        dao.clearHistory()
        assertEquals(0, dao.getRecentHistory().first().size)
    }
}
```

- [ ] **Step 6: Run HistoryDaoTest — expect PASS**

```bash
./gradlew :core:data:test --tests "*.HistoryDaoTest"
```

Expected: `4 tests completed, 0 failures`

- [ ] **Step 7: Run all core:data tests**

```bash
./gradlew :core:data:test
```

Expected: `13 tests completed, 0 failures`

- [ ] **Step 8: Commit**

```bash
git add core/data/src/test/
git commit -m "test: add Robolectric DAO tests (FavoriteDao, PlaylistDao, HistoryDao)"
```

---

## Task 6: Create UserDataRepository

**Files:**
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt`
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt`

- [ ] **Step 1: Create the interface**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt
package com.laconical.player.core.data

import com.laconical.player.core.data.db.entity.PlayHistory
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    // Favorites
    fun getAllFavoriteIds(): Flow<List<Long>>
    fun isFavorite(trackId: Long): Flow<Boolean>
    suspend fun addFavorite(trackId: Long)
    suspend fun removeFavorite(trackId: Long)

    // Playlists
    fun getAllPlaylists(): Flow<List<Playlist>>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun deletePlaylist(playlistId: Long)
    fun getTrackIdsForPlaylist(playlistId: Long): Flow<List<Long>>
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, position: Int)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    // History
    fun getRecentHistory(limit: Int = 50): Flow<List<PlayHistory>>
    suspend fun recordPlay(trackId: Long)
    suspend fun getPlayCount(trackId: Long): Int
    suspend fun clearHistory()
}
```

- [ ] **Step 2: Create the implementation**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt
package com.laconical.player.core.data

import com.laconical.player.core.data.db.dao.FavoriteDao
import com.laconical.player.core.data.db.dao.HistoryDao
import com.laconical.player.core.data.db.dao.PlaylistDao
import com.laconical.player.core.data.db.entity.FavoriteTrack
import com.laconical.player.core.data.db.entity.PlayHistory
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataRepositoryImpl @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: HistoryDao
) : UserDataRepository {

    override fun getAllFavoriteIds() = favoriteDao.getAllFavoriteIds()
    override fun isFavorite(trackId: Long) = favoriteDao.isFavorite(trackId)
    override suspend fun addFavorite(trackId: Long) = favoriteDao.addFavorite(FavoriteTrack(trackId))
    override suspend fun removeFavorite(trackId: Long) = favoriteDao.removeFavorite(trackId)

    override fun getAllPlaylists() = playlistDao.getAllPlaylists()
    override suspend fun createPlaylist(name: String) = playlistDao.createPlaylist(Playlist(name = name))
    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        playlistDao.updatePlaylist(Playlist(id = playlistId, name = newName))
    }
    override suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.clearPlaylist(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }
    override fun getTrackIdsForPlaylist(playlistId: Long) = playlistDao.getTrackIdsForPlaylist(playlistId)
    override suspend fun addTrackToPlaylist(playlistId: Long, trackId: Long, position: Int) =
        playlistDao.addTrackToPlaylist(PlaylistTrack(playlistId, trackId, position))
    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) =
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)

    override fun getRecentHistory(limit: Int) = historyDao.getRecentHistory(limit)
    override suspend fun recordPlay(trackId: Long) = historyDao.recordPlay(PlayHistory(trackId = trackId))
    override suspend fun getPlayCount(trackId: Long) = historyDao.getPlayCount(trackId)
    override suspend fun clearHistory() = historyDao.clearHistory()
}
```

- [ ] **Step 3: Verify compiles**

```bash
./gradlew :core:data:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepository.kt \
        core/data/src/main/kotlin/com/laconical/player/core/data/UserDataRepositoryImpl.kt
git commit -m "feat: add UserDataRepository interface and Room-backed implementation"
```

---

## Task 7: Wire Hilt DatabaseModule

**Files:**
- Create: `core/data/src/main/kotlin/com/laconical/player/core/data/di/DatabaseModule.kt`
- Modify: `core/data/src/main/kotlin/com/laconical/player/core/data/di/DataModule.kt`

- [ ] **Step 1: Create DatabaseModule**

```kotlin
// core/data/src/main/kotlin/com/laconical/player/core/data/di/DatabaseModule.kt
package com.laconical.player.core.data.di

import android.content.Context
import androidx.room.Room
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.dao.FavoriteDao
import com.laconical.player.core.data.db.dao.HistoryDao
import com.laconical.player.core.data.db.dao.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase =
        Room.databaseBuilder(context, MusicDatabase::class.java, "music_database.db").build()

    @Provides
    fun provideFavoriteDao(db: MusicDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun providePlaylistDao(db: MusicDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideHistoryDao(db: MusicDatabase): HistoryDao = db.historyDao()
}
```

- [ ] **Step 2: Add UserDataRepository binding to DataModule**

Replace the content of `core/data/src/main/kotlin/com/laconical/player/core/data/di/DataModule.kt`:

```kotlin
package com.laconical.player.core.data.di

import com.laconical.player.core.data.LocalMediaRepositoryImpl
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.data.UserDataRepository
import com.laconical.player.core.data.UserDataRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: LocalMediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindUserDataRepository(impl: UserDataRepositoryImpl): UserDataRepository
}
```

- [ ] **Step 3: Verify Hilt wires correctly (full debug build)**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add core/data/src/main/kotlin/com/laconical/player/core/data/di/
git commit -m "feat: wire Hilt DatabaseModule and UserDataRepository binding"
```

---

## Task 8: Create NavRoute and stub screens

**Files:**
- Create: `app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt`
- Create: `app/src/main/java/com/laconical/player/ui/screens/AlbumsScreen.kt`
- Create: `app/src/main/java/com/laconical/player/ui/screens/ArtistsScreen.kt`
- Create: `app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt`

- [ ] **Step 1: Create NavRoute**

```kotlin
// app/src/main/java/com/laconical/player/ui/navigation/NavRoute.kt
package com.laconical.player.ui.navigation

object NavRoute {
    const val TRACKS = "tracks"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val PLAYLISTS = "playlists"
}
```

- [ ] **Step 2: Create AlbumsScreen stub**

```kotlin
// app/src/main/java/com/laconical/player/ui/screens/AlbumsScreen.kt
package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AlbumsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Albums — coming soon", style = MaterialTheme.typography.bodyLarge)
    }
}
```

- [ ] **Step 3: Create ArtistsScreen stub**

```kotlin
// app/src/main/java/com/laconical/player/ui/screens/ArtistsScreen.kt
package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun ArtistsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Artists — coming soon", style = MaterialTheme.typography.bodyLarge)
    }
}
```

- [ ] **Step 4: Create PlaylistsScreen stub**

```kotlin
// app/src/main/java/com/laconical/player/ui/screens/PlaylistsScreen.kt
package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaylistsScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Playlists — coming soon", style = MaterialTheme.typography.bodyLarge)
    }
}
```

- [ ] **Step 5: Verify compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/navigation/ \
        app/src/main/java/com/laconical/player/ui/screens/
git commit -m "feat: add NavRoute definitions and stub screens for Albums/Artists/Playlists"
```

---

## Task 9: Lift state in LaconicalBottomNav

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt`

The current component owns `selectedItem` internally and never tells anyone about tab changes. We need to lift this state so `LibraryScreen` can drive the `NavController`.

- [ ] **Step 1: Update LaconicalBottomNav signature and internals**

Replace the entire file content with:

```kotlin
package com.laconical.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import com.laconical.player.ui.navigation.NavRoute

private data class NavItem(val label: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun LaconicalBottomNav(
    selectedRoute: String,
    onTabSelected: (String) -> Unit,
    dynamicColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val items = remember {
        listOf(
            NavItem("Tracks", NavRoute.TRACKS, Icons.Outlined.MusicNote),
            NavItem("Albums", NavRoute.ALBUMS, Icons.Outlined.Album),
            NavItem("Artists", NavRoute.ARTISTS, Icons.Outlined.Person),
            NavItem("Playlists", NavRoute.PLAYLISTS, Icons.Outlined.QueueMusic)
        )
    }

    val navColor = if (dynamicColor != null) {
        val alpha = 0.35f
        Color(
            red = dynamicColor.red * alpha,
            green = dynamicColor.green * alpha,
            blue = dynamicColor.blue * alpha,
            alpha = 1f
        )
    } else {
        Color(0xFF0D0D10)
    }

    val iconBaseColor = if (dynamicColor != null) {
        Color(
            red = (dynamicColor.red * 0.3f + 0.7f).coerceIn(0f, 1f),
            green = (dynamicColor.green * 0.3f + 0.7f).coerceIn(0f, 1f),
            blue = (dynamicColor.blue * 0.3f + 0.7f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else {
        Color.White
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF000000))
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(navColor.copy(alpha = 0.35f), Color.Black),
                    center = Offset(40f, -50f),
                    radius = 950f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = selectedRoute == item.route
                    val itemColor = if (isSelected) iconBaseColor else Color(0xFF666666)

                    val yOffset by animateDpAsState(
                        targetValue = if (isSelected) (-4).dp else 0.dp,
                        animationSpec = tween(300, easing = FastOutSlowInEasing),
                        label = "iconOffsetAnim_${item.route}"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onTabSelected(item.route) }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = itemColor,
                            modifier = Modifier.offset(y = yOffset)
                        )
                        Text(
                            text = item.label,
                            color = itemColor,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: compile error at the `LaconicalBottomNav(...)` call site in `LibraryScreen.kt` — the old signature is gone. This is expected and will be fixed in Task 10.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/components/LaconicalBottomNav.kt
git commit -m "refactor: lift selectedRoute state out of LaconicalBottomNav"
```

---

## Task 10: Wire NavHost in LibraryScreen

**Files:**
- Modify: `app/src/main/java/com/laconical/player/ui/LibraryScreen.kt`

`LibraryScreen` currently renders a `LazyColumn` of tracks unconditionally and passes no route state to `LaconicalBottomNav`. We need to:

1. Create a `NavController` at the top of `LibraryScreen`
2. Wrap the content area in a `NavHost` with four routes
3. Move the existing track list `LazyColumn` into the `NavRoute.TRACKS` composable
4. Add routes for Albums, Artists, Playlists pointing to the stub screens
5. Pass `currentDestination` + `onTabSelected` to `LaconicalBottomNav`

- [ ] **Step 1: Add NavController and NavHost imports, wire navigation**

Find the line in `LibraryScreen.kt` where `LaconicalBottomNav` is called (search for `LaconicalBottomNav(`). The call currently looks like:

```kotlin
LaconicalBottomNav(
    dynamicColor = dominantColor,
    modifier = Modifier.align(Alignment.BottomCenter)
)
```

Replace it with:

```kotlin
LaconicalBottomNav(
    selectedRoute = navController.currentBackStackEntryAsState().value?.destination?.route ?: NavRoute.TRACKS,
    onTabSelected = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    },
    dynamicColor = dominantColor,
    modifier = Modifier.align(Alignment.BottomCenter)
)
```

- [ ] **Step 2: Find the LazyColumn block and wrap it in a NavHost**

In `LibraryScreen.kt`, locate the `LazyColumn` that renders the track list. It will be inside the `BottomSheetScaffold` content lambda, above `MiniPlayer` and `LaconicalBottomNav`. Wrap it in a `NavHost`:

Find (approximately — match the exact structure in the file):
```kotlin
LazyColumn(
    state = listState,
    // ...
) {
    // track items
}
```

Wrap it so the structure becomes:

```kotlin
NavHost(
    navController = navController,
    startDestination = NavRoute.TRACKS,
    modifier = Modifier.fillMaxSize() // keep existing modifier
) {
    composable(NavRoute.TRACKS) {
        LazyColumn(
            state = listState,
            // ... keep all existing content unchanged
        ) {
            // ... keep all existing items unchanged
        }
    }
    composable(NavRoute.ALBUMS) { AlbumsScreen() }
    composable(NavRoute.ARTISTS) { ArtistsScreen() }
    composable(NavRoute.PLAYLISTS) { PlaylistsScreen() }
}
```

- [ ] **Step 3: Add the missing imports at the top of LibraryScreen.kt**

Add these imports (after the existing import block):

```kotlin
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.laconical.player.ui.navigation.NavRoute
import com.laconical.player.ui.screens.AlbumsScreen
import com.laconical.player.ui.screens.ArtistsScreen
import com.laconical.player.ui.screens.PlaylistsScreen
```

- [ ] **Step 4: Declare navController at the top of the LibraryScreen composable**

Find the first line inside `fun LibraryScreen(` (after the opening brace). Add:

```kotlin
val navController = rememberNavController()
```

- [ ] **Step 5: Build the full app**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Run all tests**

```bash
./gradlew :core:data:test :core:media:test
```

Expected: all tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/laconical/player/ui/LibraryScreen.kt
git commit -m "feat: wire NavHost and NavController in LibraryScreen — bottom nav tabs now functional"
```

---

## Self-Review

### Spec coverage check
- [x] Room entities (FavoriteTrack, Playlist, PlaylistTrack, PlayHistory) — Task 2
- [x] DAOs (FavoriteDao, PlaylistDao, HistoryDao) — Task 3
- [x] MusicDatabase — Task 4
- [x] UserDataRepository interface + impl — Task 6
- [x] Hilt DatabaseModule + binding — Task 7
- [x] NavRoute definitions — Task 8
- [x] Stub screens (Albums, Artists, Playlists) — Task 8
- [x] LaconicalBottomNav state lift — Task 9
- [x] NavHost wired in LibraryScreen — Task 10
- [x] Test infrastructure added — Task 1
- [x] DAO tests — Task 5

### Type consistency check
- `FavoriteTrack.trackId: Long` → used as `Long` throughout FavoriteDao and UserDataRepositoryImpl ✓
- `Playlist.id: Long` → returned from `createPlaylist()` and used in all playlist operations ✓
- `PlaylistTrack(playlistId, trackId, position)` → all three fields used consistently ✓
- `PlayHistory.trackId: Long` → consistent throughout HistoryDao ✓
- `NavRoute.TRACKS/ALBUMS/ARTISTS/PLAYLISTS` → used in LaconicalBottomNav items list and NavHost composable routes ✓

### No placeholders
Scanned — no TBD, TODO, "similar to task N", or vague steps found.
