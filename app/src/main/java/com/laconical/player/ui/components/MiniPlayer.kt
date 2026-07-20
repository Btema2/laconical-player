package com.laconical.player.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laconical.player.ui.LocalAppSurface
import com.laconical.player.ui.MainViewModel
import com.laconical.player.ui.toHsl

@Composable
fun MiniPlayer(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    /** When true the album-art slot is left empty (morphing overlay renders it instead). */
    hideArt: Boolean = false,
    /** 0 = rest, 1 = first flick (pink warning), 2/3 = second+ flick (red warning). */
    warningStage: Int = 0,
    onClick: () -> Unit = {}
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val vibeColor by viewModel.playingTrackDominantColor.collectAsState()

    if (currentTrack == null) return

    val surfaceColor = LocalAppSurface.current

    // Bright, saturated accent derived from the album color so the mini progress bar actually
    // reads at rest (previously dimmed to 30% brightness — nearly invisible on the dark strip).
    val restAccent = if (vibeColor != null) {
        val hsl = vibeColor!!.toHsl()
        Color.hsl(
            hue = hsl[0] * 360f,
            saturation = hsl[1].coerceIn(0.35f, 0.65f),
            lightness = 0.55f
        )
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Swipe-down-to-remove warning tint: rest -> vivid orange -> vivid red, easing back on abort.
    // Orange/red reads as "warning" conventionally (pink doesn't); saturated/bright on purpose —
    // a subtle tint would be easy to miss as the warning signal.
    val baseColor by animateColorAsState(
        targetValue = when (warningStage) {
            0 -> restAccent
            1 -> Color(0xFFFF9500)
            else -> Color(0xFFFF2424)
        },
        animationSpec = tween(220),
        label = "MiniWarnAccent"
    )
    // Gradient/progress-bar intensity is boosted on top of the color change itself so the
    // warning reads clearly even at a glance, not just as a faint hue shift.
    val warnBoost = warningStage > 0

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .height(75.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surfaceColor)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        baseColor.copy(alpha = if (warnBoost) 0.75f else 0.45f),
                        baseColor.copy(alpha = if (warnBoost) 0.40f else 0.15f),
                        Color.Transparent,
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 120.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        )
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
            // Art slot — kept as a transparent placeholder so layout is stable
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (hideArt) Color.Transparent else Color(0xFF2A2A2E)),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title: always laid out so Column height is stable.
                // When hideArt=true the morphing overlay renders the title on top,
                // so we make it invisible (alpha=0) rather than replacing it with a
                // spacer — this guarantees pixel-perfect geometry alignment.
                Text(
                    text = currentTrack!!.title,
                    color = Color.White.copy(alpha = if (hideArt) 0f else 1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentTrack!!.artist,
                    color = Color(0xFFBBBBBB).copy(alpha = if (hideArt) 0f else 1f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Controls — invisible ghost when hideArt=true; morphing overlay in LibraryScreen renders them.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(if (hideArt) 0f else 1f)
            ) {
                GlowIconButton(onClick = { viewModel.skipToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(24.dp))
                }
                GlowIconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                GlowIconButton(onClick = { viewModel.skipToNext() }) {
                    Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.BottomCenter)
                .background(Color(0x33FFFFFF))
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
