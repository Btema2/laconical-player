package com.laconical.player.core.data

import com.laconical.player.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PlaybackSessionResolveTest {

    private fun track(id: Long) = Track(
        id = id, title = "T$id", artist = "A", album = "B",
        durationMs = 1000L, mediaUri = "content://$id",
        albumArtUri = null, dataPath = null
    )

    private val t1 = track(1)
    private val t2 = track(2)
    private val t3 = track(3)

    @Test
    fun `all ids present returns full list with same index`() {
        val saved = PlaybackSession(trackIds = listOf(1L, 2L, 3L), index = 1, shuffle = false, repeat = 0)
        val result = resolveSession(saved, mapOf(1L to t1, 2L to t2, 3L to t3))
        assertEquals(ResolvedSession(listOf(t1, t2, t3), 1), result)
    }

    @Test
    fun `some ids deleted keeps surviving tracks and remaps current index`() {
        // saved index=2 => current is track 3; byId has tracks 1 and 3 only
        val saved = PlaybackSession(trackIds = listOf(1L, 2L, 3L), index = 2, shuffle = false, repeat = 0)
        val result = resolveSession(saved, mapOf(1L to t1, 3L to t3))
        // track 3 survives at position 1 in filtered list
        assertEquals(ResolvedSession(listOf(t1, t3), 1), result)
    }

    @Test
    fun `current id deleted clamps to closest surviving position`() {
        // saved index=1 => current is track 2 (deleted); byId has tracks 1 and 3
        // survivingBeforeIndex = ids in take(1)=[1L] that survive = 1 → coerceIn(0,1) = 1
        val saved = PlaybackSession(trackIds = listOf(1L, 2L, 3L), index = 1, shuffle = false, repeat = 0)
        val result = resolveSession(saved, mapOf(1L to t1, 3L to t3))
        assertEquals(ResolvedSession(listOf(t1, t3), 1), result)
    }

    @Test
    fun `all ids deleted returns null`() {
        val saved = PlaybackSession(trackIds = listOf(1L, 2L, 3L), index = 1, shuffle = false, repeat = 0)
        val result = resolveSession(saved, emptyMap())
        assertNull(result)
    }

    @Test
    fun `empty trackIds list returns null`() {
        val saved = PlaybackSession(trackIds = emptyList(), index = 0, shuffle = false, repeat = 0)
        val result = resolveSession(saved, mapOf(1L to t1))
        assertNull(result)
    }

    @Test
    fun `index out of bounds too large clamps to last index`() {
        // index=99, getOrNull(99)=null → else branch: take(99) has all 3 ids surviving → survivingBeforeIndex=3 → coerceIn(0,2)=2
        val saved = PlaybackSession(trackIds = listOf(1L, 2L, 3L), index = 99, shuffle = false, repeat = 0)
        val result = resolveSession(saved, mapOf(1L to t1, 2L to t2, 3L to t3))
        assertEquals(ResolvedSession(listOf(t1, t2, t3), 2), result)
    }

    @Test
    fun `negative index clamps to zero`() {
        // index=-1, getOrNull(-1)=null → else branch: take(coerceAtLeast(0)=0)=empty → survivingBeforeIndex=0 → coerceIn(0,2)=0
        val saved = PlaybackSession(trackIds = listOf(1L, 2L, 3L), index = -1, shuffle = false, repeat = 0)
        val result = resolveSession(saved, mapOf(1L to t1, 2L to t2, 3L to t3))
        assertEquals(ResolvedSession(listOf(t1, t2, t3), 0), result)
    }

    @Test
    fun `single track round-trips correctly`() {
        val saved = PlaybackSession(trackIds = listOf(1L), index = 0, shuffle = false, repeat = 0)
        val result = resolveSession(saved, mapOf(1L to t1))
        assertEquals(ResolvedSession(listOf(t1), 0), result)
    }
}
