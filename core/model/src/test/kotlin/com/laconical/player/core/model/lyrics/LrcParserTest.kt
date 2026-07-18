package com.laconical.player.core.model.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `single timestamp line parses`() {
        val lyrics = LrcParser.parse("[00:12.34]Hello world")
        assertTrue(lyrics.synced)
        assertEquals(1, lyrics.lines.size)
        assertEquals(12_340L, lyrics.lines[0].timestampMs)
        assertEquals("Hello world", lyrics.lines[0].text)
    }

    @Test
    fun `multiple timestamps on one line emit one line each, sorted`() {
        val lyrics = LrcParser.parse("[01:00.00][00:30.00]Chorus")
        assertEquals(2, lyrics.lines.size)
        assertEquals(30_000L, lyrics.lines[0].timestampMs)
        assertEquals(60_000L, lyrics.lines[1].timestampMs)
        assertTrue(lyrics.lines.all { it.text == "Chorus" })
    }

    @Test
    fun `positive offset shifts timestamps earlier`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:10.00]Line")
        assertEquals(9_500L, lyrics.lines[0].timestampMs)
    }

    @Test
    fun `negative offset shifts timestamps later`() {
        val lyrics = LrcParser.parse("[offset:-500]\n[00:10.00]Line")
        assertEquals(10_500L, lyrics.lines[0].timestampMs)
    }

    @Test
    fun `offset never produces negative timestamps`() {
        val lyrics = LrcParser.parse("[offset:5000]\n[00:01.00]Line")
        assertEquals(0L, lyrics.lines[0].timestampMs)
    }

    @Test
    fun `word-level tags are stripped`() {
        val lyrics = LrcParser.parse("[00:10.00]<00:10.00>Hello <00:10.50>world")
        assertEquals("Hello world", lyrics.lines[0].text)
    }

    @Test
    fun `metadata header tags are ignored`() {
        val lyrics = LrcParser.parse("[ar:Artist]\n[ti:Title]\n[al:Album]\n[by:Someone]\n[length:3:20]\n[00:05.00]Real line")
        assertEquals(1, lyrics.lines.size)
        assertEquals("Real line", lyrics.lines[0].text)
    }

    @Test
    fun `malformed lines fold into plain fallback without crash`() {
        val lyrics = LrcParser.parse("just some text\nmore text")
        assertFalse(lyrics.synced)
        assertEquals("just some text\nmore text", lyrics.plain)
    }

    @Test
    fun `zero timestamps means plain lyrics`() {
        val lyrics = LrcParser.parse("Verse one\nVerse two\n\nVerse three")
        assertFalse(lyrics.synced)
        assertEquals("Verse one\nVerse two\nVerse three", lyrics.plain)
    }

    @Test
    fun `empty input gives empty lyrics`() {
        val lyrics = LrcParser.parse("")
        assertNull(lyrics.plain)
        assertFalse(lyrics.synced)
        assertFalse(lyrics.hasContent)
    }

    @Test
    fun `two-digit fraction is centiseconds`() {
        assertEquals(12_340L, LrcParser.parse("[00:12.34]x").lines[0].timestampMs)
    }

    @Test
    fun `three-digit fraction is milliseconds`() {
        assertEquals(12_345L, LrcParser.parse("[00:12.345]x").lines[0].timestampMs)
    }

    @Test
    fun `one-digit fraction is tenths`() {
        assertEquals(12_300L, LrcParser.parse("[00:12.3]x").lines[0].timestampMs)
    }

    @Test
    fun `no fraction parses`() {
        assertEquals(12_000L, LrcParser.parse("[00:12]x").lines[0].timestampMs)
    }

    @Test
    fun `colon fraction separator parses`() {
        assertEquals(12_340L, LrcParser.parse("[00:12:34]x").lines[0].timestampMs)
    }

    @Test
    fun `minutes over 59 parse`() {
        assertEquals(3_600_000L + 5_000L, LrcParser.parse("[60:05.00]x").lines[0].timestampMs)
    }

    @Test
    fun `CRLF line endings parse`() {
        val lyrics = LrcParser.parse("[00:01.00]one\r\n[00:02.00]two")
        assertEquals(2, lyrics.lines.size)
    }

    @Test
    fun `plain text is derived from synced lines`() {
        val lyrics = LrcParser.parse("[00:01.00]one\n[00:02.00]two")
        assertEquals("one\ntwo", lyrics.plain)
    }

    @Test
    fun `empty timestamped lines are kept as gap markers`() {
        val lyrics = LrcParser.parse("[00:01.00]one\n[00:02.00]\n[00:03.00]two")
        assertEquals(3, lyrics.lines.size)
        assertEquals("", lyrics.lines[1].text)
    }

    @Test
    fun `unsorted input is sorted by timestamp`() {
        val lyrics = LrcParser.parse("[00:10.00]late\n[00:01.00]early")
        assertEquals("early", lyrics.lines[0].text)
        assertEquals("late", lyrics.lines[1].text)
    }
}
