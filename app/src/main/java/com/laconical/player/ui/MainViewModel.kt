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
import androidx.media3.common.MediaItem

@HiltViewModel
class MainViewModel @Inject constructor(
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

    fun loadTracks() {
        viewModelScope.launch {
            _allTracks.value = repository.getTracks()
        }
    }

    fun playTrack(track: Track) {
        try {
            _currentTrack.value = track
            val mediaItem = androidx.media3.common.MediaItem.fromUri(track.mediaUri)
            musicPlayer.playMediaItem(mediaItem)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
