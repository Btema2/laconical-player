package com.laconical.player.core.model.lyrics

/**
 * A single lyrics line. [timestampMs] is null for unsynced (plain) lyrics.
 */
data class LyricsLine(
    val timestampMs: Long?,
    val text: String
)

/**
 * Where a [Lyrics] result came from, in the retrieval chain.
 */
enum class LyricsSource {
    /** Embedded in the audio file's metadata (ID3 USLT/SYLT, VorbisComment LYRICS). */
    EMBEDDED,

    /** A `.lrc` file sitting next to the audio file on device. */
    LOCAL_LRC,

    /** Fetched from the LRCLIB online API. */
    LRCLIB
}

/**
 * Parsed lyrics for a track.
 *
 * @param plain full lyrics as plain text (fallback rendering, always present when any
 *   lyrics exist unless [instrumental])
 * @param lines time-synced lines sorted by timestamp; empty when only plain lyrics exist
 * @param instrumental true when the track is known to have no lyrics at all
 */
data class Lyrics(
    val plain: String?,
    val lines: List<LyricsLine> = emptyList(),
    val instrumental: Boolean = false
) {
    /** True when line-level synced timestamps are available. */
    val synced: Boolean get() = lines.isNotEmpty()

    /** True when there is anything renderable at all. */
    val hasContent: Boolean get() = synced || !plain.isNullOrBlank()
}
