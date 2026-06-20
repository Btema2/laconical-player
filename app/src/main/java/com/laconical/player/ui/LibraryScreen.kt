package com.laconical.player.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.BlurMaskFilter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import androidx.compose.material.icons.rounded.MusicNote
import com.laconical.player.ui.components.FullPlayer
import com.laconical.player.ui.components.staggeredEntrance
import com.laconical.player.ui.components.LaconicalBottomNav
import com.laconical.player.ui.components.LaconicalTopBar
import com.laconical.player.ui.components.MiniPlayer
import com.laconical.player.ui.components.TrackListItem
import com.laconical.player.ui.components.CreatePlaylistDialog
import com.laconical.player.ui.components.QueueSheet
import com.laconical.player.ui.components.FadingMarqueeText
import com.laconical.player.ui.components.TrackMenuOverlay
import com.laconical.player.ui.components.PlaylistMenuOverlay
import com.laconical.player.ui.components.PlaylistBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.ui.geometry.Offset
import com.laconical.player.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.laconical.player.ui.navigation.navEnterTransition
import com.laconical.player.ui.navigation.navExitTransition
import com.laconical.player.ui.navigation.navPopEnterTransition
import com.laconical.player.ui.navigation.navPopExitTransition
import com.laconical.player.ui.navigation.NavRoute
import com.laconical.player.ui.screens.AlbumDetailScreen
import com.laconical.player.ui.screens.AlbumsScreen
import com.laconical.player.ui.screens.ArtistDetailScreen
import com.laconical.player.ui.screens.ArtistsScreen
import com.laconical.player.ui.screens.FavoritesScreen
import com.laconical.player.ui.screens.PlaylistDetailScreen
import com.laconical.player.ui.screens.PlaylistsScreen
import com.laconical.player.ui.screens.SearchResultsPanel
import com.laconical.player.core.data.db.entity.Playlist
import com.laconical.player.ui.LocalAppBackground
import com.laconical.player.ui.LocalAppSurface

// Symmetric open/close duration for the queue morph (ms). Material standard easing
// (FastOutSlowInEasing) is used in both directions so the motion feels the same in and out.
private const val QUEUE_ANIM_MS = 300
private val sortOrders = SortOrder.entries.toTypedArray()

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
    viewModel: MainViewModel = hiltViewModel(),
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
        if (isGranted) viewModel.loadTracks()
    }

    val playingTrackDominantColor by viewModel.playingTrackDominantColor.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchedAlbums by viewModel.searchedAlbums.collectAsState()
    val searchedArtists by viewModel.searchedArtists.collectAsState()
    val searchedPlaylists by viewModel.searchedPlaylists.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val isPlaybackActive by viewModel.isPlaying.collectAsState()
    val isLoadingTracks by viewModel.isLoadingTracks.collectAsState()

    val targetBgColor = if (playingTrackDominantColor != null) {
        val d = playingTrackDominantColor!!
        Color(
            red   = 0.0784f * 0.93f + d.red   * 0.07f,
            green = 0.0745f * 0.93f + d.green * 0.07f,
            blue  = 0.0745f * 0.93f + d.blue  * 0.07f,
            alpha = 1f
        )
    } else Color(0xFF141313)

    val targetSurfaceColor = if (playingTrackDominantColor != null) {
        val d = playingTrackDominantColor!!
        Color(
            red   = 0.1294f * 0.94f + d.red   * 0.06f,
            green = 0.1294f * 0.94f + d.green * 0.06f,
            blue  = 0.1294f * 0.94f + d.blue  * 0.06f,
            alpha = 1f
        )
    } else Color(0xFF212121)

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(1000),
        label = "BgColorAnim"
    )

    val animatedSurfaceColor by animateColorAsState(
        targetValue = targetSurfaceColor,
        animationSpec = tween(1000),
        label = "SurfaceColorAnim"
    )

    val scope = rememberCoroutineScope()
    var pendingNewPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    var showCreateForPicker by remember { mutableStateOf(false) }
    var newPlaylistOriginOffset by remember { mutableStateOf(Offset.Zero) }
    var showCreateFromPlaylistsTab by remember { mutableStateOf(false) }
    var playlistToastData by remember { mutableStateOf<Pair<String, String>?>(null) }
    val playlists by viewModel.playlists.collectAsState()
    val playlistArtTracks by viewModel.playlistArtTracks.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    // Context menu state — hoisted here so overlay renders above all other layers
    var contextMenuTrack by remember { mutableStateOf<Track?>(null) }
    var contextMenuArtOffset by remember { mutableStateOf(Offset.Zero) }
    var contextMenuArtSize by remember { mutableFloatStateOf(0f) }
    var isMenuFromFullPlayer by remember { mutableStateOf(false) }
    var contextMenuPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var contextMenuPlaylistArtOffset by remember { mutableStateOf(Offset.Zero) }
    var contextMenuPlaylistArtSize by remember { mutableFloatStateOf(0f) }
    var showRenamePlaylist by remember { mutableStateOf(false) }
    var showDeletePlaylist by remember { mutableStateOf(false) }
    val navController = rememberNavController()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val queueAnimatable = remember { Animatable(0f) }
    var isSearchOpen by remember { mutableStateOf(false) }
    val contentFadeProgress = remember { Animatable(0f) }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            contentFadeProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        } else {
            contentFadeProgress.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
        }
    }

    var isTransitioning by remember { mutableStateOf(false) }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collectLatest {
            isTransitioning = true
            delay(250L)
            isTransitioning = false
        }
    }

    val rawRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        ?: NavRoute.TRACKS

    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val bottomNavHeight = 64.dp
    val miniPlayerHeight = (75 + 12).dp
    val bottomInsets = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // logicalPeekHeight drives maxOffset (must stay stable during IME animation).
    // sheetPeekHeight is the animated value passed to BottomSheetScaffold.
    val logicalPeekHeight = if (currentTrack != null)
        miniPlayerHeight + bottomNavHeight + bottomInsets
    else
        0.dp
    val sheetPeekHeight by animateDpAsState(
        targetValue = if (isSearchOpen && imeVisible) 0.dp else logicalPeekHeight,
        animationSpec = tween(200),
        label = "SheetPeekHeight"
    )
    // Always leave room for the bottom nav bar even when there is no mini player.
    val trackListBottomPadding = if (currentTrack != null)
        miniPlayerHeight + bottomNavHeight + bottomInsets
    else
        bottomNavHeight + bottomInsets

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
    var fullArtTopPx  by remember { mutableFloatStateOf(-1f) }
    var fullArtLeftPx by remember { mutableFloatStateOf(-1f) }
    var fullArtSizePx by remember { mutableFloatStateOf(-1f) }

    // expandedFraction computed at this level so both sheetContent and outer Box can use it
    val maxOffset = if (containerHeightPx > 0f)
        containerHeightPx - with(density) { logicalPeekHeight.toPx() }
    else 1000f

    // requireOffset() throws (offset == NaN) until AnchoredDraggable's anchors are
    // initialised, and can throw transiently around layout in newer Compose foundation.
    // The old fallback returned maxOffset → expandedFraction 0 = fully collapsed, so any
    // transient throw mid-expand snapped the morph shut for a single frame and produced
    // the visible "jumps back and forth". Cache the last valid offset in a NON-snapshot
    // holder (a plain FloatArray — writing it during composition must not trigger another
    // recomposition) and reuse it on a throw. Only before the very first valid reading do
    // we fall back to a settled value derived from currentValue.
    val offsetCache = remember { floatArrayOf(Float.NaN) }
    val rawOffset = try {
        scaffoldState.bottomSheetState.requireOffset()
    } catch (_: IllegalStateException) {
        Float.NaN
    }
    val currentOffset = when {
        !rawOffset.isNaN() -> { offsetCache[0] = rawOffset; rawOffset }
        !offsetCache[0].isNaN() -> offsetCache[0]
        scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded -> 0f
        else -> maxOffset
    }

    val rawExpandedFraction = if (maxOffset > 0f)
        (1f - (currentOffset / maxOffset)).coerceIn(0f, 1f)
    else 0f

    // ── Monotonic morph driver ──────────────────────────────────────────────
    // expandedFraction is the ONLY live input to the morph overlay (mini-side coords are
    // frozen anchors, maxOffset is constant), so any per-frame non-monotonicity in the raw
    // sheet offset shows up directly as the morph "moving back then continuing".
    //
    // A FINGER DRAG reads the offset smoothly and 1:1 — consecutive frames barely differ —
    // so it looks perfect and must stay untouched. But a TAP/FLING runs a settle animation
    // whose offset can step BACKWARD for a single frame near the endpoint: either a genuine
    // spring settle wobble, or a transient requireOffset() throw falling back to the 1-frame-
    // stale offsetCache (a stale offset during fast motion = a visibly wrong fraction). It is
    // visible only on fast/animated transitions, never on a slow drag.
    //
    // Fix: while the sheet is ANIMATING (not being dragged), lock the driver so it can only
    // move TOWARD the animation's target endpoint → the morph path is strictly monotonic
    // (linear, no back-step). During a drag we pass the raw value through so finger reversals
    // stay live. isAnimationRunning (material3 1.4+) is the only signal that cleanly separates
    // a settle animation from an active drag — currentValue != targetValue is also true mid-
    // drag (targetValue tracks the predicted anchor), so it would wrongly freeze finger drags.
    val bottomSheet = scaffoldState.bottomSheetState
    val lastFraction = remember { floatArrayOf(Float.NaN) }
    val expandedFraction = if (bottomSheet.isAnimationRunning && !lastFraction[0].isNaN()) {
        if (bottomSheet.targetValue == SheetValue.Expanded)
            rawExpandedFraction.coerceAtLeast(lastFraction[0]) // expanding → never step back down
        else
            rawExpandedFraction.coerceAtMost(lastFraction[0])  // collapsing → never step back up
    } else {
        rawExpandedFraction
    }
    // Plain (non-snapshot) holder — written during composition without triggering another
    // recomposition, exactly like offsetCache above.
    lastFraction[0] = expandedFraction

    // ── Frozen morph anchors ────────────────────────────────────────────────
    // The mini→full morph lerps mini-side screen coords against full-side targets expressed
    // sheet-relative as (fullXxxPx - sheetRootYPx). That difference is INVARIANT to the
    // sheet's offset — the full player's internal layout never moves relative to the sheet
    // root — so it only needs measuring once. The morph used to recompute it every frame
    // from two *live* onGloballyPositioned values. Newer Compose backs those callbacks with
    // a throttled/debounced rect tracker (see CLAUDE.md → Compose dependency notes), so the
    // two values desync frame-to-frame and the "constant" difference wobbles → stutter; the
    // live-value readiness gate also flickered → the disappear/reappear flash.
    //
    // Fix: snapshot every ghost position AND the sheet root ATOMICALLY while the sheet is at
    // rest (expandedFraction ≈ 0, track present), then hold it frozen. One settled layout
    // pass = mutually-consistent values. The morph then depends only on the smooth
    // expandedFraction driver, never on per-frame callback lockstep.
    //
    // The `currentTrack != null` gate is essential: with no track the peek height is 0 (see
    // logicalPeekHeight), so the sheet rests entirely below the screen and sheetRootYPx is
    // the screen bottom — capturing that would anchor the mini elements off-screen.
    var morphAnchors by remember { mutableStateOf<MorphAnchors?>(null) }
    // Keyed on the rest BOOLEAN, not the raw per-frame float, so the effect relaunches only
    // when the sheet crosses the rest threshold — never 60×/sec during the morph itself.
    //
    // `!isAnimationRunning` is LOAD-BEARING. sheetRootYPx and the full-side ghost positions are
    // measured on nodes INSIDE the sheet, so they physically move as the sheet slides, written
    // through the throttled/debounced onGloballyPositioned in newer Compose. `expandedFraction
    // < 0.05f` alone is NOT "at rest": on a fast tap/fling collapse the fraction crosses 0.05
    // while the sheet is still moving at speed, so the capture would read a lagging mid-motion
    // sheetRootYPx and overwrite the anchors with a bad value. anchors.sheetRootYPx is the
    // mini-side origin (dominant near the end of the collapse) → every morph element jumps
    // ("moves back"), then snaps right once the settled callback re-fires a correct capture.
    // The held anchors are invariant to the sheet offset, so refusing to capture mid-animation
    // loses nothing — we only ever (re)capture from a genuinely settled, idle layout pass.
    val anchorsAtRest = expandedFraction < 0.05f && !bottomSheet.isAnimationRunning
    LaunchedEffect(
        currentTrack, anchorsAtRest, sheetRootYPx,
        fullTitleTopPx, fullArtistLeftPx, fullArtistTopPx,
        fullPrevCenterXPx, fullPrevCenterYPx, fullPlayCenterXPx, fullPlayCenterYPx,
        fullNextCenterXPx, fullNextCenterYPx, fullArtTopPx, fullArtLeftPx, fullArtSizePx,
    ) {
        if (currentTrack == null) {
            morphAnchors = null
            return@LaunchedEffect
        }
        val allMeasured = sheetRootYPx >= 0f &&
            fullTitleTopPx >= 0f && fullArtistTopPx >= 0f && fullArtistLeftPx >= 0f &&
            fullPrevCenterYPx >= 0f && fullPlayCenterYPx >= 0f && fullNextCenterYPx >= 0f &&
            fullPrevCenterXPx >= 0f && fullPlayCenterXPx >= 0f && fullNextCenterXPx >= 0f &&
            fullArtTopPx >= 0f && fullArtLeftPx >= 0f && fullArtSizePx >= 0f
        // Capture only at rest so every value comes from one settled layout pass. Structural
        // equality on the data class makes re-capturing identical values a no-op (no churn),
        // and re-measures naturally after a configuration change once the sheet settles again.
        if (allMeasured && anchorsAtRest) {
            morphAnchors = MorphAnchors(
                sheetRootYPx = sheetRootYPx,
                titleTopPx = fullTitleTopPx,
                artistLeftPx = fullArtistLeftPx,
                artistTopPx = fullArtistTopPx,
                prevCenterXPx = fullPrevCenterXPx,
                prevCenterYPx = fullPrevCenterYPx,
                playCenterXPx = fullPlayCenterXPx,
                playCenterYPx = fullPlayCenterYPx,
                nextCenterXPx = fullNextCenterXPx,
                nextCenterYPx = fullNextCenterYPx,
                artTopPx = fullArtTopPx,
                artLeftPx = fullArtLeftPx,
                artSizePx = fullArtSizePx,
            )
        }
    }

    val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
            scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
    val miniAlpha = (1f - expandedFraction * 2f).coerceIn(0f, 1f)

    // Collapse queue automatically if player collapses while queue is open
    LaunchedEffect(expandedFraction) {
        if (expandedFraction < 0.3f && queueAnimatable.value > 0f) {
            queueAnimatable.animateTo(0f, tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing))
        }
    }

    // Pre-compose the queue list while the full player sits fully open and idle, so opening the
    // queue animates already-built rows instead of composing a screenful on frame 1. One-shot:
    // flips true once per settle and never polls, so an idle full player burns no CPU.
    // `!isAnimationRunning` is load-bearing — composing during the mini→full morph tail would land
    // the row-composition spike on the very animation we keep smooth. withFrameNanos defers one
    // frame past the settle frame so the compose lands on a clean idle frame.
    var queuePrewarm by remember { mutableStateOf(false) }
    val fullyOpenIdle = expandedFraction >= 0.99f && !bottomSheet.isAnimationRunning
    LaunchedEffect(fullyOpenIdle) {
        if (fullyOpenIdle) {
            withFrameNanos {}
            queuePrewarm = true
        } else {
            queuePrewarm = false
        }
    }

    // Collapse the sheet when playback stops so the invisible full-player
    // cannot block input while nothing is showing.
    LaunchedEffect(currentTrack) {
        if (currentTrack == null) {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }

    // Back: search registered first (lower priority); player collapse registered last (higher priority)
    BackHandler(enabled = isSearchOpen) {
        viewModel.updateSearchQuery("")
        isSearchOpen = false
    }

    BackHandler(enabled = isExpanded) {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    CompositionLocalProvider(
        LocalAppBackground provides animatedBgColor,
        LocalAppSurface    provides animatedSurfaceColor
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = animatedBgColor,
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
                            onAlbumArtPositioned = { x, y, sizePx ->
                                fullArtLeftPx = x
                                fullArtTopPx  = y
                                fullArtSizePx = sizePx
                            },
                            onShowQueue = {
                                scope.launch {
                                    queueAnimatable.animateTo(
                                        1f,
                                        tween(QUEUE_ANIM_MS, easing = FastOutSlowInEasing)
                                    )
                                }
                            },
                            isFavorite = favoriteIds.contains(currentTrack?.id),
                            onToggleFavorite = { currentTrack?.let { viewModel.toggleFavorite(it.id) } },
                            onShowMenu = {
                                contextMenuTrack = currentTrack
                                isMenuFromFullPlayer = true
                            },
                        )

                        // ── Mini Player (artwork slot is transparent) ────────────
                        // When the morph overlay isn't ready to render (ghosts still
                        // measuring on first track), show the mini player's own art/controls
                        // so there's no blank frame.
                        if (expandedFraction < 0.99f) {
                            // Latched on the frozen anchors so a transient invalid live
                            // reading can't flip the mini player back to drawing its own art.
                            val overlayActive = currentTrack != null && morphAnchors != null
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
                            isSearchOpen = isSearchOpen,
                            searchQuery = searchQuery,
                            onSearchOpen = { isSearchOpen = true },
                            onSearchClose = {
                                viewModel.updateSearchQuery("")
                                isSearchOpen = false
                            },
                            onQueryChange = viewModel::updateSearchQuery,
                            dominantColor = playingTrackDominantColor
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = contentFadeProgress.value
                                    alpha = lerp(1f, 0f, p)
                                    scaleX = lerp(1f, 0.96f, p)
                                    scaleY = lerp(1f, 0.96f, p)
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                }
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = NavRoute.TRACKS,
                                modifier = Modifier.fillMaxSize(),
                                enterTransition    = { navEnterTransition(initialState, targetState) },
                                exitTransition     = { navExitTransition() },
                                popEnterTransition = { navPopEnterTransition() },
                                popExitTransition  = { navPopExitTransition() },
                            ) {
                                composable(NavRoute.TRACKS) {
                                    val sortOrder by viewModel.sortOrder.collectAsState()
    
                                    Column(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        ) {
                                            items(sortOrders, key = { it.name }) { order ->
                                                FilterChip(
                                                    selected = sortOrder == order,
                                                    onClick = { viewModel.setSortOrder(order) },
                                                    label = { Text(order.label, style = MaterialTheme.typography.labelSmall) },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = (playingTrackDominantColor ?: Color(0xFF404040)).copy(alpha = 0.35f),
                                                        selectedLabelColor = Color.White,
                                                        containerColor = Color.Transparent,
                                                        labelColor = Color(0xFF888888)
                                                    ),
                                                    border = FilterChipDefaults.filterChipBorder(
                                                        enabled = true,
                                                        selected = sortOrder == order,
                                                        borderColor = Color(0xFF444444),
                                                        selectedBorderColor = Color.Transparent
                                                    )
                                                )
                                            }
                                        }
    
                                        if (tracks.isEmpty()) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                Text(
                                                    text = "No tracks found",
                                                    color = Color.White,
                                                    modifier = Modifier.align(Alignment.Center)
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(bottom = trackListBottomPadding)
                                            ) {
                                                itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                                                    val isActiveTrack = currentTrack?.id == track.id
                                                    TrackListItem(
                                                        track = track,
                                                        isActiveTrack = isActiveTrack,
                                                        isPlaybackActive = isPlaybackActive,
                                                        isFavorite = favoriteIds.contains(track.id),
                                                        onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                                                        onClick = { viewModel.playTracks(tracks, index) },
                                                        modifier = Modifier.staggeredEntrance(index, isLoadingIn = isLoadingTracks),
                                                        onViewAlbum = {
                                                            navController.navigate(NavRoute.albumDetailRoute(track.album))
                                                        },
                                                        onViewArtist = {
                                                            navController.navigate(NavRoute.artistDetailRoute(track.artist))
                                                        },
                                                        onMenuOpen = { offset, size ->
                                                            contextMenuTrack = track
                                                            contextMenuArtOffset = offset
                                                            contextMenuArtSize = size
                                                            isMenuFromFullPlayer = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                composable(NavRoute.ALBUMS) {
                                    Box(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        AlbumsScreen(
                                            onAlbumClick = { albumName ->
                                                navController.navigate(NavRoute.albumDetailRoute(albumName))
                                            }
                                        )
                                    }
                                }
                                composable(
                                    route = NavRoute.ALBUM_DETAIL,
                                    arguments = listOf(
                                        navArgument("albumName") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                                    val isPlaybackActive by viewModel.isPlaying.collectAsState()
                                    val favoriteIds by viewModel.favoriteIds.collectAsState()
                                    Box(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        AlbumDetailScreen(
                                            albumName = albumName,
                                            onBack = { navController.popBackStack() },
                                            currentTrack = currentTrack,
                                            isPlaying = isPlaybackActive,
                                            favoriteIds = favoriteIds,
                                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                            onTrackClick = { list, idx -> viewModel.playTracks(list, idx) },
                                            bottomPadding = trackListBottomPadding
                                        )
                                    }
                                }
                                composable(NavRoute.ARTISTS) {
                                    Box(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        ArtistsScreen(
                                            onArtistClick = { artistName ->
                                                navController.navigate(NavRoute.artistDetailRoute(artistName))
                                            }
                                        )
                                    }
                                }
                                composable(
                                    route = NavRoute.ARTIST_DETAIL,
                                    arguments = listOf(
                                        navArgument("artistName") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val artistName = backStackEntry.arguments?.getString("artistName") ?: ""
                                    val isPlaybackActive by viewModel.isPlaying.collectAsState()
                                    val favoriteIds by viewModel.favoriteIds.collectAsState()
                                    Box(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        ArtistDetailScreen(
                                            artistName = artistName,
                                            onBack = { navController.popBackStack() },
                                            currentTrack = currentTrack,
                                            isPlaying = isPlaybackActive,
                                            favoriteIds = favoriteIds,
                                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                            onTrackClick = { list, idx -> viewModel.playTracks(list, idx) },
                                            bottomPadding = trackListBottomPadding
                                        )
                                    }
                                }
                                composable(NavRoute.PLAYLISTS) {
                                    Box(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        PlaylistsScreen(
                                            onFavoritesClick = {
                                                navController.navigate(NavRoute.FAVORITES)
                                            },
                                            onPlaylistClick = { playlistId ->
                                                navController.navigate(NavRoute.playlistDetailRoute(playlistId))
                                            },
                                            onCreatePlaylist = { showCreateFromPlaylistsTab = true },
                                            onMenuOpen = { playlist, offset, size ->
                                                contextMenuPlaylist = playlist
                                                contextMenuPlaylistArtOffset = offset
                                                contextMenuPlaylistArtSize = size
                                            },
                                            bottomPadding = trackListBottomPadding,
                                            dominantColor = playingTrackDominantColor
                                        )
                                    }
                                }
                                composable(
                                    route = NavRoute.PLAYLIST_DETAIL,
                                    arguments = listOf(navArgument("playlistId") { type = androidx.navigation.NavType.LongType })
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        PlaylistDetailScreen(
                                            onBack = { navController.popBackStack() },
                                            onPlayTracks = { list, idx -> viewModel.playTracks(list, idx) },
                                            bottomPadding = trackListBottomPadding
                                        )
                                    }
                                }
                                composable(NavRoute.FAVORITES) {
                                    val allTracks by viewModel.tracks.collectAsState()
                                    val favoriteIds by viewModel.favoriteIds.collectAsState()
                                    val isPlaybackActive by viewModel.isPlaying.collectAsState()
                                    Box(modifier = Modifier.fillMaxSize().background(LocalAppBackground.current)) {
                                        FavoritesScreen(
                                            allTracks = allTracks,
                                            favoriteIds = favoriteIds,
                                            currentTrack = currentTrack,
                                            isPlaying = isPlaybackActive,
                                            onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                            onTrackClick = { list, idx -> viewModel.playTracks(list, idx) },
                                            onBack = { navController.popBackStack() },
                                            bottomPadding = trackListBottomPadding
                                        )
                                    }
                                }
                            }

                            if (isTransitioning) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent(PointerEventPass.Initial)
                                                        .changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                )
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
            val navBarHeightPx = with(density) { (bottomNavHeight + bottomInsets).toPx() }
            LaconicalBottomNav(
                selectedRoute = when {
                    rawRoute.startsWith("album_detail") -> NavRoute.ALBUMS
                    rawRoute.startsWith("artist_detail") -> NavRoute.ARTISTS
                    rawRoute.startsWith("playlist_detail") -> NavRoute.PLAYLISTS
                    else -> rawRoute
                },
                onTabSelected = { route ->
                    if (!isTransitioning) {
                        if (isSearchOpen) {
                            isSearchOpen = false
                            viewModel.updateSearchQuery("")
                        }
                        navController.navigate(route) {
                            popUpTo(NavRoute.TRACKS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                dynamicColor = playingTrackDominantColor,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        alpha = miniAlpha
                        translationY = (1f - miniAlpha) * navBarHeightPx
                    }
            )
        }

        // ── Search results overlay ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = isSearchOpen && expandedFraction < 0.01f,
            enter = fadeIn(animationSpec = tween(250, delayMillis = 100)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = statusBarPadding + 4.dp + 56.dp, bottom = sheetPeekHeight)
                    .background(LocalAppBackground.current)
            ) {
                SearchResultsPanel(
                    searchQuery = searchQuery,
                    tracks = tracks,
                    searchedAlbums = searchedAlbums,
                    searchedArtists = searchedArtists,
                    searchedPlaylists = searchedPlaylists,
                    playlistArtTracks = playlistArtTracks,
                    dominantColor = playingTrackDominantColor,
                    currentTrack = currentTrack,
                    isPlaying = isPlaybackActive,
                    favoriteIds = favoriteIds,
                    onTrackClick = { list, idx -> viewModel.playTracks(list, idx) },
                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    onAlbumClick = { albumName ->
                        isSearchOpen = false
                        viewModel.updateSearchQuery("")
                        navController.navigate(NavRoute.albumDetailRoute(albumName))
                    },
                    onArtistClick = { artistName ->
                        isSearchOpen = false
                        viewModel.updateSearchQuery("")
                        navController.navigate(NavRoute.artistDetailRoute(artistName))
                    },
                    onPlaylistClick = { playlistId ->
                        isSearchOpen = false
                        viewModel.updateSearchQuery("")
                        navController.navigate(NavRoute.playlistDetailRoute(playlistId))
                    }
                )
            }
        }

        // ── Morph overlay + Queue Sheet ─────────────────────────────────────────
        // QueueMorphLayer reads queueAnimatable.value itself, scoping 60fps
        // recompositions to that small composable rather than all of LibraryScreen.
        //
        // Gate on the FROZEN anchors, not live positions. Once captured at rest the overlay
        // stays mounted, so a transient invalid reading from the throttled position callbacks
        // can no longer unmount it mid-animation (that unmount was the disappear/reappear
        // flash). All full-side values come from one consistent layout snapshot, so the
        // sheet-relative math never disagrees with itself.
        val morphTrack = currentTrack
        val anchors = morphAnchors
        if (morphTrack != null && anchors != null) {
            QueueMorphLayer(
                queueAnimatable = queueAnimatable,
                prewarm = queuePrewarm,
                viewModel = viewModel,
                currentTrack = morphTrack,
                expandedFraction = expandedFraction,
                collapsedSheetRootYPx = anchors.sheetRootYPx,
                sheetRootYPx = anchors.sheetRootYPx,
                fullTitleTopPx = anchors.titleTopPx,
                fullArtistLeftPx = anchors.artistLeftPx,
                fullArtistTopPx = anchors.artistTopPx,
                fullPrevCenterXPx = anchors.prevCenterXPx,
                fullPrevCenterYPx = anchors.prevCenterYPx,
                fullPlayCenterXPx = anchors.playCenterXPx,
                fullPlayCenterYPx = anchors.playCenterYPx,
                fullNextCenterXPx = anchors.nextCenterXPx,
                fullNextCenterYPx = anchors.nextCenterYPx,
                fullArtTopPx  = anchors.artTopPx,
                fullArtLeftPx = anchors.artLeftPx,
                fullArtSizePx = anchors.artSizePx,
                scope = scope,
            )
        }
        // ── Track context menu overlay ──────────────────────────────────────
        contextMenuTrack?.let { track ->
            TrackMenuOverlay(
                track = track,
                artStartOffsetPx = contextMenuArtOffset,
                artStartSizePx = contextMenuArtSize,
                skipArtMorph = isMenuFromFullPlayer,
                isFavorite = favoriteIds.contains(track.id),
                dominantColor = playingTrackDominantColor,
                playlists = playlists,
                artTracks = playlistArtTracks,
                onDismiss = {
                    contextMenuTrack = null
                    isMenuFromFullPlayer = false
                },
                onFavoriteToggle = { viewModel.toggleFavorite(track.id) },
                onViewAlbum = {
                    val fromFullPlayer = isMenuFromFullPlayer
                    contextMenuTrack = null
                    isMenuFromFullPlayer = false
                    if (fromFullPlayer) {
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }
                    navController.navigate(NavRoute.albumDetailRoute(track.album))
                },
                onViewArtist = {
                    val fromFullPlayer = isMenuFromFullPlayer
                    contextMenuTrack = null
                    isMenuFromFullPlayer = false
                    if (fromFullPlayer) {
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    }
                    navController.navigate(NavRoute.artistDetailRoute(track.artist))
                },
                onSelectPlaylist = { playlist ->
                    viewModel.addTrackToPlaylist(track.id, playlist.id)
                    playlistToastData = Pair(track.title, playlist.name)
                    contextMenuTrack = null
                    isMenuFromFullPlayer = false
                },
                onCreateNewPlaylist = { originOffset ->
                    newPlaylistOriginOffset = originOffset
                    pendingNewPlaylistTrack = track
                    // contextMenuTrack intentionally NOT cleared — overlay stays alive
                    showCreateForPicker = true
                },
            )
        }
        // ── Playlist context menu overlay ───────────────────────────────────
        contextMenuPlaylist?.let { playlist ->
            PlaylistMenuOverlay(
                playlist = playlist,
                artTracks = playlistArtTracks[playlist.id] ?: emptyList(),
                artStartOffsetPx = contextMenuPlaylistArtOffset,
                artStartSizePx = contextMenuPlaylistArtSize,
                dominantColor = playingTrackDominantColor,
                onDismiss = {
                    if (!showRenamePlaylist && !showDeletePlaylist) contextMenuPlaylist = null
                },
                onRename = { showRenamePlaylist = true },
                onDelete = { showDeletePlaylist = true },
            )
        }
        if (showRenamePlaylist) {
            contextMenuPlaylist?.let { target ->
                PlaylistBottomSheet(
                    title = "Rename Playlist",
                    initialName = target.name,
                    onDismiss = {
                        showRenamePlaylist = false
                        contextMenuPlaylist = null
                    },
                    onConfirm = { name ->
                        viewModel.renamePlaylist(target.id, name)
                        showRenamePlaylist = false
                        contextMenuPlaylist = null
                    }
                )
            }
        }
        if (showDeletePlaylist) {
            contextMenuPlaylist?.let { target ->
                AlertDialog(
                    onDismissRequest = {
                        showDeletePlaylist = false
                        contextMenuPlaylist = null
                    },
                    title = { Text("Delete \"${target.name}\"?") },
                    text = { Text("This will permanently delete the playlist and remove all its tracks. Your music files are not affected.") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deletePlaylist(target.id)
                            showDeletePlaylist = false
                            contextMenuPlaylist = null
                        }) {
                            Text("Delete", color = Color(0xFFEF4444))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDeletePlaylist = false
                            contextMenuPlaylist = null
                        }) { Text("Cancel") }
                    }
                )
            }
        }
        if (showCreateForPicker) {
            CreatePlaylistDialog(
                originOffset = newPlaylistOriginOffset.takeIf { contextMenuTrack != null },
                dominantColor = playingTrackDominantColor,
                onDismiss = {
                    showCreateForPicker = false
                    contextMenuTrack = null
                    isMenuFromFullPlayer = false
                },
                onBack = {
                    showCreateForPicker = false
                    // contextMenuTrack stays — reveals TrackMenuOverlay in PLAYLIST mode
                },
                onConfirm = { name ->
                    pendingNewPlaylistTrack?.let { t ->
                        viewModel.createPlaylistAndAdd(name, t.id)
                        playlistToastData = Pair(t.title, name)
                    }
                    pendingNewPlaylistTrack = null
                    showCreateForPicker = false
                    contextMenuTrack = null
                    isMenuFromFullPlayer = false
                },
            )
        }
        if (showCreateFromPlaylistsTab) {
            CreatePlaylistDialog(
                originOffset = null,
                dominantColor = playingTrackDominantColor,
                onDismiss = { showCreateFromPlaylistsTab = false },
                onBack = { showCreateFromPlaylistsTab = false },
                onConfirm = { name ->
                    viewModel.createPlaylist(name)
                    showCreateFromPlaylistsTab = false
                },
            )
        }
        PlaylistAddedToast(
            data = playlistToastData,
            onDismiss = { playlistToastData = null },
        )
    } // end outer Box
    } // end CompositionLocalProvider
}

/**
 * One atomic snapshot of every mini→full morph anchor, captured while the sheet is at rest.
 *
 * All positions are root-space pixels from a single settled layout pass, so they are mutually
 * consistent — [sheetRootYPx] is the sheet-content root and the `*Px` fields are the FullPlayer
 * ghost positions. The morph derives stable sheet-relative targets as (`fieldPx - sheetRootYPx`),
 * which is invariant to the sheet's offset. Freezing this snapshot is what makes the morph
 * immune to the throttled/desynced onGloballyPositioned callbacks in newer Compose foundation.
 */
/**
 * Ken-Perlin smootherstep — zero first AND second derivative at both ends, so a value driven
 * by it eases in and out with no perceptible kink. Used only for the morph's decorative
 * properties (glow bloom, play-circle fade); positional lerps stay linear so they track the
 * drag finger 1:1.
 */
private fun smootherstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

private data class MorphAnchors(
    val sheetRootYPx: Float,
    val titleTopPx: Float,
    val artistLeftPx: Float,
    val artistTopPx: Float,
    val prevCenterXPx: Float,
    val prevCenterYPx: Float,
    val playCenterXPx: Float,
    val playCenterYPx: Float,
    val nextCenterXPx: Float,
    val nextCenterYPx: Float,
    val artTopPx: Float,
    val artLeftPx: Float,
    val artSizePx: Float,
)

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
    prewarm: Boolean,
    viewModel: MainViewModel,
    currentTrack: Track,
    expandedFraction: Float,
    collapsedSheetRootYPx: Float,
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
    fullArtTopPx: Float = -1f,
    fullArtLeftPx: Float = -1f,
    fullArtSizePx: Float = -1f,
    scope: CoroutineScope,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val screenWidthDp = configuration.screenWidthDp.dp

    // Reading value HERE instead of in LibraryScreen is the performance fix.
    val queueProg = queueAnimatable.value

    val isStable = (expandedFraction < 0.05f && queueProg < 0.05f) ||
                   (expandedFraction > 0.95f && queueProg < 0.05f) ||
                   (queueProg > 0.95f)

    // ── Queue Sheet (rendered first = below morph overlay) ───────────────────
    // Fade + small 80dp slide instead of a full-screen-height slide. This keeps
    // the sheet background behind the morph elements from the very start of the
    // transition, eliminating the empty-space-at-top gap.
    if (queueProg > 0.001f || prewarm) {
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
                // While invisible (pre-warm at rest), park the whole sheet OFF-SCREEN so its hit
                // region does not overlap the FullPlayer beneath. In Compose the topmost sibling
                // holding any pointer node claims the pointer (shareWithSiblings=false by default)
                // and blocks the controls below — the LazyColumn's scroll node does this over the
                // lower half (UP NEXT / seek / like), and userScrollEnabled can't remove that node.
                // graphicsLayer translation moves the hit bounds too, but NOT measurement/layout,
                // so the rows still pre-compose off-screen. Once opening (queueProg > 0.001), use
                // the small 80dp slide that keeps the sheet background behind the morph elements.
                translationY = if (queueProg < 0.001f) screenH
                               else (1f - queueProg) * slideDistance
                alpha = queueProg.coerceIn(0f, 1f)
            }
        )
    }

    // ── Morph overlay (rendered second = above QueueSheet) ───────────────────
    val context = LocalContext.current
    val loadTarget = currentTrack.mediaUri
    val imageModel: ImageRequest = remember(loadTarget) {
        ImageRequest.Builder(context)
            .data(AudioArtData(loadTarget, currentTrack.albumArtUri))
            .size(Size.ORIGINAL)
            .build()
    }
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
    // Every full-side value here ([sheetRootYPx], [collapsedSheetRootYPx] and the full*Px
    // params) is a FROZEN anchor — one atomic snapshot captured at rest by LibraryScreen (see
    // MorphAnchors). They are NOT live per-frame readings, so the (full*Px - sheetRootYPx)
    // differences below are true constants: a "hook"-free straight path on the mini side and
    // jitter-proof targets on the full side, regardless of how the throttled position
    // callbacks fire mid-animation. The ONLY live input to this overlay is expandedFraction
    // (the smooth sheet-offset driver) and queueProg.
    val miniSheetRootYPx = if (collapsedSheetRootYPx >= 0f) collapsedSheetRootYPx else sheetRootYPx
    val miniSheetRootYDp = with(density) { miniSheetRootYPx.toDp() }
    val miniArtSizeDp = 52.dp
    val miniArtLeftDp = 24.dp
    val miniArtTopDp  = miniSheetRootYDp + 11.5.dp

    val fullArtSizeDp = if (fullArtSizePx >= 0f) with(density) { fullArtSizePx.toDp() }
                        else (screenWidthDp - 48.dp) * 0.95f
    val fullArtLeftDp = if (fullArtLeftPx >= 0f) with(density) { fullArtLeftPx.toDp() }
                        else (screenWidthDp - fullArtSizeDp) / 2f
    val fullArtTopDp  = if (fullArtTopPx >= 0f) with(density) { (fullArtTopPx - sheetRootYPx).toDp() }
                        else statusBarPadding + 16.dp + 48.dp + 64.dp

    val queueArtSizeDp = 56.dp
    val queueArtLeftDp = 20.dp
    val queueArtTopDp  = statusBarPadding + 20.dp

    // ── Title morph overlay ────────────────────────────────────────────
    // Rendered before art so the thumbnail draws on top during transition.
    val miniTitleLeftDp = miniArtLeftDp + miniArtSizeDp + 12.dp
    val miniTitleTopDp  = miniArtTopDp + 2.dp
    val fullTitleLeftDp = 48.dp
    // Full-player Y positions: (frozen absY - frozen sheetRootY) = the element's offset within
    // the sheet, invariant to the sheet's live position — a stable lerp target.
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

    FadingMarqueeText(
        text = currentTrack.title,
        color = Color.White,
        fontSize = finalTitleSize,
        fontWeight = FontWeight.Bold,
        isScrolling = isStable,
        modifier = Modifier
            .offset(x = finalTitleLeft, y = finalTitleTop)
            .widthIn(max = finalTitleMaxWidthDp),
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

    FadingMarqueeText(
        text = currentTrack.artist,
        color = Color(0xFFBBBBBB),
        fontSize = finalArtistSize,
        fontWeight = FontWeight.Medium,
        isScrolling = isStable,
        modifier = Modifier
            .offset(x = finalArtistLeft, y = finalArtistTop)
            .widthIn(max = finalTitleMaxWidthDp),
    )

    // ── Album art morph overlay ────────────────────────────────────────
    // Declared after title/artist so it draws on top of text during transition.
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
            val easedGlow = smootherstep(glowFraction)
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
        SubcomposeAsyncImage(
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
            contentScale = ContentScale.Crop,
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF555555),
                        modifier = Modifier.fillMaxSize(0.45f)
                    )
                }
            }
        )
    }

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
    val circleAlpha       = smootherstep(expandedFraction) * (1f - queueProg)
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

@Composable
private fun PlaylistAddedToast(
    data: Pair<String, String>?,
    onDismiss: () -> Unit,
) {
    val visible = data != null
    val trackTitle = data?.first.orEmpty()
    val playlistName = data?.second.orEmpty()

    LaunchedEffect(data) {
        if (data != null) {
            kotlinx.coroutines.delay(2600)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = tween(300, easing = LinearOutSlowInEasing),
            initialOffsetY = { -it },
        ) + fadeIn(tween(200)),
        exit = fadeOut(tween(250)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 10.dp)
            .wrapContentHeight(Alignment.Top),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF0181820))
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF7C6FE0).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = null,
                    tint = Color(0xFF7C6FE0),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "Added to $playlistName",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
