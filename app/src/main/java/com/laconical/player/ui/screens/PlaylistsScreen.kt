package com.laconical.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.components.staggeredEntrance
import com.laconical.player.ui.viewmodels.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
    onMenuOpen: (playlist: Playlist, artOffsetPx: Offset, artSizePx: Float) -> Unit,
    bottomPadding: Dp = 0.dp,
    dominantColor: Color? = null,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val accentColor = if (dominantColor != null) {
        Color(
            red   = (dominantColor.red   * 0.3f + 0.7f).coerceIn(0f, 1f),
            green = (dominantColor.green * 0.3f + 0.7f).coerceIn(0f, 1f),
            blue  = (dominantColor.blue  * 0.3f + 0.7f).coerceIn(0f, 1f),
            alpha = 1f
        )
    } else Color.White
    val playlists by viewModel.playlists.collectAsState()
    val artMap by viewModel.playlistArtTracks.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + bottomPadding + 80.dp
            )
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFavoritesClick() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFE84B7A),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Favorites",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF666666)
                    )
                }
            }

            item {
                NewPlaylistRow(
                    accentColor = accentColor,
                    onClick = onCreatePlaylist
                )
            }

            if (playlists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No playlists yet. Tap 'Create playlist' to add one.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF555555)
                        )
                    }
                }
            } else {
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    Box(modifier = Modifier.staggeredEntrance(index)) {
                        PlaylistRow(
                            playlist = playlist,
                            artTracks = artMap[playlist.id] ?: emptyList(),
                            onClick = { onPlaylistClick(playlist.id) },
                            onMenuOpen = { offset, size -> onMenuOpen(playlist, offset, size) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPlaylistRow(
    accentColor: Color,
    onClick: () -> Unit
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color(0xFF2A2A2A))
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Light,
                        color = accentColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "New Playlist",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = accentColor
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color(0xFF2A2A2A))
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    artTracks: List<Track>,
    onClick: () -> Unit,
    onMenuOpen: (artOffsetPx: Offset, artSizePx: Float) -> Unit,
) {
    val density = LocalDensity.current
    var mosaicOffsetPx by remember { mutableStateOf(Offset.Zero) }
    val mosaicSizePx = with(density) { 52.dp.toPx() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistCoverMosaic(
            tracks = artTracks,
            size = 52.dp,
            modifier = Modifier.onGloballyPositioned { coords ->
                mosaicOffsetPx = coords.positionInRoot()
            }
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1
            )
        }
        IconButton(onClick = { onMenuOpen(mosaicOffsetPx, mosaicSizePx) }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Playlist options",
                tint = Color(0xFF888888)
            )
        }
    }
}
