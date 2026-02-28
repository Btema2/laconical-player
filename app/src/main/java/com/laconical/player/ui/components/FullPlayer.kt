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
import com.laconical.player.LocalSharedTransitionScope
import androidx.compose.animation.SharedTransitionScope
import kotlin.random.Random

/**
 * Full-screen "Now Playing" UI for the expanded bottom sheet.
 * Refined to match modern, premium music player aesthetics.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FullPlayer(
    viewModel: MainViewModel,
    expandedFraction: Float,
    isSharedVisible: Boolean,
    onCollapse: () -> Unit
) {
    val sharedTransitionScope = LocalSharedTransitionScope.current
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(animatedBg)
            .graphicsLayer { alpha = expandedFraction }
    ) {
        // 1. Particle System Environment
        ParticleSystem(
            isPlaying = isPlaying,
            color = themeColor.copy(alpha = 0.45f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 2. Top Bar (height 48.dp)
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
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
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.sp,
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

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Album Art Piece (CRITICAL: massive)
            PulsatingAlbumArt(
                trackId = currentTrack!!.id,
                trackData = currentTrack!!.dataPath ?: currentTrack!!.mediaUri,
                beatPulse = beatPulse,
                dominantColor = themeColor,
                sharedTransitionScope = sharedTransitionScope,
                isSharedVisible = isSharedVisible
            )

            Spacer(modifier = Modifier.weight(1f))

            // 4. Track Info Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack!!.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
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
                        color = Color.Gray,
                        fontSize = 16.sp,
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

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Mirrored Visualizer Seek Bar
            VisualizerSeekBar(
                modifier = Modifier.height(32.dp).padding(horizontal = 24.dp),
                waveform = waveform,
                progress = progress,
                onSeek = { viewModel.seekTo(it) },
                activeColor = themeColor
            )

            // Time Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(currentPosition), color = Color.Gray, fontSize = 12.sp)
                Text(text = formatTime(duration), color = Color.Gray, fontSize = 12.sp)
            }

            // 6. Playback Controls (Massive circle, even spacing)
            PlaybackControls(
                isPlaying = isPlaying,
                themeColor = themeColor,
                onTogglePlay = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.skipToPrevious() },
                onNext = { viewModel.skipToNext() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Divider & Footer controls
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.8f),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { /* Shuffle */ }) {
                    Icon(
                        Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                TextButton(onClick = { }) {
                    Text("UP NEXT", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { }) {
                    Text("LYRICS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { /* Repeat */ }) {
                    Icon(
                        Icons.Outlined.Repeat,
                        contentDescription = "Repeat",
                        tint = Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PulsatingAlbumArt(
    trackId: Long,
    trackData: String,
    beatPulse: Float,
    dominantColor: Color,
    sharedTransitionScope: SharedTransitionScope?,
    isSharedVisible: Boolean
) {
    val animatedPulse by animateFloatAsState(
        targetValue = 0.97f + (beatPulse * 0.03f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PulseAnim"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
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
                    maskFilter = BlurMaskFilter(if (beatPulse > 0.5f) 90f else 70f, BlurMaskFilter.Blur.NORMAL)
                    alpha = (30 + (beatPulse * 40)).toInt().coerceIn(20, 100)
                }
                canvas.nativeCanvas.drawRoundRect(
                    0f, 0f, size.width, size.height,
                    24.dp.toPx(), 24.dp.toPx(),
                    paint
                )
            }
        }

        AsyncImage(
            model = AudioArtData(trackData),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (sharedTransitionScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElementWithCallerManagedVisibility(
                                sharedContentState = rememberSharedContentState(key = "album_art_${trackId}"),
                                visible = isSharedVisible
                            )
                        }
                    } else Modifier
                )
                .clip(RoundedCornerShape(24.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun VisualizerSeekBar(
    modifier: Modifier = Modifier,
    waveform: FloatArray,
    progress: Float,
    onSeek: (Float) -> Unit,
    activeColor: Color
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
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
    themeColor: Color,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val buttonBgColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(
            hue = hsl[0] * 360f,
            saturation = hsl[1].coerceIn(0.2f, 0.5f),
            lightness = 0.4f
        )
    }

    val animatedButtonColor by animateColorAsState(
        targetValue = buttonBgColor,
        animationSpec = tween(800),
        label = "ButtonColor"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(animatedButtonColor)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = isPlaying, label = "PlayPause") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun Footer() {
    // This Footer composable is no longer used directly in FullPlayer as its content was moved/modified.
    // Keeping it here for now in case it's used elsewhere or for future reference.
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
    var time by remember { mutableLongStateOf(0L) }
    var lastTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                lastTime = time
                time = frameNanos
            }
        }
    }

    val particles = remember { List(25) { DriftParticle() } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val currentTime = time
        val dt = if (lastTime == 0L) 0.016f
                 else ((currentTime - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)
        
        particles.forEach { p ->
            p.update(size.width, size.height, dt, isPlaying)
            val computedAlpha = (p.life / p.maxLife.coerceAtLeast(0.1f)) * p.fadeAlpha
            if (computedAlpha > 0.01f) {
                drawCircle(
                    color = color.copy(alpha = color.alpha * computedAlpha),
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
            }
        }
    }
}

private class DriftParticle {
    var x = 0f
    var y = 0f
    var angle = 0f
    var speed = 0f
    var radius = 0f
    var life = 0f
    var maxLife = 0f
    var fadeAlpha = 1f

    private fun spawn(width: Float, height: Float, initial: Boolean) {
        x = Random.nextFloat() * width
        // Spawn randomly over screen on start, but at the top later
        y = if (initial) Random.nextFloat() * height else Random.nextFloat() * (height * 0.15f)
        
        // Random directions (radians)
        angle = Random.nextFloat() * (2f * Math.PI.toFloat())
        
        // Slower, smoother speed
        speed = 10f + Random.nextFloat() * 20f
        
        radius = 1.5f + Random.nextFloat() * 4f
        maxLife = 4f + Random.nextFloat() * 5f // 4 to 9 seconds life
        life = if (initial) Random.nextFloat() * maxLife else maxLife
        fadeAlpha = 1f
    }

    fun update(width: Float, height: Float, dt: Float, isPlaying: Boolean) {
        if (maxLife == 0f) {
            spawn(width, height, true)
        }

        // Apply movement
        x += kotlin.math.cos(angle) * speed * dt
        y += kotlin.math.sin(angle) * speed * dt
        
        // Slight brownian turning
        angle += (Random.nextFloat() - 0.5f) * 0.2f

        if (isPlaying) {
            fadeAlpha = 1f
            life -= dt
            if (life <= 0f) {
                spawn(width, height, false)
            }
        } else {
            // Natural death when paused, multipling alpha by 0.85 per frame as requested
            fadeAlpha *= 0.85f
        }
        
        // Loop around edges softly
        if (x < -10f) x = width + 10f
        if (x > width + 10f) x = -10f
        if (y > height + 10f) y = -10f
        if (y < -10f) y = height + 10f
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
