package com.laconical.player.core.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.laconical.player.core.model.Track
import com.laconical.player.core.model.TrackAudioDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioMetadataExtractor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun extractDetails(track: Track): TrackAudioDetails = withContext(Dispatchers.IO) {
        val uri = Uri.parse(track.mediaUri)
        val retriever = MediaMetadataRetriever()
        var bitrate: Int? = null
        var sampleRate: Int? = null
        var channelsStr: String? = null
        var mimeType: String? = null
        var albumArtist: String? = null
        var composer: String? = null
        var year: String? = null
        var genre: String? = null
        var discNumber: String? = null
        var codec: String? = null
        var bitDepth: Int? = null

        try {
            retriever.setDataSource(context, uri)
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 }
            albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            composer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } catch (e: Exception) {
            Log.w("AudioMetadataExtractor", "Failed to extract metadata using MediaMetadataRetriever for URI: $uri", e)
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                Log.w("AudioMetadataExtractor", "Failed to release MediaMetadataRetriever", e)
            }
        }

        var filePath: String? = track.dataPath
        var fileSizeFormatted: String? = null
        var dateAddedFormatted: String? = null

        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                filePath = file.absolutePath
                fileSizeFormatted = formatFileSize(file.length())
                dateAddedFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
            }
        } else if (uri.scheme == "content") {
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { pfd ->
                    fileSizeFormatted = formatFileSize(pfd.length)
                }
            } catch (e: Exception) {
                Log.w("AudioMetadataExtractor", "Failed to open asset file descriptor for URI: $uri", e)
            }

            try {
                val proj = arrayOf(
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATE_MODIFIED,
                )
                context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        val relIdx = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                        val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                        val dateIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

                        val dataPath = if (dataIdx != -1) cursor.getString(dataIdx) else null
                        val relativePath = if (relIdx != -1) cursor.getString(relIdx) else null
                        val displayName = if (nameIdx != -1) cursor.getString(nameIdx) else null

                        if (!dataPath.isNull_or_blank()) {
                            filePath = dataPath
                        } else if (!relativePath.isNull_or_blank() && !displayName.isNull_or_blank()) {
                            filePath = relativePath + displayName
                        } else if (!displayName.isNull_or_blank()) {
                            filePath = displayName
                        }

                        if (dateIdx != -1) {
                            val dateSec = cursor.getLong(dateIdx)
                            if (dateSec > 0) {
                                dateAddedFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(dateSec * 1000L))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("AudioMetadataExtractor", "Failed to query MediaStore details for URI: $uri", e)
            }
        }

        // MediaExtractor for precise channel count, sample rate, bit depth, codec
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)
            if (extractor.trackCount > 0) {
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        if (mimeType == null) mimeType = mime
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            val chCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            channelsStr = when (chCount) {
                                1 -> "Mono (1.0)"
                                2 -> "Stereo (2.0)"
                                6 -> "5.1 Surround"
                                else -> "$chCount Channels"
                            }
                        }
                        if (format.containsKey("pcm-encoding")) {
                            val encoding = format.getInteger("pcm-encoding")
                            bitDepth = when (encoding) {
                                2 -> 16
                                3 -> 8
                                4 -> 32
                                else -> 24
                            }
                        }
                        codec = mime.removePrefix("audio/").uppercase(Locale.getDefault())
                        break
                    }
                }
            }
            extractor.release()
        } catch (e: Exception) {
            Log.w("AudioMetadataExtractor", "Failed to extract media details using MediaExtractor for URI: $uri", e)
        }

        TrackAudioDetails(
            track = track,
            filePath = filePath ?: track.mediaUri,
            fileSizeFormatted = fileSizeFormatted ?: "Unknown size",
            mimeType = mimeType ?: "audio/*",
            dateAddedFormatted = dateAddedFormatted ?: "Unknown",
            bitrateKbps = bitrate,
            sampleRateHz = sampleRate,
            bitDepthBits = bitDepth ?: 16,
            channels = channelsStr ?: "Stereo",
            codec = codec ?: "AUDIO",
            albumArtist = albumArtist,
            composer = composer,
            year = year,
            genre = genre,
            discNumber = discNumber,
        )
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
}
