package com.laconical.player.core.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLibScorerTest {

    private fun candidate(
        title: String = "Song Title",
        artist: String = "The Artist",
        album: String = "The Album",
        durationSec: Double = 200.0,
        instrumental: Boolean = false,
        synced: String? = "[00:01.00]line",
        plain: String? = "line"
    ) = LrcLibResult(
        trackName = title,
        artistName = artist,
        albumName = album,
        durationSec = durationSec,
        instrumental = instrumental,
        plainLyrics = plain,
        syncedLyrics = synced
    )

    private val query = LyricsQuery(
        title = "Song Title",
        artist = "The Artist",
        album = "The Album",
        durationMs = 200_000L
    )

    @Test
    fun `correct match beats distractors`() {
        val correct = candidate()
        val wrongArtist = candidate(artist = "Someone Else")
        val wrongDuration = candidate(durationSec = 400.0)
        val best = LrcLibScorer.pickBest(listOf(wrongArtist, wrongDuration, correct), query)
        assertEquals(correct, best)
    }

    @Test
    fun `synced result preferred over plain at equal metadata`() {
        val syncedResult = candidate(synced = "[00:01.00]x")
        val plainOnly = candidate(synced = null)
        val best = LrcLibScorer.pickBest(listOf(plainOnly, syncedResult), query)
        assertEquals(syncedResult, best)
    }

    @Test
    fun `duration far outside tolerance is heavily penalised`() {
        val near = LrcLibScorer.score(candidate(durationSec = 201.0), query)
        val far = LrcLibScorer.score(candidate(durationSec = 400.0), query)
        assertTrue(near > far)
        assertTrue(far < near - 50)
    }

    @Test
    fun `empty candidate list returns null`() {
        assertNull(LrcLibScorer.pickBest(emptyList(), query))
    }

    @Test
    fun `below-threshold candidate is rejected`() {
        val junk = candidate(title = "Completely Different", artist = "Nobody", durationSec = 999.0)
        assertNull(LrcLibScorer.pickBest(listOf(junk), query))
    }

    @Test
    fun `case-insensitive title and artist match`() {
        val upper = candidate(title = "SONG TITLE", artist = "THE ARTIST")
        assertTrue(LrcLibScorer.score(upper, query) >= 150)
    }

    @Test
    fun `contains match scores lower than exact`() {
        val exact = LrcLibScorer.score(candidate(), query)
        val partial = LrcLibScorer.score(candidate(title = "Song Title (Remastered 2024)"), query)
        assertTrue(exact > partial)
        assertTrue(partial > LrcLibScorer.score(candidate(title = "Unrelated"), query))
    }

    @Test
    fun `instrumental candidate with no lyrics text is still eligible`() {
        val instrumental = candidate(instrumental = true, synced = null, plain = null)
        assertEquals(instrumental, LrcLibScorer.pickBest(listOf(instrumental), query))
    }

    @Test
    fun `candidate with no lyrics and not instrumental is filtered out`() {
        val empty = candidate(synced = null, plain = null)
        assertNull(LrcLibScorer.pickBest(listOf(empty), query))
    }
}
