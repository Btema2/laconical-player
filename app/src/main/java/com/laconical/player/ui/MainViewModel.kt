package com.laconical.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.media.MusicPlayer
import com.laconical.player.core.media.AudioVisualizerManager
import com.laconical.player.core.model.Track
import com.laconical.player.core.media.WaveformExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.media3.common.MediaItem
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.asImage
import coil3.request.SuccessResult
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wrapper type so Coil dispatches to OUR fetcher, not its built-in ContentUriFetcher. */
data class AudioArtData(val uri: String)

class AudioAlbumArtFetcher(
    private val artData: AudioArtData,
        private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        try {
            if (artData.uri.startsWith("/")) {
                retriever.setDataSource(artData.uri)
            } else {
                retriever.setDataSource(options.context, Uri.parse(artData.uri))
            }
            val picture = retriever.embeddedPicture
            if (picture != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
                if (bitmap != null) {
                    return ImageFetchResult(
                        image = bitmap.asImage(),
                                            isSampled = false,
                                            dataSource = DataSource.DISK
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    retriever.close()
                } else {
                    retriever.release()
                }
            } catch (_: Exception) {}
        }
        return null
    }

    class Factory : Fetcher.Factory<AudioArtData> {
        override fun create(data: AudioArtData, options: Options, imageLoader: coil3.ImageLoader): Fetcher {
            return AudioAlbumArtFetcher(data, options)
        }
    }
}

class AudioAlbumArtKeyer : Keyer<AudioArtData> {
    override fun key(data: AudioArtData, options: Options): String {
        return "audio_art_${data.uri}"
    }
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MediaRepository,
        private val musicPlayer: MusicPlayer,
            private val visualizerManager: AudioVisualizerManager,
                private val waveformExtractor: WaveformExtractor
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val tracks: StateFlow<List<Track>> = combine(_allTracks, _searchQuery) { tracks, query ->
        if (query.isBlank()) tracks
            else tracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _playingTrackDominantColor = MutableStateFlow<Color?>(null)
    val playingTrackDominantColor: StateFlow<Color?> = _playingTrackDominantColor.asStateFlow()

    val isPlaying: StateFlow<Boolean> = musicPlayer.isPlaying
    val currentPosition: StateFlow<Long> = musicPlayer.currentPosition
    val duration: StateFlow<Long> = musicPlayer.duration

    val waveform: StateFlow<FloatArray> = visualizerManager.waveform
    val beatPulse: StateFlow<Float> = visualizerManager.beatPulse

    private val _waveformData = MutableStateFlow<List<Int>>(emptyList())
    val waveformData: StateFlow<List<Int>> = _waveformData.asStateFlow()

    private val _currentNormalizedAmplitude = MutableStateFlow(0f)
    val currentNormalizedAmplitude: StateFlow<Float> = _currentNormalizedAmplitude.asStateFlow()

    /** Pre-computed peak so we don't scan the list every 16 ms. */
    private var cachedMaxAmplitude = 1

    val progress: StateFlow<Float> = combine(currentPosition, duration) { pos, dur ->
        if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0f)

    fun loadTracks() {
        viewModelScope.launch {
            _allTracks.value = repository.getTracks()
        }
        startAmplitudeTicker()
    }

    private var amplitudeTickerJob: kotlinx.coroutines.Job? = null

        private fun startAmplitudeTicker() {
            amplitudeTickerJob?.cancel()
            amplitudeTickerJob = viewModelScope.launch {
                while (true) {
                    if (musicPlayer.isPlaying.value && _waveformData.value.isNotEmpty()) {
                        val pos = musicPlayer.currentPosition.value
                        val dur = musicPlayer.duration.value
                        val data = _waveformData.value
                        if (dur > 0 && data.isNotEmpty()) {
                            val index = ((pos.toFloat() / dur.toFloat()) * (data.size - 1))
                            .toInt().coerceIn(0, data.size - 1)
                            val targetAmp = data[index].toFloat() / cachedMaxAmplitude.toFloat()

                            // 75% previous + 25% new — heavier smoothing absorbs small jitter,
                            // only sustained loud sections drive noticeable movement
                            _currentNormalizedAmplitude.value =
                            (_currentNormalizedAmplitude.value * 0.75f) + (targetAmp * 0.25f)
                        }
                    } else {
                        // Gentle exponential decay toward 0 when paused / no data
                        _currentNormalizedAmplitude.value =
                        (_currentNormalizedAmplitude.value * 0.92f).coerceAtLeast(0f)
                    }
                    delay(16) // ~60 fps
                }
            }
        }

        fun playTrack(track: Track) {
            try {
                _currentTrack.value = track

                // Reset amplitude state so stale data from the previous track
                // never drives the pulse on the new one.
                _waveformData.value = emptyList()
                cachedMaxAmplitude = 1
                _currentNormalizedAmplitude.value = 0f

                val mediaItem = MediaItem.fromUri(track.mediaUri)
                musicPlayer.playMediaItem(mediaItem)

                // Extract waveform — prefer file path (Amplituda handles it best)
                val waveformSource = track.dataPath ?: track.mediaUri
                viewModelScope.launch {
                    try {
                        val wf = waveformExtractor.extractWaveform(waveformSource)
                        _waveformData.value = wf
                        cachedMaxAmplitude = wf.maxOrNull()?.coerceAtLeast(1) ?: 1
                    } catch (e: Exception) {
                        Log.e("MainViewModel", "Failed to extract waveform", e)
                        _waveformData.value = emptyList()
                        cachedMaxAmplitude = 1
                    }
                }

                // Extract dominant colour for theming
                val loadTarget = if (!track.dataPath.isNullOrEmpty()) track.dataPath else track.mediaUri
                if (!loadTarget.isNullOrEmpty()) {
                    viewModelScope.launch {
                        withContext(Dispatchers.Default) {
                            try {
                                val imageLoader = ImageLoader.Builder(context)
                                .components {
                                    add(AudioAlbumArtFetcher.Factory())
                                    add(AudioAlbumArtKeyer())
                                }
                                .build()

                                val request = ImageRequest.Builder(context)
                                .data(AudioArtData(loadTarget))
                                .size(100)
                                .build()

                                val result = imageLoader.execute(request)
                                if (result is SuccessResult) {
                                    val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                                    bitmap?.let { bmp ->
                                        Palette.from(bmp).generate().dominantSwatch?.let { swatch ->
                                            _playingTrackDominantColor.value = Color(swatch.rgb)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                } else {
                    _playingTrackDominantColor.value = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateSearchQuery(query: String) { _searchQuery.value = query }

        fun togglePlayPause() {
            if (isPlaying.value) musicPlayer.pause() else musicPlayer.play()
        }

        fun skipToPrevious() { musicPlayer.skipToPrevious() }
        fun skipToNext() { musicPlayer.skipToNext() }

        fun seekTo(progress: Float) {
            val dur = musicPlayer.duration.value
            if (dur > 0) musicPlayer.seekTo((progress * dur).toLong())
        }

        override fun onCleared() {
            super.onCleared()
            visualizerManager.destroy()
        }
}
