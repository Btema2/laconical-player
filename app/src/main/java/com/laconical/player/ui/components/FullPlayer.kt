package com.laconical.player.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.MainViewModel
import kotlinx.coroutines.flow.StateFlow
import java.util.*
import kotlin.math.*

/**
 * Full-screen "Now Playing" UI for the expanded bottom sheet.
 */
@Composable
fun FullPlayer(
    viewModel: MainViewModel,
    expansionAlpha: Float,
    onCollapse: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val dominantColor by viewModel.playingTrackDominantColor.collectAsState()
    val beatPulse by viewModel.beatPulse.collectAsState()
    val waveform by viewModel.waveform.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    
    if (currentTrack == null) return

    val themeColor = dominantColor ?: Color(0xFF1E1E1E)
    val darkenedBg = themeColor.copy(alpha = 1f).toHsl().let { (h, s, l) ->
        Color.hsl(h, s, (l * 0.15f).coerceIn(0.02f, 0.1f))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkenedBg)
            .graphicsLayer { alpha = expansionAlpha }
    ) {
        // 1. Particle System Environment
        ParticleSystem(
            isPlaying = isPlaying,
            color = themeColor.copy(alpha = 0.4f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 2. Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = currentTrack!!.album.uppercase(),
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                IconButton(onClick = { /* More options */ }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // 3. Pulsating Album Art centerpiece
            PulsatingAlbumArt(
                trackData = currentTrack!!.dataPath ?: currentTrack!!.mediaUri,
                beatPulse = beatPulse,
                dominantColor = themeColor
            )

            Spacer(modifier = Modifier.weight(0.5f))

            // 4. Track Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack!!.title,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = currentTrack!!.artist,
                        color = Color.LightGray,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                
                IconButton(onClick = { /* Like toggle */ }) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 5. Visualizer Progress Bar
            VisualizerSeekBar(
                waveform = waveform,
                progress = progress,
                onSeek = { viewModel.seekTo(it) },
                activeColor = themeColor,
                currentTime = formatTime(currentPosition),
                totalTime = formatTime(duration)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 6. Playback Controls
            PlaybackControls(
                isPlaying = isPlaying,
                themeColor = themeColor,
                onTogglePlay = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.skipToPrevious() },
                onNext = { viewModel.skipToNext() }
            )

            Spacer(modifier = Modifier.weight(0.8f))

            // 7. YTM-Style Footer
            Footer()
        }
    }
}

@Composable
fun PulsatingAlbumArt(
    trackData: String,
    beatPulse: Float,
    dominantColor: Color
) {
    // Pulse animation: base scale is 0.95, pulse adds up to 0.05
    val animatedPulse by animateFloatAsState(
        targetValue = 0.95f + (beatPulse * 0.05f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PulseAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = animatedPulse
                scaleY = animatedPulse
            },
        contentAlignment = Alignment.Center
    ) {
        // Neon Glow behind the art
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    color = dominantColor.toArgb()
                    maskFilter = BlurMaskFilter(if (beatPulse > 0.5f) 80f else 60f, BlurMaskFilter.Blur.NORMAL)
                    alpha = (0.3f + (beatPulse * 0.3f) * 255).toInt().coerceIn(40, 180)
                }
                canvas.nativeCanvas.drawRoundRect(
                    0f, 0f, size.width, size.height,
                    48.dp.toPx(), 48.dp.toPx(),
                    paint
                )
            }
        }

        AsyncImage(
            model = AudioArtData(trackData),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(32.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun VisualizerSeekBar(
    waveform: FloatArray,
    progress: Float,
    onSeek: (Float) -> Unit,
    activeColor: Color,
    currentTime: String,
    totalTime: String
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onSeek(offset.x / size.width)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        onSeek(change.position.x / size.width)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path()
                val width = size.width
                val height = size.height
                val midY = height / 2
                
                if (waveform.isNotEmpty()) {
                    val step = width / waveform.size
                    waveform.forEachIndexed { index, value ->
                        // Waveform values are 0..1, map to height
                        val amplitude = (value - 0.5f) * height * 0.8f
                        if (index == 0) {
                            path.moveTo(0f, midY + amplitude)
                        } else {
                            path.lineTo(index * step, midY + amplitude)
                        }
                    }
                } else {
                    // Fallback straight line
                    path.moveTo(0f, midY)
                    path.lineTo(width, midY)
                }

                // Draw background (unplayed)
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.15f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Draw active (played) portion using a mask/clip
                val playedWidth = width * progress
                drawIntoCanvas { canvas ->
                    canvas.save()
                    canvas.clipRect(0f, 0f, playedWidth, height)
                    drawPath(
                        path = path,
                        color = activeColor.copy(alpha = 0.9f),
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                    canvas.restore()
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = currentTime, color = Color.Gray, fontSize = 12.sp)
            Text(text = totalTime, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    themeColor: Color,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { /* Shuffle */ }) {
            Icon(Icons.Outlined.Shuffle, contentDescription = "Shuffle", tint = Color.LightGray)
        }

        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        // Main Play/Pause Button
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(themeColor.copy(alpha = 0.2f))
                .clickable(onClick = onTogglePlay)
                .drawBehind {
                    drawCircle(
                        color = themeColor.copy(alpha = 0.15f),
                        radius = size.width / 2 + 8.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        IconButton(onClick = { /* Repeat */ }) {
            Icon(Icons.Outlined.Repeat, contentDescription = "Repeat", tint = Color.LightGray)
        }
    }
}

@Composable
fun Footer() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.6f),
            thickness = 0.5.dp,
            color = Color.White.copy(alpha = 0.1f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TextButton(onClick = { }) {
                Text("UP NEXT", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { }) {
                Text("LYRICS", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ParticleSystem(
    isPlaying: Boolean,
    color: Color
) {
    val particles = remember { List(15) { DriftParticle() } }
    val infiniteTransition = rememberInfiniteTransition(label = "ParticleAnim")
    
    // We update the positions based on a "motion" value that increments when playing
    val motion by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(60000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MotionFlow"
    )

    var lastMotion by remember { mutableFloatStateOf(0f) }
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        val dt = if (isPlaying) (motion - lastMotion) else 0f
        lastMotion = motion
        
        particles.forEach { p ->
            if (isPlaying) {
                p.update(size.width, size.height, dt)
            }
            drawCircle(
                color = color,
                radius = p.radius,
                center = Offset(p.x, p.y)
            )
        }
    }
}

private class DriftParticle {
    var x = Math.random().toFloat() * 1000f
    var y = Math.random().toFloat() * 2000f
    var vx = (Math.random().toFloat() - 0.5f) * 0.5f
    var vy = (Math.random().toFloat() - 0.5f) * 0.5f
    val radius = 2f + Math.random().toFloat() * 6f

    fun update(width: Float, height: Float, dt: Float) {
        // Random drift (Brownian-ish)
        vx += (Math.random().toFloat() - 0.5f) * 0.05f
        vy += (Math.random().toFloat() - 0.5f) * 0.05f
        
        // Speed limit
        vx = vx.coerceIn(-1f, 1f)
        vy = vy.coerceIn(-1f, 1f)

        x += vx * 10f
        y += vy * 10f

        if (x < 0f) x = width
        if (x > width) x = 0f
        if (y < 0f) y = height
        if (y > height) y = 0f
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Helper to convert Color to HSL for darkening */
private fun Color.toHsl(): FloatArray {
    val hsl = FloatArray(3)
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, maxOf(g, b))
    val min = minOf(r, minOf(g, b))
    hsl[2] = (max + min) / 2

    if (max == min) {
        hsl[0] = 0f
        hsl[1] = 0f
    } else {
        val d = max - min
        hsl[1] = if (hsl[2] > 0.5f) d / (2f - max - min) else d / (max + min)
        when (max) {
            r -> hsl[0] = (g - b) / d + (if (g < b) 6f else 0f)
            g -> hsl[0] = (b - r) / d + 2f
            b -> hsl[0] = (r - g) / d + 4f
        }
        hsl[0] /= 6f
    }
    return hsl
}
