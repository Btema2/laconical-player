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
import com.laconical.player.ui.components.staggeredEntrance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import com.laconical.player.ui.components.PlaylistBottomSheet
import com.laconical.player.ui.components.PlaylistCoverMosaic
import com.laconical.player.ui.viewmodels.PlaylistsViewModel

@Composable
fun PlaylistsScreen(
    onFavoritesClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onCreatePlaylist: () -> Unit,
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

    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget by remember { mutableStateOf<Playlist?>(null) }

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
                            onRename = { renameTarget = playlist },
                            onDelete = { deleteTarget = playlist }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        PlaylistBottomSheet(
            title = "Rename Playlist",
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.renamePlaylist(target.id, name)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = { Text("This will permanently delete the playlist and remove all its tracks. Your music files are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlaylist(target.id)
                    deleteTarget = null
                }) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
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
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = accentColor.copy(alpha = 0.5f)
            )
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
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        PlaylistCoverMosaic(
            tracks = artTracks,
            size = 52.dp
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
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Playlist options",
                    tint = Color(0xFF888888)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = { menuExpanded = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFEF4444)) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                    },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}
