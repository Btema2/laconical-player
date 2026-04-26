package com.laconical.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistOverlay(
    track: Track,
    playlists: List<Playlist>,
    artTracks: Map<Long, List<Track>>,
    dominantColor: Color?,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    fun dismiss() {
        scope.launch {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }

    BackHandler { dismiss() }

    val prog = progress.value

    val headerBg = if (dominantColor != null) {
        Color(
            red = (dominantColor.red * 0.3f + 0.05f).coerceIn(0f, 1f),
            green = (dominantColor.green * 0.3f + 0.05f).coerceIn(0f, 1f),
            blue = (dominantColor.blue * 0.3f + 0.07f).coerceIn(0f, 1f),
            alpha = 1f,
        )
    } else Color(0xFF1A1A24)

    val menuBg = Color(0xFF12121A)

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (prog * 1.6f).coerceIn(0f, 1f) }
                .background(Color(0xCC000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismiss() },
                ),
        )

        // Card
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        alpha = prog
                        scaleX = lerp(0.92f, 1f, prog)
                        scaleY = lerp(0.92f, 1f, prog)
                        translationY = lerp(48f, 0f, prog)
                    }
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center,
                    ) {
                        SubcomposeAsyncImage(
                            model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFF555555),
                                )
                            },
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add to Playlist",
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF2A2A35))

                // Playlist list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(menuBg)
                        .heightIn(max = 280.dp),
                ) {
                    if (playlists.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No playlists yet",
                                    color = Color(0xFF555555),
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    } else {
                        itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                            if (index > 0) {
                                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))
                            }
                            PlaylistPickerRow(
                                playlist = playlist,
                                artTracks = artTracks[playlist.id] ?: emptyList(),
                                background = menuBg,
                                onClick = { onSelectPlaylist(playlist) },
                            )
                        }
                    }
                }

                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFF1E1E28))

                // New Playlist footer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(menuBg)
                        .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White),
                            onClick = onCreateNew,
                        )
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF7C6FE0),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "New Playlist",
                        color = Color(0xFF7C6FE0),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickerRow(
    playlist: Playlist,
    artTracks: List<Track>,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = Color.White),
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        PlaylistCoverMosaic(
            tracks = artTracks,
            size = 48.dp,
            cornerRadius = 10.dp,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = playlist.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
