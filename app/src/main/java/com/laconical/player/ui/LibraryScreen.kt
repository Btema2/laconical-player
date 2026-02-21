package com.laconical.player.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.laconical.player.ui.components.LaconicalBottomNav
import com.laconical.player.ui.components.LaconicalTopBar
import com.laconical.player.ui.components.TrackListItem

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

    Scaffold(
        containerColor = animatedColor,
        topBar = { 
            if (hasPermission) {
                LaconicalTopBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = viewModel::updateSearchQuery
                ) 
            }
        },
        bottomBar = { 
            if (hasPermission) {
                LaconicalBottomNav() 
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (hasPermission) {
                val tracks by viewModel.tracks.collectAsState()

                LaunchedEffect(Unit) {
                    viewModel.loadTracks()
                }

                val currentTrack by viewModel.currentTrack.collectAsState()

                if (tracks.isEmpty()) {
                    Text("No tracks found")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(tracks) { track ->
                            val isPlaying = currentTrack?.id == track.id
                            TrackListItem(
                                track = track,
                                isPlaying = isPlaying,
                                onClick = { viewModel.playTrack(track) }
                            )
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
