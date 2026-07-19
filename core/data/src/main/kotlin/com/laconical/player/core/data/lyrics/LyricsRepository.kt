package com.laconical.player.core.data.lyrics

import com.laconical.player.core.model.Track
import com.laconical.player.core.model.lyrics.Lyrics
import com.laconical.player.core.model.lyrics.LyricsSource

/** Outcome of a lyrics lookup. */
sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics, val source: LyricsSource) : LyricsResult

    /** The track is known to have no lyrics (LRCLIB instrumental flag) — never refetched. */
    data object Instrumental : LyricsResult

    data object NotFound : LyricsResult

    /** The network stage failed (offline, timeout, server error) — distinct from a miss. */
    data object NetworkError : LyricsResult
}

interface LyricsRepository {

    /**
     * Runs the retrieval chain for [track].
     *
     * @param allowNetwork whether the LRCLIB stage may run (user opt-in AND lyrics UI open)
     * @param forceRefresh bypass memory + Room caches and re-run the full chain
     */
    suspend fun getLyrics(track: Track, allowNetwork: Boolean, forceRefresh: Boolean = false): LyricsResult

    /** Drops persisted lyrics for tracks that no longer exist in MediaStore. */
    suspend fun purgeStaleTrackIds(liveIds: Set<Long>)
}
