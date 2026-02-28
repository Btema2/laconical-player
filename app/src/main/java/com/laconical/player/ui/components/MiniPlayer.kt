package com.laconical.player.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.MainViewModel
import com.laconical.player.LocalSharedTransitionScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayer(
    viewModel: MainViewModel,
    isSharedVisible: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val vibeColor by viewModel.playingTrackDominantColor.collectAsState()

    if (currentTrack == null) return

    // Soften the color intensity for bright thumbnails
    val baseColor = if (vibeColor != null) {
        // Blend with dark color to de-intensify
        val alpha = 0.6f
        Color(
            red = vibeColor!!.red * alpha,
            green = vibeColor!!.green * alpha,
            blue = vibeColor!!.blue * alpha,
            alpha = 1f
        )
    } else {
        Color(0xFF1E1E1E)
    }
    
    // Floating rounded rectangle (not circular pill)
    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp) // Slightly more padding for floating effect
            .fillMaxWidth()
            .height(75.dp) // ~10% taller than 68dp
            .clip(RoundedCornerShape(16.dp)) 
            .background(Color(0xFF0D0D10)) // Solid base to prevent see-through
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = 0.5f), // Softer start
                        baseColor.copy(alpha = 0.15f), // Fades earlier
                        Color(0xF00D0D10) // Darker end
                    )
                )
            )
            // Removed direct clickable here to avoid blocking internal buttons
    ) {
        // Transparent interaction layer that doesn't overlap the control buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 120.dp) // Leave space for controls on the right
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null, // No ripple for the main area to keep it clean
                    onClick = onClick
                )
        )
        // High-level top border for glass effect
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 24.dp),
            thickness = 0.5.dp,
            color = Color(0x22FFFFFF)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Album Art Thumbnail (Rounded Square)
            Box(
                modifier = Modifier
                    .size(52.dp) // Slightly larger for taller player
                    .then(
                        if (sharedTransitionScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElementWithCallerManagedVisibility(
                                    sharedContentState = rememberSharedContentState(key = "album_art_${currentTrack!!.id}"),
                                    visible = isSharedVisible
                                )
                            }
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(10.dp)) // Rounded square
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                val loadTarget = if (!currentTrack!!.dataPath.isNullOrEmpty()) currentTrack!!.dataPath else currentTrack!!.mediaUri
                AsyncImage(
                    model = AudioArtData(loadTarget!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Info (Title on top, Artist on bottom)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack!!.title,
                    color = Color.White,
                    fontSize = 15.sp, // Slightly larger
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.then(
                        if (sharedTransitionScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedElementWithCallerManagedVisibility(
                                    sharedContentState = rememberSharedContentState(key = "title_${currentTrack!!.id}"),
                                    visible = isSharedVisible
                                )
                            }
                        } else Modifier
                    )
                )
                Text(
                    text = currentTrack!!.artist,
                    color = Color(0xFFBBBBBB),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls with pulsing glow
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlowIconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                
                GlowIconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp) // Slightly larger
                    )
                }

                GlowIconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }

        // Functional Progress Bar at the bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp) // Slightly thinner, more elegant
                .align(Alignment.BottomCenter)
                .background(Color(0x11FFFFFF))
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        viewModel.seekTo(newProgress)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(baseColor.copy(alpha = 0.9f))
            )
        }
    }
}

@Composable
fun GlowIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.6f else 0f,
        animationSpec = if (isPressed) infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ) else tween(200),
        label = "glowAnim"
    )

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.shadow(
            elevation = (glowAlpha * 10).dp,
            shape = RoundedCornerShape(50),
            clip = false,
            ambientColor = Color.White.copy(alpha = glowAlpha),
            spotColor = Color.White.copy(alpha = glowAlpha)
        )
    ) {
        content()
    }
}
