package com.laconical.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.laconical.player.core.model.Track
import com.laconical.player.ui.AudioArtData

@Composable
fun PlaylistCoverMosaic(
    tracks: List<Track>,
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        when {
            tracks.isEmpty() -> {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF555555),
                    modifier = Modifier.size(size * 0.5f)
                )
            }
            tracks.size < 4 -> {
                SubcomposeAsyncImage(
                    model = remember(tracks.first().mediaUri) { AudioArtData(tracks.first().mediaUri) },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(size * 0.5f)
                        )
                    }
                )
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        MosaicCell(track = tracks[0], modifier = Modifier.weight(1f))
                        MosaicCell(track = tracks[1], modifier = Modifier.weight(1f))
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        MosaicCell(track = tracks[2], modifier = Modifier.weight(1f))
                        MosaicCell(track = tracks[3], modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MosaicCell(track: Track, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = remember(track.mediaUri) { AudioArtData(track.mediaUri) },
        contentDescription = null,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFF444444)
                )
            }
        }
    )
}
