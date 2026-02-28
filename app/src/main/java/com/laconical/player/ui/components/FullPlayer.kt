
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
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
import kotlin.random.Random

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

    // Match the tracks page background formula for visual consistency
    val bgColor = if (dominantColor != null) {
        val vibe = dominantColor!!
        Color(
            red = (0.04f * 0.92f) + (vibe.red * 0.08f),
            green = (0.04f * 0.92f) + (vibe.green * 0.08f),
            blue = (0.05f * 0.92f) + (vibe.blue * 0.08f),
            alpha = 1f
        )
    } else {
        Color(0xFF0A0A0C)
    }

    val animatedBg by animateColorAsState(
        targetValue = bgColor,
        animationSpec = tween(1000),
        label = "FullPlayerBg"
    )

    // Compute play-button color here so both ParticleSystem and PlaybackControls share it
    val buttonColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(
            hue = hsl[0] * 360f,
            saturation = hsl[1].coerceIn(0.15f, 0.45f),
            lightness = 0.38f
        )
    }
    val animatedButtonColor by animateColorAsState(
        targetValue = buttonColor,
        animationSpec = tween(800),
        label = "ButtonColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBg)
            .graphicsLayer { alpha = expansionAlpha }
    ) {
        // 1. Particle System Environment
        ParticleSystem(
            isPlaying = isPlaying,
            color = animatedButtonColor.copy(alpha = 0.45f)
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

            Spacer(modifier = Modifier.height(26.dp))

            // 3. Album Art
            PulsatingAlbumArt(
                trackData = currentTrack!!.dataPath ?: currentTrack!!.mediaUri,
                beatPulse = beatPulse,
                dominantColor = themeColor
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Track Info
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 14.dp),
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
                    Spacer(modifier = Modifier.height(2.dp))
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

            Spacer(modifier = Modifier.height(38.dp))

            // 5. Visualizer Seek Bar
            VisualizerSeekBar(
                waveform = waveform,
                progress = progress,
                onSeek = { viewModel.seekTo(it) },
                activeColor = themeColor
            )

            Spacer(modifier = Modifier.weight(1f))

            // 6. Playback Controls
            PlaybackControls(
                isPlaying = isPlaying,
                buttonColor = animatedButtonColor,
                onTogglePlay = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.skipToPrevious() },
                onNext = { viewModel.skipToNext() },
                currentTime = formatTime(currentPosition),
                totalTime = formatTime(duration)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 7. Footer with shuffle/loop + navigation
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
    val animatedPulse by animateFloatAsState(
        targetValue = 0.97f + (beatPulse * 0.03f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PulseAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = animatedPulse
                scaleY = animatedPulse
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle glow behind the art
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    color = dominantColor.toArgb()
                    maskFilter = BlurMaskFilter(if (beatPulse > 0.5f) 80f else 60f, BlurMaskFilter.Blur.NORMAL)
                    alpha = (0.25f + (beatPulse * 0.25f) * 255).toInt().coerceIn(30, 150)
                }
                canvas.nativeCanvas.drawRoundRect(
                    0f, 0f, size.width, size.height,
                    20.dp.toPx(), 20.dp.toPx(),
                    paint
                )
            }
        }

        AsyncImage(
            model = AudioArtData(trackData),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun VisualizerSeekBar(
    waveform: FloatArray,
    progress: Float,
    onSeek: (Float) -> Unit,
    activeColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
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
                    val amplitude = (value - 0.5f) * height * 0.8f
                    if (index == 0) {
                        path.moveTo(0f, midY + amplitude)
                    } else {
                        path.lineTo(index * step, midY + amplitude)
                    }
                }
            } else {
                path.moveTo(0f, midY)
                path.lineTo(width, midY)
            }

            // Background (unplayed)
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.15f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active (played) portion
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
}

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    buttonColor: Color,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    currentTime: String,
    totalTime: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Current time — left-aligned
        Text(
            text = currentTime,
            color = Color.LightGray,
            fontSize = 13.sp,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.weight(0.4f))

        // Previous — close to play/pause
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Main Play/Pause button with crossfade animation
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = isPlaying,
                animationSpec = tween(200),
                label = "PlayPauseCrossfade"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Next — close to play/pause
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
        }

        Spacer(modifier = Modifier.weight(0.4f))

        // Total time — right-aligned
        Text(
            text = totalTime,
            color = Color.LightGray,
            fontSize = 13.sp,
            modifier = Modifier.width(48.dp),
            textAlign = TextAlign.End
        )
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
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Shuffle */ }) {
                Icon(
                    Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
            TextButton(onClick = { }) {
                Text("UP NEXT", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { }) {
                Text("LYRICS", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = { /* Repeat */ }) {
                Icon(
                    Icons.Outlined.Repeat,
                    contentDescription = "Repeat",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun ParticleSystem(
    isPlaying: Boolean,
    color: Color
) {
    val particles = remember { List(24) { FullPlayerParticle() } }

    // Frame-based animation loop — no infiniteTransition, no pulse bug
    var time by remember { mutableLongStateOf(0L) }
    var lastTime by remember { mutableLongStateOf(0L) }

    // Smooth fade-in multiplier: snaps to 0 on each resume then animates to 1 over 900ms
    val resumeMultiplier = remember { Animatable(1f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            resumeMultiplier.snapTo(0f)
            resumeMultiplier.animateTo(1f, animationSpec = tween(durationMillis = 900))
        }
        // On pause: leave multiplier at 1 so particles die naturally by their own life
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                lastTime = time
                time = frameNanos
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val dt = if (lastTime == 0L) 0.016f
                 else ((time - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)

        val w = size.width
        val h = size.height
        // Upper region boundary — particles live in top ~45% of screen
        val upperBound = h * 0.45f

        particles.forEach { p ->
            // Always update position (allows natural death drift after pause)
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= dt * 0.5f  // faster fade

            // Kill particles that touch screen edges
            if (p.x < 0f || p.x > w || p.y < 0f || p.y > h) {
                p.life = 0f
            }

            if (p.life <= 0f) {
                if (isPlaying) {
                    // Respawn with staggered life to prevent sync pulse
                    val wasWaiting = p.life < -0.1f
                    p.life = if (wasWaiting) Random.nextFloat() * p.maxLife else p.maxLife
                    p.x = Random.nextFloat() * w
                    p.y = Random.nextFloat() * upperBound
                    val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
                    val speed = 40f + Random.nextFloat() * 55f
                    p.vx = kotlin.math.cos(angle) * speed
                    p.vy = kotlin.math.sin(angle) * speed
                }
                // When paused and life <= 0, particle stays dead (invisible)
            }

            // No wrapping — particles die at edges instead

            val alpha = (p.baseAlpha * (p.life / p.maxLife) * resumeMultiplier.value).coerceIn(0f, 1f)
            if (alpha > 0f) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
            }
        }
    }
}

private class FullPlayerParticle {
    var x = Random.nextFloat() * 1000f
    var y = Random.nextFloat() * 600f
    val radius = 4f + Random.nextFloat() * 7f
    val baseAlpha = 0.3f + Random.nextFloat() * 0.4f
    val maxLife = 1.5f + Random.nextFloat() * 2.5f  // 1.5–4 seconds
    var life = Random.nextFloat() * maxLife
    private val initAngle = Random.nextFloat() * (2f * Math.PI.toFloat())
    private val initSpeed = 40f + Random.nextFloat() * 55f
    var vx = kotlin.math.cos(initAngle) * initSpeed
    var vy = kotlin.math.sin(initAngle) * initSpeed
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
