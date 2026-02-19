package com.laconical.player.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.media.MusicPlayer
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.media3.common.MediaItem

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val musicPlayer: MusicPlayer
) : ViewModel() {

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    fun loadTracks() {
        viewModelScope.launch {
            _tracks.value = repository.getTracks()
        }
    }

    fun playTrack(track: Track) {
        val mediaItem = androidx.media3.common.MediaItem.fromUri(track.mediaUri)
        musicPlayer.playMediaItem(mediaItem)
    }
}
