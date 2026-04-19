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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onTrackClick: (Track) -> Unit,
    onBack: () -> Unit,
    bottomPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val favoriteTracks = allTracks.filter { favoriteIds.contains(it.id) }

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
                    "No favorites yet. Tap ♡ on any track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF888888)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                items(favoriteTracks, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        isActiveTrack = currentTrack?.id == track.id,
                        isPlaybackActive = isPlaying,
                        isFavorite = true,
                        onFavoriteToggle = { onFavoriteToggle(track.id) },
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}
