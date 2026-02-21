package com.laconical.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.laconical.player.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrackListItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dominantColor by remember { mutableStateOf(Color.Transparent) }
    val context = LocalContext.current

    LaunchedEffect(track.albumArtUri, isPlaying) {
        if (isPlaying && track.albumArtUri != null) {
            withContext(Dispatchers.Default) {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(track.albumArtUri)
                    .size(100)
                    .build()

                when (val result = imageLoader.execute(request)) {
                    is SuccessResult -> {
                        val bitmap = (result.image as? coil3.BitmapImage)?.bitmap
                        bitmap?.let { bmp ->
                            try {
                                Palette.from(bmp).generate().dominantSwatch?.let { swatch ->
                                    dominantColor = Color(swatch.rgb)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    val backgroundBrush = if (isPlaying && dominantColor != Color.Transparent) {
        Brush.horizontalGradient(
            colors = listOf(
                dominantColor.copy(alpha = 0.3f),
                Color.Transparent
            )
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    }

    Box(modifier = modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    text = track.title,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                    color = if (isPlaying && dominantColor != Color.Transparent) dominantColor else MaterialTheme.colorScheme.onSurface
                )
            },
            supportingContent = { Text(track.artist) },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = track.albumArtUri,
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize(),
                        error = {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = "Music Placeholder",
                                tint = Color.LightGray
                            )
                        },
                        loading = {
                            // Empty box
                        }
                    )
                }
            },
            trailingContent = {
                IconButton(onClick = { /* TODO: Track menu */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More"
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .background(backgroundBrush)
                .clickable(onClick = onClick)
        )

        if (isPlaying && dominantColor != Color.Transparent) {
            ParticlesEffectCanvas(
                color = dominantColor,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
