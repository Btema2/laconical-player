package com.laconical.player.core.data.lyrics

import android.content.Context
import androidx.core.net.toUri
import com.laconical.player.core.model.Track
import com.laconical.player.core.model.lyrics.Id3UsltParser
import com.laconical.player.core.model.lyrics.LrcParser
import com.laconical.player.core.model.lyrics.Lyrics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Embedded-tag lyrics: MP3 ID3v2 USLT/SYLT first (pure-JVM parser over a ContentResolver
 * stream — reads only the tag at the file head), then container-level metadata via the
 * Media3-backed [EmbeddedFormatLyricsExtractor] (FLAC/OGG VorbisComment).
 *
 * Tracks with no embedded lyrics are memoised per-process so repeat track-changes cost nothing.
 */
@Singleton
class EmbeddedLyricsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatExtractor: EmbeddedFormatLyricsExtractor
) {

    private val negativeCache = ConcurrentHashMap.newKeySet<Long>()

    suspend fun fetch(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        if (track.id in negativeCache) return@withContext null

        parseId3(track)?.let { return@withContext it }

        formatExtractor.extract(track.mediaUri)?.let { raw ->
            return@withContext raw.toLyrics()
        }

        negativeCache += track.id
        null
    }

    private fun parseId3(track: Track): Lyrics? = try {
        context.contentResolver.openInputStream(track.mediaUri.toUri())?.use { stream ->
            when (val result = Id3UsltParser.parse(BufferedInputStream(stream))) {
                is Id3UsltParser.Id3LyricsResult.Synced -> Lyrics(
                    plain = result.lines.joinToString("\n") { it.text }.ifBlank { null },
                    lines = result.lines
                )
                // USLT text frequently carries LRC markup — try to parse it as synced.
                is Id3UsltParser.Id3LyricsResult.Unsynced -> result.text.toLyrics()
                null -> null
            }
        }
    } catch (_: Exception) {
        null
    }

    /** Parse as LRC when timestamps are present; otherwise keep the raw text verbatim. */
    private fun String.toLyrics(): Lyrics =
        LrcParser.parse(this).takeIf { it.synced } ?: Lyrics(plain = trim().ifBlank { null })
}
