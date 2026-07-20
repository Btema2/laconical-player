package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.core.model.Track
import com.laconical.player.ui.SortOrder
import com.laconical.player.ui.applySort
import com.laconical.player.ui.components.ShuffleFab
import com.laconical.player.ui.components.SortChipRow
import com.laconical.player.ui.components.TrackListItem
import com.laconical.player.ui.viewmodels.AlbumsViewModel

@Composable
fun AlbumDetailScreen(
    albumName: String,
    onBack: () -> Unit,
    currentTrack: Track?,
    isPlaying: Boolean,
    favoriteIds: Set<Long>,
    onFavoriteToggle: (Long) -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    /** Opens the shared TrackMenuOverlay (kebab menu) hosted at LibraryScreen, mirroring the Tracks screen. */
    onTrackMenuOpen: (Track, Offset, Float) -> Unit = { _, _, _ -> },
    dominantColor: Color? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val albumsState by viewModel.albums.collectAsState()
    val albumTracks = remember(albumName, albumsState) {
        viewModel.getTracksForAlbum(albumName)
    }
    var sortOrder by remember { mutableStateOf(SortOrder.DEFAULT) }
    val tracks = remember(albumTracks, sortOrder) { albumTracks.applySort(sortOrder) }
    val listState = rememberLazyListState()
    LaunchedEffect(sortOrder) { listState.scrollToItem(0) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 4.dp, end = 16.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = albumName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        SortChipRow(
            options = SortOrder.entries.toList(),
            selected = sortOrder,
            onSelect = { sortOrder = it },
            dominantColor = dominantColor,
            trailing = {
                ShuffleFab(
                    dominantColor = dominantColor,
                    onClick = {
                        if (tracks.isNotEmpty()) onTrackClick(tracks.shuffled(), 0)
                    }
                )
            }
        )
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                TrackListItem(
                    track = track,
                    isActiveTrack = currentTrack?.id == track.id,
                    isPlaybackActive = isPlaying,
                    isFavorite = favoriteIds.contains(track.id),
                    onFavoriteToggle = { onFavoriteToggle(track.id) },
                    onClick = { onTrackClick(tracks, index) },
                    onMenuOpen = { offset, size -> onTrackMenuOpen(track, offset, size) }
                )
            }
        }
    }
}
