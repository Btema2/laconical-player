package com.laconical.player.core.data.lyrics

import com.laconical.player.core.data.db.dao.LyricsDao
import com.laconical.player.core.data.db.entity.LyricsEntity
import com.laconical.player.core.model.Track
import com.laconical.player.core.model.lyrics.LrcParser
import com.laconical.player.core.model.lyrics.Lyrics
import com.laconical.player.core.model.lyrics.LyricsLine
import com.laconical.player.core.model.lyrics.LyricsSource
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Layered lyrics retrieval: in-memory LRU → Room → local sources (embedded tags,
 * sibling .lrc) → LRCLIB API.
 *
 * Architecture inspired by PixelPlayerOSS (https://github.com/PixelPlayerHQ/PixelPlayerOSS)
 * and Namida (https://github.com/namidaco/namida) — chain layering, scored remote matching,
 * and instrumental-marker persistence are modelled on their lyrics stacks (no code copied).
 *
 * Persistence policy: Found and Instrumental results are written to Room; NotFound never is,
 * so a track can gain lyrics later (new .lrc file, LRCLIB catalogue growth) without being
 * permanently walled off. NetworkError is never cached anywhere.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    private val lyricsDao: LyricsDao,
    private val embeddedSource: EmbeddedLyricsSource,
    private val siblingLrcSource: SiblingLrcSource,
    private val lrcLibClient: LrcLibClient,
    private val settingsStore: LyricsSettingsStore
) : LyricsRepository {

    private val cacheMutex = Mutex()
    private val lruCache = object : LinkedHashMap<Long, LyricsResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, LyricsResult>): Boolean =
            size > LRU_SIZE
    }

    override suspend fun getLyrics(track: Track, allowNetwork: Boolean, forceRefresh: Boolean): LyricsResult {
        if (!forceRefresh) {
            cacheMutex.withLock { lruCache[track.id] }?.let { return it }
            lyricsDao.getByTrackId(track.id)?.toResult()?.let { result ->
                cache(track.id, result)
                return result
            }
        }

        val priority = settingsStore.sourcePriority.first()
        var networkFailed = false

        suspend fun tryNetwork(): LyricsResult? = try {
            fetchFromLrcLib(track)
        } catch (_: IOException) {
            networkFailed = true
            null
        }

        if (allowNetwork && priority == LyricsSourcePriority.API_FIRST) {
            tryNetwork()?.let { return persistAndCache(track.id, it) }
        }

        val localSources: List<suspend () -> Pair<Lyrics?, LyricsSource>> = when (priority) {
            LyricsSourcePriority.LOCAL_FIRST -> listOf(
                { siblingLrcSource.fetch(track) to LyricsSource.LOCAL_LRC },
                { embeddedSource.fetch(track) to LyricsSource.EMBEDDED }
            )
            else -> listOf(
                { embeddedSource.fetch(track) to LyricsSource.EMBEDDED },
                { siblingLrcSource.fetch(track) to LyricsSource.LOCAL_LRC }
            )
        }
        for (source in localSources) {
            val (lyrics, sourceKind) = source()
            if (lyrics != null && lyrics.hasContent) {
                return persistAndCache(track.id, LyricsResult.Found(lyrics, sourceKind))
            }
        }

        if (allowNetwork && priority != LyricsSourcePriority.API_FIRST) {
            tryNetwork()?.let { return persistAndCache(track.id, it) }
        }

        return if (networkFailed) LyricsResult.NetworkError else LyricsResult.NotFound
    }

    override suspend fun purgeStaleTrackIds(liveIds: Set<Long>) {
        lyricsDao.deleteStaleTrackIds(liveIds)
        cacheMutex.withLock { lruCache.keys.retainAll { it in liveIds } }
    }

    // ---- LRCLIB ---------------------------------------------------------------------------

    /**
     * Three strategies, most-specific first: exact metadata lookup, artist+title search,
     * then a cleaned title-only search. Search candidates are score-ranked — best match
     * wins, never the first result.
     */
    private suspend fun fetchFromLrcLib(track: Track): LyricsResult? {
        val query = LyricsQuery(
            title = track.title,
            artist = track.artist,
            album = track.album.ifBlank { null },
            durationMs = track.durationMs
        )

        lrcLibClient.getExact(
            artist = track.artist,
            title = track.title,
            album = track.album,
            durationSec = (track.durationMs / 1000).toInt()
        )?.let { return it.toResult() }

        LrcLibScorer.pickBest(lrcLibClient.search(track.artist, track.title), query)
            ?.let { return it.toResult() }

        val cleanedTitle = track.title.replace(PARENTHETICAL, "").trim()
        if (cleanedTitle.isNotEmpty() && cleanedTitle != track.title) {
            LrcLibScorer.pickBest(lrcLibClient.search("", cleanedTitle), query)
                ?.let { return it.toResult() }
        }
        return null
    }

    private fun LrcLibResult.toResult(): LyricsResult? {
        if (instrumental) return LyricsResult.Instrumental
        syncedLyrics?.let { raw ->
            val parsed = LrcParser.parse(raw)
            if (parsed.synced) return LyricsResult.Found(parsed, LyricsSource.LRCLIB)
        }
        plainLyrics?.let { plain ->
            return LyricsResult.Found(Lyrics(plain = plain.trim()), LyricsSource.LRCLIB)
        }
        return null
    }

    // ---- caching / persistence ------------------------------------------------------------

    private suspend fun persistAndCache(trackId: Long, result: LyricsResult): LyricsResult {
        when (result) {
            is LyricsResult.Found -> lyricsDao.upsert(
                LyricsEntity(
                    trackId = trackId,
                    plainLyrics = result.lyrics.plain,
                    syncedLyrics = result.lyrics.lines.takeIf { it.isNotEmpty() }?.toLrcText(),
                    source = result.source.name,
                    fetchedAtMs = System.currentTimeMillis(),
                    instrumental = false
                )
            )
            LyricsResult.Instrumental -> lyricsDao.upsert(
                LyricsEntity(
                    trackId = trackId,
                    plainLyrics = null,
                    syncedLyrics = null,
                    source = LyricsSource.LRCLIB.name,
                    fetchedAtMs = System.currentTimeMillis(),
                    instrumental = true
                )
            )
            LyricsResult.NotFound, LyricsResult.NetworkError -> return result // never persisted
        }
        cache(trackId, result)
        return result
    }

    private suspend fun cache(trackId: Long, result: LyricsResult) {
        cacheMutex.withLock { lruCache[trackId] = result }
    }

    private fun LyricsEntity.toResult(): LyricsResult? {
        if (instrumental) return LyricsResult.Instrumental
        val source = runCatching { LyricsSource.valueOf(source) }.getOrDefault(LyricsSource.LRCLIB)
        syncedLyrics?.let { raw ->
            val parsed = LrcParser.parse(raw)
            if (parsed.synced) {
                return LyricsResult.Found(
                    Lyrics(plain = plainLyrics ?: parsed.plain, lines = parsed.lines),
                    source
                )
            }
        }
        plainLyrics?.let { return LyricsResult.Found(Lyrics(plain = it), source) }
        return null // corrupt row — fall through to a fresh lookup
    }

    /** Lossless line-level LRC serialisation for persisting parsed synced lyrics. */
    private fun List<LyricsLine>.toLrcText(): String = joinToString("\n") { line ->
        val ts = line.timestampMs ?: 0L
        val minutes = ts / 60_000
        val seconds = (ts % 60_000) / 1_000
        val millis = ts % 1_000
        String.format(Locale.US, "[%02d:%02d.%03d]%s", minutes, seconds, millis, line.text)
    }

    private companion object {
        const val LRU_SIZE = 100
        val PARENTHETICAL = Regex("""[(\[][^)\]]*[)\]]""")
    }
}
