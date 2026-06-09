package com.laconical.player.core.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.laconical.player.core.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMediaRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaRepository {

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.ALBUM_ID
    )

    private val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    private val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

    override suspend fun getTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                tracks.add(rowToTrack(cursor, idCol, titleCol, artistCol, albumCol, durationCol, albumIdCol))
            }
        }
        tracks
    }

    override fun getTracksFlow(batchSize: Int): Flow<List<Track>> = flow {
        require(batchSize > 0) { "batchSize must be > 0, was $batchSize" }
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )?.use { cursor ->
            val idCol       = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol   = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol    = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol  = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (cursor.moveToNext()) {
                tracks.add(rowToTrack(cursor, idCol, titleCol, artistCol, albumCol, durationCol, albumIdCol))
                if (tracks.size % batchSize == 0) emit(tracks.toList())
            }
        }
        if (tracks.isNotEmpty() && tracks.size % batchSize != 0) emit(tracks.toList())
    }.flowOn(Dispatchers.IO)

    private fun rowToTrack(
        cursor: android.database.Cursor,
        idCol: Int, titleCol: Int, artistCol: Int,
        albumCol: Int, durationCol: Int, albumIdCol: Int
    ): Track {
        val id      = cursor.getLong(idCol)
        val albumId = cursor.getLong(albumIdCol)
        return Track(
            id        = id,
            title     = cursor.getString(titleCol)    ?: "Unknown Title",
            artist    = cursor.getString(artistCol)   ?: "Unknown Artist",
            album     = cursor.getString(albumCol)    ?: "Unknown Album",
            durationMs = cursor.getLong(durationCol),
            mediaUri  = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
            ).toString(),
            albumArtUri = ContentUris.withAppendedId(
                android.net.Uri.parse("content://media/external/audio/albumart"), albumId
            ).toString(),
            dataPath  = null  // DATA column deprecated since API 29; content URI is used for all access
        )
    }
}
