package com.laconical.player.core.data.lyrics

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.laconical.player.core.model.Track
import com.laconical.player.core.model.lyrics.LrcParser
import com.laconical.player.core.model.lyrics.Lyrics
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks for a `.lrc` file with the same basename next to the audio file
 * (`Music/song.mp3` → `Music/song.lrc`).
 *
 * Best-effort by design: on API ≤ 28 the sibling is read directly from disk; on API 29+
 * scoped storage usually blocks direct reads of non-media files, so a MediaStore Files
 * lookup is attempted as fallback (works when the .lrc is indexed and accessible, e.g.
 * app-created or on builds/configs that expose it). Embedded tags + LRCLIB carry the
 * weight where this layer can't reach.
 */
@Singleton
class SiblingLrcSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun fetch(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        try {
            val audioPath = queryAudioDataPath(track.id) ?: return@withContext null
            val lrcPath = audioPath.substringBeforeLast('.') + ".lrc"

            readDirect(lrcPath)?.let { return@withContext it.toLyrics() }
            readViaMediaStoreFiles(lrcPath)?.let { return@withContext it.toLyrics() }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** The audio row's DATA column — deprecated but still populated for MediaStore.Audio. */
    @Suppress("DEPRECATION")
    private fun queryAudioDataPath(trackId: Long): String? =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media._ID} = ?",
            arrayOf(trackId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).takeIf { !it.isNullOrBlank() } else null
        }

    private fun readDirect(path: String): String? {
        val file = File(path)
        return if (file.canRead()) {
            file.readText().ifBlank { null }
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun readViaMediaStoreFiles(path: String): String? {
        val filesUri = MediaStore.Files.getContentUri("external")
        val id = context.contentResolver.query(
            filesUri,
            arrayOf(MediaStore.Files.FileColumns._ID),
            "${MediaStore.Files.FileColumns.DATA} = ?",
            arrayOf(path),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        } ?: return null

        return context.contentResolver
            .openInputStream(ContentUris.withAppendedId(filesUri, id))
            ?.use { it.readBytes().decodeToString() }
            ?.ifBlank { null }
    }

    private fun String.toLyrics(): Lyrics =
        LrcParser.parse(this).takeIf { it.hasContent } ?: Lyrics(plain = null)
}
