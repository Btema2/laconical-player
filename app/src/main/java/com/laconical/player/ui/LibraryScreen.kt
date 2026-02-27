package com.laconical.player.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.ui.components.FullPlayer
import com.laconical.player.ui.components.LaconicalBottomNav
import com.laconical.player.ui.components.LaconicalTopBar
import com.laconical.player.ui.components.MiniPlayer
import com.laconical.player.ui.components.TrackListItem
import kotlinx.coroutines.launch

/**
 * Main library screen that displays local audio files and handles media permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val playingTrackDominantColor by viewModel.playingTrackDominantColor.collectAsState()

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
    val bottomNavHeight = 64.dp
    val miniPlayerHeight = (75 + 12).dp // Height + bottom padding
    val bottomInsets = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekHeight = miniPlayerHeight + bottomNavHeight + bottomInsets

    // Simplified state based on current/target state for reliable UI
    val isExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded || 
                    scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
    
    val expansionAlpha by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(400),
        label = "ExpansionAlpha"
    )

    BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    Surface(
        color = animatedColor,
        modifier = Modifier.fillMaxSize()
    ) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight,
            sheetContainerColor = Color.Transparent, 
            sheetShadowElevation = 0.dp,
            sheetDragHandle = null,
            sheetContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f }
                ) {
                    // Full Player
                    FullPlayer(
                        viewModel = viewModel,
                        expansionAlpha = expansionAlpha,
                        onCollapse = {
                            scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                        }
                    )

                    // Mini Player (Visible when partially expanded)
                    if (expansionAlpha < 0.9f) {
                        MiniPlayer(
                            viewModel = viewModel,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer { 
                                    alpha = 1f - expansionAlpha
                                    translationY = expansionAlpha * -50f 
                                },
                            onClick = {
                                if (hasPermission) {
                                    scope.launch { scaffoldState.bottomSheetState.expand() }
                                }
                            }
                        )
                    }

                    // Bottom Navigation Bar (Visible in peek area, below MiniPlayer)
                    if (hasPermission) {
                        LaconicalBottomNav(
                            dynamicColor = playingTrackDominantColor,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = miniPlayerHeight)
                                .graphicsLayer {
                                    translationY = expansionAlpha * with(density) { (bottomNavHeight + bottomInsets).toPx() }
                                    alpha = 1f - expansionAlpha
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

                    LaunchedEffect(Unit) {
                        viewModel.loadTracks()
                    }

                    val currentTrack by viewModel.currentTrack.collectAsState()
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
                        Text(
                            text = "Permission required to access audio files",
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                        }) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}
