package com.laconical.player.ui.screens

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.core.model.Track
import com.laconical.player.ui.components.TrackListItem

@Composable
fun FavoritesScreen(
    allTracks: List<Track>,
    favoriteIds: Set<Long>,
    currentTrack: Track?,
    isPlaying: Boolean,
    onFavoriteToggle: (Long) -> Unit,
    onTrackClick: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    /** Opens the shared TrackMenuOverlay (kebab menu) hosted at LibraryScreen, mirroring the Tracks screen. */
    onTrackMenuOpen: (Track, Offset, Float) -> Unit = { _, _, _ -> },
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val favoriteTracks = remember(allTracks, favoriteIds) { allTracks.filter { favoriteIds.contains(it.id) } }

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
                text = "Favorites",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(Modifier.height(4.dp))
        if (favoriteTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No favorites yet. Tap the heart icon on any track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                itemsIndexed(favoriteTracks, key = { _, track -> track.id }) { index, track ->
                    TrackListItem(
                        track = track,
                        isActiveTrack = currentTrack?.id == track.id,
                        isPlaybackActive = isPlaying,
                        isFavorite = true,
                        onFavoriteToggle = { onFavoriteToggle(track.id) },
                        onClick = { onTrackClick(favoriteTracks, index) },
                        onMenuOpen = { offset, size -> onTrackMenuOpen(track, offset, size) }
                    )
                }
            }
        }
    }
}
