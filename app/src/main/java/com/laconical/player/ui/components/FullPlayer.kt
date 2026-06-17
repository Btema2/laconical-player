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
import androidx.media3.common.Player
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.laconical.player.ui.AudioArtData
import com.laconical.player.ui.MainViewModel
import com.laconical.player.ui.toHsl
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Full-screen "Now Playing" UI for the expanded bottom sheet.
 *
 * @param expandedFraction 0 = fully collapsed, 1 = fully expanded.
 *   Used to fade in the full-player UI independently of the morphing overlay.
 */
@Composable
fun FullPlayer(
    viewModel: MainViewModel,
    expandedFraction: Float,
    onCollapse: () -> Unit,
    onTitlePositioned: (Float) -> Unit = {},
    /** Reports root-space top-left (x, y) of the artist text for the morphing overlay. */
    onArtistPositioned: (Float, Float) -> Unit = { _, _ -> },
    /** Reports root-space center (x, y) of Prev, Play, Next buttons for the morphing overlay. */
    onPlayControlsPositioned: (prevX: Float, prevY: Float, playX: Float, playY: Float, nextX: Float, nextY: Float) -> Unit = { _, _, _, _, _, _ -> },
    onAlbumArtPositioned: (x: Float, y: Float, sizePx: Float) -> Unit = { _, _, _ -> },
    onShowQueue: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onShowMenu: () -> Unit = {},
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val dominantColor by viewModel.playingTrackDominantColor.collectAsState()
    val beatPulse by viewModel.beatPulse.collectAsState()
    val waveform by viewModel.waveform.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    if (currentTrack == null) return

    val track = currentTrack!!
    val themeColor = dominantColor ?: Color(0xFF1E1E1E)

    // Matches the play/pause button background — used for the active portion of the seek bar
    val seekBarActiveColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
    }

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

    val particleColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
    }

    var likePressed by remember { mutableStateOf(false) }
    val likeScale by animateFloatAsState(
        targetValue = if (likePressed) 1.4f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 600f),
        label = "LikeScale",
    )
    LaunchedEffect(likePressed) {
        if (likePressed) { delay(50); likePressed = false }
    }

    // The full player content fades in as the sheet expands
    val contentAlpha = expandedFraction.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .background(animatedBg)
    ) {
        ParticleSystem(isPlaying = isPlaying, isVisible = expandedFraction > 0.01f, color = particleColor)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            val artSizeDp = minOf(maxWidth * 0.95f, maxHeight * 0.42f)
            val controlScale = (artSizeDp.value / 280f).coerceIn(0.85f, 1.15f)
            val playButtonSize = 72.dp * controlScale
            val skipIconSize = 48.dp * controlScale

        Column(
            modifier = Modifier.fillMaxSize(),
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0.7f to Color.Black,
                                        1.0f to Color.Transparent,
                                    ),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                )

                IconButton(onClick = onShowMenu) {
                    Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.weight(0.08f))

            // Album art layout spacer — the morphing overlay in LibraryScreen
            // renders the actual art on top; this just reserves the right amount of space.
            // Vertical drag: swipe up → queue, swipe down → collapse player.
            Spacer(
                modifier = Modifier
                    .size(artSizeDp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        onAlbumArtPositioned(pos.x, pos.y, coords.size.width.toFloat())
                    }
                    .pointerInput(Unit) {
                        val threshold = 80.dp.toPx()
                        var totalDragY = 0f
                        detectDragGestures(
                            onDragStart = { totalDragY = 0f },
                            onDrag = { _, dragAmount -> totalDragY += dragAmount.y },
                            onDragEnd = {
                                when {
                                    totalDragY < -threshold -> onShowQueue()
                                    totalDragY > threshold -> onCollapse()
                                }
                            }
                        )
                    }
            )

            // weight(0.165f) leaves 50% of the previous gap — pulls title/author closer to thumbnail
            Spacer(modifier = Modifier.weight(0.165f))

            // Track Info Row
            // Title AND artist are invisible layout ghosts — the morphing overlay in
            // LibraryScreen renders the real text on top, same pattern as the thumbnail.
            // onGloballyPositioned reports root-space coords for pixel-perfect alignment.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = Color.White.copy(alpha = 0f), // invisible ghost for layout only
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            onTitlePositioned(coords.positionInRoot().y)
                        }
                    )
                    Text(
                        text = track.artist,
                        color = Color.Gray.copy(alpha = 0f), // invisible ghost for layout only
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val p = coords.positionInRoot()
                            onArtistPositioned(p.x, p.y)
                        }
                    )
                }

                IconButton(onClick = { likePressed = true; onToggleFavorite() }) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isFavorite) Color(0xFFE84B7A) else Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { scaleX = likeScale; scaleY = likeScale },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Visualizer Seek Bar
            VisualizerSeekBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .padding(horizontal = 24.dp),
                waveform = waveform,
                progress = progress,
                duration = duration,
                onSeek = { viewModel.seekTo(it) },
                activeColor = seekBarActiveColor,
                isPlaying = isPlaying,
                expandedFraction = expandedFraction
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

            // Playback Controls ghost — invisible layout spacer that reports root-space
            // button centers. The morphing overlay in LibraryScreen renders the real buttons.
            Box(modifier = Modifier.graphicsLayer { alpha = 0f }) {
                PlaybackControls(
                    isPlaying = isPlaying,
                    themeColor = themeColor,
                    onTogglePlay = {},
                    onPrevious = {},
                    onNext = {},
                    playButtonSize = playButtonSize,
                    skipIconSize = skipIconSize,
                    onPrevPositioned  = { x, y -> onPlayControlsPositioned(x, y, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE) },
                    onPlayPositioned  = { x, y -> onPlayControlsPositioned(Float.MIN_VALUE, Float.MIN_VALUE, x, y, Float.MIN_VALUE, Float.MIN_VALUE) },
                    onNextPositioned  = { x, y -> onPlayControlsPositioned(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE, x, y) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

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
                TextButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = if (shuffleModeEnabled) Icons.Filled.Shuffle else Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (shuffleModeEnabled) seekBarActiveColor else Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                TextButton(onClick = onShowQueue) {
                    Text("UP NEXT", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { }) {
                    Text("LYRICS", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { viewModel.cycleRepeatMode() }) {
                    val repeatIcon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Filled.Repeat   // filled = visually distinct from OFF
                        else -> Icons.Outlined.Repeat
                    }
                    val repeatTint = if (repeatMode != Player.REPEAT_MODE_OFF) seekBarActiveColor else Color.Gray
                    Icon(repeatIcon, contentDescription = "Repeat", tint = repeatTint, modifier = Modifier.size(22.dp))
                }
            }
        } // Column
        } // BoxWithConstraints
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Album art with real audio amplitude pulse (no shared element)
 * ────────────────────────────────────────────────────────────────────── */

@Composable
fun PulsatingAlbumArt(
    trackData: String,
    amplitude: Float,
    dominantColor: Color,
    modifier: Modifier = Modifier,
) {
    val shapedAmplitude = amplitude * amplitude

    val animatedPulse by animateFloatAsState(
        targetValue = 0.98f + (shapedAmplitude * 0.04f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
        label = "PulseAnim"
    )

    val imageModel = remember(trackData) { AudioArtData(trackData) }

    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Glow — separate from the image so it never morphs
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = animatedPulse; scaleY = animatedPulse }
        ) {
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    color = dominantColor.toArgb()
                    maskFilter = BlurMaskFilter(70f + shapedAmplitude * 40f, BlurMaskFilter.Blur.NORMAL)
                    alpha = (25 + (shapedAmplitude * 60)).toInt().coerceIn(20, 100)
                }
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, 24.dp.toPx(), 24.dp.toPx(), paint)
            }
        }

        AsyncImage(
            model = imageModel,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = animatedPulse; scaleY = animatedPulse }
                .clip(RoundedCornerShape(24.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Waveform seek bar
 * ────────────────────────────────────────────────────────────────────── */

@Composable
fun VisualizerSeekBar(
    modifier: Modifier = Modifier,
    waveform: FloatArray,
    progress: Float,
    duration: Long,
    onSeek: (Float) -> Unit,
    activeColor: Color,
    isPlaying: Boolean,
    expandedFraction: Float
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
    val animatePhase = isPlaying && expandedFraction > 0.01f
    LaunchedEffect(animatePhase) {
        if (animatePhase) {
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
            val midY = cHeight * 0.55f

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
                val lineW = 3.dp.toPx()
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(lineX - lineW / 2f, 0f),
                    size = Size(lineW, cHeight),
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
                    .offset(x = thumbX, y = (-18).dp)
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
 *  Playback controls
 * ────────────────────────────────────────────────────────────────────── */

@Composable
fun PlaybackControls(
    isPlaying: Boolean,
    themeColor: Color,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    playButtonSize: Dp = 72.dp,
    skipIconSize: Dp = 48.dp,
    onPrevPositioned: (Float, Float) -> Unit = { _, _ -> },
    onPlayPositioned: (Float, Float) -> Unit = { _, _ -> },
    onNextPositioned: (Float, Float) -> Unit = { _, _ -> },
) {
    val playIconSize = remember(playButtonSize) { playButtonSize * (42f / 72f) }

    val buttonBgColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
    }

    val animatedButtonColor by animateColorAsState(
        targetValue = buttonBgColor, animationSpec = tween(800), label = "ButtonColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.onGloballyPositioned { coords ->
                val c = coords.positionInRoot()
                onPrevPositioned(c.x + coords.size.width / 2f, c.y + coords.size.height / 2f)
            }
        ) {
            Icon(Icons.Default.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(skipIconSize))
        }

        Box(
            modifier = Modifier
                .size(playButtonSize)
                .clip(CircleShape)
                .background(animatedButtonColor)
                .clickable(onClick = onTogglePlay)
                .onGloballyPositioned { coords ->
                    val c = coords.positionInRoot()
                    onPlayPositioned(c.x + coords.size.width / 2f, c.y + coords.size.height / 2f)
                },
            contentAlignment = Alignment.Center
        ) {
            Crossfade(targetState = isPlaying, label = "PlayPause") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(playIconSize)
                )
            }
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.onGloballyPositioned { coords ->
                val c = coords.positionInRoot()
                onNextPositioned(c.x + coords.size.width / 2f, c.y + coords.size.height / 2f)
            }
        ) {
            Icon(Icons.Default.SkipNext, "Next", tint = Color.White, modifier = Modifier.size(skipIconSize))
        }
    }
}

/* ──────────────────────────────────────────────────────────────────────
 *  Particle system — smooth energy-based transitions
 * ────────────────────────────────────────────────────────────────────── */

@Composable
fun ParticleSystem(isPlaying: Boolean, isVisible: Boolean, color: Color) {
    var frameTime by remember { mutableLongStateOf(0L) }
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    val energy by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "ParticleEnergy"
    )

    val particles = remember { List(25) { DriftParticle() } }

    // All state mutation happens here, outside the draw phase. When playback is
    // paused and all particles have died out, suspend on delay(100) instead of
    // burning 60fps cycles — matters for battery when FullPlayer is composed
    // but hidden (alpha=0) behind the mini player.
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            if (!isVisible || (energy < 0.005f && particles.all { it.life <= 0f })) {
                delay(100)
                lastNanos = 0L
                continue
            }
            withFrameNanos { nanos -> frameTime = nanos }
            val dt = if (lastNanos == 0L) 0.016f
                     else ((frameTime - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastNanos = frameTime

            if (canvasWidth <= 0f || canvasHeight <= 0f) continue

            particles.forEach { p ->
                p.update(canvasWidth, canvasHeight, dt, energy)
            }
        }
    }

    // Pure draw — reads particle state, no mutations.
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                canvasWidth = it.width.toFloat()
                canvasHeight = it.height.toFloat()
            }
    ) {
        if (frameTime == 0L) return@Canvas
        particles.forEach { p ->
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
    var x = 0f; var y = 0f; var angle = 0f; var speed = 0f
    var radius = 0f; var life = 0f; var maxLife = 0f; var fadeAlpha = 1f

    private fun spawn(width: Float, height: Float, initial: Boolean) {
        x = Random.nextFloat() * width
        y = if (initial) Random.nextFloat() * height else Random.nextFloat() * (height * 0.15f)
        angle = Random.nextFloat() * (2f * Math.PI.toFloat())
        speed = 10f + Random.nextFloat() * 20f
        radius = 4f + Random.nextFloat() * 8f
        maxLife = 4f + Random.nextFloat() * 5f
        life = if (initial) Random.nextFloat() * maxLife else maxLife
        fadeAlpha = 1f
    }

    fun update(width: Float, height: Float, dt: Float, energy: Float) {
        if (maxLife == 0f) spawn(width, height, true)
        val speedMult = 0.3f + energy * 0.7f
        x += kotlin.math.cos(angle) * speed * speedMult * dt
        y += kotlin.math.sin(angle) * speed * speedMult * dt
        angle += (Random.nextFloat() - 0.5f) * 0.15f
        fadeAlpha = energy
        life -= dt * (0.15f + energy * 0.85f)
        // Only respawn while playback has meaningful energy — prevents "TV snow"
        // flicker on pause where new particles would briefly appear during the
        // 1s energy tween-down before going dark.
        if (life <= 0f && energy > 0.02f) spawn(width, height, false)
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

