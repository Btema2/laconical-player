package com.laconical.player.core.data.lyrics

import kotlin.math.abs

/** Query terms a candidate is scored against. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long
)

/**
 * Scores LRCLIB candidates against the local track's metadata and picks the best match —
 * never the first result. Below-threshold candidates are rejected outright: a wrong match
 * is worse than no match.
 */
object LrcLibScorer {

    private const val TITLE_EXACT = 100
    private const val TITLE_CONTAINS = 40
    private const val ARTIST_EXACT = 50
    private const val ARTIST_CONTAINS = 20
    private const val ALBUM_EXACT = 10
    private const val SYNCED_BONUS = 25
    private const val DURATION_MAX = 30
    private const val DURATION_OUTSIDE_TOLERANCE = -50
    private const val SYNCED_TOLERANCE_MS = 5_000L
    private const val PLAIN_TOLERANCE_MS = 15_000L

    /** Minimum score a candidate must clear to be accepted at all. */
    private const val ACCEPTANCE_THRESHOLD = 60

    fun score(candidate: LrcLibResult, query: LyricsQuery): Int {
        var score = 0

        val title = query.title.normalized()
        val candidateTitle = candidate.trackName.normalized()
        score += when {
            candidateTitle == title -> TITLE_EXACT
            title.isNotEmpty() && (candidateTitle.contains(title) || title.contains(candidateTitle)) ->
                TITLE_CONTAINS
            else -> 0
        }

        val artist = query.artist.normalized()
        val candidateArtist = candidate.artistName.normalized()
        score += when {
            candidateArtist == artist -> ARTIST_EXACT
            artist.isNotEmpty() && (candidateArtist.contains(artist) || artist.contains(candidateArtist)) ->
                ARTIST_CONTAINS
            else -> 0
        }

        if (!query.album.isNullOrBlank() && candidate.albumName.normalized() == query.album.normalized()) {
            score += ALBUM_EXACT
        }

        val hasSynced = candidate.syncedLyrics != null
        if (hasSynced) score += SYNCED_BONUS

        if (query.durationMs > 0 && candidate.durationSec > 0) {
            val diffMs = abs(candidate.durationSec * 1000 - query.durationMs).toLong()
            val toleranceMs = if (hasSynced) SYNCED_TOLERANCE_MS else PLAIN_TOLERANCE_MS
            score += if (diffMs <= toleranceMs) {
                (DURATION_MAX * (1.0 - diffMs.toDouble() / toleranceMs)).toInt()
            } else {
                DURATION_OUTSIDE_TOLERANCE
            }
        }

        return score
    }

    fun pickBest(candidates: List<LrcLibResult>, query: LyricsQuery): LrcLibResult? =
        candidates
            .filter { it.instrumental || it.syncedLyrics != null || it.plainLyrics != null }
            .maxByOrNull { score(it, query) }
            ?.takeIf { score(it, query) >= ACCEPTANCE_THRESHOLD }

    private fun String.normalized(): String = trim().lowercase()
}
