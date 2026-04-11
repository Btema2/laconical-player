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
 *  - MorphingAlbumArt overlay — a SINGLE image that lerps from the
 *    mini thumbnail position to the full-player art position based on
 *    `expandedFraction`. This is the sole animated element.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // READ_MEDIA_AUDIO was added in API 33 (TIRAMISU). On API 32 and below we need READ_EXTERNAL_STORAGE.
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

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val bottomNavHeight = 64.dp
    val miniPlayerHeight = (75 + 12).dp
    val bottomInsets = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val peekHeight = miniPlayerHeight + bottomNavHeight + bottomInsets

    var containerHeightPx by remember { mutableFloatStateOf(0f) }

    val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
            scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded

    BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

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
                val maxOffset = if (containerHeightPx > 0f)
                    containerHeightPx - with(density) { peekHeight.toPx() }
                else 1000f

                // requireOffset() throws IllegalStateException before the sheet is first measured.
                // Only catch that specific type — all other exceptions should propagate.
                val currentOffset = try {
                    scaffoldState.bottomSheetState.requireOffset()
                } catch (_: IllegalStateException) {
                    maxOffset
                }

                val expandedFraction = if (maxOffset > 0f)
                    (1f - (currentOffset / maxOffset)).coerceIn(0f, 1f)
                else 0f

                // ── Position constants for the morphing overlay ──────────────
                // Mini art: inside MiniPlayer Box (top of sheet), padding 12dp outer + 12dp row = 24dp from left
                // vertically centered in 75dp container → top = (75-52)/2 = 11.5dp from sheet top
                val miniArtSizeDp   = 52.dp
                val miniArtLeftDp   = 24.dp    // 12dp outer + 12dp row horizontal padding
                val miniArtTopDp    = 11.5.dp  // (75-52)/2

                // Full art: matches FullPlayer's actual layout.
                // FullPlayer Column has padding(horizontal=24dp) so column width = screenWidth-48dp.
                // fillMaxWidth(0.95f) inside that column → art width = (screenWidth-48dp)*0.95f.
                // Top: statusBarPadding + 16dp (column top pad) + 48dp (TopBar) + 64dp (Spacer).
                val fullArtSizeDp   = (screenWidthDp - 48.dp) * 0.95f
                val fullArtLeftDp   = (screenWidthDp - fullArtSizeDp) / 2f
                val fullArtTopDp    = statusBarPadding + 16.dp + 48.dp + 64.dp

                // Lerped values
                val morphSizeDp     = lerp(miniArtSizeDp.value,    fullArtSizeDp.value,    expandedFraction).dp
                val morphLeftDp     = lerp(miniArtLeftDp.value,     fullArtLeftDp.value,    expandedFraction).dp
                val morphTopDp      = lerp(miniArtTopDp.value,      fullArtTopDp.value,     expandedFraction).dp
                val morphCornerDp   = lerp(10f,                     24f,                    expandedFraction).dp

                // Mini alpha: fully visible at 0, gone by 0.5
                val miniAlpha        = (1f - expandedFraction * 2f).coerceIn(0f, 1f)
                // Full player controls visible after 0.33, fully in at 0.67
                val fullControlAlpha = ((expandedFraction - 0.33f) / 0.34f).coerceIn(0f, 1f)

                // Track the sheet content Box's own root-Y so we can convert
                // boundsInRoot() from ghost elements to sheet-local coordinates.
                var sheetRootYPx by remember { mutableFloatStateOf(0f) }

                Box(modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { sheetRootYPx = it.positionInRoot().y }
                ) {

                    // Measured positions of ghost elements in FullPlayer (root coords = sheet-local when fully expanded)
                    var fullTitleTopPx by remember { mutableFloatStateOf(-1f) }
                    var fullPrevCenterXPx  by remember { mutableFloatStateOf(-1f) }
                    var fullPrevCenterYPx  by remember { mutableFloatStateOf(-1f) }
                    var fullPlayCenterXPx  by remember { mutableFloatStateOf(-1f) }
                    var fullPlayCenterYPx  by remember { mutableFloatStateOf(-1f) }
                    var fullNextCenterXPx  by remember { mutableFloatStateOf(-1f) }
                    var fullNextCenterYPx  by remember { mutableFloatStateOf(-1f) }

                    // ── Full Player (background + controls) ─────────────────
                    FullPlayer(
                        viewModel = viewModel,
                        expandedFraction = expandedFraction,
                        onCollapse = { scope.launch { scaffoldState.bottomSheetState.partialExpand() } },
                        onTitlePositioned = { fullTitleTopPx = it },
                        onPlayControlsPositioned = { px, py, lx, ly, nx, ny ->
                            if (px != Float.MIN_VALUE) { fullPrevCenterXPx = px; fullPrevCenterYPx = py }
                            if (lx != Float.MIN_VALUE) { fullPlayCenterXPx = lx; fullPlayCenterYPx = ly }
                            if (nx != Float.MIN_VALUE) { fullNextCenterXPx = nx; fullNextCenterYPx = ny }
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

                    // ── Morphing Album Art Overlay ───────────────────────────
                    //    This single image physically moves between both positions.
                    if (currentTrack != null) {
                        val track = currentTrack!!
                        val loadTarget = track.mediaUri
                        val imageModel = remember(loadTarget) { AudioArtData(loadTarget) }
                        val dominantColor by viewModel.playingTrackDominantColor.collectAsState()
                        val themeColor = dominantColor ?: Color(0xFF1E1E1E)
                        val currentAmplitude by viewModel.currentNormalizedAmplitude.collectAsState()
                        val isPlaying by viewModel.isPlaying.collectAsState()
                        val shapedAmplitude = currentAmplitude * currentAmplitude
                        // Pulse gradually ramps in as the overlay approaches full-player size
                        val pulseIntensity = ((expandedFraction - 0.7f) / 0.3f).coerceIn(0f, 1f)
                        val animatedPulse by animateFloatAsState(
                            targetValue = 1f - (0.02f * pulseIntensity) + (shapedAmplitude * 0.04f * pulseIntensity),
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 280f),
                            label = "MorphPulse"
                        )

                        // The morph overlay stays visible at all times — it IS the album art.
                        // No fade-out / handoff; this eliminates the blink completely.
                        Box(
                            modifier = Modifier
                                .offset(x = morphLeftDp, y = morphTopDp)
                                .size(morphSizeDp)
                                .graphicsLayer {
                                    scaleX = animatedPulse
                                    scaleY = animatedPulse
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Glow — smoothly emerges with an ease-in curve
                            if (expandedFraction > 0.4f) {
                                val glowFraction = ((expandedFraction - 0.4f) / 0.6f).coerceIn(0f, 1f)
                                val easedGlow = glowFraction * glowFraction // ease-in for subtle onset
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
                                    .clip(RoundedCornerShape(morphCornerDp))
                                    .border(0.5.dp, Color.White.copy(alpha = lerp(0.02f, 0.08f, expandedFraction)), RoundedCornerShape(morphCornerDp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // ── Morphing Title Overlay ───────────────────────────
                        //    Moves from mini position to full-player position
                        val miniTitleLeftDp  = miniArtLeftDp + miniArtSizeDp + 12.dp
                        // Align with text baseline inside the Column
                        val miniTitleTopDp   = miniArtTopDp + 2.dp
                        // FullPlayer Column has padding(horizontal=24dp) + Row has padding(horizontal=24dp)
                        // so title text always starts at 48dp from screen left — matches artist text.
                        val fullTitleLeftDp  = 48.dp
                        // Use the measured ghost-title Y for a pixel-perfect landing position.
                        // Subtract sheetRootYPx to convert root coords → sheet-local coords.
                        val fullTitleTopDp = if (fullTitleTopPx >= 0f)
                            with(density) { (fullTitleTopPx - sheetRootYPx).toDp() }
                        else
                            fullArtTopDp + fullArtSizeDp + 200.dp // rough fallback, never visible

                        val titleLeftDp  = lerp(miniTitleLeftDp.value,  fullTitleLeftDp.value,  expandedFraction).dp
                        val titleTopDp   = lerp(miniTitleTopDp.value,   fullTitleTopDp.value,   expandedFraction).dp
                        val titleSizeSp  = lerp(15f, 20f, expandedFraction).sp

                        // The overlay IS the title — no fade-out, same approach as the thumbnail.
                        // FullPlayer's title is a permanent invisible ghost (alpha=0 layout spacer).
                        val titleOverlayAlpha = 1f
                        // Mini: right limit = before the 3 control buttons (~140dp from right)
                        // Full: right limit = before the heart icon (56dp from right)
                        val titleMaxWidthDp = lerp(
                            (screenWidthDp - miniTitleLeftDp - 170.dp).value,
                            (screenWidthDp - fullTitleLeftDp - 86.dp).value,
                            expandedFraction
                        ).dp

                        Text(
                            text = track.title,
                            color = Color.White.copy(alpha = titleOverlayAlpha),
                            fontSize = titleSizeSp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .offset(x = titleLeftDp, y = titleTopDp)
                                .widthIn(max = titleMaxWidthDp)
                        )

                        // ── Morphing Playback Controls Overlay ──────────────
                        // Mini button centers (sheet-local, static geometry):
                        //   MiniPlayer outer padding H=12, row padding H=12 → right edge at screenWidth-24dp
                        //   3 × 48dp buttons (IconButton default) right-aligned
                        //   Center Y = 75dp/2 = 37.5dp
                        val miniCtrlCenterYDp = 37.5.dp
                        val miniPrevCenterXDp = screenWidthDp - 144.dp  // 3rd from right: -24 - 120 = -144
                        val miniPlayCenterXDp = screenWidthDp - 96.dp   // 2nd from right: -24 - 72 = -96
                        val miniNextCenterXDp = screenWidthDp - 48.dp   // 1st from right: -24 - 24 = -48

                        // Full button centers (sheet-local, from ghost onGloballyPositioned):
                        val fullPrevCenterXDp = if (fullPrevCenterXPx >= 0f) with(density) { fullPrevCenterXPx.toDp() } else miniPrevCenterXDp
                        val fullPrevCenterYDp = if (fullPrevCenterYPx >= 0f) with(density) { (fullPrevCenterYPx - sheetRootYPx).toDp() } else miniCtrlCenterYDp
                        val fullPlayCenterXDp = if (fullPlayCenterXPx >= 0f) with(density) { fullPlayCenterXPx.toDp() } else miniPlayCenterXDp
                        val fullPlayCenterYDp = if (fullPlayCenterYPx >= 0f) with(density) { (fullPlayCenterYPx - sheetRootYPx).toDp() } else miniCtrlCenterYDp
                        val fullNextCenterXDp = if (fullNextCenterXPx >= 0f) with(density) { fullNextCenterXPx.toDp() } else miniNextCenterXDp
                        val fullNextCenterYDp = if (fullNextCenterYPx >= 0f) with(density) { (fullNextCenterYPx - sheetRootYPx).toDp() } else miniCtrlCenterYDp

                        // Lerped centers
                        val prevCX = lerp(miniPrevCenterXDp.value, fullPrevCenterXDp.value, expandedFraction).dp
                        val prevCY = lerp(miniCtrlCenterYDp.value, fullPrevCenterYDp.value, expandedFraction).dp
                        val playCX = lerp(miniPlayCenterXDp.value, fullPlayCenterXDp.value, expandedFraction).dp
                        val playCY = lerp(miniCtrlCenterYDp.value, fullPlayCenterYDp.value, expandedFraction).dp
                        val nextCX = lerp(miniNextCenterXDp.value, fullNextCenterXDp.value, expandedFraction).dp
                        val nextCY = lerp(miniCtrlCenterYDp.value, fullNextCenterYDp.value, expandedFraction).dp

                        // Sizes: prev/next icon 24→48dp, play container 48→72dp, icon 36→42dp
                        val prevNextIconSize = lerp(24f, 48f, expandedFraction).dp
                        val playContainerSize = lerp(48f, 72f, expandedFraction).dp
                        val playIconSize = lerp(36f, 42f, expandedFraction).dp

                        // Circle background alpha: 0 at mini, 1 at full
                        val circleAlpha = expandedFraction
                        // themeColor and isPlaying already declared above in this block
                        val buttonBgColor = remember(themeColor) {
                            val hsl = themeColor.toHsl()
                            Color.hsl(hue = hsl[0] * 360f, saturation = hsl[1].coerceIn(0.2f, 0.5f), lightness = 0.4f)
                        }
                        val animatedBtnColor by animateColorAsState(buttonBgColor, tween(800), label = "MorphBtnColor")

                        // ── Prev Button ──
                        Box(
                            modifier = Modifier
                                .offset(x = prevCX - prevNextIconSize / 2, y = prevCY - prevNextIconSize / 2)
                                .size(prevNextIconSize),
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

                        // ── Play/Pause Button ──
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

                        // ── Next Button ──
                        Box(
                            modifier = Modifier
                                .offset(x = nextCX - prevNextIconSize / 2, y = nextCY - prevNextIconSize / 2)
                                .size(prevNextIconSize),
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
}
