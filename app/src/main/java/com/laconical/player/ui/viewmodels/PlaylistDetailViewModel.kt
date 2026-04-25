package com.laconical.player.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.data.UserDataRepository
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.data.db.entity.PlaylistTrack
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    val playlist: StateFlow<Playlist?> = userDataRepository.getAllPlaylists()
        .map { list -> list.find { it.id == playlistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    val tracks: StateFlow<List<Track>> = combine(
        userDataRepository.getTrackIdsForPlaylist(playlistId),
        _allTracks
    ) { ids, all ->
        val map = all.associateBy { it.id }
        ids.mapNotNull { map[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _allTracks.value = mediaRepository.getTracks() }
    }

    fun moveTrack(from: Int, to: Int) {
        viewModelScope.launch {
            val current = tracks.value.toMutableList()
            if (from < 0 || to < 0 || from >= current.size || to >= current.size) return@launch
            val moved = current.removeAt(from)
            current.add(to, moved)
            val updated = current.mapIndexed { idx, track ->
                PlaylistTrack(playlistId, track.id, idx)
            }
            userDataRepository.reorderPlaylistTracks(playlistId, updated)
        }
    }

    fun removeTrack(trackId: Long) {
        viewModelScope.launch { userDataRepository.removeTrackFromPlaylist(playlistId, trackId) }
    }

}
