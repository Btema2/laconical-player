package com.laconical.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.BlurMaskFilter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.lerp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.laconical.player.ui.components.FullPlayer
import com.laconical.player.ui.components.LaconicalBottomNav
import com.laconical.player.ui.components.LaconicalTopBar
import com.laconical.player.ui.components.MiniPlayer
import com.laconical.player.ui.components.TrackListItem
import com.laconical.player.ui.components.QueueSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import kotlinx.coroutines.launch

/**
 * Main library screen.
 *
 * The bottom sheet hosts:
 *  - FullPlayer   (fades in as sheet expands)
 *  - MiniPlayer   (fades out; art slot left empty)
 *  - BottomNav    (fades out)
 *
 * The outer Box (above the BottomSheetScaffold) holds:
 *  - MorphingAlbumArt overlay — lerps mini→large→queue positions
 *  - MorphingTitle overlay    — lerps mini→large→queue positions
 *  - MorphingControls overlay — fades out as queue opens
 *  - QueueSheet               — full-screen, translated into view via Animatable
 *
 * This layering ensures the morph overlay always renders above QueueSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        hasPermission = isGranted
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val playingTrackDominantColor by viewModel.playingTrackDominantColor.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    val targetColor = if (playingTrackDominantColor != null) {
        val vibe = playingTrackDominantColor!!
        Color(
            red = (0.04f * 0.92f) + (vibe.red * 0.08f),
            green = (0.04f * 0.92f) + (vibe.green * 0.08f),
            blue = (0.05f * 0.92f) + (vibe.blue * 0.08f),
            alpha = 1f
        )
    } else {
        Color(0xFF0A0A0C)
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(1000),
        label = "BgColorAnim"
    )

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val queueAnimatable = remember { Animatable(0f) }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val bottomNavHeight = 64.dp
    val miniPlayerHeight = (75 + 12).dp
    val bottomInsets = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val peekHeight = miniPlayerHeight + bottomNavHeight + bottomInsets

    // Hoisted so the root-level morph overlay can access them
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var sheetRootYPx by remember { mutableFloatStateOf(0f) }
    var fullTitleTopPx by remember { mutableFloatStateOf(-1f) }
    var fullPrevCenterXPx by remember { mutableFloatStateOf(-1f) }
    var fullPrevCenterYPx by remember { mutableFloatStateOf(-1f) }
    var fullPlayCenterXPx by remember { mutableFloatStateOf(-1f) }
    var fullPlayCenterYPx by remember { mutableFloatStateOf(-1f) }
    var fullNextCenterXPx by remember { mutableFloatStateOf(-1f) }
    var fullNextCenterYPx by remember { mutableFloatStateOf(-1f) }

    // expandedFraction computed at this level so both sheetContent and outer Box can use it
    val maxOffset = if (containerHeightPx > 0f)
        containerHeightPx - with(density) { peekHeight.toPx() }
    else 1000f

    val currentOffset = try {
        scaffoldState.bottomSheetState.requireOffset()
    } catch (_: IllegalStateException) {
        maxOffset
    }

    val expandedFraction = if (maxOffset > 0f)
        (1f - (currentOffset / maxOffset)).coerceIn(0f, 1f)
    else 0f

    val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
            scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded

    // Collapse queue automatically if player collapses while queue is open
    LaunchedEffect(expandedFraction) {
        if (expandedFraction < 0.3f && queueAnimatable.value > 0f) {
            queueAnimatable.animateTo(0f, tween(200))
        }
    }

    // Back: queue first, then player
    BackHandler(enabled = isExpanded) {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = animatedColor,
            modifier = Modifier.fillMaxSize()
        ) {
            BottomSheetScaffold(
                modifier = Modifier.onSizeChanged { containerHeightPx = it.height.toFloat() },
                scaffoldState = scaffoldState,
                sheetPeekHeight = peekHeight,
                sheetContainerColor = Color.Transparent,
                sheetShadowElevation = 0.dp,
                sheetDragHandle = null,
                sheetContent = {
                    // miniAlpha / fullControlAlpha for sheet elements (MiniPlayer, BottomNav fade)
                    val miniAlpha = (1f - expandedFraction * 2f).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { sheetRootYPx = it.positionInRoot().y }
                    ) {
                        // ── Full Player ─────────────────────────────────────────
                        FullPlayer(
                            viewModel = viewModel,
                            expandedFraction = expandedFraction,
                            onCollapse = { scope.launch { scaffoldState.bottomSheetState.partialExpand() } },
                            onTitlePositioned = { fullTitleTopPx = it },
                            onPlayControlsPositioned = { px, py, lx, ly, nx, ny ->
                                if (px != Float.MIN_VALUE) { fullPrevCenterXPx = px; fullPrevCenterYPx = py }
                                if (lx != Float.MIN_VALUE) { fullPlayCenterXPx = lx; fullPlayCenterYPx = ly }
                                if (nx != Float.MIN_VALUE) { fullNextCenterXPx = nx; fullNextCenterYPx = ny }
                            },
                            onShowQueue = {
                                scope.launch {
                                    queueAnimatable.animateTo(
                                        1f,
                                        tween(600, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        )

                        // ── Mini Player (artwork slot is transparent) ────────────
                        if (expandedFraction < 0.99f) {
                            MiniPlayer(
                                viewModel = viewModel,
                                hideArt = true,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer { alpha = miniAlpha },
                                onClick = {
                                    if (hasPermission) {
                                        scope.launch { scaffoldState.bottomSheetState.expand() }
                                    }
                                }
                            )
                        }

                        // ── Bottom Navigation ────────────────────────────────────
                        if (hasPermission && expandedFraction < 0.99f) {
                            LaconicalBottomNav(
                                dynamicColor = playingTrackDominantColor,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = miniPlayerHeight)
                                    .graphicsLayer { alpha = miniAlpha }
                            )
                        }
                    }
                },
                containerColor = Color.Transparent,
                topBar = {
                    if (hasPermission) {
                        LaconicalTopBar(
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::updateSearchQuery
                        )
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    if (hasPermission) {
                        val tracks by viewModel.tracks.collectAsState()
                        LaunchedEffect(Unit) { viewModel.loadTracks() }
                        val isPlaybackActive by viewModel.isPlaying.collectAsState()

                        if (tracks.isEmpty()) {
                            Text(
                                text = "No tracks found",
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = peekHeight)
                            ) {
                                items(tracks) { track ->
                                    val isActiveTrack = currentTrack?.id == track.id
                                    TrackListItem(
                                        track = track,
                                        isActiveTrack = isActiveTrack,
                                        isPlaybackActive = isPlaybackActive,
                                        onClick = { viewModel.playTrack(track) }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Laconical",
                                fontFamily = FontFamily.Serif,
                                fontSize = 48.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Text("Permission required to access audio files", color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { launcher.launch(audioPermission) }) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }
        }

        // ── Root-level morph overlay ─────────────────────────────────────────────
        // Rendered above QueueSheet so album art + title stay visible during queue transition.
        if (currentTrack != null && expandedFraction > 0.01f) {
            val track = currentTrack!!
            val loadTarget = track.mediaUri
            val imageModel = remember(loadTarget) { AudioArtData(loadTarget) }
            val dominantColor by viewModel.playingTrackDominantColor.collectAsState()
            val themeColor = dominantColor ?: Color(0xFF1E1E1E)
            val currentAmplitude by viewModel.currentNormalizedAmplitude.collectAsState()
            val isPlaying by viewModel.isPlaying.collectAsState()
            val shapedAmplitude = currentAmplitude * currentAmplitude

            val queueProg = queueAnimatable.value

            // Pulse fades in as player expands, fades out as queue opens
            val pulseIntensity = ((expandedFraction - 0.7f) / 0.3f).coerceIn(0f, 1f) *
                    (1f - queueProg)
            val animatedPulse by animateFloatAsState(
                targetValue = 1f - (0.02f * pulseIntensity) + (shapedAmplitude * 0.04f * pulseIntensity),
                animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
                label = "MorphPulse"
            )

            // ── Position math ──────────────────────────────────────────────────
            // Mini position (root coords): sheet sits at sheetRootYPx from screen top
            val sheetRootYDp = with(density) { sheetRootYPx.toDp() }
            val miniArtSizeDp = 52.dp
            val miniArtLeftDp = 24.dp
            val miniArtTopDp  = sheetRootYDp + 11.5.dp  // root coords

            // Full position (root coords; sheetRootY ≈ 0 when expanded)
            val fullArtSizeDp = (screenWidthDp - 48.dp) * 0.95f
            val fullArtLeftDp = (screenWidthDp - fullArtSizeDp) / 2f
            val fullArtTopDp  = statusBarPadding + 16.dp + 48.dp + 64.dp  // root coords

            // Queue header position (root coords; matches QueueSheet header layout)
            val queueArtSizeDp = 56.dp
            val queueArtLeftDp = 20.dp
            val queueArtTopDp  = statusBarPadding + 20.dp

            // Phase 1: mini → large
            val playerArtSize = lerp(miniArtSizeDp.value, fullArtSizeDp.value, expandedFraction).dp
            val playerArtLeft = lerp(miniArtLeftDp.value, fullArtLeftDp.value, expandedFraction).dp
            val playerArtTop  = lerp(miniArtTopDp.value,  fullArtTopDp.value,  expandedFraction).dp

            // Phase 2: large → queue header
            val finalArtSize   = lerp(playerArtSize.value, queueArtSizeDp.value, queueProg).dp
            val finalArtLeft   = lerp(playerArtLeft.value, queueArtLeftDp.value, queueProg).dp
            val finalArtTop    = lerp(playerArtTop.value,  queueArtTopDp.value,  queueProg).dp
            val finalArtCorner = lerp(lerp(10f, 24f, expandedFraction), 12f, queueProg).dp

            Box(
                modifier = Modifier
                    .offset(x = finalArtLeft, y = finalArtTop)
                    .size(finalArtSize)
                    .graphicsLayer {
                        scaleX = animatedPulse
                        scaleY = animatedPulse
                    },
                contentAlignment = Alignment.Center
            ) {
                // Glow — fades in with expandedFraction, fades out as queue opens
                if (expandedFraction > 0.4f && queueProg < 0.8f) {
                    val glowFraction = ((expandedFraction - 0.4f) / 0.6f).coerceIn(0f, 1f) *
                            (1f - queueProg)
                    val easedGlow = glowFraction * glowFraction
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint().apply {
                                color = themeColor.toArgb()
                                maskFilter = BlurMaskFilter(
                                    70f + shapedAmplitude * 40f,
                                    BlurMaskFilter.Blur.NORMAL
                                )
                                alpha = (easedGlow * (25 + shapedAmplitude * 60))
                                    .toInt().coerceIn(0, 100)
                            }
                            canvas.nativeCanvas.drawRoundRect(
                                0f, 0f, size.width, size.height,
                                24.dp.toPx(), 24.dp.toPx(), paint
                            )
                        }
                    }
                }

                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(finalArtCorner))
                        .border(
                            0.5.dp,
                            Color.White.copy(alpha = lerp(lerp(0.02f, 0.08f, expandedFraction), 0.06f, queueProg)),
                            RoundedCornerShape(finalArtCorner)
                        ),
                    contentScale = ContentScale.Crop
                )
            }

            // ── Title morph overlay ────────────────────────────────────────────
            val miniTitleLeftDp = miniArtLeftDp + miniArtSizeDp + 12.dp
            val miniTitleTopDp  = miniArtTopDp + 2.dp  // root coords
            val fullTitleLeftDp = 48.dp
            val fullTitleTopDp = if (fullTitleTopPx >= 0f)
                with(density) { fullTitleTopPx.toDp() }  // positionInRoot — already root coords
            else
                fullArtTopDp + fullArtSizeDp + 200.dp

            // Queue title position (matches QueueSheet header Column layout)
            val queueTitleLeftDp = 88.dp  // 20dp start + 56dp art + 12dp spacer
            val queueTitleTopDp  = statusBarPadding + 26.dp  // center-ish in 56dp row

            val playerTitleLeft = lerp(miniTitleLeftDp.value, fullTitleLeftDp.value, expandedFraction).dp
            val playerTitleTop  = lerp(miniTitleTopDp.value,  fullTitleTopDp.value,  expandedFraction).dp
            val finalTitleLeft  = lerp(playerTitleLeft.value, queueTitleLeftDp.value, queueProg).dp
            val finalTitleTop   = lerp(playerTitleTop.value,  queueTitleTopDp.value,  queueProg).dp
            val finalTitleSize  = lerp(lerp(15f, 20f, expandedFraction), 16f, queueProg).sp

            val playerTitleMaxWidthDp = lerp(
                (screenWidthDp - miniTitleLeftDp - 170.dp).value,
                (screenWidthDp - fullTitleLeftDp - 86.dp).value,
                expandedFraction
            ).dp
            val queueTitleMaxWidthDp = screenWidthDp - 88.dp - 80.dp  // btn (48) + gap + right pad
            val finalTitleMaxWidthDp = lerp(
                playerTitleMaxWidthDp.value,
                queueTitleMaxWidthDp.value,
                queueProg
            ).dp

            Text(
                text = track.title,
                color = Color.White,
                fontSize = finalTitleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .offset(x = finalTitleLeft, y = finalTitleTop)
                    .widthIn(max = finalTitleMaxWidthDp)
            )

            // ── Morphing playback controls overlay ────────────────────────────
            // Fade out as queue opens (queue provides its own play button)
            val controlsAlpha = (1f - queueProg * 1.5f).coerceIn(0f, 1f)

            val miniCtrlCenterYDp = sheetRootYDp + 37.5.dp  // root coords
            val miniPrevCenterXDp = screenWidthDp - 144.dp
            val miniPlayCenterXDp = screenWidthDp - 96.dp
            val miniNextCenterXDp = screenWidthDp - 48.dp

            val fullPrevCenterXDp = if (fullPrevCenterXPx >= 0f) with(density) { fullPrevCenterXPx.toDp() } else miniPrevCenterXDp
            val fullPrevCenterYDp = if (fullPrevCenterYPx >= 0f) with(density) { fullPrevCenterYPx.toDp() } else miniCtrlCenterYDp
            val fullPlayCenterXDp = if (fullPlayCenterXPx >= 0f) with(density) { fullPlayCenterXPx.toDp() } else miniPlayCenterXDp
            val fullPlayCenterYDp = if (fullPlayCenterYPx >= 0f) with(density) { fullPlayCenterYPx.toDp() } else miniCtrlCenterYDp
            val fullNextCenterXDp = if (fullNextCenterXPx >= 0f) with(density) { fullNextCenterXPx.toDp() } else miniNextCenterXDp
            val fullNextCenterYDp = if (fullNextCenterYPx >= 0f) with(density) { fullNextCenterYPx.toDp() } else miniCtrlCenterYDp

            val prevCX = lerp(miniPrevCenterXDp.value, fullPrevCenterXDp.value, expandedFraction).dp
            val prevCY = lerp(miniCtrlCenterYDp.value, fullPrevCenterYDp.value, expandedFraction).dp
            val playCX = lerp(miniPlayCenterXDp.value, fullPlayCenterXDp.value, expandedFraction).dp
            val playCY = lerp(miniCtrlCenterYDp.value, fullPlayCenterYDp.value, expandedFraction).dp
            val nextCX = lerp(miniNextCenterXDp.value, fullNextCenterXDp.value, expandedFraction).dp
            val nextCY = lerp(miniCtrlCenterYDp.value, fullNextCenterYDp.value, expandedFraction).dp

            val prevNextIconSize = lerp(24f, 48f, expandedFraction).dp
            val playContainerSize = lerp(48f, 72f, expandedFraction).dp
            val playIconSize = lerp(36f, 42f, expandedFraction).dp
            val circleAlpha = expandedFraction

            val buttonBgColor = remember(themeColor) {
                val hsl = themeColor.toHsl()
                Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
            }
            val animatedBtnColor by animateColorAsState(buttonBgColor, tween(800), label = "MorphBtnColor")

            Box(
                modifier = Modifier
                    .offset(x = prevCX - prevNextIconSize / 2, y = prevCY - prevNextIconSize / 2)
                    .size(prevNextIconSize)
                    .graphicsLayer { alpha = controlsAlpha },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SkipPrevious, "Previous",
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { viewModel.skipToPrevious() }
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = playCX - playContainerSize / 2, y = playCY - playContainerSize / 2)
                    .size(playContainerSize)
                    .graphicsLayer { alpha = controlsAlpha }
                    .clip(CircleShape)
                    .background(animatedBtnColor.copy(alpha = circleAlpha))
                    .clickable { viewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(playIconSize)
                )
            }

            Box(
                modifier = Modifier
                    .offset(x = nextCX - prevNextIconSize / 2, y = nextCY - prevNextIconSize / 2)
                    .size(prevNextIconSize)
                    .graphicsLayer { alpha = controlsAlpha },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.SkipNext, "Next",
                    tint = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { viewModel.skipToNext() }
                )
            }
        }

        // ── Queue Sheet ───────────────────────────────────────────────────────
        // Rendered between the player and the morph overlay. The Animatable drives
        // translationY so the sheet slides in/out physically rather than fading.
        val queueProg = queueAnimatable.value
        if (queueProg > 0.001f) {
            val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
            QueueSheet(
                viewModel = viewModel,
                progress = queueProg,
                onDismiss = {
                    scope.launch {
                        queueAnimatable.animateTo(0f, tween(350, easing = FastOutLinearInEasing))
                    }
                },
                onDragDelta = { dy ->
                    val newProg = (queueProg - dy / screenH).coerceIn(0f, 1f)
                    scope.launch { queueAnimatable.snapTo(newProg) }
                },
                onDragEnd = { velocityY ->
                    scope.launch {
                        if (queueProg > 0.5f && velocityY < 250f) {
                            queueAnimatable.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
                        } else {
                            queueAnimatable.animateTo(0f, tween(350, easing = FastOutLinearInEasing))
                        }
                    }
                },
                modifier = Modifier.graphicsLayer {
                    translationY = (1f - queueProg) * screenH
                }
            )
        }
    } // end outer Box
}
