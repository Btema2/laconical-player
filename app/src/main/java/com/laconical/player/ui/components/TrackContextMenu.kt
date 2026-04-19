package com.laconical.player.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun TrackContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onViewAlbum: (() -> Unit)?,
    onViewArtist: (() -> Unit)?,
    onAddToPlaylist: (() -> Unit)?
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
            leadingIcon = {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFavorite) Color(0xFFE84B7A) else Color.Unspecified
                )
            },
            onClick = {
                onFavoriteToggle()
                onDismiss()
            }
        )
        if (onViewAlbum != null) {
            DropdownMenuItem(
                text = { Text("View album") },
                leadingIcon = { Icon(Icons.Default.Album, contentDescription = null) },
                onClick = {
                    onViewAlbum()
                    onDismiss()
                }
            )
        }
        if (onViewArtist != null) {
            DropdownMenuItem(
                text = { Text("View artist") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                onClick = {
                    onViewArtist()
                    onDismiss()
                }
            )
        }
        if (onAddToPlaylist != null) {
            DropdownMenuItem(
                text = { Text("Add to playlist") },
                leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                onClick = {
                    onAddToPlaylist()
                    onDismiss()
                }
            )
        }
    }
}
