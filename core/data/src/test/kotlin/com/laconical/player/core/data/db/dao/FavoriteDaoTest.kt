package com.laconical.player.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.entity.FavoriteTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
        dao.deleteStaleTrackIds(setOf(10L, 99L))
        assertTrue(dao.isFavorite(10L).first())
    }
}
