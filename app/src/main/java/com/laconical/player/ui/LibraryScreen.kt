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
import com.laconical.player.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// Symmetric open/close duration for the queue morph (ms). Material standard easing
// (FastOutSlowInEasing) is used in both directions so the motion feels the same in and out.
private const val QUEUE_ANIM_MS = 300

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
    // Sheet only needs to peek enough to show the mini player; nav bar lives outside the sheet.
    val sheetPeekHeight = miniPlayerHeight + bottomInsets
    val trackListBottomPadding = miniPlayerHeight + bottomNavHeight + bottomInsets

    // Hoisted so the root-level morph overlay can access them
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    // -1f = not yet measured. Valid measured value is any float >= 0f
    // (it reaches exactly 0f when the sheet is fully expanded to the top).
    var sheetRootYPx by remember { mutableFloatStateOf(-1f) }
    var fullTitleTopPx by remember { mutableFloatStateOf(-1f) }
    var fullArtistLeftPx by remember { mutableFloatStateOf(-1f) }
    var fullArtistTopPx by remember { mutableFloatStateOf(-1f) }
    var fullPrevCenterXPx by remember { mutableFloatStateOf(-1f) }
    var fullPrevCenterYPx by remember { mutableFloatStateOf(-1f) }
    var fullPlayCenterXPx by remember { mutableFloatStateOf(-1f) }
    var fullPlayCenterYPx by remember { mutableFloatStateOf(-1f) }
    var fullNextCenterXPx by remember { mutableFloatStateOf(-1f) }
    var fullNextCenterYPx by remember { mutableFloatStateOf(-1f) }

    // expandedFraction computed at this level so both sheetContent and outer Box can use it
    val maxOffset = if (containerHeightPx > 0f)
        containerHeightPx - with(density) { sheetPeekHeight.toPx() }
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
    val miniAlpha = (1f - expandedFraction * 2f).coerceIn(0f, 1f)

    // Collapse queue automatically if player collapses while queue is open
    LaunchedEffect(expandedFraction) {
        if (expandedFraction < 0.3f && queueAnimatable.value > 0f) {
            queueAnimatable.animateTo(0f, tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing))
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
                sheetPeekHeight = sheetPeekHeight,
                sheetContainerColor = Color.Transparent,
                sheetShadowElevation = 0.dp,
                sheetDragHandle = null,
                sheetContent = {
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
                            onArtistPositioned = { x, y -> fullArtistLeftPx = x; fullArtistTopPx = y },
                            onPlayControlsPositioned = { px, py, lx, ly, nx, ny ->
                                if (px != Float.MIN_VALUE) { fullPrevCenterXPx = px; fullPrevCenterYPx = py }
                                if (lx != Float.MIN_VALUE) { fullPlayCenterXPx = lx; fullPlayCenterYPx = ly }
                                if (nx != Float.MIN_VALUE) { fullNextCenterXPx = nx; fullNextCenterYPx = ny }
                            },
                            onShowQueue = {
                                scope.launch {
                                    queueAnimatable.animateTo(
                                        1f,
                                        tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        )

                        // ── Mini Player (artwork slot is transparent) ────────────
                        // When the morph overlay isn't ready to render (ghosts still
                        // measuring on first track), show the mini player's own art/controls
                        // so there's no blank frame.
                        if (expandedFraction < 0.99f) {
                            val overlayActive = currentTrack != null && sheetRootYPx >= 0f &&
                                fullTitleTopPx >= 0f && fullArtistTopPx >= 0f &&
                                fullPrevCenterYPx >= 0f && fullPlayCenterYPx >= 0f && fullNextCenterYPx >= 0f
                            MiniPlayer(
                                viewModel = viewModel,
                                hideArt = overlayActive,
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
                                contentPadding = PaddingValues(bottom = trackListBottomPadding)
                            ) {
                                items(tracks, key = { it.id }) { track ->
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

        // ── Bottom Navigation (fixed outside sheet so it doesn't ride up during drag) ──
        if (hasPermission && expandedFraction < 0.99f) {
            LaconicalBottomNav(
                dynamicColor = playingTrackDominantColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = miniAlpha }
            )
        }

        // ── Morph overlay + Queue Sheet ─────────────────────────────────────────
        // QueueMorphLayer reads queueAnimatable.value itself, scoping 60fps
        // recompositions to that small composable rather than all of LibraryScreen.
        //
        // Gate on every ghost position being measured. Before the overlay renders,
        // the sheet-relative math and the -1f fallback math disagree — if we let
        // the overlay animate with some positions still at -1f, the lerp visibly
        // snaps to the measured value mid-drag. FullPlayer is always composed so
        // its onGloballyPositioned callbacks fire within one frame of currentTrack
        // being set.
        val allGhostsReady = sheetRootYPx >= 0f &&
            fullTitleTopPx >= 0f && fullArtistTopPx >= 0f &&
            fullPrevCenterYPx >= 0f && fullPlayCenterYPx >= 0f && fullNextCenterYPx >= 0f
        if (currentTrack != null && allGhostsReady) {
            QueueMorphLayer(
                queueAnimatable = queueAnimatable,
                viewModel = viewModel,
                currentTrack = currentTrack!!,
                expandedFraction = expandedFraction,
                sheetRootYPx = sheetRootYPx,
                fullTitleTopPx = fullTitleTopPx,
                fullArtistLeftPx = fullArtistLeftPx,
                fullArtistTopPx = fullArtistTopPx,
                fullPrevCenterXPx = fullPrevCenterXPx,
                fullPrevCenterYPx = fullPrevCenterYPx,
                fullPlayCenterXPx = fullPlayCenterXPx,
                fullPlayCenterYPx = fullPlayCenterYPx,
                fullNextCenterXPx = fullNextCenterXPx,
                fullNextCenterYPx = fullNextCenterYPx,
                scope = scope,
            )
        }
    } // end outer Box
}

/**
 * Renders the queue sheet and the morph overlay (album art, title, artist, controls)
 * as a single isolated composable.
 *
 * Isolation is the key performance win: [queueAnimatable.value] is read HERE, not in
 * [LibraryScreen]. During a queue animation, only this small composable recomposes at
 * 60 fps — the LazyColumn track list and the rest of LibraryScreen stay idle.
 *
 * Z-order within the caller's Box:
 *  1. [QueueSheet]   — fades in + 80dp slide so its background fills the screen quickly
 *  2. Morph overlay  — drawn on top so art/title/play stay visible over the sheet background
 *
 * Swapping these (sheet last = on top) was the original bug: at queueProg=1 the opaque
 * sheet background covered the morph elements, leaving the header area empty.
 */
@Composable
private fun QueueMorphLayer(
    queueAnimatable: Animatable<Float, AnimationVector1D>,
    viewModel: MainViewModel,
    currentTrack: Track,
    expandedFraction: Float,
    sheetRootYPx: Float,
    fullTitleTopPx: Float,
    fullArtistLeftPx: Float,
    fullArtistTopPx: Float,
    fullPrevCenterXPx: Float,
    fullPrevCenterYPx: Float,
    fullPlayCenterXPx: Float,
    fullPlayCenterYPx: Float,
    fullNextCenterXPx: Float,
    fullNextCenterYPx: Float,
    scope: CoroutineScope,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenWidthDp = configuration.screenWidthDp.dp

    // Reading value HERE instead of in LibraryScreen is the performance fix.
    val queueProg = queueAnimatable.value

    // ── Queue Sheet (rendered first = below morph overlay) ───────────────────
    // Fade + small 80dp slide instead of a full-screen-height slide. This keeps
    // the sheet background behind the morph elements from the very start of the
    // transition, eliminating the empty-space-at-top gap.
    if (queueProg > 0.001f) {
        val screenH = with(density) { configuration.screenHeightDp.dp.toPx() }
        val slideDistance = with(density) { 80.dp.toPx() }
        QueueSheet(
            viewModel = viewModel,
            progress = queueProg,
            onDismiss = {
                scope.launch {
                    queueAnimatable.animateTo(0f, tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing))
                }
            },
            onDragDelta = { dy ->
                val newProg = (queueAnimatable.value - dy / screenH).coerceIn(0f, 1f)
                scope.launch { queueAnimatable.snapTo(newProg) }
            },
            onDragEnd = { velocityY ->
                scope.launch {
                    if (queueAnimatable.value > 0.5f && velocityY < 250f) {
                        queueAnimatable.animateTo(1f, tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing))
                    } else {
                        queueAnimatable.animateTo(0f, tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing))
                    }
                }
            },
            modifier = Modifier.graphicsLayer {
                translationY = (1f - queueProg) * slideDistance
                alpha = queueProg.coerceIn(0f, 1f)
            }
        )
    }

    // ── Morph overlay (rendered second = above QueueSheet) ───────────────────
    val loadTarget = currentTrack.mediaUri
    val imageModel = remember(loadTarget) { AudioArtData(loadTarget) }
    val dominantColor by viewModel.playingTrackDominantColor.collectAsState()
    val themeColor = dominantColor ?: Color(0xFF1E1E1E)
    val currentAmplitude by viewModel.currentNormalizedAmplitude.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val shapedAmplitude = currentAmplitude * currentAmplitude

    // Pulse ramps in over the final 5% of sheet expansion and is fully off while
    // the queue is open. The target has no constant offset — it stays at 1f when
    // amplitude is zero, so the spring never fires on a step discontinuity when
    // the sheet settles. The art only scales up with actual beat amplitude.
    val pulseIntensity = if (queueProg < 0.01f) {
        ((expandedFraction - 0.95f) / 0.05f).coerceIn(0f, 1f)
    } else 0f
    val animatedPulse by animateFloatAsState(
        targetValue = 1f + (shapedAmplitude * 0.04f * pulseIntensity),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
        label = "MorphPulse"
    )

    // ── Position math ──────────────────────────────────────────────────
    // Freeze the sheet root Y at the collapsed position so the mini lerp start-point
    // is stable throughout the animation. Using a live sheetRootYPx would make both
    // endpoints of the lerp move, producing a curved "hook" path instead of a line.
    val frozenMiniSheetRootYPx = remember { mutableFloatStateOf(-1f) }
    LaunchedEffect(sheetRootYPx, expandedFraction) {
        if (expandedFraction < 0.15f && sheetRootYPx >= 0f) {
            frozenMiniSheetRootYPx.floatValue = sheetRootYPx
        }
    }
    val miniSheetRootYPx = if (frozenMiniSheetRootYPx.floatValue >= 0f)
        frozenMiniSheetRootYPx.floatValue
    else
        sheetRootYPx
    val miniSheetRootYDp = with(density) { miniSheetRootYPx.toDp() }
    val miniArtSizeDp = 52.dp
    val miniArtLeftDp = 24.dp
    val miniArtTopDp  = miniSheetRootYDp + 11.5.dp

    val fullArtSizeDp = (screenWidthDp - 48.dp) * 0.95f
    val fullArtLeftDp = (screenWidthDp - fullArtSizeDp) / 2f
    val fullArtTopDp  = statusBarPadding + 16.dp + 48.dp + 64.dp

    val queueArtSizeDp = 56.dp
    val queueArtLeftDp = 20.dp
    val queueArtTopDp  = statusBarPadding + 20.dp

    val playerArtSize = lerp(miniArtSizeDp.value, fullArtSizeDp.value, expandedFraction).dp
    val playerArtLeft = lerp(miniArtLeftDp.value, fullArtLeftDp.value, expandedFraction).dp
    val playerArtTop  = lerp(miniArtTopDp.value,  fullArtTopDp.value,  expandedFraction).dp

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
        if (expandedFraction > 0.4f && queueProg < 0.8f) {
            val glowFraction = ((expandedFraction - 0.4f) / 0.6f).coerceIn(0f, 1f) * (1f - queueProg)
            val easedGlow = glowFraction * glowFraction
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = themeColor.toArgb()
                        maskFilter = BlurMaskFilter(
                            70f + shapedAmplitude * 40f,
                            BlurMaskFilter.Blur.NORMAL
                        )
                        alpha = (easedGlow * (25 + shapedAmplitude * 60)).toInt().coerceIn(0, 100)
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
    val miniTitleTopDp  = miniArtTopDp + 2.dp
    val fullTitleLeftDp = 48.dp
    // Full player Y positions: subtract sheetRootYPx to get sheet-relative coordinates.
    // positionInRoot() moves as the sheet scrolls, but (absY - sheetRootY) is constant
    // regardless of sheet position, giving a stable lerp target.
    val fullTitleTopDp  = if (fullTitleTopPx >= 0f)
        with(density) { (fullTitleTopPx - sheetRootYPx).toDp() }
    else
        fullArtTopDp + fullArtSizeDp + 30.dp

    val queueTitleLeftDp = 88.dp
    val queueTitleTopDp  = statusBarPadding + 26.dp

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
    val queueTitleMaxWidthDp  = screenWidthDp - 88.dp - 80.dp
    val finalTitleMaxWidthDp  = lerp(playerTitleMaxWidthDp.value, queueTitleMaxWidthDp.value, queueProg).dp

    Text(
        text = currentTrack.title,
        color = Color.White,
        fontSize = finalTitleSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(x = finalTitleLeft, y = finalTitleTop)
            .widthIn(max = finalTitleMaxWidthDp)
    )

    // ── Artist morph overlay ───────────────────────────────────────────
    val miniArtistLeftDp = miniArtLeftDp + miniArtSizeDp + 12.dp
    val miniArtistTopDp  = miniArtTopDp + 20.dp
    val fullArtistLeftDp = if (fullArtistLeftPx >= 0f) with(density) { fullArtistLeftPx.toDp() } else 48.dp
    val fullArtistTopDp  = if (fullArtistTopPx >= 0f) with(density) { (fullArtistTopPx - sheetRootYPx).toDp() } else fullTitleTopDp + 20.dp
    val queueArtistLeftDp = 88.dp
    val queueArtistTopDp  = statusBarPadding + 46.dp

    val playerArtistLeft = lerp(miniArtistLeftDp.value, fullArtistLeftDp.value, expandedFraction).dp
    val playerArtistTop  = lerp(miniArtistTopDp.value,  fullArtistTopDp.value,  expandedFraction).dp
    val finalArtistLeft  = lerp(playerArtistLeft.value, queueArtistLeftDp.value, queueProg).dp
    val finalArtistTop   = lerp(playerArtistTop.value,  queueArtistTopDp.value,  queueProg).dp
    val finalArtistSize  = lerp(lerp(13f, 14f, expandedFraction), 13f, queueProg).sp

    Text(
        text = currentTrack.artist,
        color = Color(0xFFBBBBBB),
        fontSize = finalArtistSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier
            .offset(x = finalArtistLeft, y = finalArtistTop)
            .widthIn(max = finalTitleMaxWidthDp)
    )

    // ── Morphing playback controls overlay ────────────────────────────
    val miniCtrlCenterYDp = miniSheetRootYDp + 37.5.dp
    val miniPrevCenterXDp = screenWidthDp - 144.dp
    val miniPlayCenterXDp = screenWidthDp - 96.dp
    val miniNextCenterXDp = screenWidthDp - 48.dp

    val fullPrevCenterXDp = if (fullPrevCenterXPx >= 0f) with(density) { fullPrevCenterXPx.toDp() } else miniPrevCenterXDp
    val fullPrevCenterYDp = if (fullPrevCenterYPx >= 0f) with(density) { (fullPrevCenterYPx - sheetRootYPx).toDp() } else miniCtrlCenterYDp
    val fullPlayCenterXDp = if (fullPlayCenterXPx >= 0f) with(density) { fullPlayCenterXPx.toDp() } else miniPlayCenterXDp
    val fullPlayCenterYDp = if (fullPlayCenterYPx >= 0f) with(density) { (fullPlayCenterYPx - sheetRootYPx).toDp() } else miniCtrlCenterYDp
    val fullNextCenterXDp = if (fullNextCenterXPx >= 0f) with(density) { fullNextCenterXPx.toDp() } else miniNextCenterXDp
    val fullNextCenterYDp = if (fullNextCenterYPx >= 0f) with(density) { (fullNextCenterYPx - sheetRootYPx).toDp() } else miniCtrlCenterYDp

    val queuePlayCenterXDp = screenWidthDp - 20.dp - 24.dp
    val queuePlayCenterYDp = statusBarPadding + 20.dp + 28.dp

    val p1PrevCX = lerp(miniPrevCenterXDp.value, fullPrevCenterXDp.value, expandedFraction)
    val p1PrevCY = lerp(miniCtrlCenterYDp.value, fullPrevCenterYDp.value, expandedFraction)
    val p1PlayCX = lerp(miniPlayCenterXDp.value, fullPlayCenterXDp.value, expandedFraction)
    val p1PlayCY = lerp(miniCtrlCenterYDp.value, fullPlayCenterYDp.value, expandedFraction)
    val p1NextCX = lerp(miniNextCenterXDp.value, fullNextCenterXDp.value, expandedFraction)
    val p1NextCY = lerp(miniCtrlCenterYDp.value, fullNextCenterYDp.value, expandedFraction)

    val playCX = lerp(p1PlayCX, queuePlayCenterXDp.value, queueProg).dp
    val playCY = lerp(p1PlayCY, queuePlayCenterYDp.value, queueProg).dp
    val prevCX = lerp(p1PrevCX, queuePlayCenterXDp.value, queueProg).dp
    val prevCY = lerp(p1PrevCY, queuePlayCenterYDp.value, queueProg).dp
    val nextCX = lerp(p1NextCX, queuePlayCenterXDp.value, queueProg).dp
    val nextCY = lerp(p1NextCY, queuePlayCenterYDp.value, queueProg).dp

    val prevNextIconSize  = lerp(24f, 48f, expandedFraction).dp
    val playContainerSize = lerp(lerp(48f, 72f, expandedFraction), 48f, queueProg).dp
    val playIconSize      = lerp(lerp(36f, 42f, expandedFraction), 36f, queueProg).dp
    val circleAlpha       = expandedFraction * (1f - queueProg)
    val prevNextAlpha     = (1f - queueProg * 1.4f).coerceIn(0f, 1f)

    val buttonBgColor = remember(themeColor) {
        val hsl = themeColor.toHsl()
        Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
    }
    val animatedBtnColor by animateColorAsState(buttonBgColor, tween(800), label = "MorphBtnColor")

    // Prev/next are only rendered while visible — graphicsLayer { alpha = 0f } keeps hit-testing
    // active, so an invisible next-button sitting on top of the play button would steal taps.
    if (prevNextAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .offset(x = prevCX - prevNextIconSize / 2, y = prevCY - prevNextIconSize / 2)
                .size(prevNextIconSize)
                .graphicsLayer { alpha = prevNextAlpha },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SkipPrevious, "Previous",
                tint = Color.White,
                modifier = Modifier.fillMaxSize().clickable { viewModel.skipToPrevious() }
            )
        }
    }

    Box(
        modifier = Modifier
            .offset(x = playCX - playContainerSize / 2, y = playCY - playContainerSize / 2)
            .size(playContainerSize)
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

    if (prevNextAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .offset(x = nextCX - prevNextIconSize / 2, y = nextCY - prevNextIconSize / 2)
                .size(prevNextIconSize)
                .graphicsLayer { alpha = prevNextAlpha },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.SkipNext, "Next",
                tint = Color.White,
                modifier = Modifier.fillMaxSize().clickable { viewModel.skipToNext() }
            )
        }
    }
}
