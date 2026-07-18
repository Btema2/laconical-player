package com.laconical.player.core.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.laconical.player.core.data.db.MusicDatabase
import com.laconical.player.core.data.db.entity.LyricsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricsDaoTest {

    private lateinit var db: MusicDatabase
    private lateinit var dao: LyricsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MusicDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.lyricsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(trackId: Long, plain: String? = "text") = LyricsEntity(
        trackId = trackId,
        plainLyrics = plain,
        syncedLyrics = null,
        source = "LRCLIB",
        fetchedAtMs = 1000L,
        instrumental = false
    )

    @Test
    fun `upsert then read round-trips`() = runTest {
        dao.upsert(entity(1L))
        assertEquals("text", dao.getByTrackId(1L)?.plainLyrics)
    }

    @Test
    fun `upsert replaces existing row`() = runTest {
        dao.upsert(entity(1L, plain = "old"))
        dao.upsert(entity(1L, plain = "new"))
        assertEquals("new", dao.getByTrackId(1L)?.plainLyrics)
    }

    @Test
    fun `missing track returns null`() = runTest {
        assertNull(dao.getByTrackId(99L))
    }

    @Test
    fun `delete removes row`() = runTest {
        dao.upsert(entity(1L))
        dao.delete(1L)
        assertNull(dao.getByTrackId(1L))
    }

    @Test
    fun `deleteStaleTrackIds keeps only live ids`() = runTest {
        dao.upsert(entity(1L))
        dao.upsert(entity(2L))
        dao.upsert(entity(3L))
        dao.deleteStaleTrackIds(setOf(1L, 3L))
        assertNull(dao.getByTrackId(2L))
        assertEquals(1L, dao.getByTrackId(1L)?.trackId)
        assertEquals(3L, dao.getByTrackId(3L)?.trackId)
    }

    @Test
    fun `observeByTrackId emits on upsert`() = runTest {
        assertNull(dao.observeByTrackId(1L).first())
        dao.upsert(entity(1L))
        assertEquals("text", dao.observeByTrackId(1L).first()?.plainLyrics)
    }
}
