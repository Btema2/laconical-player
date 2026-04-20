package com.laconical.player.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.data.UserDataRepository
import com.laconical.player.core.data.db.entity.Playlist
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

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = userDataRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    val playlistArtTracks: StateFlow<Map<Long, List<Track>>> = combine(
        userDataRepository.getAllPlaylistTracks(),
        _allTracks
    ) { playlistTracks, allTracks ->
        val trackMap = allTracks.associateBy { it.id }
        playlistTracks
            .groupBy { it.playlistId }
            .mapValues { (_, pts) -> pts.take(4).mapNotNull { trackMap[it.trackId] } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { _allTracks.value = mediaRepository.getTracks() }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { userDataRepository.createPlaylist(name.trim()) }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch { userDataRepository.renamePlaylist(playlistId, name.trim()) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { userDataRepository.deletePlaylist(playlistId) }
    }
}
