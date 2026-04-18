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
        val history = dao.getRecentHistory(50).first()
        assertEquals(1, history.size)
        assertEquals(5L, history[0].trackId)
    }

    @Test
    fun `getRecentHistory returns newest first`() = runTest {
        dao.recordPlay(PlayHistory(trackId = 1L, playedAt = 1000L))
        dao.recordPlay(PlayHistory(trackId = 2L, playedAt = 3000L))
        dao.recordPlay(PlayHistory(trackId = 3L, playedAt = 2000L))
        val history = dao.getRecentHistory(50).first()
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
        assertEquals(0, dao.getRecentHistory(50).first().size)
    }
}
