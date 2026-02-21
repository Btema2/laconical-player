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
import coil3.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            if (track.albumArtUri != null) {
                viewModelScope.launch {
                    withContext(Dispatchers.Default) {
                        try {
                            val imageLoader = ImageLoader(context)
                            val request = ImageRequest.Builder(context)
                                .data(track.albumArtUri)
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
