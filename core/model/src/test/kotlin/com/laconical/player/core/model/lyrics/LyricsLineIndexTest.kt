package com.laconical.player.core.model.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsLineIndexTest {

    private val lines = listOf(
        LyricsLine(1_000L, "one"),
        LyricsLine(5_000L, "two"),
        LyricsLine(10_000L, "three")
    )

    @Test
    fun `empty list returns -1`() {
        assertEquals(-1, currentLineIndex(emptyList(), 5_000L))
    }

    @Test
    fun `before first line returns -1`() {
        assertEquals(-1, currentLineIndex(lines, 500L))
    }

    @Test
    fun `exact timestamp matches its line`() {
        assertEquals(1, currentLineIndex(lines, 5_000L))
    }

    @Test
    fun `between lines returns earlier line`() {
        assertEquals(0, currentLineIndex(lines, 4_999L))
        assertEquals(1, currentLineIndex(lines, 9_999L))
    }

    @Test
    fun `after last line returns last index`() {
        assertEquals(2, currentLineIndex(lines, 60_000L))
    }

    @Test
    fun `single line list`() {
        assertEquals(-1, currentLineIndex(listOf(LyricsLine(1_000L, "x")), 999L))
        assertEquals(0, currentLineIndex(listOf(LyricsLine(1_000L, "x")), 1_000L))
    }
}
