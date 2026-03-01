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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.MainViewModel
import com.laconical.player.LocalSharedTransitionScope
import androidx.compose.animation.SharedTransitionScope
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Full-screen "Now Playing" UI for the expanded bottom sheet.
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
    val currentAmplitude by viewModel.currentNormalizedAmplitude.collectAsState()

    if (currentTrack == null) return

    val track = currentTrack!!
    val themeColor = dominantColor ?: Color(0xFF1E1E1E)

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
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
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
                    text = track.album.uppercase(),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )

                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "More",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Album Art — driven by real Amplituda amplitude
            PulsatingAlbumArt(
                trackId = track.id,
                trackData = track.dataPath ?: track.mediaUri,
                amplitude = currentAmplitude,
                dominantColor = themeColor,
                sharedTransitionScope = sharedTransitionScope,
                isSharedVisible = isSharedVisible
            )

            Spacer(modifier = Modifier.weight(1f))

            // Track Info Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier = Modifier.then(
                            if (sharedTransitionScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedElementWithCallerManagedVisibility(
                                        sharedContentState = rememberSharedContentState(key = "title_${track.id}"),
                                        visible = isSharedVisible
                                    )
                                }
                            } else Modifier
                        )
                    )
                    Text(
                        text = track.artist,
                        color = Color.Gray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                IconButton(onClick = { }) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Visualizer Seek Bar
            VisualizerSeekBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp),
                waveform = waveform,
                progress = progress,
                duration = duration,
                onSeek = { viewModel.seekTo(it) },
                activeColor = themeColor,
                isPlaying = isPlaying
            )

            // Time Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(currentPosition), color = Color.Gray, fontSize = 12.sp)
                Text(text = formatTime(duration), color = Color.Gray, fontSize = 12.sp)
            }

            // Playback Controls
            PlaybackControls(
                isPlaying = isPlaying,
                themeColor = themeColor,
                onTogglePlay = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.skipToPrevious() },
                onNext = { viewModel.skipToNext() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Divider & Footer
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.8f),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.15f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { }) {
                    Icon(Icons.Outlined.Shuffle, contentDescription = "Shuffle", tint = Color.Gray, modifier = Modifier.size(22.dp))
                }
                TextButton(onClick = { }) {
                    Text("UP NEXT", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { }) {
                    Text("LYRICS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { }) {
                    Icon(Icons.Outlined.Repeat, contentDescription = "Repeat", tint = Color.Gray, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Album art that scales with real audio amplitude
 * ────────────────────────────────────────────────────────────────────── */

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PulsatingAlbumArt(
    trackId: Long,
    trackData: String,
    amplitude: Float,
    dominantColor: Color,
    sharedTransitionScope: SharedTransitionScope?,
    isSharedVisible: Boolean
) {
    // Quadratic curve: crushes small values, only real beats punch through
    val shapedAmplitude = amplitude * amplitude

    // Range: 0.98 (silence) → 1.02 (loudest) — subtle, organic breathing
    val animatedPulse by animateFloatAsState(
        targetValue = 0.98f + (shapedAmplitude * 0.04f),
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 280f
        ),
        label = "PulseAnim"
    )

    // Stable model identity — prevents image reload on parent recomposition
    val imageModel = remember(trackData) { AudioArtData(trackData) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = animatedPulse
                scaleY = animatedPulse
            },
        contentAlignment = Alignment.Center
    ) {
        // Glow — blur radius and opacity now scale continuously with amplitude
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    color = dominantColor.toArgb()
                    maskFilter = BlurMaskFilter(
                        70f + shapedAmplitude * 40f,
                        BlurMaskFilter.Blur.NORMAL
                    )
                    alpha = (25 + (shapedAmplitude * 60)).toInt().coerceIn(20, 100)
                }
                canvas.nativeCanvas.drawRoundRect(
                    0f, 0f, size.width, size.height,
                    24.dp.toPx(), 24.dp.toPx(),
                    paint
                )
            }
        }

        AsyncImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (sharedTransitionScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElementWithCallerManagedVisibility(
                                sharedContentState = rememberSharedContentState(key = "album_art_$trackId"),
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

/* ──────────────────────────────────────────────────────────────────────
 *  Waveform seek bar (unchanged)
 * ────────────────────────────────────────────────────────────────────── */

@Composable
fun VisualizerSeekBar(
    modifier: Modifier = Modifier,
    waveform: FloatArray,
    progress: Float,
    duration: Long,
    onSeek: (Float) -> Unit,
    activeColor: Color,
    isPlaying: Boolean
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var seekTarget by remember { mutableFloatStateOf(-1f) }
    var frozenProgress by remember { mutableFloatStateOf(0f) }
    var stableProgress by remember { mutableFloatStateOf(progress) }

    LaunchedEffect(progress) {
        if (!isDragging && seekTarget < 0f) stableProgress = progress
    }

    LaunchedEffect(progress) {
        if (seekTarget >= 0f && kotlin.math.abs(progress - seekTarget) < 0.01f) seekTarget = -1f
    }

    LaunchedEffect(seekTarget) {
        if (seekTarget >= 0f) { delay(1500); seekTarget = -1f }
    }

    val displayedProgress = when {
        isDragging       -> frozenProgress
        seekTarget >= 0f -> seekTarget
        else             -> stableProgress
    }

    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) { withFrameNanos { phase = (phase + 0.0005f) % 1000f } }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val p = (offset.x / size.width).coerceIn(0f, 1f)
                    seekTarget = p; stableProgress = p; onSeek(p)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        frozenProgress = stableProgress
                        seekTarget = -1f
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        seekTarget = dragProgress; stableProgress = dragProgress
                        onSeek(dragProgress); isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        val cWidth = constraints.maxWidth.toFloat()
        val cHeight = constraints.maxHeight.toFloat()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val waveHeight = cHeight * 0.4f
            val midY = cHeight * 0.7f

            val path = Path()
            if (waveform.isNotEmpty()) {
                val step = cWidth / (waveform.size - 1).coerceAtLeast(1)
                val phaseOffset = phase * 100f

                for (i in waveform.indices) {
                    val samplePos = (i.toFloat() + phaseOffset).mod(waveform.size.toFloat())
                    val idx0 = samplePos.toInt().coerceIn(0, waveform.size - 1)
                    val idx1 = (idx0 + 1) % waveform.size
                    val frac = samplePos - idx0.toFloat()
                    val value = waveform[idx0] * (1f - frac) + waveform[idx1] * frac

                    val amp = (value - 0.5f) * waveHeight
                    val x = i * step
                    val y = midY + amp
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
            } else {
                path.moveTo(0f, midY); path.lineTo(cWidth, midY)
            }

            drawPath(path, Color.White.copy(alpha = 0.15f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))

            val playedWidth = cWidth * displayedProgress
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.clipRect(-10.dp.toPx(), 0f, playedWidth, cHeight)
                drawPath(path, activeColor.copy(alpha = 0.9f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
                canvas.restore()
            }

            if (isDragging) {
                val lineX = cWidth * dragProgress
                val lineH = waveHeight * 1.2f
                val lineW = 3.dp.toPx()
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(lineX - lineW / 2f, midY - lineH / 2f),
                    size = Size(lineW, lineH),
                    cornerRadius = CornerRadius(lineW / 2f)
                )
            }
        }

        if (isDragging) {
            val timeText = formatTime((dragProgress * duration).toLong())
            val thumbX = maxWidth * dragProgress
            Text(
                text = timeText,
                modifier = Modifier
                    .offset(x = thumbX, y = 4.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.placeRelative(-placeable.width / 2, 0)
                        }
                    },
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Playback controls (unchanged)
 * ────────────────────────────────────────────────────────────────────── */

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
        Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
    }

    val animatedButtonColor by animateColorAsState(
        targetValue = buttonBgColor, animationSpec = tween(800), label = "ButtonColor"
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
        }

        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(animatedButtonColor).clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = isPlaying, label = "PlayPause") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(42.dp)
                )
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun Footer() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(modifier = Modifier.fillMaxWidth(0.6f), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) { Icon(Icons.Outlined.Shuffle, "Shuffle", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp)) }
            TextButton(onClick = { }) { Text("UP NEXT", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            TextButton(onClick = { }) { Text("LYRICS", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            IconButton(onClick = { }) { Icon(Icons.Outlined.Repeat, "Repeat", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp)) }
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Particle system — smooth energy-based transitions, no abrupt freeze
 * ────────────────────────────────────────────────────────────────────── */

@Composable
fun ParticleSystem(
    isPlaying: Boolean,
    color: Color
) {
    var time by remember { mutableLongStateOf(0L) }
    var lastTime by remember { mutableLongStateOf(0L) }

    // Smooth 0→1 energy instead of a hard boolean flip.
    // Ramps up in 600 ms (play), winds down over 1500 ms (pause).
    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isPlaying) 600 else 1500,
            easing = FastOutSlowInEasing
        ),
        label = "ParticleEnergy"
    )

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
        val dt = if (lastTime == 0L) 0.016f
        else ((time - lastTime) / 1_000_000_000f).coerceIn(0f, 0.05f)

        particles.forEach { p ->
            p.update(size.width, size.height, dt, energy)
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
        y = if (initial) Random.nextFloat() * height else Random.nextFloat() * (height * 0.15f)
        angle = Random.nextFloat() * (2f * Math.PI.toFloat())
        speed = 10f + Random.nextFloat() * 20f
        radius = 1.5f + Random.nextFloat() * 4f
        maxLife = 4f + Random.nextFloat() * 5f
        life = if (initial) Random.nextFloat() * maxLife else maxLife
        fadeAlpha = 1f
    }

    /**
     * @param energy 0 = fully paused, 1 = fully playing.
     *               Smoothly animated by the caller so particles
     *               never snap between states.
     */
    fun update(width: Float, height: Float, dt: Float, energy: Float) {
        if (maxLife == 0f) {
            spawn(width, height, true)
        }

        // Movement slows to 30 % of normal when paused
        val speedMult = 0.3f + energy * 0.7f
        x += kotlin.math.cos(angle) * speed * speedMult * dt
        y += kotlin.math.sin(angle) * speed * speedMult * dt
        angle += (Random.nextFloat() - 0.5f) * 0.15f

        // Brightness dims to 25 % when paused
        fadeAlpha = 0.25f + energy * 0.75f

        // Life drains at 15 % speed when paused, full speed when playing
        life -= dt * (0.15f + energy * 0.85f)
        if (life <= 0f) {
            spawn(width, height, false)
        }

        // Wrap around edges
        if (x < -10f) x = width + 10f
        if (x > width + 10f) x = -10f
        if (y > height + 10f) y = -10f
        if (y < -10f) y = height + 10f
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Helpers
 * ────────────────────────────────────────────────────────────────────── */

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Color.toHsl(): FloatArray {
    val hsl = FloatArray(3)
    val r = red; val g = green; val b = blue
    val max = maxOf(r, maxOf(g, b))
    val min = minOf(r, minOf(g, b))
    hsl[2] = (max + min) / 2
    if (max == min) {
        hsl[0] = 0f; hsl[1] = 0f
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