package com.laconical.player.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laconical.player.core.data.AppSettingsStore
import com.laconical.player.core.data.MediaRepository
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AlbumSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class Album(
    val name: String,
    val artistName: String,
    val trackCount: Int,
    val representativeTrackUri: String
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    // appSettingsStore is a Singleton, so this value survives navigating away from and back
    // to the Albums screen (each detail-entry gets its own AlbumsViewModel instance, but they
    // all read/write the same underlying DataStore) and across app restarts.
    val albumSortOrder: StateFlow<AlbumSortOrder> = appSettingsStore.albumSortOrder
        .map { raw -> raw?.let { runCatching { AlbumSortOrder.valueOf(it) }.getOrNull() } ?: AlbumSortOrder.NAME }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AlbumSortOrder.NAME)

    fun setAlbumSortOrder(order: AlbumSortOrder) {
        viewModelScope.launch { appSettingsStore.setAlbumSortOrder(order.name) }
    }

    init { loadAlbums() }

    private fun loadAlbums() {
        viewModelScope.launch {
            val tracks = repository.getTracks()
            _allTracks.value = tracks
            _albums.value = tracks
                .groupBy { it.album }
                .map { (name, albumTracks) ->
                    Album(
                        name = name,
                        artistName = albumTracks.first().artist,
                        trackCount = albumTracks.size,
                        representativeTrackUri = albumTracks.first().mediaUri
                    )
                }
                .sortedBy { it.name.lowercase() }
        }
    }

    fun getTracksForAlbum(albumName: String): List<Track> =
        _allTracks.value.filter { it.album == albumName }
}
