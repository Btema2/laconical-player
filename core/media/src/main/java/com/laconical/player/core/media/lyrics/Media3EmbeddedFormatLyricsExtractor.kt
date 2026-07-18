package com.laconical.player.core.media.lyrics

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.laconical.player.core.data.lyrics.EmbeddedFormatLyricsExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/**
 * Extracts embedded lyrics from container metadata via Media3's [MetadataRetriever]
 * (FLAC/OGG VorbisComment `LYRICS`/`UNSYNCEDLYRICS`, matched case-insensitively —
 * Media3 historically had case-sensitivity quirks on Vorbis keys).
 */
@Singleton
@androidx.annotation.OptIn(UnstableApi::class)
class Media3EmbeddedFormatLyricsExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) : EmbeddedFormatLyricsExtractor {

    override suspend fun extract(mediaUri: String): String? = withContext(Dispatchers.IO) {
        try {
            MetadataRetriever.Builder(context, MediaItem.fromUri(mediaUri)).build().use { retriever ->
                val trackGroups = retriever.retrieveTrackGroups().await()
                for (groupIndex in 0 until trackGroups.length) {
                    val group = trackGroups[groupIndex]
                    for (trackIndex in 0 until group.length) {
                        val metadata = group.getFormat(trackIndex).metadata ?: continue
                        for (entryIndex in 0 until metadata.length()) {
                            val entry = metadata.get(entryIndex)
                            if (entry is VorbisComment && entry.key.isLyricsKey()) {
                                entry.value.takeIf { it.isNotBlank() }?.let { return@withContext it }
                            }
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun String.isLyricsKey(): Boolean =
        equals("LYRICS", ignoreCase = true) || equals("UNSYNCEDLYRICS", ignoreCase = true)
}
