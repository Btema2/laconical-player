package com.laconical.player.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Artist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val representativeTrackUri: String
)

@HiltViewModel
class ArtistsViewModel @Inject constructor(
    private val repository: MediaRepository
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    val artists: StateFlow<List<Artist>> = _artists.asStateFlow()

    init { loadArtists() }

    private fun loadArtists() {
        viewModelScope.launch {
            val tracks = repository.getTracks()
            _allTracks.value = tracks
            _artists.value = tracks
                .groupBy { it.artist }
                .map { (name, artistTracks) ->
                    Artist(
                        name = name,
                        trackCount = artistTracks.size,
                        albumCount = artistTracks.distinctBy { it.album }.size,
                        representativeTrackUri = artistTracks.first().mediaUri
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    fun getTracksForArtist(artistName: String): List<Track> =
        _allTracks.value.filter { it.artist == artistName }
}
