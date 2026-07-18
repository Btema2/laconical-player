package com.laconical.player.core.model.lyrics

import com.laconical.player.core.model.lyrics.Id3UsltParser.Id3LyricsResult
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Id3UsltParserTest {

    // ---- synthetic tag builders -------------------------------------------------------------

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte()
    )

    private fun be32(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun tag(major: Int, flags: Int = 0, body: ByteArray): ByteArray =
        byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), major.toByte(), 0, flags.toByte()) +
            synchsafe(body.size) + body

    private fun frame22(id: String, data: ByteArray): ByteArray {
        require(id.length == 3)
        val size = data.size
        return id.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(((size ushr 16) and 0xFF).toByte(), ((size ushr 8) and 0xFF).toByte(), (size and 0xFF).toByte()) +
            data
    }

    private fun frame23(id: String, data: ByteArray): ByteArray {
        require(id.length == 4)
        return id.toByteArray(Charsets.ISO_8859_1) + be32(data.size) + byteArrayOf(0, 0) + data
    }

    private fun frame24(id: String, data: ByteArray, formatFlags: Int = 0): ByteArray {
        require(id.length == 4)
        return id.toByteArray(Charsets.ISO_8859_1) + synchsafe(data.size) + byteArrayOf(0, formatFlags.toByte()) + data
    }

    /** USLT payload: encoding + "eng" + descriptor(terminated) + text. */
    private fun uslt(encoding: Int, text: String, descriptor: String = ""): ByteArray {
        val lang = "eng".toByteArray(Charsets.ISO_8859_1)
        return byteArrayOf(encoding.toByte()) + lang +
            encode(descriptor, encoding) + terminator(encoding) + encode(text, encoding)
    }

    /** SYLT payload: encoding + "eng" + timeFormat + contentType + descriptor + entries. */
    private fun sylt(encoding: Int, timeFormat: Int, entries: List<Pair<String, Int>>): ByteArray {
        val lang = "eng".toByteArray(Charsets.ISO_8859_1)
        var payload = byteArrayOf(encoding.toByte()) + lang +
            byteArrayOf(timeFormat.toByte(), 1) + terminator(encoding)
        for ((text, ts) in entries) {
            payload += encode(text, encoding) + terminator(encoding) + be32(ts)
        }
        return payload
    }

    private fun encode(text: String, encoding: Int): ByteArray = when (encoding) {
        0 -> text.toByteArray(Charsets.ISO_8859_1)
        1 -> byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        2 -> text.toByteArray(Charsets.UTF_16BE)
        else -> text.toByteArray(Charsets.UTF_8)
    }

    private fun terminator(encoding: Int): ByteArray =
        if (encoding == 1 || encoding == 2) byteArrayOf(0, 0) else byteArrayOf(0)

    /** Applies ID3 unsynchronisation encoding: inserts 0x00 after every 0xFF. */
    private fun applyUnsync(bytes: ByteArray): ByteArray {
        val out = mutableListOf<Byte>()
        for (b in bytes) {
            out += b
            if (b.toInt() and 0xFF == 0xFF) out += 0
        }
        return out.toByteArray()
    }

    private fun parse(bytes: ByteArray) = Id3UsltParser.parse(ByteArrayInputStream(bytes))

    // ---- USLT across versions and encodings -------------------------------------------------

    @Test
    fun `v2_3 USLT ISO-8859-1`() {
        val result = parse(tag(3, body = frame23("USLT", uslt(0, "Hello lyrics"))))
        assertEquals("Hello lyrics", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_3 USLT UTF-16 LE with BOM`() {
        val result = parse(tag(3, body = frame23("USLT", uslt(1, "Ünïcödé lyrics"))))
        assertEquals("Ünïcödé lyrics", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_4 USLT UTF-16BE`() {
        val result = parse(tag(4, body = frame24("USLT", uslt(2, "BE text"))))
        assertEquals("BE text", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_4 USLT UTF-8`() {
        val result = parse(tag(4, body = frame24("USLT", uslt(3, "utf8 ✓ lyrics"))))
        assertEquals("utf8 ✓ lyrics", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_2 ULT frame`() {
        val result = parse(tag(2, body = frame22("ULT", uslt(0, "old format"))))
        assertEquals("old format", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `descriptor is skipped`() {
        val result = parse(tag(3, body = frame23("USLT", uslt(0, "the text", descriptor = "desc"))))
        assertEquals("the text", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_4 synchsafe frame size over 127 bytes`() {
        val longText = "x".repeat(300) // frame > 127 bytes: synchsafe vs plain size diverge
        val result = parse(tag(4, body = frame24("USLT", uslt(3, longText))))
        assertEquals(longText, (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_4 data-length-indicator flag`() {
        val payload = uslt(3, "dli text")
        val withDli = be32(payload.size) + payload
        val result = parse(tag(4, body = frame24("USLT", withDli, formatFlags = 0x01)))
        assertEquals("dli text", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `frames before USLT are skipped`() {
        val tit2 = frame23("TIT2", byteArrayOf(0) + "Title".toByteArray(Charsets.ISO_8859_1))
        val result = parse(tag(3, body = tit2 + frame23("USLT", uslt(0, "after title"))))
        assertEquals("after title", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `first USLT frame wins`() {
        val body = frame23("USLT", uslt(0, "first")) + frame23("USLT", uslt(0, "second"))
        val result = parse(tag(3, body = body))
        assertEquals("first", (result as Id3LyricsResult.Unsynced).text)
    }

    // ---- unsynchronisation / extended header ------------------------------------------------

    @Test
    fun `v2_3 tag-level unsynchronisation is reversed`() {
        // Force 0xFF bytes into the payload via UTF-16LE BOM (FF FE).
        val body = frame23("USLT", uslt(1, "unsync ünïcödé"))
        val unsynced = applyUnsync(body)
        val result = parse(tag(3, flags = 0x80, body = unsynced))
        assertEquals("unsync ünïcödé", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_4 frame-level unsynchronisation is reversed`() {
        val payload = uslt(1, "frame unsync")
        val result = parse(tag(4, body = frame24("USLT", applyUnsync(payload), formatFlags = 0x02)))
        assertEquals("frame unsync", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_3 extended header is skipped`() {
        // v2.3 ext header: 4-byte size (excluding itself) = 6, then 6 bytes of ext data.
        val ext = be32(6) + ByteArray(6)
        val result = parse(tag(3, flags = 0x40, body = ext + frame23("USLT", uslt(0, "after ext"))))
        assertEquals("after ext", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `v2_4 extended header is skipped`() {
        // v2.4 ext header: synchsafe size including itself = 6 total.
        val ext = synchsafe(6) + byteArrayOf(1, 0)
        val result = parse(tag(4, flags = 0x40, body = ext + frame24("USLT", uslt(3, "after ext4"))))
        assertEquals("after ext4", (result as Id3LyricsResult.Unsynced).text)
    }

    // ---- SYLT -------------------------------------------------------------------------------

    @Test
    fun `SYLT with ms timestamps parses to synced lines`() {
        val body = frame23("SYLT", sylt(0, timeFormat = 2, entries = listOf("line one" to 1000, "line two" to 5000)))
        val result = parse(tag(3, body = body)) as Id3LyricsResult.Synced
        assertEquals(2, result.lines.size)
        assertEquals(1000L, result.lines[0].timestampMs)
        assertEquals("line one", result.lines[0].text)
        assertEquals(5000L, result.lines[1].timestampMs)
    }

    @Test
    fun `SYLT with mpeg-frame timestamps is ignored, falls back to USLT`() {
        val body = frame23("SYLT", sylt(0, timeFormat = 1, entries = listOf("x" to 1))) +
            frame23("USLT", uslt(0, "fallback"))
        val result = parse(tag(3, body = body))
        assertEquals("fallback", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `SYLT preferred over USLT when both present`() {
        val body = frame23("USLT", uslt(0, "plain")) +
            frame23("SYLT", sylt(0, timeFormat = 2, entries = listOf("synced" to 500)))
        val result = parse(tag(3, body = body))
        assertTrue(result is Id3LyricsResult.Synced)
    }

    @Test
    fun `SYLT newline-prefixed entries are trimmed`() {
        val body = frame23("SYLT", sylt(0, timeFormat = 2, entries = listOf("\nline" to 100)))
        val result = parse(tag(3, body = body)) as Id3LyricsResult.Synced
        assertEquals("line", result.lines[0].text)
    }

    // ---- failure modes ----------------------------------------------------------------------

    @Test
    fun `no ID3 tag returns null`() {
        assertNull(parse(byteArrayOf(0x4D, 0x5A, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)))
    }

    @Test
    fun `empty stream returns null`() {
        assertNull(parse(ByteArray(0)))
    }

    @Test
    fun `tag without lyrics frames returns null`() {
        val tit2 = frame23("TIT2", byteArrayOf(0) + "Title".toByteArray(Charsets.ISO_8859_1))
        assertNull(parse(tag(3, body = tit2)))
    }

    @Test
    fun `truncated frame does not crash`() {
        // Frame claims 1000 bytes but body ends after 10.
        val lying = "USLT".toByteArray(Charsets.ISO_8859_1) + be32(1000) + byteArrayOf(0, 0) + ByteArray(10)
        assertNull(parse(tag(3, body = lying)))
    }

    @Test
    fun `garbage frame id aborts gracefully`() {
        val garbage = byteArrayOf(0x01, 0x02, 0x03, 0x04) + be32(4) + byteArrayOf(0, 0) + ByteArray(4)
        assertNull(parse(tag(3, body = garbage)))
    }

    @Test
    fun `padding after frames stops the loop`() {
        val body = frame23("USLT", uslt(0, "padded")) + ByteArray(64)
        val result = parse(tag(3, body = body))
        assertEquals("padded", (result as Id3LyricsResult.Unsynced).text)
    }

    @Test
    fun `blank USLT text returns null`() {
        assertNull(parse(tag(3, body = frame23("USLT", uslt(0, "   ")))))
    }
}
