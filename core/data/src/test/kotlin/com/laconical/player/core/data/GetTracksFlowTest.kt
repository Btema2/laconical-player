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
            require(batchSize > 0) { "batchSize must be > 0, was $batchSize" }
            val acc = mutableListOf<Track>()
            for (track in allTracks) {
                acc.add(track)
                if (acc.size % batchSize == 0) emit(acc.toList())
            }
            if (acc.isNotEmpty() && acc.size % batchSize != 0) emit(acc.toList())
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

        // Each emission is an ordered prefix of the full list (growing snapshot, not a set)
        assertEquals(tracks.subList(0, 25), emissions[0])
        assertEquals(tracks, emissions[2])
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

    @Test
    fun `getTracksFlow emits no duplicate when track count is exact multiple of batchSize`() = runTest {
        val tracks = (1L..50L).map { track(it) }
        val repo = FakeMediaRepository(tracks)
        val emissions = repo.getTracksFlow(batchSize = 25).toList()

        assertEquals(2, emissions.size)
        assertEquals(25, emissions[0].size)
        assertEquals(50, emissions[1].size)
    }

    @Test
    fun `getTracksFlow throws for non-positive batchSize`() = runTest {
        val repo = FakeMediaRepository((1L..5L).map { track(it) })
        try {
            repo.getTracksFlow(batchSize = 0).toList()
            assertTrue("Expected IllegalArgumentException", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("batchSize") == true)
        }
    }

    @Test
    fun `getTracksFlow with batchSize 1 emits one item per track`() = runTest {
        val tracks = (1L..3L).map { track(it) }
        val repo = FakeMediaRepository(tracks)
        val emissions = repo.getTracksFlow(batchSize = 1).toList()

        assertEquals(3, emissions.size)
        assertEquals(listOf(tracks[0]), emissions[0])
        assertEquals(tracks, emissions[2])
    }
}
