package com.laconical.player.core.model.lyrics

/**
 * Parser for LRC-format lyrics text.
 *
 * Supported:
 *  - standard timestamps `[mm:ss.xx]` (1-3 fraction digits, `.` or `:` separator, minutes > 59 OK)
 *  - multiple timestamps per line: `[00:12.00][01:15.00]repeated chorus line`
 *  - global `[offset:±ms]` tag (positive offset shifts lyrics earlier, per LRC convention)
 *  - enhanced word-level `<mm:ss.xx>` tags are stripped from line text (line-level sync only)
 *  - metadata header tags (`[ar:]`, `[ti:]`, `[al:]`, ...) are ignored
 *
 * Malformed input never throws: lines without a valid timestamp are folded into the plain-text
 * fallback, and a file with zero timestamps parses as plain (unsynced) lyrics.
 */
object LrcParser {

    private val TIMESTAMP_PREFIX = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
    private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG = Regex("""^\[[a-zA-Z#][^\[\]]*]\s*$""")

    fun parse(raw: String): Lyrics {
        if (raw.isBlank()) return Lyrics(plain = null)

        val offsetMs = OFFSET_TAG.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val syncedLines = mutableListOf<LyricsLine>()
        val plainFallback = mutableListOf<String>()

        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            // Collect all leading timestamps.
            val timestamps = mutableListOf<Long>()
            var rest = line
            while (true) {
                val match = TIMESTAMP_PREFIX.find(rest) ?: break
                val (min, sec, frac) = match.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                timestamps += min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
                rest = rest.substring(match.value.length)
            }

            if (timestamps.isEmpty()) {
                // No leading timestamp: metadata tag → ignore; anything else → plain fallback.
                if (!METADATA_TAG.matches(line)) plainFallback += line
                continue
            }

            val text = WORD_TAG.replace(rest, "").trim()
            for (ts in timestamps) {
                // LRC convention: positive offset shifts lyrics earlier.
                syncedLines += LyricsLine((ts - offsetMs).coerceAtLeast(0L), text)
            }
        }

        syncedLines.sortBy { it.timestampMs }

        return when {
            syncedLines.isNotEmpty() -> Lyrics(
                plain = syncedLines.joinToString("\n") { it.text }.ifBlank { null },
                lines = syncedLines
            )
            plainFallback.isNotEmpty() -> Lyrics(plain = plainFallback.joinToString("\n"))
            else -> Lyrics(plain = null)
        }
    }
}
