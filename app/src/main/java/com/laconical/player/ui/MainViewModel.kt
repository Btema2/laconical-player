package com.laconical.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.media.MusicPlayer
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        Log.d("LaconicalDiag", "Fetcher starting for: ${artData.uri}")
        val retriever = MediaMetadataRetriever()
        try {
            if (artData.uri.startsWith("/")) {
                Log.d("LaconicalDiag", "Setting data source via absolute path")
                retriever.setDataSource(artData.uri)
            } else {
                Log.d("LaconicalDiag", "Setting data source via context/uri")
                retriever.setDataSource(options.context, Uri.parse(artData.uri))
            }
            val picture = retriever.embeddedPicture
            if (picture != null) {
                Log.d("LaconicalDiag", "Embedded picture found! Size: ${picture.size}")
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(picture, 0, picture.size)
                if (bitmap != null) {
                    Log.d("LaconicalDiag", "Bitmap decoded successfully")
                    return ImageFetchResult(
                        image = bitmap.asImage(),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                } else {
                    Log.e("LaconicalDiag", "Bitmap decoding FAILED")
                }
            } else {
                Log.w("LaconicalDiag", "No embedded picture found in file")
            }
        } catch (e: Exception) {
            Log.e("LaconicalDiag", "Fetcher ERROR: ${e.message}", e)
        } finally {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    retriever.close()
                } else {
                    retriever.release()
                }
            } catch (e: Exception) {}
        }
        return null
    }

    class Factory : Fetcher.Factory<AudioArtData> {
        override fun create(data: AudioArtData, options: Options, imageLoader: coil3.ImageLoader): Fetcher {
            Log.d("LaconicalDiag", "Factory create called for: ${data.uri}")
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
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val tracks: StateFlow<List<Track>> = combine(_allTracks, _searchQuery) { tracks, query ->
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.artist.contains(query, ignoreCase = true) 
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _playingTrackDominantColor = MutableStateFlow<Color?>(null)
    val playingTrackDominantColor: StateFlow<Color?> = _playingTrackDominantColor.asStateFlow()

    fun loadTracks() {
        viewModelScope.launch {
            _allTracks.value = repository.getTracks()
        }
    }

    fun playTrack(track: Track) {
        try {
            _currentTrack.value = track
            val mediaItem = MediaItem.fromUri(track.mediaUri)
            musicPlayer.playMediaItem(mediaItem)
            
            // Extract the Palette in a background coroutine so LibraryScreen can transition its UI
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

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
